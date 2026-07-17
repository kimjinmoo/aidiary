# AIDiary 프로젝트 구조 (Architecture)

> 본 문서는 신규 진입자 및 AI 에이전트가 코드 수정 전 전체 구조를 빠르게 파악하기 위한 참고 자료입니다.
> 코드 변경 시 이 문서도 함께 갱신합니다.

## 1. 한 줄 요약

- **언어/플랫폼**: Kotlin + Jetpack Compose, Android XR(API 34+) 타깃
- **아키텍처**: MVI 단방향 흐름 (`State` → `Composable` → `Intent` → `ViewModel` → `State`)
- **콘텐츠 모델**: 블록 기반(`ContentBlock` 시즈 클래스) - 텍스트/제목/인용/이미지/구분선
- **인라인 텍스트 서식**: `TextFormatting` 으로 bold/italic/underline/strikethrough/color/size 6종 지원 (입력 중 인라인 프리뷰)
- **기본 폰트**: Pretendard (Regular/Medium/SemiBold/Bold 4개 weight, OTF, `res/font/`)
- **온디바이스 AI**:
  - 텍스트 분석: Gemma 4 (`gemma-4-E2B-it`, ~2.3GB, LiteRT-LM)
  - 음성 인식: Sherpa-Onnx (오프라인, 한국어 Zipformer)
- **데이터 저장**: 앱 내부 저장소
  - `filesDir/diary_history.json` (일기 메타)
  - `filesDir/diary_images/<uuid>.jpg` (첨부 이미지 원본)
  - `cacheDir/` (녹음/카메라 임시 파일)

## 2. 디렉토리 트리

```
app/src/main/java/com/grepiu/aidiary/
├── MainActivity.kt                  # 엔트리, 2D/XR 라우팅, 권한/이미지 런처
│
├── data/
│   ├── model/
│   │   ├── DiaryEntry.kt            # 일기 엔트리 (id, title, blocks, content 평문, emotion, aiAnalysis)
│   │   ├── ContentBlock.kt          # 블록 시즈 (Heading/Text/Quote/Image/Divider) + JSON 직렬화
│   │   └── TextFormatting.kt        # 인라인 서식 (bold/italic/underline/strikethrough/color/size) + AnnotatedString 변환
│   ├── repository/
│   │   ├── DiaryRepository.kt       # JSON 직렬화, 인메모리 캐시, 200개 상한, 일기 삭제 시 이미지 정리
│   │   └── ImageStorageManager.kt   # URI/파일 → filesDir/diary_images/ 복사, 블록 경로 resolve/delete
│   └── slm/
│       ├── DeviceCapabilityChecker.kt # RAM/SDK/GPU 호환성 판정
│       ├── DiaryLLMEngine.kt        # LiteRT-LM 추론 래퍼 (스트리밍 토큰 콜백)
│       ├── ModelDownloaderV2.kt     # Gemma/Whisper 모델 다운로드·압축 해제·에셋 복사
│       └── SherpaEngine.kt          # 오프라인 음성 인식 추론
│
├── mvi/
│   ├── state/
│   │   ├── DiaryPhase.kt            # 화면 단계 (LIST / WRITE / DETAIL)
│   │   └── DiaryState.kt            # 모든 UI 상태의 단일 진실 공급원 (draftBlocks 포함)
│   ├── intent/DiaryIntent.kt        # 사용자 의도 (UpdateBlockText 가 text+formatting 동시 갱신)
│   ├── effect/DiaryEffect.kt        # 1회성 부수 효과 (카메라 권한/촬영 요청)
│   └── viewmodel/DiaryViewModel.kt  # 비즈니스 로직·엔진·이미지 import 라이프사이클
│
└── ui/
    ├── components/
    │   ├── DownloadStatusCard.kt    # 목록 상단 AI 모델 안내 카드
    │   ├── BlockRenderer.kt         # 읽기 전용 블록 렌더러 (TextFormatting 반영)
    │   ├── BlockEditor.kt           # 작성 화면용 블록 입력/편집 + AddBlockBar
    │   ├── RichTextField.kt         # 인라인 서식 프리뷰가 있는 텍스트 에디터 (Text + BasicTextField 오버레이)
    │   └── RichTextToolbar.kt       # B/I/U/S 토글 + 색상 팔레트 + 크기 셀렉터
    ├── screens/
    │   ├── DiaryListScreen.kt       # 목록 + 감정 통계 + 썸네일 + 다운로드 카드
    │   ├── DiaryWriteScreen.kt      # 블록 기반 작성/녹음/AI 분석 트리거
    │   └── DiaryDetailScreen.kt     # 상세 + AI 멘토 리포트 (블록 렌더러 사용)
    └── theme/                       # Material3 Color/Type/Theme (Pretendard 폰트 패밀리)
```

