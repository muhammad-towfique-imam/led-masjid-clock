package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.CardConnector
import com.masjidclock.wfsender.protocol.CardSender
import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.SendListener
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card
import java.io.File
import java.net.ConnectException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CardSendTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val refused = CardConnector { _, _, _ -> throw ConnectException("refused") }
    private val sender = CardSender(refused, maxAttempts = 1)

    @Test
    fun missingFolderGivesInvalidExportInsteadOfACrash() {
        val card = Card("english", File(tmp.root, "gone"), 128, 64, 100)
        val result = sendCard(card, sender, object : SendListener {})
        assertTrue("got $result", result is SendResult.Failed)
        assertEquals(FailureReason.INVALID_EXPORT, (result as SendResult.Failed).reason)
    }

    @Test
    fun damagedFileGivesInvalidExportInsteadOfACrash() {
        val dir = tmp.newFolder("english")
        File(dir, "screen.hds").writeBytes(ByteArray(10))
        File(dir, "progam.hds").writeBytes(ByteArray(10))
        val result = sendCard(Card("english", dir, 128, 64, 10), sender, object : SendListener {})
        assertEquals(FailureReason.INVALID_EXPORT, (result as SendResult.Failed).reason)
    }

    @Test
    fun validExportIsSent() {
        val dir = tmp.newFolder("english")
        File(dir, "screen.hds").writeBytes(ByteArray(66).also { it[0] = 'H'.code.toByte(); it[1] = 'S'.code.toByte() })
        File(dir, "progam.hds").writeBytes(ByteArray(20).also { it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte() })
        val result = sendCard(Card("english", dir, 0, 0, 20), sender, object : SendListener {})
        // The export is valid, so the sender runs; the refused connection gives NETWORK.
        assertEquals(FailureReason.NETWORK, (result as SendResult.Failed).reason)
    }

    @Test
    fun safeSendWithAMissingFolderGivesInvalidExport() {
        val card = Card("english", File(tmp.root, "gone"), 128, 64, 100)
        val safe = com.masjidclock.wfsender.protocol.SafeSender(
            com.masjidclock.wfsender.protocol.UdpConnector { throw java.io.IOException("not used") },
        )
        val result = sendCardSafe(card, safe, object : SendListener {})
        assertEquals(FailureReason.INVALID_EXPORT, (result as SendResult.Failed).reason)
    }
}
