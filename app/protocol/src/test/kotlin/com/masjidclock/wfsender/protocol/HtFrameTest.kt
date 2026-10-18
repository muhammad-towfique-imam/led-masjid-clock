package com.masjidclock.wfsender.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtFrameTest {

    private fun hex(s: String): ByteArray =
        s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun buildsHeaderPayloadChecksumAndTerminator() {
        val frame = HtFrame.build(0x12, 1, byteArrayOf(0x02, 0x00))
        val expected = hex(
            "48 54 00 1b 00 20 12" +
                " 00".repeat(19) +
                " 01 02 00 00 ec aa"
        )
        assertArrayEquals(expected, frame)
    }

    @Test
    fun buildsTheCapturedStartFrame() {
        // Start frame of the HD2020 transfer in the spike capture (transfer ID 0x030c, 13 chunks).
        val frame = HtFrame.build(0x02, 2, hex("05 0c 03 00 0d"))
        val expected = hex(
            "48 54 00 1b 00 23 02" +
                " 00".repeat(19) +
                " 02 05 0c 03 00 0d 00 ff aa"
        )
        assertArrayEquals(expected, frame)
    }

    @Test
    fun parsesTheCapturedAck() {
        val ack = Ack.parse(hex("485200ff0071faca000d2000018eec00001a001c130000000000000105c0aa"))
        assertEquals(Ack(command = 0x13, status = 0), ack)
    }

    @Test(expected = UnexpectedReplyException::class)
    fun rejectsShortReply() {
        Ack.parse(hex("4852001b"))
    }

    @Test(expected = UnexpectedReplyException::class)
    fun rejectsReplyWithoutHrPrefix() {
        Ack.parse(ByteArray(HtFrame.ACK_LENGTH) { 0x58 })
    }

    @Test
    fun detectsHrPrefix() {
        assertTrue(Ack.isHr(hex("4852")))
        assertFalse(Ack.isHr(hex("4854")))
        assertFalse(Ack.isHr(hex("48")))
    }
}
