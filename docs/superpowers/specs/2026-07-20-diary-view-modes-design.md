# 기록 보기 모드 (리스트 / 블로그 / 달력) 설계 (Spec)

> 작성: 2026-07-20
> 목적: 기록 탭에서 작성된 글을 3가지 방식(리스트·블로그·달력)으로 볼 수 있게 한다.
> 성격: 순수 UI/UX + 파생 조회. Room 스키마·MVI 로직 변경 최소.

---

## 1. 배경 / 문제

현재 기록(DIARY) 탭은 **선택한 하루**의 기록만 카드 리스트로 보여준다(주간 캘린더 스트립 + 타입 필터). 여러 날의 기록을 한 흐름으로 훑거나, 한 달을 달력으로 조망하는 방법이 없다.

→ 기록 탭 안에 **보기 모드 토글**(리스트/블로그/달력)을 추가한다.

---

## 2. 결정 사항 (사용자 확정)

| # | 항목 | 결정 |
|---|---|---|
| D1 | 배치 | 기록 탭 내 보기 토글 (새 탭/설정 아님) |
| D2 | 블로그 형태 | 날짜 묶음 피드 (전체 기록 최신순, 날짜 헤더 + 카드) |
| D3 | 달력 형태 | 월간 그리드 + 날짜 탭 시 그날 기록 하단 표시 |
| D4 | 상단 요소 | 블로그/달력은 주간 스트립 숨김. 타입 필터는 3모드 공통 적용 |

---

## 3. 용어 / 데이터

- `DiaryMeta`(기존): `id, timestamp, title, emotion, contentType, contentPreview` — 이미지 경로 없음.
- `state.diaries: List<DiaryMeta>` — **페이지네이션**으로 누적(`pagedMetas`, `LoadMoreDiaries`). 전체 아님.
- `state.diaryTotalCount: Int` — 전체 개수.
- `repository.observeMetas(): Flow<List<DiaryMeta>>` — 전체 메타(경량). 달력 도트 파생에 사용.

---

## 4. 상태 (State)

### 4.1 보기 모드 (UI 로컬)
```
enum class DiaryViewMode { LIST, BLOG, CALENDAR }
```
- `DiaryTabContent` 내부 `rememberSaveable { mutableStateOf(DiaryViewMode.LIST) }`.
- 기본값 LIST. 회전·재구성에도 유지. MVI 상태로 승격하지 않는다(순수 UI).

### 4.2 달력 도트 데이터 (신규 파생)
- `DiaryState.diaryDates: Set<String>` 추가 — 기록이 1건 이상 있는 `yyyy-MM-dd` 집합.
- ViewModel 초기화 시 `repository.observeMetas()` 를 구독해 날짜만 매핑(`SimpleDateFormat` yyyy-MM-dd)하여 채운다. 페이지네이션과 무관하게 전 기간 도트 표시.
- 경량(문자열 집합)이라 메모리 부담 없음.

### 4.3 달력 선택일 기록 (신규 파생)
- `DiaryState.selectedDateDiaries: List<DiaryMeta>` 추가 — 달력에서 탭한 날짜의 기록.
- 이유: 탭한 날짜가 페이지네이션 미로드 구간(오래된 날)일 수 있어 `state.diaries` 필터만으론 누락 가능.
- 채우기: 신규 `repository.metasForDate(dateString): List<DiaryMeta>` (DAO에 날짜 범위 쿼리 1개 추가) 결과를 `SelectDate` 처리 시 반영.
- 리스트 모드는 기존 `state.diaries` 필터 로직 유지(변경 없음). `selectedDateDiaries`는 달력 모드 전용.

---

## 5. 컴포넌트

모두 `DiaryListScreen.kt` 내(또는 필요 시 분리) 신규 컴포저블. `DiaryTabContent` 가 `viewMode` 에 따라 분기.

### 5.1 ViewModeToggle
- 세그먼트 3버튼: 리스트(`Icons.Default.ViewList`) / 블로그(`Icons.AutoMirrored.Filled.Article` 또는 `ViewAgenda`) / 달력(`Icons.Default.CalendarMonth`).
- 배치: 기록 탭 헤더 행(날짜/개수 배지 우측) 또는 타입 필터 칩 줄 위. 컴팩트 아이콘 토글.
- 선택 강조: primary 컨테이너 배경 + primary 아이콘(v3.0 토큰).

### 5.2 DiaryListView (기존)
- 현재 `DiaryTabContent` 의 리스트 렌더 그대로. 주간 스트립 + 타입 필터 + `DiaryListItemCard` LazyColumn + LoadMore.

