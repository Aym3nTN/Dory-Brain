package com.dorybrain.desktop

import com.dorybrain.desktop.data.AppPaths
import com.dorybrain.desktop.data.FileSettingsRepository
import com.dorybrain.desktop.data.SqliteNoteStore
import com.dorybrain.shared.categorize.CategorizerRepository
import com.dorybrain.shared.model.Category
import com.dorybrain.shared.model.Note
import com.dorybrain.shared.notes.NoteStore
import com.dorybrain.shared.nvidia.NvidiaConnectionTester
import com.dorybrain.shared.refine.RefineMode
import com.dorybrain.shared.refine.RefinerRepository
import com.dorybrain.shared.settings.SettingsDefaults
import com.dorybrain.shared.settings.SettingsRepository
import com.dorybrain.shared.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which pane the desktop window is showing. */
enum class DesktopTab(val label: String) {
    HOME("Home"),
    NOTES("Notes"),
    SETTINGS("Settings")
}

sealed interface RefineState {
    data object Idle : RefineState
    data class Working(val noteId: Long, val mode: RefineMode) : RefineState
    data class Proposal(
        val noteId: Long,
        val mode: RefineMode,
        val originalText: String,
        val refinedText: String
    ) : RefineState
}

sealed interface CaptureState {
    data object Editing : CaptureState
    data object Categorizing : CaptureState
    data class Saved(val category: Category) : CaptureState
}

sealed interface ConnectionState {
    data object Untested : ConnectionState
    data object Testing : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

data class SettingsUiState(
    val maskedApiKey: String?,
    val hasApiKey: Boolean,
    val model: String,
    val forceOnDevice: Boolean,
    val themeMode: ThemeMode
)

/**
 * Desktop's state holder. The domain work (storage, categorizing, rewriting)
 * all comes from `:shared`; this owns only the presentation state, which is
 * the one layer that can't be shared yet — `androidx.lifecycle.ViewModel`
 * has no multiplatform equivalent at the versions this project pins.
 */
class DesktopStore(private val scope: CoroutineScope) {

    private val settings: SettingsRepository = FileSettingsRepository(AppPaths.settingsFile)
    private val noteStore: NoteStore = SqliteNoteStore(AppPaths.databaseFile)
    private val categorizer = CategorizerRepository(settings)
    private val refiner = RefinerRepository(settings)
    private val connectionTester = NvidiaConnectionTester(settings)

    val notes: StateFlow<List<Note>> = noteStore.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow<Category?>(null)
    val categoryFilter: StateFlow<Category?> = _categoryFilter.asStateFlow()

    val visibleNotes: StateFlow<List<Note>> =
        combine(notes, _searchQuery, _categoryFilter) { all, query, filter ->
            all.asSequence()
                .filter { filter == null || it.category == filter }
                .filter { query.isBlank() || it.text.contains(query.trim(), ignoreCase = true) }
                .toList()
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val bucketCounts: StateFlow<Map<Category, Int>> = notes
        .map { all -> all.groupingBy { it.category }.eachCount() }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _tab = MutableStateFlow(DesktopTab.HOME)
    val tab: StateFlow<DesktopTab> = _tab.asStateFlow()

    private val _selectedNoteId = MutableStateFlow<Long?>(null)
    val selectedNote: StateFlow<Note?> =
        combine(notes, _selectedNoteId) { all, id ->
            id?.let { selected -> all.firstOrNull { it.id == selected } }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _refineState = MutableStateFlow<RefineState>(RefineState.Idle)
    val refineState: StateFlow<RefineState> = _refineState.asStateFlow()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Editing)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Untested)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _settingsState = MutableStateFlow(readSettings())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settingsState
        .map { it.themeMode }
        .stateIn(scope, SharingStarted.Eagerly, settings.themeMode)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    // ---- navigation ----

    fun selectTab(tab: DesktopTab) {
        _tab.value = tab
        if (tab != DesktopTab.NOTES) _selectedNoteId.value = null
    }

    fun openNote(id: Long) {
        _selectedNoteId.value = id
        _tab.value = DesktopTab.NOTES
    }

    fun closeNote() {
        _selectedNoteId.value = null
    }

    fun openBucket(category: Category) {
        _categoryFilter.value = category
        _selectedNoteId.value = null
        _tab.value = DesktopTab.NOTES
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: Category?) {
        _categoryFilter.value = category
    }

    fun postMessage(message: String) {
        _messages.tryEmit(message)
    }

    fun resetCapture() {
        _captureState.value = CaptureState.Editing
    }

    // ---- notes ----

