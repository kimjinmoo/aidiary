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
- **2B 모델 상하 문맥 보강 (v2)**: 모든 보조 액션 프롬프트는 [LLMContextBuilder] 가 통일. proofread/decorate 는 인접 블록 컨텍스트, 챗봇은 멀티턴 Conversation 재사용 + 직전 N턴 raw + 그 이전 1줄 요약 슬라이딩 윈도우, 저장 시점 (글 타입+감정) 은 1회 통합 호출.
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
│       ├── DiaryLLMEngine.kt        # LiteRT-LM 추론 래퍼 (스트리밍 토큰 콜백 + 보조 액션 단발성 프롬프트, 멀티턴 챗봇 Conversation 재사용, 작업별 Sampler 프리셋)
│       ├── LLMContextBuilder.kt     # [NEW] 모든 보조 액션 프롬프트의 계층적 빌더 (도메인/세션/인접/현재입력/제약/예시), 인접 컨텍스트 추출, 슬라이딩 윈도우용 롤링 요약
│       ├── DecorateResult.kt        # AI 꾸미기 추천 JSON 파서 + TextFormatting 변환 (5종 스타일)
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
    │   ├── RichTextToolbar.kt       # B/I/U/S 토글 + 색상 팔레트 + 크기 셀렉터
    │   └── ConfettiOverlay.kt       # [NEW] 목표 완료 시 화면 전체에 날리는 꽃가루 애니메이션 효과 레이어
    ├── screens/
    │   ├── DiarySplashScreen.kt     # [NEW] 애니메이션 스플래시 화면
    │   ├── DiaryListScreen.kt       # 목록 + 썸네일 + 다운로드 카드 (UI/UX 전면 개편, 감정 통계 제거, 책갈피 필터 추가, Confetti 연동)
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
- **음성 인식 다국어**: 작성 화면 `VoiceCard` 하단에 언어 chip row (자동/한국어/English/日本語/中文) 노출. 선택 시 `DiaryIntent.UpdateVoiceLanguage` → `state.voiceLanguage` 즉시 갱신 + `state.isChangingVoiceLanguage = true` + 백그라운드(`Dispatchers.IO`)에서 Sherpa 엔진 dispose + 새 언어로 재초기화. UX: 1) 클릭 즉시 선택 칩이 새 언어로 강조, 2) 그 칩 안에 작은 `CircularProgressIndicator` 가 떠서 "처리중" 알림, 3) 재초기화 완료 시 스피너 사라지고 토스트. 연타 시 `voiceLangJob` generation guard 로 마지막 요청만 로딩 상태를 해제. UI 라벨은 "음성으로 기록하기" (구 "음성으로 일기 쓰기"에서 변경).

## 4.3 작성 보조 AI 액션 (LiteRT-LM 단발성 프롬프트)

`DiaryLLMEngine` 은 일기 분석(스트리밍) 외에 보조 액션용 단발성 추론도 지원합니다. 모두 `state.isModelReady == true` 일 때만 노출됩니다. v2 부터 모든 user prompt 는 [LLMContextBuilder] 가 통일된 계층 구조로 만들고, 작업별 Sampler 프리셋을 적용합니다.

