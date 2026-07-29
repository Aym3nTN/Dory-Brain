package com.dorybrain.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dorybrain.app.data.SettingsStore
import com.dorybrain.app.data.nvidia.NvidiaConnectionTester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.dorybrain.app.ui.theme.ThemeMode

/** Result of the "Test Connection" row. */
sealed interface ConnectionState {
    data object Untested : ConnectionState
    data object Testing : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

data class SettingsUiState(
    val maskedApiKey: String? = null,
    val hasApiKey: Boolean = false,
    val model: String = SettingsStore.DEFAULT_MODEL,
    val forceOnDevice: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val connectionTester: NvidiaConnectionTester
) : ViewModel() {

    private val _uiState = MutableStateFlow(readSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Untested)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Drives the app theme, so it also lives outside the settings screen. */
    val themeMode: StateFlow<ThemeMode> = uiState
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.themeMode)

    private fun readSettings() = SettingsUiState(
        maskedApiKey = settingsStore.maskedApiKey(),
        hasApiKey = settingsStore.hasApiKey,
        model = settingsStore.model,
        forceOnDevice = settingsStore.forceOnDevice,
        themeMode = settingsStore.themeMode
    )

    fun setApiKey(key: String) {
        settingsStore.apiKey = key.trim().ifBlank { null }
        _connectionState.value = ConnectionState.Untested
        _uiState.value = readSettings()
    }

    fun setModel(model: String) {
        settingsStore.model = model.ifBlank { SettingsStore.DEFAULT_MODEL }
        _connectionState.value = ConnectionState.Untested
        _uiState.value = readSettings()
    }

    fun setForceOnDevice(enabled: Boolean) {
        settingsStore.forceOnDevice = enabled
        _uiState.value = readSettings()
    }

    fun setThemeMode(mode: ThemeMode) {
        settingsStore.themeMode = mode
        _uiState.value = readSettings()
    }

    fun testConnection() {
        if (_connectionState.value is ConnectionState.Testing) return

        _connectionState.value = ConnectionState.Testing
        viewModelScope.launch {
            _connectionState.value = when (val result = connectionTester.test()) {
                NvidiaConnectionTester.Result.Connected -> ConnectionState.Connected
                NvidiaConnectionTester.Result.NoApiKey ->
                    ConnectionState.Failed("No API key saved yet.")
                is NvidiaConnectionTester.Result.Failed -> ConnectionState.Failed(result.message)
            }
        }
    }

    class Factory(
        private val settingsStore: SettingsStore,
        private val connectionTester: NvidiaConnectionTester
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(settingsStore, connectionTester) as T
        }
    }
}