    fun addNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        _captureState.value = CaptureState.Categorizing
        scope.launch {
            val id = noteStore.insert(
                Note(
                    text = trimmed,
                    category = Category.OTHER,
                    createdAt = System.currentTimeMillis(),
                    isCategorizing = true,
                    isAutoCategorized = true
                )
            )
            _captureState.value = CaptureState.Saved(applyAutoCategory(id, trimmed))
        }
    }

    fun setCategory(note: Note, category: Category) {
        scope.launch {
            noteStore.update(note.copy(category = category, isAutoCategorized = false))
        }
    }

    fun updateText(note: Note, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == note.text) return

        scope.launch {
            if (note.isAutoCategorized) {
                noteStore.update(note.copy(text = trimmed, isCategorizing = true))
                applyAutoCategory(note.id, trimmed)
            } else {
                noteStore.update(note.copy(text = trimmed))
            }
        }
    }

    fun deleteNote(note: Note) {
        scope.launch {
            noteStore.delete(note)
            if (_selectedNoteId.value == note.id) _selectedNoteId.value = null
        }
    }

    // ---- AI rewrite ----

    fun refine(note: Note, mode: RefineMode) {
        if (_refineState.value is RefineState.Working) return

        _refineState.value = RefineState.Working(note.id, mode)
        scope.launch {
            when (val result = refiner.refine(note.text, mode)) {
                is RefinerRepository.Result.Success -> {
                    if (result.refinedText.trim() == note.text.trim()) {
                        _refineState.value = RefineState.Idle
                        _messages.emit("Already looks good — nothing to change.")
                    } else {
                        _refineState.value = RefineState.Proposal(
                            noteId = note.id,
                            mode = mode,
                            originalText = note.text,
                            refinedText = result.refinedText
                        )
                    }
                }

                is RefinerRepository.Result.Failure -> {
                    _refineState.value = RefineState.Idle
                    _messages.emit(result.message)
                }

                RefinerRepository.Result.NoApiKey -> {
                    _refineState.value = RefineState.Idle
                    _messages.emit("Add an NVIDIA API key in Settings to rewrite notes.")
                }
            }
        }
    }

    fun acceptRefinement() {
        val proposal = _refineState.value as? RefineState.Proposal ?: return
        _refineState.value = RefineState.Idle

        scope.launch {
            val note = noteStore.getById(proposal.noteId) ?: return@launch
            if (note.text != proposal.originalText) {
                _messages.emit("That note changed — rewrite discarded.")
                return@launch
            }

            val rewritten = note.copy(text = proposal.refinedText)
            if (note.isAutoCategorized) {
                noteStore.update(rewritten.copy(isCategorizing = true))
                applyAutoCategory(note.id, proposal.refinedText)
            } else {
                noteStore.update(rewritten)
            }
        }
    }

    fun dismissRefinement() {
        if (_refineState.value is RefineState.Proposal) _refineState.value = RefineState.Idle
    }

    // ---- settings ----

    fun setApiKey(key: String) {
        settings.apiKey = key.trim().ifBlank { null }
        _connectionState.value = ConnectionState.Untested
        _settingsState.value = readSettings()
    }

    fun setModel(model: String) {
        settings.model = model.ifBlank { SettingsDefaults.MODEL }
        _connectionState.value = ConnectionState.Untested
        _settingsState.value = readSettings()
    }

    fun setForceOnDevice(enabled: Boolean) {
        settings.forceOnDevice = enabled
        _settingsState.value = readSettings()
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.themeMode = mode
        _settingsState.value = readSettings()
    }

    fun testConnection() {
        if (_connectionState.value is ConnectionState.Testing) return

        _connectionState.value = ConnectionState.Testing
        scope.launch {
            _connectionState.value = when (val result = connectionTester.test()) {
                NvidiaConnectionTester.Result.Connected -> ConnectionState.Connected
                NvidiaConnectionTester.Result.NoApiKey ->
                    ConnectionState.Failed("No API key saved yet.")
                is NvidiaConnectionTester.Result.Failed -> ConnectionState.Failed(result.message)
            }
        }
    }

    private fun readSettings() = SettingsUiState(
        maskedApiKey = settings.maskedApiKey(),
        hasApiKey = settings.hasApiKey,
        model = settings.model,
        forceOnDevice = settings.forceOnDevice,
        themeMode = settings.themeMode
    )

    private suspend fun applyAutoCategory(noteId: Long, text: String): Category {
        val category = categorizer.categorize(text)
        noteStore.getById(noteId)?.let { current ->
            noteStore.update(current.copy(category = category, isCategorizing = false))
        }
        return category
    }
}
