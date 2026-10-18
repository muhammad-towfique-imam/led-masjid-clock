package com.masjidclock.wfsender.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HdFramesTest {

    private fun resource(name: String) = javaClass.getResourceAsStream("/time/$name")!!.readBytes()

    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    @Test
    fun discoverRequestMatchesTheCapture() {
        val captured = resource("discover-request.bin")
        val built = HdFrames.frame27(0x1b, ByteArray(15), u32(captured, 23), captured.copyOfRange(27, 37))
        assertArrayEquals(captured, built)
        assertArrayEquals(captured, HdFrames.discoverRequest(u32(captured, 23)))
    }

    @Test
    fun timePacketsMatchTheCapturesByteForByte() {
        for (name in listOf("set-time-a.bin", "set-time-b.bin", "set-time-c.bin")) {
            val captured = resource(name)
            val built = TimePacket.build(
                deviceId = captured.copyOfRange(8, 23),
                number = u32(captured, 23),
                wallSeconds = u32(captured, 32),
            )
            assertArrayEquals(name, captured, built)
        }
    }

    @Test
    fun timeTextIsTheWallClockTime() {
        // 0x6ad53595 is 2026-10-18 21:09:41 on the wall clock (seconds since 1970, without time zone).
        assertEquals("2026/10/18 21:09:41", TimePacket.text(0x6ad53595L))
    }

    @Test
    fun readsTheIdentityFromTheDiscoverAnswer() {
        val identity = CardIdentity.parse(resource("discover-answer.bin"))
        assertEquals("ff0074f98d000d200001f80400001a", identity.deviceId.toHex().replace(" ", ""))
        assertEquals("WF1_3030f98d34c8", identity.name)
    }

    @Test(expected = UnexpectedReplyException::class)
    fun rejectsAnAnswerThatIsNotADiscoverAnswer() {
        CardIdentity.parse(resource("set-time-answer.bin"))
    }
}
