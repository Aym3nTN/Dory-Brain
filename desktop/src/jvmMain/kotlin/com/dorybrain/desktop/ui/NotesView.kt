package com.dorybrain.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorybrain.desktop.DesktopStore
import com.dorybrain.shared.model.Category
import com.dorybrain.shared.model.Note
import com.dorybrain.shared.ui.accentColor
import com.dorybrain.shared.ui.components.AccentCard
import com.dorybrain.shared.ui.components.IconTile
import com.dorybrain.shared.ui.icon

@Composable
fun NotesView(store: DesktopStore) {
    val notes by store.visibleNotes.collectAsState()
    val query by store.searchQuery.collectAsState()
    val filter by store.categoryFilter.collectAsState()
    var filterMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text(
            "Notes",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = store::setSearchQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search notes...") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { store.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Box {
                IconButton(onClick = { filterMenuOpen = true }) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = "Filter by bucket",
                        tint = if (filter != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                DropdownMenu(
                    expanded = filterMenuOpen,
                    onDismissRequest = { filterMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All buckets") },
                        onClick = {
                            store.setCategoryFilter(null)
                            filterMenuOpen = false
                        }
                    )
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
                                store.setCategoryFilter(category)
                                filterMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        if (filter != null) {
            Text(
                text = "Showing ${filter!!.label} only",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (query.isNotBlank() || filter != null) {
                        "Nothing matches that."
                    } else {
                        "No notes yet.\nCapture a thought from Home and it'll show up here."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val grouped = notes.groupBy { dayLabel(it.createdAt) }
                grouped.forEach { (day, notesForDay) ->
                    item(key = "day_$day") {
                        Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                    }
                    items(notesForDay, key = { it.id }) { note ->
                        Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                            NoteRow(note = note, onClick = { store.openNote(note.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, onClick: () -> Unit) {
    AccentCard(
        accent = note.category.accentColor,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(icon = note.category.icon, tint = note.category.accentColor, size = 32.dp)
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (note.isCategorizing) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = timeOfDay(note.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
