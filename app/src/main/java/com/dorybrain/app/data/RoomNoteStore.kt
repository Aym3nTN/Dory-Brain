package com.dorybrain.app.data

import com.dorybrain.shared.model.Note
import com.dorybrain.shared.notes.NoteStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Backs the shared [NoteStore] contract with Room. */
class RoomNoteStore(private val dao: NoteDao) : NoteStore {

    override fun observeAll(): Flow<List<Note>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<Note?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun insert(note: Note): Long =
        dao.insert(NoteEntity.fromDomain(note))

    override suspend fun update(note: Note) =
        dao.update(NoteEntity.fromDomain(note))

    override suspend fun delete(note: Note) =
        dao.delete(NoteEntity.fromDomain(note))

    override suspend fun getById(id: Long): Note? =
        dao.getById(id)?.toDomain()
}