| 액션 | 트리거 UI | 엔진 메서드 | 결과 적용 |
|---|---|---|---|
| 제목 자동 생성 | 제목 입력 옆 `AI 제목` 아이콘 버튼 | `suggestTitle(content, currentTitle, contentTypeLabel)` | `state.draftTitle` |
| 글 타입 자동 분류 | 타입 셀렉터 위 `AI 자동 분류` 텍스트 버튼 | `classifyContentType(content, currentTypeLabel)` | `state.draftContentType` |
| 본문 다듬기 (오탈자/띄어쓰기) | 블록 헤더의 `✦` 메뉴 → `AI 오타 띄어쓰기` | `proofreadText(text, previousTail, nextHead, sessionTitle)` | 해당 블록의 `text` (formatting 유지). 인접 블록 컨텍스트로 어조 일관성 보존 |
| 본문 꾸미기 (색·굵게·이탤릭·밑줄·크기) | 블록 헤더의 `✦` 메뉴 → `AI 꾸미기 (색·크기·밑줄)` | `decorateText(text, previousTail, nextHead, sessionTitle)` → `DecorateResultParser.parse()` → `DecorateResult.toTextFormatting()` | 해당 블록의 `formatting` (start/end 텍스트 길이 내로 클램프). LLM 시스템 프롬프트가 5가지 스타일(bold/italic/underline/color/size) 을 모두 안내하고, 6색 팔레트 + 사이즈 화이트리스트(14/15/18/22/26sp) 만 사용하도록 강제 |
| 마음 분석 + 감정 자동 태그 (TAG AI) | **저장 시 자동 실행** (수동 버튼 없음) | 저장 흐름은 `classifyAndDetectEmotion(title, content, date, currentTypeLabel)` **1회 통합 호출** → `ClassifyAndEmotion(typeKey, emotion, raw)`. 단독 사용 시 `detectEmotion(title, content, date)` → `EmotionResult(raw, emotion)` | 본문 끝에 `ContentBlock.TagAiBlock(emotion)` 자동 추가 + `DiaryEntry.emotion` 코드 매핑. 위로/조언 본문 생성은 제거되어 단순 1-토큰 분류만 수행. **저장 시 통합 호출은 (분류+감정) 을 한 번에 받아 응답 지연 약 50% 감소** |
| 플래너 할 일명 AI 자동 추천 | 플래너 탭 입력란 옆 `AI 자동 플래너명` 아이콘 버튼 (AutoAwesome) | `suggestPlannerTaskName(context)` | `state.suggestedPlannerTaskText` 에 1회성 저장 → UI `LaunchedEffect` 가 입력란에 반영 후 `ClearSuggestedPlannerTask` 인텐트로 비움 |
| 탭별 AI 브리핑 (기록/플래너/목표) | 각 탭 LazyColumn 상단 `AiBriefingCard` 의 새로고침 아이콘 (Refresh) | `generateBriefing(tabKey, context)` — tabKey 별 분기 시스템 프롬프트. **v2: 기존 브리핑이 있으면 `[직전 브리핑]` 섹션으로 컨텍스트에 포함해 추세/변화 비교를 유도** | `state.{diary,planner,goals}Briefing` (영속) + `isBriefing{Diary,Planner,Goals}` (로딩). 다시 요청 가능, 모델 미준비 시 dimmed |
| 온디바이스 AI 챗봇 | 챗봇 UI 의 입력 + 전송 | `generateChatResponse(contextBlock, userQuery)` (멀티턴) | `state.chatMessages` 스트리밍 누적. **v2: 동일 `Conversation` 을 재사용하여 최근 raw N턴 + 그 이전 1줄 누적 요약으로 슬라이딩 윈도우. 히스토리가 6턴 초과 시 자동 압축** |

`LLMContextBuilder` 가 만들어주는 user prompt 공통 구조:

```
[도메인 헤더] 한국어 일기/플래너/장기목표 앱의 온디바이스 AI 어시스턴트
[역할 정의] 이 작업에서 무엇을 해야 하는지
[세션 컨텍스트] 글 제목, 글 타입, 인접 블록 발췌 (proofread/decorate)
[현재 입력] 처리할 텍스트
[제약] 출력 형식, 길이, 화이트리스트
[예시] few-shot 출력 예시 (분류/꾸미기/통합)
```

`SamplerPresets` 4종:
- `CLASSIFY` (topK=10, topP=0.5, temperature=0.05) — 글 타입 / 감정 분류
- `GENERATE_SHORT` (topK=25, topP=0.7, temperature=0.4) — 제목 / 1줄 계획
- `GENERATE_MEDIUM` (topK=30, topP=0.75, temperature=0.5) — 브리핑 / 축하 / 꾸미기 / 챗봇
- `GENERATE_LONG` (topK=25, topP=0.7, temperature=0.2) — 다듬기 / 번역 (low temp 로 충실도 우선)

`LLMContextBuilder.extractAdjacentContext(blocks, targetId, prevChars=120, nextChars=120)` 가 proofread/decorate 의 인접 컨텍스트 추출 헬퍼.

