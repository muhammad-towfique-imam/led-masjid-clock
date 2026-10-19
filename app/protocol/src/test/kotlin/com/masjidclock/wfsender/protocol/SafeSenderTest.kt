package com.masjidclock.wfsender.protocol

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeSenderTest {

    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.readBytes()

    private val export = HdsExport(resource("/safe/screen.hds"), resource("/safe/progam.hds"))
    private val discoverAnswer = resource("/time/discover-answer.bin")
    private var card: DatagramSocket? = null
    private val received = CopyOnWriteArrayList<ByteArray>()

    @After
    fun close() {
        card?.close()
    }

    private fun ack(command: Int, status: Int = 0) = ByteArray(31).also {
        it[0] = 'H'.code.toByte(); it[1] = 'R'.code.toByte(); it[20] = command.toByte(); it[21] = status.toByte()
        it[30] = 0xAA.toByte()
    }

    /** A UDP fake card. [answer] gets each request and its number (1-based). */
    private fun startCard(answer: (request: ByteArray, count: Int) -> List<ByteArray>): Int {
        val socket = DatagramSocket(0, InetAddress.getLoopbackAddress())
        card = socket
        thread(isDaemon = true) {
            val buffer = ByteArray(4096)
            var count = 0
            while (!socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: Exception) {
                    break
                }
                val request = packet.data.copyOf(packet.length)
                received += request
                for (reply in answer(request, ++count)) socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
            }
        }
        return socket.localPort
    }

    private fun normal(request: ByteArray): List<ByteArray> =
        if (request[6].toInt() == 0x1b) listOf(discoverAnswer) else listOf(ack(request[6].toInt() and 0xFF))

    private fun sender(port: Int) = SafeSender(
        connector = UdpConnector { DatagramSocket() },
        host = "127.0.0.1",
        port = port,
        timeoutMs = 200,
        tries = 3,
        newTransferId = { 0xb4ad },
        newCounter = { 1000L },
        wallSeconds = { 0x6ad53595L },
    )

    @Test
    fun sendsDiscoverThenTheSafeTransfer() {
        val port = startCard { request, _ -> normal(request) }
        assertEquals(SendResult.Success, sender(port).send(export))
        val commands = received.map { it[6].toInt() and 0xFF }
        assertEquals(listOf(0x1b, 0x27, 0x13, 0x02) + List(7) { 0x03 } + listOf(0x04), commands)
        // The frames carry the device ID from the discover answer.
        assertEquals(discoverAnswer.copyOfRange(3, 18).toList(), received[1].copyOfRange(8, 23).toList())
        // The data packets carry progam.hds without change (confirmed on a V4.45 card, 2026-10-19).
        val sent = received.filter { it[6].toInt() == 0x03 }.fold(ByteArray(0)) { acc, f -> acc + f.copyOfRange(32, f.size - 3) }
        assertEquals(export.program.toList(), sent.toList())
    }

    @Test
    fun sendsALostPacketAgain() {
        val port = startCard { request, count -> if (count == 5) emptyList() else normal(request) }
        assertEquals(SendResult.Success, sender(port).send(export))
    }

    @Test
    fun errorStatusIsRejected() {
        val port = startCard { request, _ ->
            if (request[6].toInt() == 0x04) listOf(ack(0x04, status = 0x09)) else normal(request)
        }
        val result = sender(port).send(export) as SendResult.Failed
        assertEquals(FailureReason.REJECTED, result.reason)
        assertEquals(0x09, result.status)
    }

    @Test
    fun noAnswerIsANetworkFailure() {
        val port = startCard { _, _ -> emptyList() }
        assertEquals(FailureReason.NETWORK, (sender(port).send(export) as SendResult.Failed).reason)
    }

    @Test
    fun reportsFramesAndAcks() {
        val port = startCard { request, _ -> normal(request) }
        val events = mutableListOf<String>()
        val listener = object : SendListener {
            override fun onFrameSent(index: Int, total: Int, frame: ByteArray) { events += "frame $index/$total" }
            override fun onAck(ack: Ack) { events += "ack" }
        }
        sender(port).send(export, listener)
        assertEquals("frame 11/11", events[events.size - 2])
        assertEquals(11, events.count { it == "ack" })
    }
}
