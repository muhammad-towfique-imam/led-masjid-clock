package com.masjidclock.wfsender.ui

import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.send.SendState

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
