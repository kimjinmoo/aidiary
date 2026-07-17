package com.grepiu.aidiary.mvi.viewmodel

import android.Manifest
import android.app.Application
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
import com.grepiu.aidiary.data.model.extractPlainText
import com.grepiu.aidiary.data.repository.DiaryRepository
import com.grepiu.aidiary.data.repository.ImageStorageManager
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // MVI 상태 데이터 홀더
    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state.asStateFlow()

    // MVI 부수 효과 채널
    private val _effect = Channel<DiaryEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        // 앱 구동 시 일기 데이터 불러오기 및 AI 모델 검사 실행
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

        viewModelScope.launch {
            ensureModelReady()
        }
        viewModelScope.launch {
            ensureWhisperModelReady()
        }
    }

    /**
     * MVI 인텐트 처리
     */
    fun processIntent(intent: DiaryIntent) {
        when (intent) {
            is DiaryIntent.LoadDiaries -> {
                val diaries = repository.getDiaries()
                _state.update { it.copy(diaries = diaries) }
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
                _state.update { currentState ->
                    currentState.copy(
                        phase = intent.phase,
                        selectedDiary = intent.selectedDiary,
                        // 화면 전환 시 기존 AI 피드백 텍스트 리셋
                        aiAnalysisText = if (intent.phase == DiaryPhase.WRITE) null else currentState.aiAnalysisText,
                        // 새 일기 작성 화면 진입 시 draft 값 초기화
                        draftBlocks = if (intent.phase == DiaryPhase.WRITE) emptyList() else currentState.draftBlocks,
                        draftEmotion = if (intent.phase == DiaryPhase.WRITE) "Neutral" else currentState.draftEmotion,
                        draftContentType = if (intent.phase == DiaryPhase.WRITE) com.grepiu.aidiary.data.model.ContentType.DIARY else currentState.draftContentType
                    )
                }
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
                val updated = repository.deleteEntry(intent.id)
                _state.update { it.copy(diaries = updated, phase = DiaryPhase.LIST, selectedDiary = null) }
                sendEffect(DiaryEffect.ShowToast("일기가 삭제되었습니다."))
            }
            is DiaryIntent.AnalyzeDiary -> {
                runOnDeviceAIAnalysis()
            }
            is DiaryIntent.UpdateDraftType -> {
                _state.update { it.copy(draftContentType = intent.contentType) }
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

            // ===== 블록 기반 콘텐츠 =====
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

            // ===== 이미지 픽업/촬영 =====
            is DiaryIntent.ImagePicked -> {
                importPickedImage(intent.uri)
            }
            is DiaryIntent.CameraImageCaptured -> {
                importCapturedImage(intent.tempFilePath)
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
                    val updatedGoals = current.goals + Goal(text = intent.text)
                    plannerRepository.saveGoals(updatedGoals)
                    current.copy(goals = updatedGoals)
                }
            }
            is DiaryIntent.ToggleGoal -> {
                _state.update { current ->
                    val updatedGoals = current.goals.map {
                        if (it.id == intent.id) it.copy(isCompleted = !it.isCompleted) else it
                    }
                    plannerRepository.saveGoals(updatedGoals)
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
                    val updatedTasks = current.plannerTasks + PlannerTask(text = intent.text, dateString = intent.dateString)
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

            // ===== 온디바이스 AI 챗봇 =====
            is DiaryIntent.SendChatMessage -> {
                runOnDeviceChat(intent.text)
            }
            is DiaryIntent.ClearChatHistory -> {
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
     * 외부에서 픽업된 이미지 URI 를 내부 저장소로 복사하고 ImageBlock 으로 추가합니다.
     */
    private fun importPickedImage(uri: Uri) {
        if (_state.value.isImportingImage) return
        _state.update { it.copy(isImportingImage = true) }
        viewModelScope.launch {
            imageStore.importFromUri(uri)
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
                    sendEffect(DiaryEffect.ShowToast("이미지를 가져오지 못했어요: ${e.message}"))
                }
        }
    }

    /**
     * 카메라 촬영으로 생성된 임시 파일을 내부 저장소로 가져와 ImageBlock 으로 추가합니다.
     */
    private fun importCapturedImage(tempFilePath: String) {
        val temp = File(tempFilePath)
        if (!temp.exists() || temp.length() == 0L) {
            sendEffect(DiaryEffect.ShowToast("촬영된 이미지를 찾을 수 없어요."))
            return
        }
        if (_state.value.isImportingImage) return
        _state.update { it.copy(isImportingImage = true) }
        viewModelScope.launch {
            imageStore.importFromFile(temp)
                .onSuccess { relPath ->
                    val block = ContentBlock.ImageBlock(relativePath = relPath)
                    _state.update {
                        it.copy(
                            draftBlocks = it.draftBlocks + block,
                            isImportingImage = false
                        )
                    }
                    temp.delete()
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
                onTokenReceived = { token, done ->
                    _state.update { currentState ->
                        if (currentState.phase != DiaryPhase.WRITE) return@update currentState
                        val rawText = if (currentState.aiAnalysisText.isNullOrEmpty()) {
                            token
                        } else {
                            currentState.aiAnalysisText + token
                        }
                        // 이스케이프 문자 치환
                        val updatedText = rawText
                            .replace("\\n", "\n")
                            .replace("\\t", " ")

                        if (done) {
                            sendEffect(DiaryEffect.AnalysisComplete(updatedText))
                        }
                        currentState.copy(
                            aiAnalysisText = updatedText,
                            isGeneratingAnalysis = !done
                        )
                    }
                }
            }
            _state.update { it.copy(isModelReady = true, isModelInitializing = false) }
            Log.d("DiaryViewModel", "LLM initialized successfully from source: $source")
        } catch (e: Exception) {
            downloader.deleteModelFile() // 손상 파일 삭제 유도
            _state.update { it.copy(isModelInitializing = false, isModelReady = false, showDownloadNotice = true) }
            sendEffect(DiaryEffect.ShowToast("AI 초기화 실패. 모델을 다시 다운로드합니다: ${e.message}"))
        }
    }

    /**
     * 일기 작성란의 텍스트를 바탕으로 온디바이스 AI 감정 분석 및 위로 답변을 스트리밍 요청합니다.
     */
    private fun runOnDeviceAIAnalysis() {
        val currentState = _state.value
        val plain = currentState.draftPlainText
        if (plain.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("일기 내용을 작성해주세요."))
            return
        }
        val engine = llmEngine
        if (engine == null || !currentState.isModelReady) {
            sendEffect(DiaryEffect.ShowToast("AI 모델이 아직 준비되지 않았습니다."))
            return
        }

        analysisJob?.cancel()
        _state.update { it.copy(isGeneratingAnalysis = true, aiAnalysisText = "") }

        analysisJob = viewModelScope.launch {
            try {
                val dateStr = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN).format(Date())
                engine.generateAnalysis(
                    title = currentState.sessionTitle,
                    content = plain,
                    dateString = dateStr
                )
            } catch (e: Exception) {
                _state.update { it.copy(isGeneratingAnalysis = false) }
                sendEffect(DiaryEffect.ShowToast("AI 분석 오류: ${e.message}"))
            }
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
                val title = engine.suggestTitle(plain)
                if (title.isNotBlank()) {
                    applyTitleToFirstHeading(title)
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
     * AI 추천 제목을 첫 HeadingBlock 에 적용합니다. 없으면 새로 만듭니다.
     * (단일 HeadingBlock 정책 - 두 번째 생성은 차단)
     */
    private fun applyTitleToFirstHeading(title: String) {
        _state.update { current ->
            val blocks = current.draftBlocks.toMutableList()
            val firstHeadingIdx = blocks.indexOfFirst { it is ContentBlock.HeadingBlock }
            if (firstHeadingIdx >= 0) {
                val existing = blocks[firstHeadingIdx] as ContentBlock.HeadingBlock
                blocks[firstHeadingIdx] = existing.copy(text = title)
            } else {
                blocks.add(0, ContentBlock.HeadingBlock(text = title))
            }
            current.copy(draftBlocks = blocks)
        }
    }

    private fun classifyDraftContentType() {
        val plain = _state.value.draftPlainText
        if (plain.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("분류를 위해 본문을 먼저 작성해주세요."))
            return
        }
        if (!requireReadyModel()) return
        if (_state.value.isClassifyingType) return

        _state.update { it.copy(isClassifyingType = true) }
        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val key = engine.classifyContentType(plain)
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
        if (!requireReadyModel()) return
        if (state.isProofreadingBlockId == blockId) return

        _state.update { it.copy(isProofreadingBlockId = blockId) }
        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val revised = engine.proofreadText(originalText)
                if (revised.isNotBlank() && revised != originalText) {
                    applyTextToBlock(blockId, revised)
                    sendEffect(DiaryEffect.ShowToast("본문을 다듬었어요."))
                } else if (revised.isNotBlank()) {
                    sendEffect(DiaryEffect.ShowToast("이미 깔끔한 본문이에요."))
                } else {
                    sendEffect(DiaryEffect.ShowToast("다듬기에 실패했어요. 다시 시도해 주세요."))
                }
            } catch (e: Exception) {
                sendEffect(DiaryEffect.ShowToast("AI 다듬기 오류: ${e.message}"))
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
        if (!requireReadyModel()) return
        if (state.isDecoratingBlockId == blockId) return

        _state.update { it.copy(isDecoratingBlockId = blockId) }
        viewModelScope.launch {
            try {
                val engine = llmEngine ?: return@launch
                val raw = engine.decorateText(originalText)
                val result = DecorateResultParser.parse(raw, originalText)
                if (result.suggestions.isNotEmpty()) {
                    val newFmt = result.toTextFormatting()
                    applyFormattingToBlock(blockId, newFmt)
                    sendEffect(DiaryEffect.ShowToast("강조 추천을 적용했어요. (${result.suggestions.size}건)"))
                } else {
                    sendEffect(DiaryEffect.ShowToast("강조할 단어를 찾지 못했어요."))
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
     */
    private fun runOnDeviceChat(query: String) {
        val currentState = _state.value
        val engine = llmEngine
        if (engine == null || !currentState.isModelReady) {
            sendEffect(DiaryEffect.ShowToast("AI 모델이 아직 준비되지 않았습니다."))
            return
        }

        chatJob?.cancel()

        // 1. 사용자 메시지 및 AI 대기 상태 메시지를 리스트에 적재
        val userMsg = ChatMessage("USER", query)
        val aiMsg = ChatMessage("AI", "생각 중...")
        _state.update { it.copy(chatMessages = it.chatMessages + userMsg + aiMsg) }

        // 2. RAG 검색 수행 (텍스트 키워드 매칭)
        val cleanQuery = query.trim().lowercase()
        val keywords = cleanQuery.split("\\s+".toRegex())
            .filter { it.length > 1 }
            .map { it.replace(Regex("[은는이가을를에에서으로]"), "") }
            .filter { it.isNotBlank() }

        // 일기 매칭
        val matchedDiaries = currentState.diaries.filter { diary ->
            keywords.any { kw -> diary.title.lowercase().contains(kw) || diary.contentText.lowercase().contains(kw) }
        }.take(3)

        // 할 일 매칭
        val matchedTasks = currentState.plannerTasks.filter { task ->
            keywords.any { kw -> task.text.lowercase().contains(kw) }
        }.take(3)

        // 목표 매칭
        val matchedGoals = currentState.goals.filter { goal ->
            keywords.any { kw -> goal.text.lowercase().contains(kw) }
        }.take(3)

        // 매칭 결과가 전혀 없는 경우 최근 3개 데이터를 폴백 컨텍스트로 지정
        // 오늘 및 선택한 날짜의 할 일 정보 별도 기입 준비
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayTasks = currentState.plannerTasks.filter { it.dateString == todayStr }
        val selectedDateStr = currentState.selectedDateString
        val selectedDateTasks = currentState.plannerTasks.filter { it.dateString == selectedDateStr }

        // 매칭 결과가 전혀 없는 경우 최근 3개 데이터를 폴백 컨텍스트로 지정
        val finalDiaries = if (matchedDiaries.isEmpty() && keywords.isNotEmpty()) {
            currentState.diaries.take(3)
        } else matchedDiaries

        val finalTasks = if (matchedTasks.isEmpty() && keywords.isNotEmpty()) {
            currentState.plannerTasks.take(3)
        } else matchedTasks

        val finalGoals = if (matchedGoals.isEmpty() && keywords.isNotEmpty()) {
            currentState.goals.take(3)
        } else matchedGoals

        // 3. 온디바이스 전용 프롬프트 조합 (RAG 정보 주입)
        val systemPrompt = "당신은 사용자의 일기 내용과 일정을 기억하는 다이어리 인공지능 비서예요. " +
                "제공되는 [컨텍스트 기록] 정보에 기반하여 사용자의 질문에만 정직하게 대답해야 해요. " +
                "**필독 규칙**: 절대로 사용자의 일정이나 일기를 상상해서 지어내어 거짓으로 답변하지 마세요. " +
                "오늘의 할 일 목록에 해당하는 일정이 없다면, 가상 일정을 만들지 말고 '오늘 계획된 일정이 등록되어 있지 않아요'라고 솔직하게 대답하세요. " +
                "반드시 100% 한국어로만 답변하고, '~해요'체로 상냥하고 간결하게 대답하세요."

        val userPrompt = buildString {
            append("[컨텍스트 기록]\n")
            append("- 기준 날짜 (오늘): $todayStr\n")
            append("- 선택된 캘린더 날짜: $selectedDateStr\n\n")

            append("■ 오늘($todayStr)의 실제 계획된 할 일 목록:\n")
            if (todayTasks.isEmpty()) {
                append("  - (오늘 계획된 할 일이 등록되어 있지 않습니다)\n\n")
            } else {
                todayTasks.forEach { task ->
                    append("  - 할 일: ${task.text}, 상태: ${if (task.isCompleted) "완료" else "미완료"}\n")
                }
                append("\n")
            }

            if (selectedDateStr != todayStr) {
                append("■ 선택한 날짜($selectedDateStr)의 실제 계획된 할 일 목록:\n")
                if (selectedDateTasks.isEmpty()) {
                    append("  - (이날 계획된 할 일이 등록되어 있지 않습니다)\n\n")
                } else {
                    selectedDateTasks.forEach { task ->
                        append("  - 할 일: ${task.text}, 상태: ${if (task.isCompleted) "완료" else "미완료"}\n")
                    }
                    append("\n")
                }
            }

            if (finalDiaries.isNotEmpty() || finalTasks.isNotEmpty() || finalGoals.isNotEmpty()) {
                append("■ 연관 검색 기록:\n")
                if (finalDiaries.isNotEmpty()) {
                    append("[일기 내용]\n")
                    finalDiaries.forEach { diary ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(diary.timestamp))
                        append("  - 날짜: $dateStr, 제목: ${diary.title}, 본문: ${diary.contentText}, 감정: ${diary.emotion}\n")
                    }
                }
                if (finalTasks.isNotEmpty()) {
                    append("[기타 할 일]\n")
                    finalTasks.forEach { task ->
                        append("  - 계획날짜: ${task.dateString}, 내용: ${task.text}, 상태: ${if (task.isCompleted) "완료" else "미완료"}\n")
                    }
                }
                if (finalGoals.isNotEmpty()) {
                    append("[나의 장기 목표]\n")
                    finalGoals.forEach { goal ->
                        append("  - 목표: ${goal.text}, 상태: ${if (goal.isCompleted) "완료" else "미완료"}\n")
                    }
                }
            }
            append("\n[사용자 질문]\n")
            append("$query\n")
        }

        val prompt = "<start_of_turn>system\n$systemPrompt<end_of_turn>\n" +
                "<start_of_turn>user\n$userPrompt<end_of_turn>\n" +
                "<start_of_turn>model\n"

        // 4. streaming 토큰 누적 수집 콜백 등록
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

        // 5. 비동기 백그라운드 추론 구동
        chatJob = viewModelScope.launch {
            try {
                engine.generateChatResponse(prompt)
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
            sherpaEngine = SherpaEngine.create(actualDir.absolutePath)
            _state.update { it.copy(isSherpaModelReady = true) }
            Log.d("DiaryViewModel", "Sherpa engine ready")
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
                    if (elapsed >= 3600) { launch(Dispatchers.Main) { stopRecording() }; break }
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
     * 작성 완료한 일기 및 AI 분석 결과를 영구 보관합니다.
     */
    private fun saveDiaryDraft() {
        val currentState = _state.value
        val plain = currentState.draftPlainText
        if (plain.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("일기 본문을 작성해주세요."))
            return
        }

        // 세션 제목(첫 HeadingBlock) 비어있으면 자동 입력 유도
        if (currentState.sessionTitle.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("제목 블록을 추가하고 입력하거나 'AI 제목' 버튼으로 자동 생성해주세요."))
            return
        }

        val finalEmotion = if (!currentState.aiAnalysisText.isNullOrBlank()) {
            parseEmotionFromAnalysis(currentState.aiAnalysisText)
        } else {
            currentState.draftEmotion
        }

        val newEntry = DiaryEntry(
            title = currentState.sessionTitle,
            blocks = currentState.draftBlocks,
            content = plain,
            emotion = finalEmotion,
            aiAnalysis = currentState.aiAnalysisText,
            contentType = currentState.draftContentType
        )

        val updated = repository.addEntry(newEntry)
        _state.update {
            it.copy(
                diaries = updated,
                phase = DiaryPhase.LIST,
                draftBlocks = emptyList(),
                draftEmotion = "Neutral",
                draftContentType = com.grepiu.aidiary.data.model.ContentType.DIARY,
                aiAnalysisText = null
            )
        }
        sendEffect(DiaryEffect.ShowToast("${currentState.draftContentType.label}이(가) 성공적으로 저장되었습니다!"))
    }

    /**
     * AI 분석 문장 내 감정 키워드를 통해 감정 태그(Joy, Sadness, Anger, Anxiety, Calm, Neutral)를 판별해내는 함수입니다.
     */
    private fun parseEmotionFromAnalysis(analysisText: String): String {
        // "오늘의 감정 분석:" 파트의 문구를 주요 추출 타겟으로 삼음
        val targetText = analysisText.lines().firstOrNull { it.contains("오늘의 감정 분석") } ?: analysisText
        
        return when {
            targetText.contains("기쁨") || targetText.contains("행복") || targetText.contains("즐거") || targetText.contains("신나") || targetText.contains("뿌듯") -> "Joy"
            targetText.contains("슬픔") || targetText.contains("우울") || targetText.contains("눈물") || targetText.contains("외롭") || targetText.contains("서글") || targetText.contains("상처") -> "Sadness"
            targetText.contains("분노") || targetText.contains("화가") || targetText.contains("짜증") || targetText.contains("스트레스") || targetText.contains("답답") -> "Anger"
            targetText.contains("불안") || targetText.contains("걱정") || targetText.contains("근심") || targetText.contains("초조") || targetText.contains("두려") || targetText.contains("긴장") -> "Anxiety"
            targetText.contains("평온") || targetText.contains("안정") || targetText.contains("편안") || targetText.contains("고요") || targetText.contains("여유") -> "Calm"
            else -> "Neutral"
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
        private const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        private const val SHERPA_DOWNLOAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2"
    }
}
