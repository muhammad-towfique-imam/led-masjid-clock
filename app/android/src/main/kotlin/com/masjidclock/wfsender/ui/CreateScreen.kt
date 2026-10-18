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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    var name by rememberSaveable { mutableStateOf("") }
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
            val clean = PackageName.clean(name)
            val invalid = name.isNotBlank() && clean == null
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("File name") },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(Messages.NAME_RULE) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { clean?.let(model::save) }, enabled = clean != null) {
                    IconLabel(Icons.Filled.Done, "Save")
                }
                OutlinedButton(
                    onClick = {
                        clean?.let { packageName ->
                            scope.launch { model.shareFile(packageName)?.let { uri -> context.startActivity(shareIntent(uri)) } }
                        }
                    },
                    enabled = clean != null,
                ) { IconLabel(Icons.Filled.Share, "Share") }
            }
            result?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun shareIntent(uri: Uri): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/octet-stream")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri("", uri)
    return Intent.createChooser(send, "Share package")
}
