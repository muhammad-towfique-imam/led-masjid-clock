package com.masjidclock.wfsender.hijri

import java.time.LocalDateTime
import java.time.ZoneOffset

/** The stored program data cannot be used. This occurs only with a wrong build. */
class HijriDataException(message: String) : Exception(message)

/** The pixels of 1 year digit: 3 color planes × 16 rows × 7 columns. */
class Glyph(private val pixels: BooleanArray) {
    init {
        require(pixels.size == SIZE) { "a glyph has $SIZE pixels" }
    }

    operator fun get(plane: Int, row: Int, column: Int): Boolean = pixels[(plane * ROWS + row) * WIDTH + column]

    override fun equals(other: Any?): Boolean = other is Glyph && pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = pixels.contentHashCode()

    companion object {
        const val PLANES = 3
        const val ROWS = 16
        const val WIDTH = 7
        const val SIZE = PLANES * ROWS * WIDTH
    }
}

/**
 * Makes an m5 Hijri progam.hds from the HD2020 base of a month (spec section 5): writes the year digits into the
 * 3 month-and-year text areas and the counter start into the 3 day counter areas. The rest stays byte for byte.
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
        return program
    }
}

/** 1 HA area of a progam.hds (spec section 5.2). */
internal class Area(private val program: ByteArray, val start: Int, val end: Int) {
    val y = u16(start + 6)
    val width = u16(start + 8)
    val height = u16(start + 10)
    val isCounter = end - start == COUNTER_SIZE
    val isText = !isCounter && y in 0..1 && height in setOf(16, 19)
    private val stride = (width + 31) / 32 * 4

    /** The pixel at [row], [column] of [plane]. Rows outside the area read as 0. */
    fun pixel(plane: Int, row: Int, column: Int): Boolean {
        if (row !in 0 until height) return false
        val b = program[index(plane, row, column)].toInt()
        return (b shr (7 - column % 8)) and 1 == 1
    }

    /** The cell whose row 0 is area row [rowOffset]. */
    fun cell(column: Int, rowOffset: Int): Glyph {
        val pixels = BooleanArray(Glyph.SIZE)
        for (p in 0 until Glyph.PLANES) for (r in 0 until Glyph.ROWS) for (c in 0 until Glyph.WIDTH) {
            pixels[(p * Glyph.ROWS + r) * Glyph.WIDTH + c] = pixel(p, r + rowOffset, column + c)
        }
        return Glyph(pixels)
    }

    /** The column and the row offset of [year] (4 glyphs), or null. */
    fun find(year: List<Glyph>): Pair<Int, Int>? {
        for (rowOffset in -3..(height - Glyph.ROWS + 3)) {
            for (column in 0..(stride * 8 - year.size * HijriProgram.STEP)) {
                if (year.indices.all { k -> cell(column + k * HijriProgram.STEP, rowOffset) == year[k] }) {
                    return column to rowOffset
                }
            }
        }
        return null
    }

    fun write(column: Int, rowOffset: Int, glyph: Glyph) {
        for (p in 0 until Glyph.PLANES) for (r in 0 until Glyph.ROWS) {
            val row = r + rowOffset
            if (row !in 0 until height) continue
            for (c in 0 until Glyph.WIDTH) {
                val i = index(p, row, column + c)
                val mask = 1 shl (7 - (column + c) % 8)
                val b = program[i].toInt()
                program[i] = (if (glyph[p, r, c]) b or mask else b and mask.inv()).toByte()
            }
        }
    }

    private fun index(plane: Int, row: Int, column: Int) = start + BITMAP + (row * 3 + plane) * stride + column / 8

    private fun u16(i: Int) = ((program[i].toInt() and 0xFF) shl 8) or (program[i + 1].toInt() and 0xFF)

    companion object {
        const val COUNTER_SIZE = 306
        const val BITMAP = 62
    }
}

internal object Areas {
    private const val HP = 31

    fun of(program: ByteArray): List<Area> {
        val count = program[HP + 4].toInt() and 0xFF
        val starts = (0 until count).map { i ->
            val at = HP + 52 + 4 * i
            HP + ((program[at].toInt() and 0xFF) shl 24 or ((program[at + 1].toInt() and 0xFF) shl 16) or
                ((program[at + 2].toInt() and 0xFF) shl 8) or (program[at + 3].toInt() and 0xFF))
        }
        return starts.mapIndexed { i, s -> Area(program, s, if (i + 1 < count) starts[i + 1] else program.size) }
    }
}

/** The data of the m5 display (384 × 32): the 12 month bases from HD2020 and the year digits. */
object HijriM5 {
    /** The year columns of the first text area of m01 (spec section 5.1). */
    private val YEAR_COLUMNS = listOf(91, 98, 105, 112)

    val screen: ByteArray by lazy { read("screen.hds") }

    /** A copy of the HD2020 base of [month] (1 = Muharram … 12 = Zul-hijjah). */
    fun base(month: Int): ByteArray {
        require(month in 1..12) { "month $month" }
        return bases[month - 1].copyOf()
    }

    private val bases: List<ByteArray> by lazy { (1..12).map { read("m%02d.hds".format(it)) } }

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
