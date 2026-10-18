package com.masjidclock.wfsender.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.AppViewModel
import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.send.SendState

/** The file picker shows all files: Android has no type for .hdpkg. The app checks the name. */
private val PACKAGE_TYPES = arrayOf("*/*")

@Composable
internal fun SendScreen(model: AppViewModel) {
    val site by model.site.collectAsStateWithLifecycle()
    val importError by model.importError.collectAsStateWithLifecycle()
    val card by model.selectedCard.collectAsStateWithLifecycle()
    val connection by model.connection.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openPackage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importPackage(uri)
    }
    var confirming by remember { mutableStateOf(false) }
    val sending = sendState is SendState.Sending
    val loaded = site != null && card != null
    val steps = stepStates(loaded, connection, sendState)

    // During a send, the back button does nothing and the screen stays on.
    BackHandler(enabled = sending) {}
    if (sending) KeepScreenOn()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepCard(1, "Load file", steps.load) {
            val shown = card
            if (site == null || shown == null) {
                Text(Messages.NO_SITE, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { openPackage.launch(PACKAGE_TYPES) }, enabled = !sending) {
                    IconLabel(Icons.Filled.Add, "Load file")
                }
            } else {
                Text("${site!!.name}.hdpkg", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Display: ${Messages.cardRow(shown)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { openPackage.launch(PACKAGE_TYPES) }, enabled = !sending) {
                    Text("Load another file")
                }
            }
        }

        StepCard(2, "Connect to the display WiFi", steps.connect) {
            Text(Messages.WIFI_INSTRUCTIONS, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                enabled = !sending,
            ) { IconLabel(Icons.Filled.Settings, "Open WiFi settings") }
            if (sendState == SendState.Idle) {
                val status = when (connection) {
                    ConnectionStatus.NOT_CONNECTED -> Messages.NOT_CONNECTED
                    ConnectionStatus.NO_DISPLAY -> Messages.NO_DISPLAY
                    ConnectionStatus.DISPLAY_FOUND -> Messages.DISPLAY_FOUND
                }
                StatusLine(status, ok = connection == ConnectionStatus.DISPLAY_FOUND)
            }
        }

        StepCard(3, "Send", steps.send) {
            when (val state = sendState) {
                SendState.Idle -> Button(
                    onClick = { confirming = true },
                    enabled = loaded && connection == ConnectionStatus.DISPLAY_FOUND,
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

    val selected = card
    if (confirming && selected != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
            title = { Text("Send the program?") },
            text = { Text(Messages.confirmation(selected)) },
            confirmButton = { TextButton(onClick = { confirming = false; model.send() }) { Text("Yes, send") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
    importError?.let { message ->
        AlertDialog(
            onDismissRequest = model::clearImportError,
            confirmButton = { TextButton(onClick = model::clearImportError) { Text("OK") } },
            title = { Text("Cannot open the file") },
            text = { Text(message) },
        )
    }
}
