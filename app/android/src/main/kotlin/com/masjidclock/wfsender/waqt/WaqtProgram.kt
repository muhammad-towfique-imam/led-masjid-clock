package com.masjidclock.wfsender.waqt

import com.masjidclock.wfsender.display.Area
import com.masjidclock.wfsender.display.Glyph
import com.masjidclock.wfsender.display.Program
import com.masjidclock.wfsender.display.Programs
import com.masjidclock.wfsender.hijri.HijriDataException
import java.time.LocalDate
import java.time.ZoneOffset

/** The 3 time areas of the m5 waqt program (x = 81, 209, 337; spec section 8). */
internal val Area.isWaqtTime: Boolean get() = x in TIME_AREAS

private val TIME_AREAS = setOf(81, 209, 337)

/** The digit cells of " h:mm": text index → column (index 2, the colon, never changes). */
private val CELLS = mapOf(0 to 20, 1 to 27, 3 to 38, 4 to 45)

/** The rows of the 8 times are 16 pixels high. */
private const val ROW = 16

/** The data of the m5 waqt display (384 × 128): the HD2020 export "w-digits" and its digit cells. */
object WaqtM5 {
    /** The 8 texts of the first program of the base (the kit's case w-digits). */
    private val BASE_TEXTS = listOf(" 4:26", "12:37", " 3:48", " 5:59", " 7:01", "10:15", " 0:00", " 0:00")

    val screen: ByteArray by lazy { read("screen.hds") }

    private val baseFile: ByteArray by lazy { read("base.hds") }

    fun base(): ByteArray = baseFile.copyOf()

    val digits: Map<Char, Glyph> by lazy { digitsOf(baseFile) }

    /** Cuts the digit cells from the first program of [base]; 2 different cuts of 1 character throw. */
    internal fun digitsOf(base: ByteArray): Map<Char, Glyph> {
        val out = mutableMapOf<Char, Glyph>()
        val areas = Programs.of(base)[0].areas.filter { it.isWaqtTime }
        if (areas.size != 3) throw HijriDataException("the waqt base has ${areas.size} time areas")
        for (area in areas) for ((row, text) in BASE_TEXTS.withIndex()) for ((index, column) in CELLS) {
            val glyph = area.cell(column, ROW * row)
            val known = out.getOrPut(text[index]) { glyph }
            if (known != glyph) throw HijriDataException("2 different cells for '${text[index]}'")
        }
        // A damaged base gives empty cells: then the digits would all be the same.
        if (out.values.toSet().size != out.size) throw HijriDataException("the waqt digit cells are not distinct")
        return out
    }

    private fun read(name: String): ByteArray =
        WaqtM5::class.java.getResourceAsStream("/waqt/m5/$name")?.use { it.readBytes() }
            ?: throw HijriDataException("$name is missing")
}

/** Makes the m5 waqt progam.hds: the first HP block of the base, 1 time for each copy (spec section 8). */
object WaqtProgram {
    fun make(copies: List<WaqtCopy>): ByteArray {
        if (copies.isEmpty() || copies.size > 255) throw HijriDataException("${copies.size} copies")
        val base = WaqtM5.base()
        val first = Programs.of(base)[0]
        val block = base.copyOfRange(first.start, first.end)
        val blocks = copies.map { copy(block, it) }
        val header = base.copyOfRange(0, 27)
        header[4] = copies.size.toByte()
        val out = java.io.ByteArrayOutputStream()
        out.write(header)
        var offset = 27 + 4 * copies.size
        for (b in blocks) {
            out.write(byteArrayOf((offset shr 24).toByte(), (offset shr 16).toByte(), (offset shr 8).toByte(), offset.toByte()))
            offset += b.size
        }
        blocks.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun copy(block: ByteArray, copy: WaqtCopy): ByteArray {
        val b = block.copyOf()
        val window = copy.timeStart != null
        b[5] = if (window) 1 else 0
        put(b, 6, 3, (copy.timeStart ?: 0).toLong())
        put(b, 9, 3, (copy.timeEnd ?: 86_399).toLong())
        b[17] = 1
        put(b, 18, 4, wall(copy.dateStart))
        put(b, 22, 4, wall(copy.dateEnd) + 86_399)
        val digits = WaqtM5.digits
        for (area in Program(b, 0, b.size).areas.filter { it.isWaqtTime }) {
            for ((row, text) in copy.times.withIndex()) for ((index, column) in CELLS) {
                val glyph = digits[text[index]] ?: throw HijriDataException("no cell for '${text[index]}'")
                area.write(column, ROW * row, glyph)
            }
        }
        return b
    }

    private fun wall(date: LocalDate): Long = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

    private fun put(b: ByteArray, at: Int, length: Int, value: Long) {
        for (i in 0 until length) b[at + i] = (value shr (8 * (length - 1 - i))).toByte()
    }
}