상태/Intent:
- `DiaryState.isSuggestingTitle` / `isClassifyingType` / `isProofreadingBlockId` / `isDecoratingBlockId` / `isSuggestingPlannerTask` / `isGeneratingAnalysis` (저장 시 AI TAG 생성 진행 표시) / `isClassifyingTypeOnSave` (저장 시 타입 재확인 중) / `pendingContentTypeChange` (타입 변경 제안 다이얼로그 1회성 상태) / `suggestedPlannerTaskText` (1회성 추천 결과) / `diaryBriefing` / `plannerBriefing` / `goalsBriefing` (탭별 AI 브리핑 결과) / `isBriefingDiary` / `isBriefingPlanner` / `isBriefingGoals` (브리핑 로딩)
- `DiaryIntent.SuggestTitle` / `ClassifyContentType` / `ProofreadBlock(id)` / `DecorateBlock(id)` / `SuggestPlannerTask` / `ClearSuggestedPlannerTask` / `RequestBriefing(tab)` / `ConfirmContentTypeChange(newType)` / `KeepCurrentContentTypeAndSave` / `CancelContentTypeChange` / `UpdateDraftTitle(text)` / `SaveDiary`
- 수동 `AnalyzeDiary` 인텐트/버튼 제거됨 — 마음 분석은 `SaveDiary` 흐름에 흡수되어 자동 실행
- **상단 제목 입력란**: 키보드 입력이 기본. `state.draftTitle` 에 직접 바인딩되며 AI 추천(`SuggestTitle`) 도 같은 필드를 갱신
- 제목 스타일 피커(`draftTitleStyle`): `state.draftTitle.isNotBlank()` 일 때만 노출 (예전 `hasHeadingBlock` 가드 대체)
- **저장 시 자동 흐름 (v2)**: `SaveDiary` → 본문/제목 검증 → 모델 미준비 시 즉시 저장 / 모델 준비 시 `classifyAndDetectEmotion` **1회 호출**로 (글 타입 + 감정) 동시 수신. 추천 타입이 현재 선택과 다르면 `pendingContentTypeChange` 세팅 + 감정 결과는 `pendingEmotionLabel` 캐시 + `ContentTypeChangeDialog` (3버튼: `"변경하고 저장" / "원래 타입 저장" / "취소"`) 노출. 같으면 즉시 `ContentBlock.TagAiBlock(emotion)` 본문 끝에 append + `DiaryEntry.emotion` 코드 매핑 후 저장. 사용자 응답(변경/유지) 시 캐시된 감정으로 `proceedWithEmotionAndSave()` 가 LLM 재호출 없이 저장. 통합 분석 실패 시 안전 폴백 (현재 타입 유지 / TAG 블록 없이 저장).
- **본문 복사 / AI 한글 번역 (작성 화면)**: 본문 섹션 헤더 우측에 아이콘 2개. `ContentCopy` = 본문 평문 시스템 클립보드 복사, `Translate` = `DiaryLLMEngine.translateToKorean(content)` 호출 (다국어→한국어, 한국어면 자연스러운 다듬기). 결과는 `state.translatedDraft` 에 1회성 저장 + `TranslationResultDialog` (원문/번역문 미리보기 + `[본문에 적용]` / `[복사]` / `[취소]`). 적용 시 마지막 `TextBlock` 의 `text` 끝에 append (없으면 새 TextBlock). `isTranslatingDraft` 로 로딩 표시.

