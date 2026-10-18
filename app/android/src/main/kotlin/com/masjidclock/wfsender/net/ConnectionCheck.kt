package com.masjidclock.wfsender.net

enum class ConnectionStatus { NOT_CONNECTED, NO_DISPLAY, DISPLAY_FOUND }

/**
 * Decides when to probe the card (spec section 5.3). After a card has received a program,
 * each probe makes the display flicker, so the check probes only until the card answers.
 * After that, it only watches that the phone stays on the same WiFi network.
 */
class ConnectionCheck<N : Any>(
    private val currentNetwork: () -> N?,
    private val probe: (N) -> Boolean,
) {
    private var foundOn: N? = null

    @Volatile
    private var stopped = false

    /** The new status, or null after [stop]. */
    fun next(): ConnectionStatus? {
        if (stopped) return null
        val network = currentNetwork()
        if (network == null) {
            foundOn = null
            return ConnectionStatus.NOT_CONNECTED
        }
        if (network == foundOn) return ConnectionStatus.DISPLAY_FOUND
        foundOn = if (probe(network)) network else null
        return if (foundOn != null) ConnectionStatus.DISPLAY_FOUND else ConnectionStatus.NO_DISPLAY
    }

    /** Stops all probes, for example when a send starts. */
    fun stop() {
        stopped = true
    }
}
