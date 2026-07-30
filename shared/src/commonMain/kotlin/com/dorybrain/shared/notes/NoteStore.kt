package com.dorybrain.shared.notes

import com.dorybrain.shared.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for notes. Android backs this with Room; desktop backs
 * it with SQLite over JDBC. Everything above this line is shared.
 */
interface NoteStore {
    fun observeAll(): Flow<List<Note>>

    fun observeById(id: Long): Flow<Note?>

    /** Returns the id assigned to the stored note. */
    suspend fun insert(note: Note): Long

    suspend fun update(note: Note)

    suspend fun delete(note: Note)

    suspend fun getById(id: Long): Note?
}
