package com.masjidclock.wfsender.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.TimeViewModel
import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.time.WallClock
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Settings as SettingsIcon

@Composable
fun TimeScreen(model: TimeViewModel, onBack: () -> Unit) {
    val connection by model.connection.collectAsStateWithLifecycle()
    val cardName by model.cardName.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val result by model.result.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(WallClock.now()) }
    var autoTime by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            now = WallClock.now()
            delay(1_000)
        }
    }
    // The checks run only while the tab is visible. The automatic-time setting is read again on return.
    LifecycleResumeEffect(Unit) {
        autoTime = Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 1) == 1
        model.startChecks()
        onPauseOrDispose { model.stopChecks() }
    }
    BackHandler { onBack() }

    val found = connection == ConnectionStatus.DISPLAY_FOUND
    val steps = timeSteps(found, timeSet = result?.ok == true)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepCard(1, "Connect to the display WiFi", steps.connect) {
            Text(Messages.WIFI_INSTRUCTIONS, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) {
                IconLabel(Icons.Filled.SettingsIcon, "Open WiFi settings")
            }
            val status = when (connection) {
                ConnectionStatus.NOT_CONNECTED -> Messages.NOT_CONNECTED
                ConnectionStatus.NO_DISPLAY -> Messages.NO_DISPLAY
                ConnectionStatus.DISPLAY_FOUND -> cardName?.let(Messages::displayFound) ?: Messages.DISPLAY_FOUND
            }
            StatusLine(status, ok = found)
        }

        StepCard(2, "Set the time", steps.set) {
            Text("Phone time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(WallClock.time(now), style = MaterialTheme.typography.displaySmall)
            Text(WallClock.date(now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!autoTime) StatusLine(Messages.TIME_MANUAL, ok = false)
            Button(onClick = model::setTime, enabled = found && !busy, modifier = Modifier.fillMaxWidth()) {
                IconLabel(Icons.Filled.DateRange, "Set the time")
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            result?.let { StatusLine(it.message, it.ok) }
        }
    }
}
