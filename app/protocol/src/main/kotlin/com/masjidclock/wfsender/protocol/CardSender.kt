package com.masjidclock.wfsender.protocol

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.random.Random

/** Opens a TCP connection to the card. The Android app gives a socket that is bound to the card WiFi. */
fun interface CardConnector {
    fun connect(host: String, port: Int, timeoutMs: Int): Socket
}

/** SAFE_REQUIRED: the card refused the normal form at the start packet (firmware V4.45); use SafeSender. */
enum class FailureReason { NETWORK, NETWORK_LOST, REJECTED, UNEXPECTED_REPLY, INVALID_EXPORT, SAFE_REQUIRED }

sealed class SendResult {
    object Success : SendResult()
    data class Failed(val reason: FailureReason, val status: Int?, val detail: String) : SendResult()
}

/** Progress of a send. All methods are called on the thread that calls [CardSender.send]. */
interface SendListener {
    fun onAttemptStarted(attempt: Int, transferId: Int) {}
    fun onFrameSent(index: Int, total: Int, frame: ByteArray) {}
    fun onAck(ack: Ack) {}
    fun onAttemptFailed(attempt: Int, error: String) {}
}

/** Sends a program to a WF card (spec section 6.1). The calls block; use a background thread. */
class CardSender(
    private val connector: CardConnector,
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMs: Int = 5_000,
    private val ackTimeoutMs: Int = 10_000,
    private val maxAttempts: Int = 3,
    private val probeTimeoutMs: Int = 3_000,
    private val newTransferId: () -> Int = { Random.nextInt(0x10000) },
    private val isNetworkLost: () -> Boolean = { false },
) {

    fun send(export: HdsExport, listener: SendListener = object : SendListener {}): SendResult {
        var lastError = ""
        for (attempt in 1..maxAttempts) {
            val transferId = newTransferId()
            listener.onAttemptStarted(attempt, transferId)
            val frames = ProgramTransfer.frames(export, transferId)
            try {
                connector.connect(host, port, connectTimeoutMs).use { socket ->
                    socket.soTimeout = ackTimeoutMs
                    val output = socket.getOutputStream()
                    val input = socket.getInputStream()
                    frames.forEachIndexed { index, frame ->
                        output.write(frame)
                        output.flush()
                        listener.onFrameSent(index + 1, frames.size, frame)
                        val command = frame[6].toInt() and 0xFF
                        val ack = try {
                            awaitAck(input, command)
                        } catch (e: EOFException) {
                            // Firmware V4.45 accepts the screen packet but closes the connection at the
                            // normal start packet. A retry gives the same result.
                            if (command != Command.START) throw e
                            val detail = "the card closed the connection at the start packet"
                            listener.onAttemptFailed(attempt, detail)
                            return SendResult.Failed(FailureReason.SAFE_REQUIRED, null, detail)
                        }
                        listener.onAck(ack)
                        if (ack.status != 0) {
                            val detail = "status 0x%02x for command 0x%02x".format(ack.status, ack.command)
                            listener.onAttemptFailed(attempt, detail)
                            return SendResult.Failed(FailureReason.REJECTED, ack.status, detail)
                        }
                    }
                }
                return SendResult.Success
            } catch (e: UnexpectedReplyException) {
                val detail = e.message ?: "unexpected reply"
                listener.onAttemptFailed(attempt, detail)
                return SendResult.Failed(FailureReason.UNEXPECTED_REPLY, null, detail)
            } catch (e: IOException) {
                lastError = e.toString()
                listener.onAttemptFailed(attempt, lastError)
                if (isNetworkLost()) return SendResult.Failed(FailureReason.NETWORK_LOST, null, lastError)
            }
        }
        return SendResult.Failed(FailureReason.NETWORK, null, lastError)
    }

    /** Sends the harmless SDK version packet (spec section 3.4). True if the reply starts with HR. */
    fun probe(): Boolean = try {
        connector.connect(host, port, probeTimeoutMs).use { socket ->
            socket.soTimeout = probeTimeoutMs
            socket.getOutputStream().apply {
                write(PROBE)
                flush()
            }
            Ack.isHr(readExactly(socket.getInputStream(), 2))
        }
    } catch (e: IOException) {
        false
    }

    /**
     * Reads ACKs until one answers [command] or has an error status.
     * An ACK with status 0 for a different command is ignored.
     */
    private fun awaitAck(input: InputStream, command: Int): Ack {
        val deadline = System.nanoTime() + ackTimeoutMs * 1_000_000L
        while (true) {
            val ack = Ack.parse(readExactly(input, HtFrame.ACK_LENGTH))
            if (ack.command == command || ack.status != 0) return ack
            if (System.nanoTime() > deadline) {
                throw SocketTimeoutException("no ACK for command 0x%02x".format(command))
            }
        }
    }

    companion object {
        const val DEFAULT_HOST = "192.168.4.1"
        const val DEFAULT_PORT = 6101

        /** SDK version request: length 8, command 0x2001, version 0x01000005 (little-endian). */
        val PROBE: ByteArray get() = byteArrayOf(0x08, 0x00, 0x01, 0x20, 0x05, 0x00, 0x00, 0x01)

        internal fun readExactly(input: InputStream, n: Int): ByteArray {
            val buffer = ByteArray(n)
            var offset = 0
            while (offset < n) {
                val read = input.read(buffer, offset, n - offset)
                if (read < 0) throw EOFException("the card closed the connection")
                offset += read
            }
            return buffer
        }
    }
}
