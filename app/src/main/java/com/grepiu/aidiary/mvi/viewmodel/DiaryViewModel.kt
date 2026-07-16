package com.grepiu.aidiary.mvi.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.repository.DiaryRepository
import com.grepiu.aidiary.data.slm.DeviceCapabilityChecker
import com.grepiu.aidiary.data.slm.ModelDownloaderV2
import com.grepiu.aidiary.data.slm.DiaryLLMEngine
import com.grepiu.aidiary.data.slm.WhisperEngine
import com.grepiu.aidiary.mvi.effect.DiaryEffect
import com.grepiu.aidiary.mvi.intent.DiaryIntent
import com.grepiu.aidiary.mvi.state.DiaryPhase
import com.grepiu.aidiary.mvi.state.DiaryState
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
    private var llmEngine: DiaryLLMEngine? = null
    private var whisperEngine: WhisperEngine? = null

    private var downloadJob: Job? = null
    private var analysisJob: Job? = null
    private var recordingJob: Job? = null
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
                        draftTitle = if (intent.phase == DiaryPhase.WRITE) "" else currentState.draftTitle,
                        draftContent = if (intent.phase == DiaryPhase.WRITE) "" else currentState.draftContent,
                        draftEmotion = if (intent.phase == DiaryPhase.WRITE) "Neutral" else currentState.draftEmotion
                    )
                }
            }
            is DiaryIntent.UpdateDraft -> {
                _state.update { currentState ->
                    currentState.copy(
                        draftTitle = intent.title ?: currentState.draftTitle,
                        draftContent = intent.content ?: currentState.draftContent,
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
        }
    }

    private fun sendEffect(effect: DiaryEffect) {
        viewModelScope.launch {
            _effect.send(effect)
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
        if (currentState.draftContent.isBlank()) {
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
                    title = currentState.draftTitle,
                    content = currentState.draftContent,
                    dateString = dateStr
                )
            } catch (e: Exception) {
                _state.update { it.copy(isGeneratingAnalysis = false) }
                sendEffect(DiaryEffect.ShowToast("AI 분석 오류: ${e.message}"))
            }
        }
    }

    // ===== Whisper =====

    private suspend fun ensureWhisperModelReady() {
        if (downloader.isSherpaModelDownloaded()) {
            initWhisper()
            return
        }
        try {
            _state.update { it.copy(isDownloadingModel = true, modelDownloadProgress = 0f, modelDownloadSizeText = "음성인식 모델 다운로드 중...") }
            downloader.downloadSherpaModel(SHERPA_DOWNLOAD_URL) { bytesRead, totalBytes ->
                val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                val sizeText = "${downloader.toHumanReadableSize(bytesRead)} / ${if (totalBytes > 0) downloader.toHumanReadableSize(totalBytes) else "???"}"
                _state.update { it.copy(modelDownloadProgress = progress, modelDownloadSizeText = sizeText) }
            }.onSuccess {
                _state.update { it.copy(isDownloadingModel = false, modelDownloadSizeText = null) }
                initWhisper()
            }.onFailure { e ->
                _state.update { it.copy(isDownloadingModel = false, modelDownloadSizeText = null) }
                Log.e("DiaryViewModel", "Sherpa model download failed: ${e.message}")
            }
        } catch (_: Exception) {}
    }

    private fun initWhisper() {
        try {
            val modelDir = downloader.getSherpaModelDir()
            if (!modelDir.exists()) return
            // 중첩 디렉토리 찾기
            val actualDir = modelDir.listFiles()
                ?.firstOrNull { it.isDirectory && !it.name.startsWith(".") && File(it, "tokens.txt").exists() }
                ?: modelDir
            whisperEngine = WhisperEngine.create(actualDir.absolutePath)
            _state.update { it.copy(isWhisperModelReady = true) }
            Log.d("DiaryViewModel", "Sherpa engine ready")
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Sherpa init failed: ${e.message}")
        }
    }

    private fun startRecording() {
        if (_state.value.isRecording) return
        if (!_state.value.isWhisperModelReady) {
            sendEffect(DiaryEffect.ShowToast("음성 인식 모델이 준비되지 않았습니다."))
            return
        }
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            sendEffect(DiaryEffect.RequestAudioPermission)
            return
        }

        recordingJob?.cancel()
        _state.update { it.copy(isRecording = true, recordingSeconds = 0) }

        // 화면이 꺼져도 CPU가 녹음을 계속할 수 있도록 WakeLock 획득
        try {
            val pm = getApplication<Application>().getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIDiary:Recording").apply {
                acquire(3600 * 1000L) // 최대 1시간
            }
        } catch (e: Exception) {
            Log.w("DiaryViewModel", "WakeLock 획득 실패: ${e.message}")
        }

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 16000
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
                    sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2)
                this@DiaryViewModel.audioRecord = audioRecord

                val pcmFile = File(getApplication<Application>().cacheDir, "recording.pcm")
                val fos = FileOutputStream(pcmFile)
                val buffer = ShortArray(bufferSize)
                audioRecord.startRecording()

                val startTime = System.currentTimeMillis()
                while (_state.value.isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        fos.write(shortArrayToByteArray(buffer, read))
                    }
                    val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    if (elapsed != _state.value.recordingSeconds) {
                        _state.update { it.copy(recordingSeconds = elapsed) }
                    }
                    // 최대 3600초(1시간) 제한
                    if (elapsed >= 3600) {
                        launch(Dispatchers.Main) { stopRecording() }
                        break
                    }
                }
                audioRecord.stop()
                audioRecord.release()
                fos.close()

                if (pcmFile.length() > 0) {
                    val wavFile = convertPcmToWav(pcmFile, sampleRate)
                    pcmFile.delete()
                    transcribeAudio(wavFile)
                }
            } catch (e: Exception) {
                Log.e("DiaryViewModel", "Recording error: ${e.message}")
                _state.update { it.copy(isRecording = false) }
            }
        }
    }

    private fun stopRecording() {
        _state.update { it.copy(isRecording = false) }
        recordingJob?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun shortArrayToByteArray(shorts: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val s = shorts[i]
            bytes[i * 2] = (s.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun convertPcmToWav(pcmFile: File, sampleRate: Int): File {
        val wavFile = File(getApplication<Application>().cacheDir, "recording.wav")
        val pcmData = pcmFile.readBytes()
        val totalDataLen = pcmData.size + 36
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = ((totalDataLen and 0xFF).toByte())
        header[5] = ((totalDataLen shr 8) and 0xFF).toByte()
        header[6] = ((totalDataLen shr 16) and 0xFF).toByte()
        header[7] = ((totalDataLen shr 24) and 0xFF).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // PCM
        header[20] = 1; header[21] = 0 // PCM format
        header[22] = channels.toByte(); header[23] = 0
        header[24] = ((sampleRate and 0xFF).toByte())
        header[25] = ((sampleRate shr 8) and 0xFF).toByte()
        header[26] = ((sampleRate shr 16) and 0xFF).toByte()
        header[27] = ((sampleRate shr 24) and 0xFF).toByte()
        header[28] = ((byteRate and 0xFF).toByte())
        header[29] = ((byteRate shr 8) and 0xFF).toByte()
        header[30] = ((byteRate shr 16) and 0xFF).toByte()
        header[31] = ((byteRate shr 24) and 0xFF).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0
        header[34] = bitsPerSample.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = ((pcmData.size and 0xFF).toByte())
        header[41] = ((pcmData.size shr 8) and 0xFF).toByte()
        header[42] = ((pcmData.size shr 16) and 0xFF).toByte()
        header[43] = ((pcmData.size shr 24) and 0xFF).toByte()

        FileOutputStream(wavFile).use { it.write(header); it.write(pcmData) }
        return wavFile
    }

    private fun transcribeAudio(wavFile: File) {
        val engine = whisperEngine ?: return
        val fileSizeMB = wavFile.length() / 1024 / 1024
        _state.update { it.copy(isTranscribing = true) }
        // 긴 녹음(5분 이상)은 변환 시간이 오래 걸릴 수 있음을 안내
        if (fileSizeMB > 10) {
            sendEffect(DiaryEffect.ShowToast("긴 녹음 파일입니다. 변환에 수 분이 소요될 수 있습니다."))
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = engine.transcribe(wavFile.absolutePath)
                withContext(Dispatchers.Main) {
                    val currentContent = _state.value.draftContent
                    val appended = if (currentContent.isBlank()) text else "$currentContent\n$text"
                    _state.update { it.copy(draftContent = appended, isTranscribing = false) }
                    sendEffect(DiaryEffect.TranscriptionResult(text))
                }
            } catch (e: Exception) {
                _state.update { it.copy(isTranscribing = false) }
                sendEffect(DiaryEffect.ShowToast("음성 변환 실패: ${e.message}"))
            } finally {
                wavFile.delete()
            }
        }
    }

    /**
     * 작성 완료한 일기 및 AI 분석 결과를 영구 보관합니다.
     */
    private fun saveDiaryDraft() {
        val currentState = _state.value
        if (currentState.draftContent.isBlank()) {
            sendEffect(DiaryEffect.ShowToast("일기 본문을 작성해주세요."))
            return
        }

        // AI 피드백 텍스트 분석하여 감정 태그 자동 매핑
        val finalEmotion = if (!currentState.aiAnalysisText.isNullOrBlank()) {
            parseEmotionFromAnalysis(currentState.aiAnalysisText)
        } else {
            currentState.draftEmotion
        }

        val newEntry = DiaryEntry(
            title = currentState.draftTitle.ifBlank { "오늘의 기록" },
            content = currentState.draftContent,
            emotion = finalEmotion,
            aiAnalysis = currentState.aiAnalysisText
        )

        val updated = repository.addEntry(newEntry)
        _state.update {
            it.copy(
                diaries = updated,
                phase = DiaryPhase.LIST,
                draftTitle = "",
                draftContent = "",
                draftEmotion = "Neutral",
                aiAnalysisText = null
            )
        }
        sendEffect(DiaryEffect.ShowToast("일기가 성공적으로 저장되었습니다!"))
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
        whisperEngine?.dispose()
        super.onCleared()
    }

    companion object {
        private const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        private const val SHERPA_DOWNLOAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-korean-2024-06-24.tar.bz2"
    }
}
