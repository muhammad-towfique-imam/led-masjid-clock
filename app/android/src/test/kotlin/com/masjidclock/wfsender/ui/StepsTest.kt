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

    private val found = ConnectionStatus.DISPLAY_FOUND

    @Test
    fun noFileLoaded() {
        assertEquals(
            Steps(CURRENT, WAITING, WAITING, WAITING),
            stepStates(false, ConnectionStatus.NOT_CONNECTED, timeDone = false, sendState = idle),
        )
    }

    @Test
    fun fileLoadedButNotConnected() {
        assertEquals(
            Steps(DONE, CURRENT, WAITING, WAITING),
            stepStates(true, ConnectionStatus.NOT_CONNECTED, timeDone = false, sendState = idle),
        )
        assertEquals(
            Steps(DONE, CURRENT, WAITING, WAITING),
            stepStates(true, ConnectionStatus.NO_DISPLAY, timeDone = false, sendState = idle),
        )
    }

    @Test
    fun displayFoundTimeNotDone() {
        assertEquals(Steps(DONE, DONE, CURRENT, WAITING), stepStates(true, found, timeDone = false, sendState = idle))
    }

    @Test
    fun displayFoundTimeDone() {
        assertEquals(Steps(DONE, DONE, DONE, CURRENT), stepStates(true, found, timeDone = true, sendState = idle))
    }

    @Test
    fun timeDoneButWifiLostWaitsForTheWifi() {
        assertEquals(
            Steps(DONE, CURRENT, WAITING, WAITING),
            stepStates(true, ConnectionStatus.NOT_CONNECTED, timeDone = true, sendState = idle),
        )
    }

    @Test
    fun sending() {
        // The connection check stops during a send, so the status is not used.
        val sending = SendState.Sending(3, 16)
        assertEquals(
            Steps(DONE, DONE, DONE, CURRENT),
            stepStates(true, ConnectionStatus.NOT_CONNECTED, timeDone = true, sendState = sending),
        )
    }

    @Test
    fun success() {
        val done = SendState.Finished(success = true, message = "Done.", log = "")
        assertEquals(Steps(DONE, DONE, DONE, DONE), stepStates(true, found, timeDone = true, sendState = done))
    }

    @Test
    fun failure() {
        val failed = SendState.Finished(success = false, message = "weak", log = "")
        assertEquals(Steps(DONE, DONE, DONE, CURRENT), stepStates(true, found, timeDone = true, sendState = failed))
    }

    @Test
    fun createStepsFollowTheUsbStatus() {
        assertEquals(CreateSteps(CURRENT, WAITING, WAITING, WAITING), createSteps(UsbStatus.NO_PERMISSION))
        assertEquals(CreateSteps(DONE, CURRENT, WAITING, WAITING), createSteps(UsbStatus.NO_DRIVE))
        assertEquals(CreateSteps(DONE, DONE, CURRENT, WAITING), createSteps(UsbStatus.NO_EXPORT))
        assertEquals(CreateSteps(DONE, DONE, CURRENT, WAITING), createSteps(UsbStatus.TOO_LARGE))
        assertEquals(CreateSteps(DONE, DONE, DONE, CURRENT), createSteps(UsbStatus.FOUND))
    }

    @Test
    fun timeStepsFollowTheConnectionAndTheResult() {
        assertEquals(TimeSteps(CURRENT, WAITING), timeSteps(found = false, timeSet = false))
        assertEquals(TimeSteps(DONE, CURRENT), timeSteps(found = true, timeSet = false))
        assertEquals(TimeSteps(DONE, DONE), timeSteps(found = true, timeSet = true))
    }

}
