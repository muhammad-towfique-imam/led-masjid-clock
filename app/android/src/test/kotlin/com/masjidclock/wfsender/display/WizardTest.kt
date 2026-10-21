package com.masjidclock.wfsender.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardTest {

    @Test
    fun stepsOfTheTabs() {
        assertEquals(listOf(WizardStep.CONNECT, WizardStep.TIME, WizardStep.SEND), wizardSteps(hasInputs = false))
        assertEquals(
            listOf(WizardStep.CONNECT, WizardStep.INPUTS, WizardStep.TIME, WizardStep.SEND),
            wizardSteps(hasInputs = true),
        )
    }

    @Test
    fun nextNeedsTheStepComplete() {
        assertFalse(canGoNext(WizardStep.CONNECT, found = false, inputsValid = true, timeDone = true))
        assertTrue(canGoNext(WizardStep.CONNECT, found = true, inputsValid = false, timeDone = false))
        assertFalse(canGoNext(WizardStep.INPUTS, found = true, inputsValid = false, timeDone = true))
        assertTrue(canGoNext(WizardStep.INPUTS, found = true, inputsValid = true, timeDone = false))
        assertFalse(canGoNext(WizardStep.TIME, found = true, inputsValid = true, timeDone = false))
        assertTrue(canGoNext(WizardStep.TIME, found = true, inputsValid = true, timeDone = true))
        assertFalse(canGoNext(WizardStep.SEND, found = true, inputsValid = true, timeDone = true))
    }

    @Test
    fun aLostConnectionGoesBackToConnect() {
        assertEquals(0, stepAfterConnection(current = 2, found = false, idle = true))
        assertEquals(2, stepAfterConnection(current = 2, found = true, idle = true))
        // During a send or with a result, the step does not change.
        assertEquals(3, stepAfterConnection(current = 3, found = false, idle = false))
        assertEquals(0, stepAfterConnection(current = 0, found = false, idle = true))
    }
}
