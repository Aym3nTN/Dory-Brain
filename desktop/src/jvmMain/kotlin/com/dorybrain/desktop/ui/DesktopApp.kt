package com.dorybrain.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dorybrain.desktop.DesktopStore
import com.dorybrain.desktop.DesktopTab
import com.dorybrain.shared.ui.theme.DoryBrainTheme

@Composable
fun DesktopApp(store: DesktopStore) {
    val themeMode by store.themeMode.collectAsState()
    val tab by store.tab.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        store.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    DoryBrainTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                    SideRail(current = tab, onSelect = store::selectTab)
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (tab) {
                            DesktopTab.HOME -> HomeView(store)
                            DesktopTab.NOTES -> NotesPane(store)
                            DesktopTab.SETTINGS -> SettingsView(store)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Notes either shows the list or, once a note is picked, its detail — the
 * desktop stand-in for the phone's push navigation.
 */
@Composable
private fun NotesPane(store: DesktopStore) {
    val selected by store.selectedNote.collectAsState()

    val current = selected
    if (current == null) {
        NotesView(store)
    } else {
        NoteDetailView(store = store, note = current)
    }
}

@Composable
private fun SideRail(current: DesktopTab, onSelect: (DesktopTab) -> Unit) {
    val items = listOf(
        DesktopTab.HOME to Icons.Filled.Home,
        DesktopTab.NOTES to (Icons.Outlined.Description as ImageVector),
        DesktopTab.SETTINGS to Icons.Filled.Settings
    )

    NavigationRail(
        modifier = Modifier.width(92.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = "Dory\nBrain",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        items.forEach { (destination, icon) ->
            NavigationRailItem(
                selected = current == destination,
                onClick = { onSelect(destination) },
                icon = { Icon(icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
