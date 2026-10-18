package com.masjidclock.wfsender

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.masjidclock.wfsender.ui.WfSenderApp

class MainActivity : ComponentActivity() {

    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 draws apps edge to edge. Dark icons on a transparent bar keep the status bar visible
        // on the light page; the page itself is padded below the status bar (safeDrawingPadding).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { WfSenderApp(model) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A ZIP from "Open with" (ACTION_VIEW) or from the share sheet (ACTION_SEND). */
    private fun handleIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        model.importZip(uri)
    }
}
