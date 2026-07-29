package com.dorybrain.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dorybrain.app.data.Category
import com.dorybrain.app.data.Note
import com.dorybrain.app.data.refine.RefineMode
import com.dorybrain.app.ui.NoteListViewModel
import com.dorybrain.app.ui.RefineState
import com.dorybrain.app.ui.speech.rememberSpeechInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: NoteListViewModel,
    onOpenSettings: () -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    val refineState by viewModel.refineState.collectAsState()
    var draft by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Text spoken before this dictation run, so live partials can be appended
    // without clobbering what the user already typed.
    var draftBeforeDictation by remember { mutableStateOf("") }

    val speech = rememberSpeechInput(
        onFinalText = { spoken -> draft = appendSpoken(draftBeforeDictation, spoken) },
        onPartialText = { partial -> draft = appendSpoken(draftBeforeDictation, partial) },
        onError = { message -> viewModel.postMessage(message) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dory Brain") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            QuickCaptureBar(
                draft = draft,
                isListening = speech.isListening,
                onDraftChange = { draft = it },
                onToggleDictation = {
                    if (!speech.isListening) draftBeforeDictation = draft
                    speech.toggle()
                },
                onSubmit = {
                    viewModel.addNote(draft)
                    draft = ""
                    draftBeforeDictation = ""
                }
            )
            HorizontalDivider()

            if (notes.isEmpty()) {
                EmptyState()
            } else {
                NoteList(
                    notes = notes,
                    refineState = refineState,
                    onCategoryChange = viewModel::setCategory,
                    onRefine = viewModel::refine,
                    onDelete = viewModel::deleteNote
                )
            }
        }
    }

    (refineState as? RefineState.Proposal)?.let { proposal ->
        RefineReviewDialog(
            proposal = proposal,
            onAccept = viewModel::acceptRefinement,
            onDismiss = viewModel::dismissRefinement
        )
    }
}

/** Joins dictated text onto whatever was already in the field. */
private fun appendSpoken(existing: String, spoken: String): String =
    if (existing.isBlank()) spoken else "${existing.trimEnd()} $spoken"

@Composable
private fun QuickCaptureBar(
    draft: String,
    isListening: Boolean,
    onDraftChange: (String) -> Unit,
    onToggleDictation: () -> Unit,
    onSubmit: () -> Unit
) {
    val micTint by animateColorAsState(
        targetValue = if (isListening) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "micTint"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(if (isListening) "Listening..." else "Dump whatever's on your mind...")
            },
            maxLines = 4
        )

        IconButton(onClick = onToggleDictation) {
            Icon(
                imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (isListening) "Stop dictation" else "Dictate note",
                tint = micTint
            )
        }

        IconButton(onClick = onSubmit, enabled = draft.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Save note")
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Nothing here yet.\nType or dictate something above and it'll get sorted for you.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun NoteList(
    notes: List<Note>,
    refineState: RefineState,
    onCategoryChange: (Note, Category) -> Unit,
    onRefine: (Note, RefineMode) -> Unit,
    onDelete: (Note) -> Unit
) {
    val grouped = notes.groupBy { it.category }
        .toSortedMap(compareBy { it.ordinal })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grouped.forEach { (category, notesInCategory) ->
            item(key = "header_${category.name}") {
                Text(
                    text = "${category.label} (${notesInCategory.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(notesInCategory, key = { it.id }) { note ->
                val isRefining = (refineState as? RefineState.Working)?.noteId == note.id
                NoteRow(
                    note = note,
                    isRefining = isRefining,
                    onCategoryChange = { onCategoryChange(note, it) },
                    onRefine = { mode -> onRefine(note, mode) },
                    onDelete = { onDelete(note) }
                )
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    isRefining: Boolean,
    onCategoryChange: (Category) -> Unit,
    onRefine: (RefineMode) -> Unit,
    onDelete: () -> Unit
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var refineMenuExpanded by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = note.text, style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box {
                    AssistChip(
                        onClick = { categoryMenuExpanded = true },
                        label = { Text(note.category.label) }
                    )
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        Category.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.label) },
                                onClick = {
                                    onCategoryChange(category)
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (note.isCategorizing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }

                Text(
                    text = timeFormatter.format(Date(note.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { refineMenuExpanded = true },
                        enabled = !isRefining
                    ) {
                        if (isRefining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Rewrite with AI")
                        }
                    }
                    DropdownMenu(
                        expanded = refineMenuExpanded,
                        onDismissRequest = { refineMenuExpanded = false }
                    ) {
                        RefineMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    onRefine(mode)
                                    refineMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                }
            }
        }
    }
}

/** Shows the rewrite next to the original so nothing is replaced unseen. */
@Composable
private fun RefineReviewDialog(
    proposal: RefineState.Proposal,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                Text(
                    "After",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    proposal.refinedText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("Replace") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep original") }
        }
    )
}
