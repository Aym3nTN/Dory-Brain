package com.dorybrain.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dorybrain.shared.model.Category
import com.dorybrain.shared.model.Note

/**
 * Room's row shape. Kept separate from the shared [Note] domain model so the
 * shared module stays free of Android dependencies.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val category: Category,
    val createdAt: Long,
    val isCategorizing: Boolean = false,
    val isAutoCategorized: Boolean = true
) {
    fun toDomain(): Note = Note(
        id = id,
        text = text,
        category = category,
        createdAt = createdAt,
        isCategorizing = isCategorizing,
        isAutoCategorized = isAutoCategorized
    )

    companion object {
        fun fromDomain(note: Note): NoteEntity = NoteEntity(
            id = note.id,
            text = note.text,
            category = note.category,
            createdAt = note.createdAt,
            isCategorizing = note.isCategorizing,
            isAutoCategorized = note.isAutoCategorized
        )
    }
}
