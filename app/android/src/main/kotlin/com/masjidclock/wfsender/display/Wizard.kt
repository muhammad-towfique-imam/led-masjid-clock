package com.masjidclock.wfsender.display

/** The steps of a display tab, shown 1 at a time with Prev and Next. */
enum class WizardStep { CONNECT, INPUTS, TIME, SEND }

/** Connect first; the inputs (Bangla, Hijri) after the connection, so that a card's saved values show first. */
fun wizardSteps(hasInputs: Boolean): List<WizardStep> =
    if (hasInputs) listOf(WizardStep.CONNECT, WizardStep.INPUTS, WizardStep.TIME, WizardStep.SEND)
    else listOf(WizardStep.CONNECT, WizardStep.TIME, WizardStep.SEND)

/** Next is enabled when the step is complete. The Send step has no Next. */
fun canGoNext(step: WizardStep, found: Boolean, inputsValid: Boolean, timeDone: Boolean): Boolean = when (step) {
    WizardStep.CONNECT -> found
    WizardStep.INPUTS -> inputsValid
    WizardStep.TIME -> timeDone
    WizardStep.SEND -> false
}

/** The later steps need the display: if the connection ends while nothing is sent, back to Connect. */
fun stepAfterConnection(current: Int, found: Boolean, idle: Boolean): Int = if (!found && idle) 0 else current

/**
 * The list "Displays nearby" shows while no display is found, and also when the connected card got another tab's
 * program last time (the wrong card). It hides for the right card, and for an unknown card (the app cannot know).
 */
fun showNearby(found: Boolean, cardTab: DisplayTab?, tab: DisplayTab): Boolean = !found || (cardTab != null && cardTab != tab)
