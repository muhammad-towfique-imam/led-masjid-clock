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
}
