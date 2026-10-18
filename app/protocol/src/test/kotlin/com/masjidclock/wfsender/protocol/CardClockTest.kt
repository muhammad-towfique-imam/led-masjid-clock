package com.masjidclock.wfsender.protocol

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardClockTest {

    private fun resource(name: String) = javaClass.getResourceAsStream("/time/$name")!!.readBytes()

    private val discoverAnswer = resource("discover-answer.bin")
    private val timeAnswer = resource("set-time-answer.bin")
    private var card: DatagramSocket? = null

    @After
    fun close() {
        card?.close()
    }

    /** A UDP fake card. [answer] gets each request and returns the datagrams to send back. */
    private fun startCard(answer: (request: ByteArray, count: Int) -> List<ByteArray>): Pair<Int, AtomicInteger> {
        val socket = DatagramSocket(0, InetAddress.getLoopbackAddress())
        card = socket
        val count = AtomicInteger()
        thread(isDaemon = true) {
            val buffer = ByteArray(2048)
            while (!socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: Exception) {
                    break
                }
                val request = packet.data.copyOf(packet.length)
                for (reply in answer(request, count.incrementAndGet())) {
                    socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                }
            }
        }
        return socket.localPort to count
    }

    private fun clock(port: Int) = CardClock(
        connector = UdpConnector { DatagramSocket() },
        host = "127.0.0.1",
        port = port,
        timeoutMs = 200,
        tries = 3,
    )

    private fun normalCard(request: ByteArray): List<ByteArray> = when (request[6].toInt()) {
        0x1b -> listOf(discoverAnswer)
        0x14 -> listOf(timeAnswer)
        else -> emptyList()
    }

    @Test
    fun setsTheTime() {
        var timePacket: ByteArray? = null
        val (port, _) = startCard { request, _ ->
            if (request[6].toInt() == 0x14) timePacket = request
            normalCard(request)
        }
        val result = clock(port).setTime(0x6ad53595L)
        assertEquals(ClockResult.Success("WF1_3030f98d34c8"), result)
        // The time packet carries the device ID from the discover answer.
        assertEquals(
            discoverAnswer.copyOfRange(3, 18).toList(),
            timePacket!!.copyOfRange(8, 23).toList(),
        )
        assertEquals("2026/10/18 21:09:41", String(timePacket!!, 70, 19, Charsets.US_ASCII))
    }

    @Test
    fun noAnswerAfterThreeTries() {
        val (port, count) = startCard { _, _ -> emptyList() }
        assertEquals(ClockResult.Failed(ClockFailure.NO_ANSWER, null), clock(port).setTime(0x6ad53595L))
        assertEquals(3, count.get())
    }

    @Test
    fun retriesALostPacket() {
        val (port, _) = startCard { request, count -> if (count == 1) emptyList() else normalCard(request) }
        assertTrue(clock(port).setTime(0x6ad53595L) is ClockResult.Success)
    }

    @Test
    fun errorStatusIsRejected() {
        val rejected = timeAnswer.copyOf().also { it[21] = 0x07 }
        val (port, _) = startCard { request, _ ->
            if (request[6].toInt() == 0x14) listOf(rejected) else normalCard(request)
        }
        assertEquals(ClockResult.Failed(ClockFailure.REJECTED, 0x07), clock(port).setTime(0x6ad53595L))
    }

    @Test
    fun ignoresAnAnswerForADifferentCommand() {
        val (port, _) = startCard { request, _ ->
            if (request[6].toInt() == 0x14) listOf(discoverAnswer, timeAnswer) else normalCard(request)
        }
        assertTrue(clock(port).setTime(0x6ad53595L) is ClockResult.Success)
    }

    @Test
    fun discoverGivesTheCardName() {
        val (port, _) = startCard { request, _ -> normalCard(request) }
        assertEquals("WF1_3030f98d34c8", clock(port).discover()?.name)
    }

    @Test
    fun discoverWithoutAnswerGivesNull() {
        val (port, _) = startCard { _, _ -> emptyList() }
        assertEquals(null, clock(port).discover())
    }
}
