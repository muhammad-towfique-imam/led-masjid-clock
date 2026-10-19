package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.ClockFailure
import com.masjidclock.wfsender.protocol.ClockResult
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
            "Create needs Android 11 or later. This phone has an older Android version. You can still use Send.",
            Messages.CREATE_NEEDS_ANDROID_11,
        )
        assertEquals(
            "The HD2020 export is larger than 20 MB. The app cannot send a program of this size.",
            Messages.TOO_LARGE,
        )
        assertEquals(
            "Connect the USB drive with the HD2020 export to the phone. Use a USB OTG adapter.",
            Messages.CONNECT_USB,
        )
    }

    @Test
    fun timeMessages() {
        assertEquals("Display found: WF2_e072a1efcde0", Messages.displayFound("WF2_e072a1efcde0"))
        assertEquals("The display clock is set to 22:55:42.", Messages.timeSet("22:55:42"))
        assertEquals("The display did not answer. Check the WiFi connection.", Messages.TIME_NO_ANSWER)
        assertEquals("The display rejected the time (code 0x07).", Messages.timeRejected(7))
        assertEquals("The phone time is set by hand. Check that it is correct.", Messages.TIME_MANUAL)
    }

    @Test
    fun wifiInstructionsDoNotShowThePassword() {
        assertEquals("Connect the phone to the WiFi of the display in Android settings.", Messages.WIFI_INSTRUCTIONS)
    }

    @Test
    fun clockResultMessages() {
        assertEquals("The display clock is set to 21:09:41.", Messages.forClock(ClockResult.Success("WF1"), "21:09:41"))
        assertEquals(
            "The display rejected the time (code 0x07).",
            Messages.forClock(ClockResult.Failed(ClockFailure.REJECTED, 7), "21:09:41"),
        )
        assertEquals(
            "The display did not answer. Check the WiFi connection.",
            Messages.forClock(ClockResult.Failed(ClockFailure.NO_ANSWER, null), "21:09:41"),
        )
        assertEquals(
            "The display did not answer. Check the WiFi connection.",
            Messages.forClock(ClockResult.Failed(ClockFailure.NETWORK, null), "21:09:41"),
        )
    }

    @Test
    fun safeFormMessages() {
        assertEquals("Does the display show the new program?", Messages.SAFE_QUESTION)
        assertEquals(
            "The display did not show the program. Send it with HD2020, and send the log to support.",
            Messages.SAFE_NOT_SHOWN,
        )
        // SAFE_REQUIRED is handled by the app (it sends again with the safe form); it is never the last result.
        assertEquals(
            Messages.UNEXPECTED_REPLY,
            Messages.forResult(SendResult.Failed(FailureReason.SAFE_REQUIRED, null, "")),
        )
    }
}
