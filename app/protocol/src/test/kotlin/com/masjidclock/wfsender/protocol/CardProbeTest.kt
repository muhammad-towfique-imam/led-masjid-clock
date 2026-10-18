package com.masjidclock.wfsender.protocol

import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardProbeTest {

    private var card: FakeCard? = null

    @After
    fun closeCard() {
        card?.close()
    }

    private fun startCard(handler: FakeCard.(Int, java.io.InputStream, java.io.OutputStream) -> Unit): FakeCard =
        FakeCard(handler).also { card = it }

    private fun sender(card: FakeCard) = CardSender(connector = card.connector, probeTimeoutMs = 300)

    @Test
    fun findsACardThatAnswersWithHr() {
        val request = AtomicReference<ByteArray>()
        val card = startCard { _, input, output ->
            request.set(FakeCard.readFully(input, 8))
            output.write(FakeCard.ack(0x00, 0x04)); output.flush()
            input.read()
        }
        assertTrue(sender(card).probe())
        assertArrayEquals(byteArrayOf(0x08, 0x00, 0x01, 0x20, 0x05, 0x00, 0x00, 0x01), request.get())
        assertArrayEquals(request.get(), CardSender.PROBE)
    }

    @Test
    fun findsACardWhenTheReplyArrivesInPieces() {
        val card = startCard { _, input, output ->
            FakeCard.readFully(input, 8)
            output.write('H'.code); output.flush()
            Thread.sleep(20)
            output.write('R'.code); output.flush()
            input.read()
        }
        assertTrue(sender(card).probe())
    }

    @Test
    fun noReply() {
        val card = startCard { _, input, _ -> while (input.read() >= 0) Unit }
        assertFalse(sender(card).probe())
    }

    @Test
    fun replyWithoutHr() {
        val card = startCard { _, input, output ->
            FakeCard.readFully(input, 8)
            output.write(ByteArray(31) { 'X'.code.toByte() }); output.flush()
            input.read()
        }
        assertFalse(sender(card).probe())
    }

    @Test
    fun nothingListens() {
        val card = startCard { _, _, _ -> }
        card.close()
        assertFalse(sender(card).probe())
    }
}
