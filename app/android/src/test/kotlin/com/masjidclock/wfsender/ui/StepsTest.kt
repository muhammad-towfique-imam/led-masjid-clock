package com.masjidclock.wfsender.ui

import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.send.SendState
import com.masjidclock.wfsender.ui.StepState.CURRENT
import com.masjidclock.wfsender.ui.StepState.DONE
import com.masjidclock.wfsender.ui.StepState.WAITING
import com.masjidclock.wfsender.usb.UsbStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class StepsTest {

    private val idle = SendState.Idle

    @Test
    fun noFileLoaded() {
        assertEquals(Steps(CURRENT, WAITING, WAITING), stepStates(false, ConnectionStatus.NOT_CONNECTED, idle))
    }

    @Test
    fun fileLoadedButNotConnected() {
        assertEquals(Steps(DONE, CURRENT, WAITING), stepStates(true, ConnectionStatus.NOT_CONNECTED, idle))
        assertEquals(Steps(DONE, CURRENT, WAITING), stepStates(true, ConnectionStatus.NO_DISPLAY, idle))
    }

    @Test
    fun displayFound() {
        assertEquals(Steps(DONE, DONE, CURRENT), stepStates(true, ConnectionStatus.DISPLAY_FOUND, idle))
    }

    @Test
    fun sending() {
        // The connection check stops during a send, so the status is not used.
        val sending = SendState.Sending(3, 16)
        assertEquals(Steps(DONE, DONE, CURRENT), stepStates(true, ConnectionStatus.NOT_CONNECTED, sending))
    }

    @Test
    fun success() {
        val done = SendState.Finished(success = true, message = "Done.", log = "")
        assertEquals(Steps(DONE, DONE, DONE), stepStates(true, ConnectionStatus.DISPLAY_FOUND, done))
    }

    @Test
    fun failure() {
        val failed = SendState.Finished(success = false, message = "weak", log = "")
        assertEquals(Steps(DONE, DONE, CURRENT), stepStates(true, ConnectionStatus.DISPLAY_FOUND, failed))
    }

    @Test
    fun createStepsFollowTheUsbStatus() {
        assertEquals(CreateSteps(CURRENT, WAITING, WAITING, WAITING), createSteps(UsbStatus.NO_PERMISSION))
        assertEquals(CreateSteps(DONE, CURRENT, WAITING, WAITING), createSteps(UsbStatus.NO_DRIVE))
        assertEquals(CreateSteps(DONE, DONE, CURRENT, WAITING), createSteps(UsbStatus.NO_EXPORT))
        assertEquals(CreateSteps(DONE, DONE, CURRENT, WAITING), createSteps(UsbStatus.TOO_LARGE))
        assertEquals(CreateSteps(DONE, DONE, DONE, CURRENT), createSteps(UsbStatus.FOUND))
    }
}
