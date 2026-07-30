package com.dorybrain.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dorybrain.desktop.DesktopStore
import com.dorybrain.desktop.RefineState
import com.dorybrain.shared.model.Category
import com.dorybrain.shared.model.Note
import com.dorybrain.shared.refine.RefineMode
import com.dorybrain.shared.ui.accentColor
import com.dorybrain.shared.ui.components.CategoryPill
import com.dorybrain.shared.ui.icon

@Composable
fun NoteDetailView(store: DesktopStore, note: Note) {
    val refineState by store.refineState.collectAsState()

    var isEditing by remember(note.id) { mutableStateOf(false) }
    var editText by remember(note.id) { mutableStateOf(note.text) }
    var refineMenuOpen by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val isRefining = (refineState as? RefineState.Working)?.noteId == note.id

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = store::closeNote) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to notes")
            }

            Box(modifier = Modifier.weight(1f))

            if (isEditing) {
                IconButton(
                    onClick = {
                        store.updateText(note, editText)
                        isEditing = false
                    },
                    enabled = editText.isNotBlank()
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Save changes")
                }
                TextButton(onClick = { isEditing = false }) { Text("Cancel") }
            } else {
                IconButton(
                    onClick = {
                        editText = note.text
                        isEditing = true
                    }
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit note")
                }

                Box {
                    IconButton(onClick = { refineMenuOpen = true }, enabled = !isRefining) {
                        if (isRefining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Rewrite with AI")
                        }
                    }
                    DropdownMenu(
                        expanded = refineMenuOpen,
                        onDismissRequest = { refineMenuOpen = false }
                    ) {
                        RefineMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    store.refine(note, mode)
                                    refineMenuOpen = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete note",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(top = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Box(modifier = Modifier.padding(bottom = 16.dp)) {
                CategoryPill(
                    label = note.category.label,
                    icon = note.category.icon,
                    accent = note.category.accentColor,
                    onClick = { categoryMenuOpen = true }
                )
                DropdownMenu(
                    expanded = categoryMenuOpen,
                    onDismissRequest = { categoryMenuOpen = false }
                ) {
                    Category.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.label) },
                            leadingIcon = {
                                Icon(
                                    category.icon,
                                    contentDescription = null,
                                    tint = category.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                store.setCategory(note, category)
                                categoryMenuOpen = false
                            }
                        )
                    }
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                Text(
                    text = note.text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = fullTimestamp(note.createdAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    (refineState as? RefineState.Proposal)?.takeIf { it.noteId == note.id }?.let { proposal ->
        AlertDialog(
            onDismissRequest = store::dismissRefinement,
            icon = {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(proposal.mode.label) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Before",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        proposal.originalText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                    )
                    Text(
                        "After",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        proposal.refinedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = store::acceptRefinement) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = store::dismissRefinement) { Text("Keep original") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        store.deleteNote(note)
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
