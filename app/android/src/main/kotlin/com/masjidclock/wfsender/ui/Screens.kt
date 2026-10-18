package com.masjidclock.wfsender.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card as CardBox
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.AppViewModel
import com.masjidclock.wfsender.ConnectionStatus
import com.masjidclock.wfsender.SendState
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.site.Card
import com.masjidclock.wfsender.site.Site

private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

@Composable
fun WfSenderApp(model: AppViewModel) {
    val site by model.site.collectAsStateWithLifecycle()
    val importError by model.importError.collectAsStateWithLifecycle()
    val card by model.selectedCard.collectAsStateWithLifecycle()
    val connection by model.connection.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importZip(uri)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val selected = card
            if (selected == null) {
                SiteScreen(site, onOpenZip = { openZip.launch(ZIP_TYPES) }, onSelect = model::selectCard)
            } else {
                SendScreen(selected, connection, sendState, onSend = model::send, onBack = { model.selectCard(null) })
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
private fun SiteScreen(site: Site?, onOpenZip: () -> Unit, onSelect: (Card) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Masjid Clock Sender", style = MaterialTheme.typography.headlineSmall)
        if (site == null) {
            Text(Messages.NO_SITE)
        } else {
            Text("Site: ${site.name}", style = MaterialTheme.typography.titleMedium)
            Text("Select the display to update:")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(site.cards) { card ->
                    CardBox(modifier = Modifier.fillMaxWidth().clickable { onSelect(card) }) {
                        Text(Messages.cardRow(card), modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
        Button(onClick = onOpenZip, modifier = Modifier.fillMaxWidth()) { Text("Open ZIP") }
    }
}

@Composable
private fun SendScreen(
    card: Card,
    connection: ConnectionStatus,
    sendState: SendState,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }
    val sending = sendState is SendState.Sending

    BackHandler(enabled = true) { if (!sending) onBack() }
    if (sending) KeepScreenOn()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Messages.cardRow(card), style = MaterialTheme.typography.headlineSmall)
        when (sendState) {
            SendState.Idle -> {
                Text(Messages.WIFI_INSTRUCTIONS)
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) {
                    Text("Open WiFi settings")
                }
                val (status, color) = when (connection) {
                    ConnectionStatus.NOT_CONNECTED -> Messages.NOT_CONNECTED to Color(0xFFB00020)
                    ConnectionStatus.NO_DISPLAY -> Messages.NO_DISPLAY to Color(0xFFB00020)
                    ConnectionStatus.DISPLAY_FOUND -> Messages.DISPLAY_FOUND to Color(0xFF1B5E20)
                }
                Text(status, color = color, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { confirming = true },
                    enabled = connection == ConnectionStatus.DISPLAY_FOUND,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
            is SendState.Sending -> {
                if (sendState.total == 0) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Connecting to the display…")
                } else {
                    LinearProgressIndicator(
                        progress = { sendState.sent.toFloat() / sendState.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Sending… ${sendState.sent} of ${sendState.total}")
                }
                Text("Keep the phone near the display.")
            }
            is SendState.Finished -> {
                Text(
                    sendState.message,
                    color = if (sendState.success) Color(0xFF1B5E20) else Color(0xFFB00020),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sendState.log)
                    context.startActivity(Intent.createChooser(share, "Share log"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Share log") }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Send the program?") },
            text = { Text(Messages.confirmation(card)) },
            confirmButton = { TextButton(onClick = { confirming = false; onSend() }) { Text("Yes, send") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
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