## 3. 데이터 흐름 (MVI)

```
[User Gesture] ──▶ [Composable onClick] ──▶ viewModel.processIntent(Intent)
                                              │
                                              ▼
                          [ViewModel] 상태 변경 + 코루틴 잡 + 엔진/이미지 IO
                                              │
                                              ▼
                          [MutableStateFlow<DiaryState>.update(...)]
                                              │
                                              ▼
                          [Composable collectAsState] ──▶ [Rebuild UI]
                                              │
                                              ▼ (선택)
                          [Channel<DiaryEffect>] ──▶ LaunchedEffect(Toast/Camera/...)
```

- **단방향**: UI는 오직 `state` 만 읽고, `Intent` 만 보냅니다.
- **단일 상태**: 화면 단계(phase), draftBlocks, 모델 준비, 녹음, AI 분석, 이미지 임포트 진행률까지 모두 `DiaryState` 안에 모입니다.
- **부수 효과 분리**: 토스트/권한 다이얼로그/카메라 촬영 URI 등은 `Effect` 채널로 흘려보내 UI 라이프사이클과 분리합니다.

## 4. 콘텐츠 블록 모델

`ContentBlock` (sealed class, `data/model/ContentBlock.kt`)

| 블록 | 필드 | 용도 |
|---|---|---|
| `HeadingBlock` | `text`, `formatting` | 섹션 제목 (큰 굵은 텍스트) |
| `TextBlock` | `text`, `formatting` | 본문 문단 (음성 전사 결과 누적) |
| `QuoteBlock` | `text`, `formatting` | 강조 인용 (이탤릭 + 좌측 컬러 바) |
| `ImageBlock` | `relativePath`, `caption` | `filesDir/diary_images/<uuid>.jpg` 상대 경로 |
| `DividerBlock` | - | 가로 구분선 |

- 모든 블록은 `id` 를 가져 `UpdateBlockText` / `RemoveBlock` 등 키 기반 업데이트에 사용됩니다.
- AI 분석용 평문 추출: `List<ContentBlock>.extractPlainText()` (Heading/Text/Quote 의 `text` 만 결합).

## 4.1 인라인 텍스트 서식 (`TextFormatting`)

`data/model/TextFormatting.kt` — 각 스타일은 `IntRange` 리스트로 표현.

| 속성 | 타입 | 예시 |
|---|---|---|
| `boldRanges` | `List<IntRange>` | `[(0..4), (10..12)]` |
| `italicRanges` | `List<IntRange>` | `[(0..2)]` |
| `underlineRanges` | `List<IntRange>` | `[(5..7)]` |
| `strikethroughRanges` | `List<IntRange>` | `[]` |
| `colorRanges` | `List<Pair<IntRange, String>>` | `[(0..4) to "#D32F2F"]` |
| `sizeRanges` | `List<Pair<IntRange, Int>>` | `[(0..4) to 18]` |

주요 API:
- `toAnnotatedString(text, baseColor)`: 현재 서식을 적용한 `AnnotatedString` 반환
- `shift(oldText, newText)`: 텍스트 변경에 따른 범위 이동/분할/병합
- `toggleBold/Italic/Underline/Strikethrough(range)`: 영역 단위 토글
- `setColor(range, hex)` / `setSize(range, sp)`: 영역에 값 설정 (null 이면 제거)
- `toJson()` / `TextFormatting.fromJson(JSONObject)`: 영속화

## 4.2 리치 텍스트 에디터 UX

- `RichTextField` (`ui/components/RichTextField.kt`)
  - 뒤쪽: 서식이 적용된 `Text(AnnotatedString)` 으로 인라인 프리뷰
  - 앞쪽: 투명 텍스트 `BasicTextField` 가 실제 입력 + 커서 처리
  - 둘은 동일한 TextStyle / padding 으로 정렬
- `RichTextToolbar` (`ui/components/RichTextToolbar.kt`)
  - 1행: B / I / U / S 토글 (활성 시 강조 색 배경)
  - 2행: 기본/9색 팔레트 (Color)
  - 3행: 12 / 15 / 18 / 22 (sp) 크기
- `BlockEditor` 의 `HeadingBlock / TextBlock / QuoteBlock` 분기는 `RichTextEditorBody` 로 묶여 동일 위젯을 사용.
- 음성 전사 결과는 마지막 `TextBlock` 의 `text` 에만 이어붙이며 서식은 보존됨.

## 5. 핵심 컴포넌트 책임

