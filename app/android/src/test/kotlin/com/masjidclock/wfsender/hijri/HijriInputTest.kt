package com.masjidclock.wfsender.hijri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

class HijriInputTest {

    private val today = LocalDate.of(2026, 10, 20)

    private fun settings(map: MutableMap<String, String>) =
        HijriSettings(read = { map[it] }, write = { key, value -> if (value == null) map.remove(key) else map[key] = value })

    @Test
    fun monthNames() {
        assertEquals(12, HijriMonths.NAMES.size)
        assertEquals("Muharram", HijriMonths.NAMES[0])
        assertEquals("Juma Ula", HijriMonths.NAMES[4])
        assertEquals("Zul-hijjah", HijriMonths.NAMES[11])
    }

    @Test
    fun parseYear() {
        assertEquals(1448, HijriInput.parseYear("1448"))
        assertEquals(1400, HijriInput.parseYear("1400"))
        assertEquals(1499, HijriInput.parseYear("1499"))
        for (bad in listOf("1399", "1500", "448", "14480", " 1448", "14a8", "", "১৪৪৮", "١٤٤٨")) {
            assertNull("\"$bad\"", HijriInput.parseYear(bad))
        }
    }

    @Test
    fun dateIsFar() {
        assertFalse(HijriInput.dateIsFar(LocalDate.of(2026, 11, 19), today))
        assertTrue(HijriInput.dateIsFar(LocalDate.of(2026, 11, 20), today))
        assertFalse(HijriInput.dateIsFar(LocalDate.of(2026, 9, 20), today))
        assertTrue(HijriInput.dateIsFar(LocalDate.of(2026, 9, 19), today))
    }

    @Test
    fun pickerConversion() {
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"))
        try {
            val date = LocalDate.of(2026, 10, 12)
            val utcMidnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            assertEquals(date, HijriInput.dateFromPicker(utcMidnight))
            assertEquals(date, HijriInput.dateFromPicker(HijriInput.toPickerMillis(date)))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun settingsFirstUse() {
        assertEquals(HijriValues(today, null, ""), settings(mutableMapOf()).load(today))
    }

    @Test
    fun settingsRoundTrip() {
        val map = mutableMapOf<String, String>()
        settings(map).apply {
            saveDate(LocalDate.of(2026, 10, 12))
            saveMonth(5)
            saveYear(1448)
        }
        assertEquals(HijriValues(LocalDate.of(2026, 10, 12), 5, "1448"), settings(map).load(today))
    }

    @Test
    fun settingsIgnoreDamagedValues() {
        val map = mutableMapOf("monthStart" to "12/10/26", "month" to "13", "year" to "abc")
        assertEquals(HijriValues(today, null, ""), settings(map).load(today))
    }
}
