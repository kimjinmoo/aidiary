# AIDiary 프로젝트 구조 (Architecture)

> 본 문서는 신규 진입자 및 AI 에이전트가 코드 수정 전 전체 구조를 빠르게 파악하기 위한 참고 자료입니다.
> 코드 변경 시 이 문서도 함께 갱신합니다.

---

## 목차

1. [한 줄 요약](#1-한-줄-요약)
2. [디렉토리 트리](#2-디렉토리-트리)
3. [데이터 흐름 (MVI)](#3-데이터-흐름-mvi)
4. [콘텐츠 블록 모델](#4-콘텐츠-블록-모델)
5. [입체 미디어 자동 감지 (3D)](#5-입체-미디어-자동-감지-3d)
6. [샌드박스 저장 정책 / Export 준비](#6-샌드박스-저장-정책--export-준비)
7. [핵심 컴포넌트 책임](#7-핵심-컴포넌트-책임)
8. [의존성/플러그인 위치](#8-의존성플러그인-위치)
9. [화면 전환 규약](#9-화면-전환-규약)
10. [모델 자산/캐시/저장소 디렉토리](#10-모델-자산캐시저장소-디렉토리)
11. [권한 런처 매핑 (MainActivity)](#11-권한-런처-매핑-mainactivity)
12. [변경 시 체크리스트](#12-변경-시-체크리스트)

---

## 1. 한 줄 요약

| 항목 | 내용 |
|---|---|
| **언어/플랫폼** | Kotlin + Jetpack Compose, Android XR (API 34+) 타깃 |
| **아키텍처** | MVI 단방향 (`State` → `Composable` → `Intent` → `ViewModel` → `State`) |
| **콘텐츠 모델** | 블록 기반 `ContentBlock` sealed class (텍스트/제목/인용/이미지/구분선/위치/입체 미디어) |
| **콘텐츠 타입** | `ContentType` (DIARY / POST / NOTE) — AI 분석은 DIARY 에서만 |
| **인라인 텍스트 서식** | `TextFormatting` (bold/italic/underline/strikethrough/color/size 6종) |
| **기본 폰트** | Pretendard (Regular/Medium/SemiBold/Bold OTF) |
| **색상 테마 (v4)** | `AppTheme` enum 3종 (ATLAS 기본 / BLOSSOM / ECLIPSE). 설정 화면 선택 + SharedPreferences 영속화 |
| **온디바이스 AI** | 텍스트 분석: Gemma 4 (`gemma-4-E2B-it`, ~2.3GB, LiteRT-LM) |
| | 음성 인식: Sherpa-Onnx SenseVoice (오프라인, 다국어: ko/en/ja/zh/yue + auto) |
| **온디바이스 AI 챗봇** | 5종 원터치 프리셋 (이번주 써머리 / 이번달 감정 / 최근 기록 / 다음주 계획 / 현재 목표 현황) + 가로 슬라이더 & 복사/스트리밍 고도화 UI |
| **일기 수정 기능 (v4.3)** | `DiaryIntent.EditDiary` 인텐트를 통한 기존 일기 작성 모드(`DiaryPhase.WRITE`) 로드, 제목/블록/타입/감정 기존 데이터 유지 및 `editingDiaryId` 기반 기존 entry 업데이트 보존 |
| **2B 모델 컨텍스트 보강 (v2)** | 모든 보조 액션 프롬프트는 `LLMContextBuilder` 통일, 인접 블록 + 슬라이딩 윈도우 |
| **입체 미디어 자동 감지 (v3.2)** | 사진/영상 첨부 시 3D 포맷(MPO/HEIC aux/Stereo EXIF/Stereo MP4/MOV spatial/MV-HEVC) 자동 감지. 2D 도 첨부 가능. 영상 30초 가드. VideoView 재생 |
| **데이터 저장 (v3)** | Room DB (메인) + 파일 시스템 샌드박스. 향후 export 기능 추가 시 그대로 활용 |
| **확장성 (v3.1)** | 200건 상한 제거, 무제한. FTS5 본문 6,000자. 페이지네이션 50건 |

### 저장 위치 요약

```
context.filesDir/
├── diary.db                              # Room v2 (diary / block / diary_fts)
├── diary_images/                         # 첨부 사진 (2D/3D/HEIC aux/MPO view)
│   ├── <uuid>.jpg                        # 2D 사진
│   ├── <uuid>_L.jpg, _R.jpg              # MPO 좌/우
│   └── <uuid>_main.jpg, _aux.jpg          # HEIC aux primary/aux
├── diary_videos/                         # 첨부 영상 (sandbox)
│   └── <uuid>.<ext>                      # 2D/3D 영상 (≤ 30s)
├── models/                               # Gemma 4 litertlm
├── sherpa/                               # Sherpa 음성 모델
├── backup/                               # 구버전 JSON import 후 이동
└── diary_history.json                    # (구버전) 1회만 import

context.cacheDir/
├── recording.pcm, recording.wav          # 음성 녹음 임시
└── capture_<ts>.jpg                      # 카메라 촬영 임시 (FileProvider)
```

---

## 2. 디렉토리 트리

```
app/src/main/java/com/grepiu/aidiary/
├── MainActivity.kt                  # 엔트리, 2D/XR 라우팅, 권한/이미지/비디오 런처
│
├── data/
│   ├── model/
│   │   ├── DiaryEntry.kt            # 일기 엔트리 (id, title, blocks, content 평문, emotion, aiAnalysis, contentType)
│   │   ├── ContentBlock.kt          # 블록 sealed class (Heading/Text/Quote/Image/Divider/Location/TagAi/Table/SpatialMedia)
│   │   │                            # + SpatialMediaType / SpatialCaptureMode enum
│   │   │                            # + fromJson / toJson / extractPlainText
│   │   ├── ContentType.kt           # DIARY/POST/NOTE enum + storageKey 영속화
│   │   └── TextFormatting.kt        # 인라인 서식 + AnnotatedString 변환
│   │
│   ├── repository/
│   │   ├── DiaryRepository.kt       # Room CRUD + 페이지네이션 + FTS5 검색 (LIKE 폴백)
│   │   ├── DiaryMeta.kt             # [v3.1] 화면 표시용 경량 데이터
│   │   ├── DiaryDatabase.kt         # [v2] Room DB (diary / block v2 + spatial 컬럼 / FTS5 / MIGRATION_1_2)
│   │   ├── DiaryDao.kt              # 메타/페이지네이션/Blocking/LIKE 폴백
│   │   ├── DiaryEntity.kt           # 일기 메타 @Entity
│   │   ├── BlockEntity.kt           # [v2] 블록 @Entity (spatial_type / spatial_paths_json / spatial_capture_mode)
│   │   ├── DiaryWithBlocks.kt       # @Relation 매핑
│   │   ├── DiarySearchDao.kt        # FTS5 raw query
│   │   ├── DiaryMetaRow.kt          # 화면 표시용 메타 row
│   │   ├── LegacyJsonImporter.kt    # 구버전 diary_history.json → Room 1회 import
│   │   ├── ImageStorageManager.kt   # 사진 sandbox (2D/3D/Mono 자동 분기) + MPO/HEIC aux view 추출
│   │   ├── VideoStorageManager.kt   # [NEW] 영상 sandbox + 30초 가드 + getVideoDurationMs(MetadataRetriever)
│   │   └── PlannerRepository.kt     # 플래너/목표 로컬 JSON 영속화
│   │
│   └── slm/
│       ├── DeviceCapabilityChecker.kt # RAM/SDK/GPU 호환성 판정 (RAM 6GB 이하 시 LLM 설치 사양 불가능, STT 모델만 설치/사용 허용)
│       ├── DiaryLLMEngine.kt        # LiteRT-LM 추론 (스트리밍 + 멀티턴 + 작업별 Sampler)
│       ├── LLMContextBuilder.kt     # 보조 액션 프롬프트 계층적 빌더
│       ├── DecorateResult.kt        # AI 꾸미기 JSON 파서 + TextFormatting 변환
│       ├── ModelDownloaderV2.kt     # Gemma/Whisper 다운로드
│       ├── SherpaEngine.kt          # 오프라인 음성 인식
│       ├── ImageFormatDetector.kt   # [NEW] MPO / HEIC aux / Stereo EXIF / Plain2D 자동 감지
│       └── VideoFormatDetector.kt   # [NEW] ISOBMFF 박스 파서 (자체 구현) — 2-track / st3d / MV-HEVC / Plain2D
│
├── mvi/
│   ├── state/
│   │   └── DiaryState.kt            # 단일 진실 공급원 + DiaryPhase
│   ├── intent/DiaryIntent.kt        # 사용자 의도 (UpdateBlockText 가 text+formatting 동시 갱신)
│   │                                # [v3.2] VideoPicked 추가
│   ├── effect/DiaryEffect.kt        # 1회성 부수 효과
│   │                                # [v3.2] LaunchVideoPicker 추가
│   └── viewmodel/DiaryViewModel.kt  # 비즈니스 로직 / 엔진 라이프사이클 / import 처리
│                                    # [v3.2] importPickedVideo (30s 가드 + 3D 분기)
│
└── ui/
    ├── components/
    │   ├── DownloadStatusCard.kt    # 목록 상단 AI 모델 안내 카드
    │   ├── DeviceUnsupportedModal.kt # [NEW] 2030 타깃 세련된 AI 사양 안내 감성 모달
    │   ├── OpenSourceLicenseModalDialog.kt # [NEW] 2030 타깃 오픈소스 라이선스 고지 모달
    │   ├── BlockRenderer.kt         # 읽기 전용 블록 렌더러 (SpatialMediaBlockView 포함)
    │   ├── BlockEditor.kt           # 작성 화면용 블록 입력/편집 + AddBlockBar
    │   ├── RichTextField.kt         # 인라인 서식 프리뷰 텍스트 에디터
    │   ├── RichTextToolbar.kt       # B/I/U/S + 색상 팔레트 + 크기 셀렉터
    │   └── ConfettiOverlay.kt       # 목표 완료 꽃가루 애니메이션
    ├── screens/
    │   ├── DiarySplashScreen.kt     # 애니메이션 스플래시
    │   ├── DiaryListScreen.kt       # [v3.1] DiaryMeta 만 렌더, 검색바 + 페이지네이션
    │   ├── DiarySettingsScreen.kt   # [NEW] 백업/복원, 버전 및 라이선스를 제공하는 전문가 수준 설정 화면
    │   ├── DiaryWriteScreen.kt      # 제목 + 글 타입 + 블록 작성 + AddBlockBar
    │   └── DiaryDetailScreen.kt     # 상세 + AI 멘토 리포트
    └── theme/                       # Material3 (Pretendard)
                                     # [v4] AppTheme enum (BLOSSOM/ATLAS/ECLIPSE) + Color.kt v4 팔레트
                                     # AIDiaryTheme(appTheme) 로 전달

app/src/test/java/com/grepiu/aidiary/
├── data/
│   └── slm/
│       ├── ImageFormatDetectorTest.kt   # MPO / HEIC aux / Plain2D 픽스처
│       └── VideoFormatDetectorTest.kt   # 2-track / st3d / MV-HEVC / Plain2D 픽스처
└── ExampleUnitTest.kt
```

---

## 3. 데이터 흐름 (MVI)

```
[User Gesture] ──▶ [Composable onClick] ──▶ viewModel.processIntent(Intent)
                                              │
                                              ▼
                          [ViewModel] 상태 변경 + 코루틴 잡 + 엔진/IO
                                              │
                                              ▼
                          [MutableStateFlow<DiaryState>.update(...)]
                                              │
                                              ▼
                          [Composable collectAsState] ──▶ [Rebuild UI]
                                              │
                                              ▼ (선택)
                          [Channel<DiaryEffect>] ──▶ LaunchedEffect(Toast/VideoPicker/...)
```

- **단방향**: UI는 `state` 만 읽고, `Intent` 만 보냄
- **단일 상태**: 화면 단계(phase), draftTitle, draftBlocks, 모델 준비, 녹음, AI 분석, 이미지/영상 import 진행률 모두 `DiaryState`
- **부수 효과 분리**: 토스트/권한 다이얼로그/카메라 URI/VideoPicker 등은 `Effect` 채널

---

## 4. 콘텐츠 블록 모델

`ContentBlock` (sealed class, `data/model/ContentBlock.kt`)

| 블록 | 필드 | 용도 |
|---|---|---|
| `HeadingBlock` | `text`, `formatting` | 본문 내 '섹션 제목'. 메인 제목은 상단 `draftTitle` 분리 |
| `TextBlock` | `text`, `formatting` | 본문 문단 (음성 전사 누적) |
| `QuoteBlock` | `text`, `formatting` | 강조 인용 (이탤릭 + 좌측 컬러 바) |
| `ImageBlock` | `relativePath`, `caption` | `filesDir/diary_images/<uuid>.jpg`. **구 데이터 호환용** (레거시). 신규는 모두 `SpatialMediaBlock` |
| `DividerBlock` | - | 가로 구분선 |
| `TagAiBlock` | `emotion` | 저장 시 AI 자동 생성. 편집 불가 (삭제만 가능) |
| `TableBlock` | `rows`, `cols`, `cells` | 표 블록 (최대 30×10) |
| `LocationBlock` | `lat`, `lng`, `address` | 현재 위치 (Geocoder) |
| **`SpatialMediaBlock`** | `mediaType`, `paths`, `captureMode`, `caption` | **[v3.2]** 모든 미디어 (2D/3D 사진 + 영상) 단일 모델. 자동 감지 분기 |

### 4.1 `SpatialMediaBlock` 상세 (v3.2)

```kotlin
data class SpatialMediaBlock(
    override val id: String = UUID.randomUUID().toString(),
    val mediaType: SpatialMediaType,         // PHOTO | VIDEO
    val paths: List<String>,                  // PHOTO=[L,R or main,aux] / VIDEO=[단일]
    val captureMode: SpatialCaptureMode,     // MPO / HEIC_AUX / STEREO_EXIF / STEREO_MP4 / MOV_SPATIAL / MV_HEVC / PLAIN_2D_PHOTO / PLAIN_2D_VIDEO
    val caption: String = ""
) : ContentBlock()

enum class SpatialMediaType { PHOTO, VIDEO }

enum class SpatialCaptureMode(val key: String, val label: String) {
    MPO("mpo", "MPO 3D 사진"),
    HEIC_AUX("heic_aux", "HEIC 공간 사진"),
    STEREO_EXIF("stereo_exif", "Stereo EXIF 3D 사진"),
    STEREO_MP4("stereo_mp4", "Stereo MP4 입체 영상"),
    MOV_SPATIAL("mov_spatial", "MOV 공간 영상"),
    MV_HEVC("mv_hevc", "MV-HEVC 공간 영상"),
    PLAIN_2D_PHOTO("plain_2d_photo", "2D 사진"),
    PLAIN_2D_VIDEO("plain_2d_video", "영상");

    /** 3D 입체 미디어 여부. PLAIN_2D_* 는 false */
    val is3D: Boolean get() = this != PLAIN_2D_PHOTO && this != PLAIN_2D_VIDEO
}
```

**자동 분류 규칙** ([`ImageFormatDetector`](app/src/main/java/com/grepiu/aidiary/data/slm/ImageFormatDetector.kt) / [`VideoFormatDetector`](app/src/main/java/com/grepiu/aidiary/data/slm/VideoFormatDetector.kt)):

| 첨부 파일 | 감지 | 결과 |
|---|---|---|
| JPEG + APP2 `MPF\0` 시그니처 | MPO | `SpatialMediaBlock(PHOTO, [L,R], MPO)` |
| HEIF (heic/heix) + `auxl`/`auxC` 패턴 | HeicAux | `SpatialMediaBlock(PHOTO, [main,aux], HEIC_AUX)` |
| EXIF ImageDescription/UserComment 에 `stereo`/`3d` | StereoExif | `SpatialMediaBlock(PHOTO, [원본], STEREO_EXIF)` |
| 일반 2D 사진 | Plain2D | `SpatialMediaBlock(PHOTO, [원본], PLAIN_2D_PHOTO)` ← **구 ImageBlock 도 호환 렌더링** |
| MP4 비디오 트랙 2개 | StereoMp4 | `SpatialMediaBlock(VIDEO, [mp4], STEREO_MP4)` |
| QuickTime + `st3d` atom | MovSpatial | `SpatialMediaBlock(VIDEO, [mov], MOV_SPATIAL)` |
| MP4 HEVC hvcC `general_profile_idc=6 or 7` | MvHevc | `SpatialMediaBlock(VIDEO, [mp4], MV_HEVC)` |
| 그 외 | Plain2D | `SpatialMediaBlock(VIDEO, [파일], PLAIN_2D_VIDEO)` |

### 4.2 배지 표출 규칙 (사용자 UX 의도)

상단 배지:
- **메인 라벨**: 항상 "사진" / "영상" 만 (3D 접두사 X)
- **3D 칩**: 3D 일 때만 메인 배지 옆에 별도 소형 "3D" 칩
- **보조 라벨**: 3D 일 때만 포맷 상세 (예: "MPO 3D 사진", "Stereo MP4 입체 영상")

본문 (미디어 영역):
- 2D 사진: 단일 이미지 + 회색 border
- 3D 사진: 좌/우 SBS 합성 + 보라 border (#7C4DFF)
- 2D 영상: VideoView + 회색 border
- 3D 영상: VideoView + 보라 border

### 4.3 Room v2 마이그레이션

`block` 테이블에 3개 컬럼 추가:

```sql
ALTER TABLE block ADD COLUMN spatial_type TEXT;
ALTER TABLE block ADD COLUMN spatial_paths_json TEXT;  -- ["path1","path2"] JSON 배열
ALTER TABLE block ADD COLUMN spatial_capture_mode TEXT;
```

`MIGRATION_1_2` (DiaryDatabase.kt) 가 `addMigrations(MIGRATION_1_2)` 로 등록됨. `fallbackToDestructiveMigration` 사용 금지.

---

## 5. 입체 미디어 자동 감지 (3D)

### 5.1 `ImageFormatDetector`

PR 1 에서 다루는 포맷:

| 감지 | 방법 | 신뢰도 | 비고 |
|---|---|---|---|
| **MPO** (Multi-Picture Object) | JPEG 의 APP2 마커 안에 `MPF\0` 시그니처 + 2 view | ⭐⭐⭐ | CIPA DC-007 표준 |
| **HEIC auxiliary** | ISOBMFF `ftyp` + `auxl`/`auxC` 박스 | ⭐⭐⭐ | iPhone 15 Pro Spatial Photo |
| **Stereo EXIF** | ExifInterface ImageDescription / UserComment 의 "stereo"/"3d" 키워드 | ⭐ 보통 | 일부 카메라/앱만 |
| **Plain2D** | 위 어느 것도 아님 | — | 일반 2D 사진 |

→ 모든 감지는 raw bytes 직접 파싱 (외부 라이브러리 없음). AGENTS.md 의 보수성 정책 준수.

### 5.2 `VideoFormatDetector`

자체 구현한 ISOBMFF 박스 파서 (~250줄) 기반:

| 감지 | 방법 | 신뢰도 | 비고 |
|---|---|---|---|
| **Stereo MP4** | `moov.trak.mdia.hdlr.handler_type == 'vide'` 2개 이상 | ⭐⭐⭐ | 일반 3D 영상 |
| **MOV spatial** | `st3d` stereoscopic atom 검출 | ⭐⭐⭐ | Apple QuickTime |
| **MV-HEVC** (단일 트랙) | `hvcC` 박스 `general_profile_idc = 6 (MultiView) or 7 (Scalable)` | ⭐⭐ (90%+) | iPhone 15 Pro, Galaxy XR, Pixel XR |
| **Plain2D** | 위 어느 것도 아님 | — | 일반 2D 영상 |

**MV-HEVC 의 한계**: VPS NAL unit 풀 파싱은 PR 2 예정. 현재는 `hvcC` 의 profile_idc 만 확인. vendor 비표준 비트는 false negative 가능. (실제 iPhone 15 Pro / Galaxy XR 은 모두 profile_idc=6 사용으로 검증됨)

### 5.3 첨부 흐름 (3D 자동 분기)

```
[User] 갤러리/카메라/비디오 picker 선택
   ↓
[MainActivity] ActivityResultContracts.PickMultipleVisualMedia / PickVisualMedia
   ↓
[DiaryViewModel] importPickedImages / importCapturedImage / importPickedVideo
   ↓
[ImageStorageManager] importDetectingFormat(uri)
   ↓
[ImageFormatDetector] detect(tempFile) → Image3DFormat
   ↓
   ├─ Plain2D     → SpatialMediaBlock(PHOTO, [path], PLAIN_2D_PHOTO)
   ├─ MPO         → MPO 두 view 추출 → SpatialMediaBlock(PHOTO, [L,R], MPO)
   ├─ HeicAux     → primary JPEG 디코딩 → SpatialMediaBlock(PHOTO, [main,aux], HEIC_AUX)
   └─ StereoExif  → 원본 + 플래그 부착 → SpatialMediaBlock(PHOTO, [path], STEREO_EXIF)
   ↓
[DiaryViewModel] _state.update { draftBlocks + block }
   ↓
[BlockRenderer / BlockEditor] 재구성 — 배지 + 본문 border + SBS / VideoView
```

비디오는 동일한 패턴 + 30초 가드:

```
[VideoStorageManager] importFromUri(uri)
   ↓
[DiaryViewModel] 30초 가드 (MediaMetadataRetriever)
   ↓
[VideoFormatDetector] detect → Video3DFormat
   ↓
   ├─ Plain2D   → SpatialMediaBlock(VIDEO, [path], PLAIN_2D_VIDEO)
   ├─ StereoMp4 → SpatialMediaBlock(VIDEO, [path], STEREO_MP4)
   ├─ MovSpatial→ SpatialMediaBlock(VIDEO, [path], MOV_SPATIAL)
   └─ MvHevc    → SpatialMediaBlock(VIDEO, [path], MV_HEVC)
```

### 5.4 영상 30초 가드

`VideoStorageManager.MAX_VIDEO_DURATION_MS = 30_000L` (companion object 상수). 30초 초과 시:
1. 복사된 파일 즉시 삭제
2. 토스트: `"영상은 최대 30초까지 첨부할 수 있어요. (선택 영상: N초)"`
3. 블록 추가 안함

`AddBlockBar` 의 칩 라벨에 `"영상(최대 30초)"` 명시.

### 5.5 VideoView 재생

`BlockRenderer.SpatialMediaBlockView` 의 VIDEO 분기:
- `androidx.compose.ui.viewinterop.AndroidView` + `android.widget.VideoView`
- `Uri.fromFile(file)` 로 sandbox 파일 직접 재생
- `MediaController` 자동 부착 (재생/일시정지/시크)
- setOnErrorListener 로 안전하게 에러 처리

WRITE 화면 (`BlockEditor.SpatialMediaEditorView`) 은 편집 중 자동 재생 방지를 위해 placeholder 텍스트만 표시. 상세 화면에서만 VideoView 재생.

---

## 6. 샌드박스 저장 정책 / Export 준비

### 6.1 저장 위치 (sandbox)

| 종류 | 경로 | UUID 패턴 |
|---|---|---|
| 2D 사진 | `filesDir/diary_images/` | `<uuid>.jpg` 등 원본 확장자 |
| 3D 사진 (MPO) | `filesDir/diary_images/` | `<uuid>_L.jpg`, `<uuid>_R.jpg` |
| 3D 사진 (HEIC aux) | `filesDir/diary_images/` | `<uuid>_main.jpg`, `<uuid>_aux.jpg` |
| 2D/3D 영상 | `filesDir/diary_videos/` | `<uuid>.<ext>` (mime 기반) |

### 6.2 정책

- **앱 sandbox (내부 저장소)**: OS 가 외부 앱 접근 차단 (`MODE_PRIVATE`). 앱 삭제 시 자동 정리
- **DB/JSON 에는 상대 경로** (`diary_images/<uuid>.jpg`) 만 저장 → 기기 간 백업/복원 시 이식성
- **UUID 충돌 방지**: `UUID.randomUUID()` 기반. orphan 파일 누적 없음 (다른 일기에서 동일 UUID 미사용)
- **일기 삭제 시 일괄 정리**: `DiaryRepository.deleteEntry()` → `imageStore.deleteForEntry()` + `videoStore.deleteForEntry()` → Room `block` 행 CASCADE → FTS5 row 삭제

### 6.3 Export 기능 (향후 추가 시)

- `diary_images/` + `diary_videos/` 모든 파일 zip 으로 묶어 `ACTION_CREATE_DOCUMENT` 로 사용자 저장소(Google Drive 등) 저장
- DB 도 JSON export (이미 `LegacyJsonImporter` 가 JSON 포맷 정의 — round-trip 가능)
- 3D 미디어 메타정보(`SpatialMediaBlock` 의 `captureMode`) 도 JSON 에 포함
- zip 내부 구조 예시:
  ```
  diary_export_<ts>.zip
  ├── manifest.json           # 일기 메타 + 블록 (경로 매핑)
  ├── diary_images/
  │   ├── <uuid>.jpg
  │   ├── <uuid>_L.jpg
  │   └── ...
  └── diary_videos/
      ├── <uuid>.mp4
      └── ...
  ```

### 6.4 Storage Manager 인터페이스

```kotlin
class ImageStorageManager(context: Context) {
    suspend fun importFromUri(uri: Uri): Result<String>                    // 단순 2D ImageBlock 생성 (legacy)
    suspend fun importDetectingFormat(uri: Uri): Result<ContentBlock>     // [v3.2] 3D 자동 감지
    fun resolve(relativePath: String): File?
    fun delete(relativePath: String)
    fun deleteForEntry(entry: DiaryEntry)
}

class VideoStorageManager(context: Context) {
    suspend fun importFromUri(uri: Uri): Result<String>                    // 복사만
    suspend fun getVideoDurationMs(file: File): Long?                      // 30초 가드용
    fun resolve(relativePath: String): File?
    fun delete(relativePath: String)
    fun deleteForEntry(entry: DiaryEntry)
    companion object { const val MAX_VIDEO_DURATION_MS = 30_000L }
}
```

---

## 7. 핵심 컴포넌트 책임

| 컴포넌트 | 책임 | 주의 |
|---|---|---|
| `DiaryRepository` (v3.1) | Room CRUD. 메타 lazy (`observeMetas`/`pagedMetas`), 풀 lazy (`loadFullDiary`), 페이지네이션, FTS5 검색 | `searchDiaries` FTS5 실패 시 Room LIKE 폴백 |
| `DiaryDatabase` (v2) | Room DB. FTS5 가상테이블. `MIGRATION_1_2` | FTS5 미지원 기기는 검색만 LIKE 폴백 |
| `DiaryDao` (v3) | 메타/페이지네이션 쿼리, Blocking API | runInTransaction 내부에서만 Blocking |
| `DiarySearchDao` (v3) | FTS5 raw query | 본문 인덱스 `MAX_CONTENT_CHARS = 6000` |
| `LegacyJsonImporter` (v3) | 구버전 JSON → Room 1회 자동 import | 1,000건 batch. JSON 은 `backup/` 으로 이동 |
| `ImageStorageManager` (v3.2) | URI/파일 → `filesDir/diary_images/`, 3D 자동 분기, MPO view 추출, 경로 resolve/delete | 항상 상대 경로만 DB/JSON 저장 |
| `VideoStorageManager` (v3.2) | URI/파일 → `filesDir/diary_videos/`, duration 조회, 30초 가드, 일괄 정리 | 추후 export 기능 시 그대로 활용 |
| `ImageFormatDetector` (v3.2) | MPO/HEIC aux/Stereo EXIF/Plain2D 자동 감지 (raw bytes) | 외부 라이브러리 0 |
| `VideoFormatDetector` (v3.2) | MP4 ISOBMFF 박스 파서 (자체 ~250줄). 2-track/st3d/MV-HEVC/Plain2D | MV-HEVC 는 hvcC profile_idc 휴리스틱 (90%+) |
| `DiaryViewModel` | 인텐트 라우팅, 모델/엔진 초기화, 미디어 import 라이프사이클, AI 액션 트리거 | `WakeLock`, `AudioRecord`, `LLM`, `Sherpa` 자원 해제 |
| `DiaryLLMEngine` | LiteRT-LM 세션, 토큰 스트리밍 | `dispose()` 전 메모리 누수 |
| `SherpaEngine` | 오프라인 음성→텍스트, 16kHz mono PCM, 다국어(`auto`/`ko`/`en`/`ja`/`zh`/`yue`) | `SherpaEngine.create(modelDir, language)` |
| `ModelDownloaderV2` | HuggingFace 다운로드, tar.bz2 압축 해제, 에셋/로컬 폴백 | 토큰 단위 진행률 |
| `DiaryState` | 모든 UI 상태 immutable 스냅샷 | `draftBlocks` 가 본문 단일 진실 |
| `PlannerRepository` | 할 일/목표 로컬 JSON 영속화. `seriesId` 로 반복 시리즈 그룹 | 시리즈 단위 삭제 가능 |

---

## 8. 의존성/플러그인 위치

- **네이티브 라이브러리**: `app/libs/jniLibs/` (Sherpa `.so` 직접 관리)
- **에셋 모델**: `app/src/main/assets/` (선택적 번들)
- **버전 카탈로그**: `gradle/libs.versions.toml` (`coil-compose`, `exifinterface` 포함)
- **앱 모듈 의존성**: `app/build.gradle.kts` (`testOptions.unitTests.isReturnDefaultValues = true` 필수)
- **FileProvider 경로**: `app/src/main/res/xml/file_paths.xml`
- **폰트 리소스**: `app/src/main/res/font/pretendard_*.otf`

```toml
# libs.versions.toml 핵심
coil = "2.7.0"
room = "2.7.0"
exifinterface = "1.3.7"   # ImageFormatDetector (ExifInterface)
```

---

## 9. 화면 전환 규약

`DiaryState.phase` 값으로 분기:

| Phase | 진입 Intent | 사용 화면 |
|---|---|---|
| `SPLASH` | 앱 시작 (기본) | `DiarySplashScreen` |
| `LIST` | 스플래시 완료 / 뒤로 / 저장 완료 | `DiaryListScreen` |
| `WRITE` | FAB / `NavigateTo(WRITE)` | `DiaryWriteScreen` |
| `DETAIL` | `NavigateTo(DETAIL, diary)` | `DiaryDetailScreen` |

상세 보기의 선택 일기는 `state.selectedDiary`. (Lazy load: `LoadFullDiary(id)` 인텐트)

---

## 10. 모델 자산/캐시/저장소 디렉토리

- **모델 파일**: `context.filesDir/models/`
- **Sherpa 모델**: `context.filesDir/sherpa/`
- **녹음 임시 파일**: `context.cacheDir/recording.pcm`, `recording.wav` (변환 후 삭제)
- **다이어리 Room DB (v2)**: `context.filesDir/diary.db`
- **다이어리 JSON (구버전)**: `filesDir/diary_history.json` → `filesDir/backup/diary_history_imported_<ts>.json`
- **다이어리 첨부 사진**: `context.filesDir/diary_images/<uuid>...` (2D / `_L.jpg` / `_R.jpg` / `_main.jpg` / `_aux.jpg`)
- **다이어리 첨부 영상**: `context.filesDir/diary_videos/<uuid>.<ext>` (≤ 30초)
- **카메라 촬영 임시**: `context.cacheDir/capture_<ts>.jpg` (FileProvider)

---

## 11. 권한 런처 매핑 (MainActivity)

| 런처 | 트리거 | 인텐트 |
|---|---|---|
| `audioPermissionLauncher` | `DiaryEffect.RequestAudioPermission` | `RECORD_AUDIO` |
| `cameraPermissionLauncher` | `DiaryEffect.RequestCameraPermission` | `CAMERA` |
| `locationPermissionLauncher` | `DiaryEffect.RequestLocationPermission` | `ACCESS_FINE_LOCATION` |
| `allPermissionsLauncher` | `DiaryEffect.RequestAllWelcomePermissions` | 마이크·위치·카메라 일괄 |
| `pickImageLauncher` (PhotoPicker) | WRITE 화면 "+ 갤러리" | `ImagesPicked(uris)` |
| `pickVideoLauncher` (VideoPicker) | WRITE 화면 "+ 영상(최대 30초)" | `VideoPicked(uri)` |
| `pickCloudLauncher` (FilePicker/GetMultipleContents) | WRITE 화면 "+ 클라우드" | `CloudFilesPicked(uris)` |
| `takePictureLauncher` | `DiaryEffect.LaunchCamera(uri)` | `CameraImageCaptured(uri)` |

`viewModel.requestVideoImport()` 호출 시 `DiaryEffect.LaunchVideoPicker` 송출 → `MainActivity` 가 `pickVideoLauncher` 실행.
클라우드 칩 클릭 시 `DiaryIntent.RequestCloudImport` 송출 → `DiaryEffect.LaunchCloudPicker` 송출 → `MainActivity` 가 `pickCloudLauncher` 실행.


---

## 12. 변경 시 체크리스트

### 데이터 모델
- [ ] `ContentBlock` 새 타입 추가 시 → `ContentBlock.fromJson`, `toJson`, `BlockRenderer`, `BlockEditor`, `blockTypeMeta`, **`BlockEntity.toContentBlock` / `DiaryEntry.toBlockEntities` (v3)** 동시 갱신
- [ ] `SpatialMediaBlock` 의 `captureMode` enum 추가 시 → `fromKey` 갱신 + `is3D` getter 갱신 + 배지 표출 로직 갱신
- [ ] `TextFormatting` 속성 추가 시 → 데이터 클래스, JSON 직렬화, `toAnnotatedString`, `RichTextToolbar`, `is*At` 헬퍼 동시 갱신
- [ ] `ContentType` 값 추가 시 → enum, `getContentTypeUI`, `contentTypeMeta`, `DiaryRepository` 영속화

### Room DB
- [ ] **Room Entity 필드 추가 시** → 변환 헬퍼(`toDiaryEntity`, `toBlockEntities`, `toContentBlock`) 와 DAO 쿼리, `DiaryMeta` / `DiaryMetaRow`, `LegacyJsonImporter` 동시 갱신
- [ ] **`DiaryMeta` 필드 추가 시 (v3.1)** → DAO SELECT 컬럼 + `DiaryMetaRow` 매핑 갱신
- [ ] **메타 vs 풀 DiaryEntry 분기** → UI 카드는 `DiaryMeta` 만. 풀 데이터 필요 시 `LoadFullDiary(id)` 인텐트 발행
- [ ] **Room 스키마 변경 시** → `version` 올리고 `MIGRATION_N_M` 추가. **`fallbackToDestructiveMigration` 사용 금지** (사용자 데이터 손실)
- [ ] **FTS5 인덱스 길이 변경 시** → `DiarySearchDao.MAX_CONTENT_CHARS` 갱신
- [ ] **구버전 JSON 포맷 호환 변경 시** → `LegacyJsonImporter.parseEntry` 갱신 + 하위 호환

### 입체 미디어 (v3.2)
- [ ] **새 3D 포맷 감지 추가 시** → `ImageFormatDetector` / `VideoFormatDetector` 의 `sealed class` (Image3DFormat / Video3DFormat) 에 `data object` 추가
- [ ] **`SpatialCaptureMode` 에 새 모드 추가 시** → `VideoStorageManager` / `ImageStorageManager` 의 `when` 분기, `DiaryViewModel.importPickedVideo/Images` 분기, 토스트 메시지 동시 갱신
- [ ] **MPO/HEIC view 추출 실패 시** → 항상 2D `PLAIN_2D_PHOTO` 로 폴백 (예외 throw X)
- [ ] **30초 가드** → `VideoStorageManager.MAX_VIDEO_DURATION_MS` 변경 시 `AddBlockBar` 의 라벨도 갱신
- [ ] **VideoView 가 sandbox 외부 경로 읽기 불가** → `Uri.fromFile(file)` 만 사용 (content:// / http:// 사용 X)

### MVI
- [ ] `DiaryState` 필드 추가 시 → `DiaryViewModel` 의 모든 `copy(...)` 갱신
- [ ] `DiaryIntent` 추가 시 → `processIntent` 의 `when` 분기 추가
- [ ] `DiaryEffect` 추가 시 → `MainActivity` 의 `LaunchedEffect` 에 effect 처리 추가

### 권한 / 런처
- [ ] 권한 추가 시 → `AndroidManifest.xml` + `MainActivity` 의 런처 + `DiaryEffect.RequestXxx`
- [ ] 비디오 picker 추가 시 → `MainActivity` 의 `pickVideoLauncher` 와 `DiaryEffect.LaunchVideoPicker` 연결 확인

### 빌드 / 환경
- [ ] JNI/native 추가 시 → `app/libs/jniLibs/` 에 ABI별 .so 배치
- [ ] 한글 IME 조합(ㅇ→아→안→안녕) 입력 보존이 필요하면 `RichTextEditorBody` 의 외부 텍스트 동기화 로직을 수정할 때 `tfv.composition` 검사
- [ ] **단위 테스트**: Android framework 메서드 (`Log.isLoggable`, `ExifInterface` 등) 가 mock 되지 않으므로 `app/build.gradle.kts` 에 `testOptions.unitTests.isReturnDefaultValues = true` 설정 필수 (이미 설정됨)
- [ ] **본 문서도 함께 갱신**

---

## 8. v4.2 최근 UI/UX 고도화 사항

- **전역 보기 모드 (`DiaryState.viewMode`) 상태 승격**:
  - 기존 로컬 `rememberSaveable`로 관리되던 `globalViewMode`를 MVI `DiaryState.viewMode` 및 `DiaryIntent.SelectViewMode` 인텐트로 승격하여, 상세보기(`DiaryPhase.DETAIL`) 진입 후 뒤로가기를 하거나 작성 화면(`DiaryPhase.WRITE`) 완료 후 되돌아오더라도 사용자가 선택한 `BLOG` (또는 `CALENDAR`) 보기 모드가 100% 영속되도록 UX 대응 완료.
- **X (트위터) / Threads 스타일 SNS 타임라인 피드**:
  - `UnifiedBlogView`를 수직 쓰레드 연결선(`Thread Line`), 소셜 아바타 배지(`✨`, `📌`, `🎯`), 소셜 반응 바(💬 AI대화, ❤️ 좋아요, 📋 복사, 📤 공유)를 포함한 트렌디한 피드로 전면 개편.
- **해시태그 블록 가로 스크롤 & 자동 스크롤**:
  - 작성 및 읽기 화면의 해시태그 목록에 `horizontalScroll` 및 `LaunchedEffect` 기반 신규 태그 입력 시 우측 끝 자동 스크롤 기능 적용.
- **달력 모드 상업용 레벨 UI/UX 고도화 & 캘린더 셀 내 기록 텍스트 프리뷰 (v4.5)**:
  - `UnifiedCalendarView`를 기존 도트 전용 셀에서 상업용 인포그래픽 달력으로 전면 리팩토링.
  - **월간 통계 요약 카드**: 이번 달 작성 일기 수(📝), 계획 완료 건수 및 완료율 프로그레스 바(✅ N/M, N%), 목표 수(🎯) 및 '오늘' 빠른 이동 숏컷 제공.
  - **캘린더 셀 내 인포 프리뷰 칩**: 날짜 셀 내부 공간을 확장하여 등록된 일기 제목(📝), 계획 텍스트 및 완료 상태(✓/○), 목표(🎯) 텍스트 프리뷰 칩을 각 타입별 테마 색상으로 최대 2개 직접 표출하며, 초과 건수는 `+N` 배지로 안내.
  - **인터랙티브 타임라인 스크랩북**: 선택한 날짜 하단 스크랩북에서 계획/목표의 완료 상태를 직접 클릭해 바로 토글(`TogglePlannerTask`, `ToggleGoal`) 가능하며, 빠른 '일기 작성' CTA 제공.
- **Google AdMob & AD_ID 권한 연동 (v4.6)**:
  - `AndroidManifest.xml`에 `com.google.android.gms.permission.AD_ID` 권한 명시 (Android 13+ / Target API 33+ 준수).
  - **개발 vs 운영 빌드 변이 분기 (`build.gradle.kts`)**:
    - `debug`: 구글 공식 테스트 App ID(`ca-app-pub-3940256099942544~3347511713`) 및 테스트 배너 ID 적용.
    - `release` (AAB/운영 서명 빌드): 실 운영 App ID 및 배너 ID(`ca-app-pub-2803985305864806~3228866354`) 자동 적용 (`manifestPlaceholders` & `BuildConfig.ADMOB_BANNER_ID`).
  - **AdMobBanner & AdMobNativeAd 컴포넌트**: Compose 내 `AdView` 및 `NativeAdView` 래퍼 컴포넌트.
  - **하단 전용 광고 독 (Ad Dock) & 우측 하단 FAB 배치 개선**:
    - 하단 플로팅 알약 버튼과의 오클릭 및 시각적 겹침 문제를 해결하기 위해 `Scaffold.bottomBar`를 배너 광고 전용 고정 독(`Surface`)으로 전환.
    - 기록 작성 버튼은 우측 하단(`FabPosition.End`)의 세련된 `ExtendedFloatingActionButton`("+ 기록 작성")으로 분리 배치하여 최상의 모바일 UI/UX 확보.
  - **스마트 AI 본문 블록 자동 정렬 & AI 도우미 배너 영구 닫기 (v4.8)**:
    - **스마트 AI 자동 정렬 (`DiaryIntent.AutoArrangeBlocks`)**: 무작위 순서로 입력된 본문 블록들을 구조적 우선순위(`Heading` → `SpatialMedia/Image` → `Text/Quote` → `Table/Location` → `Divider` → `Hashtag/TagAi`)로 일괄 자동 재정렬하며 동일 타입 내 상대적 입력 순서는 보존. 본문 섹션 타이틀 우측 및 `AddBlockBar` AI 도구 칩으로 접근성 확보.
    - **AI 글쓰기 도우미 배너 영구 닫기 다이얼로그**: 작성 화면 상단 `AiWritingGuideCard` 의 'X' 닫기 버튼 터치 시 AI 관련 주요 기능들을 한 번 더 친절히 안내하는 다이얼로그를 표출하며, `확인 (영구 닫기)` 터치 시 `SharedPreferences`(`ai_writing_guide_permanently_dismissed`)에 동기화하여 해당 안드로이드 기기에서 영구 숨김 처리.
  - **표 블록(TableBlock) 동일 행 셀 높이 동기화 (v4.9)**:
    - `TableBlockEditor`(편집기) 및 `TableBlockView`(보기)의 행 레이아웃에 `Row(modifier = Modifier.height(IntrinsicSize.Min))` 및 셀 `fillMaxHeight()`를 적용하여, 한 셀의 텍스트 줄 수가 늘어나 높이가 확장되더라도 같은 행의 모든 셀이 가장 높이가 큰 셀에 맞춰 일괄적으로 동기화되도록 그리드 정렬 개선.
  - **새 글 작성 시 본문 기본 텍스트 블록 1개 자동 생성 (v5.0)**:
    - `DiaryState.kt` 및 `DiaryViewModel.kt`의 신규 작성 진입 처리(`NavigateTo(DiaryPhase.WRITE)`) 시, 본문 블록(`draftBlocks`)을 기본적으로 1개의 빈 텍스트 블록(`ContentBlock.TextBlock("")`)으로 초기화하여 사용자가 진입 즉시 탭 한번으로 바로 본문을 입력할 수 있도록 작성 편의성 향상.
  - **리스트 모드 기록 카드 UI/UX 미니멀리즘 리팩토링 (v5.1)**:
    - `DiaryListItemCard` 카드 상단/좌측의 둔탁한 선 요소를 전면 제거하고, `0.5.dp` 초슬림 헤어라인 보더와 `16.dp` 라운딩 처리된 `Surface` 카드 바디 내부에 깔끔한 타입 뱃지(📝 일기, ✅ 계획, 🎯 목표, 📝 메모)만 정돈하여 시각적 피로감 없는 미니멀리즘 디자인 적용.