| 컴포넌트 | 책임 | 주의 |
|---|---|---|
| `DiaryRepository` | JSON 직렬화/역직렬화, 인메모리 캐시, 200개 상한 | `addEntry` 초과분/삭제 시 `ImageStorageManager.deleteForEntry` 호출 |
| `ImageStorageManager` | URI/파일 → `filesDir/diary_images/` 복사, 경로 resolve/delete | 항상 상대 경로(`diary_images/...`) 만 DB/JSON 에 저장 |
| `DiaryViewModel` | 인텐트 라우팅, 모델/엔진 초기화, 이미지 IO 코루틴, 블록 라이프사이클 | `WakeLock`, `AudioRecord` 자원 해제 필수 |
| `DiaryLLMEngine` | LiteRT-LM 세션, 토큰 단위 스트리밍 | `dispose()` 호출 전 메모리 누수 |
| `SherpaEngine` | 오프라인 음성→텍스트, 16kHz mono PCM | `sherpa-onnx-zipformer-korean-2024-06-24` 고정 |
| `ModelDownloaderV2` | HuggingFace 다운로드, tar.bz2 압축 해제, 에셋/로컬 폴백 | 토큰 단위 진행률 콜백 |
| `DiaryState` | 모든 UI 상태의 immutable 스냅샷 | `draftBlocks` 가 본문 단일 진실, 평문은 `draftPlainText` 로 노출 |

## 6. 의존성/플러그인 위치

- **네이티브 라이브러리**: `app/libs/jniLibs/` (Sherpa `.so` 직접 관리)
- **에셋 모델**: `app/src/main/assets/` (선택적 번들 모델)
- **버전 카탈로그**: `gradle/libs.versions.toml` (`coil-compose` 포함)
- **앱 모듈 의존성**: `app/build.gradle.kts`
- **FileProvider 경로**: `app/src/main/res/xml/file_paths.xml`
- **폰트 리소스**: `app/src/main/res/font/pretendard_*.otf` (Regular/Medium/SemiBold/Bold)

## 7. 화면 전환 규약

`DiaryState.phase` 값으로 분기:

| Phase | 진입 Intent | 사용 화면 |
|---|---|---|
| `LIST` | 앱 시작 / 뒤로가기 / 저장 완료 | `DiaryListScreen` |
| `WRITE` | FAB / `NavigateTo(WRITE)` | `DiaryWriteScreen` |
| `DETAIL` | `NavigateTo(DETAIL, diary)` | `DiaryDetailScreen` |

상세 보기의 선택 일기는 `state.selectedDiary` 에 담깁니다.

## 8. 모델 자산/캐시/저장소 디렉토리

- **모델 파일**: `context.filesDir/models/`
- **Sherpa 모델**: `context.filesDir/sherpa/`
- **녹음 임시 파일**: `context.cacheDir/recording.pcm`, `recording.wav` (변환 후 삭제)
- **다이어리 JSON**: `context.filesDir/diary_history.json`
- **다이어리 첨부 이미지**: `context.filesDir/diary_images/<uuid>.jpg`
- **카메라 촬영 임시 파일**: `context.cacheDir/capture_<ts>.jpg` (FileProvider 로 노출)

## 9. 권한 런처 매핑 (MainActivity)

| 런처 | 트리거 | 인텐트 |
|---|---|---|
| `audioPermissionLauncher` | `DiaryEffect.RequestAudioPermission` | `RECORD_AUDIO` |
| `cameraPermissionLauncher` | `DiaryEffect.RequestCameraPermission` | `CAMERA` |
| `pickImageLauncher` (PhotoPicker) | WRITE 화면 "+ 갤러리" | `ImagePicked(uri)` |
| `takePictureLauncher` | `DiaryEffect.LaunchCamera(uri)` | `CameraImageCaptured(path)` |

## 10. 변경 시 체크리스트

- [ ] `DiaryState` 필드 추가 시 → `DiaryViewModel` 의 모든 `copy(...)` 갱신
- [ ] `DiaryIntent` 추가 시 → `processIntent` 의 `when` 분기 추가
- [ ] 새 `ContentBlock` 타입 추가 시 → `ContentBlock.fromJson`, `toJson`, `BlockRenderer`, `BlockEditor`, `blockTypeMeta` 동시 갱신
- [ ] `TextFormatting` 속성 추가 시 → `TextFormatting` 데이터 클래스, JSON 직렬화, `toAnnotatedString`, `RichTextToolbar`, `is*At` 헬퍼 동시 갱신
- [ ] 모델 포맷(JSON) 변경 시 → `DiaryRepository.parseEntry/persist` 양쪽 동시 수정 + 하위 호환
- [ ] 권한 추가 시 → `AndroidManifest.xml` + `MainActivity` 의 런처 + `DiaryEffect.RequestXxx`
- [ ] JNI/native 추가 시 → `app/libs/jniLibs/` 에 ABI별 .so 배치 확인
- [ ] 본 문서도 함께 갱신
