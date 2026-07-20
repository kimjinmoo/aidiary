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
        assertEquals("2026-07-19", dateStringOf(end))
        assertTrue(end > start)
    }

    @Test
    fun weekRange_startsMonday_spansSevenDays() {
        // 2026-07-20 은 월요일
        val (startMon, endMon) = weekRangeMillis("2026-07-20")
        assertEquals("2026-07-20", dateStringOf(startMon))
        assertEquals("2026-07-27", dateStringOf(endMon))
        // 같은 주 일요일(2026-07-26) 도 동일한 주 범위로 접힘
        val (startSun, endSun) = weekRangeMillis("2026-07-26")
        assertEquals("2026-07-20", dateStringOf(startSun))
        assertEquals("2026-07-27", dateStringOf(endSun))
    }

    @Test
    fun monthRange_coversWholeMonth() {
        val (start, end) = monthRangeMillis("2026-07-20")
        assertEquals("2026-07-01", dateStringOf(start))
        assertEquals("2026-08-01", dateStringOf(end))
        assertTrue(end > start)
    }

    @Test
    fun metasToDateSet_collectsDistinctDates() {
        val day1 = 1_800_000_000_000L
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
        assertEquals(dateStringOf(nextDay), grouped.first().first)
        val sameDayGroup = grouped.first { it.first == dateStringOf(older) }.second
        assertEquals("mid", sameDayGroup.first().id)
    }

    @Test
    fun buildMonthGrid_hasAllDays_paddedToWeeks() {
        val cells = buildMonthGrid(2026, 7)
        assertEquals(0, cells.size % 7)
        val days = cells.filterNotNull()
        assertEquals(31, days.size)
        assertEquals((1..31).toList(), days)
        assertEquals(cells.indexOfFirst { it != null }, cells.takeWhile { it == null }.size)
    }
}
