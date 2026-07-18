package com.grepiu.aidiary.mvi.viewmodel

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.model.TitleStyle
import com.grepiu.aidiary.data.model.extractPlainText
import com.grepiu.aidiary.data.repository.DiaryRepository
import com.grepiu.aidiary.data.repository.DiaryDatabase
import com.grepiu.aidiary.data.repository.DiaryMeta
import com.grepiu.aidiary.data.repository.DiarySearchHit
import com.grepiu.aidiary.data.repository.ImageStorageManager
import com.grepiu.aidiary.data.repository.LegacyJsonImporter
import com.grepiu.aidiary.data.repository.PlannerRepository
import com.grepiu.aidiary.data.repository.Goal
import com.grepiu.aidiary.data.repository.PlannerTask
import com.grepiu.aidiary.data.slm.DeviceCapabilityChecker
import com.grepiu.aidiary.data.slm.DecorateResultParser
import com.grepiu.aidiary.data.slm.ModelDownloaderV2
import com.grepiu.aidiary.data.slm.DiaryLLMEngine
import com.grepiu.aidiary.data.slm.SherpaEngine
import com.grepiu.aidiary.data.slm.toTextFormatting
import com.grepiu.aidiary.mvi.effect.DiaryEffect
import com.grepiu.aidiary.mvi.intent.DiaryIntent
import com.grepiu.aidiary.mvi.state.DiaryPhase
import com.grepiu.aidiary.mvi.state.DiaryState
import com.grepiu.aidiary.mvi.state.ChatMessage
import com.grepiu.aidiary.mvi.state.PendingContentTypeChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

/**
 * 일기 앱의 비즈니스 로직 및 온디바이스 AI 라이프사이클을 조율하는 Android ViewModel입니다.
 */
