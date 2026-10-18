package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class MessagesTest {

    private val card = Card("english", File("x"), 128, 64, 12586)

    @Test
    fun resultMessagesMatchTheSpec() {
        assertEquals("Done. The display shows the new program.", Messages.forResult(SendResult.Success))
        assertEquals(
            "The WiFi signal is too weak. Move nearer to the display and try again.",
            Messages.forResult(SendResult.Failed(FailureReason.NETWORK, null, "")),
        )
        assertEquals(
            "The phone left the WiFi of the display. Connect again and try again.",
            Messages.forResult(SendResult.Failed(FailureReason.NETWORK_LOST, null, "")),
        )
        assertEquals(
            "The display rejected the program (code 0x05). Contact the person who sent you the file.",
            Messages.forResult(SendResult.Failed(FailureReason.REJECTED, 5, "")),
        )
        assertEquals(
            "The display sent an unexpected reply. Contact the person who sent you the file.",
            Messages.forResult(SendResult.Failed(FailureReason.UNEXPECTED_REPLY, null, "")),
        )
        assertEquals(
            "The program files on the phone are damaged. Open the ZIP file again.",
            Messages.forResult(SendResult.Failed(FailureReason.INVALID_EXPORT, null, "")),
        )
    }

    @Test
    fun confirmationNamesTheCardAndTheSize() {
        assertEquals(
            "This program is for a 128 × 64 display named 'english'. Is the phone connected to this display?",
            Messages.confirmation(card),
        )
    }

    @Test
    fun cardRowShowsLabelAndSize() {
        assertEquals("english — 128 × 64", Messages.cardRow(card))
    }

    @Test
    fun createMessages() {
        assertEquals("Found on: SanDisk USB drive — 128 × 64 display", Messages.found("SanDisk USB drive", 128, 64))
        assertEquals("Saved: Downloads/english (1).hdpkg", Messages.saved("english (1).hdpkg"))
        assertEquals("The USB drive has no HD2020 export. Use the U-disk export of HD2020.", Messages.NO_EXPORT)
        assertEquals(
            "The HD2020 export is larger than 20 MB. The app cannot send a program of this size.",
            Messages.TOO_LARGE,
        )
        assertEquals(
            "Connect the USB drive with the HD2020 export to the phone. Use a USB OTG adapter.",
            Messages.CONNECT_USB,
        )
    }
}
