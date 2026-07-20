# 기록 보기 모드 (리스트/블로그/달력) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기록 탭에서 작성된 글을 리스트/블로그/달력 3가지 보기 모드로 볼 수 있게 한다.

**Architecture:** 순수 UI 로컬 상태(`rememberSaveable`)로 보기 모드 전환. 날짜 그룹핑·달력 그리드·날짜 범위 계산은 순수 함수로 분리해 JUnit 으로 TDD. 달력 도트는 `repository.observeMetas()` 파생 `diaryDates`(전 기간), 달력 선택일 기록은 신규 `metasForDate` 날짜 범위 조회로 채운다. Room 스키마 변경 없음.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room, MVI(StateFlow), JUnit4.

**검증 원칙:** 순수 함수는 JUnit 단위 테스트(TDD). DAO/State/Compose 는 `.\gradlew assembleDebug` 빌드 통과 + 수용 기준으로 확인(이 프로젝트는 인스트루먼트/UI 테스트 문화가 없음 — 기존 `app/src/test` 는 순수 로직 테스트만 존재).

---

## File Structure

**신규**
- `app/src/main/java/com/grepiu/aidiary/ui/util/DiaryViewModeUtils.kt` — 순수 함수(날짜 문자열/범위, 그룹핑, 달력 그리드) + `DiaryViewMode` enum
- `app/src/test/java/com/grepiu/aidiary/ui/util/DiaryViewModeUtilsTest.kt` — 위 순수 함수 단위 테스트
- (컴포저블은 `DiaryListScreen.kt` 내에 추가 — 기존 파일 관례 따름)

**수정**
- `data/repository/DiaryDao.kt` — `metasForDateRange(start,end)` 쿼리 추가
- `data/repository/DiaryRepository.kt` — `metasForDate(dateString)` 추가
- `mvi/state/DiaryState.kt` — `diaryDates`, `selectedDateDiaries` 필드 추가
- `mvi/viewmodel/DiaryViewModel.kt` — `observeMetas`→`diaryDates` 구독, `SelectDate`→`selectedDateDiaries` 로드
- `ui/screens/DiaryListScreen.kt` — `ViewModeToggle`, `DiaryBlogView`, `DiaryCalendarView`, `DiaryTabContent` 분기, 블로그/달력 시 주간 스트립 숨김
- `docs/DESIGN.md` — 보기 모드 패턴 + 변경이력

---

## Task 1: 순수 유틸 + 단위 테스트 (TDD)

**Files:**
- Create: `app/src/main/java/com/grepiu/aidiary/ui/util/DiaryViewModeUtils.kt`
- Test: `app/src/test/java/com/grepiu/aidiary/ui/util/DiaryViewModeUtilsTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/grepiu/aidiary/ui/util/DiaryViewModeUtilsTest.kt`:
```kotlin
package com.grepiu.aidiary.ui.util

import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.repository.DiaryMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryViewModeUtilsTest {

    private fun meta(id: String, ts: Long) =
        DiaryMeta(id = id, timestamp = ts, title = "t$id", emotion = "Neutral",
            contentType = ContentType.DIARY, contentPreview = "p$id")

    @Test
    fun dayRange_isExactlyOneDay_andStartFormatsToInput() {
        val (start, end) = dayRangeMillis("2026-07-18")
        assertEquals("2026-07-18", dateStringOf(start))
        // 다음날 00:00 은 다음 날짜로 포맷된다
        assertEquals("2026-07-19", dateStringOf(end))
        // 한국 등 DST 없는 지역 기준 24시간
        assertTrue(end > start)
    }

    @Test
    fun metasToDateSet_collectsDistinctDates() {
        val day1 = 1_800_000_000_000L // 임의 시각
        val sameDayLater = day1 + 3_600_000L
        val set = metasToDateSet(listOf(meta("a", day1), meta("b", sameDayLater)))
        assertEquals(1, set.size)
    }

    @Test
    fun groupMetasByDate_sortsDatesDescending_andNewestWithinDay() {
        val older = 1_700_000_000_000L
        val newerSameDay = older + 60_000L
        val nextDay = older + 86_400_000L * 2
        val grouped = groupMetasByDate(
            listOf(meta("old", older), meta("mid", newerSameDay), meta("new", nextDay))
        )
        // 최신 날짜 그룹이 먼저
        assertEquals(dateStringOf(nextDay), grouped.first().first)
        // 같은 날 그룹 내 최신순
        val sameDayGroup = grouped.first { it.first == dateStringOf(older) }.second
        assertEquals("mid", sameDayGroup.first().id)
    }

    @Test
    fun buildMonthGrid_hasAllDays_paddedToWeeks() {
        val cells = buildMonthGrid(2026, 7) // 7월 = 31일
        assertEquals(0, cells.size % 7)
        val days = cells.filterNotNull()
        assertEquals(31, days.size)
        assertEquals((1..31).toList(), days)
        // 선행 빈칸 개수 == 첫 non-null 인덱스
        assertEquals(cells.indexOfFirst { it != null }, cells.takeWhile { it == null }.size)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `.\gradlew testDebugUnitTest --tests "com.grepiu.aidiary.ui.util.DiaryViewModeUtilsTest"`
Expected: 컴파일 실패(`dayRangeMillis` 등 미정의)

- [ ] **Step 3: 유틸 구현**

`app/src/main/java/com/grepiu/aidiary/ui/util/DiaryViewModeUtils.kt`:
```kotlin
package com.grepiu.aidiary.ui.util

