package com.masjidclock.wfsender.bangla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The expected ranges come from the desktop app: app/tools/make_bangla_golden.py. */
class BanglaCalendarTest {

    @Test
    fun rangesMatchTheDesktopAppFrom1400To1499() {
        val rows = javaClass.getResourceAsStream("/bangla/ranges.txt")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }.map { it.split(" ") }
        assertEquals(1300, rows.size)
        val wrong = rows.filter { (year, index, start, end) ->
            BanglaCalendar.ranges(year.toInt())[index.toInt() - 1] != LocalDate.parse(start)..LocalDate.parse(end)
        }
        assertEquals("ranges that differ: ${wrong.take(5)}", 0, wrong.size)
    }

    @Test
    fun currentYear() {
        assertEquals(1432, BanglaCalendar.currentYear(LocalDate.of(2026, 4, 13)))
        assertEquals(1433, BanglaCalendar.currentYear(LocalDate.of(2026, 4, 14)))
        assertEquals(1432, BanglaCalendar.currentYear(LocalDate.of(2026, 1, 9)))
    }

    @Test
    fun dateOnThePhotoDay() {
        assertEquals(BanglaDate(25, "Poush", 1432), BanglaCalendar.dateOn(1432, LocalDate.of(2026, 1, 9)))
    }

    @Test
    fun dateOnTheYearEdges() {
        assertEquals(BanglaDate(1, "Baishakh", 1432), BanglaCalendar.dateOn(1432, LocalDate.of(2025, 4, 14)))
        assertEquals(BanglaDate(30, "Choitro", 1432), BanglaCalendar.dateOn(1432, LocalDate.of(2026, 4, 13)))
        assertEquals(BanglaDate(1, "Baishakh", 1433), BanglaCalendar.dateOn(1432, LocalDate.of(2026, 4, 14)))
    }

    @Test
    fun dateOnOutsideTheYear() {
        assertNull(BanglaCalendar.dateOn(1440, LocalDate.of(2026, 1, 9)))
    }
}
