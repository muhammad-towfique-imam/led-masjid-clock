package com.masjidclock.wfsender.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onSend: () -> Unit, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("HD Sender", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onSend, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("Send", style = MaterialTheme.typography.titleLarge)
        }
        Text("Send a package to a display.")
        OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("Create", style = MaterialTheme.typography.titleLarge)
        }
        Text("Make a package from an HD2020 export on a USB drive.")
    }
}
