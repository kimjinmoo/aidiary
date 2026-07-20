# AIDiary UI/UX 리팩토링 v3.0 설계 (Spec)

> 작성: 2026-07-20
> 타겟 재설정: 20-30대 여성 60% + 남성 40% (남성 이질감 최소화)
> 목적: (1) 기록 리스트 가시 영역 확대, (2) 색상 구분성 개선, (3) 소프트 페미닌 리브랜딩, (4) 전체 앱 일관성

---

## 1. 문제 정의

| # | 문제 | 근본 원인 |
|---|---|---|
| P1 | 기록 탭 리스트 보이는 영역이 작음 | 스탯카드 + 캘린더 스트립 + 탭바가 `weight(1f)` 리스트 위에 **항상** 스택. 평상시 스크롤 collapse 없음 (`isHeaderHidden`은 검색/챗 입력 시에만 동작) |
| P2 | 색상들이 비슷해 UX 불편 | (a) 팔레트 전체가 웜뉴트럴 저채도 어스톤(클레이/샌드/세이지) → 기능별 액센트 구분 약함. (b) **`SurfaceLight == BackgroundLight == #FDFBF9`** → 카드·다이얼로그·배경 동일색, 레이어 분리 부재 (0.15 alpha 테두리만 구분) |
| P3 | 여성 60% 타겟과 현재 남녀공통 웜클레이 톤 괴리 | v2.1에서 v2.0(로즈/피치) → 웜클레이로 롤백된 상태 |

---

## 2. 결정 사항 (사용자 확정)

- **색 방향**: 소프트 페미닌 리브랜딩 (더스티로즈/모브/피치/세이지)
- **리스트 공간**: 스크롤 시 헤더 접기(collapse) + 탭바 sticky
- **범위**: 전체 앱 4개 탭 (기록 카드 재설계 + collapse, 나머지 탭 톤/칩/버튼 통일)

---

## 3. 색 시스템 (신규 v3.0)

### 3.1 메인 팔레트 (Light)

| 역할 | 컬러명 | HEX |
|---|---|---|
| Primary | 더스티 로즈 | `#C67A8E` |
| On Primary | 화이트 | `#FFFFFF` |
| Primary Container | 라이트 로즈 | `#F7DCE2` |
| On Primary Container | 딥 로즈 | `#40121F` |
| Secondary | 모브 | `#8B87C7` |
| On Secondary | 화이트 | `#FFFFFF` |
| Secondary Container | 라이트 모브 | `#E7E2F5` |
| On Secondary Container | 딥 모브 | `#231A3E` |
| Tertiary | 세이지 | `#5FA37E` |
| On Tertiary | 화이트 | `#FFFFFF` |
| Tertiary Container | 민트 | `#CDEEDB` |
| On Tertiary Container | 딥 그린 | `#00210F` |

### 3.2 서피스 & 배경 — 3단 레이어링 (P2-b 핵심 해결)

| 역할 | HEX | 설명 |
|---|---|---|
| Background | `#FBF6F5` | 블러시 웜화이트 (핑크 언더톤 미세) |
| Surface (카드) | `#FFFFFF` | **배경보다 밝게 → 카드가 시각적으로 뜸** |
| Surface Variant | `#F4EAEA` | 서브카드·캘린더 래퍼·구분 영역 |
| Outline | `#9A8B88` | 웜 그레이 |
| Outline Variant | `#E4D2D0` | 미세 테두리 |
| On Surface | `#241F1E` | 본문 텍스트 |
| On Surface Variant | `#5B4F4C` | 보조 텍스트 |

