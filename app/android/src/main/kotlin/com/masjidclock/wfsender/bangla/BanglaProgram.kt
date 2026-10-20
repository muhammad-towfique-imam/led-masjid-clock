package com.masjidclock.wfsender.bangla

import com.masjidclock.wfsender.display.AreaType
import com.masjidclock.wfsender.display.Programs
import com.masjidclock.wfsender.display.Shape
import com.masjidclock.wfsender.hijri.HijriDataException
import com.masjidclock.wfsender.hijri.HijriM5
import com.masjidclock.wfsender.hijri.HijriProgram
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Makes an m5 Bangla progam.hds from the HD2020 base (year 1432) and a Bangla year (display-tabs spec section 9):
 * the 13 date ranges, the 39 counter starts, and the year in the text areas.
 */
object BanglaProgram {
    /** The template text of program 13; the desktop app never changes it. */
    private const val PROGRAM_13_TEXT = 1431

    fun make(
        base: ByteArray,
        digits: Map<Char, Shape>,
        year: Int,
        baseYear: Int = 1432,
        correctProgram13: Boolean = true,
    ): ByteArray {
        val file = base.copyOf()
        val programs = Programs.of(file)
        if (programs.size != 13) throw HijriDataException("the Bangla base has ${programs.size} programs")
        for ((k, range) in BanglaCalendar.ranges(year).withIndex()) {
            val program = programs[k]
            putU32(file, program.start + 18, wallSeconds(range.start))
            putU32(file, program.start + 22, wallSeconds(range.endInclusive) + 86_399)
            for (area in program.areas) {
                when (area.type) {
                    AreaType.COUNTER -> putU32(file, area.start + 37, wallSeconds(range.start.minusDays(1)))
                    AreaType.TEXT -> {
                        if (k == 12 && !correctProgram13) continue
                        val old = if (k == 12) PROGRAM_13_TEXT else baseYear
                        val new = if (k == 12) year + 1 else year
                        val oldShapes = shapes(digits, old)
                        val (column, rowOffset) = area.findShapes(oldShapes)
                            ?: throw HijriDataException("program ${k + 1}: the year $old is not in the area at ${area.start}")
                        val planes = area.planesAt(column, rowOffset, oldShapes.size)
                        shapes(digits, new).forEachIndexed { i, shape ->
                            area.writeShape(column + i * HijriProgram.STEP, rowOffset, shape, planes)
                        }
                    }
                }
            }
        }
        return file
    }

    private fun shapes(digits: Map<Char, Shape>, year: Int): List<Shape> =
        year.toString().map { digits[it] ?: throw HijriDataException("no shape for the digit $it") }

    /** Local midnight written as UTC seconds (wall-clock seconds), as HD2020 writes it. */
    private fun wallSeconds(date: LocalDate): Long = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

    private fun putU32(b: ByteArray, at: Int, value: Long) {
        for (i in 0 until 4) b[at + i] = (value shr (24 - 8 * i)).toByte()
    }
}

/** The data of the m5 Bangla display (384 × 32): the HD2020 base of 1432 and the year digits. */
object BanglaM5 {
    val screen: ByteArray by lazy { read("screen.hds") }

    private val baseFile: ByteArray by lazy { read("base.hds") }

    /** A copy of the base. */
    fun base(): ByteArray = baseFile.copyOf()

    /** The digit shapes: the same font as the Hijri display, in another color. */
    val digits: Map<Char, Shape> by lazy { HijriM5.digits.mapValues { it.value.shape() } }

    private fun read(name: String): ByteArray =
        BanglaM5::class.java.getResourceAsStream("/bangla/m5/$name")?.use { it.readBytes() }
            ?: throw HijriDataException("$name is missing")
}
