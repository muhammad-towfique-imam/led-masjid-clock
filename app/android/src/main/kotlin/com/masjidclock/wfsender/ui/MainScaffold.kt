package com.masjidclock.wfsender.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.unit.dp
import com.masjidclock.wfsender.BanglaViewModel
import com.masjidclock.wfsender.EnglishViewModel
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
import com.masjidclock.wfsender.HijriViewModel
import com.masjidclock.wfsender.Page
import com.masjidclock.wfsender.TimeViewModel
import com.masjidclock.wfsender.send.SendState
import kotlinx.coroutines.launch

/**
 * The app frame (display-tabs spec section 3): on a tab, a ≡ button opens the side menu (Send, Create, Time) and
 * the bottom bar shows English | Bangla | Hijri; on a menu page, a back arrow returns to the last tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WfSenderApp(
    model: AppViewModel,
    create: CreateViewModel,
    time: TimeViewModel,
    english: EnglishViewModel,
    bangla: BanglaViewModel,
    hijri: HijriViewModel,
) {
    val page by model.page.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val englishState by english.sendState.collectAsStateWithLifecycle()
    val banglaState by bangla.sendState.collectAsStateWithLifecycle()
    val hijriState by hijri.sendState.collectAsStateWithLifecycle()
    val sending = listOf(sendState, englishState, banglaState, hijriState).any { it is SendState.Sending }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)

    if (!page.isTab) BackHandler(enabled = !sending) { model.back() }

    HdSenderTheme {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = page.isTab && !sending,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        "Masjid Clock",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                    )
                    for ((item, icon, label) in listOf(
                        Triple(Page.SEND, Icons.AutoMirrored.Filled.Send, "Send"),
                        Triple(Page.CREATE, Icons.Filled.Add, "Create"),
                        Triple(Page.TIME, Icons.Filled.DateRange, "Time"),
                    )) {
                        NavigationDrawerItem(
                            label = { Text(label) },
                            icon = { Icon(icon, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawer.close() }
                                model.openMenuPage(item)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                when (page) {
                                    Page.ENGLISH -> "English display"
                                    Page.BANGLA -> "Bangla display"
                                    Page.HIJRI -> "Hijri month"
                                    Page.SEND -> "Send"
                                    Page.CREATE -> "Create a package"
                                    Page.TIME -> "Set the time"
                                }
                            )
                        },
                        navigationIcon = {
                            if (page.isTab) {
                                IconButton(onClick = { scope.launch { drawer.open() } }, enabled = !sending) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                }
                            } else {
                                IconButton(onClick = model::back, enabled = !sending) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
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
                    if (page.isTab) {
                        NavigationBar {
                            for ((tab, letter, label) in listOf(
                                Triple(Page.ENGLISH, "Aa", "English"),
                                Triple(Page.BANGLA, "অ", "Bangla"),
                                Triple(Page.HIJRI, "ع", "Hijri"),
                            )) {
                                NavigationBarItem(
                                    selected = page == tab,
                                    onClick = { model.openTab(tab) },
                                    enabled = !sending,
                                    icon = { Text(letter, style = MaterialTheme.typography.titleMedium) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    when (page) {
                        Page.ENGLISH -> EnglishScreen(english)
                        Page.BANGLA -> BanglaScreen(bangla)
                        Page.HIJRI -> HijriScreen(hijri)
                        Page.SEND -> SendScreen(model)
                        Page.CREATE -> CreateScreen(create, onBack = model::back)
                        Page.TIME -> TimeScreen(time, onBack = model::back)
                    }
                }
            }
        }
    }
}