import com.grepiu.aidiary.data.repository.DiaryMeta
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 기록 보기 모드 */
enum class DiaryViewMode { LIST, BLOG, CALENDAR }

private fun dateFmt() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/** epoch millis → "yyyy-MM-dd" (기기 로컬 타임존) */
fun dateStringOf(timestampMillis: Long): String = dateFmt().format(Date(timestampMillis))

/** "yyyy-MM-dd" → 그날 00:00(포함) ~ 다음날 00:00(제외) 밀리초 (DST-safe: Calendar add) */
fun dayRangeMillis(dateString: String): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.time = dateFmt().parse(dateString) ?: Date(0)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_MONTH, 1)
    return start to cal.timeInMillis
}

/** 메타 목록 → 기록 있는 날짜(yyyy-MM-dd) 집합 */
fun metasToDateSet(metas: List<DiaryMeta>): Set<String> =
    metas.mapTo(HashSet()) { dateStringOf(it.timestamp) }

/** 메타 목록 → 날짜 내림차순 그룹. 각 그룹 내부도 최신순. */
fun groupMetasByDate(metas: List<DiaryMeta>): List<Pair<String, List<DiaryMeta>>> =
    metas.groupBy { dateStringOf(it.timestamp) }
        .toSortedMap(reverseOrder())
        .map { (date, list) -> date to list.sortedByDescending { it.timestamp } }

