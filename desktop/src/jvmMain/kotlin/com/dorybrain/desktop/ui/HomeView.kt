package com.dorybrain.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dorybrain.desktop.CaptureState
import com.dorybrain.desktop.DesktopStore
import com.dorybrain.shared.model.Category
import com.dorybrain.shared.ui.accentColor
import com.dorybrain.shared.ui.components.AccentCard
import com.dorybrain.shared.ui.components.AppCard
import com.dorybrain.shared.ui.components.IconTile
import com.dorybrain.shared.ui.components.SectionHeader
import com.dorybrain.shared.ui.icon
import kotlinx.coroutines.delay

@Composable
fun HomeView(store: DesktopStore) {
    val counts by store.bucketCounts.collectAsState()
    val captureState by store.captureState.collectAsState()
    var draft by remember { mutableStateOf("") }

    // Clear the field once the note has been filed, then drop the status card.
    LaunchedEffect(captureState) {
        if (captureState is CaptureState.Saved) {
            draft = ""
            delay(1600)
            store.resetCapture()
        }
    }

    val isSaving = captureState !is CaptureState.Editing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Text(
                    "What's on your mind?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("Dump whatever's on your mind...") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.medium,
                    enabled = !isSaving,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { store.addNote(draft) },
                        enabled = draft.isNotBlank() && !isSaving,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("  Save thought")
                    }
                }

                AnimatedVisibility(visible = isSaving) {
                    CategorizingCard(
                        state = captureState,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                SectionHeader(text = "Buckets", modifier = Modifier.padding(top = 20.dp))
            }
        }

        items(Category.entries.toList(), key = { it.name }) { category ->
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                BucketRow(
                    category = category,
                    count = counts[category] ?: 0,
                    onClick = { store.openBucket(category) }
                )
            }
        }
    }
}

@Composable
private fun CategorizingCard(state: CaptureState, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (state is CaptureState.Saved) "Sorted" else "Categorizing...",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            when (state) {
                is CaptureState.Saved -> Surface(
                    modifier = Modifier.padding(top = 12.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconTile(
                            icon = state.category.icon,
                            tint = state.category.accentColor,
                            size = 30.dp
                        )
                        Text(
                            text = "Saved to ${state.category.label}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                else -> Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "AI is sorting your thought",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BucketRow(category: Category, count: Int, onClick: () -> Unit) {
    AccentCard(
        accent = category.accentColor,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconTile(icon = category.icon, tint = category.accentColor)
            Text(
                text = category.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
