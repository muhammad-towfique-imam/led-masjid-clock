package com.masjidclock.wfsender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 1 numbered step as a card. The content of a waiting step is hidden. */
@Composable
internal fun StepCard(number: Int, title: String, state: StepState, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state == StepState.CURRENT) colors.surfaceContainerHigh else colors.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepMark(number, state)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state == StepState.WAITING) colors.onSurfaceVariant else colors.onSurface,
                )
            }
            if (state != StepState.WAITING) content()
        }
    }
}

@Composable
private fun StepMark(number: Int, state: StepState) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        StepState.DONE -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Done",
            tint = colors.primary,
            modifier = Modifier.size(28.dp),
        )
        StepState.CURRENT -> Box(
            modifier = Modifier.size(28.dp).background(colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$number", color = colors.onPrimary, style = MaterialTheme.typography.labelLarge) }
        StepState.WAITING -> Box(
            modifier = Modifier.size(28.dp).border(1.5.dp, colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$number", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
    }
}

/** A status message with a check icon (ok) or a warning icon. */
@Composable
internal fun StatusLine(text: String, ok: Boolean) {
    val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** The content of a button with a leading icon. */
@Composable
internal fun IconLabel(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(label)
}

@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** The connected display: a check icon and the card name (or "Display found") in a larger font. */
@Composable
internal fun FoundLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

