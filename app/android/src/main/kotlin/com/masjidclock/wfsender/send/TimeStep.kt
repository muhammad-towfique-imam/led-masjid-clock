package com.masjidclock.wfsender.send

/** Step 3 of the Send page: the time sync before a send (optional). */
sealed interface TimeStep {
    data object Pending : TimeStep
    data object Busy : TimeStep
    data class Set(val message: String) : TimeStep
    data object Skipped : TimeStep
    data class Failed(val message: String) : TimeStep

    /** The Send step is available after the time is set or skipped. */
    val done: Boolean get() = this is Set || this == Skipped
}
