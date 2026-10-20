package com.masjidclock.wfsender.send

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendGateTest {

    @Test
    fun allowsOnlyOneSendAtATime() {
        val gate = SendGate()
        assertFalse(gate.busy)
        assertTrue(gate.tryStart())
        assertTrue(gate.busy)
        assertFalse("a second send must be refused while the first runs", gate.tryStart())
        gate.finish()
        assertFalse(gate.busy)
        assertTrue(gate.tryStart())
    }

    @Test
    fun appGateIsSharedByAllPages() {
        // The Send page and the Hijri page use the same gate: only 1 transfer to the card at a time.
        val sendPage = SendGate.APP
        val hijriPage = SendGate.APP
        assertTrue(sendPage.tryStart())
        try {
            assertTrue(hijriPage.busy)
            assertFalse(hijriPage.tryStart())
        } finally {
            sendPage.finish()
        }
    }
}
