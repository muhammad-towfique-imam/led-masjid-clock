package com.masjidclock.wfsender.protocol

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A local TCP server that acts as a WF card. [handler] runs once for each connection,
 * with the 1-based connection number. When the handler returns, the connection closes.
 */
class FakeCard(
    private val handler: FakeCard.(connection: Int, input: InputStream, output: OutputStream) -> Unit,
) : Closeable {

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    val connections = AtomicInteger()
    val received = CopyOnWriteArrayList<ByteArray>()

    val connector = CardConnector { _, _, timeoutMs ->
        Socket().apply { connect(InetSocketAddress(InetAddress.getLoopbackAddress(), this@FakeCard.port), timeoutMs) }
    }

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                val number = connections.incrementAndGet()
                thread(isDaemon = true) {
                    socket.use {
                        try {
                            handler(this@FakeCard, number, it.getInputStream(), it.getOutputStream())
                        } catch (e: IOException) {
                            // The client closed the connection.
                        }
                    }
                }
            }
        }
    }

    /** Reads 1 HT frame and records it. Returns null at end of stream. */
    fun receive(input: InputStream): ByteArray? {
        val header = readFully(input, 6) ?: return null
        val length = ((header[4].toInt() and 0xFF) shl 8) or (header[5].toInt() and 0xFF)
        val rest = readFully(input, length - 6) ?: return null
        return (header + rest).also { received += it }
    }

    /** Reads frames and ACKs each one with status 0, until end of stream. */
    fun ackAll(input: InputStream, output: OutputStream) {
        while (true) {
            val frame = receive(input) ?: return
            output.write(ack(frame[6].toInt() and 0xFF))
            output.flush()
        }
    }

    /** Reads frames and never answers, until end of stream. */
    fun ignoreAll(input: InputStream) {
        while (receive(input) != null) Unit
    }

    override fun close() = server.close()

    companion object {
        fun ack(command: Int, status: Int = 0): ByteArray = ByteArray(HtFrame.ACK_LENGTH).also {
            it[0] = 'H'.code.toByte()
            it[1] = 'R'.code.toByte()
            it[20] = command.toByte()
            it[21] = status.toByte()
            it[30] = 0xAA.toByte()
        }

        fun readFully(input: InputStream, n: Int): ByteArray? {
            val buffer = ByteArray(n)
            var offset = 0
            while (offset < n) {
                val read = input.read(buffer, offset, n - offset)
                if (read < 0) return null
                offset += read
            }
            return buffer
        }
    }
}
