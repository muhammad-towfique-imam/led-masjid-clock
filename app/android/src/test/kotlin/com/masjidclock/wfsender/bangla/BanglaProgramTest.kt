package com.masjidclock.wfsender.bangla

import com.masjidclock.wfsender.display.Area
import com.masjidclock.wfsender.display.AreaType
import com.masjidclock.wfsender.display.Programs
import com.masjidclock.wfsender.hijri.HijriDataException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The HD2020 exports of 2026-10-20 (m5 Bangla) are the expected values. */
class BanglaProgramTest {

    private fun export(year: Int): ByteArray =
        javaClass.getResourceAsStream("/bangla/m5/bn$year.hds")!!.use { it.readBytes() }

    private fun shapes(year: Int) = year.toString().map { BanglaM5.digits.getValue(it) }

    private fun program13Texts(file: ByteArray) = Programs.of(file)[12].areas.filter { it.type == AreaType.TEXT }

    @Test
    fun rebuildsTheExportsLikeTheDesktopApp() {
        for (year in listOf(1429, 1434, 1436, 1447, 1458)) {
            val made = BanglaProgram.make(BanglaM5.base(), BanglaM5.digits, year, correctProgram13 = false)
            assertArrayEquals("year $year", export(year), made)
        }
    }

    @Test
    fun keepsTheBaseForTheSameYear() {
        assertArrayEquals(BanglaM5.base(), BanglaProgram.make(BanglaM5.base(), BanglaM5.digits, 1432, correctProgram13 = false))
    }

    @Test
    fun correctsProgram13() {
        for (year in listOf(1432, 1458)) {
            val asDesktop = BanglaProgram.make(BanglaM5.base(), BanglaM5.digits, year, correctProgram13 = false)
            val corrected = BanglaProgram.make(BanglaM5.base(), BanglaM5.digits, year)
            val bitmaps = program13Texts(corrected).map { (it.start + Area.BITMAP) until it.end }
            for (i in corrected.indices) {
                if (bitmaps.none { i in it }) assertTrue("year $year byte $i", corrected[i] == asDesktop[i])
            }
            for (area in program13Texts(corrected)) assertNotNull("year $year", area.findShapes(shapes(year + 1)))
        }
    }

    @Test
    fun year1499() {
        val made = BanglaProgram.make(BanglaM5.base(), BanglaM5.digits, 1499)
        for (area in program13Texts(made)) assertNotNull(area.findShapes(shapes(1500)))
        for (k in 0 until 12) {
            for (area in Programs.of(made)[k].areas.filter { it.type == AreaType.TEXT }) {
                assertNotNull("program ${k + 1}", area.findShapes(shapes(1499)))
            }
        }
    }

    @Test
    fun damagedBaseThrows() {
        val base = BanglaM5.base()
        val area = Programs.of(base)[0].areas.first { it.type == AreaType.TEXT }
        for (i in area.start + Area.BITMAP until area.end) base[i] = 0
        assertThrows(HijriDataException::class.java) { BanglaProgram.make(base, BanglaM5.digits, 1433) }
    }
}
