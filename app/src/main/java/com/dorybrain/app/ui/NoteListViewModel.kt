package com.dorybrain.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dorybrain.app.data.Category
import com.dorybrain.app.data.Note
import com.dorybrain.app.data.NoteDao
import com.dorybrain.app.data.categorize.CategorizerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteListViewModel(
    private val noteDao: NoteDao,
    private val categorizerRepository: CategorizerRepository
) : ViewModel() {

    val notes: StateFlow<List<Note>> = noteDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

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

            val category = categorizerRepository.categorize(trimmed)

            noteDao.getById(id)?.let { current ->
                noteDao.update(current.copy(category = category, isCategorizing = false))
            }
        }
    }

    fun setCategory(note: Note, category: Category) {
        viewModelScope.launch {
            noteDao.update(note.copy(category = category, isAutoCategorized = false))
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteDao.delete(note)
        }
    }

    class Factory(
        private val noteDao: NoteDao,
        private val categorizerRepository: CategorizerRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NoteListViewModel::class.java))
            return NoteListViewModel(noteDao, categorizerRepository) as T
        }
    }
}
