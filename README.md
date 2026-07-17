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

### Whisper 음성 인식

whisper.cpp의 `ggml-small-q8_0` 모델을 사용하여 일기 작성 시 음성 녹음 → 텍스트 변환 기능을 제공합니다.
녹음된 음성은 기기 내부에서 Whisper 모델을 통해 100% 온디바이스로 텍스트로 변환됩니다.

### 모델 다운로드 흐름

1. 앱 최초 실행 시 모델 파일 존재 여부를 확인
2. 다음 순서로 모델을 확보:
   - 이미 다운로드된 모델 확인 (Gemma: ~2.3GB, Whisper: ~466MB)
   - APK 에셋에 번들된 모델 확인 후 복사
   - `/sdcard/Download/` 수동 복사 확인
   - Hugging Face에서 직접 다운로드
3. 디바이스 사양 체크 (RAM 6GB 이상, GPU OpenCL 권장)

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

## 고려 사항 (TODO)

### FTS5 호환성 — 검색 인덱스 백엔드 선택

현재 `data/repository/DiarySearchDatabase.kt` 는 시스템 SQLite에 FTS5 가 있으면 `trigram` 가상 테이블을, 없으면 일반 테이블 + LIKE 로 폴백. 모든 기기에서 FTS5 를 보장하고 싶을 때의 옵션:

- [ ] **현 구조 유지** — 200건 상한(`DiaryRepository.kt`)에선 LIKE 폴백도 충분. 비용 0
- [ ] **requery/sqlite-android** — FTS5 + R\*Tree + JSON1 번들. 그러나 2020년 이후 유지보수 중단. APK +1~2MB. `io.requery.android.database.sqlite.*` 로 API 교체
- [ ] **WCDB (`com.tencent.wcdb`)** — Tencent가 WeChat 에서 검증, 활발한 유지보수. 다만 독자 ORM API 로 `DiarySearchDatabase.kt` 전면 리라이트. APK +3~5MB. C++ JNI 의존
- [ ] **SQLCipher (`net.zetetic:sqlcipher-android`)** — AES-256 DB 암호화. `SupportFactory` 패턴으로 `SQLiteOpenHelper` 마이그레이션 최소. Android Keystore 기반 키 관리 추가 필요 (+30~50줄). APK +2~4MB
- [ ] **Android NDK + SQLite 소스 직접 빌드** — FTS5 만 필요할 때 가장 가벼움 (~수백 KB). 빌드 스크립트 작성/유지 부담

**선택 가이드**

| 시나리오 | 추천 |
|---|---|
| 검색 인덱스 암호화가 진짜 목적 | SQLCipher |
| FTS5 일관성만 필요 (200건 안팎) | 현 구조 유지 |
| Tencent급 ORM + 장기 유지보수 | WCDB |
| 최소 변경 + 보안 패치 추적 | SQLCipher 또는 NDK 직접 빌드 |

**결정 필요**: AIDiary 는 JSON 이 1차 진실이고 SQLite 는 부가 인덱스. 보안 요구가 명시되기 전까지는 현 구조 유지가 합리적.

## 라이선스

This project is licensed under the MIT License.

Gemma is provided under and subject to the Gemma Terms of Use found at [ai.google.dev/gemma/terms](https://ai.google.dev/gemma/terms).
