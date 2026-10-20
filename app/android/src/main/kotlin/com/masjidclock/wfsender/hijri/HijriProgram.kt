package com.masjidclock.wfsender.hijri

import com.masjidclock.wfsender.display.Area
import com.masjidclock.wfsender.display.Areas
import com.masjidclock.wfsender.display.Glyph
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/** The stored program data cannot be used. This occurs only with a wrong build. */
class HijriDataException(message: String) : Exception(message)

/**
 * Makes an m5 Hijri progam.hds from the HD2020 base of a month (spec section 5): writes the year digits into the
 * 3 month-and-year text areas and the counter start into the 3 day counter areas. If [showDivider] is false, the
 * pixels of the 3 divider areas (the dashed line on the lower half) are cleared. The rest stays byte for byte.
 */
object HijriProgram {
    /** The step from 1 year digit to the next, in pixels. */
    const val STEP = 7

    fun make(
        base: ByteArray,
        digits: Map<Char, Glyph>,
        year: Int,
        counterStart: LocalDateTime,
        baseYear: Int = 1448,
        showDivider: Boolean = true,
    ): ByteArray {
        val program = base.copyOf()
        val newYear = year.toString().map { digits[it] ?: throw HijriDataException("no glyph for the digit $it") }
        val oldYear = baseYear.toString().map { digits[it] ?: throw HijriDataException("no glyph for the digit $it") }
        for (area in Areas.of(program).filter { it.isText }) {
            val (column, rowOffset) = area.find(oldYear)
                ?: throw HijriDataException("the year $baseYear is not in the text area at ${area.start}")
            newYear.forEachIndexed { k, glyph -> area.write(column + k * STEP, rowOffset, glyph) }
        }
        val seconds = counterStart.toEpochSecond(ZoneOffset.UTC)
        for (area in Areas.of(program).filter { it.isCounter }) {
            for (i in 0 until 4) program[area.start + 37 + i] = (seconds shr (24 - 8 * i)).toByte()
        }
        if (!showDivider) {
            for (area in Areas.of(program).filter { it.isDivider }) program.fill(0, area.start + Area.BITMAP, area.end)
        }
        return program
    }
}

/** The data of the m5 display (384 × 32): the 12 month bases from HD2020 and the year digits. */
object HijriM5 {
    /** The year columns of the first text area of m01 (spec section 5.1). */
    private val YEAR_COLUMNS = listOf(91, 98, 105, 112)

    val screen: ByteArray by lazy { read("screen.hds") }

    /**
     * A copy of the HD2020 base of [month] (1 = Muharram … 12 = Zul-hijjah). [lowerHalf] TEMPERATURE gives the base with
     * the m4 temperature areas in the lower half (the same upper half, byte for byte); LINE and NOTHING the base with
     * the divider line.
     */
    fun base(month: Int, lowerHalf: LowerHalf = LowerHalf.LINE): ByteArray {
        require(month in 1..12) { "month $month" }
        return (if (lowerHalf == LowerHalf.TEMPERATURE) temperatureBases else bases)[month - 1].copyOf()
    }

    private val bases: List<ByteArray> by lazy { (1..12).map { read(baseName(it)) } }

    private val temperatureBases: List<ByteArray> by lazy {
        (1..12).map { read(String.format(Locale.ROOT, "t%02d.hds", it)) }
    }

    /** The resource name of the base of [month]. Locale.ROOT: a Bengali or Arabic phone must not give "m০১.hds". */
    internal fun baseName(month: Int): String = String.format(Locale.ROOT, "m%02d.hds", month)

    val digits: Map<Char, Glyph> by lazy {
        val library = mutableMapOf<Char, Glyph>()
        for ((file, year) in listOf("m01.hds" to "1448", "y1449.hds" to "1449", "y1450.hds" to "1450",
            "y1462.hds" to "1462", "y1473.hds" to "1473")) {
            val area = Areas.of(read(file)).firstOrNull { it.isText }
                ?: throw HijriDataException("no text area in $file")
            year.forEachIndexed { k, digit ->
                val glyph = area.cell(YEAR_COLUMNS[k], 0)
                val known = library.getOrPut(digit) { glyph }
                if (known != glyph) throw HijriDataException("2 different glyphs for the digit $digit")
            }
        }
        library
    }

    private fun read(name: String): ByteArray =
        HijriM5::class.java.getResourceAsStream("/hijri/m5/$name")?.use { it.readBytes() }
            ?: throw HijriDataException("$name is missing")
}
