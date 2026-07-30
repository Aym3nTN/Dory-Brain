package com.dorybrain.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dorybrain.shared.settings.SettingsDefaults
import com.dorybrain.app.ui.ConnectionState
import com.dorybrain.app.ui.SettingsViewModel
import com.dorybrain.shared.ui.components.AppCard
import com.dorybrain.shared.ui.components.SectionHeader
import com.dorybrain.shared.ui.components.SettingsRow
import com.dorybrain.shared.ui.theme.Success
import com.dorybrain.shared.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val connection by viewModel.connectionState.collectAsState()

    var showKeyDialog by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionHeader(text = "NVIDIA API", modifier = Modifier.padding(top = 4.dp))

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

                    Row(modifier = Modifier.fillMaxWidth()) {
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
                                        viewModel.setModel(model)
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
                        onClick = viewModel::testConnection,
                        trailing = { ConnectionStatus(connection) }
                    )
                }
            }

            SectionHeader(text = "General", modifier = Modifier.padding(top = 24.dp))

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(
                        title = "Use On-device Sorting",
                        subtitle = "Always sort locally, even with a key saved",
                        trailing = {
                            Switch(
                                checked = state.forceOnDevice,
                                onCheckedChange = viewModel::setForceOnDevice
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
                                    TextButton(onClick = { viewModel.setThemeMode(mode) }) {
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

            SectionHeader(text = "About", modifier = Modifier.padding(top = 24.dp))

            AppCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    title = "Dory Brain",
                    subtitle = "Version ${com.dorybrain.app.BuildConfig.VERSION_NAME}"
                )
            }

            Text(
                text = "Your API key is stored encrypted on this device and is only sent to " +
                    "NVIDIA when sorting or rewriting a note.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
            )
        }
    }

    if (showKeyDialog) {
        ApiKeyDialog(
            initialValue = "",
            onSave = { key ->
                viewModel.setApiKey(key)
                showKeyDialog = false
            },
            onDismiss = { showKeyDialog = false }
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
            Text(
                text = "Connected",
                style = MaterialTheme.typography.labelLarge,
                color = Success
            )
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
                text = "Retry",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ApiKeyDialog(
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
