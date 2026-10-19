package com.masjidclock.wfsender.protocol

import java.io.IOException
import java.util.TimeZone
import kotlin.random.Random

/**
 * The safe form of a program transfer, from the HD2020 capture hijri-full.pcapng (firmware V4.45).
 * HD2020 adds its project data (readback.json) to the program; the card does not need it: a test on a
 * V4.45 card on 2026-10-19 showed the program with progam.hds without change.
 */
object SafeTransfer {
    private val OPEN_DATA = byteArrayOf(0x08, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x04, 0x00)

    /** open (0x27), screen (0x13), start (0x02), data (0x03, 1000-byte chunks), end (0x04). */
    fun frames(
        deviceId: ByteArray,
        screen: ByteArray,
        data: ByteArray,
        wallSeconds: Long,
        counter: Long,
        transferId: Int,
    ): List<ByteArray> {
        val chunks = (data.size + ProgramTransfer.CHUNK_SIZE - 1) / ProgramTransfer.CHUNK_SIZE
        val tid = byteArrayOf(transferId.toByte(), (transferId shr 8).toByte())
        val frames = ArrayList<ByteArray>(chunks + 4)
        frames += HdFrames.frame27(0x27, deviceId, counter, OPEN_DATA)
        frames += HdFrames.frame31(Command.SCREEN, deviceId, 1, screen, wallSeconds)
        frames += HdFrames.frame31(Command.START, deviceId, 2, byteArrayOf(0x05) + tid + u16be(chunks), wallSeconds)
        for (i in 0 until chunks) {
            val chunk = data.copyOfRange(i * ProgramTransfer.CHUNK_SIZE, minOf(data.size, (i + 1) * ProgramTransfer.CHUNK_SIZE))
            frames += HdFrames.frame27(Command.DATA, deviceId, counter + 3 + i, byteArrayOf(0x05) + tid + u16be(i) + chunk)
        }
        frames += HdFrames.frame31(Command.END, deviceId, 3, byteArrayOf(0x03) + tid, wallSeconds)
        return frames
    }

    private fun u16be(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
}

/** Sends a program with the safe form over UDP (firmware V4.45). The calls block; use a background thread. */
class SafeSender(
    private val connector: UdpConnector,
    private val host: String = CardSender.DEFAULT_HOST,
    private val port: Int = CardSender.DEFAULT_PORT,
    private val timeoutMs: Int = 2_000,
    private val tries: Int = 3,
    private val newTransferId: () -> Int = { Random.nextInt(0x10000) },
    private val newCounter: () -> Long = { Random.nextLong(0x01000000L, 0x7F000000L) },
    private val wallSeconds: () -> Long = {
        val millis = System.currentTimeMillis()
        (millis + TimeZone.getDefault().getOffset(millis)) / 1000
    },
) {

    fun send(export: HdsExport, listener: SendListener = object : SendListener {}): SendResult {
        val transferId = newTransferId()
        listener.onAttemptStarted(1, transferId)
        return try {
            connector.open().use { socket ->
                val link = UdpLink(socket, host, port, timeoutMs, tries)
                val identity = link.exchange(HdFrames.discoverRequest(1), 0x1b)?.let { CardIdentity.parse(it) }
                    ?: return fail(listener, FailureReason.NETWORK, null, "no answer to discover")
                val frames = SafeTransfer.frames(
                    identity.deviceId, export.screen, export.program,
                    wallSeconds(), newCounter(), transferId,
                )
                frames.forEachIndexed { index, frame ->
                    val command = frame[6].toInt() and 0xFF
                    listener.onFrameSent(index + 1, frames.size, frame)
                    val answer = link.exchange(frame, command)
                        ?: return fail(listener, FailureReason.NETWORK, null, "no answer for command 0x%02x".format(command))
                    val ack = Ack.parse(answer)
                    listener.onAck(ack)
                    if (ack.status != 0) {
                        return fail(listener, FailureReason.REJECTED, ack.status,
                            "status 0x%02x for command 0x%02x".format(ack.status, command))
                    }
                }
                SendResult.Success
            }
        } catch (e: UnexpectedReplyException) {
            fail(listener, FailureReason.UNEXPECTED_REPLY, null, e.message ?: "unexpected reply")
        } catch (e: IOException) {
            fail(listener, FailureReason.NETWORK, null, e.toString())
        }
    }

    private fun fail(listener: SendListener, reason: FailureReason, status: Int?, detail: String): SendResult.Failed {
        listener.onAttemptFailed(1, detail)
        return SendResult.Failed(reason, status, detail)
    }
}
