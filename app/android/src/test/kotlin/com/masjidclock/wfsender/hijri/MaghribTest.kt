package com.masjidclock.wfsender.hijri

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** The expected values come from the desktop app: app/tools/make_hijri_golden.py. */
class MaghribTest {

    private fun golden(name: String): List<List<String>> =
        javaClass.getResourceAsStream("/hijri/$name")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split(" ") }

    @Test
    fun maghribInDhakaMatchesPraytimesForEachDayFrom2020To2040() {
        val rows = golden("maghrib-dhaka.txt")
        assertEquals(7671, rows.size)
        val wrong = rows.filter { (date, time) -> Maghrib.dhaka(LocalDate.parse(date)).toString() != time }
        assertEquals("days with a different Maghrib time: ${wrong.take(5)}", 0, wrong.size)
    }

    @Test
    fun counterStartMatchesTheDesktopApp() {
        val rows = golden("hijri-start.txt")
        assertEquals(3504, rows.size)
        val wrong = rows.filter { (start, today, expected) ->
            HijriStart.counterStart(LocalDate.parse(start), LocalDate.parse(today)) != LocalDateTime.parse(expected)
        }
        assertEquals("cases with a different counter start: ${wrong.take(5)}", 0, wrong.size)
    }

    @Test
    fun counterStartOfTheHijriExportOf2026_10_18() {
        // progam.hds from the m5 Hijri export holds 6a cb c3 d8 = 2026-10-11 17:14:00.
        assertEquals(
            LocalDateTime.of(2026, 10, 11, 17, 14, 0),
            HijriStart.counterStart(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)),
        )
    }
}
