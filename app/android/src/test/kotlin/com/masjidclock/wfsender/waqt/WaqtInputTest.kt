package com.masjidclock.wfsender.waqt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WaqtInputTest {

    private val times: List<HourMinute?> =
        listOf(HourMinute(5, 10), HourMinute(1, 15), HourMinute(4, 15), HourMinute(5, 40), HourMinute(7, 0), HourMinute(1, 30))
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    @Test
    fun prayers() {
        assertEquals(listOf("Fajr", "Zuhr", "Asr", "Magrib", "Isha", "Jumu'ah"), WaqtPrayers.NAMES)
        assertEquals("am", WaqtPrayers.period(0))
        assertEquals("pm", WaqtPrayers.period(5))
    }

    @Test
    fun mergeMovesPastChangesToToday() {
        // The 21 Oct change applies on 20 Oct at 17:00 (4:30 pm − 1 day + 30 min): before now.
        val values = WaqtValues(times, listOf(WaqtChange(d(10, 21), 2, HourMinute(4, 30)), WaqtChange(d(10, 31), 2, HourMinute(4, 0))))
        val merged = WaqtInput.merged(values, LocalDateTime.of(2026, 10, 21, 12, 0))
        assertEquals(HourMinute(4, 30), merged.times[2])
        assertEquals(listOf(WaqtChange(d(10, 31), 2, HourMinute(4, 0))), merged.changes)
    }

    @Test
    fun validity() {
        assertTrue(WaqtInput.isValid(WaqtValues(times, emptyList())))
        assertFalse(WaqtInput.isValid(WaqtValues(times.toMutableList().also { it[3] = null }, emptyList())))
        assertFalse(WaqtInput.isValid(WaqtValues(times, listOf(WaqtChange(d(10, 31), 2, HourMinute(4, 0)), WaqtChange(d(10, 31), 2, HourMinute(4, 5))))))
        assertTrue(WaqtInput.isValid(WaqtValues(times, listOf(WaqtChange(d(10, 31), 2, HourMinute(4, 0)), WaqtChange(d(10, 31), 3, HourMinute(5, 30))))))
    }

    @Test
    fun nextRow() {
        val values = WaqtValues(times, listOf(WaqtChange(d(10, 31), 2, HourMinute(4, 30))))
        assertEquals(WaqtChange(d(11, 1), 2, HourMinute(4, 30)), WaqtInput.nextRow(values, 2, d(10, 21)))
        assertEquals(WaqtChange(d(10, 22), 0, HourMinute(5, 10)), WaqtInput.nextRow(values, 0, d(10, 21)))
    }

    @Test
    fun codecRoundTripAndDamaged() {
        val values = WaqtValues(times.toMutableList().also { it[4] = null }, listOf(WaqtChange(d(10, 31), 2, HourMinute(12, 30))))
        assertEquals(values, WaqtCodec.decode(WaqtCodec.encode(values)))
        for (text in listOf(null, "", "x", "13:00,1:15,4:15,5:40,7:00,1:30;", "5:10,1:15,4:15,5:40,7:00,1:30;2026-10-31|6|4:30")) {
            assertNull("\"$text\"", WaqtCodec.decode(text))
        }
    }

    @Test
    fun summaryLines() {
        val values = WaqtValues(times, listOf(WaqtChange(d(10, 31), 2, HourMinute(4, 30))))
        assertEquals(
            listOf("Today: Sunrise 5:58 am, Sunset 5:27 pm (calculated).", "The program covers 19 Oct 2026 to 31 Oct 2026."),
            WaqtInput.summary(values, LocalDateTime.of(2026, 10, 21, 21, 44, 2)),
        )
    }

    @Test
    fun logHeader() {
        assertEquals(
            listOf("Display: m5, 384 × 128, progam.hds 681991 bytes", "Profile: Baitul Aman", "Waqt: 29 copies, 1 changes"),
            waqtLogHeader("Baitul Aman", WaqtValues(times, listOf(WaqtChange(d(10, 31), 2, HourMinute(4, 30)))), 29, 681991),
        )
    }

    @Test
    fun summaryDescribesTheProgramThatIsSent() {
        // A change dated yesterday has already applied: the summary must not end 3 days after it.
        val now = LocalDateTime.of(2026, 10, 21, 21, 44, 2)
        val values = WaqtValues(times, listOf(WaqtChange(d(10, 20), 2, HourMinute(4, 30))))
        assertEquals(WaqtInput.summary(WaqtInput.merged(values, now), now), WaqtInput.summary(values, now))
    }

    @Test
    fun twoPrayersCannotHaveTheSameTime() {
        assertNull(WaqtInput.timeConflict(WaqtValues(times, emptyList())))
        // Fajr 5:10 am and Magrib 5:10 pm are different times.
        assertNull(WaqtInput.timeConflict(WaqtValues(times.toMutableList().also { it[3] = HourMinute(5, 10) }, emptyList())))
        val today = WaqtValues(times.toMutableList().also { it[4] = HourMinute(4, 15) }, emptyList())
        assertEquals("Asr and Isha have the same time (today).", WaqtInput.timeConflict(today))
        assertFalse(WaqtInput.isValid(today))
        val later = WaqtValues(times, listOf(WaqtChange(d(10, 31), 4, HourMinute(4, 15))))
        assertEquals("Asr and Isha have the same time (from 31 Oct 2026).", WaqtInput.timeConflict(later))
        assertFalse(WaqtInput.isValid(later))
        // A conflict that a later change of the same date removes is not a conflict.
        val fixed = WaqtValues(times, listOf(WaqtChange(d(10, 31), 4, HourMinute(4, 15)), WaqtChange(d(10, 31), 2, HourMinute(4, 0))))
        assertNull(WaqtInput.timeConflict(fixed))
    }

    @Test
    fun zuhrAndJumuahCanHaveTheSameTime() {
        // On Friday, Jumu'ah replaces Zuhr (user, 2026-10-22).
        val today = WaqtValues(times.toMutableList().also { it[5] = HourMinute(1, 15) }, emptyList())
        assertNull(WaqtInput.timeConflict(today))
        assertTrue(WaqtInput.isValid(today))
        val later = WaqtValues(times, listOf(WaqtChange(d(10, 31), 5, HourMinute(1, 15))))
        assertNull(WaqtInput.timeConflict(later))
        assertTrue(WaqtInput.isValid(later))
    }

    @Test
    fun noChangesShowNoPeriodLine() {
        assertEquals(
            listOf("Today: Sunrise 5:58 am, Sunset 5:27 pm (calculated)."),
            WaqtInput.summary(WaqtValues(times, emptyList()), LocalDateTime.of(2026, 10, 21, 21, 44, 2)),
        )
    }
}
