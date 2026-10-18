package com.masjidclock.wfsender.protocol

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Frames of the UDP protocol of HD2020 (firmware V4.45), from the captures of 2026-10-18.
 * Both header forms carry the 15-byte device ID (bytes 8-22) and a 32-bit counter (bytes 23-26).
 */
object HdFrames {
    /** The fixed block between the command data and the time text of an extended frame. */
    private val TAIL_BLOCK = byteArrayOf(
        0x00, 0x11, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x11, 0x00, 0x41,
        0x00, 0x11, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
    )

    /** The HD2020 installation ID from the captures. The cards accept it. */
    private const val HD2020_ID = "cc573c27-8714-4be6-b987-bc66cd856ca9"

    /** The "who are you" request (0x1b) with a zero device ID. */
    private val DISCOVER_DATA = byteArrayOf(0x00, 0x0a, 0xFF.toByte(), 0x2f, 0xFF.toByte(), 0x89.toByte(), 0xFF.toByte(), 0x06, 0x00, 0x0e)

    /** Short form: a 27-byte header, then [payload]. */
    fun frame27(command: Int, deviceId: ByteArray, counter: Long, payload: ByteArray): ByteArray {
        require(deviceId.size == 15) { "device ID must be 15 bytes" }
        val total = 27 + payload.size + 3
        val head = ByteArray(27)
        head[0] = 'H'.code.toByte()
        head[1] = 'T'.code.toByte()
        head[3] = 0x1b
        head[4] = (total shr 8).toByte()
        head[5] = total.toByte()
        head[6] = command.toByte()
        deviceId.copyInto(head, 8)
        putU32(head, 23, counter)
        return finish(head + payload)
    }

    /** Extended form: a 31-byte header, [payload], the fixed block, and the time text of [wallSeconds]. */
    fun frame31(command: Int, deviceId: ByteArray, number: Long, payload: ByteArray, wallSeconds: Long): ByteArray {
        require(deviceId.size == 15) { "device ID must be 15 bytes" }
        val tail = TAIL_BLOCK + "${wallText(wallSeconds)},,hd2020,$HD2020_ID".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val total = 31 + payload.size + tail.size + 3
        val head = ByteArray(31)
        head[0] = 'H'.code.toByte()
        head[1] = 'T'.code.toByte()
        head[3] = 0x1f
        head[4] = (total shr 8).toByte()
        head[5] = total.toByte()
        head[6] = command.toByte()
        deviceId.copyInto(head, 8)
        putU32(head, 23, number)
        val first = 31 + payload.size
        head[27] = (first shr 8).toByte()
        head[28] = first.toByte()
        head[29] = (tail.size shr 8).toByte()
        head[30] = tail.size.toByte()
        return finish(head + payload + tail)
    }

    fun discoverRequest(counter: Long): ByteArray = frame27(0x1b, ByteArray(15), counter, DISCOVER_DATA)

    /** The wall-clock time (seconds since 1970, without time zone) as "YYYY/MM/DD HH:MM:SS". */
    fun wallText(wallSeconds: Long): String =
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(wallSeconds * 1000))

    internal fun putU32(target: ByteArray, at: Int, value: Long) {
        target[at] = (value shr 24).toByte()
        target[at + 1] = (value shr 16).toByte()
        target[at + 2] = (value shr 8).toByte()
        target[at + 3] = value.toByte()
    }

    private fun finish(body: ByteArray): ByteArray {
        var sum = 0
        for (b in body) sum += b.toInt() and 0xFF
        return body + byteArrayOf((sum shr 8).toByte(), sum.toByte(), 0xAA.toByte())
    }
}

/** The set-time command (0x14) of HD2020. */
object TimePacket {
    const val COMMAND = 0x14

    fun build(deviceId: ByteArray, number: Long, wallSeconds: Long): ByteArray {
        // 22 bytes: 0x16, the time (big-endian seconds), 0x01, 14 zero bytes, 0x54 0x60.
        val data = ByteArray(22)
        data[0] = 0x16
        HdFrames.putU32(data, 1, wallSeconds)
        data[5] = 0x01
        data[20] = 0x54
        data[21] = 0x60
        return HdFrames.frame31(COMMAND, deviceId, number, data, wallSeconds)
    }

    fun text(wallSeconds: Long): String = HdFrames.wallText(wallSeconds)
}

/** The device ID and the name from the answer to the discover request. */
class CardIdentity(val deviceId: ByteArray, val name: String) {
    companion object {
        fun parse(answer: ByteArray): CardIdentity {
            if (answer.size < 132 || !Ack.isHr(answer) || (answer[20].toInt() and 0xFF) != 0x1b) {
                throw UnexpectedReplyException("not a discover answer: ${answer.size} bytes")
            }
            val name = String(answer, 92, 40, Charsets.UTF_16LE).substringBefore('\u0000')
            return CardIdentity(answer.copyOfRange(3, 18), name)
        }
    }
}
