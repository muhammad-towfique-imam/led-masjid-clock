package com.masjidclock.wfsender.display

import com.masjidclock.wfsender.hijri.HijriProgram

/** The pixels of 1 year digit: 3 color planes × 16 rows × 7 columns. */
class Glyph(private val pixels: BooleanArray) {
    init {
        require(pixels.size == SIZE) { "a glyph has $SIZE pixels" }
    }

    operator fun get(plane: Int, row: Int, column: Int): Boolean = pixels[(plane * ROWS + row) * WIDTH + column]

    /** The shape of the digit: a pixel is on if it is on in any color plane. */
    fun shape(): Shape {
        val out = BooleanArray(ROWS * WIDTH)
        for (r in 0 until ROWS) for (c in 0 until WIDTH) out[r * WIDTH + c] = (0 until PLANES).any { this[it, r, c] }
        return Shape(out)
    }

    override fun equals(other: Any?): Boolean = other is Glyph && pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = pixels.contentHashCode()

    companion object {
        const val PLANES = 3
        const val ROWS = 16
        const val WIDTH = 7
        const val SIZE = PLANES * ROWS * WIDTH
    }
}

/** The pixels of 1 digit without color: 16 rows × 7 columns. */
class Shape(private val pixels: BooleanArray) {
    init {
        require(pixels.size == Glyph.ROWS * Glyph.WIDTH) { "a shape has ${Glyph.ROWS * Glyph.WIDTH} pixels" }
    }

    operator fun get(row: Int, column: Int): Boolean = pixels[row * Glyph.WIDTH + column]

    override fun equals(other: Any?): Boolean = other is Shape && pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = pixels.contentHashCode()
}

/** 1 HA area of a progam.hds (Hijri spec section 5.2; display-tabs spec section 9). */
internal class Area(private val program: ByteArray, val start: Int, val end: Int) {
    val x = u16(start + 4)
    val y = u16(start + 6)
    val width = u16(start + 8)
    val height = u16(start + 10)

    /** The area type at bytes 34-35, see [AreaType]. */
    val type = u16(start + 34)

    // Hijri only: the m5 Hijri areas are told apart by size and position.
    val isCounter = end - start == COUNTER_SIZE
    val isText = !isCounter && y in 0..1 && height in setOf(16, 19)

    /** The dashed line on the lower half (the s1/s2/s3-divider areas of the template). */
    val isDivider = !isCounter && !isText
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

    /** The column and the row offset of [year] (4 shapes, any color plane), or null. */
    fun findShapes(year: List<Shape>): Pair<Int, Int>? {
        for (rowOffset in -3..(height - Glyph.ROWS + 3)) {
            for (column in 0..(stride * 8 - year.size * HijriProgram.STEP)) {
                if (year.indices.all { k -> cell(column + k * HijriProgram.STEP, rowOffset).shape() == year[k] }) {
                    return column to rowOffset
                }
            }
        }
        return null
    }

    /** The color planes with any lit pixel in [count] cells from [column]. */
    fun planesAt(column: Int, rowOffset: Int, count: Int): Set<Int> {
        val cells = (0 until count).map { cell(column + it * HijriProgram.STEP, rowOffset) }
        return (0 until Glyph.PLANES).filter { p ->
            cells.any { g -> (0 until Glyph.ROWS).any { r -> (0 until Glyph.WIDTH).any { c -> g[p, r, c] } } }
        }.toSet()
    }

    /** Writes [shape] into [planes]; the other planes of the cell are cleared. */
    fun writeShape(column: Int, rowOffset: Int, shape: Shape, planes: Set<Int>) {
        val pixels = BooleanArray(Glyph.SIZE)
        for (p in planes) for (r in 0 until Glyph.ROWS) for (c in 0 until Glyph.WIDTH) {
            pixels[(p * Glyph.ROWS + r) * Glyph.WIDTH + c] = shape[r, c]
        }
        write(column, rowOffset, Glyph(pixels))
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

/** The m5 Hijri file has 1 program: its areas. */
internal object Areas {
    fun of(program: ByteArray): List<Area> = Programs.of(program)[0].areas
}

/** 1 HP program block of a progam.hds, from [start] to [end]. */
internal class Program(val file: ByteArray, val start: Int, val end: Int) {
    val areas: List<Area> by lazy {
        val count = file[start + 4].toInt() and 0xFF
        val starts = (0 until count).map { start + u32(file, start + 52 + 4 * it) }
        starts.mapIndexed { i, s -> Area(file, s, if (i + 1 < count) starts[i + 1] else end) }
    }
}

/** The HC header: 27 bytes with the program count at byte 4, then 1 absolute 4-byte offset for each program. */
internal object Programs {
    fun of(file: ByteArray): List<Program> {
        val count = file[4].toInt() and 0xFF
        val starts = (0 until count).map { u32(file, 27 + 4 * it) }
        return starts.mapIndexed { i, s -> Program(file, s, if (i + 1 < count) starts[i + 1] else file.size) }
    }
}

internal fun u32(b: ByteArray, i: Int): Int =
    ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or
        ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

/** The area types (area bytes 34-35). */
object AreaType {
    const val TEXT = 0x1630
    const val COUNTER = 0x3d34
    const val TIME = 0x6032
}
