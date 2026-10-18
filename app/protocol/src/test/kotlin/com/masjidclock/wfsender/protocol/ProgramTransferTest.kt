package com.masjidclock.wfsender.protocol

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramTransferTest {

    private val export = HdsExport.fromDirectory(File(javaClass.getResource("/export-128x64")!!.toURI()))
    private val captured = javaClass.getResourceAsStream("/captured-transfer.bin")!!.readBytes()

    private fun ByteArray.u16(offset: Int) = ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    @Test
    fun matchesTheHd2020CaptureByteForByte() {
        val frames = ProgramTransfer.frames(export, transferId = 0x030c)
        assertEquals(16, frames.size)
        val all = frames.fold(ByteArray(0)) { acc, f -> acc + f }
        assertArrayEquals(captured, all)
    }

    @Test
    fun framesAreInTheSpecOrder() {
        val frames = ProgramTransfer.frames(export, transferId = 0x1234)
        val commands = frames.map { it[6].toInt() and 0xFF }
        assertEquals(listOf(0x13, 0x02) + List(13) { 0x03 } + listOf(0x04), commands)
        val sequences = frames.map { it[26].toInt() and 0xFF }
        assertEquals((1..16).toList(), sequences)
        // Transfer ID is little-endian in START, DATA and END.
        assertEquals(0x34, frames[1][28].toInt() and 0xFF)
        assertEquals(0x12, frames[1][29].toInt() and 0xFF)
    }

    @Test
    fun doesNotChangeTheExport() {
        val before = export.program.copyOf()
        ProgramTransfer.frames(export, transferId = 1)
        assertArrayEquals(before, export.program)
    }

    @Test
    fun largeProgramWrapsSequenceAndKeepsChunkIndex() {
        val program = ByteArray(300 * 1000 + 5).also { it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte() }
        val big = HdsExport(export.screen, program)
        val frames = ProgramTransfer.frames(big, transferId = 7)
        // screen + start + 301 data + end
        assertEquals(304, frames.size)
        // Chunk count in the START payload (bytes 30-31) and in the HC header (data frame 0, payload bytes 9-10).
        assertEquals(301, frames[1].u16(30))
        assertEquals(301, frames[2].u16(32 + 9))
        // Data frame for chunk 254 is frames[256]: sequence (3 + 254) wraps to 1, chunk index stays 254.
        val chunk254 = frames[256]
        assertEquals(1, chunk254[26].toInt() and 0xFF)
        assertEquals(254, chunk254.u16(30))
        // The last chunk has 5 bytes: 27 header + 5 payload prefix + 5 data + 3 trailer.
        assertEquals(40, frames[302].size)
    }
}
