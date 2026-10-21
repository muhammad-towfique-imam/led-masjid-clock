package com.masjidclock.wfsender.waqt

import com.masjidclock.wfsender.display.Area
import com.masjidclock.wfsender.display.Programs
import com.masjidclock.wfsender.hijri.HijriDataException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** The HD2020 exports of 2026-10-21 (waqt-kit) are the expected values. */
class WaqtProgramTest {

    private val times = listOf(HourMinute(5, 10), HourMinute(1, 15), HourMinute(4, 15), HourMinute(5, 40), HourMinute(7, 0), HourMinute(1, 30))

    private fun export(name: String): ByteArray =
        javaClass.getResourceAsStream("/waqt/m5/$name.hds")!!.use { it.readBytes() }

    private fun rebuild(now: LocalDateTime, changes: List<WaqtChange>): ByteArray =
        WaqtProgram.make(WaqtSchedule.copies(times, changes, now, desktopEnd = true))

    @Test
    fun rebuildsTheExports() {
        assertArrayEquals("w0", export("w0"), rebuild(LocalDateTime.of(2026, 10, 21, 21, 43, 11), emptyList()))
        assertArrayEquals(
            "w1", export("w1"),
            rebuild(LocalDateTime.of(2026, 10, 21, 21, 44, 2), listOf(WaqtChange(LocalDate.of(2026, 10, 31), 2, HourMinute(4, 30)))),
        )
        assertArrayEquals(
            "w3", export("w3"),
            rebuild(
                LocalDateTime.of(2026, 10, 21, 21, 45, 6),
                listOf(
                    WaqtChange(LocalDate.of(2026, 10, 26), 0, HourMinute(5, 0)),
                    WaqtChange(LocalDate.of(2026, 10, 29), 2, HourMinute(4, 20)),
                    WaqtChange(LocalDate.of(2026, 11, 2), 0, HourMinute(4, 55)),
                ),
            ),
        )
    }

    @Test
    fun digitsAreComplete() {
        assertEquals(setOf(' ') + ('0'..'9').toSet(), WaqtM5.digits.keys)
    }

    @Test
    fun aDamagedBaseThrows() {
        val base = WaqtM5.base()
        val program = Programs.of(base)[0]
        for (area in program.areas.filter { it.isWaqtTime }) for (i in area.start + Area.BITMAP until area.end) base[i] = 0
        assertThrows(HijriDataException::class.java) { WaqtM5.digitsOf(base) }
    }
}
