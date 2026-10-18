package com.masjidclock.wfsender.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.AppViewModel
import com.masjidclock.wfsender.CreateViewModel
import com.masjidclock.wfsender.Page
import com.masjidclock.wfsender.TimeViewModel
import com.masjidclock.wfsender.send.SendState
import kotlinx.coroutines.launch

/** The app frame: a top app bar, the page, and a bottom navigation bar with Send and Create. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WfSenderApp(model: AppViewModel, create: CreateViewModel, time: TimeViewModel) {
    val page by model.page.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val sending = sendState is SendState.Sending
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    HdSenderTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (page) {
                                Page.SEND -> "Send"
                                Page.CREATE -> "Create a package"
                                Page.TIME -> "Set the time"
                            }
                        )
                    },
                    actions = {
                        if (page == Page.CREATE) {
                            IconButton(onClick = {
                                scope.launch {
                                    val report = create.storageReport()
                                    val share = Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, report)
                                    context.startActivity(Intent.createChooser(share, "Share details"))
                                }
                            }) { Icon(Icons.Filled.Info, contentDescription = "Share details (for support)") }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = page == Page.SEND,
                        onClick = model::openSend,
                        enabled = !sending,
                        icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                        label = { Text("Send") },
                    )
                    NavigationBarItem(
                        selected = page == Page.CREATE,
                        onClick = model::openCreate,
                        enabled = !sending,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        label = { Text("Create") },
                    )
                    NavigationBarItem(
                        selected = page == Page.TIME,
                        onClick = model::openTime,
                        enabled = !sending,
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        label = { Text("Time") },
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when (page) {
                    Page.SEND -> SendScreen(model)
                    Page.CREATE -> CreateScreen(create, onBack = model::openSend)
                    Page.TIME -> TimeScreen(time, onBack = model::openSend)
                }
            }
        }
    }
}
