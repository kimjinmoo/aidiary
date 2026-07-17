# AIDiary 프로젝트 구조 (Architecture)

> 본 문서는 신규 진입자 및 AI 에이전트가 코드 수정 전 전체 구조를 빠르게 파악하기 위한 참고 자료입니다.
> 코드 변경 시 이 문서도 함께 갱신합니다.

## 1. 한 줄 요약

- **언어/플랫폼**: Kotlin + Jetpack Compose, Android XR(API 34+) 타깃
- **아키텍처**: MVI 단방향 흐름 (`State` → `Composable` → `Intent` → `ViewModel` → `State`)
- **콘텐츠 모델**: 블록 기반(`ContentBlock` 시즈 클래스) - 텍스트/제목/인용/이미지/구분선
- **콘텐츠 타입**: `ContentType` (DIARY / POST / NOTE) - 일기/새 글/메모 3종, 작성 시 선택. AI 분석은 DIARY 에서만 노출
- **인라인 텍스트 서식**: `TextFormatting` 으로 bold/italic/underline/strikethrough/color/size 6종 지원 (입력 중 인라인 프리뷰)
- **기본 폰트**: Pretendard (Regular/Medium/SemiBold/Bold 4개 weight, OTF, `res/font/`)
- **온디바이스 AI**:
  - 텍스트 분석: Gemma 4 (`gemma-4-E2B-it`, ~2.3GB, LiteRT-LM)
  - 음성 인식: Sherpa-Onnx (오프라인, 한국어 Zipformer)
- **데이터 저장**: 앱 내부 저장소
  - `filesDir/diary_history.json` (일기 메타, `contentType` 필드 포함. 구버전 데이터는 DIARY 로 폴백)
  - `filesDir/diary_images/<uuid>.jpg` (첨부 이미지 원본)
  - `cacheDir/` (녹음/카메라 임시 파일)

## 2. 디렉토리 트리