### 5.3 DiaryBlogView
- `LazyColumn`. `state.diaries`(최신순 정렬 유지)를 `groupBy { yyyy-MM-dd }` → 날짜 내림차순 섹션.
- 각 섹션: `stickyHeader` 로 날짜 라벨(예: `―― 7월 18일 (목) ――`, 오늘/어제 상대 표기) + 그 아래 해당 날짜 카드들.
- 카드: `DiaryListItemCard`(DiaryMeta) 재사용하되 프리뷰를 3줄로(블로그 느낌). 타입 필터 적용.
- 스크롤이 끝에 도달하면 `LoadMoreDiaries` 호출(기존 페이지네이션 재사용).
- 카드 탭 → `onSelectDiary`(상세로).

### 5.4 DiaryCalendarView
- 상단: 월 네비 헤더(‹ 2026년 7월 ›) + 요일 헤더(일~토).
- 월간 7열 그리드: 각 날짜 셀. 기록 있는 날은 하단에 **단일 도트**(로즈 `DiaryTypeColor`, `diaryDates` 기반 존재 여부만 표시 — 타입별 다색 도트는 하지 않음). 오늘 테두리, 선택일 primary 강조.
- 날짜 탭 → `SelectDate(date)` → `selectedDateDiaries` 로드 → 그리드 하단에 `―― N일 기록 ――` + 카드 리스트.
- 월 이동: 화살표로 이전/다음 달. 데이터 도트는 `diaryDates` 에서 즉시(로드 대기 없음).
- 타입 필터: 하단 카드 리스트에 적용.

---

## 6. 데이터 흐름 요약

| 모드 | 데이터 소스 | 페이지네이션 |
|---|---|---|
| 리스트 | `state.diaries` 필터(선택일+타입) | 기존 LoadMore |
| 블로그 | `state.diaries` groupBy 날짜(+타입 필터) | 기존 LoadMore(스크롤 끝) |
| 달력 도트 | `state.diaryDates`(observeMetas 파생) | 불필요(전 기간) |
| 달력 선택일 | `state.selectedDateDiaries`(metasForDate) | 불필요(날짜 단건 조회) |

---

## 7. 손대는 파일

| 파일 | 수정 |
|---|---|
| `mvi/state/DiaryState.kt` | `diaryDates: Set<String>`, `selectedDateDiaries: List<DiaryMeta>` 추가 |
| `mvi/viewmodel/DiaryViewModel.kt` | `observeMetas` 구독→`diaryDates` 채움. `SelectDate` 처리 시 `metasForDate`→`selectedDateDiaries` |
| `data/repository/DiaryRepository.kt` + `DiaryDao.kt` | `metasForDate(dateString)` 날짜범위 조회 1개 추가 |
| `ui/screens/DiaryListScreen.kt` | `DiaryViewMode` enum, `ViewModeToggle`, `DiaryBlogView`, `DiaryCalendarView`, `DiaryTabContent` 분기 |
| `docs/DESIGN.md` | 보기 모드 컴포넌트 패턴 + 변경이력 |

---

## 8. v3.0 일관성

- 날짜 헤더/도트/선택 강조/카드 전부 기존 `MaterialTheme.colorScheme` + 타입색 토큰 재사용. 새 색 없음.
- 카드는 `DiaryListItemCard`(v3.0 재설계본) 재사용.
- 보기 토글 선택 강조는 필터칩과 동일 톤.

---

## 9. 비목표 (Out of Scope)

- 썸네일/이미지 카드 없음 (`DiaryMeta` 에 이미지 경로 없음).
- 무한 과거 프리로드 없음 (블로그는 기존 LoadMore, 달력 도트는 파생 집합).
- AI/음성 파이프라인 무관.
- 주/연 단위 보기 없음 (월간만).
- 보기 모드 영구 저장(설정) 없음 — 세션 내 `rememberSaveable` 만.

---

## 10. 수용 기준 (Acceptance)

- [ ] `.\gradlew assembleDebug` 성공
- [ ] 기록 탭에 리스트/블로그/달력 토글 노출, 전환 동작
- [ ] 블로그: 여러 날 기록이 날짜 헤더로 묶여 최신순 표시, 스크롤 시 추가 로드
- [ ] 달력: 월간 그리드에 기록 있는 날 도트, 날짜 탭 시 그날 기록 하단 표시, 월 이동 동작
- [ ] 블로그/달력에서 주간 스트립 숨김, 타입 필터는 3모드 공통 적용
- [ ] 라이트/다크 모두 v3.0 팔레트 일관
- [ ] 오래된 날짜(페이지네이션 미로드)도 달력 도트·탭 조회 정상
