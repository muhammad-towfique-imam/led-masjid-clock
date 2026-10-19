package com.masjidclock.wfsender.ui

import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.send.SendState
import com.masjidclock.wfsender.usb.UsbStatus

enum class StepState { DONE, CURRENT, WAITING }

/** The state of the 4 steps of the Send page: load file, connect, set the time, send. */
data class Steps(val load: StepState, val connect: StepState, val time: StepState, val send: StepState)

/** [timeDone] is true when the time was set or skipped for the display on this WiFi. */
fun stepStates(fileLoaded: Boolean, connection: ConnectionStatus, timeDone: Boolean, sendState: SendState): Steps {
    val done = StepState.DONE
    val current = StepState.CURRENT
    val waiting = StepState.WAITING
    if (!fileLoaded) return Steps(current, waiting, waiting, waiting)
    return when (sendState) {
        is SendState.Finished -> Steps(done, done, done, if (sendState.success) done else current)
        is SendState.Sending -> Steps(done, done, done, current)
        SendState.Idle -> when {
            connection != ConnectionStatus.DISPLAY_FOUND -> Steps(done, current, waiting, waiting)
            !timeDone -> Steps(done, done, current, waiting)
            else -> Steps(done, done, done, current)
        }
    }
}

/** The state of the 4 steps of the Create page: file access, USB drive, files, save or share. */
data class CreateSteps(val access: StepState, val drive: StepState, val files: StepState, val output: StepState)

fun createSteps(status: UsbStatus): CreateSteps = when (status) {
    UsbStatus.NO_PERMISSION -> CreateSteps(StepState.CURRENT, StepState.WAITING, StepState.WAITING, StepState.WAITING)
    UsbStatus.NO_DRIVE -> CreateSteps(StepState.DONE, StepState.CURRENT, StepState.WAITING, StepState.WAITING)
    UsbStatus.NO_EXPORT, UsbStatus.TOO_LARGE ->
        CreateSteps(StepState.DONE, StepState.DONE, StepState.CURRENT, StepState.WAITING)
    UsbStatus.FOUND -> CreateSteps(StepState.DONE, StepState.DONE, StepState.DONE, StepState.CURRENT)
}

/** The state of the 2 steps of the Time tab: connect to the display WiFi, set the time. */
data class TimeSteps(val connect: StepState, val set: StepState)

fun timeSteps(found: Boolean, timeSet: Boolean): TimeSteps = when {
    !found -> TimeSteps(StepState.CURRENT, StepState.WAITING)
    timeSet -> TimeSteps(StepState.DONE, StepState.DONE)
    else -> TimeSteps(StepState.DONE, StepState.CURRENT)
}
