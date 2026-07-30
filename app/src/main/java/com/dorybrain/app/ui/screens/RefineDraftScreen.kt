package com.dorybrain.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dorybrain.app.ui.DraftRefineState
import com.dorybrain.app.ui.NoteListViewModel
import com.dorybrain.shared.refine.RefineMode
import com.dorybrain.shared.ui.components.AppCard

/**
 * Shows a rewrite of the current draft next to what was actually said, so the
 * AI's version is accepted deliberately rather than silently applied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefineDraftScreen(
    viewModel: NoteListViewModel,
    onSaved: () -> Unit,
    onEditManually: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.draftRefineState.collectAsState()

    // If refining failed, the view model has reported why and gone back to
    // Idle — there's nothing to show here, so return to the draft.
    LaunchedEffect(state) {
        if (state is DraftRefineState.Idle) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Refine with AI", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val original = when (state) {
                is DraftRefineState.Working -> (state as DraftRefineState.Working).original
                is DraftRefineState.Ready -> (state as DraftRefineState.Ready).original
                DraftRefineState.Idle -> ""
            }

            OriginalCard(text = original)

            Box(modifier = Modifier.height(16.dp))

            when (val current = state) {
                is DraftRefineState.Ready -> RefinedCard(
                    refined = current.refined,
                    mode = current.mode
                )

                else -> RefiningCard()
            }

            Box(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val ready = state as? DraftRefineState.Ready ?: return@Button
                    viewModel.addNote(ready.refined)
                    viewModel.clearDraftRefinement()
                    onSaved()
                },
                enabled = state is DraftRefineState.Ready,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("  Save Note", style = MaterialTheme.typography.labelLarge)
            }

            TextButton(
                onClick = {
                    val ready = state as? DraftRefineState.Ready ?: return@TextButton
                    viewModel.returnDraftForEditing(ready.refined)
                    onEditManually()
                },
                enabled = state is DraftRefineState.Ready,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text("Edit Manually", style = MaterialTheme.typography.labelLarge)
            }

            PrivacyFooter(modifier = Modifier.padding(top = 20.dp, bottom = 28.dp))
        }
    }
}

@Composable
private fun OriginalCard(text: String) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Original (as you said)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun RefinedCard(refined: String, mode: RefineMode) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Refined by AI",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = refined,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    // Derived from the mode rather than hardcoded, so the
                    // claim stays true if other modes reach this screen.
                    text = describe(mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun RefiningCard() {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                "Refining with AI...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrivacyFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            // Deliberately not claiming an on-device option: sorting can run
            // locally, refining always goes to NVIDIA.
            text = "Refining is optional. When you use it, this note's text is sent to " +
                "NVIDIA to be rewritten. Sorting can stay on-device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
    }
}

private fun describe(mode: RefineMode) = when (mode) {
    RefineMode.CLEAN_UP -> "AI improved clarity and formatting."
    RefineMode.CHECKLIST -> "AI turned this into a checklist."
    RefineMode.SHORTEN -> "AI made this more concise."
    RefineMode.EXPAND -> "AI made this clearer and more specific."
}
