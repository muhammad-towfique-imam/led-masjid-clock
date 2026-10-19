package com.masjidclock.wfsender.protocol

import java.io.IOException
import java.net.DatagramSocket

/** Opens a UDP socket. The Android app gives a socket that is bound to the card WiFi. */
fun interface UdpConnector {
    fun open(): DatagramSocket
}

enum class ClockFailure { NO_ANSWER, REJECTED, NETWORK }

sealed class ClockResult {
    data class Success(val cardName: String) : ClockResult()
    data class Failed(val reason: ClockFailure, val status: Int?) : ClockResult()
}

/** Sets the clock of a card like HD2020 (UDP port 6101). The calls block; use a background thread. */
class CardClock(
    private val connector: UdpConnector,
    private val host: String = CardSender.DEFAULT_HOST,
    private val port: Int = CardSender.DEFAULT_PORT,
    private val timeoutMs: Int = 2_000,
    private val tries: Int = 3,
) {

    /** The identity of the card, or null if it does not answer. */
    fun discover(): CardIdentity? = try {
        connector.open().use { socket -> discover(socket) }
    } catch (e: IOException) {
        null
    } catch (e: UnexpectedReplyException) {
        null
    }

    fun setTime(wallSeconds: Long): ClockResult = try {
        connector.open().use { socket ->
            val identity = discover(socket) ?: return ClockResult.Failed(ClockFailure.NO_ANSWER, null)
            val answer = exchange(socket, TimePacket.build(identity.deviceId, 1, wallSeconds), TimePacket.COMMAND)
                ?: return ClockResult.Failed(ClockFailure.NO_ANSWER, null)
            val status = answer[21].toInt() and 0xFF
            if (status == 0) ClockResult.Success(identity.name) else ClockResult.Failed(ClockFailure.REJECTED, status)
        }
    } catch (e: IOException) {
        ClockResult.Failed(ClockFailure.NETWORK, null)
    } catch (e: UnexpectedReplyException) {
        ClockResult.Failed(ClockFailure.NO_ANSWER, null)
    }

    private fun link(socket: DatagramSocket) = UdpLink(socket, host, port, timeoutMs, tries)

    private fun discover(socket: DatagramSocket): CardIdentity? =
        link(socket).exchange(HdFrames.discoverRequest(1), 0x1b)?.let { CardIdentity.parse(it) }

    private fun exchange(socket: DatagramSocket, packet: ByteArray, command: Int): ByteArray? =
        link(socket).exchange(packet, command)
}
