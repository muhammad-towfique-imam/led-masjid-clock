package com.masjidclock.wfsender.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
    // Create reads the USB drive with "All files access", which exists from Android 11 (spec section 5.5).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        CreatePage(model, onBack)
    } else {
        BackHandler { onBack() }
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageTitle("Create a package", onBack)
            Text(Messages.CREATE_NEEDS_ANDROID_11)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun CreatePage(model: CreateViewModel, onBack: () -> Unit) {
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
    BackHandler { onBack() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle("Create a package", onBack)

        Step(1, "Allow file access", steps.access) {
            if (usb.status == UsbStatus.NO_PERMISSION) {
                Text(Messages.ALLOW_ACCESS)
                Button(onClick = { StorageAccess.openSettings(context) }) { Text("Allow file access") }
            }
        }
        HorizontalDivider()

        Step(2, "Connect the USB drive", steps.drive) {
            if (usb.status == UsbStatus.NO_DRIVE) Text(Messages.CONNECT_USB)
        }
        HorizontalDivider()

        Step(3, "Check the files", steps.files) {
            when (usb.status) {
                UsbStatus.NO_EXPORT -> Text(Messages.NO_EXPORT, color = RED)
                UsbStatus.TOO_LARGE -> Text(Messages.TOO_LARGE, color = RED)
                UsbStatus.FOUND -> {
                    val export = usb.export!!
                    Text(Messages.found(usb.volumeName, export.width, export.height), color = GREEN, fontWeight = FontWeight.Bold)
                }
                else -> Unit
            }
        }
        HorizontalDivider()

        Step(4, "Save or share", steps.output) {
            if (usb.status == UsbStatus.FOUND) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { naming = NameAction.SAVE }) { Text("Save") }
                    OutlinedButton(onClick = { naming = NameAction.SHARE }) { Text("Share") }
                }
            }
            result?.let { Text(it, fontWeight = FontWeight.Bold) }
        }
        HorizontalDivider()
        TextButton(onClick = {
            scope.launch {
                val report = model.storageReport()
                val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                context.startActivity(Intent.createChooser(share, "Share details"))
            }
        }) { Text("Share details (for support)") }
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
                )
                if (text.isNotBlank() && clean == null) Text(Messages.NAME_RULE, color = RED)
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