/** 연·월(month:1-12) 달력 셀 목록. null=선행/후행 빈칸, Int=일자. 7의 배수 길이. */
fun buildMonthGrid(year: Int, month1to12: Int): List<Int?> {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(year, month1to12 - 1, 1)
    val leading = cal.get(Calendar.DAY_OF_WEEK) - 1 // 일요일=1 → 빈칸 0
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = ArrayList<Int?>()
    repeat(leading) { cells.add(null) }
    for (d in 1..daysInMonth) cells.add(d)
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `.\gradlew testDebugUnitTest --tests "com.grepiu.aidiary.ui.util.DiaryViewModeUtilsTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/grepiu/aidiary/ui/util/DiaryViewModeUtils.kt app/src/test/java/com/grepiu/aidiary/ui/util/DiaryViewModeUtilsTest.kt
git commit -m "feat: 보기 모드 순수 유틸(날짜 그룹/달력 그리드) + 단위 테스트"
```

---

## Task 2: DAO/Repository 날짜 범위 조회

**Files:**
- Modify: `app/src/main/java/com/grepiu/aidiary/data/repository/DiaryDao.kt`
- Modify: `app/src/main/java/com/grepiu/aidiary/data/repository/DiaryRepository.kt`

- [ ] **Step 1: DAO 쿼리 추가**

`DiaryDao.kt` — 기존 `observeMetas()` 쿼리(projection: `id, timestamp, title, emotion, content_type, content_preview` → `DiaryMetaRow`) 바로 아래에 추가:
```kotlin
    @Query("SELECT id, timestamp, title, emotion, content_type, content_preview FROM diary WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    suspend fun metasForDateRange(start: Long, end: Long): List<DiaryMetaRow>
```

- [ ] **Step 2: Repository 함수 추가**

`DiaryRepository.kt` — `pagedMetas(...)` 아래에 추가(파일 상단에 `import com.grepiu.aidiary.ui.util.dayRangeMillis` 추가):
```kotlin
    /** 특정 날짜(yyyy-MM-dd)의 기록 메타. 페이지네이션과 무관하게 그날 전체를 조회. */
    suspend fun metasForDate(dateString: String): List<DiaryMeta> {
        val (start, end) = dayRangeMillis(dateString)
        return dao.metasForDateRange(start, end).map { it.toDiaryMeta() }
    }
```
> `toDiaryMeta()` 는 이미 이 파일 하단(약 396행)에 `private fun DiaryMetaRow.toDiaryMeta()` 로 존재. 그대로 사용.

- [ ] **Step 3: 빌드 확인**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (Room 이 새 쿼리 생성)

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/grepiu/aidiary/data/repository/DiaryDao.kt app/src/main/java/com/grepiu/aidiary/data/repository/DiaryRepository.kt
git commit -m "feat: 날짜 범위 메타 조회 metasForDate 추가"
```

---

## Task 3: State + ViewModel 배선 (diaryDates, selectedDateDiaries)

**Files:**
- Modify: `app/src/main/java/com/grepiu/aidiary/mvi/state/DiaryState.kt`
- Modify: `app/src/main/java/com/grepiu/aidiary/mvi/viewmodel/DiaryViewModel.kt`

- [ ] **Step 1: State 필드 추가**

`DiaryState.kt` — `diaries: List<DiaryMeta>` 필드 근처에 추가:
```kotlin
    /** 기록이 1건 이상 있는 날짜(yyyy-MM-dd) 집합 — 달력 도트용. observeMetas 파생. */
    val diaryDates: Set<String> = emptySet(),
    /** 달력에서 선택한 날짜의 기록(페이지네이션 무관 조회 결과). */
    val selectedDateDiaries: List<DiaryMeta> = emptyList(),
```

- [ ] **Step 2: ViewModel — diaryDates 구독**

`DiaryViewModel.kt` `init { ... }`(약 113행) 블록 안, 기존 `viewModelScope.launch { ... }` 들과 나란히 추가(파일 상단 import: `import com.grepiu.aidiary.ui.util.dateStringOf`):
```kotlin
        // 전체 메타 구독 → 달력 도트용 날짜 집합 (페이지네이션 무관)
        viewModelScope.launch {
            repository.observeMetas().collect { metas ->
                val dates = metas.mapTo(HashSet()) { dateStringOf(it.timestamp) }
                _state.update { it.copy(diaryDates = dates) }
            }
        }
```

- [ ] **Step 3: ViewModel — SelectDate 시 selectedDateDiaries 로드**

`DiaryViewModel.kt` `is DiaryIntent.SelectDate ->`(약 659행) 핸들러를 아래로 교체:
```kotlin
            is DiaryIntent.SelectDate -> {
                _state.update { it.copy(selectedDateString = intent.dateString) }
                viewModelScope.launch {
                    val metas = repository.metasForDate(intent.dateString)
                    _state.update { it.copy(selectedDateDiaries = metas) }
                }
            }
```

- [ ] **Step 4: 빌드 확인**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/grepiu/aidiary/mvi/state/DiaryState.kt app/src/main/java/com/grepiu/aidiary/mvi/viewmodel/DiaryViewModel.kt
git commit -m "feat: diaryDates(달력 도트)/selectedDateDiaries(달력 선택일) 상태 배선"
```

---

## Task 4: 보기 토글 + DiaryTabContent 분기 (블로그/달력 스텁)

**Files:**
- Modify: `app/src/main/java/com/grepiu/aidiary/ui/screens/DiaryListScreen.kt`

**배경:** 현재 `DiaryTabContent` 는 `Column { LazyColumn { item { 날짜타이틀+개수배지+타입필터칩 }; items(filteredDiaries){ DiaryListItemCard } ... } }` 구조. 이 Task 는 (a) 타입 필터 칩 줄 옆에 `ViewModeToggle` 추가, (b) `viewMode` 로컬 상태 도입, (c) `when(viewMode)` 로 리스트/블로그(스텁)/달력(스텁) 분기.

- [ ] **Step 1: import 추가**

`DiaryListScreen.kt` 상단 import 블록에:
```kotlin
import com.grepiu.aidiary.ui.util.DiaryViewMode
import com.grepiu.aidiary.ui.util.dateStringOf
import com.grepiu.aidiary.ui.util.groupMetasByDate
import com.grepiu.aidiary.ui.util.buildMonthGrid
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.CalendarMonth
```

- [ ] **Step 2: ViewModeToggle 컴포저블 추가**

`DiaryListScreen.kt` 맨 아래(다른 private 컴포저블들 곁)에 추가:
```kotlin
/** 리스트/블로그/달력 세그먼트 토글 */
@Composable
private fun ViewModeToggle(
    mode: DiaryViewMode,
    onModeChange: (DiaryViewMode) -> Unit
) {
    val items = listOf(
        Triple(DiaryViewMode.LIST, Icons.Default.ViewList, "리스트"),
        Triple(DiaryViewMode.BLOG, Icons.Default.ViewAgenda, "블로그"),
        Triple(DiaryViewMode.CALENDAR, Icons.Default.CalendarMonth, "달력"),
    )
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            items.forEach { (m, icon, desc) ->
                val selected = m == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onModeChange(m) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, desc,
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: DiaryTabContent 에 viewMode 상태 + 토글 배치 + 분기**

`DiaryTabContent` 함수 안, 기존 `val lazyListState = rememberLazyListState()` 근처에 추가:
```kotlin
    var viewMode by rememberSaveable { mutableStateOf(DiaryViewMode.LIST) }
```

기존 타입 필터 칩을 담은 `item { Column { Row { 날짜타이틀 + 개수배지 } ; Spacer ; Row { chips } } }` 에서, **날짜 타이틀 Row 의 오른쪽 끝에 토글을 붙인다.** 해당 Row(제목 Text + 개수 Surface 가 든 Row)의 닫는 지점 직전에 추가:
```kotlin
                        Spacer(Modifier.weight(1f))
                        ViewModeToggle(mode = viewMode, onModeChange = { viewMode = it })
```
> 이 Row 는 `horizontalArrangement` 지정이 없으므로 `Spacer(Modifier.weight(1f))` 가 토글을 오른쪽으로 민다. 제목/배지는 그대로 왼쪽.

그리고 `LazyColumn(state = lazyListState, ...) { item { ...헤더... } ; 일기 항목들 }` 전체를 `when (viewMode)` 로 감싼다. 기존 `LazyColumn { ... }` 을 그대로 `DiaryViewMode.LIST` 브랜치에 두고, 나머지는 스텁:
```kotlin
    when (viewMode) {
        DiaryViewMode.LIST -> {
            // (기존 LazyColumn 블록 그대로)
        }
        DiaryViewMode.BLOG -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("블로그 보기 (다음 Task)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DiaryViewMode.CALENDAR -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("달력 보기 (다음 Task)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
```
> 주의: 토글은 LIST 헤더 안에 있으므로 블로그/달력 스텁에선 안 보인다. Task 6 에서 토글을 `when` 위 공통 헤더로 끌어올린다. 이 Task 단계에선 리스트에서 토글로 전환→스텁 표시만 확인.

- [ ] **Step 4: 빌드 확인**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/grepiu/aidiary/ui/screens/DiaryListScreen.kt
git commit -m "feat: 보기 모드 토글 + DiaryTabContent 분기(블로그/달력 스텁)"
```

---

## Task 5: DiaryBlogView (날짜 묶음 피드)

**Files:**
- Modify: `app/src/main/java/com/grepiu/aidiary/ui/screens/DiaryListScreen.kt`

- [ ] **Step 1: DiaryBlogView 컴포저블 추가**

`DiaryListScreen.kt` 하단에 추가:
```kotlin
/** 블로그 보기 — 전체 기록을 날짜 헤더로 묶어 최신순 피드로. 기존 페이지네이션 재사용. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiaryBlogView(
    diaries: List<DiaryMeta>,
    onSelectDiary: (DiaryMeta) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    val groups = remember(diaries) { groupMetasByDate(diaries) }

    // 끝 근처 도달 시 추가 로드
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("아직 기록이 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        groups.forEach { (dateStr, metas) ->
            item(key = "h_$dateStr") {
                BlogDateHeader(dateStr)
            }
            items(metas, key = { it.id }) { meta ->
                DiaryListItemCard(diary = meta, onClick = { onSelectDiary(meta) })
            }
        }
    }
}

/** 블로그 날짜 구분 헤더 (―― 7월 18일 (목) ――, 오늘/어제 상대 표기) */
@Composable
private fun BlogDateHeader(dateString: String) {
    val label = remember(dateString) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = fmt.format(Date())
        val yesterday = fmt.format(Date(System.currentTimeMillis() - 86_400_000L))
        val pretty = try {
            SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(fmt.parse(dateString) ?: Date())
        } catch (_: Exception) { dateString }
        when (dateString) {
            today -> "오늘 · $pretty"
            yesterday -> "어제 · $pretty"
            else -> pretty
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
```
> `System.currentTimeMillis()` 사용은 컴포저블 내부라 허용(테스트 대상 아님). `ExperimentalFoundationApi` 는 이미 파일에서 쓰지 않으면 `@OptIn` 로 커버.

- [ ] **Step 2: BLOG 브랜치를 실제 뷰로 교체**

Task 4 의 BLOG 스텁을 교체:
```kotlin
        DiaryViewMode.BLOG -> {
            DiaryBlogView(
                diaries = filteredDiariesForBlog(state, selectedTypeFilter),
                onSelectDiary = onSelectDiary,
                onLoadMore = onLoadMore
            )
        }
```
`DiaryTabContent` 안(또는 파일 하단)에 타입 필터만 적용하는 헬퍼 추가:
```kotlin
private fun filteredDiariesForBlog(state: DiaryState, typeFilter: ContentType?): List<DiaryMeta> =
    if (typeFilter == null) state.diaries
    else state.diaries.filter { it.contentType == typeFilter }
```

- [ ] **Step 3: 빌드 확인**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/grepiu/aidiary/ui/screens/DiaryListScreen.kt
git commit -m "feat: 블로그 보기(날짜 묶음 피드 + 페이지네이션)"
```

---

## Task 6: DiaryCalendarView (월간 그리드)

**Files:**
- Modify: `app/src/main/java/com/grepiu/aidiary/ui/screens/DiaryListScreen.kt`

- [ ] **Step 1: DiaryCalendarView 컴포저블 추가**

`DiaryListScreen.kt` 하단에 추가:
```kotlin
/** 달력 보기 — 월간 그리드 + 선택일 하단 기록. 도트는 diaryDates, 선택일 기록은 selectedDateDiaries. */
@Composable
private fun DiaryCalendarView(
    diaryDates: Set<String>,
    selectedDateString: String,
    selectedDateDiaries: List<DiaryMeta>,
    typeFilter: ContentType?,
    onDateSelect: (String) -> Unit,
    onSelectDiary: (DiaryMeta) -> Unit
) {
    // 표시 중인 연/월 (선택일 기준 초기화)
    val initCal = remember(selectedDateString) {
        Calendar.getInstance().apply {
            try { time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateString) ?: Date() }
            catch (_: Exception) {}
        }
    }
    var year by remember { mutableStateOf(initCal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(initCal.get(Calendar.MONTH) + 1) } // 1-12

    val cells = remember(year, month) { buildMonthGrid(year, month) }
    val monthPrefix = remember(year, month) { "%04d-%02d-".format(year, month) }
    val visibleDiaries = remember(selectedDateDiaries, typeFilter) {
        if (typeFilter == null) selectedDateDiaries
        else selectedDateDiaries.filter { it.contentType == typeFilter }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 월 네비 헤더
        item(key = "cal_nav") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                IconButton(onClick = {
                    if (month == 1) { month = 12; year-- } else month--
                }) { Icon(Icons.Default.ChevronLeft, "이전 달") }
                Text("${year}년 ${month}월", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(onClick = {
                    if (month == 12) { month = 1; year++ } else month++
                }) { Icon(Icons.Default.ChevronRight, "다음 달") }
            }
        }
        // 요일 헤더
        item(key = "cal_dow") {
            Row(Modifier.fillMaxWidth()) {
                listOf("일","월","화","수","목","금","토").forEach { d ->
                    Text(d, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }
        // 날짜 그리드 (7개씩 행)
        items(cells.chunked(7), key = { row -> "row_" + row.joinToString("_") { it?.toString() ?: "x" } }) { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val dateStr = monthPrefix + "%02d".format(day)
                        val hasEntry = diaryDates.contains(dateStr)
                        val isSelected = dateStr == selectedDateString
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onDateSelect(dateStr) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$day", fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasEntry) {
                                    Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape)
                                        .background(if (isSelected) Color.White else DiaryTypeColor))
                                }
                            }
                        }
                    }
                }
            }
        }
        // 선택일 기록
        item(key = "cal_sel_header") {
            val pretty = remember(selectedDateString) {
                try { SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateString) ?: Date()) }
                catch (_: Exception) { selectedDateString }
            }
            Text("$pretty 기록", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
        }
        if (visibleDiaries.isEmpty()) {
            item(key = "cal_empty") {
                Text("이 날의 기록이 없어요", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
        } else {
            items(visibleDiaries, key = { it.id }) { meta ->
                DiaryListItemCard(diary = meta, onClick = { onSelectDiary(meta) })
            }
        }
    }
}
```
> import 추가: `androidx.compose.material.icons.filled.ChevronLeft`, `ChevronRight`, `androidx.compose.foundation.layout.aspectRatio`, `java.util.Calendar` (없으면). `CircleShape`, `DiaryTypeColor`, `SimpleDateFormat`, `Locale`, `Date` 는 파일에 이미 있음.

- [ ] **Step 2: CALENDAR 브랜치 교체**

Task 4 의 CALENDAR 스텁을 교체(콜백은 Step 3 에서 추가할 파라미터 `onDateSelectFromCalendar` 사용):
```kotlin
        DiaryViewMode.CALENDAR -> {
            DiaryCalendarView(
                diaryDates = state.diaryDates,
                selectedDateString = state.selectedDateString,
                selectedDateDiaries = state.selectedDateDiaries,
                typeFilter = selectedTypeFilter,
                onDateSelect = onDateSelectFromCalendar,
                onSelectDiary = onSelectDiary
            )
        }
