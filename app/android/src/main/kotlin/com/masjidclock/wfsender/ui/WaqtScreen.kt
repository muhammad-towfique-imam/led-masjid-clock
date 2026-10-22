package com.masjidclock.wfsender.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.WaqtViewModel
import com.masjidclock.wfsender.hijri.HijriInput
import com.masjidclock.wfsender.send.SendState
import com.masjidclock.wfsender.waqt.HourMinute
import com.masjidclock.wfsender.waqt.WaqtInput
import com.masjidclock.wfsender.waqt.WaqtPrayers
import com.masjidclock.wfsender.waqt.WaqtValues
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

/** The Waqt tab: the step "Prayer times" (6 cards), then the shared Connect, Set the time, and Send steps. */
@Composable
internal fun WaqtScreen(model: WaqtViewModel) {
    val values by model.values.collectAsStateWithLifecycle()
    val profiles by model.profiles.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    DisplayTabScreen(model, inputsValid = WaqtInput.isValid(values), inputTitle = "Prayer times") { sending ->
        // Each time the step opens, changes that already apply move to "Today".
        LaunchedEffect(Unit) { model.mergePast() }
        // [sending] is also true when the page is locked; a locked page can still select a profile.
        WaqtProfileField(model, profiles, canSelect = sendState !is SendState.Sending, canChange = !sending)
        for ((prayer, name) in WaqtPrayers.NAMES.withIndex()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Today", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyLarge)
                        TimeField(values.times[prayer], prayer, !sending, Modifier.weight(1f)) { model.setTime(prayer, it) }
                        // The width of the delete button of the change rows, so that the times line up.
                        Spacer(Modifier.size(48.dp))
                    }
                    for ((index, change) in values.changes.withIndex().filter { it.value.prayer == prayer }.sortedBy { it.value.date }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DayField(change.date, !sending, Modifier.weight(1.2f)) { model.setChange(index, change.copy(date = it)) }
                            TimeField(change.time, prayer, !sending, Modifier.weight(1f)) { model.setChange(index, change.copy(time = it)) }
                            IconButton(onClick = { model.removeChange(index) }, enabled = !sending) {
                                Icon(Icons.Filled.Close, contentDescription = "Delete the change")
                            }
                        }
                    }
                    TextButton(onClick = { model.addChange(prayer) }, enabled = !sending) { Text("+ Add a change") }
                }
            }
        }
        WaqtInput.timeConflict(values)?.let { StatusLine(it, ok = false) }
        WaqtSummary(values)
    }
}

@Composable
private fun WaqtSummary(values: WaqtValues) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalDateTime.now()
        }
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (line in WaqtInput.summary(values, now)) Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A 12-hour time; am/pm is fixed by the prayer. The picker's own am/pm choice is ignored. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(time: HourMinute?, prayer: Int, enabled: Boolean, modifier: Modifier, onChange: (HourMinute) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val period = WaqtPrayers.period(prayer)
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = modifier) {
        Text(time?.let { "${it.hour}:%02d $period".format(Locale.ROOT, it.minute) } ?: "Set")
    }
    if (open) {
        val start = time ?: HourMinute(12, 0)
        val state = rememberTimePickerState(
            initialHour = start.hour % 12 + if (period == "pm") 12 else 0,
            initialMinute = start.minute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    val h = state.hour % 12
                    onChange(HourMinute(if (h == 0) 12 else h, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayField(date: LocalDate, enabled: Boolean, modifier: Modifier, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = modifier) { Text(date.format(DAY)) }
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
