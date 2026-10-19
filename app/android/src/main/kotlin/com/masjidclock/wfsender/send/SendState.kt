package com.masjidclock.wfsender.send

sealed interface SendState {
    data object Idle : SendState
    data class Sending(val sent: Int, val total: Int) : SendState
    data class Finished(val success: Boolean, val message: String, val log: String) : SendState

    /** The safe form was accepted; the person must say if the display shows the program. */
    data class Confirm(val variant: com.masjidclock.wfsender.protocol.SafeVariant) : SendState
}
