# AIDiary 디자인 시스템 (Design System)

> 최종 업데이트: 2026-07-20 (v3.0)
> 타겟: 20-30대 여성 60% + 남성 40% (남성 이질감 최소화)
> 무드: 소프트 페미닌 저널링 — 더스티로즈/모브/피치/세이지, 부드럽고 따뜻하며 세련됨

---

## 1. 브랜드 아이덴티티

| 항목 | 내용 |
|---|---|
| **서비스명** | AIDiary (AI 다이어리) |
| **포지셔닝** | 감성 기록 + AI 비서 + 플래너가 융합된 프리미엄 다이어리 |
| **타겟 페르소나** | 20-30대 여성 60% + 남성 40%, 일상 기록과 자기 관리에 관심 있는 사용자 |
| **핵심 가치** | 부드러움(Softness), 따뜻함(Warmth), 집중(Focus), 균형(Balance) |
| **톤앤매너** | 소프트 페미닌하되 남성 이질감 최소화. 더스티 파스텔 + 3단 레이어링으로 카드 리프트. |

---

## 2. 컬러 시스템

### 2.1 메인 팔레트

| 역할 | 컬러명 | HEX | 용도 |
|---|---|---|---|
| **Primary** | Dusty Rose | `#C67A8E` | 메인 CTA, 선택 상태, 강조 |
| **Primary Container** | Light Rose | `#F7DCE2` | 칩 배경, 선택 영역 |
| **Secondary** | Mauve | `#8B87C7` | 보조 액션, AI/포인트 |
| **Secondary Container** | Light Mauve | `#E7E2F5` | 보조 배경 |
| **Tertiary** | Sage | `#5FA37E` | 완료/성공, 자연 모티브 |
| **Tertiary Container** | Mint | `#CDEEDB` | 긍정 배경 |

### 2.2 서피스 & 배경 — 3단 레이어링

