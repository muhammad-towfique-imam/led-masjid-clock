package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.Ack
import com.masjidclock.wfsender.protocol.SendListener
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The log of 1 send, shared as plain text (spec section 6.3). */
class SendLog(
    siteName: String,
    card: Card,
    appVersion: String,
    androidVersion: String,
    now: () -> Date = { Date() },
) : SendListener {

    private val lines = mutableListOf<String>()

    init {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now())
        add("HD Sender $appVersion, Android $androidVersion")
        add("Time: $time")
        add("Site: $siteName")
        add("Card: ${card.label}, ${card.width} × ${card.height}, progam.hds ${card.programSize} bytes")
    }

    override fun onAttemptStarted(attempt: Int, transferId: Int) =
        add("Attempt $attempt, transfer ID 0x%04x".format(transferId))

    override fun onFrameSent(index: Int, total: Int, frame: ByteArray) =
        add(
            "  sent $index/$total: command 0x%02x, sequence %d, %d bytes"
                .format(frame[6].toInt() and 0xFF, frame[26].toInt() and 0xFF, frame.size)
        )

    override fun onAck(ack: Ack) = add("  ACK: command 0x%02x, status 0x%02x".format(ack.command, ack.status))

    override fun onAttemptFailed(attempt: Int, error: String) = add("  attempt $attempt failed: $error")

    /** A free text line, for example the form and the variant of an attempt. */
    fun note(text: String) = add(text)

    fun finish(result: SendResult) = add(
        when (result) {
            SendResult.Success -> "Result: SUCCESS"
            is SendResult.Failed -> "Result: FAILED (${result.reason}) ${result.detail}"
        }
    )

    @Synchronized
    fun text(): String = lines.joinToString("\n")

    @Synchronized
    private fun add(line: String) {
        lines += line
    }
}
