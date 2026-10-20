package com.masjidclock.wfsender.bangla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BanglaInputTest {

    private val today = LocalDate.of(2026, 10, 20)
    private val map = mutableMapOf<String, String>()
    private fun settings() = BanglaSettings(read = { map[it] }, write = { k, v -> if (v == null) map.remove(k) else map[k] = v })

    @Test
    fun firstUseGivesTheCurrentYear() {
        assertEquals("1433", settings().load(today))
    }

    @Test
    fun savedYearComesBack() {
        settings().saveYear(1432)
        assertEquals("1432", settings().load(today))
    }

    @Test
    fun damagedSavedYearGivesTheCurrentYear() {
        map["year"] = "14x2"
        assertEquals("1433", settings().load(today))
    }

    @Test
    fun logHeader() {
        assertEquals(
            listOf("Display: m5, 384 × 32, progam.hds 101330 bytes", "Bangla: year 1432, 2025-04-14 to 2026-05-14"),
            banglaLogHeader(1432, 101330),
        )
    }

    @Test
    fun summaryWarnsWhenTheYearDoesNotContainToday() {
        val warn1433 = "Check the year: today is in 1433."
        assertEquals(BanglaSummary(BanglaDate(30, "Choitro", 1432), null), banglaSummary(1432, LocalDate.of(2026, 4, 13)))
        assertEquals(BanglaSummary(BanglaDate(1, "Baishakh", 1433), warn1433), banglaSummary(1432, LocalDate.of(2026, 4, 14)))
        assertEquals(BanglaSummary(BanglaDate(31, "Baishakh", 1433), warn1433), banglaSummary(1432, LocalDate.of(2026, 5, 14)))
        assertEquals(BanglaSummary(null, warn1433), banglaSummary(1432, LocalDate.of(2026, 5, 15)))
        assertEquals(BanglaSummary(null, warn1433), banglaSummary(1440, LocalDate.of(2026, 10, 20)))
        assertNull(banglaSummary(1433, LocalDate.of(2026, 10, 20)).warning)
    }
}
