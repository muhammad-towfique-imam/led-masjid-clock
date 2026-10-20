package com.masjidclock.wfsender.ui

import androidx.compose.runtime.Composable
import com.masjidclock.wfsender.EnglishViewModel

/** The English tab: no input; the shared Connect, Set the time, and Send steps. */
@Composable
internal fun EnglishScreen(model: EnglishViewModel) {
    DisplayTabScreen(model, inputsValid = true)
}
