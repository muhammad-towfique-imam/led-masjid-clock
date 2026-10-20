package com.masjidclock.wfsender.hijri

import com.masjidclock.wfsender.display.Area
import com.masjidclock.wfsender.display.Areas
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

/** The HD2020 exports of 2026-10-19 (m5, start date 10/01/26) are the expected values. */
class HijriProgramTest {

    /** The counter start in all the m5 bases. */
    private val start = LocalDateTime.of(2026, 9, 30, 17, 20)

    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/hijri/m5/$name")!!.use { it.readBytes() }

    @Test
    fun rebuildsTheYearExportsFromM01() {
        for (year in listOf(1449, 1450, 1462, 1473)) {
            val made = HijriProgram.make(HijriM5.base(1), HijriM5.digits, year, start)
            assertArrayEquals("year $year", resource("y$year.hds"), made)
        }
    }

    @Test
    fun rebuildsDPlus1() {
        val made = HijriProgram.make(HijriM5.base(1), HijriM5.digits, 1448, LocalDateTime.of(2026, 10, 1, 17, 20))
        assertArrayEquals(resource("d-plus1.hds"), made)
    }

    @Test
    fun keepsTheBaseForTheSameYearAndStart() {
        assertArrayEquals(HijriM5.base(1), HijriProgram.make(HijriM5.base(1), HijriM5.digits, 1448, start))
    }

    @Test
    fun everyMonthRoundTrips() {
        for (month in 1..12) {
            val base = HijriM5.base(month)
            val y1449 = HijriProgram.make(base, HijriM5.digits, 1449, start)
            assertFalse("month $month: the year did not change", y1449.contentEquals(base))
            val back = HijriProgram.make(y1449, HijriM5.digits, 1448, start, baseYear = 1449)
            assertArrayEquals("month $month", HijriProgram.make(base, HijriM5.digits, 1448, start), back)
        }
    }

    @Test
    fun damagedBaseThrows() {
        val base = HijriM5.base(1)
        // The first area of m01 is a text area (102 × 16, 830 bytes): clear its bitmap.
        val area = 31 + (((base[31 + 52].toInt() and 0xFF) shl 24) or ((base[31 + 53].toInt() and 0xFF) shl 16) or
            ((base[31 + 54].toInt() and 0xFF) shl 8) or (base[31 + 55].toInt() and 0xFF))
        for (i in area + 62 until area + 830) base[i] = 0
        assertThrows(HijriDataException::class.java) { HijriProgram.make(base, HijriM5.digits, 1449, start) }
    }

    @Test
    fun digitLibraryHasAllDigits() {
        assertEquals(('0'..'9').toSet(), HijriM5.digits.keys)
    }

    @Test
    fun baseNamesUseAsciiDigitsInEveryPhoneLanguage() {
        val saved = Locale.getDefault()
        try {
            for (tag in listOf("bn-BD", "ar-EG", "fa-IR")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals(tag, "m01.hds", HijriM5.baseName(1))
                assertEquals(tag, "m12.hds", HijriM5.baseName(12))
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun dividerShownKeepsTheProgram() {
        for (month in 1..12) {
            assertArrayEquals(
                "month $month",
                HijriProgram.make(HijriM5.base(month), HijriM5.digits, 1448, start),
                HijriProgram.make(HijriM5.base(month), HijriM5.digits, 1448, start, showDivider = true),
            )
        }
    }

    @Test
    fun dividerHiddenClearsOnlyTheDividerAreas() {
        for (month in 1..12) {
            val base = HijriM5.base(month)
            val dividers = Areas.of(base).filter { it.isDivider }
            assertEquals("month $month: divider areas", 3, dividers.size)
            val bitmaps = dividers.map { (it.start + Area.BITMAP) until it.end }
            assertTrue("month $month: the base shows the line", bitmaps.any { r -> r.any { base[it].toInt() != 0 } })

            val made = HijriProgram.make(base, HijriM5.digits, 1448, start, showDivider = false)
            for (i in base.indices) {
                val inDivider = bitmaps.any { i in it }
                if (inDivider) assertEquals("month $month byte $i", 0, made[i].toInt())
                else assertEquals("month $month byte $i", base[i], made[i])
            }
        }
    }

    @Test
    fun temperatureBasesKeepTheirLowerHalf() {
        for (month in 1..12) {
            val base = HijriM5.base(month, LowerHalf.TEMPERATURE)
            assertFalse("month $month", base.contentEquals(HijriM5.base(month)))
            assertArrayEquals("month $month", base, HijriProgram.make(base, HijriM5.digits, 1448, start))
            // A new year changes only the bitmaps of the 3 month-and-year areas.
            val made = HijriProgram.make(base, HijriM5.digits, 1449, start)
            val bitmaps = Areas.of(base).filter { it.isText }.map { (it.start + Area.BITMAP) until it.end }
            assertEquals("month $month text areas", 3, bitmaps.size)
            for (i in base.indices) if (bitmaps.none { i in it }) assertEquals("month $month byte $i", base[i], made[i])
        }
    }
}
