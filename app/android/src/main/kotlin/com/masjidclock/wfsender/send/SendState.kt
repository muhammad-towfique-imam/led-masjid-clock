package com.masjidclock.wfsender.send

sealed interface SendState {
    data object Idle : SendState
    data class Sending(val sent: Int, val total: Int) : SendState
    data class Finished(val success: Boolean, val message: String, val log: String) : SendState
}
