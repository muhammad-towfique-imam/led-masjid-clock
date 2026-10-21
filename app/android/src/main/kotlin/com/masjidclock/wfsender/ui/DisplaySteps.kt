package com.masjidclock.wfsender.ui

import android.content.Intent
import android.os.Build
import android.content.Context
import android.content.ActivityNotFoundException
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.masjidclock.wfsender.display.wizardSteps
import com.masjidclock.wfsender.display.stepAfterConnection
import com.masjidclock.wfsender.display.canGoNext
import com.masjidclock.wfsender.display.showNearby
import com.masjidclock.wfsender.display.WizardStep
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Spacer
import com.masjidclock.wfsender.net.WifiScanner
import com.masjidclock.wfsender.net.ScanState
import com.masjidclock.wfsender.display.signalText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.DisplayViewModel
import com.masjidclock.wfsender.display.cardNote
import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.send.SendState
import com.masjidclock.wfsender.send.TimeStep

/**
 * A display tab (English, Bangla, Hijri, Waqt) as a wizard: 1 step at a time, with Prev and Next. The steps: the
 * tab's inputs (if any), Connect, Set the time, Send. The checks run only while the tab is visible, and not after a send (see
 * DisplayViewModel.startChecks).
 */
@Composable
internal fun DisplayTabScreen(
    model: DisplayViewModel,
    inputsValid: Boolean,
    inputTitle: String = "",
    inputStep: (@Composable (sending: Boolean) -> Unit)? = null,
) {
    val connection by model.connection.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val timeStep by model.timeStep.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        model.startChecks()
        onPauseOrDispose { model.stopChecks() }
    }

    val steps = wizardSteps(hasInputs = inputStep != null)
    var index by rememberSaveable { mutableIntStateOf(0) }
    val sending = sendState is SendState.Sending
    val idle = sendState == SendState.Idle
    val found = connection == ConnectionStatus.DISPLAY_FOUND
    // A send and its result belong to the Send step. A lost connection goes back to Connect.
    val connectIndex = steps.indexOf(WizardStep.CONNECT)
    LaunchedEffect(found, idle) { index = if (idle) stepAfterConnection(index, connectIndex, found, idle) else steps.lastIndex }
    val step = steps[index.coerceIn(0, steps.lastIndex)]

    BackHandler(enabled = sending) {}
    BackHandler(enabled = idle && index > 0) { index-- }
    if (sending) KeepScreenOn()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Step ${index + 1} of ${steps.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when (step) {
                WizardStep.CONNECT -> "Connect to the display WiFi"
                WizardStep.INPUTS -> inputTitle
                WizardStep.TIME -> "Set the time"
                WizardStep.SEND -> "Send"
            },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                WizardStep.CONNECT -> ConnectStep(model)
                WizardStep.INPUTS -> inputStep?.invoke(sending)
                WizardStep.TIME -> TimeStepContent(model)
                WizardStep.SEND -> SendStep(model, inputsValid) {
                    // "Done": back to step 1 (the inputs, or Connect on English) for the next display.
                    model.sendAgain()
                    index = 0
                }
            }
        }
        if (idle) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Prev") }
                if (step != WizardStep.SEND) {
                    Button(
                        onClick = { index++ },
                        enabled = canGoNext(step, found, inputsValid, timeStep.done),
                        modifier = Modifier.weight(1f),
                    ) { Text("Next") }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ConnectStep(model: DisplayViewModel) {
    val connection by model.connection.collectAsStateWithLifecycle()
    val cardName by model.cardName.collectAsStateWithLifecycle()
    val cardTab by model.cardTab.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val sending = sendState is SendState.Sending
    val context = LocalContext.current

    Text(Messages.WIFI_INSTRUCTIONS, style = MaterialTheme.typography.bodyMedium)
    val found = connection == ConnectionStatus.DISPLAY_FOUND
    if (sendState == SendState.Idle && showNearby(found, cardTab, model.tab)) NearbyList(model)
    OutlinedButton(
        onClick = { openWifi(context) },
        enabled = !sending,
    ) { IconLabel(Icons.Filled.SettingsIcon, "Connect") }
    if (sendState == SendState.Idle) {
        when (connection) {
            ConnectionStatus.NOT_CONNECTED -> StatusLine(Messages.NOT_CONNECTED, ok = false)
            ConnectionStatus.NO_DISPLAY -> StatusLine(Messages.NO_DISPLAY, ok = false)
            ConnectionStatus.DISPLAY_FOUND -> {
                FoundLine(Messages.foundLabel(cardName))
                cardNote(cardName, model.tab, cardTab)?.let { StatusLine(it.text, ok = !it.warning) }
            }
        }
    }
}

@Composable
private fun TimeStepContent(model: DisplayViewModel) {
    val connection by model.connection.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val timeStep by model.timeStep.collectAsStateWithLifecycle()
    val sending = sendState is SendState.Sending

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

@Composable
private fun SendStep(model: DisplayViewModel, inputsValid: Boolean, onSendAgain: () -> Unit) {
    val connection by model.connection.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val timeStep by model.timeStep.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            Button(onClick = onSendAgain, modifier = Modifier.fillMaxWidth()) {
                IconLabel(Icons.Filled.Check, "Done")
            }
            // The log is for support: only a failed send needs it.
            if (!state.success) {
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

/**
 * The WiFi selection: on Android 10 and later the WiFi panel over the app (user choice, 2026-10-21), else the WiFi
 * settings. The panel shows only the first few networks, and an app cannot filter it; the list "Displays nearby"
 * names the card, and the panel's link to all networks opens the full list.
 */
internal fun openWifi(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
    }
}

/** "Displays nearby": the card WiFi networks near the phone, the card of this tab first (spec of 2026-10-20). */
@Composable
private fun NearbyList(model: DisplayViewModel) {
    val nearby by model.nearby.collectAsStateWithLifecycle()
    val scanState by model.scanState.collectAsStateWithLifecycle()
    val summary by model.scanSummary.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.permissionChanged() }
    Text("Displays nearby", style = MaterialTheme.typography.titleSmall)
    when (scanState) {
        ScanState.NO_PERMISSION -> OutlinedButton(onClick = { ask.launch(WifiScanner.PERMISSIONS) }) {
            Text("Show displays nearby")
        }
        ScanState.APPROXIMATE -> {
            StatusLine("Allow precise location to see the displays nearby.", ok = false)
            OutlinedButton(onClick = { ask.launch(WifiScanner.PERMISSIONS) }) { Text("Allow precise location") }
        }
        ScanState.LOCATION_OFF -> StatusLine("Turn on location to see the displays nearby.", ok = false)
        ScanState.OK -> if (nearby.isEmpty()) {
            Text("No display found nearby.", style = MaterialTheme.typography.bodyMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column {
                for (display in nearby) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { openWifi(context) }.padding(vertical = 6.dp),
                    ) {
                        Text(
                            display.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (display.forThisTab) FontWeight.Bold else FontWeight.Normal,
                            color = if (display.forThisTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            signalText(display.level),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
