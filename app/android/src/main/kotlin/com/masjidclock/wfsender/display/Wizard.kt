package com.masjidclock.wfsender.display

/** The steps of a display tab, shown 1 at a time with Prev and Next. */
enum class WizardStep { CONNECT, INPUTS, TIME, SEND }

/** The inputs first (user, 2026-10-21: the operator prepares them before going to the display), then Connect. */
fun wizardSteps(hasInputs: Boolean): List<WizardStep> =
    if (hasInputs) listOf(WizardStep.INPUTS, WizardStep.CONNECT, WizardStep.TIME, WizardStep.SEND)
    else listOf(WizardStep.CONNECT, WizardStep.TIME, WizardStep.SEND)

/** Next is enabled when the step is complete. The Send step has no Next. */
fun canGoNext(step: WizardStep, found: Boolean, inputsValid: Boolean, timeDone: Boolean): Boolean = when (step) {
    WizardStep.CONNECT -> found
    WizardStep.INPUTS -> inputsValid
    WizardStep.TIME -> timeDone
    WizardStep.SEND -> false
}

/** The steps after Connect need the display: if the connection ends while nothing is sent, back to [connect]. */
fun stepAfterConnection(current: Int, connect: Int, found: Boolean, idle: Boolean): Int =
    if (!found && idle && current > connect) connect else current

/**
 * The list "Displays nearby" shows while no display is found, and also when the connected card got another tab's
 * program last time (the wrong card). It hides for the right card, and for an unknown card (the app cannot know).
 */
fun showNearby(found: Boolean, cardTab: DisplayTab?, tab: DisplayTab): Boolean = !found || (cardTab != null && cardTab != tab)
