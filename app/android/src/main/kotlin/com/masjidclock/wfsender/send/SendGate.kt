package com.masjidclock.wfsender.send

/**
 * Allows 1 send at a time. While a send is active, the app must not import a new site,
 * change the card, or probe the card: 2 transfers to a card that writes its flash memory are costly.
 */
class SendGate {
    @Volatile
    var busy: Boolean = false
        private set

    @Synchronized
    fun tryStart(): Boolean {
        if (busy) return false
        busy = true
        return true
    }

    @Synchronized
    fun finish() {
        busy = false
    }
}
