package com.masjidclock.wfsender.time

import org.junit.Assert.assertEquals
import org.junit.Test

class WallClockTest {

    @Test
    fun wallSecondsAreLocalTimeWithoutTimeZone() {
        // 2026-10-18 15:09:41 UTC is 21:09:41 in Dhaka (UTC+6); HD2020 sent 0x6ad53595 for it.
        val utcMillis = 1_792_336_181_000L
        assertEquals(0x6ad53595L, WallClock.wallSeconds(utcMillis, offsetMillis = 6 * 3_600_000))
    }

    @Test
    fun formatsTheWallClockTime() {
        assertEquals("21:09:41", WallClock.time(0x6ad53595L))
        assertEquals("2026-10-18", WallClock.date(0x6ad53595L))
    }
}
