package com.masjidclock.wfsender.ui

import android.content.Intent
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
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
 * A display tab (English, Bangla, Hijri): the tab's own input step (if any), then the steps Connect, Set the time,
 * and Send. After a result, only the result shows. The checks run only while the tab is visible, and not after a
 * send (see DisplayViewModel.startChecks).
 */
@Composable
internal fun DisplayTabScreen(
    model: DisplayViewModel,
    inputsValid: Boolean,
    inputStep: (@Composable (state: StepState, sending: Boolean) -> Unit)? = null,
) {
    val connection by model.connection.collectAsStateWithLifecycle()
    val cardName by model.cardName.collectAsStateWithLifecycle()
    val cardTab by model.cardTab.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val timeStep by model.timeStep.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        model.startChecks()
        onPauseOrDispose { model.stopChecks() }
    }

    val sending = sendState is SendState.Sending
    val steps = stepStates(inputsValid, connection, timeStep.done, sendState)
    val first = if (inputStep == null) 1 else 2

    BackHandler(enabled = sending) {}
    if (sending) KeepScreenOn()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // After a result, only the result shows. "Send again" brings the steps back.
        if (sendState !is SendState.Finished) {
            inputStep?.invoke(steps.load, sending)

            StepCard(first, "Connect to the display WiFi", steps.connect) {
                Text(Messages.WIFI_INSTRUCTIONS, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                    enabled = !sending,
                ) { IconLabel(Icons.Filled.SettingsIcon, "Open WiFi settings") }
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

            StepCard(first + 1, "Set the time", steps.time) {
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
        }

        StepCard(first + 2, "Send", steps.send) {
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
    }
}
