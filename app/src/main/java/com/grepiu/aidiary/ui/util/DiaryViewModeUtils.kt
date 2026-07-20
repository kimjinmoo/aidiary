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
