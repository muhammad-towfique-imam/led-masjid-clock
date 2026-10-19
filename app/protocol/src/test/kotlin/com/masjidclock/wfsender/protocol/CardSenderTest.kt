package com.masjidclock.wfsender.protocol

import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSenderTest {

    private val export = HdsExport.fromDirectory(File(javaClass.getResource("/export-128x64")!!.toURI()))
    private val captured = javaClass.getResourceAsStream("/captured-transfer.bin")!!.readBytes()
    private var card: FakeCard? = null

    @After
    fun closeCard() {
        card?.close()
    }

    private fun startCard(handler: FakeCard.(Int, java.io.InputStream, java.io.OutputStream) -> Unit): FakeCard =
        FakeCard(handler).also { card = it }

    private fun sender(card: FakeCard, isNetworkLost: () -> Boolean = { false }) = CardSender(
        connector = card.connector,
        connectTimeoutMs = 1_000,
        ackTimeoutMs = 300,
        newTransferId = { 0x030c },
        isNetworkLost = isNetworkLost,
    )

    private fun failure(result: SendResult): SendResult.Failed {
        assertTrue("expected a failure, got $result", result is SendResult.Failed)
        return result as SendResult.Failed
    }

    @Test
    fun sendsTheSameBytesAsHd2020AndSucceeds() {
        val card = startCard { _, input, output -> ackAll(input, output) }
        val result = sender(card).send(export)
        assertEquals(SendResult.Success, result)
        assertEquals(1, card.connections.get())
        val all = card.received.fold(ByteArray(0)) { acc, f -> acc + f }
        assertArrayEquals(captured, all)
    }

    @Test
    fun retriesAfterMissingAck() {
        val card = startCard { connection, input, output ->
            if (connection == 1) ignoreAll(input) else ackAll(input, output)
        }
        val result = sender(card).send(export)
        assertEquals(SendResult.Success, result)
        assertEquals(2, card.connections.get())
    }

    @Test
    fun failsWithNetworkAfterThreeAttempts() {
        val card = startCard { _, input, _ -> ignoreAll(input) }
        val failed = failure(sender(card).send(export))
        assertEquals(FailureReason.NETWORK, failed.reason)
        assertEquals(3, card.connections.get())
    }

    @Test
    fun failsWithNetworkWhenNoCardListens() {
        val card = startCard { _, input, output -> ackAll(input, output) }
        card.close()
        val failed = failure(sender(card).send(export))
        assertEquals(FailureReason.NETWORK, failed.reason)
    }

    @Test
    fun stopsAtOnceOnErrorStatus() {
        val card = startCard { _, input, output ->
            receive(input); output.write(FakeCard.ack(Command.SCREEN)); output.flush()
            receive(input); output.write(FakeCard.ack(Command.START, status = 0x05)); output.flush()
            ignoreAll(input)
        }
        val failed = failure(sender(card).send(export))
        assertEquals(FailureReason.REJECTED, failed.reason)
        assertEquals(0x05, failed.status)
        assertEquals(1, card.connections.get())
    }

    @Test
    fun retriesWhenCardClosesTheConnection() {
        val card = startCard { connection, input, output ->
            if (connection == 1) {
                repeat(2) {
                    val frame = receive(input)!!
                    output.write(FakeCard.ack(frame[6].toInt() and 0xFF)); output.flush()
                }
                // Return: the connection closes in the middle of the transfer.
            } else {
                ackAll(input, output)
            }
        }
        assertEquals(SendResult.Success, sender(card).send(export))
        assertEquals(2, card.connections.get())
    }

    @Test
    fun stopsAtOnceOnUnexpectedReply() {
        val card = startCard { _, input, output ->
            receive(input)
            output.write(ByteArray(HtFrame.ACK_LENGTH) { 'X'.code.toByte() }); output.flush()
            ignoreAll(input)
        }
        val failed = failure(sender(card).send(export))
        assertEquals(FailureReason.UNEXPECTED_REPLY, failed.reason)
        assertEquals(1, card.connections.get())
    }

    @Test
    fun stopsAtOnceWhenTheNetworkIsLost() {
        val card = startCard { _, input, _ -> ignoreAll(input) }
        val failed = failure(sender(card, isNetworkLost = { true }).send(export))
        assertEquals(FailureReason.NETWORK_LOST, failed.reason)
        assertEquals(1, card.connections.get())
    }

    @Test
    fun succeedsWhenAckArrivesInPieces() {
        val card = startCard { _, input, output ->
            while (true) {
                val frame = receive(input) ?: break
                val ack = FakeCard.ack(frame[6].toInt() and 0xFF)
                output.write(ack, 0, 10); output.flush()
                Thread.sleep(20)
                output.write(ack, 10, ack.size - 10); output.flush()
            }
        }
        assertEquals(SendResult.Success, sender(card).send(export))
        assertEquals(1, card.connections.get())
    }

    @Test
    fun ignoresAckForOtherCommand() {
        val card = startCard { _, input, output ->
            while (true) {
                val frame = receive(input) ?: break
                output.write(FakeCard.ack(0x77)); output.flush()
                output.write(FakeCard.ack(frame[6].toInt() and 0xFF)); output.flush()
            }
        }
        assertEquals(SendResult.Success, sender(card).send(export))
        assertEquals(16, card.received.size)
    }

    @Test
    fun reportsAttemptsFramesAndAcks() {
        val card = startCard { connection, input, output ->
            if (connection == 1) ignoreAll(input) else ackAll(input, output)
        }
        val events = mutableListOf<String>()
        val listener = object : SendListener {
            override fun onAttemptStarted(attempt: Int, transferId: Int) { events += "attempt $attempt" }
            override fun onFrameSent(index: Int, total: Int, frame: ByteArray) { events += "frame $index/$total" }
            override fun onAck(ack: Ack) { events += "ack" }
            override fun onAttemptFailed(attempt: Int, error: String) { events += "failed $attempt" }
        }
        assertEquals(SendResult.Success, sender(card).send(export, listener))
        assertEquals(listOf("attempt 1", "frame 1/16", "failed 1", "attempt 2"), events.take(4))
        assertEquals("frame 16/16", events[events.size - 2])
        assertEquals("ack", events.last())
        assertEquals(16, events.count { it == "ack" })
    }

    @Test
    fun cardThatClosesAtStartNeedsTheSafeForm() {
        // Firmware V4.45: the card accepts the screen packet, then closes the connection at the start packet.
        val card = startCard { _, input, output ->
            receive(input); output.write(FakeCard.ack(Command.SCREEN)); output.flush()
            receive(input)
        }
        val failed = failure(sender(card).send(export))
        assertEquals(FailureReason.SAFE_REQUIRED, failed.reason)
        assertEquals(1, card.connections.get())
    }
}