> 원칙: 배경(#FBF6F5) < 서브(#F4EAEA) 는 웜톤, 카드(#FFFFFF)는 밝게 띄운다. 현재처럼 3개가 동일 HEX가 되지 않도록 한다.

### 3.3 기능별 액센트 — 색상환 분산 (P2-a 해결)

| 기능 | HEX | 색 계열 |
|---|---|---|
| 기록 (Diary) | `#C67A8E` | 로즈핑크 |
| 플래너 (Planner) | `#E8945C` | 피치/애프리콧 |
| 목표 (Goals) | `#5FA37E` | 세이지그린 |
| AI비서 (Chat) | `#8B87C7` | 페리윙클/모브 |

> 핑크·오렌지·그린·퍼플 4방향으로 색상환 벌림. 명도만이 아닌 색상(hue)으로 구분되어 기능 식별이 명확.

### 3.4 감정 컬러 (새 팔레트 조화 재튜닝)

| 감정 | HEX |
|---|---|
| Joy (기쁨) | `#E0A94A` (웜 골드) |
| Sadness (슬픔) | `#6B8BAE` (스틸 블루) |
| Anger (분노) | `#D96B6B` (코랄) |
| Anxiety (불안) | `#9585BE` (더스티 퍼플) |
| Calm (평온) | `#6FA98A` (세이지) |

### 3.5 콘텐츠 타입 컬러

| 타입 | HEX |
|---|---|
| DIARY | `#C67A8E` |
| POST | `#8B87C7` |
| NOTE | `#5FA37E` |

### 3.6 다크 모드

| 역할 | HEX |
|---|---|
| Background | `#1A1618` (웜 니어블랙) |
| Surface | `#241E21` (카드 리프트) |
| Surface Variant | `#3A3134` |
| Primary | `#E7A6B4` (밝은 더스티 로즈) |
| Secondary | `#C3BEF0` (밝은 모브) |
| Tertiary | `#9AD3B4` (밝은 세이지) |
| On Surface | `#ECE0DE` |

> 다크에서도 Surface > Background 명도차 유지해 카드 리프트 보존.

---

## 4. 레이아웃 — 헤더 Collapse (P1 해결)

### 4.1 동작

- 활성 탭 리스트를 **아래로 스크롤** → 스탯카드(DailyOverviewHeader) + 캘린더 스트립(WeeklyCalendarStrip) 접힘: height·alpha 애니메이션 0
- **탭바(TabSelector) sticky** — 상단 고정 유지, 접히지 않음
- **위로 스크롤** → 헤더 다시 펼침
- 기존 `isHeaderHidden`(검색/챗 포커스) 로직과 공존

### 4.2 구현 방향

- 헤더 접힘 상태를 나타내는 hoisted state (예: `headerCollapsed: Boolean` 또는 nestedScroll 오프셋 기반) 를 `DiaryListScreen` 상단에 둔다.
- 활성 탭의 `LazyListState` (스크롤 방향/오프셋) 를 관찰해 collapse 여부 도출 (`derivedStateOf`).
- 스탯카드 + 캘린더 래퍼를 `AnimatedVisibility`(shrinkVertically + fadeOut / expandVertically + fadeIn) 로 감싼다.
- TabSelector 는 collapse 대상에서 제외하고 컨텐츠 Box 위에 고정 배치.
- 각 탭 컨텐츠(DIARY/PLANNER/GOALS)는 이미 자체 LazyColumn 보유 → 활성 탭의 LazyListState 를 상위로 hoist 하거나 nestedScrollConnection 전달.

### 4.3 기대 효과

첫 진입 시 리스트 2개 노출 → collapse 후 5개 이상 노출.

---

## 5. 기록 카드 재설계 (DiaryListItemCard, DiaryMeta 버전)

| 항목 | 현재 | 변경 |
|---|---|---|
| 카드 배경 | Surface(=배경 동일 #FDFBF9) | Surface(#FFFFFF, 블러시 배경서 뜸) |
| 라운드 | 14dp | 16dp (소프트) |
| 좌측 액센트 바 | 4px verticalGradient | 3px, 타입색(4방향) 유지 |
| 프리뷰 | 3줄 | **2줄** (한 화면 카드 수↑) |
| 제목 | 16sp | 17sp |
| 감정칩 | 기존 색 | 재튜닝 색 |

> 썸네일은 이번 범위 제외. `DiaryMeta`(id/timestamp/title/emotion/contentType/contentPreview)에 이미지 경로 필드가 없어 썸네일 추가 시 Room 컬럼·DAO·Row·Repository 변경이 필요하고, 이는 섹션 8(데이터 모델 변경 없음) 위반. 텍스트 카드 스캔성 개선(배경 리프트 + 여백 + 2줄 프리뷰)에 집중한다.

---

## 6. 전역 통일 (전체 앱)

- 대부분 `MaterialTheme.colorScheme` 상속 → Color.kt/Theme.kt 교체만으로 4개 탭 톤 자동 반영.
- 명시적 색 참조 재매핑: `tabAccentColor()`, `getContentTypeUI()`, `EmotionChipSmall()`, 콘텐츠 타입 메타.
- 셰이프: 리스트 카드 14→16dp. 나머지 라운드 토큰 유지.
- Confetti(ConfettiOverlay)·프로그레스바 색 새 팔레트 재튜닝.
- 플래너/목표 버튼·칩: secondary/tertiary container 자동 반영, 하드코딩 색 있으면 토큰화.

---

## 7. 손대는 파일

| 파일 | 수정 |
|---|---|
| `ui/theme/Color.kt` | 전체 HEX 교체 (신규 v3.0 팔레트) |
| `ui/theme/Theme.kt` | Light/Dark ColorScheme 매핑, surfaceContainer 계열 추가 시 |
| `ui/theme/Type.kt` | 리스트 제목 스케일 소폭 조정 |
| `ui/screens/DiaryListScreen.kt` | 헤더 collapse, 카드 재설계, `tabAccentColor`/`getContentTypeUI`/`EmotionChipSmall` 재매핑 |
| `ui/components/ConfettiOverlay.kt` | 꽃가루 색 재튜닝 |
| `docs/DESIGN.md` | 팔레트·타겟·collapse 패턴·카드 7.4 재작성, 변경이력 v3.0 추가 |
| `docs/structure.md` | collapse 상태 필드 추가 시 갱신 |

---

## 8. 비목표 (Out of Scope)

- 데이터 모델·Room·MVI 로직 변경 없음 (순수 UI/UX)
- 새 기능 추가 없음 (카드 썸네일 제외 — DiaryMeta에 이미지 경로 없음)
- 폰트 교체 없음 (Pretendard 유지)
- AI/음성 파이프라인 무관

---

## 9. 수용 기준 (Acceptance)

- [ ] `.\gradlew assembleDebug` 성공
- [ ] Background / Surface / Surface Variant 3개 HEX가 서로 다름 (카드가 배경과 시각적으로 분리)
- [ ] 기능별 액센트 4색이 색상(hue)으로 구분됨
- [ ] 기록 탭 아래 스크롤 시 헤더 접히고 탭바 고정, 리스트 노출 카드 수 증가
- [ ] 위로 스크롤 시 헤더 복원
- [ ] 4개 탭 모두 새 팔레트 반영, 라이트/다크 모두 카드 리프트 유지
- [ ] DESIGN.md 갱신 (변경이력 v3.0)
