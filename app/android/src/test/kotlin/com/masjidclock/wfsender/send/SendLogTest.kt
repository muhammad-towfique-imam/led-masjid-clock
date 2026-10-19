package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.Ack
import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card
import java.io.File
import java.util.Date
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SendLogTest {

    @Test
    fun recordsHeaderAttemptsFramesAcksAndResult() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val log = SendLog(
            siteName = "site-a",
            card = Card("english", File("x"), 128, 64, 12586),
            appVersion = "0.1.0",
            androidVersion = "13",
            now = { Date(0) },
        )
        log.onAttemptStarted(1, 0x030c)
        val frame = ByteArray(96).also { it[6] = 0x13; it[26] = 1 }
        log.onFrameSent(1, 16, frame)
        log.onAck(Ack(0x13, 0))
        log.onAttemptFailed(1, "java.net.SocketTimeoutException: Read timed out")
        log.finish(SendResult.Failed(FailureReason.NETWORK, null, "java.net.SocketTimeoutException: Read timed out"))

        val expected = """
            HD Sender 0.1.0, Android 13
            Time: 1970-01-01 00:00:00
            Site: site-a
            Card: english, 128 × 64, progam.hds 12586 bytes
            Attempt 1, transfer ID 0x030c
              sent 1/16: command 0x13, sequence 1, 96 bytes
              ACK: command 0x13, status 0x00
              attempt 1 failed: java.net.SocketTimeoutException: Read timed out
            Result: FAILED (NETWORK) java.net.SocketTimeoutException: Read timed out
        """.trimIndent()
        assertEquals(expected, log.text())
    }

    @Test
    fun recordsSuccess() {
        val log = SendLog("s", Card("a", File("x"), 64, 32, 100), "0.1.0", "13", now = { Date(0) })
        log.finish(SendResult.Success)
        assertEquals("Result: SUCCESS", log.text().lines().last())
    }

    @Test
    fun recordsNotes() {
        val log = SendLog("s", Card("a", File("x"), 64, 32, 100), "0.7.0", "16", now = { Date(0) })
        log.note("Safe form, variant PLAIN")
        assertEquals("Safe form, variant PLAIN", log.text().lines().last())
    }
}
