package com.masjidclock.wfsender.protocol

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeTransferTest {

    private fun resource(name: String) = javaClass.getResourceAsStream("/safe/$name")!!.readBytes()

    private val screen = resource("screen.hds")
    private val program = resource("progam.hds")
    private val hd2020Data = resource("hd2020-data.bin")
    private val export = HdsExport(screen, program)

    /** Splits concatenated HT frames (total length at bytes 4-5). */
    private fun split(all: ByteArray): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        var at = 0
        while (at < all.size) {
            val length = ((all[at + 4].toInt() and 0xFF) shl 8) or (all[at + 5].toInt() and 0xFF)
            out += all.copyOfRange(at, at + length)
            at += length
        }
        return out
    }

    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    @Test
    fun matchesTheHd2020TransferByteForByte() {
        val captured = split(resource("hd2020-transfer.bin"))
        assertEquals(14, captured.size)
        val open = captured[0]
        val start = captured[2]
        // The time text of the capture, as wall-clock seconds.
        val text = String(start, 36 + 17, 19, Charsets.US_ASCII)
        val wall = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(text)!!.time / 1000
        val built = SafeTransfer.frames(
            deviceId = open.copyOfRange(8, 23),
            screen = screen,
            data = hd2020Data,
            wallSeconds = wall,
            counter = u32(open, 23),
            transferId = (start[32].toInt() and 0xFF) or ((start[33].toInt() and 0xFF) shl 8),
        )
        assertEquals(captured.size, built.size)
        for (i in captured.indices) assertArrayEquals("frame $i", captured[i], built[i])
    }

    @Test
    fun framesUseTheCommandsOfTheSafeForm() {
        val frames = SafeTransfer.frames(ByteArray(15), screen, program, 0L, counter = 100, transferId = 1)
        // open, screen, start, 7 data chunks (6053 bytes), end
        assertEquals(listOf(0x27, 0x13, 0x02) + List(7) { 0x03 } + listOf(0x04), frames.map { it[6].toInt() and 0xFF })
        assertEquals(103L, u32(frames[3], 23))
        assertEquals(109L, u32(frames[9], 23))
    }
}