```
app/src/main/java/com/grepiu/aidiary/
├── MainActivity.kt                  # 엔트리, 2D/XR 라우팅, 권한/이미지 런처
│
├── data/
│   ├── model/
│   │   ├── DiaryEntry.kt            # 일기 엔트리 (id, title, blocks, content 평문, emotion, aiAnalysis, contentType)
│   │   ├── ContentBlock.kt          # 블록 시즈 (Heading/Text/Quote/Image/Divider) + JSON 직렬화
│   │   ├── ContentType.kt           # 콘텐츠 타입 enum (DIARY/POST/NOTE) + storageKey 기반 영속화
│   │   └── TextFormatting.kt        # 인라인 서식 (bold/italic/underline/strikethrough/color/size) + AnnotatedString 변환
│   ├── repository/
│   │   ├── DiaryRepository.kt       # JSON 직렬화, 인메모리 캐시, 200개 상한, 일기 삭제 시 이미지 정리
│   │   ├── ImageStorageManager.kt   # URI/파일 → filesDir/diary_images/ 복사, 블록 경로 resolve/delete
│   │   └── PlannerRepository.kt     # 플래너 할 일(Tasks) 및 장기 목표(Goals)의 로컬 JSON 파일 영속화 관리
│   └── slm/
│       ├── DeviceCapabilityChecker.kt # RAM/SDK/GPU 호환성 판정
│       ├── DiaryLLMEngine.kt        # LiteRT-LM 추론 래퍼 (스트리밍 토큰 콜백 + 보조 액션 단발성 프롬프트)
│       ├── DecorateResult.kt        # AI 강조 추천 JSON 파서 + TextFormatting 변환
│       ├── ModelDownloaderV2.kt     # Gemma/Whisper 모델 다운로드·압축 해제·에셋 복사
│       └── SherpaEngine.kt          # 오프라인 음성 인식 추론
│
├── mvi/
│   ├── state/
│   │   └── DiaryState.kt            # 모든 UI 상태의 단일 진실 공급원 및 DiaryPhase 정의 (SPLASH / LIST / WRITE / DETAIL)
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
    │   ├── DiarySplashScreen.kt     # [NEW] 애니메이션 스플래시 화면
    │   ├── DiaryListScreen.kt       # 목록 + 감정 통계 + 썸네일 + 다운로드 카드
    │   ├── DiaryWriteScreen.kt      # 상단 제목 입력(draftTitle) + 글 타입 + 제목 스타일 + 블록 기반 작성/녹음/AI 분석 트리거
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
- **단일 상태**: 화면 단계(phase), draftTitle(상단 제목), draftBlocks, 모델 준비, 녹음, AI 분석, 이미지 임포트 진행률까지 모두 `DiaryState` 안에 모입니다.
- **부수 효과 분리**: 토스트/권한 다이얼로그/카메라 촬영 URI 등은 `Effect` 채널로 흘려보내 UI 라이프사이클과 분리합니다.

## 4. 콘텐츠 블록 모델

`ContentBlock` (sealed class, `data/model/ContentBlock.kt`)

| 블록 | 필드 | 용도 |
|---|---|---|
| `HeadingBlock` | `text`, `formatting` | 본문 내 '섹션 제목' (큰 굵은 텍스트). 메인 제목은 상단 `draftTitle` 입력란으로 분리됨 |
| `TextBlock` | `text`, `formatting` | 본문 문단 (음성 전사 결과 누적) |
| `QuoteBlock` | `text`, `formatting` | 강조 인용 (이탤릭 + 좌측 컬러 바) |
| `ImageBlock` | `relativePath`, `caption` | `filesDir/diary_images/<uuid>.jpg` 상대 경로 |
| `DividerBlock` | - | 가로 구분선 |
| `TagAiBlock` | `emotion` | 저장 시 AI 가 자동 생성한 'TAG AI' 블록. `emotion` 은 [기쁨, 슬픔, 분노, 불안, 평온] 5 종 중 하나. 위로/조언 본문은 생성하지 않으며 편집 불가(삭제만 가능). `extractPlainText` 에서는 제외되어 재분석 입력 피드백 루프를 방지 |
| `TableBlock` | `rows`, `cols`, `cells` | 표 블록. `cells` 는 `rows × cols` 2D 문자열 리스트, 첫 행이 헤더. 기본 2×2 빈 셀로 시작, `DiaryViewModel` 의 `AddTableRow/Column`, `RemoveTableRow/Column` 으로 동적 크기 조정(최대 30×10). 셀 텍스트는 `extractPlainText` 에서 ` \| ` 로 결합되어 AI 분석 입력에 포함 |

- 모든 블록은 `id` 를 가져 `UpdateBlockText` / `RemoveBlock` 등 키 기반 업데이트에 사용됩니다.
- AI 분석용 평문 추출: `List<ContentBlock>.extractPlainText()` (Heading/Text/Quote 의 `text` 와 `TableBlock.cells` 를 ` \| ` 로 결합. `TagAiBlock` 은 AI 생성 결과이므로 제외).

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
  - `BringIntoViewRequester` 로 포커스/타이핑/엔터 시 부모 `verticalScroll` 이 자동 스크롤
- `RichTextToolbar` (`ui/components/RichTextToolbar.kt`)
  - 1행: B / I / U / S 토글 (활성 시 강조 색 배경)
  - 2행: 기본/9색 팔레트 (Color)
  - 3행: 12 / 15 / 18 / 22 (sp) 크기
- `BlockEditor` 의 `HeadingBlock / TextBlock / QuoteBlock` 분기는 `RichTextEditorBody` 로 묶여 동일 위젯을 사용.
- 음성 전사 결과는 마지막 `TextBlock` 의 `text` 에만 이어붙이며 서식은 보존됨.

## 4.3 작성 보조 AI 액션 (LiteRT-LM 단발성 프롬프트)

`DiaryLLMEngine` 은 일기 분석(스트리밍) 외에 보조 액션용 단발성 추론도 지원합니다. 모두 `state.isModelReady == true` 일 때만 노출됩니다.

| 액션 | 트리거 UI | 엔진 메서드 | 결과 적용 |
|---|---|---|---|
| 제목 자동 생성 | 제목 입력 옆 `AI 제목` 아이콘 버튼 | `suggestTitle(content)` | `state.draftTitle` |
| 글 타입 자동 분류 | 타입 셀렉터 위 `AI 자동 분류` 텍스트 버튼 | `classifyContentType(content)` | `state.draftContentType` |
| 본문 다듬기 (오탈자/띄어쓰기) | 블록 헤더의 `✦` 메뉴 → `AI 다듬기` | `proofreadText(text)` | 해당 블록의 `text` (formatting 유지) |
| 본문 강조 추천 (굵게/색) | 블록 헤더의 `✦` 메뉴 → `AI 강조` | `decorateText(text)` → `DecorateResultParser.parse()` → `DecorateResult.toTextFormatting()` | 해당 블록의 `formatting` (start/end 텍스트 길이 내로 클램프) |
| 마음 분석 + 감정 자동 태그 (TAG AI) | **저장 시 자동 실행** (수동 버튼 없음) | `detectEmotion(title, content, date)` → `DiaryLLMEngine.EmotionResult(raw, emotion)` | 본문 끝에 `ContentBlock.TagAiBlock(emotion)` 자동 추가 + `DiaryEntry.emotion` 코드 매핑. 위로/조언 본문 생성은 제거되어 단순 1-토큰 분류만 수행 (저장 지연 최소화) |

상태/Intent:
- `DiaryState.isSuggestingTitle` / `isClassifyingType` / `isProofreadingBlockId` / `isDecoratingBlockId` / `isGeneratingAnalysis` (저장 시 AI TAG 생성 진행 표시)
- `DiaryIntent.SuggestTitle` / `ClassifyContentType` / `ProofreadBlock(id)` / `DecorateBlock(id)` / `UpdateDraftTitle(text)` / `SaveDiary`
- 수동 `AnalyzeDiary` 인텐트/버튼 제거됨 — 마음 분석은 `SaveDiary` 흐름에 흡수되어 자동 실행
- **상단 제목 입력란**: 키보드 입력이 기본. `state.draftTitle` 에 직접 바인딩되며 AI 추천(`SuggestTitle`) 도 같은 필드를 갱신
- 제목 스타일 피커(`draftTitleStyle`): `state.draftTitle.isNotBlank()` 일 때만 노출 (예전 `hasHeadingBlock` 가드 대체)
- **저장 시 자동 흐름**: `SaveDiary` → 본문 비면 토스트 / 제목 비면 토스트 / 모델 준비 시 `detectEmotion` 호출 → 5 종 감정 라벨(기쁨/슬픔/분노/불안/평온) 중 하나를 `ContentBlock.TagAiBlock(emotion)` 으로 본문 끝에 append + `DiaryEntry.emotion` 코드 매핑. 위로/조언 본문은 생성하지 않으므로 분석 본문 필드(`aiAnalysis`) 는 null. 모델 미준비/실패 시 TAG 블록/감정 갱신 없이 저장만 진행.

UI 규약:
- 블록 헤더의 `✦` 메뉴는 텍스트가 있는 Heading/Text/Quote 블록에서만 노출
- AI 강조 색상은 6색 팔레트(`#D32F2F #E65100 #F9A825 #2E7D32 #0277BD #6A1B9A`) 중 모델이 선택
- 모델 응답이 잘못된 JSON/빈 문자열인 경우 안전 폴백 (원본 유지 + 토스트 안내)

