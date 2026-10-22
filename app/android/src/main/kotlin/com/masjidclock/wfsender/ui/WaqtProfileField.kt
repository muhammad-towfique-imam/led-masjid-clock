package com.masjidclock.wfsender.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.masjidclock.wfsender.WaqtViewModel
import com.masjidclock.wfsender.waqt.WaqtProfiles

/** What the name dialog does. */
private enum class ProfileAction { CREATE, DUPLICATE, RENAME, DELETE }

/**
 * The dropdown "Masjid": the profiles, then the actions on the selected profile. [canSelect]: no send runs.
 * [canChange]: no send runs and the inputs are not locked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WaqtProfileField(model: WaqtViewModel, profiles: WaqtProfiles, canSelect: Boolean, canChange: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<ProfileAction?>(null) }
    val selected = profiles.selected
    ExposedDropdownMenuBox(expanded = expanded && canSelect, onExpandedChange = { if (canSelect) expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = canSelect,
            label = { Text("Masjid") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, canSelect),
        )
        ExposedDropdownMenu(expanded = expanded && canSelect, onDismissRequest = { expanded = false }) {
            for (profile in profiles.profiles) {
                val isSelected = profile.name == selected
                DropdownMenuItem(
                    text = { Text(if (isSelected) "✓ ${profile.name}" else "   ${profile.name}", fontWeight = if (isSelected) FontWeight.Bold else null) },
                    onClick = {
                        model.selectProfile(profile.name)
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            fun item(text: String, next: ProfileAction, enabled: Boolean = canChange) = @Composable {
                DropdownMenuItem(text = { Text(text) }, enabled = enabled, onClick = {
                    action = next
                    expanded = false
                })
            }
            item("+ New profile", ProfileAction.CREATE)()
            item("Copy \"$selected\"", ProfileAction.DUPLICATE)()
            item("Rename \"$selected\"", ProfileAction.RENAME)()
            item("Delete \"$selected\"", ProfileAction.DELETE, canChange && profiles.profiles.size > 1)()
        }
    }
    when (val a = action) {
        null -> Unit
        ProfileAction.DELETE -> AlertDialog(
            onDismissRequest = { action = null },
            title = { Text("Delete \"$selected\"?") },
            text = { Text("The times and the changes of this profile are lost.") },
            confirmButton = {
                TextButton(onClick = {
                    model.deleteProfile(selected)
                    action = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { action = null }) { Text("Cancel") } },
        )
        else -> NameDialog(profiles, a, onDone = { action = null }) { name ->
            when (a) {
                ProfileAction.CREATE -> model.createProfile(name)
                ProfileAction.DUPLICATE -> model.duplicateProfile(name)
                else -> model.renameProfile(selected, name)
            }
        }
    }
}

@Composable
private fun NameDialog(profiles: WaqtProfiles, action: ProfileAction, onDone: () -> Unit, onName: (String) -> Unit) {
    val selected = profiles.selected
    val (title, button, start) = when (action) {
        ProfileAction.CREATE -> Triple("New profile", "Create", "")
        ProfileAction.DUPLICATE -> Triple("Copy \"$selected\"", "Copy", "$selected (copy)")
        else -> Triple("Rename \"$selected\"", "Rename", selected)
    }
    var name by remember { mutableStateOf(start) }
    val error = profiles.nameError(name, except = if (action == ProfileAction.RENAME) selected else null)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                // An empty field is the normal start of "New profile": no message for it.
                if (error != null && name.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onName(name)
                onDone()
            }, enabled = error == null) { Text(button) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}
