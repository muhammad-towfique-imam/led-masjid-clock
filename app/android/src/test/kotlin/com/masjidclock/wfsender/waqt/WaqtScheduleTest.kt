package com.masjidclock.wfsender.waqt

import com.masjidclock.wfsender.hijri.Sun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The expected copies come from the desktop app's code: app/tools/make_waqt_golden.py. */
class WaqtScheduleTest {

    private class Case(
        val name: String,
        val now: LocalDateTime,
        val times: List<HourMinute>,
        val changes: List<WaqtChange>,
        val copies: List<WaqtCopy>,
    )

    private val cases: List<Case> by lazy {
        val out = mutableListOf<Case>()
        var header: List<String>? = null
        var copies = mutableListOf<WaqtCopy>()
        fun flush() = header?.let { h ->
            val times = h[3].split(",").map { hm(it) }
            val changes = if (h[4] == "-") emptyList() else h[4].split(";").map {
                val (d, i, t) = it.split("|")
                WaqtChange(LocalDate.parse(d, DMY), i.toInt(), hm(t))
            }
            out += Case(h[1], LocalDateTime.parse(h[2]), times, changes, copies)
        }
        for (line in javaClass.getResourceAsStream("/waqt/copies.txt")!!.bufferedReader().readLines()) {
            val f = line.split("\t")
            if (f[0] == "#") {
                flush()
                header = f
                copies = mutableListOf()
            } else if (f.size == 12) {
                copies += WaqtCopy(
                    LocalDate.parse(f[0]), LocalDate.parse(f[1]),
                    f[2].toIntOrNull(), f[3].toIntOrNull(), f.subList(4, 12),
                )
            }
        }
        flush()
        out
    }

    private fun case(name: String) = cases.first { it.name == name }

    @Test
    fun matchesTheDesktopApp() {
        assertEquals(11, cases.size)
        for (c in cases) {
            assertEquals(c.name, c.copies, WaqtSchedule.copies(c.times, c.changes, c.now, desktopEnd = true))
        }
    }

    @Test
    fun noonTime() {
        assertEquals(LocalDateTime.of(2026, 10, 30, 13, 0), WaqtSchedule.applyTime(LocalDate.of(2026, 10, 31), 1, HourMinute(12, 30)))
        assertEquals("12:30", HourMinute(12, 30).text())
        assertEquals(" 4:05", HourMinute(4, 5).text())
    }

    @Test
    fun endThreeDaysAfterTheLastChange() {
        val c = case("w1")
        val copies = WaqtSchedule.copies(c.times, c.changes, c.now)
        val last = copies.last()
        assertEquals(LocalDate.of(2026, 11, 3), last.dateEnd)
        assertEquals(86_399, last.timeEnd)
        // Sunrise and Sunset are calculated up to the end.
        val sun = Sun.dhaka(LocalDate.of(2026, 11, 3))
        assertEquals(HourMinute(sun.sunrise.hour, sun.sunrise.minute).text(), last.times[6])
        assertEquals(HourMinute(sun.sunset.hour - 12, sun.sunset.minute).text(), last.times[7])
        assertNoOverlap(copies)
    }

    @Test
    fun noChangesRunsThirtyDays() {
        val c = case("w0")
        assertEquals(c.now.toLocalDate().plusDays(30), WaqtSchedule.copies(c.times, c.changes, c.now).last().dateEnd)
    }

    @Test
    fun longScheduleHasNoOverlap() {
        val c = case("forty-days")
        val copies = WaqtSchedule.copies(c.times, c.changes, c.now)
        assertEquals(LocalDate.of(2026, 12, 3), copies.last().dateEnd)
        assertNoOverlap(copies)
    }

    @Test
    fun sameApplyTimeGivesNoReversedWindow() {
        // Zuhr and Jumu'ah change to 1:20 on the same date: both apply on 30 Oct at 13:50.
        val c = case("w0")
        val changes = listOf(
            WaqtChange(LocalDate.of(2026, 10, 31), 1, HourMinute(1, 20)),
            WaqtChange(LocalDate.of(2026, 10, 31), 5, HourMinute(1, 20)),
        )
        val copies = WaqtSchedule.copies(c.times, changes, c.now)
        assertNoOverlap(copies)
        assertTrue(copies.none { it.times[1] == " 1:20" && it.times[5] != " 1:20" })
    }

    /** The copies are in order, and each starts after the one before ends. */
    private fun assertNoOverlap(copies: List<WaqtCopy>) {
        var previousEnd: LocalDateTime? = null
        for (copy in copies) {
            val start = copy.dateStart.atStartOfDay().plusSeconds((copy.timeStart ?: 0).toLong())
            val end = copy.dateEnd.atStartOfDay().plusSeconds((copy.timeEnd ?: 86_399).toLong())
            assertTrue("$copy", !end.isBefore(start))
            previousEnd?.let { assertTrue("$copy starts before $it", start.isAfter(it)) }
            previousEnd = end
        }
    }

    private companion object {
        val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
        fun hm(text: String): HourMinute = text.split(":").let { HourMinute(it[0].toInt(), it[1].toInt()) }
    }
}
