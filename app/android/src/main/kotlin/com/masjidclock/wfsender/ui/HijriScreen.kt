package com.masjidclock.wfsender.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.HijriViewModel
import com.masjidclock.wfsender.hijri.HijriInput
import com.masjidclock.wfsender.hijri.HijriMonths
import com.masjidclock.wfsender.hijri.HijriStart
import com.masjidclock.wfsender.hijri.LowerHalf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val DATE_TEXT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
private val TIME_TEXT = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** The Hijri tab: the 3 inputs, then the shared Connect, Set the time, and Send steps. */
@Composable
internal fun HijriScreen(model: HijriViewModel) {
    val values by model.values.collectAsStateWithLifecycle()
    val fromCard by model.valuesFromCard.collectAsStateWithLifecycle()
    val year = HijriInput.parseYear(values.yearText)
    val month = values.month
    DisplayTabScreen(model, inputsValid = month != null && year != null, inputTitle = "Hijri month") { sending ->
        DateField(values.monthStart, enabled = !sending, onChange = model::setDate)
        MonthField(month, enabled = !sending, onChange = model::setMonth)
        OutlinedTextField(
            value = values.yearText,
            onValueChange = model::setYearText,
            label = { Text("Hijri year") },
            singleLine = true,
            enabled = !sending,
            isError = values.yearText.isNotEmpty() && year == null,
            supportingText = if (values.yearText.isNotEmpty() && year == null) {
                { Text(HijriInput.YEAR_ERROR) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Lower half", style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.selectableGroup()) {
            for (lower in LowerHalf.entries) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = values.lowerHalf == lower,
                        enabled = !sending,
                        role = Role.RadioButton,
                        onClick = { model.setLowerHalf(lower) },
                    ).padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = values.lowerHalf == lower, onClick = null, enabled = !sending)
                    Text(lower.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (fromCard) Text(CARD_VALUES_NOTE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Summary(values.monthStart, month, year)
    
    }
}

/** The month start date. The field opens the Material 3 date picker, which uses UTC midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, enabled: Boolean, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        IconLabel(Icons.Filled.DateRange, "Month start date: ${date.format(DATE_TEXT)}")
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = HijriInput.toPickerMillis(date))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(HijriInput.dateFromPicker(it)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthField(month: Int?, enabled: Boolean, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = month?.let { HijriMonths.NAMES[it - 1] } ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Hijri month") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            HijriMonths.NAMES.forEachIndexed { i, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onChange(i + 1)
                    expanded = false
                })
            }
        }
    }
}

/** What the display will show, and when day 1 starts (spec section 3). */
@Composable
private fun Summary(monthStart: LocalDate, month: Int?, year: Int?) {
    // The shown day number follows the phone clock: update it every 30 seconds.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (month != null && year != null) {
                val start = HijriStart.counterStart(monthStart, now.toLocalDate())
                val day = HijriInput.dayNumber(start, now)
                if (day != null) {
                    Text(
                        "The display shows: $day ${HijriMonths.NAMES[month - 1]} $year",
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    val dayOne = HijriInput.dayOneStart(start)
                    Text(
                        "Day 1 starts on ${dayOne.toLocalDate().format(DATE_TEXT)} at ${dayOne.format(TIME_TEXT)}.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    "The date changes every day at ${start.format(TIME_TEXT)} (Maghrib).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (HijriInput.dateIsFar(monthStart, now.toLocalDate())) StatusLine(HijriInput.DATE_WARNING, ok = false)
            }
        }
    }
}
