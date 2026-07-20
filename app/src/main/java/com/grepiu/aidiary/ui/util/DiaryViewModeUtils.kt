package com.grepiu.aidiary.ui.util

import com.grepiu.aidiary.data.repository.DiaryMeta
import com.grepiu.aidiary.data.repository.PlannerTask
import com.grepiu.aidiary.data.repository.Goal
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

/** "yyyy-MM-dd" 가 속한 주(월요일 시작) 00:00(포함) ~ 다음 주 월요일 00:00(제외) 밀리초. */
fun weekRangeMillis(dateString: String): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.time = dateFmt().parse(dateString) ?: Date(0)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    // DAY_OF_WEEK: 일=1 … 토=7. 월요일까지 되돌릴 일수.
    val diffToMonday = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 6
        else cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
    cal.add(Calendar.DAY_OF_MONTH, -diffToMonday)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_MONTH, 7)
    return start to cal.timeInMillis
}

/** "yyyy-MM-dd" 가 속한 달 1일 00:00(포함) ~ 다음 달 1일 00:00(제외) 밀리초. */
fun monthRangeMillis(dateString: String): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.time = dateFmt().parse(dateString) ?: Date(0)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
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

// =============================================================================
// 전역 통합 보기(블로그/달력) — 일기 + 계획(할 일) + 목표를 날짜 기준으로 합침
// =============================================================================

/** 통합 보기 항목: 일기/할 일/목표를 한 타입으로 감싸 날짜별로 섞어 표시. */
sealed interface DayItem {
    val date: String   // yyyy-MM-dd
    val sortTs: Long

    data class DiaryItem(val meta: DiaryMeta) : DayItem {
        override val date get() = dateStringOf(meta.timestamp)
        override val sortTs get() = meta.timestamp
    }
    data class TaskItem(val task: PlannerTask) : DayItem {
        override val date get() = task.dateString
        override val sortTs get() = task.timestamp
    }
    data class GoalDayItem(val goal: Goal) : DayItem {
        override val date get() = dateStringOf(goal.timestamp)
        override val sortTs get() = goal.timestamp
    }
}

/** 일기+할 일+목표를 날짜 내림차순으로 그룹. 각 그룹 내부도 최신순. */
fun buildUnifiedByDate(
    diaries: List<DiaryMeta>,
    tasks: List<PlannerTask>,
    goals: List<Goal>
): List<Pair<String, List<DayItem>>> {
    val items = ArrayList<DayItem>(diaries.size + tasks.size + goals.size)
    diaries.forEach { items.add(DayItem.DiaryItem(it)) }
    tasks.forEach { items.add(DayItem.TaskItem(it)) }
    goals.forEach { items.add(DayItem.GoalDayItem(it)) }
    return items.groupBy { it.date }
        .toSortedMap(reverseOrder())
        .map { (date, list) -> date to list.sortedByDescending { it.sortTs } }
}

/** 일기·할 일·목표 중 하나라도 있는 날짜 집합 (통합 달력 도트용). */
fun unifiedDateSet(
    diaries: List<DiaryMeta>,
    tasks: List<PlannerTask>,
    goals: List<Goal>
): Set<String> {
    val set = HashSet<String>()
    diaries.forEach { set.add(dateStringOf(it.timestamp)) }
    tasks.forEach { set.add(it.dateString) }
    goals.forEach { set.add(dateStringOf(it.timestamp)) }
    return set
}

/** 특정 날짜의 통합 항목만 추출(달력 선택일 하단용), 최신순. */
fun unifiedForDate(
    date: String,
    diaries: List<DiaryMeta>,
    tasks: List<PlannerTask>,
    goals: List<Goal>
): List<DayItem> {
    val items = ArrayList<DayItem>()
    diaries.forEach { if (dateStringOf(it.timestamp) == date) items.add(DayItem.DiaryItem(it)) }
    tasks.forEach { if (it.dateString == date) items.add(DayItem.TaskItem(it)) }
    goals.forEach { if (dateStringOf(it.timestamp) == date) items.add(DayItem.GoalDayItem(it)) }
    return items.sortedByDescending { it.sortTs }
}