class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = ModelDownloaderV2(application)
    private val repository = DiaryRepository(application)
    private val imageStore = ImageStorageManager(application)
    private val plannerRepository = PlannerRepository(application)
    private var llmEngine: DiaryLLMEngine? = null
    private var sherpaEngine: SherpaEngine? = null

    private var downloadJob: Job? = null
    private var analysisJob: Job? = null
    private var recordingJob: Job? = null
    private var chatJob: Job? = null
    private var searchJob: Job? = null
    private var voiceLangJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // 저장 시 통합 분석 결과의 감정 라벨을 다이얼로그 응답까지 캐싱 (재호출 방지)
    private var pendingEmotionLabel: String? = null

    // MVI 상태 데이터 홀더
    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state.asStateFlow()

    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private fun isTermsAccepted(): Boolean {
        return prefs.getBoolean("terms_accepted", false)
    }

    private fun acceptTerms() {
        prefs.edit().putBoolean("terms_accepted", true).apply()
    }

    // MVI 부수 효과 채널
    private val _effect = Channel<DiaryEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        // 앱 구동 시 일기 메타 페이지 1 로드 (Flow 자동 갱신은 별도 collect)
        processIntent(DiaryIntent.LoadDiaries)

        // 플래너 및 목표 데이터 불러오기
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val initialGoals = plannerRepository.loadGoals()
        val initialTasks = plannerRepository.loadTasks()
        _state.update {
            it.copy(
                selectedDateString = todayStr,
                goals = initialGoals,
                plannerTasks = initialTasks
            )
        }

        // 구버전 JSON → Room 자동 import (1회). DB 비어 있고 파일이 있으면 실행.
        ensureLegacyImported()

        viewModelScope.launch {
            ensureModelReady()
        }
        viewModelScope.launch {
            ensureWhisperModelReady()
        }
    }

    /**
     * 첫 실행 시 [diary_history.json] 이 존재하고 Room DB 가 비어 있으면
     * [LegacyJsonImporter] 로 1회 import. 결과에 따라 토스트 안내.
     */
    private fun ensureLegacyImported() {
        viewModelScope.launch {
            _state.update { it.copy(isImportingLegacy = true, legacyImportProgress = 0f) }
            val importer = LegacyJsonImporter(
                getApplication(),
                DiaryDatabase.get(getApplication())
            )
            val result: LegacyJsonImporter.ImportResult? = withContext(Dispatchers.IO) {
                importer.importIfNeeded { imported, total ->
                    val p = if (total > 0) imported.toFloat() / total else 0f
                    _state.update { it.copy(legacyImportProgress = p) }
                }
            }
            _state.update { it.copy(isImportingLegacy = false, legacyImportProgress = 0f) }
            if (result != null && result.totalImported > 0) {
                repository.invalidateCache()
                // import 후 1페이지 다시 로드
                loadFirstDiaryPage()
                sendEffect(DiaryEffect.ShowToast("기존 일기 ${result.totalImported}건을 가져왔어요."))
            }
        }
    }

    /**
     * MVI 인텐트 처리
     */
    fun processIntent(intent: DiaryIntent) {
        when (intent) {
            is DiaryIntent.LoadDiaries -> {
                loadFirstDiaryPage()
            }
            is DiaryIntent.LoadMoreDiaries -> {
                loadMoreDiaryPage()
            }
            is DiaryIntent.LoadFullDiary -> {
                viewModelScope.launch {
                    val full = repository.loadFullDiary(intent.id)
                    if (full != null) {
                        // 검색 모드였다면 검색 헤제 (상세화면은 일반 모드 진입이 자연스러움)
                        _state.update {
                            it.copy(
                                selectedDiary = full,
                                phase = DiaryPhase.DETAIL,
                                searchQuery = "",
                                isSearching = false
                            )
                        }
                    } else {
                        sendEffect(DiaryEffect.ShowToast("일기를 찾을 수 없어요."))
                    }
                }
            }
            is DiaryIntent.SearchDiaries -> {
                handleSearch(intent.query)
            }
            is DiaryIntent.ClearDiarySearch -> {
                searchJob?.cancel()
                loadFirstDiaryPage()
            }
            is DiaryIntent.StartDownload -> {
                startModelDownload()
            }
            is DiaryIntent.CancelDownload -> {
                downloadJob?.cancel()
                _state.update { it.copy(isDownloadingModel = false, modelDownloadSizeText = null) }
                sendEffect(DiaryEffect.ShowToast("다운로드가 취소되었습니다."))
            }
            is DiaryIntent.NavigateTo -> {
                val targetPhase = if (intent.phase == DiaryPhase.LIST && !isTermsAccepted()) {
                    DiaryPhase.WELCOME
                } else {
                    intent.phase
                }
                _state.update { currentState ->
                    currentState.copy(
                        phase = targetPhase,
                        selectedDiary = intent.selectedDiary,
                        // 새 일기 작성 화면 진입 시 draft 값 초기화
                        draftTitle = if (targetPhase == DiaryPhase.WRITE) "" else currentState.draftTitle,
                        draftBlocks = if (targetPhase == DiaryPhase.WRITE) emptyList() else currentState.draftBlocks,
                        draftTitleStyle = if (targetPhase == DiaryPhase.WRITE) TitleStyle.Default else currentState.draftTitleStyle,
                        draftEmotion = if (targetPhase == DiaryPhase.WRITE) "Neutral" else currentState.draftEmotion,
                        // initialContentType이 지정된 경우 해당 타입으로, 아니면 DIARY 기본값
                        draftContentType = if (targetPhase == DiaryPhase.WRITE) {
                            intent.initialContentType ?: com.grepiu.aidiary.data.model.ContentType.DIARY
                        } else currentState.draftContentType,
                        isGeneratingAnalysis = if (targetPhase == DiaryPhase.WRITE) false else currentState.isGeneratingAnalysis
                    )
                }
            }
            is DiaryIntent.AcceptTermsAndProceed -> {
                acceptTerms()
                _state.update { it.copy(phase = DiaryPhase.LIST) }
            }
            is DiaryIntent.UpdateDraft -> {
                _state.update { currentState ->
                    currentState.copy(
                        draftEmotion = intent.emotion ?: currentState.draftEmotion
                    )
                }
            }
            is DiaryIntent.SaveDiary -> {
                saveDiaryDraft()
            }
            is DiaryIntent.DeleteDiary -> {
                viewModelScope.launch {
                    repository.deleteEntry(intent.id)
                    // 삭제 후 메타 페이지 갱신 (선택 일기도 무효화)
                    refreshCurrentMetaPage()
                    _state.update { it.copy(phase = DiaryPhase.LIST, selectedDiary = null) }
                    sendEffect(DiaryEffect.ShowToast("일기가 삭제되었습니다."))
                }
            }
            is DiaryIntent.ConfirmContentTypeChange -> {
                val pending = _state.value.pendingContentTypeChange ?: return
                _state.update {
                    it.copy(
                        draftContentType = intent.newType,
                        pendingContentTypeChange = null,
                        isClassifyingTypeOnSave = false
                    )
                }
                sendEffect(DiaryEffect.ShowToast("글 타입을 '${pending.suggestedType.label}'(으)로 변경해 저장할게요."))
                proceedWithEmotionAndSave()
            }
            is DiaryIntent.KeepCurrentContentTypeAndSave -> {
                val pending = _state.value.pendingContentTypeChange ?: return
                _state.update {
                    it.copy(
                        pendingContentTypeChange = null,
                        isClassifyingTypeOnSave = false
                    )
                }
                sendEffect(DiaryEffect.ShowToast("현재 타입 '${pending.currentType.label}'(으)로 저장할게요."))
                proceedWithEmotionAndSave()
            }
            is DiaryIntent.CancelContentTypeChange -> {
                pendingEmotionLabel = null
                _state.update {
                    it.copy(
                        pendingContentTypeChange = null,
                        isClassifyingTypeOnSave = false,
                        isGeneratingAnalysis = false
                    )
                }
                analysisJob?.cancel()
                sendEffect(DiaryEffect.ShowToast("저장을 취소했어요."))
            }
            is DiaryIntent.UpdateDraftType -> {
                _state.update { it.copy(draftContentType = intent.contentType) }
            }
            is DiaryIntent.UpdateDraftTitleStyle -> {
                _state.update { it.copy(draftTitleStyle = intent.style) }
            }
            is DiaryIntent.UpdateDraftTitle -> {
                _state.update { it.copy(draftTitle = intent.text) }
            }

            // ===== 작성 보조 AI 액션 =====
            is DiaryIntent.SuggestTitle -> {
                suggestTitleFromBody()
            }
            is DiaryIntent.ClassifyContentType -> {
                classifyDraftContentType()
            }
            is DiaryIntent.ProofreadBlock -> {
                proofreadBlock(intent.blockId)
            }
            is DiaryIntent.DecorateBlock -> {
                decorateBlock(intent.blockId)
            }
            is DiaryIntent.ShowDownloadNotice -> {
                _state.update { it.copy(showDownloadNotice = intent.show) }
            }
            is DiaryIntent.ShowWifiWarning -> {
                _state.update { it.copy(showWifiWarning = intent.show) }
            }
            is DiaryIntent.StartRecording -> {
                startRecording()
            }
            is DiaryIntent.StopRecording -> {
                stopRecording()
            }
            is DiaryIntent.UpdateVoiceLanguage -> {
                val newLang = intent.language
                // 같은 언어 클릭(로딩 중 포함) 은 무시 — 사용자가 같은 칩을 연타해도 재로딩하지 않음
                if (_state.value.voiceLanguage == newLang) return

                // 직전 언어 변경 잡이 살아있으면 취소 (가장 마지막 요청만 적용)
                voiceLangJob?.cancel()

                // 1) 클릭 즉시 상태 반영: 선택된 칩이 바로 새 언어로 강조됨
                _state.update { it.copy(voiceLanguage = newLang, isChangingVoiceLanguage = true) }

                if (!_state.value.isSherpaModelReady) {
                    // 모델이 아직 없으면 상태만 저장하고 로딩은 해제
                    sendEffect(DiaryEffect.ShowToast("음성 인식 언어가 '${languageLabel(newLang)}'(으)로 설정됐어요. 모델 다운로드 후 적용됩니다."))
                    _state.update { it.copy(isChangingVoiceLanguage = false) }
                    return
                }

                // 2) 백그라운드에서 엔진 dispose + 새 언어로 재초기화
                voiceLangJob = viewModelScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            try { sherpaEngine?.dispose() } catch (_: Exception) {}
                            sherpaEngine = null
                            initSherpa()
                        }
                        // 직전 요청 도중 새 요청이 들어와 잡이 취소되었을 수 있으므로 현재 선택 언어와 일치할 때만 토스트
                        if (_state.value.voiceLanguage == newLang) {
                            sendEffect(DiaryEffect.ShowToast("음성 인식 언어를 '${languageLabel(newLang)}'(으)로 변경했어요."))
                        }
                    } catch (e: Exception) {
                        if (_state.value.voiceLanguage == newLang) {
                            sendEffect(DiaryEffect.ShowToast("언어 변경 실패: ${e.message}"))
                        }
                    } finally {
                        // generation guard: 최신 요청의 잡만 로딩 상태를 해제
                        if (_state.value.voiceLanguage == newLang) {
                            _state.update { it.copy(isChangingVoiceLanguage = false) }
                        }
                    }
                }
            }
            is DiaryIntent.TranslateDraftToKorean -> {
                translateDraftToKorean()
            }
            is DiaryIntent.ApplyTranslatedDraft -> {
                applyTranslatedDraft()
            }
            is DiaryIntent.ClearTranslatedDraft -> {
                _state.update { it.copy(translatedDraft = null, isTranslatingDraft = false) }
            }
            is DiaryIntent.CopyDraftToClipboard -> {
                copyDraftToClipboard()
            }

            // ===== 블록 기반 콘텐츠 =====
            is DiaryIntent.RequestLocationBlock -> {
                requestLocationBlock()
            }
            is DiaryIntent.AddBlock -> {
                // HeadingBlock 은 단일만 허용 (두 번째 추가는 무시 + 토스트)
                if (intent.block is ContentBlock.HeadingBlock && _state.value.hasHeadingBlock) {
                    sendEffect(DiaryEffect.ShowToast("제목 블록은 한 글에 1개만 추가할 수 있어요."))
                    return
                }
                _state.update { it.copy(draftBlocks = it.draftBlocks + intent.block) }
            }
            is DiaryIntent.InsertBlock -> {
                _state.update { current ->
                    val idx = intent.index.coerceIn(0, current.draftBlocks.size)
                    val newList = current.draftBlocks.toMutableList()
                    newList.add(idx, intent.block)
                    current.copy(draftBlocks = newList)
                }
            }
            is DiaryIntent.UpdateBlockText -> {
                _state.update { current ->
                    current.copy(
                        draftBlocks = current.draftBlocks.map { block ->
                            if (block.id != intent.blockId) return@map block
                            when (block) {
                                is ContentBlock.HeadingBlock -> block.copy(text = intent.text, formatting = intent.formatting)
                                is ContentBlock.TextBlock -> block.copy(text = intent.text, formatting = intent.formatting)
                                is ContentBlock.QuoteBlock -> block.copy(text = intent.text, formatting = intent.formatting)
                                else -> block
                            }
                        }
                    )
                }
            }
            is DiaryIntent.UpdateBlockCaption -> {
                _state.update { current ->
                    current.copy(
                        draftBlocks = current.draftBlocks.map { block ->
                            if (block is ContentBlock.ImageBlock && block.id == intent.blockId) {
                                block.copy(caption = intent.caption)
                            } else block
                        }
                    )
                }
            }
            is DiaryIntent.RemoveBlock -> {
                val removed = _state.value.draftBlocks.firstOrNull { it.id == intent.blockId }
                if (removed is ContentBlock.ImageBlock) {
                    imageStore.delete(removed.relativePath)
                }
                _state.update { current ->
                    current.copy(draftBlocks = current.draftBlocks.filter { it.id != intent.blockId })
                }
            }
            is DiaryIntent.MoveBlock -> {
                _state.update { current ->
                    val list = current.draftBlocks.toMutableList()
                    val idx = list.indexOfFirst { it.id == intent.blockId }
                    if (idx < 0) return@update current
                    val newIdx = (idx + intent.direction).coerceIn(0, list.size - 1)
                    if (newIdx == idx) return@update current
                    val item = list.removeAt(idx)
                    list.add(newIdx, item)
                    current.copy(draftBlocks = list)
                }
            }
            is DiaryIntent.CopyBlockToClipboard -> {
                copyBlockToClipboard(intent.blockId)
            }
            is DiaryIntent.TranslateBlock -> {
                translateBlockToKorean(intent.blockId)
            }

            // ===== 표 =====
            is DiaryIntent.UpdateTableCell -> {
                _state.update { current ->
                    current.copy(
                        draftBlocks = current.draftBlocks.map { block ->
                            if (block !is ContentBlock.TableBlock || block.id != intent.blockId) return@map block
                            val r = intent.row.coerceIn(0, block.rows - 1)
                            val c = intent.col.coerceIn(0, block.cols - 1)
                            val newCells = block.cells.mapIndexed { ri, row ->
                                if (ri != r) row
                                else row.mapIndexed { ci, cell -> if (ci == c) intent.text else cell }
                            }
                            block.copy(cells = newCells)
                        }
                    )
                }
            }
            is DiaryIntent.AddTableRow -> {
                _state.update { current ->
                    current.copy(
                        draftBlocks = current.draftBlocks.map { block ->
                            if (block !is ContentBlock.TableBlock || block.id != intent.blockId) return@map block
                            if (block.rows >= TABLE_MAX_ROWS) return@map block
                            val newRow = List(block.cols) { "" }
                            block.copy(rows = block.rows + 1, cells = block.cells + listOf(newRow))
                        }
                    )
                }
            }
            is DiaryIntent.RemoveTableRow -> {
                _state.update { current ->
                    current.copy(
                        draftBlocks = current.draftBlocks.map { block ->
                            if (block !is ContentBlock.TableBlock || block.id != intent.blockId) return@map block
                            if (block.rows <= 1) return@map block
                            val r = intent.rowIndex.coerceIn(0, block.rows - 1)
                            val newCells = block.cells.toMutableList().also { it.removeAt(r) }
                            block.copy(rows = block.rows - 1, cells = newCells)
                        }
                    )
                }
            }
            is DiaryIntent.AddTableColumn -> {
                _state.update { current ->
                    current.copy(
                        draftBlocks = current.draftBlocks.map { block ->
                            if (block !is ContentBlock.TableBlock || block.id != intent.blockId) return@map block
                            if (block.cols >= TABLE_MAX_COLS) return@map block
                            val newCells = block.cells.map { row -> row + "" }
                            block.copy(cols = block.cols + 1, cells = newCells)
                        }
                    )
                }
            }
            is DiaryIntent.RemoveTableColumn -> {
                _state.update { current ->
                    current.copy(
                        draftBlocks = current.draftBlocks.map { block ->
                            if (block !is ContentBlock.TableBlock || block.id != intent.blockId) return@map block
                            if (block.cols <= 1) return@map block
                            val c = intent.colIndex.coerceIn(0, block.cols - 1)
                            val newCells = block.cells.map { row ->
                                row.toMutableList().also { it.removeAt(c) }
                            }
                            block.copy(cols = block.cols - 1, cells = newCells)
                        }
                    )
                }
            }

            // ===== 이미지 픽업/촬영 =====
            is DiaryIntent.ImagesPicked -> {
                if (intent.uris.isNotEmpty()) importPickedImages(intent.uris)
            }
            is DiaryIntent.CameraImageCaptured -> {
                importCapturedImage(intent.capturedUri)
            }

            // ===== 플래너 및 목표 기록 =====
            is DiaryIntent.SelectDate -> {
                _state.update { it.copy(selectedDateString = intent.dateString) }
            }
            is DiaryIntent.ChangeTab -> {
                _state.update { it.copy(activeTab = intent.tab) }
            }
            is DiaryIntent.AddGoal -> {
                _state.update { current ->
                    val updatedGoals = current.goals + Goal(text = intent.text, category = intent.category)
                    plannerRepository.saveGoals(updatedGoals)
                    current.copy(goals = updatedGoals)
                }
            }
            is DiaryIntent.ToggleGoal -> {
                _state.update { current ->
                    val targetGoal = current.goals.find { it.id == intent.id }
                    val willBeCompleted = targetGoal?.isCompleted == false
                    val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    
                    val updatedGoals = current.goals.map {
                        if (it.id == intent.id) {
                            it.copy(
                                isCompleted = willBeCompleted,
                                completedDateString = if (willBeCompleted) todayStr else null,
                                aiCongratulationText = if (willBeCompleted) "AI 멘토가 응원 메시지를 작성하는 중입니다... 📝" else null
                            )
                        } else it
                    }
                    plannerRepository.saveGoals(updatedGoals)
                    
                    if (willBeCompleted && targetGoal != null) {
                        generateAiCongratulation(targetGoal.id, targetGoal.text)
                    }
                    
                    current.copy(goals = updatedGoals)
                }
            }
            is DiaryIntent.DeleteGoal -> {
                _state.update { current ->
                    val updatedGoals = current.goals.filter { it.id != intent.id }
                    plannerRepository.saveGoals(updatedGoals)
                    current.copy(goals = updatedGoals)
                }
            }
            is DiaryIntent.AddPlannerTask -> {
                _state.update { current ->
                    val newTasks = if (intent.isRepeat && !intent.repeatEndDateString.isNullOrBlank() && intent.repeatDays.isNotEmpty()) {
                        try {
                            val start = java.time.LocalDate.parse(intent.dateString)
                            val end = java.time.LocalDate.parse(intent.repeatEndDateString)
                            val seriesId = java.util.UUID.randomUUID().toString()
                            var curr = start
                            val list = mutableListOf<PlannerTask>()
                            while (!curr.isAfter(end)) {
                                val dayOfWeekVal = curr.dayOfWeek.value // 1 (월) ~ 7 (일)
                                if (intent.repeatDays.contains(dayOfWeekVal)) {
                                    list.add(
                                        PlannerTask(
                                            text = intent.text,
                                            dateString = curr.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                                            startTime = intent.startTime,
                                            endTime = intent.endTime,
                                            location = intent.location,
                                            seriesId = seriesId
                                        )
                                    )
                                }
                                curr = curr.plusDays(1)
                            }
                            list
                        } catch (e: Exception) {
                            listOf(
                                PlannerTask(
                                    text = intent.text,
                                    dateString = intent.dateString,
                                    startTime = intent.startTime,
                                    endTime = intent.endTime,
                                    location = intent.location
                                )
                            )
                        }
                    } else {
                        listOf(
                            PlannerTask(
                                text = intent.text,
                                dateString = intent.dateString,
                                startTime = intent.startTime,
                                endTime = intent.endTime,
                                location = intent.location
                            )
                        )
                    }
                    val updatedTasks = current.plannerTasks + newTasks
                    plannerRepository.saveTasks(updatedTasks)
                    current.copy(plannerTasks = updatedTasks)
                }
            }
            is DiaryIntent.TogglePlannerTask -> {
                _state.update { current ->
                    val updatedTasks = current.plannerTasks.map {
                        if (it.id == intent.id) it.copy(isCompleted = !it.isCompleted) else it
                    }
                    plannerRepository.saveTasks(updatedTasks)
                    current.copy(plannerTasks = updatedTasks)
                }
            }
            is DiaryIntent.DeletePlannerTask -> {
                _state.update { current ->
                    val updatedTasks = current.plannerTasks.filter { it.id != intent.id }
                    plannerRepository.saveTasks(updatedTasks)
                    current.copy(plannerTasks = updatedTasks)
                }
            }
            is DiaryIntent.DeletePlannerTaskSeries -> {
                _state.update { current ->
                    val updatedTasks = current.plannerTasks.filter { it.seriesId != intent.seriesId }
                    plannerRepository.saveTasks(updatedTasks)
                    current.copy(plannerTasks = updatedTasks)
                }
            }
            is DiaryIntent.SuggestPlannerTask -> {
                suggestPlannerTaskName(
                    startTime = intent.startTime,
                    endTime = intent.endTime,
                    location = intent.location,
                    isRepeat = intent.isRepeat,
                    repeatDays = intent.repeatDays,
                    repeatEndDateString = intent.repeatEndDateString
                )
            }
            is DiaryIntent.ClearSuggestedPlannerTask -> {
                _state.update { it.copy(suggestedPlannerTaskText = null) }
            }
            is DiaryIntent.RequestBriefing -> {
                requestBriefing(intent.tab)
            }

            // ===== 온디바이스 AI 챗봇 =====
            is DiaryIntent.SendChatMessage -> {
                runOnDeviceChat(intent.text)
            }
            is DiaryIntent.ClearChatHistory -> {
                llmEngine?.clearChat()
                _state.update { it.copy(chatMessages = emptyList()) }
            }
        }
    }

    private fun sendEffect(effect: DiaryEffect) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }

    /**
     * 첫 페이지 메타 로드. v3.1 페이지네이션: DiaryMeta 만 50건씩.
     * 검색 모드/ClearSearch 시 호출되며 검색 결과로 채운다.
     */
    private fun loadFirstDiaryPage() {
        viewModelScope.launch {
            val pageSize = _state.value.diaryPageSize
            val total = withContext(Dispatchers.IO) { repository.daoCount() }
            val page1 = withContext(Dispatchers.IO) { repository.pagedMetas(pageSize, 0) }
            val hasMore = page1.size < total
            _state.update {
                it.copy(
                    diaries = page1,
                    diaryPageCount = 1,
                    diaryHasMore = hasMore,
                    diaryTotalCount = total,
                    isSearching = false,
                    searchQuery = ""
                )
            }
        }
    }

    /**
     * 다음 페이지 추가 로드. LazyColumn 끝에서 호출.
     */
    private fun loadMoreDiaryPage() {
        val s = _state.value
        if (!s.diaryHasMore || s.isLoadingMoreDiaries || s.isSearching) return
        _state.update { it.copy(isLoadingMoreDiaries = true) }
        viewModelScope.launch {
            val pageSize = s.diaryPageSize
            val nextOffset = s.diaryPageCount * pageSize
            val more = withContext(Dispatchers.IO) { repository.pagedMetas(pageSize, nextOffset) }
            val newList = s.diaries + more
            val total = s.diaryTotalCount
            val hasMore = newList.size < total
            _state.update {
                it.copy(
                    diaries = newList,
                    diaryPageCount = s.diaryPageCount + 1,
                    diaryHasMore = hasMore,
                    isLoadingMoreDiaries = false
                )
            }
        }
    }

    /**
     * FTS5 기반 검색 실행. 결과를 [DiaryState.diaries] 에 채워넣고
     * [DiaryState.searchQuery] 에 원본 쿼리를 보관한다.
     * 검색 모드일 때 페이지네이션은 일시 중지된다 ([loadMoreDiaryPage] 가드).
     */
    private fun handleSearch(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isBlank()) {
            loadFirstDiaryPage()
            return
        }
        _state.update { it.copy(isSearching = true, searchQuery = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            val hits = withContext(Dispatchers.IO) { repository.searchDiaries(q, limit = 50) }
            // FTS hit 의 메타로 DiaryMeta 리스트 채우기
            val metas = withContext(Dispatchers.IO) {
                hits.mapNotNull { hit -> repository.metaOf(hit.id) }
            }
            _state.update { it.copy(isSearching = false, diaries = metas) }
        }
    }

    /**
     * 외부에서 픽업된 이미지 URI 를 내부 저장소로 복사하고 ImageBlock 으로 추가합니다.
     */
    /**
     * 다중 픽업 이미지(1..N) 를 순차적으로 내부 저장소로 가져와 ImageBlock 들을 본문 끝에 append 합니다.
     * 부분 실패는 카운트해서 마지막에 토스트로 요약 알립니다.
     */
    private fun importPickedImages(uris: List<Uri>) {
        if (_state.value.isImportingImage) return
        _state.update { it.copy(isImportingImage = true) }
        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            for (uri in uris) {
                val result = imageStore.importFromUri(uri)
                result.onSuccess { relPath ->
                    successCount++
                    val block = ContentBlock.ImageBlock(relativePath = relPath)
                    _state.update {
                        it.copy(draftBlocks = it.draftBlocks + block)
                    }
                }.onFailure { e ->
                    failCount++
                    android.util.Log.w("DiaryViewModel", "Image import failed: uri=$uri err=${e.message}")
                }
            }
            _state.update { it.copy(isImportingImage = false) }
            when {
                failCount == 0 -> sendEffect(
                    DiaryEffect.ShowToast(
                        if (successCount == 1) "이미지를 추가했어요."
                        else "이미지 ${successCount}장을 추가했어요."
                    )
                )
                successCount == 0 -> sendEffect(
                    DiaryEffect.ShowToast("이미지를 가져오지 못했어요.")
                )
                else -> sendEffect(
                    DiaryEffect.ShowToast("${successCount}장 추가, ${failCount}장 실패")
                )
            }
        }
    }

    /**
     * 카메라 촬영의 [content://] URI 를 ContentResolver 로 읽어 내부 저장소로 복사한 뒤 ImageBlock 으로 추가합니다.
     */
    private fun importCapturedImage(capturedUri: Uri) {
        if (_state.value.isImportingImage) return
        _state.update { it.copy(isImportingImage = true) }
        viewModelScope.launch {
            imageStore.importFromUri(capturedUri)
                .onSuccess { relPath ->
                    val block = ContentBlock.ImageBlock(relativePath = relPath)
                    _state.update {
                        it.copy(
                            draftBlocks = it.draftBlocks + block,
                            isImportingImage = false
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isImportingImage = false) }
                    sendEffect(DiaryEffect.ShowToast("카메라 이미지를 저장하지 못했어요: ${e.message}"))
                }
        }
    }

    /**
     * 카메라 촬영용 임시 URI 를 생성하고 UI 측에 촬영 런처를 띄우도록 알립니다.
     * [com.grepiu.aidiary.mvi.effect.DiaryEffect.LaunchCamera] 로 uri 를 전달합니다.
     */
    fun requestCameraCapture() {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            sendEffect(DiaryEffect.RequestCameraPermission)
            return
        }
        val tempFile = File(getApplication<Application>().cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        if (!tempFile.exists()) tempFile.createNewFile()
        val authority = "${getApplication<Application>().packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(getApplication(), authority, tempFile)
        sendEffect(DiaryEffect.LaunchCamera(uri))
    }

    /**
     * 현재 위치를 가져오기 위한 권한 체크 및 요청을 수행하고, 권한이 있을 경우 위치 블록을 추가합니다.
     */
    fun requestLocationBlock() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted && !coarseLocationGranted) {
            sendEffect(DiaryEffect.RequestLocationPermission)
            return
        }

        startLocationFetchFlow()
    }

    /**
     * 화면에 위치 정보를 가져오는 중임을 나타내는 로딩 상태의 LocationBlock을 먼저 '즉시 추가'하고,
     * 백그라운드 코루틴으로 위경도 수신 및 역지오코딩을 수행하여 해당 블록을 비동기적으로 업데이트합니다.
     */
    fun startLocationFetchFlow() {
        val blockId = java.util.UUID.randomUUID().toString()
        val loadingBlock = ContentBlock.LocationBlock(
            id = blockId,
            latitude = 0.0,
            longitude = 0.0,
            address = "위치 정보를 가져오는 중..."
        )

        // 1. 로딩 상태 블록 먼저 즉시 화면에 주입
        _state.update { current ->
            current.copy(draftBlocks = current.draftBlocks + loadingBlock)
        }

        // 2. 비동기 위치 탐색 및 해당 블록 업데이트 진행
        fetchLocationAndUpdateBlock(blockId)
    }

    /**
     * 기기의 GPS/Network 프로바이더를 사용하여 현재 위경도 좌표를 조회하고,
     * Geocoder API를 통해 한국어 주소로 변환한 뒤 특정 ID의 LocationBlock 정보를 업데이트합니다.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun fetchLocationAndUpdateBlock(blockId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val locationManager = getApplication<Application>()
                    .getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager

                // 사용 가능한 모든 프로바이더 조회
                val providers = locationManager.getProviders(true)
                var bestLocation: android.location.Location? = null

                // 1단계: 실시간으로 GPS 프로바이더를 사용하여 최고 정확도의 위치 획득 시도 (QUALITY_HIGH_ACCURACY, 4초 타임아웃)
                if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                    val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                    val locationRequest = android.location.LocationRequest.Builder(0)
                        .setQuality(android.location.LocationRequest.QUALITY_HIGH_ACCURACY)
                        .build()
                    val loc = withTimeoutOrNull(4000) {
                        suspendCancellableCoroutine<android.location.Location?> { cont ->
                            val cancellationSignal = android.os.CancellationSignal()
                            cont.invokeOnCancellation {
                                cancellationSignal.cancel()
                            }
                            try {
                                locationManager.getCurrentLocation(
                                    android.location.LocationManager.GPS_PROVIDER,
                                    locationRequest,
                                    cancellationSignal,
                                    executor
                                ) { resultLoc ->
                                    if (cont.isActive) {
                                        cont.resume(resultLoc)
                                    }
                                }
                            } catch (e: Exception) {
                                if (cont.isActive) {
                                    cont.resume(null)
                                }
                            }
                        }
                    }
                    if (loc != null) {
                        bestLocation = loc
                    }
                }

                // 2단계: GPS 획득 실패 시 네트워크 프로바이더를 통해 고정밀 위치 획득 시도 (3초 타임아웃)
                if (bestLocation == null) {
                    if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                        val locationRequest = android.location.LocationRequest.Builder(0)
                            .setQuality(android.location.LocationRequest.QUALITY_HIGH_ACCURACY)
                            .build()
                        val loc = withTimeoutOrNull(3000) {
                            suspendCancellableCoroutine<android.location.Location?> { cont ->
                                val cancellationSignal = android.os.CancellationSignal()
                                cont.invokeOnCancellation {
                                    cancellationSignal.cancel()
                                }
                                try {
                                    locationManager.getCurrentLocation(
                                        android.location.LocationManager.NETWORK_PROVIDER,
                                        locationRequest,
                                        cancellationSignal,
                                        executor
                                    ) { resultLoc ->
                                        if (cont.isActive) {
                                            cont.resume(resultLoc)
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (cont.isActive) {
                                        cont.resume(null)
                                    }
                                }
                            }
                        }
                        if (loc != null) {
                            bestLocation = loc
                        }
                    }
                }

                // 3단계: 실시간 위치 측정이 모두 안 되면, 마지막으로 기록된 캐시 위치 중 가장 높은 정확도의 값 사용
                if (bestLocation == null) {
                    for (provider in providers) {
                        val loc = locationManager.getLastKnownLocation(provider) ?: continue
                        if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                            bestLocation = loc
                        }
                    }
                }

                if (bestLocation != null) {
                    val lat = bestLocation.latitude
                    val lng = bestLocation.longitude

                    // Geocoder API를 통해 좌표 -> 주소 변환
                    val geocoder = android.location.Geocoder(getApplication(), Locale.KOREAN)
                    var addressStr = ""

                    try {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lng, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            addressStr = address.getAddressLine(0) ?: ""
                            // 대한민국 주소에 붙는 불필요한 국가명 제거 가공
                            if (addressStr.startsWith("대한민국 ")) {
                                addressStr = addressStr.removePrefix("대한민국 ")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (addressStr.isBlank()) {
                        addressStr = "위도: ${String.format(Locale.US, "%.5f", lat)}, 경도: ${String.format(Locale.US, "%.5f", lng)}"
                    }

                    val finalAddress = addressStr
                    withContext(Dispatchers.Main) {
                        _state.update { current ->
                            current.copy(
                                draftBlocks = current.draftBlocks.map { block ->
                                    if (block.id == blockId && block is ContentBlock.LocationBlock) {
                                        block.copy(latitude = lat, longitude = lng, address = finalAddress)
                                    } else block
                                }
                            )
                        }
                        sendEffect(DiaryEffect.ShowToast("위치 정보를 성공적으로 가져왔습니다."))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _state.update { current ->
                            current.copy(
                                draftBlocks = current.draftBlocks.map { block ->
                                    if (block.id == blockId && block is ContentBlock.LocationBlock) {
                                        block.copy(address = "위치를 가져올 수 없습니다. (GPS 확인 필요)")
                                    } else block
                                }
                            )
                        }
                        sendEffect(DiaryEffect.ShowToast("위치 정보를 수신하지 못했습니다."))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _state.update { current ->
                        current.copy(
                            draftBlocks = current.draftBlocks.map { block ->
                                if (block.id == blockId && block is ContentBlock.LocationBlock) {
                                    block.copy(address = "위치 탐색 오류: ${e.localizedMessage}")
                                } else block
                            }
                        )
                    }
                    sendEffect(DiaryEffect.ShowToast("위치 정보를 가져오는 중 오류가 발생했습니다."))
                }
            }
        }
    }

    /**
     * 로컬 저장소 및 에셋의 모델 구비 여부를 점검하고 엔진 바인딩을 추진합니다.
     */
    private suspend fun ensureModelReady() {
        if (downloader.isModelDownloaded()) {
            initLLM("downloaded")
            return
        }

        if (downloader.isModelInAssets()) {
            _state.update { it.copy(isDownloadingModel = true, modelDownloadProgress = 0f, modelDownloadSizeText = "에셋 복사 중...") }
            downloader.copyFromAssets { bytesWritten, totalBytes ->
                val progress = if (totalBytes > 0) bytesWritten.toFloat() / totalBytes else 0f
                val sizeText = "${downloader.toHumanReadableSize(bytesWritten)} / ${downloader.toHumanReadableSize(totalBytes)}"
                _state.update { it.copy(modelDownloadProgress = progress, modelDownloadSizeText = sizeText) }
            }.onSuccess {
                initLLM("assets")
            }.onFailure { e ->
                sendEffect(DiaryEffect.ShowToast("에셋 AI 모델 복사 실패: ${e.message}"))
                _state.update { it.copy(isDownloadingModel = false, modelDownloadSizeText = null) }
            }
            return
        }

        // 수동 로컬 복사 검사 (/sdcard/Download/gemma-4-E2B-it.litertlm)
        val localFile = File("/sdcard/Download/gemma-4-E2B-it.litertlm")
        if (localFile.exists()) {
            _state.update { it.copy(isDownloadingModel = true, modelDownloadProgress = 0.5f, modelDownloadSizeText = "로컬 파일 복사 중...") }
            try {
                withContext(Dispatchers.IO) {
                    downloader.copyFromLocalSource(localFile.absolutePath)
                }
                initLLM("local")
                return
            } catch (e: Exception) {
                sendEffect(DiaryEffect.ShowToast("로컬 파일 복사 실패: ${e.message}"))
                _state.update { it.copy(isDownloadingModel = false, modelDownloadSizeText = null) }
            }
        }

        // 디바이스 온디바이스 AI 사양 체크
        val capability = DeviceCapabilityChecker.check(getApplication())
        if (!capability.isSupported) {
            _state.update {
                it.copy(
                    isDeviceUnsupported = true,
                    deviceUnsupportedReason = capability.reason
                )
            }
            return
        }

        // 다운로드 유도를 위한 팝업 표시 설정
        _state.update {
            it.copy(
                showDownloadNotice = true,
                isLowRamDevice = downloader.isLowRamDevice()
            )
        }
    }

    /**
     * 모델 웹 다운로드 시작
     */
    private fun startModelDownload() {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            // Wi-Fi 상태가 아닌데 경고창을 통과 안했다면 경고 먼저 띄움
            if (!downloader.isWifiConnected() && !_state.value.showWifiWarning && !_state.value.isDownloadingModel) {
                _state.update { it.copy(showWifiWarning = true, showDownloadNotice = false) }
                return@launch
            }

            _state.update { it.copy(isDownloadingModel = true, modelDownloadProgress = 0f, modelDownloadSizeText = "연결 중...", showDownloadNotice = false, showWifiWarning = false) }

            try {
                val result = downloader.downloadModel(MODEL_DOWNLOAD_URL) { bytesRead, totalBytes ->
                    val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                    val sizeText = "${downloader.toHumanReadableSize(bytesRead)} / ${if (totalBytes > 0) downloader.toHumanReadableSize(totalBytes) else "알 수 없음"}"
                    _state.update {
                        it.copy(
                            modelDownloadProgress = progress,
                            modelDownloadSizeText = sizeText
                        )
                    }
                }

                if (result.isSuccess) {
                    _state.update { it.copy(isDownloadingModel = false, modelDownloadSizeText = null) }
                    initLLM("download")
                } else {
                    val error = result.exceptionOrNull()
                    if (error is CancellationException) {
                        return@launch
                    }
                    sendEffect(DiaryEffect.ShowToast("다운로드 중단: ${error?.message}"))
                    _state.update {
                        it.copy(
                            isDownloadingModel = false,
                            modelDownloadSizeText = null,
                            showDownloadNotice = true
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                sendEffect(DiaryEffect.ShowToast("다운로드 에러: ${e.message}"))
                _state.update {
                    it.copy(isDownloadingModel = false, modelDownloadSizeText = null, showDownloadNotice = true)
                }
            }
        }
    }

    /**
     * LiteRT-LM 엔진 물리 로드
     */
    private suspend fun initLLM(source: String) {
        try {
            _state.update { it.copy(isModelInitializing = true, isDownloadingModel = false, showDownloadNotice = false, showWifiWarning = false) }
            
            val modelFile = downloader.getModelFile()
            if (!modelFile.exists() || modelFile.length() < 2.3 * 1024 * 1024 * 1024) {
                throw java.io.IOException("모델 파일이 없거나 크기가 손상되었습니다.")
            }

            val modelPath = modelFile.absolutePath

            // OpenCL 라이브러리 사전 로드 (tarotxr은 3D glb 렌더링으로 자동 로드됨)
            withContext(Dispatchers.IO) {
                try {
                    System.loadLibrary("OpenCL")
                    Log.d("DiaryViewModel", "OpenCL library preloaded successfully")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w("DiaryViewModel", "OpenCL preload failed, will fallback to CPU: ${e.message}")
                }
            }

            llmEngine = withContext(Dispatchers.IO) {
                DiaryLLMEngine.create(getApplication(), modelPath)
            }.apply {
                // 저장 시 자동 호출되는 analyzeAndTag 는 내부에서 토큰을 흘려보내지만,
                // 별도 UI 스트리밍은 하지 않고 최종 결과만 사용합니다.
                onTokenReceived = null
            }
            _state.update { it.copy(isModelReady = true, isModelInitializing = false) }
            Log.d("DiaryViewModel", "LLM initialized successfully from source: $source")
        } catch (e: Exception) {
            downloader.deleteModelFile() // 손상 파일 삭제 유도
            _state.update { it.copy(isModelInitializing = false, isModelReady = false, showDownloadNotice = true) }
            sendEffect(DiaryEffect.ShowToast("AI 초기화 실패. 모델을 다시 다운로드합니다: ${e.message}"))
        }
    }

    // ===== 작성 보조 AI 액션 =====

    /** 모델이 준비되어 있고 동시 분석 작업이 없는지 검사하는 공통 게이트. */
    private fun requireReadyModel(toast: String = "AI 모델이 아직 준비되지 않았습니다."): Boolean {
        if (!_state.value.isModelReady) {
            sendEffect(DiaryEffect.ShowToast(toast))
            return false
        }
        return true
    }

    private fun suggestTitleFromBody() {
        val state = _state.value
        val plain = state.draftPlainText
        if (plain.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("제목 추천을 위해 본문을 먼저 작성해주세요."))
            return
        }
        if (!requireReadyModel()) return
        if (state.isSuggestingTitle) return

        _state.update { it.copy(isSuggestingTitle = true) }
        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val title = engine.suggestTitle(
                    content = plain,
                    currentTitle = state.draftTitle,
                    contentTypeLabel = state.draftContentType.label
                )
                if (title.isNotBlank()) {
                    applyDraftTitle(title)
                    sendEffect(DiaryEffect.ShowToast("AI 추천 제목을 적용했어요."))
                } else {
                    sendEffect(DiaryEffect.ShowToast("제목을 만들지 못했어요. 다시 시도해 주세요."))
                }
            } catch (e: Exception) {
                sendEffect(DiaryEffect.ShowToast("AI 제목 추천 오류: ${e.message}"))
            } finally {
                _state.update { it.copy(isSuggestingTitle = false) }
            }
        }
    }

    /**
     * AI 추천 제목을 상단 입력란 (draftTitle) 에 적용합니다.
     */
    private fun applyDraftTitle(title: String) {
        _state.update { current -> current.copy(draftTitle = title) }
    }

    /**
     * 선택된 날짜 + 사용자가 지금 입력 중인 시간/장소/반복 조건(1순위),
     * 같은 날 기존 계획/장기 목표/최근 일기(2~4순위) 를 종합해
     * 오늘의 플래너 할 일 1건을 LLM 으로 추천하도록 요청합니다.
     * 결과는 [DiaryState.suggestedPlannerTaskText] 에 1회성으로 저장되어
     * UI 가 [DiaryIntent.ClearSuggestedPlannerTask] 를 보내면 비워집니다.
     */
    private fun suggestPlannerTaskName(
        startTime: String?,
        endTime: String?,
        location: String?,
        isRepeat: Boolean,
        repeatDays: List<Int>,
        repeatEndDateString: String?
    ) {
        val state = _state.value
        if (!requireReadyModel()) return
        if (state.isSuggestingPlannerTask) return

        val context = buildPlannerTaskContext(
            state = state,
            startTime = startTime,
            endTime = endTime,
            location = location,
            isRepeat = isRepeat,
            repeatDays = repeatDays,
            repeatEndDateString = repeatEndDateString
        )
        if (context.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("추천을 위한 컨텍스트가 부족해요."))
            return
        }

        _state.update { it.copy(isSuggestingPlannerTask = true) }
        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val suggestion = engine.suggestPlannerTaskName(context)
                if (suggestion.isNotBlank()) {
                    _state.update { it.copy(suggestedPlannerTaskText = suggestion) }
                    sendEffect(DiaryEffect.ShowToast("AI 추천 계획을 입력했어요. 필요하면 수정하세요."))
                } else {
                    sendEffect(DiaryEffect.ShowToast("추천할 계획을 만들지 못했어요. 다시 시도해 주세요."))
                }
            } catch (e: Exception) {
                sendEffect(DiaryEffect.ShowToast("AI 플래너 추천 오류: ${e.message}"))
            } finally {
                _state.update { it.copy(isSuggestingPlannerTask = false) }
            }
        }
    }

    /**
     * LLM 에게 줄 플래너 추천 컨텍스트 문자열을 만듭니다.
     * 우선순위는 다음과 같습니다.
     *  - 1순위: 사용자가 지금 입력 중인 조건 (날짜, 시작/종료 시간, 장소, 반복 요일·종료일)
     *  - 2순위: 같은 날 이미 등록된 계획(시간·장소 포함)
     *  - 3순위: 미완료 장기 목표 (최대 5건)
     *  - 4순위: 최근 일기 평문 (최대 3건, 각 120자)
     */
    private fun buildPlannerTaskContext(
        state: DiaryState,
        startTime: String?,
        endTime: String?,
        location: String?,
        isRepeat: Boolean,
        repeatDays: List<Int>,
        repeatEndDateString: String?
    ): String {
        val dayOfWeek = try {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREAN)
                .parse(state.selectedDateString)
            if (date != null) {
                java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN).format(date)
            } else state.selectedDateString
        } catch (e: Exception) {
            state.selectedDateString
        }

        // 1순위: 사용자가 지금 입력 중인 조건
        val currentInput = buildString {
            append("- 날짜: $dayOfWeek\n")
            val hasTime = !startTime.isNullOrBlank() || !endTime.isNullOrBlank()
            if (hasTime) {
                val timeText = buildString {
                    if (!startTime.isNullOrBlank()) append(startTime)
                    if (!endTime.isNullOrBlank()) {
                        if (isNotEmpty()) append(" ~ ")
                        append(endTime)
                    }
                }
                append("- 시간: $timeText\n")
            }
            if (!location.isNullOrBlank()) {
                append("- 장소: $location\n")
            }
            if (isRepeat && repeatDays.isNotEmpty()) {
                val dayNames = repeatDays.sorted()
                    .joinToString(", ") { plannerDayOfWeekName(it) }
                append("- 반복 요일: $dayNames\n")
                if (!repeatEndDateString.isNullOrBlank()) {
                    append("- 반복 종료일: $repeatEndDateString\n")
                }
            }
        }

        // 2순위: 같은 날 이미 등록된 계획
        val existingTasks = state.plannerTasks
            .filter { it.dateString == state.selectedDateString }
            .joinToString("\n") { task ->
                val timePart = buildString {
                    if (!task.startTime.isNullOrBlank()) append(" ${task.startTime}")
                    if (!task.endTime.isNullOrBlank()) append("~${task.endTime}")
                }
                val locPart = if (!task.location.isNullOrBlank()) " (장소: ${task.location})" else ""
                "- ${task.text}$timePart$locPart"
            }

        // 3순위: 미완료 장기 목표
        val openGoals = state.goals
            .filter { !it.isCompleted }
            .take(5)
            .joinToString("\n") { "- ${it.text}" }

        // 4순위: 최근 일기 평문 (메타의 contentPreview 사용, 본문 전체는 lazy)
        val recentDiaries = state.diaries
            .take(3)
            .joinToString("\n") { entry ->
                val plain = entry.contentPreview.take(120)
                if (plain.isBlank()) "- (제목만: ${entry.title})" else "- ${entry.title}: $plain"
            }

        return buildString {
            append("[1순위: 사용자가 지금 입력 중인 조건]\n")
            append(currentInput)
            if (existingTasks.isNotBlank()) {
                append("\n[2순위: 이 날 이미 등록된 계획]\n$existingTasks\n")
            }
            if (openGoals.isNotBlank()) {
                append("\n[3순위: 아직 완료하지 않은 장기 목표]\n$openGoals\n")
            }
            if (recentDiaries.isNotBlank()) {
                append("\n[4순위: 최근 일기]\n$recentDiaries\n")
            }
            append("\n위 1순위 조건을 가장 우선으로 반영해 1개의 새 계획을 한국어 1줄로 추천해 주세요. ")
            append("시간·장소·반복 요일이 정해져 있다면 그에 맞게 어울리는 내용으로, ")
            append("기존 계획과 중복되지 않게 해주세요.")
        }
    }

    /** java.time.DayOfWeek 1(월)~7(일) → 한글 요일명 */
    private fun plannerDayOfWeekName(value: Int): String = when (value) {
        1 -> "월"
        2 -> "화"
        3 -> "수"
        4 -> "목"
        5 -> "금"
        6 -> "토"
        7 -> "일"
        else -> "?"
    }

    /**
     * 탭별 AI 브리핑을 생성/재생성합니다.
     * 동일 탭에서 이미 진행 중이면 무시, 다른 탭은 병행 가능.
     * v2: 기존 브리핑이 있으면 "직전 브리핑" 으로 컨텍스트에 포함해 추세 비교를 유도.
     */
    private fun requestBriefing(tab: String) {
        val state = _state.value
        if (!requireReadyModel()) return

        val alreadyRunning = when (tab) {
            "DIARY" -> state.isBriefingDiary
            "PLANNER" -> state.isBriefingPlanner
            "GOALS" -> state.isBriefingGoals
            else -> {
                sendEffect(DiaryEffect.ShowToast("지원하지 않는 브리핑 탭입니다."))
                return
            }
        }
        if (alreadyRunning) return

        val baseContext = buildBriefingContext(tab, state)
        if (baseContext.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("브리핑을 위한 데이터가 부족해요."))
            return
        }
        val prev = when (tab) {
            "DIARY" -> state.diaryBriefing
            "PLANNER" -> state.plannerBriefing
            "GOALS" -> state.goalsBriefing
            else -> null
        }
        val context = if (!prev.isNullOrBlank()) {
            "[직전 브리핑]\n${prev.trim()}\n\n$baseContext"
        } else baseContext

        _state.update {
            when (tab) {
                "DIARY" -> it.copy(isBriefingDiary = true)
                "PLANNER" -> it.copy(isBriefingPlanner = true)
                "GOALS" -> it.copy(isBriefingGoals = true)
                else -> it
            }
        }

        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val text = engine.generateBriefing(tab, context)
                _state.update { current ->
                    when (tab) {
                        "DIARY" -> current.copy(diaryBriefing = text, isBriefingDiary = false)
                        "PLANNER" -> current.copy(plannerBriefing = text, isBriefingPlanner = false)
                        "GOALS" -> current.copy(goalsBriefing = text, isBriefingGoals = false)
                        else -> current
                    }
                }
                if (text.isBlank()) {
                    sendEffect(DiaryEffect.ShowToast("브리핑을 만들지 못했어요. 다시 시도해 주세요."))
                }
            } catch (e: Exception) {
                _state.update { current ->
                    when (tab) {
                        "DIARY" -> current.copy(isBriefingDiary = false)
                        "PLANNER" -> current.copy(isBriefingPlanner = false)
                        "GOALS" -> current.copy(isBriefingGoals = false)
                        else -> current
                    }
                }
                sendEffect(DiaryEffect.ShowToast("AI 브리핑 오류: ${e.message}"))
            }
        }
    }

    /**
     * 탭별 브리핑 LLM 컨텍스트 문자열을 만듭니다.
     */
    private fun buildBriefingContext(tab: String, state: DiaryState): String = when (tab) {
        "DIARY" -> buildDiaryBriefingContext(state)
        "PLANNER" -> buildPlannerBriefingContext(state)
        "GOALS" -> buildGoalsBriefingContext(state)
        else -> ""
    }

    private fun buildDiaryBriefingContext(state: DiaryState): String {
        if (state.diaries.isEmpty()) return ""
        val recent = state.diaries.take(7)
        val dayOfWeek = try {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREAN)
                .parse(state.selectedDateString)
            if (date != null) {
                java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN).format(date)
            } else state.selectedDateString
        } catch (e: Exception) {
            state.selectedDateString
        }
        val entries = recent.joinToString("\n\n") { e ->
            val plain = e.contentPreview.take(180)
            val emotion = if (e.emotion.isBlank() || e.emotion == "Neutral") "" else " [감정: ${e.emotion}]"
            "- ${e.title}$emotion: $plain"
        }
        val typeStats = state.diaries.groupingBy { it.contentType.storageKey }
            .eachCount()
            .entries.joinToString(", ") { "${it.key} ${it.value}건" }
        val emotionStats = state.diaries.filter { it.emotion.isNotBlank() && it.emotion != "Neutral" }
            .groupingBy { it.emotion }
            .eachCount()
            .entries.joinToString(", ") { "${it.key} ${it.value}건" }
        return buildString {
            append("선택된 날짜: $dayOfWeek\n")
            append("총 일기 수: ${state.diaries.size}건\n")
            if (typeStats.isNotBlank()) append("콘텐츠 타입 통계: $typeStats\n")
            if (emotionStats.isNotBlank()) append("감정 통계: $emotionStats\n")
            append("\n[최근 ${recent.size}건]\n$entries")
        }
    }

    private fun buildPlannerBriefingContext(state: DiaryState): String {
        val dayOfWeek = try {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREAN)
                .parse(state.selectedDateString)
            if (date != null) {
                java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN).format(date)
            } else state.selectedDateString
        } catch (e: Exception) {
            state.selectedDateString
        }
        val todayTasks = state.plannerTasks.filter { it.dateString == state.selectedDateString }
        val seriesIds = state.plannerTasks.mapNotNull { it.seriesId }.toSet()
        val recurringDates = state.plannerTasks
            .filter { it.seriesId != null }
            .groupBy { it.seriesId }
            .map { (sid, list) -> Triple(sid, list.size, list.first().text) }

        val todayText = todayTasks.joinToString("\n") { t ->
            val timePart = buildString {
                if (!t.startTime.isNullOrBlank()) append(" ${t.startTime}")
                if (!t.endTime.isNullOrBlank()) append("~${t.endTime}")
            }
            val locPart = if (!t.location.isNullOrBlank()) " (장소: ${t.location})" else ""
            val done = if (t.isCompleted) " ✓완료" else ""
            "- ${t.text}$timePart$locPart$done"
        }

        val recurringText = recurringDates.take(5).joinToString("\n") { (_, count, text) ->
            "- $text (반복 시리즈, $count 일자)"
        }

        val upcomingDates = state.plannerTasks
            .map { it.dateString }
            .distinct()
            .sorted()
            .filter { it >= state.selectedDateString }
            .take(5)
            .joinToString(", ")

        return buildString {
            append("오늘 날짜: $dayOfWeek\n")
            append("전체 할 일 수: ${state.plannerTasks.size}건 (고유 시리즈 ${seriesIds.size}개)\n")
            if (upcomingDates.isNotBlank()) append("예정된 날짜(다음 5개): $upcomingDates\n")
            if (todayText.isNotBlank()) {
                append("\n[오늘의 계획 ${todayTasks.size}건]\n$todayText")
            } else {
                append("\n[오늘의 계획] 등록된 계획 없음\n")
            }
            if (recurringText.isNotBlank()) {
                append("\n\n[반복 시리즈 ${recurringDates.size}개]\n$recurringText")
            }
        }
    }

    private fun buildGoalsBriefingContext(state: DiaryState): String {
        if (state.goals.isEmpty()) return ""
        val total = state.goals.size
        val completed = state.goals.count { it.isCompleted }
        val active = state.goals.filter { !it.isCompleted }
        val recentlyCompleted = state.goals.filter { it.isCompleted }
            .sortedByDescending { it.completedDateString ?: "" }
            .take(3)

        val activeText = active.take(5).joinToString("\n") { g ->
            "- [${g.category}] ${g.text}"
        }
        val recentDoneText = recentlyCompleted.joinToString("\n") { g ->
            "- [${g.category}] ${g.text} (달성: ${g.completedDateString ?: "?"})"
        }

        val todayPlans = state.plannerTasks.filter { it.dateString == state.selectedDateString }
        val planText = todayPlans.joinToString("\n") { "- ${it.text}" }

        return buildString {
            append("전체 목표: ${total}건 (달성 $completed, 진행중 ${active.size})\n")
            if (activeText.isNotBlank()) {
                append("\n[진행중 목표 ${active.size}건 중 상위 5]\n$activeText")
            }
            if (recentDoneText.isNotBlank()) {
                append("\n\n[최근 달성 ${recentlyCompleted.size}건]\n$recentDoneText")
            }
            if (planText.isNotBlank()) {
                append("\n\n[오늘의 플래너 ${todayPlans.size}건]\n$planText")
            }
        }
    }

    private fun classifyDraftContentType() {
        val state = _state.value
        val plain = state.draftPlainText
        if (plain.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("분류를 위해 본문을 먼저 작성해주세요."))
            return
        }
        if (!requireReadyModel()) return
        if (state.isClassifyingType) return

        _state.update { it.copy(isClassifyingType = true) }
        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val key = engine.classifyContentType(plain, currentTypeLabel = state.draftContentType.label)
                val newType = com.grepiu.aidiary.data.model.ContentType.fromStorageKey(key)
                _state.update { it.copy(draftContentType = newType) }
                sendEffect(DiaryEffect.ShowToast("글 타입을 '${newType.label}'(으)로 분류했어요."))
            } catch (e: Exception) {
                sendEffect(DiaryEffect.ShowToast("AI 분류 오류: ${e.message}"))
            } finally {
                _state.update { it.copy(isClassifyingType = false) }
            }
        }
    }

    private fun proofreadBlock(blockId: String) {
        val state = _state.value
        val block = state.draftBlocks.firstOrNull { it.id == blockId } ?: return
        val originalText = (block as? com.grepiu.aidiary.data.model.ContentBlock.TextBlock)?.text
            ?: (block as? com.grepiu.aidiary.data.model.ContentBlock.HeadingBlock)?.text
            ?: (block as? com.grepiu.aidiary.data.model.ContentBlock.QuoteBlock)?.text
            ?: return
        if (originalText.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("다듬을 본문이 비어 있어요."))
            return
        }
        if (originalText.length > DiaryLLMEngine.MAX_BLOCK_AI_INPUT_CHARS) {
            sendEffect(
                DiaryEffect.ShowToast(
                    "본문이 ${DiaryLLMEngine.MAX_BLOCK_AI_INPUT_CHARS}자를 초과해 AI 보정을 적용할 수 없어요. 블록을 나눠 주세요."
                )
            )
            return
        }
        if (!requireReadyModel()) return
        if (state.isProofreadingBlockId == blockId) return

        val adj = com.grepiu.aidiary.data.slm.LLMContextBuilder.extractAdjacentContext(
            state.draftBlocks, blockId
        )

        _state.update { it.copy(isProofreadingBlockId = blockId) }
        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val revised = engine.proofreadText(
                    text = originalText,
                    previousTail = adj.previousTail,
                    nextHead = adj.nextHead,
                    sessionTitle = state.draftTitle.takeIf { it.isNotBlank() }
                )
                if (revised.isNotBlank() && revised != originalText) {
                    applyTextToBlock(blockId, revised)
                    sendEffect(DiaryEffect.ShowToast("본문을 다듬었어요."))
                } else if (revised.isNotBlank()) {
                    sendEffect(DiaryEffect.ShowToast("이미 깔끔한 본문이에요."))
                } else {
                    sendEffect(DiaryEffect.ShowToast("AI 보정에 실패했어요. 다시 시도해 주세요."))
                }
            } catch (e: Exception) {
                sendEffect(DiaryEffect.ShowToast("AI 보정 오류: ${e.message}"))
            } finally {
                _state.update { it.copy(isProofreadingBlockId = null) }
            }
        }
    }

    private fun decorateBlock(blockId: String) {
        val state = _state.value
        val block = state.draftBlocks.firstOrNull { it.id == blockId } ?: return
        val originalText = (block as? com.grepiu.aidiary.data.model.ContentBlock.TextBlock)?.text
            ?: (block as? com.grepiu.aidiary.data.model.ContentBlock.HeadingBlock)?.text
            ?: (block as? com.grepiu.aidiary.data.model.ContentBlock.QuoteBlock)?.text
            ?: return
        if (originalText.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("꾸밀 본문이 비어 있어요."))
            return
        }
        if (originalText.length > DiaryLLMEngine.MAX_BLOCK_AI_INPUT_CHARS) {
            sendEffect(
                DiaryEffect.ShowToast(
                    "본문이 ${DiaryLLMEngine.MAX_BLOCK_AI_INPUT_CHARS}자를 초과해 AI 꾸미기를 적용할 수 없어요. 블록을 나눠 주세요."
                )
            )
            return
        }
        if (!requireReadyModel()) return
        if (state.isDecoratingBlockId == blockId) return

        val adj = com.grepiu.aidiary.data.slm.LLMContextBuilder.extractAdjacentContext(
            state.draftBlocks, blockId
        )

        _state.update { it.copy(isDecoratingBlockId = blockId) }
        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val raw = engine.decorateText(
                    text = originalText,
                    previousTail = adj.previousTail,
                    nextHead = adj.nextHead,
                    sessionTitle = state.draftTitle.takeIf { it.isNotBlank() }
                )
                val result = DecorateResultParser.parse(raw, originalText)
                if (result.suggestions.isNotEmpty()) {
                    val newFmt = result.toTextFormatting()
                    applyFormattingToBlock(blockId, newFmt)
                    sendEffect(DiaryEffect.ShowToast("AI 꾸미기를 적용했어요. (${result.suggestions.size}건)"))
                } else {
                    sendEffect(DiaryEffect.ShowToast("꾸미기 적용할 단어를 찾지 못했어요."))
                }
            } catch (e: Exception) {
                sendEffect(DiaryEffect.ShowToast("AI 꾸미기 오류: ${e.message}"))
            } finally {
                _state.update { it.copy(isDecoratingBlockId = null) }
            }
        }
    }

    /**
     * 특정 블록의 text 만 교체합니다 (formatting 은 그대로 유지).
     */
    private fun applyTextToBlock(blockId: String, newText: String) {
        _state.update { current ->
            current.copy(
                draftBlocks = current.draftBlocks.map { block ->
                    if (block.id != blockId) return@map block
                    when (block) {
                        is com.grepiu.aidiary.data.model.ContentBlock.HeadingBlock ->
                            block.copy(text = newText)
                        is com.grepiu.aidiary.data.model.ContentBlock.TextBlock ->
                            block.copy(text = newText)
                        is com.grepiu.aidiary.data.model.ContentBlock.QuoteBlock ->
                            block.copy(text = newText)
                        else -> block
                    }
                }
            )
        }
    }

    /**
     * 특정 블록의 formatting 만 교체합니다. 텍스트 길이가 변할 수 있으므로
     * 새 서식의 인덱스가 텍스트 길이 안쪽으로 클램핑되도록 안전 가드를 둡니다.
     */
    private fun applyFormattingToBlock(
        blockId: String,
        newFormatting: com.grepiu.aidiary.data.model.TextFormatting
    ) {
        _state.update { current ->
            current.copy(
                draftBlocks = current.draftBlocks.map { block ->
                    if (block.id != blockId) return@map block
                    val textLen = when (block) {
                        is com.grepiu.aidiary.data.model.ContentBlock.HeadingBlock -> block.text.length
                        is com.grepiu.aidiary.data.model.ContentBlock.TextBlock -> block.text.length
                        is com.grepiu.aidiary.data.model.ContentBlock.QuoteBlock -> block.text.length
                        else -> return@map block
                    }
                    val safe = clampFormatting(newFormatting, textLen)
                    when (block) {
                        is com.grepiu.aidiary.data.model.ContentBlock.HeadingBlock ->
                            block.copy(formatting = safe)
                        is com.grepiu.aidiary.data.model.ContentBlock.TextBlock ->
                            block.copy(formatting = safe)
                        is com.grepiu.aidiary.data.model.ContentBlock.QuoteBlock ->
                            block.copy(formatting = safe)
                        else -> block
                    }
                }
            )
        }
    }

    private fun clampFormatting(
        fmt: com.grepiu.aidiary.data.model.TextFormatting,
        textLen: Int
    ): com.grepiu.aidiary.data.model.TextFormatting {
        fun cap(r: IntRange): IntRange? {
            val s = r.first.coerceIn(0, textLen)
            val e = r.last.coerceIn(0, (textLen - 1).coerceAtLeast(0))
            return if (s > e) null else s..e
        }
        return fmt.copy(
            boldRanges = fmt.boldRanges.mapNotNull(::cap),
            italicRanges = fmt.italicRanges.mapNotNull(::cap),
            underlineRanges = fmt.underlineRanges.mapNotNull(::cap),
            strikethroughRanges = fmt.strikethroughRanges.mapNotNull(::cap),
            colorRanges = fmt.colorRanges.mapNotNull { (r, v) -> cap(r)?.let { it to v } },
            sizeRanges = fmt.sizeRanges.mapNotNull { (r, v) -> cap(r)?.let { it to v } },
        )
    }

    /**
     * 사용자가 입력한 대화 내용을 바탕으로 캘린더 일기, 플래너, 목표 리스트로부터 로컬 RAG 검색을 수집한 뒤
     * 온디바이스 Gemma 4 언어 모델에게 질문 및 답변 유도를 수행합니다. (스트리밍 방식 피드백)
     *
     * v2 멀티턴 강화:
     *  - DiaryLLMEngine 이 Conversation 을 재사용하여 최근 raw N턴 + 그 이전 요약 슬라이딩 윈도우로 상하 문맥 보존
     *  - 컨텍스트 빌더 [DiaryLLMEngine.buildChatContextBlock] 로 RAG 블록을 표준화
     *  - 일기 RAG 컨텍스트는 평문이 길어 maxChars 적용 + 핵심 정보만 추출
     */
    private fun runOnDeviceChat(query: String) {
        val currentState = _state.value
        val engine = llmEngine
        if (engine == null || !currentState.isModelReady) {
            sendEffect(DiaryEffect.ShowToast("AI 모델이 아직 준비되지 않았습니다."))
            return
        }

        chatJob?.cancel()

        val userMsg = ChatMessage("USER", query)
        val aiMsg = ChatMessage("AI", "생각 중...")
        _state.update { it.copy(chatMessages = it.chatMessages + userMsg + aiMsg) }

        chatJob = viewModelScope.launch {
            val cleanQuery = query.trim().lowercase()
            val keywords = cleanQuery.split("\\s+".toRegex())
                .filter { it.length > 1 }
                .map { it.replace(Regex("[은는이가을를에에서으로]"), "") }
                .filter { it.isNotBlank() }

            // RAG: FTS5 hit → 매칭 후보 ID → 풀 DiaryEntry lazy 로드
            val ftsHits = withContext(Dispatchers.IO) { repository.searchDiaries(query, limit = 30) }
            val stateNow = _state.value
            val diaryIndex = stateNow.diaries.associateBy { it.id }
            val candidateIds: List<String> = when {
                ftsHits.isNotEmpty() -> ftsHits.take(CHAT_DIARY_CONTEXT_LIMIT).map { it.id }
                keywords.isNotEmpty() -> stateNow.diaries.filter { meta ->
                    keywords.any { kw -> meta.title.lowercase().contains(kw) || meta.contentPreview.lowercase().contains(kw) }
                }.take(CHAT_DIARY_CONTEXT_LIMIT).map { it.id }
                else -> stateNow.diaries.take(CHAT_DIARY_CONTEXT_LIMIT).map { it.id }
            }
            // 본문이 필요한 RAG 만 lazy 풀 로드 (3건 정도)
            val matchedDiaries: List<DiaryEntry> = candidateIds.mapNotNull { id ->
                diaryIndex[id]?.let { meta ->
                    withContext(Dispatchers.IO) { repository.loadFullDiary(meta.id) }
                }
            }

            val matchedTasks = stateNow.plannerTasks.filter { task ->
                keywords.any { kw -> task.text.lowercase().contains(kw) }
            }.take(CHAT_TASK_CONTEXT_LIMIT)
            val matchedGoals = stateNow.goals.filter { goal ->
                keywords.any { kw -> goal.text.lowercase().contains(kw) }
            }.take(CHAT_GOAL_CONTEXT_LIMIT)

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val todayTasks = stateNow.plannerTasks.filter { it.dateString == todayStr }
            val selectedDateStr = stateNow.selectedDateString
            val selectedDateTasks = stateNow.plannerTasks.filter { it.dateString == selectedDateStr }

            val finalDiaries = if (matchedDiaries.isEmpty() && keywords.isNotEmpty()) emptyList() else matchedDiaries
            val finalTasks = if (matchedTasks.isEmpty() && keywords.isNotEmpty()) stateNow.plannerTasks.take(CHAT_TASK_CONTEXT_LIMIT) else matchedTasks
            val finalGoals = if (matchedGoals.isEmpty() && keywords.isNotEmpty()) stateNow.goals.take(CHAT_GOAL_CONTEXT_LIMIT) else matchedGoals

            // 일기 본문은 200자로 강제 truncate → 토큰 폭주 방지
            val diaryLines = finalDiaries.map { diary ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(diary.timestamp))
                "날짜: $dateStr, 제목: ${diary.title}, 본문: ${com.grepiu.aidiary.data.slm.LLMContextBuilder.truncateChars(diary.contentText, CHAT_DIARY_CONTENT_CHARS)}, 감정: ${diary.emotion}"
            }
            val taskLinesToday = todayTasks.map { "할 일: ${it.text}, 상태: ${if (it.isCompleted) "완료" else "미완료"}" }
            val taskLinesSelected = selectedDateTasks.map { "할 일: ${it.text}, 상태: ${if (it.isCompleted) "완료" else "미완료"}" }
            val taskLinesMatched = finalTasks.map { "계획날짜: ${it.dateString}, 내용: ${it.text}, 상태: ${if (it.isCompleted) "완료" else "미완료"}" }
            val goalLines = finalGoals.map { "목표: ${it.text}, 상태: ${if (it.isCompleted) "완료" else "미완료"}" }

            val contextBlock = engine.buildChatContextBlock(
                todayStr = todayStr,
                selectedDateStr = selectedDateStr,
                todayTasks = taskLinesToday,
                selectedDateTasks = if (selectedDateStr != todayStr) taskLinesSelected else emptyList(),
                matchedDiaries = diaryLines,
                matchedTasks = taskLinesMatched,
                matchedGoals = goalLines
            )

            var hasStartedOutput = false
            engine.onTokenReceived = { token, done ->
                _state.update { current ->
                    val list = current.chatMessages.toMutableList()
                    if (list.isNotEmpty()) {
                        val last = list.last()
                        if (last.sender == "AI") {
                            val newText = if (!hasStartedOutput) {
                                hasStartedOutput = true
                                token
                            } else {
                                last.text + token
                            }
                            list[list.size - 1] = last.copy(
                                text = newText.replace("\\n", "\n").replace("\\t", " ")
                            )
                        }
                    }
                    current.copy(
                        chatMessages = list,
                        isGeneratingChat = !done
                    )
                }
            }

            try {
                engine.generateChatResponse(contextBlock, query)
            } catch (e: Exception) {
                _state.update { current ->
                    val list = current.chatMessages.toMutableList()
                    if (list.isNotEmpty() && list.last().sender == "AI") {
                        list[list.size - 1] = list.last().copy(text = "[답변을 생성할 수 없습니다: ${e.message}]")
                    }
                    current.copy(chatMessages = list, isGeneratingChat = false)
                }
            }
        }
    }

    // ===== Whisper =====

    private suspend fun ensureWhisperModelReady() {
        if (downloader.isSherpaModelDownloaded()) {
            initSherpa()
            return
        }
        try {
            _state.update { it.copy(isDownloadingModel = true, modelDownloadProgress = 0f, modelDownloadSizeText = "음성인식 모델 다운로드 중...") }
            downloader.downloadSherpaModel(SHERPA_DOWNLOAD_URL) { bytesRead, totalBytes ->
                val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                val label = if (progress >= 0.99f) "압축 해제 중..." else "음성인식 모델 다운로드 중..."
                val sizeText = "${downloader.toHumanReadableSize(bytesRead)} / ${if (totalBytes > 0) downloader.toHumanReadableSize(totalBytes) else "???"}"
                _state.update { it.copy(modelDownloadProgress = progress, modelDownloadSizeText = "$label $sizeText") }
            }.onSuccess {
                _state.update { it.copy(isDownloadingModel = false, modelDownloadSizeText = null) }
                initSherpa()
            }.onFailure { e ->
                _state.update { it.copy(isDownloadingModel = false, modelDownloadSizeText = null) }
                Log.e("DiaryViewModel", "Sherpa download failed: ${e.message}", e)
                sendEffect(DiaryEffect.ShowToast("Sherpa download failed: ${e.message}"))
            }
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "ensure error", e)
        }
    }

    private fun initSherpa() {
        try {
            val modelDir = downloader.getSherpaModelDir()
            if (!modelDir.exists()) return
            // 중첩 디렉토리 찾기
            val actualDir = modelDir.listFiles()
                ?.firstOrNull { it.isDirectory && !it.name.startsWith(".") && File(it, "tokens.txt").exists() }
                ?: modelDir
            sherpaEngine = SherpaEngine.create(
                actualDir.absolutePath,
                language = _state.value.voiceLanguage
            )
            _state.update { it.copy(isSherpaModelReady = true) }
            Log.d("DiaryViewModel", "Sherpa engine ready (lang=${_state.value.voiceLanguage})")
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Sherpa init failed: ${e.message}")
        }
    }
    private fun startRecording() {
        if (_state.value.isRecording) return
        if (!_state.value.isSherpaModelReady) { sendEffect(DiaryEffect.ShowToast("모델 준비되지 않음")); return }
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) { sendEffect(DiaryEffect.RequestAudioPermission); return }

        recordingJob?.cancel()
        _state.update { it.copy(isRecording = true, recordingSeconds = 0, recordingVolume = 0f) }

        try {
            val pm = getApplication<Application>().getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIDiary:Recording").apply { acquire(3600 * 1000L) }
        } catch (_: Exception) {}

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val rate = 16000
                val bufSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(512)
                val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 4)
                audioRecord = rec
                val pcmFile = File(getApplication<Application>().cacheDir, "recording.pcm")
                val fos = FileOutputStream(pcmFile)
                val buf = ShortArray(bufSize)
                rec.startRecording()
                val start = System.currentTimeMillis()

                while (_state.value.isRecording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    fos.write(shortArrayToByteArray(buf, n))

                    var sum = 0L
                    for (i in 0 until n) { val s = buf[i].toInt(); sum += s * s }
                    val rms = Math.sqrt(sum.toDouble() / n)
                    val db = if (rms > 1.0) 20.0 * Math.log10(rms / 32768.0) else -60.0
                    val vol = ((db + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)

                    val elapsed = ((System.currentTimeMillis() - start) / 1000).toInt()
                    launch(Dispatchers.Main) { _state.update { it.copy(recordingSeconds = elapsed, recordingVolume = vol) } }
                    if (elapsed >= 120) { launch(Dispatchers.Main) { stopRecording() }; break }
                }

                rec.stop(); rec.release(); fos.close()

                if (pcmFile.length() > 0) {
                    val wavFile = convertPcmToWav(pcmFile, rate); pcmFile.delete(); transcribeAudio(wavFile)
                }
            } catch (e: Exception) {
                Log.e("DiaryViewModel", "Rec error", e)
                _state.update { it.copy(isRecording = false, recordingVolume = 0f) }
            }
        }
    }

    private fun stopRecording() {
        _state.update { it.copy(isRecording = false) }
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    /**
     * 사용자가 선택한 음성 인식 언어 코드를 한국어 라벨로 변환한다.
     * (UI 토스트/표시용)
     */
    private fun languageLabel(code: String): String = when (code) {
        "auto" -> "자동"
        "ko" -> "한국어"
        "en" -> "English"
        "ja" -> "日本語"
        "zh" -> "中文"
        "yue" -> "粤语"
        else -> code
    }

    /**
     * 현재 본문 평문을 AI 로 한국어로 번역한다.
     * 결과는 [DiaryState.translatedDraft] 에 저장되어 다이얼로그에서 노출,
     * [DiaryIntent.ApplyTranslatedDraft] 로 본문에 적용하거나
     * [DiaryIntent.ClearTranslatedDraft] 로 폐기한다.
     */
    private fun translateDraftToKorean() {
        val state = _state.value
        if (!requireReadyModel()) return
        val plain = state.draftPlainText
        if (plain.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("번역할 본문이 비어 있어요."))
            return
        }
        if (state.isTranslatingDraft) return

        _state.update { it.copy(isTranslatingDraft = true, translatedDraft = null) }
        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val translated = engine.translateToKorean(plain)
                if (translated.isBlank()) {
                    sendEffect(DiaryEffect.ShowToast("번역 결과를 만들지 못했어요. 다시 시도해 주세요."))
                    _state.update { it.copy(isTranslatingDraft = false) }
                } else {
                    _state.update { it.copy(translatedDraft = translated, isTranslatingDraft = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isTranslatingDraft = false) }
                sendEffect(DiaryEffect.ShowToast("AI 번역 오류: ${e.message}"))
            }
        }
    }

    /**
     * [DiaryState.translatedDraft] 결과를 현재 본문(첫 TextBlock) 에 덮어쓴다.
     * - 본문이 비어 있으면 새 TextBlock 을 만들고, 있으면 마지막 TextBlock 에 append 한다.
     * - RichTextField 의 TextFormatting 은 유지되지 않으므로 텍스트만 교체한다.
     */
    private fun applyTranslatedDraft() {
        val translated = _state.value.translatedDraft ?: return
        val newBlocks = _state.value.draftBlocks.toMutableList()
        val lastTextIdx = newBlocks.indexOfLast { it is ContentBlock.TextBlock }
        if (lastTextIdx >= 0) {
            val last = newBlocks[lastTextIdx] as ContentBlock.TextBlock
            val combined = if (last.text.isBlank()) translated
                           else "${last.text}\n$translated"
            newBlocks[lastTextIdx] = last.copy(text = combined)
        } else {
            newBlocks.add(ContentBlock.TextBlock(text = translated))
        }
        _state.update {
            it.copy(
                draftBlocks = newBlocks,
                translatedDraft = null
            )
        }
        sendEffect(DiaryEffect.ShowToast("본문에 번역 결과를 적용했어요."))
    }

    /**
     * 본문 평문을 Android 시스템 클립보드에 복사한다.
     * 본문이 비어 있으면 토스트로 알리고 early return.
     */
    private fun copyDraftToClipboard() {
        val plain = _state.value.draftPlainText
        if (plain.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("복사할 본문이 비어 있어요."))
            return
        }
        try {
            val clipboard = getApplication<Application>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("diary_draft", plain)
            clipboard.setPrimaryClip(clip)
            sendEffect(DiaryEffect.ShowToast("본문을 클립보드에 복사했어요."))
        } catch (e: Exception) {
            sendEffect(DiaryEffect.ShowToast("클립보드 복사 실패: ${e.message}"))
        }
    }

    /**
     * 특정 블록의 텍스트를 시스템 클립보드에 복사합니다.
     */
    private fun copyBlockToClipboard(blockId: String) {
        val block = _state.value.draftBlocks.firstOrNull { it.id == blockId }
        val textToCopy = when (block) {
            is ContentBlock.TextBlock -> block.text
            is ContentBlock.HeadingBlock -> block.text
            is ContentBlock.QuoteBlock -> block.text
            else -> ""
        }
        if (textToCopy.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("복사할 텍스트가 없습니다."))
            return
        }
        try {
            val clipboard = getApplication<Application>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("diary_block", textToCopy)
            clipboard.setPrimaryClip(clip)
            sendEffect(DiaryEffect.ShowToast("선택한 블록을 복사했습니다."))
        } catch (e: Exception) {
            sendEffect(DiaryEffect.ShowToast("클립보드 복사 실패: ${e.message}"))
        }
    }

    /**
     * 특정 블록의 텍스트를 AI로 한국어로 번역하여 해당 블록의 내용에 즉각 반영합니다.
     */
    private fun translateBlockToKorean(blockId: String) {
        if (!requireReadyModel()) return
        val block = _state.value.draftBlocks.firstOrNull { it.id == blockId }
        val textToTranslate = when (block) {
            is ContentBlock.TextBlock -> block.text
            is ContentBlock.HeadingBlock -> block.text
            is ContentBlock.QuoteBlock -> block.text
            else -> ""
        }
        if (textToTranslate.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("번역할 텍스트가 비어 있습니다."))
            return
        }

        if (_state.value.translatingBlockIds.contains(blockId)) return

        _state.update { it.copy(translatingBlockIds = it.translatingBlockIds + blockId) }

        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val translated = engine.translateToKorean(textToTranslate)
                if (translated.isBlank()) {
                    sendEffect(DiaryEffect.ShowToast("번역을 실패했습니다."))
                } else {
                    _state.update { current ->
                        current.copy(
                            draftBlocks = current.draftBlocks.map { b ->
                                if (b.id == blockId) {
                                    when (b) {
                                        is ContentBlock.TextBlock -> b.copy(text = translated)
                                        is ContentBlock.HeadingBlock -> b.copy(text = translated)
                                        is ContentBlock.QuoteBlock -> b.copy(text = translated)
                                        else -> b
                                    }
                                } else b
                            }
                        )
                    }
                    sendEffect(DiaryEffect.ShowToast("번역 완료"))
                }
            } catch (e: Exception) {
                sendEffect(DiaryEffect.ShowToast("AI 번역 실패: ${e.message}"))
            } finally {
                _state.update { it.copy(translatingBlockIds = it.translatingBlockIds - blockId) }
            }
        }
    }

    private fun shortArrayToByteArray(shorts: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) { val s = shorts[i]; bytes[i * 2] = (s.toInt() and 0xFF).toByte(); bytes[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte() }
        return bytes
    }

    private fun convertPcmToWav(pcmFile: File, sampleRate: Int): File {
        val wavFile = File(getApplication<Application>().cacheDir, "recording.wav")
        val pcmData = pcmFile.readBytes()
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2
        val header = ByteArray(44)
        "RIFF".toByteArray().copyInto(header, 0)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)
        header.set(16, 16); header.set(20, 1); header.set(22, 1)
        header.set(24, (sampleRate and 0xFF).toByte()); header.set(25, ((sampleRate shr 8) and 0xFF).toByte())
        header.set(26, ((sampleRate shr 16) and 0xFF).toByte()); header.set(27, ((sampleRate shr 24) and 0xFF).toByte())
        header.set(28, (byteRate and 0xFF).toByte()); header.set(29, ((byteRate shr 8) and 0xFF).toByte())
        header.set(30, ((byteRate shr 16) and 0xFF).toByte()); header.set(31, ((byteRate shr 24) and 0xFF).toByte())
        header.set(32, 2); header.set(34, 16)
        "data".toByteArray().copyInto(header, 36)
        header.set(40, (pcmData.size and 0xFF).toByte()); header.set(41, ((pcmData.size shr 8) and 0xFF).toByte())
        header.set(42, ((pcmData.size shr 16) and 0xFF).toByte()); header.set(43, ((pcmData.size shr 24) and 0xFF).toByte())
        header.set(4, (totalDataLen and 0xFF).toByte()); header.set(5, ((totalDataLen shr 8) and 0xFF).toByte())
        header.set(6, ((totalDataLen shr 16) and 0xFF).toByte()); header.set(7, ((totalDataLen shr 24) and 0xFF).toByte())
        FileOutputStream(wavFile).use { it.write(header); it.write(pcmData) }
        return wavFile
    }

    private fun transcribeAudio(wavFile: File) {
        val engine = sherpaEngine ?: return
        _state.update { it.copy(isTranscribing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = engine.transcribe(wavFile.absolutePath)
                withContext(Dispatchers.Main) {
                    _state.update { current ->
                        val blocks = current.draftBlocks.toMutableList()
                        val lastIdx = blocks.indexOfLast { it is ContentBlock.TextBlock }
                        if (lastIdx >= 0) {
                            val last = blocks[lastIdx] as ContentBlock.TextBlock
                            blocks[lastIdx] = last.copy(
                                text = if (last.text.isBlank()) text else "${last.text}\n$text"
                            )
                        } else {
                            blocks.add(ContentBlock.TextBlock(text = text))
                        }
                        current.copy(draftBlocks = blocks, isTranscribing = false)
                    }
                    sendEffect(DiaryEffect.TranscriptionResult(text))
                }
            } catch (e: Exception) {
                _state.update { it.copy(isTranscribing = false) }
                sendEffect(DiaryEffect.ShowToast("변환 실패: ${e.message}"))
            } finally { wavFile.delete() }
        }
    }
    /**
     * 작성 완료한 일기를 영구 보관합니다.
     *
     * v2 흐름 (1회 통합 호출):
     * 1. 본문/제목 검증
     * 2. 모델 미준비 시 즉시 저장
     * 3. 모델 준비 시 (글 타입 + 감정) 을 1회 LLM 호출로 동시에 추출
     *    → 추천 타입이 현재 선택과 다르면 [DiaryState.pendingContentTypeChange] 세팅 + 다이얼로그
     *    → 같거나 사용자 응답이 들어오면 감정 결과로 TAG AI 블록 만들어 저장
     */
    private fun saveDiaryDraft() {
        val currentState = _state.value
        if (currentState.isGeneratingAnalysis) return
        if (currentState.isClassifyingTypeOnSave) return
        if (currentState.pendingContentTypeChange != null) return

        val plain = currentState.draftPlainText
        if (plain.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("일기 본문을 작성해주세요."))
            return
        }
        if (currentState.sessionTitle.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("상단 제목 입력란에 제목을 입력하거나 AI 제목 버튼을 눌러 주세요."))
            return
        }

        val engine = llmEngine
        if (engine == null || !currentState.isModelReady) {
            viewModelScope.launch {
                persistDiary(
                    currentState = currentState,
                    plain = plain,
                    finalEmotion = currentState.draftEmotion,
                    tagAiBlock = null
                )
            }
            return
        }

        analysisJob?.cancel()
        _state.update { it.copy(isClassifyingTypeOnSave = true) }
        analysisJob = viewModelScope.launch {
            try {
                val dateStr = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN).format(Date())
                val result = engine.classifyAndDetectEmotion(
                    title = currentState.sessionTitle,
                    content = plain,
                    dateString = dateStr,
                    currentTypeLabel = currentState.draftContentType.label
                )
                _state.update { it.copy(isClassifyingTypeOnSave = false) }
                val suggestedType = com.grepiu.aidiary.data.model.ContentType.fromStorageKey(result.typeKey)
                val currentType = _state.value.draftContentType
                if (suggestedType != currentType) {
                    _state.update {
                        it.copy(
                            pendingContentTypeChange = PendingContentTypeChange(
                                currentType = currentType,
                                suggestedType = suggestedType
                            ),
                            // 감정 결과는 미리 캐싱해 다이얼로그 응답 시 즉시 사용
                        )
                    }
                    // 추천 타입 결과와 감정 결과를 pending 캐시로 보관
                    pendingEmotionLabel = result.emotion
                } else {
                    // 같은 타입 → 즉시 저장
                    val emotionCode = mapEmotionLabelToCode(result.emotion)
                    _state.update { it.copy(isGeneratingAnalysis = true) }
                    persistDiary(
                        currentState = _state.value,
                        plain = plain,
                        finalEmotion = emotionCode,
                        tagAiBlock = ContentBlock.TagAiBlock(emotion = result.emotion)
                    )
                }
            } catch (e: Exception) {
                Log.e("DiaryViewModel", "저장 시 통합 분석 실패, 현재 타입/감정 폴백으로 저장합니다", e)
                sendEffect(DiaryEffect.ShowToast("AI 분석에 실패해 현재 상태로 저장할게요."))
                _state.update { it.copy(isClassifyingTypeOnSave = false) }
                persistDiary(
                    currentState = _state.value,
                    plain = plain,
                    finalEmotion = currentState.draftEmotion,
                    tagAiBlock = null
                )
            }
        }
    }

    /**
     * 다이얼로그 응답 (변경/유지) 이후 저장. 통합 호출에서 받은 감정 결과를 재사용한다.
     */
    private fun proceedWithEmotionAndSave() {
        val currentState = _state.value
        if (currentState.isGeneratingAnalysis) return
        val engine = llmEngine
        val plain = currentState.draftPlainText
        if (plain.isBlank()) return

        // 1) 이미 캐시된 감정 결과가 있으면 즉시 사용
        val cached = pendingEmotionLabel
        if (engine != null && cached != null) {
            pendingEmotionLabel = null
            _state.update { it.copy(isGeneratingAnalysis = true) }
            val emotionCode = mapEmotionLabelToCode(cached)
            viewModelScope.launch {
                persistDiary(
                    currentState = currentState,
                    plain = plain,
                    finalEmotion = emotionCode,
                    tagAiBlock = ContentBlock.TagAiBlock(emotion = cached)
                )
            }
            return
        }

        // 2) 캐시가 없거나 모델 미준비 시 단일 감정 분류 폴백
        if (engine == null) {
            viewModelScope.launch {
                persistDiary(
                    currentState = currentState,
                    plain = plain,
                    finalEmotion = currentState.draftEmotion,
                    tagAiBlock = null
                )
            }
            return
        }
        _state.update { it.copy(isGeneratingAnalysis = true) }
        viewModelScope.launch {
            try {
                val dateStr = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN).format(Date())
                val result = engine.detectEmotion(
                    title = currentState.sessionTitle,
                    content = plain,
                    dateString = dateStr
                )
                val emotionCode = mapEmotionLabelToCode(result.emotion)
                persistDiary(
                    currentState = currentState,
                    plain = plain,
                    finalEmotion = emotionCode,
                    tagAiBlock = ContentBlock.TagAiBlock(emotion = result.emotion)
                )
            } catch (e: Exception) {
                Log.e("DiaryViewModel", "AI 감정 분류 실패, TAG 블록 없이 저장합니다", e)
                sendEffect(DiaryEffect.ShowToast("AI 감정 분류에 실패해 TAG 블록 없이 저장했어요."))
                _state.update { it.copy(isGeneratingAnalysis = false) }
                persistDiary(
                    currentState = currentState,
                    plain = plain,
                    finalEmotion = currentState.draftEmotion,
                    tagAiBlock = null
                )
            }
        }
    }

    /**
     * 실제 영구 저장 + 상태 리셋. TAG AI 블록이 있으면 본문 끝에 append 합니다.
     */
    private suspend fun persistDiary(
        currentState: DiaryState,
        plain: String,
        finalEmotion: String,
        tagAiBlock: ContentBlock.TagAiBlock?
    ) {
        pendingEmotionLabel = null
        val finalBlocks = if (tagAiBlock != null) {
            currentState.draftBlocks + tagAiBlock
        } else {
            currentState.draftBlocks
        }
        val newEntry = DiaryEntry(
            title = currentState.sessionTitle,
            titleStyle = currentState.draftTitleStyle,
            blocks = finalBlocks,
            content = plain,
            emotion = finalEmotion,
            aiAnalysis = null,
            contentType = currentState.draftContentType
        )

        val updated = repository.addEntry(newEntry)
        // 저장 직후 메타 1페이지 다시 로드 + draft 초기화
        refreshCurrentMetaPage()
        _state.update {
            it.copy(
                phase = DiaryPhase.LIST,
                draftTitle = "",
                draftBlocks = emptyList(),
                draftTitleStyle = TitleStyle.Default,
                draftEmotion = "Neutral",
                draftContentType = com.grepiu.aidiary.data.model.ContentType.DIARY,
                isGeneratingAnalysis = false
            )
        }
        sendEffect(DiaryEffect.ShowToast("${currentState.draftContentType.label}이(가) 저장되었어요."))
    }

    /**
     * 현재 표시 모드(검색/일반) 를 유지하면서 메타 1페이지만 다시 로드.
     * - 검색 모드면 검색을 다시 실행
     * - 일반 모드면 페이지 1로 리셋
     */
    private fun refreshCurrentMetaPage() {
        if (_state.value.isSearching || _state.value.searchQuery.isNotBlank()) {
            handleSearch(_state.value.searchQuery)
        } else {
            loadFirstDiaryPage()
        }
    }

    /**
     * AI 가 반환한 한국어 감정 라벨을 DiaryEntry.emotion 코드로 매핑합니다.
     * (5 종 감정만 반환되므로 간단한 매핑)
     */
    private fun mapEmotionLabelToCode(label: String): String = when (label.trim()) {
        "기쁨" -> "Joy"
        "슬픔" -> "Sadness"
        "분노" -> "Anger"
        "불안" -> "Anxiety"
        "평온" -> "Calm"
        else -> "Neutral"
    }

    /**
     * 목표 완료 시 비동기적으로 온디바이스 AI 축하 코멘트를 생성하여 갱신합니다.
     */
    private fun generateAiCongratulation(goalId: String, goalText: String) {
        if (llmEngine == null || !_state.value.isModelReady) {
            // 모델 미준비 시 기본 멘트 매핑
            viewModelScope.launch {
                _state.update { current ->
                    val updated = current.goals.map {
                        if (it.id == goalId) it.copy(aiCongratulationText = "목표 달성을 진심으로 축하합니다! 앞으로의 여정도 응원합니다. 🎉") else it
                    }
                    plannerRepository.saveGoals(updated)
                    current.copy(goals = updated)
                }
            }
            return
        }

        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val message = engine.generateCongratulation(goalText)
                _state.update { current ->
                    val updated = current.goals.map {
                        if (it.id == goalId) it.copy(aiCongratulationText = if (message.isNotBlank()) message else "목표 달성을 응원합니다! 고생하셨어요. 👏") else it
                    }
                    plannerRepository.saveGoals(updated)
                    current.copy(goals = updated)
                }
            } catch (e: Exception) {
                android.util.Log.e("DiaryViewModel", "Error generating goal congratulation: ${e.message}")
                _state.update { current ->
                    val updated = current.goals.map {
                        if (it.id == goalId) it.copy(aiCongratulationText = "목표 달성을 진심으로 축하합니다! 고생 많으셨습니다. 🏆") else it
                    }
                    plannerRepository.saveGoals(updated)
                    current.copy(goals = updated)
                }
            }
        }
    }

    override fun onCleared() {
        downloadJob?.cancel()
        analysisJob?.cancel()
        recordingJob?.cancel()
        audioRecord?.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        llmEngine?.dispose()
        sherpaEngine?.dispose()
        super.onCleared()
    }

    companion object {
        // AI 비서(RAG) 가 한 번에 LLM 컨텍스트로 주입하는 상한.
        // 일기는 FTS + 날짜 가중치로 추려서 15건, 할 일/목표는 데이터량이 적어 5건.
        private const val DIARY_CONTEXT_LIMIT = 15
        private const val TASK_CONTEXT_LIMIT = 5
        private const val GOAL_CONTEXT_LIMIT = 5

        // 챗봇 RAG 전용 한도. 2B 모델(1024 토큰) 환경에서 RAG 컨텍스트가
        // system + 멀티턴 + query 와 합쳐 한도를 넘지 않도록 더 작게 설정.
        // 일기 본문은 [CHAT_DIARY_CONTENT_CHARS] 자로 강제 truncate.
        private const val CHAT_DIARY_CONTEXT_LIMIT = 3
        private const val CHAT_TASK_CONTEXT_LIMIT = 3
        private const val CHAT_GOAL_CONTEXT_LIMIT = 3
        private const val CHAT_DIARY_CONTENT_CHARS = 80

        private const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        private const val SHERPA_DOWNLOAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2"

        /** 표 블록 UI 안전 범위. */
        private const val TABLE_MAX_ROWS = 30
        private const val TABLE_MAX_COLS = 10
    }
}