UI 규약:
- 블록 헤더의 `✦` 메뉴는 텍스트가 있는 Heading/Text/Quote 블록에서만 노출
- AI 강조 색상은 6색 팔레트(`#D32F2F #E65100 #F9A825 #2E7D32 #0277BD #6A1B9A`) 중 모델이 선택, 사이즈는 화이트리스트(14/15/18/22/26sp) 내에서만 선택
- 모델 응답이 잘못된 JSON/빈 문자열인 경우 안전 폴백 (원본 유지 + 토스트 안내)
- **AI 플래너 추천 컨텍스트**: `buildPlannerTaskContext` 가 우선순위대로 4개 섹션을 조합해 LLM 프롬프트로 전달 — (1순위) `DiaryIntent.SuggestPlannerTask` 의 입력 필드 (날짜, 시작/종료 시간, 장소, 반복 요일·종료일), (2순위) 같은 날 이미 등록된 계획(시간·장소 포함), (3순위) 미완료 장기 목표(최대 5건), (4순위) 최근 일기 평문(최대 3건, 각 120자). 1순위가 비어 있어도 (날짜는 항상 포함) 동작. 결과는 한국어 1줄, 따옴표·접두사·이모지·번호·마침표 없이 30자 내로 잘라낸다.
- **키보드 가림 방지 (탭 입력)**: `DiaryListScreen` 의 모든 탭(PLANNER/GOALS 등) 입력 폼은 단일 `LazyColumn` (또는 `verticalScroll`) 안에 들어가야 `BringIntoViewRequester` 로 포커스 시 자동 스크롤된다. PLANNER 는 첫 아이템이 입력 카드라 자연 동작. GOALS 는 대시보드/입력/목록을 모두 단일 LazyColumn 으로 통합 + 입력 카드에 `bringIntoViewRequester` 부착 + `onFocusChanged` 에서 `bringIntoView()` 호출.
- **탭별 AI 브리핑 컨텍스트**: `buildBriefingContext(tab, state)` 가 tabKey 별로 분기 — "DIARY": 최근 7건 일기 + 콘텐츠 타입/감정 통계 + 선택 날짜 / "PLANNER": 오늘 계획 + 반복 시리즈 + 예정된 날짜(다음 5개) / "GOALS": 전체 진행률 + 활성 목표 + 최근 달성 + 오늘 플래너. v2 부터 기존에 저장된 브리핑이 있으면 `[직전 브리핑]` 섹션으로 컨텍스트에 함께 주입해 추세/변화 비교를 유도. 결과는 한국어 1단락(2~4줄), 마크다운/이모지/번호 없이 600자 내로 잘라낸다. `AiBriefingCard` 의 새로고침 아이콘으로 다시 요청 가능.
- **AI 챗봇 멀티턴 컨텍스트 (v2.1)**: `DiaryLLMEngine` 의 채팅 경로는 매 호출 **fresh conversation** 을 만든다. LiteRT-LM `Conversation` 은 stateful (내부 history 누적) 이라 인스턴스를 재사용하면서 동시에 prompt 에 multi-turn history 를 임베드하면 history 중복으로 1024 토큰 한도를 2턴째에 초과해 응답이 나오지 않음. 따라서 multi-turn history 는 `[이전 대화 요약] + [최근 raw N턴] + [RAG 컨텍스트(오늘/선택일/연관 일기·할 일·목표)] + [현재 질문]` 형태의 user prompt 에서만 관리되고, `chatHistory` + `chatPriorSummary` 가 그 원천. 히스토리가 임계치(CHAT_SUMMARY_TRIGGER_TURNS=4) 초과 시 오래된 턴을 1줄로 압축해 `chatPriorSummary` 앞에 누적. `ClearChatHistory` 인텐트 또는 `engine.dispose()` 시 요약·히스토리 모두 정리. RAG 일기는 평문 `LLMContextBuilder.truncateChars(..., 280)` 로 컨텍스트 폭주 방지.

## 5. 핵심 컴포넌트 책임

| 컴포넌트 | 책임 | 주의 |
|---|---|---|
| `DiaryRepository` | JSON 직렬화/역직렬화, 인메모리 캐시, 200개 상한 | `addEntry` 초과분/삭제 시 `ImageStorageManager.deleteForEntry` 호출 |
| `ImageStorageManager` | URI/파일 → `filesDir/diary_images/` 복사, 경로 resolve/delete | 항상 상대 경로(`diary_images/...`) 만 DB/JSON 에 저장 |
| `DiaryViewModel` | 인텐트 라우팅, 모델/엔진 초기화, 이미지 IO 코루틴, 블록 라이프사이클 | `WakeLock`, `AudioRecord` 자원 해제 필수 |
| `DiaryLLMEngine` | LiteRT-LM 세션, 토큰 단위 스트리밍 | `dispose()` 호출 전 메모리 누수 |
| `SherpaEngine` | 오프라인 음성→텍스트, 16kHz mono PCM, 다국어(`auto`/`ko`/`en`/`ja`/`zh`/`yue`) | 모델 디렉토리에서 `.onnx` + `tokens.txt` 자동 탐지. `SherpaEngine.create(modelDir, language)` 로 언어 전달. 기본 `auto` |
| `ModelDownloaderV2` | HuggingFace 다운로드, tar.bz2 압축 해제, 에셋/로컬 폴백 | 토큰 단위 진행률 콜백 |
| `DiaryState` | 모든 UI 상태의 immutable 스냅샷 | `draftBlocks` 가 본문 단일 진실, 플래너/목표 및 AI 챗봇 대화 기록 동시 관리 |
| `PlannerRepository` | 할 일 및 목표의 로컬 JSON 영속화 관리 (`PlannerTask` 필드: id / text / isCompleted / dateString / startTime / endTime / location / **seriesId** / timestamp) | `seriesId` 는 반복 계획 일괄 등록 그룹 식별자. 같은 시리즈의 모든 일자에 동일 UUID 가 부여되어 시리즈 단위 삭제가 가능. 데이터가 비어 있으면 시작 시 웰컴 가이드 데이터들을 자동 세팅 |

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
