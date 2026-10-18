package com.masjidclock.wfsender

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.masjidclock.wfsender.ui.WfSenderApp

class MainActivity : ComponentActivity() {

    private val model: AppViewModel by viewModels()
    private val create: CreateViewModel by viewModels()
    private val time: TimeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge: the status-bar icons follow light or dark mode, and the Scaffold pads the
        // page below the top app bar and above the bottom navigation bar.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { WfSenderApp(model, create, time) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A package from "Open with" (ACTION_VIEW) or from the share sheet (ACTION_SEND) opens the Send page. */
    private fun handleIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        model.importPackage(uri)
    }
}
