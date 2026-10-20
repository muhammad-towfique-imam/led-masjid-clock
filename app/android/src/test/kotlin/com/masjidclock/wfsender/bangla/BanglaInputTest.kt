package com.masjidclock.wfsender.bangla

import org.junit.Assert.assertEquals
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
}
