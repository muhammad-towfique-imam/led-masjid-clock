package com.masjidclock.wfsender.ui

import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.send.SendState
import com.masjidclock.wfsender.usb.UsbStatus

enum class StepState { DONE, CURRENT, WAITING }

/** The state of the 3 steps on the page: load file, connect to the display WiFi, send. */
data class Steps(val load: StepState, val connect: StepState, val send: StepState)

fun stepStates(fileLoaded: Boolean, connection: ConnectionStatus, sendState: SendState): Steps {
    if (!fileLoaded) return Steps(StepState.CURRENT, StepState.WAITING, StepState.WAITING)
    return when (sendState) {
        is SendState.Finished ->
            Steps(StepState.DONE, StepState.DONE, if (sendState.success) StepState.DONE else StepState.CURRENT)
        is SendState.Sending -> Steps(StepState.DONE, StepState.DONE, StepState.CURRENT)
        SendState.Idle ->
            if (connection == ConnectionStatus.DISPLAY_FOUND) {
                Steps(StepState.DONE, StepState.DONE, StepState.CURRENT)
            } else {
                Steps(StepState.DONE, StepState.CURRENT, StepState.WAITING)
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