## 5. 핵심 컴포넌트 책임

| 컴포넌트 | 책임 | 주의 |
|---|---|---|
| `DiaryRepository` | JSON 직렬화/역직렬화, 인메모리 캐시, 200개 상한 | `addEntry` 초과분/삭제 시 `ImageStorageManager.deleteForEntry` 호출 |
| `ImageStorageManager` | URI/파일 → `filesDir/diary_images/` 복사, 경로 resolve/delete | 항상 상대 경로(`diary_images/...`) 만 DB/JSON 에 저장 |
| `DiaryViewModel` | 인텐트 라우팅, 모델/엔진 초기화, 이미지 IO 코루틴, 블록 라이프사이클 | `WakeLock`, `AudioRecord` 자원 해제 필수 |
| `DiaryLLMEngine` | LiteRT-LM 세션, 토큰 단위 스트리밍 | `dispose()` 호출 전 메모리 누수 |
| `SherpaEngine` | 오프라인 음성→텍스트, 16kHz mono PCM | `sherpa-onnx-zipformer-korean-2024-06-24` 고정 |
| `ModelDownloaderV2` | HuggingFace 다운로드, tar.bz2 압축 해제, 에셋/로컬 폴백 | 토큰 단위 진행률 콜백 |
| `DiaryState` | 모든 UI 상태의 immutable 스냅샷 | `draftBlocks` 가 본문 단일 진실, 플래너/목표 및 AI 챗봇 대화 기록 동시 관리 |
| `PlannerRepository` | 할 일 및 목표의 로컬 JSON 영속화 관리 | 데이터가 비어 있으면 시작 시 웰컴 가이드 데이터들을 자동 세팅 |

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
| `SPLASH` | 앱 시작 (기본 상태) | `DiarySplashScreen` |
| `LIST` | 스플래시 완료 / 뒤로가기 / 저장 완료 | `DiaryListScreen` |
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
- [ ] `ContentType` 값 추가 시 → `ContentType`, `getContentTypeUI`(ListScreen), `contentTypeMeta`(WriteScreen), `DiaryRepository` 영속화 동시 갱신
- [ ] 모델 포맷(JSON) 변경 시 → `DiaryRepository.parseEntry/persist` 양쪽 동시 수정 + 하위 호환
- [ ] 권한 추가 시 → `AndroidManifest.xml` + `MainActivity` 의 런처 + `DiaryEffect.RequestXxx`
- [ ] JNI/native 추가 시 → `app/libs/jniLibs/` 에 ABI별 .so 배치 확인
- [ ] 한글 IME 조합(ㅇ→아→안→안녕) 입력 보존이 필요하면 `RichTextEditorBody` 의 외부 텍스트 동기화 로직을 수정할 때 `tfv.composition` 을 검사할 것
- [ ] 본 문서도 함께 갱신