배경 < 서브카드는 웜톤, 카드는 밝게 띄워 시각적으로 분리(카드 리프트)한다. v2에서 Background==Surface(#FDFBF9)로 레이어가 뭉개지던 문제를 해결.

| 역할 | HEX | 설명 |
|---|---|---|
| **Background** | `#FBF6F5` | 블러시 웜화이트 (핑크 언더톤 미세) |
| **Surface** | `#FFFFFF` | 카드, 다이얼로그 — **배경보다 밝게 → 카드가 뜸** |
| **Surface Variant** | `#F4EAEA` | 서브 카드, 캘린더 래퍼, 구분 영역 |

### 2.3 기능별 액센트 — 색상환 4방향 분산

명도가 아닌 색상(hue)으로 구분해 기능 식별을 명확히 한다.

| 기능 | HEX | 설명 |
|---|---|---|
| **Diary (기록)** | `#C67A8E` | 로즈핑크 — 메인 브랜드 컬러 |
| **Planner (플래너)** | `#E8945C` | 피치/애프리콧 — 에너지, 할 일 |
| **Goals (목표)** | `#5FA37E` | 세이지그린 — 성장, 달성 |
| **Chat (AI 비서)** | `#8B87C7` | 페리윙클/모브 — 신뢰, 대화 |

### 2.4 감정 컬러

| 감정 | HEX | 설명 |
|---|---|---|
| **Joy (기쁨)** | `#E0A94A` | 웜 골드 |
| **Sadness (슬픔)** | `#6B8BAE` | 스틸 블루 |
| **Anger (분노)** | `#D96B6B` | 코랄 |
| **Anxiety (불안)** | `#9585BE` | 더스티 퍼플 |
| **Calm (평온)** | `#6FA98A` | 세이지 |

### 2.5 다크 모드

Surface(`#241E21`) > Background(`#1A1618`) 명도차를 유지해 다크에서도 카드 리프트를 보존한다. Primary `#E7A6B4`(밝은 더스티 로즈), Secondary `#C3BEF0`(밝은 모브), Tertiary `#9AD3B4`(밝은 세이지)로 밝기를 올리되 채도를 낮춰 눈의 피로를 줄인다.

---

## 3. 타이포그래피

### 3.1 폰트

| 설정 | 값 |
|---|---|
| **서체** | Pretendard (SIL OFL) |
| **Weight** | Regular (400), Medium (500), SemiBold (600), Bold (700) |
| **특징** | 한글/영문 모두 최적화된 모던 고딕. 가독성과 감성의 균형. |

### 3.2 타입 스케일

| 스타일 | Size | Weight | Line Height | 용도 |
|---|---|---|---|---|
| **displayLarge** | 32sp | Bold | 40sp | 스플래시, 특별 이벤트 |
| **headlineMedium** | 22sp | SemiBold | 30sp | 페이지 타이틀 |
| **titleLarge** | 20sp | SemiBold | 28sp | 섹션 헤더 |
| **titleMedium** | 17sp | SemiBold | 24sp | 카드 타이틀 |
| **bodyLarge** | 16sp | Regular | 24sp | 본문 |
| **bodyMedium** | 14sp | Regular | 22sp | 보조 본문 |
| **bodySmall** | 12sp | Regular | 18sp | 캡션, 메타 |
| **labelLarge** | 14sp | SemiBold | 20sp | 버튼, 탭 |
| **labelMedium** | 12sp | Medium | 16sp | 칩, 배지 |
| **labelSmall** | 10sp | Medium | 14sp | 초소형 라벨 |

---

## 4. 셰이프 & 라운드

| 요소 | Radius | 비고 |
|---|---|---|
| **Large Card** | 24dp | 헤더 카드, 다이얼로그 |
| **Medium Card** | 16-20dp | 일기 카드, 캘린더 컨테이너 |
| **Small Card** | 12-14dp | 칩, 배지, 필터 탭 |
| **Pill Button** | 28dp | 하단 CTA |
| **Circle** | 50% | 아이콘 버튼, 프로필 |

---

## 5. 엘리베이션 & 그림자

| 레벨 | Elevation | 용도 |
|---|---|---|
| **Level 0** | 0-1dp | 일반 카드, 리스트 아이템 |
| **Level 1** | 2-4dp | 선택된 캘린더 셀, 호버 상태 |
| **Level 2** | 6-8dp | 탭 인디케이터, 강조 카드 |
| **Level 3** | 12-16dp | 플로팅 CTA 필, FAB |

그림자는 Primary/Accent 컬러의 `copy(alpha=0.2~0.3)` tint를 적용해 따뜻한 글로우 효과를 냅니다.

---

## 6. 스페이싱 시스템

| 토큰 | 값 | 용도 |
|---|---|---|
| **xxs** | 4dp | 아이콘-텍스트 사이 |
| **xs** | 8dp | 관련 요소 간격 |
| **sm** | 12dp | 그룹 간격 |
| **md** | 16dp | 섹션 패딩 |
| **lg** | 20dp | 카드 내부 패딩 |
| **xl** | 24dp | 페이지 패딩 |
| **xxl** | 32dp | 큰 여백 |

---

## 7. 컴포넌트 패턴

### 7.0 TopAppBar (DiaryTopAppBar)
- Surface 배경, no shadow (평평한 미니멀)
- 타이틀: "오늘의 기록" / "기록 탐색" + 선택일 서브타이틀
- 액션: "오늘" 버튼(과거 탐색 시), 달력 피커, 설정
- 스크롤 시에도 동일한 컨테이너 색상 유지

### 7.1 헤더 카드 (DailyOverviewHeader)
- 24dp 라운드 카드, Surface 배경
- 20dp 내부 패딩
- 상단: 그리팅 + 액션 버튼 Row
- 구분선 (HorizontalDivider, alpha 0.2)
- 하단: 3-Stat 칩 Row (weight 1f 균등 분할)

### 7.2 캘린더 스트립 (WeeklyCalendarStrip)
- 20dp 라운드 Surface 래퍼 (alpha 0.7)
- 선택일: Primary 배경 + 4dp shadow
- 오늘: PrimaryContainer 배경 + 1.5dp Primary 테두리
- 일반: surfaceVariant (alpha 0.35) 배경
- 하단 도트: DiaryAccent / PlannerAccent / GoalsAccent

### 7.3 탭 셀렉터 (TabSelector)
- 16dp 라운드 Card 컨테이너
- 42dp 높이, 4개 탭 균등 분할
- 선택 탭: Accent 컬러 배경 + 2dp 그림자
- 바운시 스프링 애니메이션

### 7.4 일기 카드 (DiaryListItemCard)
- 16dp 라운드, Surface(#FFFFFF) 배경 — 블러시 배경 위에서 리프트
- 좌측 3px 컨텐츠 타입 액센트 바 (verticalGradient, 타입색 4방향)
- 제목 17sp SemiBold (2줄), 프리뷰 2줄 (한 화면 카드 수 ↑)
- 타입 배지 (8dp radius, 10% alpha 배경)
- 감정 칩 (6dp radius Surface)
- 60ms stagger 페이드인 + 슬라이드 진입

### 7.5 플로팅 CTA (FloatingWritePill)
- 중앙 정렬, 220×52dp, 28dp radius
- Primary → Primary(alpha 0.85) → Tertiary(alpha 0.7) 그라데이션
- 12dp shadowElevation
- Bounce 스케일 애니메이션 (0.95 ~ 1.0)

### 7.6 스탯 칩 (StatChip)
- 12dp radius, Accent color 8% alpha 배경
- 아이콘(16dp) + 라벨(10sp) + 값(14sp Bold)

### 7.7 빈 상태 (Empty State)
- 20dp 라운드 Card, Surface 배경
- 72dp CircleShape 아이콘 컨테이너 (PrimaryContainer 30% alpha)
- FilledTonalButton CTA

### 7.8 헤더 접힘 (Header Collapse) — 리스트 공간 확보
- 활성 탭 리스트를 아래로 스크롤 → 스탯카드(DailyOverviewHeader) + 캘린더(WeeklyCalendarStrip)가 `AnimatedVisibility`(shrink/expandVertically + fade)로 접힘
- **탭바(TabSelector)는 sticky** — 접히지 않고 상단 고정
- 위로 스크롤 → 헤더 복원. 탭 전환 시 자동 펼침
- 구현: `DiaryListScreen`에 `headerCollapsed` 상태 hoist, 콘텐츠 Box에 `nestedScroll` 커넥션을 달아 `onPreScroll` 델타 방향(±4f)으로 토글. 검색/챗 포커스용 `isHeaderHidden`과 별개 동작
- 효과: 첫 진입 카드 2개 노출 → collapse 후 5개 이상

---

## 8. 애니메이션

| 대상 | 이펙트 | Duration | Easing |
|---|---|---|---|
| **탭 인디케이터** | offset + color | 300ms | Spring (DampingRatioMediumBouncy) |
| **캘린더 셀** | scale + bgColor | 200-250ms | Spring (StiffnessMedium) |
| **일기 카드 진입** | fadeIn + slideInVertically | 400ms | FastOutSlowInEasing |
| **카드 stagger** | delay(index * 60ms) | — | — |
| **플로팅 필** | scale on press | — | Spring (DampingRatioMediumBouncy) |
| **페이지 전환** | slideInVertically + fadeIn | 350ms | tween |

---

## 9. 접근성

- 모든 인터랙티브 요소 최소 터치 영역 44dp
- 대비 비율: 본문 4.5:1, 헤드라인 3:1 이상
- 다크 모드 지원
- Pretendard의 높은 가독성 (lineHeight 1.5배)

---

## 10. 변경 이력

| 날짜 | 버전 | 내용 |
|---|---|---|
| 2026-07-20 | v3.0 | 소프트 페미닌 리브랜딩(더스티로즈/모브/피치/세이지). 3단 서피스 레이어링으로 카드 리프트, 기능 액센트 색상환 4방향 분산. 헤더 스크롤 collapse + 탭바 sticky. 기록 카드 재설계(16dp/3px/프리뷰 2줄). `getContentTypeUI`/`getEmotionUI` 토큰화 |
| 2026-07-19 | v2.1 | 20-30대 남녀 공통 타겟 리브랜딩: 로즈→웜 클레이, 피치→웜 샌드, 세이지 유지 |
| 2026-07-19 | v2.0 | 20-30대 여성 타겟 리브랜딩: 인디고→더스티 로즈, 앰버→피치 코랄, 그린→소프트 세이지 |
| 2026-07-19 | v1.0 | 초기 디자인 시스템 구축, M3 커스텀 테마 |

---

## 11. 컬러 변경 가이드

디자인 시스템 변경이 필요할 때 수정해야 하는 파일들:

| 파일 | 수정 내용 |
|---|---|
| `ui/theme/Color.kt` | 모든 HEX 컬러 값 정의 |
| `ui/theme/Theme.kt` | Light/Dark ColorScheme 매핑 |
| `ui/theme/Type.kt` | 타이포그래피 스타일 |
| `ui/screens/DiaryListScreen.kt` | `tabAccentColor()`, `getContentTypeUI()`, `getEmotionUI()`, `EmotionChipSmall`, 헤더 collapse |
| `docs/DESIGN.md` | 본 문서 업데이트 |
| `docs/structure.md` | (구조 변경 시에만) |
