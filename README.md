# AI Diary

Android XR 기반 온디바이스 AI 일기 애플리케이션

## 기술 스택

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Platform**: Android XR (Android 14+, API 34+)
- **Build**: Gradle 9.5.0 (Kotlin DSL)
- **On-Device AI**: Gemma 4 (gemma-4-E2B-it) via LiteRT-LM

## 주요 라이브러리

- Android XR Compose (`androidx.xr.compose`)
- Android XR SceneCore (`androidx.xr.scenecore`)
- Android XR Runtime
- Android XR Extensions (`com.android.extensions.xr`)
- LiteRT-LM (`com.google.ai.edge.litertlm`) - 온디바이스 LLM 추론
- OkHttp 4 - 모델 파일 다운로드

## 온디바이스 AI (Gemma 4)

이 앱은 Google의 Gemma 4 (gemma-4-E2B-it, 2B 파라미터) 모델을 LiteRT-LM 형식으로 변환하여 기기 내부에서 실행합니다.
모든 일기 데이터와 AI 분석은 **완전히 오프라인**으로 처리되어 개인정보가 외부로 유출되지 않습니다.

### 모델 다운로드 흐름

1. 앱 최초 실행 시 모델 파일(`gemma-4-E2B-it.litertlm`, 약 2.3GB)의 존재 여부를 확인
2. 다음 순서로 모델을 확보:
   - 이미 다운로드된 모델이 있는지 확인
   - APK 에셋에 번들된 모델이 있는지 확인 후 복사
   - `/sdcard/Download/`에 수동 복사된 모델 파일 확인
   - Hugging Face에서 직접 다운로드 (`litert-community/gemma-4-E2B-it-litert-lm`)
3. 다운로드 전 디바이스 사양 체크 (RAM 6GB 이상, GPU OpenCL 지원 필수)
4. Wi-Fi 미연결 시 모바일 데이터 경고 표시

### 모델 다운로드 URL

```
https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm
```

## 디바이스 요구사항

- RAM 6GB 이상
- GPU OpenCL 지원 (Adreno, Mali, PowerVR, Immortalis, GeForce, Tegra 등)
- Android 14 (API 34) 이상
- 에뮬레이터는 지원되지 않음 (SwiftShader, LLVMpipe 등)

## 시작하기

1. Android Studio 설치 (Hedgehog 이상)
2. 프로젝트 클론
3. Android SDK 36 및 XR 관련 도구 설치
4. `gradlew assembleDebug` 실행

## 프로젝트 구조

```
app/src/main/java/com/grepiu/aidiary/
├── data/
│   ├── model/DiaryEntry.kt            # 일기 데이터 모델
│   ├── repository/DiaryRepository.kt  # 로컬 JSON 저장소
│   └── slm/
│       ├── DeviceCapabilityChecker.kt # 디바이스 사양 체크
│       ├── DiaryLLMEngine.kt          # LiteRT-LM 추론 엔진
│       └── ModelDownloaderV2.kt       # 모델 다운로더
├── mvi/
│   ├── effect/DiaryEffect.kt          # 부수 효과
│   ├── intent/DiaryIntent.kt          # 사용자 의도
│   ├── state/DiaryState.kt            # 화면 상태
│   └── viewmodel/DiaryViewModel.kt    # 비즈니스 로직
└── ui/
    ├── components/                    # 공통 UI 컴포넌트
    ├── screens/                       # 화면 (목록, 작성, 상세)
    └── theme/                         # Material 3 테마
```

## 라이선스

This project is licensed under the MIT License.

Gemma is provided under and subject to the Gemma Terms of Use found at [ai.google.dev/gemma/terms](https://ai.google.dev/gemma/terms).
