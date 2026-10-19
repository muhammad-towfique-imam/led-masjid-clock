package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.ClockFailure
import com.masjidclock.wfsender.protocol.ClockResult
import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card

/** All texts that the person sees (spec sections 5.2, 5.3, 6.2). */
object Messages {
    const val SUCCESS = "Done. The display shows the new program."
    const val WEAK_SIGNAL = "The WiFi signal is too weak. Move nearer to the display and try again."
    const val NETWORK_LOST = "The phone left the WiFi of the display. Connect again and try again."
    const val UNEXPECTED_REPLY = "The display sent an unexpected reply. Contact the person who sent you the file."
    const val WIFI_INSTRUCTIONS = "Connect the phone to the WiFi of the display in Android settings."
    const val NOT_CONNECTED = "Not connected to WiFi"
    const val NO_DISPLAY = "No display found on this WiFi"
    const val DISPLAY_FOUND = "Display found"
    const val INVALID_EXPORT = "The program files on the phone are damaged. Open the ZIP file again."
    const val WAIT_FOR_SEND = "Wait until the send is complete. Then open the file again."
    const val NO_SITE = "Load the HD Sender package (.hdpkg) that you received."

    const val CREATE_NEEDS_ANDROID_11 =
        "Create needs Android 11 or later. This phone has an older Android version. You can still use Send."
    const val ALLOW_ACCESS = "HD Sender needs access to files to read the USB drive and to save packages."
    const val CONNECT_USB = "Connect the USB drive with the HD2020 export to the phone. Use a USB OTG adapter."
    const val NO_EXPORT = "The USB drive has no HD2020 export. Use the U-disk export of HD2020."
    const val TOO_LARGE = "The HD2020 export is larger than 20 MB. The app cannot send a program of this size."
    const val SAVE_FAILED = "Could not save the file. Check that the phone has free space."
    const val NAME_RULE = "Use only English letters, numbers, spaces, - and _ (maximum 50 characters)."

    fun found(volume: String, width: Int, height: Int): String = "Found on: $volume — $width × $height display"

    fun saved(fileName: String): String = "Saved: Downloads/$fileName"

    const val TIME_NO_ANSWER = "The display did not answer. Check the WiFi connection."
    const val TIME_MANUAL = "The phone time is set by hand. Check that it is correct."

    /** The status when the display is found: its card name (for example WF1_3030f98d38da), if the card gave it. */
    fun foundLabel(cardName: String?): String = if (cardName.isNullOrEmpty()) DISPLAY_FOUND else cardName

    fun timeSet(time: String): String = "The display clock is set to $time."

    fun timeRejected(status: Int): String = "The display rejected the time (code 0x%02X).".format(status)

    /** The result of a time sync. [time] is the time that was sent, as "HH:MM:SS". */
    fun forClock(result: ClockResult, time: String): String = when (result) {
        is ClockResult.Success -> timeSet(time)
        is ClockResult.Failed ->
            if (result.reason == ClockFailure.REJECTED) timeRejected(result.status ?: 0) else TIME_NO_ANSWER
    }

    fun forResult(result: SendResult): String = when (result) {
        SendResult.Success -> SUCCESS
        is SendResult.Failed -> when (result.reason) {
            FailureReason.NETWORK -> WEAK_SIGNAL
            FailureReason.NETWORK_LOST -> NETWORK_LOST
            FailureReason.REJECTED ->
                "The display rejected the program (code 0x%02X). Contact the person who sent you the file."
                    .format(result.status ?: 0)
            FailureReason.UNEXPECTED_REPLY, FailureReason.SAFE_REQUIRED -> UNEXPECTED_REPLY
            FailureReason.INVALID_EXPORT -> INVALID_EXPORT
        }
    }

    fun cardRow(card: Card): String = "${card.label} — ${card.width} × ${card.height}"
}
