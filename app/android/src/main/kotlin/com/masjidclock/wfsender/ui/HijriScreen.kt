package com.masjidclock.wfsender.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.HijriViewModel
import com.masjidclock.wfsender.hijri.HijriInput
import com.masjidclock.wfsender.hijri.HijriMonths
import com.masjidclock.wfsender.hijri.HijriStart
import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.send.SendState
import com.masjidclock.wfsender.send.TimeStep
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val DATE_TEXT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
private val TIME_TEXT = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** The Hijri tab: the 3 inputs, then the same WiFi, time, and send steps as the Send page. */
@Composable
internal fun HijriScreen(model: HijriViewModel) {
    val values by model.values.collectAsStateWithLifecycle()
    val connection by model.connection.collectAsStateWithLifecycle()
    val cardName by model.cardName.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val timeStep by model.timeStep.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The checks run only while the tab is visible, and not after a send (see HijriViewModel.startChecks).
    LifecycleResumeEffect(Unit) {
        model.startChecks()
        onPauseOrDispose { model.stopChecks() }
    }

    val sending = sendState is SendState.Sending
    val year = HijriInput.parseYear(values.yearText)
    val month = values.month
    val inputsValid = month != null && year != null
    val steps = stepStates(inputsValid, connection, timeStep.done, sendState)

    BackHandler(enabled = sending) {}
    if (sending) KeepScreenOn()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepCard(1, "Hijri month", steps.load) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = values.showDivider,
                    enabled = !sending,
                    role = Role.Checkbox,
                    onValueChange = model::setShowDivider,
                ),
            ) {
                Checkbox(checked = values.showDivider, onCheckedChange = null, enabled = !sending)
                Text("Show the divider line", style = MaterialTheme.typography.bodyLarge)
            }
            Summary(values.monthStart, month, year)
        }

        StepCard(2, "Connect to the display WiFi", steps.connect) {
            Text(Messages.WIFI_INSTRUCTIONS, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                enabled = !sending,
            ) { IconLabel(Icons.Filled.SettingsIcon, "Open WiFi settings") }
            if (sendState == SendState.Idle) {
                when (connection) {
                    ConnectionStatus.NOT_CONNECTED -> StatusLine(Messages.NOT_CONNECTED, ok = false)
                    ConnectionStatus.NO_DISPLAY -> StatusLine(Messages.NO_DISPLAY, ok = false)
                    ConnectionStatus.DISPLAY_FOUND -> FoundLine(Messages.foundLabel(cardName))
                }
            }
        }

        StepCard(3, "Set the time", steps.time) {
            when (val step = timeStep) {
                is TimeStep.Set -> StatusLine(step.message, ok = true)
                TimeStep.Skipped -> Text("Skipped.", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    if (step is TimeStep.Failed) StatusLine(step.message, ok = false)
                    if (step == TimeStep.Busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = model::setTime,
                            enabled = step != TimeStep.Busy && !sending && connection == ConnectionStatus.DISPLAY_FOUND,
                        ) { IconLabel(Icons.Filled.DateRange, "Set the time") }
                        OutlinedButton(onClick = model::skipTime, enabled = step != TimeStep.Busy && !sending) {
                            Text("Skip")
                        }
                    }
                }
            }
        }

        StepCard(4, "Send", steps.send) {
            when (val state = sendState) {
                SendState.Idle -> Button(
                    onClick = model::send,
                    enabled = inputsValid && connection == ConnectionStatus.DISPLAY_FOUND && timeStep.done,
                    modifier = Modifier.fillMaxWidth(),
                ) { IconLabel(Icons.AutoMirrored.Filled.Send, "Send") }
                is SendState.Sending -> {
                    if (state.total == 0) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Connecting to the display…", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LinearProgressIndicator(
                            progress = { state.sent.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Sending… ${state.sent} of ${state.total}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Keep the phone near the display.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is SendState.Finished -> {
                    StatusLine(state.message, ok = state.success)
                    Button(onClick = model::sendAgain, modifier = Modifier.fillMaxWidth()) {
                        IconLabel(Icons.Filled.Refresh, "Send again")
                    }
                    OutlinedButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, state.log)
                        context.startActivity(Intent.createChooser(share, "Share log"))
                    }, modifier = Modifier.fillMaxWidth()) { IconLabel(Icons.Filled.Share, "Share log") }
                }
            }
        }
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
