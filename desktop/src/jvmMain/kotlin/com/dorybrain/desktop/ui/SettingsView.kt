package com.dorybrain.desktop.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dorybrain.desktop.ConnectionState
import com.dorybrain.desktop.DesktopStore
import com.dorybrain.shared.settings.SettingsDefaults
import com.dorybrain.shared.settings.ThemeMode
import com.dorybrain.shared.ui.components.AppCard
import com.dorybrain.shared.ui.components.SectionHeader
import com.dorybrain.shared.ui.components.SettingsRow
import com.dorybrain.shared.ui.theme.Success

@Composable
fun SettingsView(store: DesktopStore) {
    val state by store.settingsState.collectAsState()
    val connection by store.connectionState.collectAsState()

    var showKeyDialog by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            SectionHeader(text = "NVIDIA API")

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(
                        title = "API Key",
                        subtitle = state.maskedApiKey ?: "Not set — using on-device sorting",
                        onClick = { showKeyDialog = true },
                        trailing = {
                            Text(
                                text = if (state.hasApiKey) "Change" else "Add",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    androidx.compose.foundation.layout.Box {
                        SettingsRow(
                            title = "Model",
                            subtitle = state.model,
                            onClick = { modelMenuOpen = true },
                            trailing = {
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = "Choose model",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = modelMenuOpen,
                            onDismissRequest = { modelMenuOpen = false }
                        ) {
                            SettingsDefaults.SUGGESTED_MODELS.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        store.setModel(model)
                                        modelMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    SettingsRow(
                        title = "Test Connection",
                        subtitle = (connection as? ConnectionState.Failed)?.message,
                        onClick = store::testConnection,
                        trailing = { ConnectionStatus(connection) }
                    )
                }
            }

            SectionHeader(text = "General", modifier = Modifier.padding(top = 24.dp))

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(
                        title = "Use on-device sorting",
                        subtitle = "Always sort locally, even with a key saved",
                        trailing = {
                            Switch(
                                checked = state.forceOnDevice,
                                onCheckedChange = store::setForceOnDevice
                            )
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    SettingsRow(
                        title = "Appearance",
                        subtitle = state.themeMode.label,
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ThemeMode.entries.forEach { mode ->
                                    TextButton(onClick = { store.setThemeMode(mode) }) {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (state.themeMode == mode) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Text(
                text = "Heads up: on desktop your API key is stored in a plain settings file " +
                    "readable only by your user account — it is not encrypted at rest the way " +
                    "it is on Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
            )
        }
    }

    if (showKeyDialog) {
        var key by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("NVIDIA API key") },
            text = {
                Column {
                    Text(
                        "Paste a key from build.nvidia.com. Leave it empty to clear the key and " +
                            "sort notes on-device only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        placeholder = { Text("nvapi-...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.setApiKey(key)
                        showKeyDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showKeyDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConnectionStatus(state: ConnectionState) {
    when (state) {
        ConnectionState.Untested -> Text(
            text = "Test",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        ConnectionState.Testing -> CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp
        )

        ConnectionState.Connected -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Success,
                modifier = Modifier.size(16.dp)
            )
            Text("Connected", style = MaterialTheme.typography.labelLarge, color = Success)
        }

        is ConnectionState.Failed -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Retry",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
