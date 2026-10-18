package com.masjidclock.wfsender.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.AppViewModel
import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.send.SendState

private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
private val GREEN = Color(0xFF1B5E20)
private val RED = Color(0xFFB00020)
private val GREY = Color(0xFF757575)

@Composable
fun WfSenderApp(model: AppViewModel) {
    val site by model.site.collectAsStateWithLifecycle()
    val importError by model.importError.collectAsStateWithLifecycle()
    val card by model.selectedCard.collectAsStateWithLifecycle()
    val connection by model.connection.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importZip(uri)
    }
    var confirming by remember { mutableStateOf(false) }
    val sending = sendState is SendState.Sending
    val loaded = site != null && card != null
    val steps = stepStates(loaded, connection, sendState)

    // During a send, the back button does nothing and the screen stays on.
    BackHandler(enabled = sending) {}
    if (sending) KeepScreenOn()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("HD Sender", style = MaterialTheme.typography.headlineSmall)

                Step(1, "Load file", steps.load) {
                    val shown = card
                    if (site == null || shown == null) {
                        Text(Messages.NO_SITE)
                    } else {
                        Text("${site!!.name}.zip", fontWeight = FontWeight.Bold)
                        Text("Display: ${Messages.cardRow(shown)}")
                    }
                    OutlinedButton(onClick = { openZip.launch(ZIP_TYPES) }, enabled = !sending) {
                        Text(if (site == null) "Load file" else "Load another file")
                    }
                }
                HorizontalDivider()

                Step(2, "Connect to the display WiFi", steps.connect) {
                    if (steps.connect != StepState.WAITING) {
                        Text(Messages.WIFI_INSTRUCTIONS)
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                            enabled = !sending,
                        ) { Text("Open WiFi settings") }
                        if (sendState == SendState.Idle) {
                            val (status, color) = when (connection) {
                                ConnectionStatus.NOT_CONNECTED -> Messages.NOT_CONNECTED to RED
                                ConnectionStatus.NO_DISPLAY -> Messages.NO_DISPLAY to RED
                                ConnectionStatus.DISPLAY_FOUND -> Messages.DISPLAY_FOUND to GREEN
                            }
                            Text("Status: $status", color = color, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider()

                Step(3, "Send", steps.send) {
                    when (val state = sendState) {
                        SendState.Idle -> Button(
                            onClick = { confirming = true },
                            enabled = loaded && connection == ConnectionStatus.DISPLAY_FOUND,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Send") }
                        is SendState.Sending -> {
                            if (state.total == 0) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("Connecting to the display…")
                            } else {
                                LinearProgressIndicator(
                                    progress = { state.sent.toFloat() / state.total },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("Sending… ${state.sent} of ${state.total}")
                            }
                            Text("Keep the phone near the display.")
                        }
                        is SendState.Finished -> {
                            Text(state.message, color = if (state.success) GREEN else RED, fontWeight = FontWeight.Bold)
                            Button(onClick = model::sendAgain, modifier = Modifier.fillMaxWidth()) { Text("Send again") }
                            OutlinedButton(onClick = {
                                val share = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, state.log)
                                context.startActivity(Intent.createChooser(share, "Share log"))
                            }, modifier = Modifier.fillMaxWidth()) { Text("Share log") }
                        }
                    }
                }
            }

            val selected = card
            if (confirming && selected != null) {
                AlertDialog(
                    onDismissRequest = { confirming = false },
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
                    title = { Text("Cannot open the ZIP file") },
                    text = { Text(message) },
                )
            }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, state: StepState, content: @Composable ColumnScope.() -> Unit) {
    val (mark, color) = when (state) {
        StepState.DONE -> "✔" to GREEN
        StepState.CURRENT -> "●" to MaterialTheme.colorScheme.primary
        StepState.WAITING -> "○" to GREY
    }
    Row {
        Text(mark, color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$number  $title",
                style = MaterialTheme.typography.titleMedium,
                color = if (state == StepState.WAITING) GREY else Color.Unspecified,
            )
            content()
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
