package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card

/** All texts that the person sees (spec sections 5.2, 5.3, 6.2). */
object Messages {
    const val SUCCESS = "Done. The display shows the new program."
    const val WEAK_SIGNAL = "The WiFi signal is too weak. Move nearer to the display and try again."
    const val NETWORK_LOST = "The phone left the WiFi of the display. Connect again and try again."
    const val UNEXPECTED_REPLY = "The display sent an unexpected reply. Contact the person who sent you the file."
    const val WIFI_INSTRUCTIONS =
        "Connect the phone to the WiFi of the display in Android settings. The password is 88888888."
    const val NOT_CONNECTED = "Not connected to WiFi"
    const val NO_DISPLAY = "No display found on this WiFi"
    const val DISPLAY_FOUND = "Display found"
    const val INVALID_EXPORT = "The program files on the phone are damaged. Open the ZIP file again."
    const val WAIT_FOR_SEND = "Wait until the send is complete. Then open the file again."
    const val NO_SITE = "Load the HD Sender package (.hdpkg) that you received."

    const val ALLOW_ACCESS = "HD Sender needs access to files to read the USB drive and to save packages."
    const val CONNECT_USB = "Connect the USB drive with the HD2020 export to the phone. Use a USB OTG adapter."
    const val NO_EXPORT = "The USB drive has no HD2020 export. Use the U-disk export of HD2020."
    const val SAVE_FAILED = "Could not save the file. Check that the phone has free space."
    const val NAME_RULE = "Use only English letters, numbers, spaces, - and _ (maximum 50 characters)."

    fun found(volume: String, width: Int, height: Int): String = "Found on: $volume — $width × $height display"

    fun saved(fileName: String): String = "Saved: Downloads/$fileName"

    fun forResult(result: SendResult): String = when (result) {
        SendResult.Success -> SUCCESS
        is SendResult.Failed -> when (result.reason) {
            FailureReason.NETWORK -> WEAK_SIGNAL
            FailureReason.NETWORK_LOST -> NETWORK_LOST
            FailureReason.REJECTED ->
                "The display rejected the program (code 0x%02X). Contact the person who sent you the file."
                    .format(result.status ?: 0)
            FailureReason.UNEXPECTED_REPLY -> UNEXPECTED_REPLY
            FailureReason.INVALID_EXPORT -> INVALID_EXPORT
        }
    }

    fun confirmation(card: Card): String =
        "This program is for a ${card.width} × ${card.height} display named '${card.label}'. " +
            "Is the phone connected to this display?"

    fun cardRow(card: Card): String = "${card.label} — ${card.width} × ${card.height}"
}
