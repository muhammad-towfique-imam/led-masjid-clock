package com.masjidclock.wfsender.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.CreateViewModel
import com.masjidclock.wfsender.pkg.PackageName
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.usb.StorageAccess
import com.masjidclock.wfsender.usb.UsbStatus
import kotlinx.coroutines.launch

private enum class NameAction { SAVE, SHARE }

@Composable
fun CreateScreen(model: CreateViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    // Create reads the USB drive with "All files access", which exists from Android 11 (spec section 5.5).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        CreatePage(model)
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(Messages.CREATE_NEEDS_ANDROID_11, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun CreatePage(model: CreateViewModel) {
    val usb by model.usb.collectAsStateWithLifecycle()
    val result by model.result.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf<NameAction?>(null) }
    val steps = createSteps(usb.status)

    // The checks run only while the page is visible; they start again when the person returns from Settings.
    LifecycleResumeEffect(Unit) {
        model.startChecks()
        onPauseOrDispose { model.stopChecks() }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepCard(1, "Allow file access", steps.access) {
            if (usb.status == UsbStatus.NO_PERMISSION) {
                Text(Messages.ALLOW_ACCESS, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { StorageAccess.openSettings(context) }) { IconLabel(Icons.Filled.Lock, "Allow file access") }
            }
        }

        StepCard(2, "Connect the USB drive", steps.drive) {
            if (usb.status == UsbStatus.NO_DRIVE) Text(Messages.CONNECT_USB, style = MaterialTheme.typography.bodyMedium)
        }

        StepCard(3, "Check the files", steps.files) {
            when (usb.status) {
                UsbStatus.NO_EXPORT -> StatusLine(Messages.NO_EXPORT, ok = false)
                UsbStatus.TOO_LARGE -> StatusLine(Messages.TOO_LARGE, ok = false)
                UsbStatus.FOUND -> {
                    val export = usb.export!!
                    StatusLine(Messages.found(usb.volumeName, export.width, export.height), ok = true)
                }
                else -> Unit
            }
        }

        StepCard(4, "Save or share", steps.output) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { naming = NameAction.SAVE }) { IconLabel(Icons.Filled.Done, "Save") }
                OutlinedButton(onClick = { naming = NameAction.SHARE }) { IconLabel(Icons.Filled.Share, "Share") }
            }
            result?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
        }
    }

    naming?.let { action ->
        NameDialog(
            onDismiss = { naming = null },
            onConfirm = { name ->
                naming = null
                when (action) {
                    NameAction.SAVE -> model.save(name)
                    NameAction.SHARE -> scope.launch {
                        model.shareFile(name)?.let { uri -> context.startActivity(shareIntent(uri)) }
                    }
                }
            },
        )
    }
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val clean = PackageName.clean(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("for example masjid-north-english") },
                    supportingText = { Text(".hdpkg is added automatically") },
                    isError = text.isNotBlank() && clean == null,
                )
                if (text.isNotBlank() && clean == null) {
                    Text(Messages.NAME_RULE, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { clean?.let(onConfirm) }, enabled = clean != null) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun shareIntent(uri: Uri): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/octet-stream")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri("", uri)
    return Intent.createChooser(send, "Share package")
}