```
> `DiaryTabContent` 는 날짜 선택 인텐트를 직접 안 받으므로, 상위에서 넘어온 콜백을 쓴다. Step 3 에서 `DiaryTabContent` 시그니처에 `onDateSelectFromCalendar: (String) -> Unit` 를 추가하고 호출부에서 `SelectDate` 인텐트로 연결한다.

- [ ] **Step 3: DiaryTabContent 시그니처 + 호출부 수정**

`fun DiaryTabContent(` 파라미터 목록에 추가:
```kotlin
    onDateSelectFromCalendar: (String) -> Unit,
```
`DiaryListScreen` 내 `"DIARY" -> { DiaryTabContent( ... ) }` 호출부에 추가:
```kotlin
                            onDateSelectFromCalendar = { onIntent(DiaryIntent.SelectDate(it)) },
```
> 기존 주간 스트립도 `SelectDate` 인텐트를 쓰므로 동일 경로. Step 2 의 `onDateSelect = onDateSelectFromCalendar` 가 이 콜백을 받는다.

- [ ] **Step 4: 초기 선택일 기록 로드 보장**

달력 첫 진입 시 `selectedDateDiaries` 가 비어있을 수 있으므로, `DiaryTabContent` 안에 최초 1회 로드:
```kotlin
    LaunchedEffect(Unit) { onDateSelectFromCalendar(state.selectedDateString) }
```
> 이미 선택된 날짜에 대해 `SelectDate` 를 재발행 → `selectedDateDiaries` 채움. 리스트 모드 동작에는 영향 없음(같은 selectedDateString).

- [ ] **Step 5: 빌드 확인**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/grepiu/aidiary/ui/screens/DiaryListScreen.kt
git commit -m "feat: 달력 보기(월간 그리드 + 도트 + 선택일 기록)"
```

---

## Task 7: 토글 공통 헤더화 + 주간 스트립 숨김 + 문서 + 마감

**Files:**
- Modify: `app/src/main/java/com/grepiu/aidiary/ui/screens/DiaryListScreen.kt`
- Modify: `docs/DESIGN.md`

- [ ] **Step 1: 토글을 모드 공통 헤더로 승격**

Task 4 에서 토글이 LIST 헤더 안에만 있어 블로그/달력에서 안 보이는 문제 해결. `DiaryTabContent` 의 `when(viewMode)` **바깥 위쪽**에 공통 헤더 Row 를 두어 항상 토글 노출:
```kotlin
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        // 타입 필터(전체/일기/새글/메모) — 3모드 공통
        DiaryTypeFilterRow(selectedTypeFilter, onTypeFilterChange, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        ViewModeToggle(mode = viewMode, onModeChange = { viewMode = it })
    }
```
> 이를 위해 기존 LIST 헤더 `item{}` 안에 있던 타입 필터 칩 Row 를 `DiaryTypeFilterRow` 컴포저블로 추출(현재 칩 렌더 코드를 그대로 옮김). LIST 헤더의 `item{}` 에서는 날짜 타이틀 + 개수 배지만 남긴다. 블로그/달력에는 날짜 타이틀이 불필요하므로 공통 헤더엔 필터+토글만.

- [ ] **Step 2: 블로그/달력에서 주간 캘린더 스트립 숨김**

`DiaryListScreen` 상위에서 주간 스트립(`WeeklyCalendarStrip` 을 감싼 `Surface`)과 `DailyOverviewHeader` 는 `AnimatedVisibility(!headerCollapsed)` 안에 있다. 보기 모드는 `DiaryTabContent` 로컬 상태라 상위에서 모른다 → **보기 모드를 상위로 hoist**한다:
  - `DiaryListScreen` 에 `var diaryViewMode by rememberSaveable { mutableStateOf(DiaryViewMode.LIST) }` 추가.
  - `DiaryTabContent` 의 로컬 `viewMode` 제거하고 파라미터 `viewMode: DiaryViewMode`, `onViewModeChange: (DiaryViewMode) -> Unit` 로 승격. 호출부에서 `diaryViewMode`/`{ diaryViewMode = it }` 전달.
  - 주간 스트립을 감싼 `Surface`(캘린더) 를 `if (state.activeTab != "DIARY" || diaryViewMode == DiaryViewMode.LIST) { ... }` 로 감싼다. 즉 기록 탭의 블로그/달력일 때만 스트립 숨김(다른 탭은 영향 없음).

```kotlin
                        if (!(state.activeTab == "DIARY" && diaryViewMode != DiaryViewMode.LIST)) {
                            // A. 글래스모피즘 캘린더 스트립 컨테이너 (기존 Surface { WeeklyCalendarStrip(...) })
                        }
```

- [ ] **Step 3: 빌드 확인**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: DESIGN.md 갱신**

`docs/DESIGN.md` 컴포넌트 패턴 섹션(7.x)에 추가:
```markdown
### 7.9 기록 보기 모드 (리스트/블로그/달력)
- 기록 탭 헤더에 `ViewModeToggle`(세그먼트 3버튼). 선택 강조 primary.
- 리스트: 선택일 기준 카드(기존). 블로그: 전체 최신순 날짜 묶음 피드(`groupMetasByDate`, stickyHeader 날짜). 달력: 월간 7열 그리드(`buildMonthGrid`) + 도트(`diaryDates`) + 선택일 기록(`selectedDateDiaries`).
- 블로그/달력에서 주간 스트립 숨김. 타입 필터는 3모드 공통.
```
그리고 변경 이력 표 맨 위에 행 추가:
```markdown
| 2026-07-20 | v3.2 | 기록 보기 모드(리스트/블로그/달력) 추가. metasForDate/diaryDates 파생 조회, Room 스키마 무변경 |
```

- [ ] **Step 5: 전체 테스트 + 빌드 + 커밋**

Run: `.\gradlew testDebugUnitTest --tests "com.grepiu.aidiary.ui.util.DiaryViewModeUtilsTest"`
Expected: PASS
Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/grepiu/aidiary/ui/screens/DiaryListScreen.kt docs/DESIGN.md
git commit -m "feat: 보기 토글 공통 헤더화 + 블로그/달력 시 주간 스트립 숨김 + 문서"
```

---

## 수용 기준 재확인 (Task 7 이후 수동)

- [ ] 기록 탭에 리스트/블로그/달력 토글 노출, 전환 동작
- [ ] 블로그: 여러 날 기록이 날짜 헤더로 묶여 최신순, 스크롤 시 추가 로드
- [ ] 달력: 월간 그리드 도트, 날짜 탭 시 그날 기록 하단, 월 이동 동작
- [ ] 블로그/달력에서 주간 스트립 숨김, 타입 필터 3모드 공통
- [ ] 라이트/다크 v3.0 팔레트 일관
- [ ] 오래된 날짜(페이지네이션 미로드)도 달력 도트·탭 조회 정상
