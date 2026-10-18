package com.masjidclock.wfsender.protocol

/** Builds HT request frames (spec section 3.2). */
object HtFrame {
    const val HEADER_LENGTH = 27
    const val ACK_LENGTH = 31

    fun build(command: Int, sequence: Int, payload: ByteArray): ByteArray {
        val total = HEADER_LENGTH + payload.size + 3
        require(total <= 0xFFFF) { "frame too long: $total bytes" }
        val frame = ByteArray(total)
        frame[0] = 'H'.code.toByte()
        frame[1] = 'T'.code.toByte()
        frame[3] = 0x1B
        frame[4] = (total shr 8).toByte()
        frame[5] = total.toByte()
        frame[6] = command.toByte()
        frame[26] = sequence.toByte()
        payload.copyInto(frame, HEADER_LENGTH)
        var sum = 0
        for (i in 0 until total - 3) sum += frame[i].toInt() and 0xFF
        frame[total - 3] = (sum shr 8).toByte()
        frame[total - 2] = sum.toByte()
        frame[total - 1] = 0xAA.toByte()
        return frame
    }
}

/** The card sent a reply that is not a valid HR frame. A retry does not help. */
class UnexpectedReplyException(message: String) : Exception(message)

/** A 31-byte HR frame from the card: the command it answers and its status (0 = success). */
data class Ack(val command: Int, val status: Int) {
    companion object {
        fun parse(bytes: ByteArray): Ack {
            if (bytes.size < HtFrame.ACK_LENGTH) {
                throw UnexpectedReplyException("reply too short (${bytes.size} bytes): ${bytes.toHex()}")
            }
            if (!isHr(bytes)) {
                throw UnexpectedReplyException("reply does not start with HR: ${bytes.toHex()}")
            }
            return Ack(command = bytes[20].toInt() and 0xFF, status = bytes[21].toInt() and 0xFF)
        }

        fun isHr(bytes: ByteArray): Boolean =
            bytes.size >= 2 && bytes[0] == 'H'.code.toByte() && bytes[1] == 'R'.code.toByte()
    }
}

internal fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }
