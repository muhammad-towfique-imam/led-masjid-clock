package com.masjidclock.wfsender.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

internal val GREEN = Color(0xFF1B5E20)
internal val RED = Color(0xFFB00020)
internal val GREY = Color(0xFF757575)

/** A page title with a Back button. */
@Composable
internal fun PageTitle(title: String, onBack: () -> Unit, backEnabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, enabled = backEnabled) { Text("‹ Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

/** 1 numbered step with its mark: ✔ done, ● current, ○ waiting. */
@Composable
internal fun Step(number: Int, title: String, state: StepState, content: @Composable ColumnScope.() -> Unit) {
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
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
