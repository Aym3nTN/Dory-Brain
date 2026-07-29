package com.dorybrain.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dorybrain.app.data.Category
import com.dorybrain.app.data.Note
import com.dorybrain.app.data.NoteDao
import com.dorybrain.app.data.categorize.CategorizerRepository
import com.dorybrain.app.data.refine.RefineMode
import com.dorybrain.app.data.refine.RefinerRepository
import kotlinx.coroutines.flow.Flow
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

/** Where a note is in the AI rewrite flow. */
sealed interface RefineState {
    data object Idle : RefineState

    data class Working(val noteId: Long, val mode: RefineMode) : RefineState

    /** A rewrite the user hasn't accepted yet — the note is untouched so far. */
    data class Proposal(
        val noteId: Long,
        val mode: RefineMode,
        val originalText: String,
        val refinedText: String
    ) : RefineState
}

/** Progress of the note being written on the compose screen. */
sealed interface CaptureState {
    data object Editing : CaptureState
    data object Categorizing : CaptureState
    data class Saved(val category: Category) : CaptureState
}

class NoteListViewModel(
    private val noteDao: NoteDao,
    private val categorizerRepository: CategorizerRepository,
    private val refinerRepository: RefinerRepository
) : ViewModel() {

    val notes: StateFlow<List<Note>> = noteDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow<Category?>(null)
    val categoryFilter: StateFlow<Category?> = _categoryFilter.asStateFlow()

    /** Notes after the Notes-screen search box and bucket filter are applied. */
    val visibleNotes: StateFlow<List<Note>> =
        combine(notes, _searchQuery, _categoryFilter) { all, query, filter ->
            all.asSequence()
                .filter { filter == null || it.category == filter }
                .filter { query.isBlank() || it.text.contains(query.trim(), ignoreCase = true) }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bucket -> note count, for the Home screen list. */
    val bucketCounts: StateFlow<Map<Category, Int>> = notes
        .map { all -> all.groupingBy { it.category }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _refineState = MutableStateFlow<RefineState>(RefineState.Idle)
    val refineState: StateFlow<RefineState> = _refineState.asStateFlow()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Editing)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun observeNote(id: Long): Flow<Note?> = noteDao.observeById(id)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: Category?) {
        _categoryFilter.value = category
    }

    /** Surfaces a one-off message (dictation trouble, API errors) in the UI. */
    fun postMessage(message: String) {
        _messages.tryEmit(message)
    }

    fun resetCapture() {
        _captureState.value = CaptureState.Editing
    }

    /**
     * Saves a dumped thought, then categorizes it. The compose screen watches
     * [captureState] so it can show the sorting step and the resulting bucket.
     */
    fun addNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        _captureState.value = CaptureState.Categorizing
        viewModelScope.launch {
            val id = noteDao.insert(
                Note(
                    text = trimmed,
                    category = Category.OTHER,
                    createdAt = System.currentTimeMillis(),
                    isCategorizing = true,
                    isAutoCategorized = true
                )
            )
            val category = applyAutoCategory(id, trimmed)
            _captureState.value = CaptureState.Saved(category)
        }
    }

    fun setCategory(note: Note, category: Category) {
        viewModelScope.launch {
            noteDao.update(note.copy(category = category, isAutoCategorized = false))
        }
    }

    fun updateText(note: Note, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == note.text) return

        viewModelScope.launch {
            if (note.isAutoCategorized) {
                noteDao.update(note.copy(text = trimmed, isCategorizing = true))
                applyAutoCategory(note.id, trimmed)
            } else {
                noteDao.update(note.copy(text = trimmed))
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteDao.delete(note)
        }
    }

    fun refine(note: Note, mode: RefineMode) {
        if (_refineState.value is RefineState.Working) return

        _refineState.value = RefineState.Working(note.id, mode)
        viewModelScope.launch {
            when (val result = refinerRepository.refine(note.text, mode)) {
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

    /** Commits a proposed rewrite. Only called from the review dialog. */
    fun acceptRefinement() {
        val proposal = _refineState.value as? RefineState.Proposal ?: return
        _refineState.value = RefineState.Idle

        viewModelScope.launch {
            val note = noteDao.getById(proposal.noteId) ?: return@launch
            // Bail out if the note changed underneath us while the dialog was open.
            if (note.text != proposal.originalText) {
                _messages.emit("That note changed — rewrite discarded.")
                return@launch
            }

            val rewritten = note.copy(text = proposal.refinedText)
            // Re-bucket the rewritten text, but never override a category the
            // user picked by hand.
            if (note.isAutoCategorized) {
                noteDao.update(rewritten.copy(isCategorizing = true))
                applyAutoCategory(note.id, proposal.refinedText)
            } else {
                noteDao.update(rewritten)
            }
        }
    }

    fun dismissRefinement() {
        if (_refineState.value is RefineState.Proposal) _refineState.value = RefineState.Idle
    }

    private suspend fun applyAutoCategory(noteId: Long, text: String): Category {
        val category = categorizerRepository.categorize(text)
        noteDao.getById(noteId)?.let { current ->
            noteDao.update(current.copy(category = category, isCategorizing = false))
        }
        return category
    }

    class Factory(
        private val noteDao: NoteDao,
        private val categorizerRepository: CategorizerRepository,
        private val refinerRepository: RefinerRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NoteListViewModel::class.java))
            return NoteListViewModel(noteDao, categorizerRepository, refinerRepository) as T
        }
    }
}
