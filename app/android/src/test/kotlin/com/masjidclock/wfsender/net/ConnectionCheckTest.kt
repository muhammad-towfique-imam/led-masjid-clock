package com.masjidclock.wfsender.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionCheckTest {

    private var network: String? = "wifi-1"
    private var cardAnswers = true
    private var probes = 0

    private val check = ConnectionCheck({ network }) { _ -> probes++; cardAnswers }

    @Test
    fun stopsProbingAfterTheCardAnswers() {
        repeat(5) { assertEquals(ConnectionStatus.DISPLAY_FOUND, check.next()) }
        assertEquals("a card that has received a program flickers on each probe", 1, probes)
    }

    @Test
    fun keepsProbingWhileTheCardDoesNotAnswer() {
        cardAnswers = false
        repeat(3) { assertEquals(ConnectionStatus.NO_DISPLAY, check.next()) }
        cardAnswers = true
        assertEquals(ConnectionStatus.DISPLAY_FOUND, check.next())
        assertEquals(ConnectionStatus.DISPLAY_FOUND, check.next())
        assertEquals(4, probes)
    }

    @Test
    fun probesAgainAfterTheWifiDisconnects() {
        check.next()
        network = null
        assertEquals(ConnectionStatus.NOT_CONNECTED, check.next())
        assertEquals(1, probes)
        network = "wifi-1"
        assertEquals(ConnectionStatus.DISPLAY_FOUND, check.next())
        assertEquals(2, probes)
    }

    @Test
    fun probesAgainOnADifferentWifiNetwork() {
        check.next()
        network = "wifi-2"
        check.next()
        assertEquals(2, probes)
    }

    @Test
    fun noProbeAfterStop() {
        cardAnswers = false
        check.next()
        check.stop()
        repeat(3) { assertNull(check.next()) }
        assertEquals(1, probes)
    }
}
