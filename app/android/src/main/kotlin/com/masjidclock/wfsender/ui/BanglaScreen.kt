package com.masjidclock.wfsender.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.BanglaViewModel
import com.masjidclock.wfsender.bangla.BanglaText
import com.masjidclock.wfsender.bangla.banglaSummary
import com.masjidclock.wfsender.hijri.HijriInput
import java.time.LocalDate
import kotlinx.coroutines.delay

/** The Bangla tab: the Bangla year, then the shared Connect, Set the time, and Send steps. */
@Composable
internal fun BanglaScreen(model: BanglaViewModel) {
    val yearText by model.yearText.collectAsStateWithLifecycle()
    val fromCard by model.valuesFromCard.collectAsStateWithLifecycle()
    val year = HijriInput.parseYear(yearText)
    DisplayTabScreen(model, inputsValid = year != null) { state, sending ->
        StepCard(1, "Bangla year", state) {
            OutlinedTextField(
                value = yearText,
                onValueChange = model::setYearText,
                label = { Text("Bangla year") },
                singleLine = true,
                enabled = !sending,
                isError = yearText.isNotEmpty() && year == null,
                supportingText = if (yearText.isNotEmpty() && year == null) {
                    { Text(BanglaText.YEAR_ERROR) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (fromCard) Text(CARD_VALUES_NOTE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (year != null) BanglaSummary(year)
        }
    }
}

/** The date that the display shows today, and a warning if the year does not contain today. */
@Composable
private fun BanglaSummary(year: Int) {
    // The date changes at 00:00: update the day every minute.
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            today = LocalDate.now()
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val summary = banglaSummary(year, today)
            val date = summary.date
            if (date != null) {
                Text(
                    "The display shows: ${date.day} ${date.month} ${date.year}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(BanglaText.CHANGE_TIME, style = MaterialTheme.typography.bodyMedium)
            }
            summary.warning?.let { StatusLine(it, ok = false) }
        }
    }
}
