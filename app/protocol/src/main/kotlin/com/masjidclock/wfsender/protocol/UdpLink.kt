package com.masjidclock.wfsender.protocol

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Request and answer over UDP: sends a packet up to [tries] times and waits for the HR answer. */
internal class UdpLink(
    private val socket: DatagramSocket,
    host: String,
    private val port: Int,
    private val timeoutMs: Int,
    private val tries: Int,
) {
    private val address = InetAddress.getByName(host)
    private val buffer = ByteArray(4096)

    /** The HR answer for [command], or null after [tries] tries without an answer. */
    fun exchange(packet: ByteArray, command: Int): ByteArray? {
        socket.soTimeout = timeoutMs
        repeat(tries) {
            socket.send(DatagramPacket(packet, packet.size, address, port))
            try {
                while (true) {
                    val reply = DatagramPacket(buffer, buffer.size)
                    socket.receive(reply)
                    val data = reply.data.copyOf(reply.length)
                    if (data.size > 21 && Ack.isHr(data) && (data[20].toInt() and 0xFF) == command) return data
                }
            } catch (e: SocketTimeoutException) {
                // Try again.
            }
        }
        return null
    }
}
