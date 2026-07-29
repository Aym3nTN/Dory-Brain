package com.dorybrain.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val category: Category,
    val createdAt: Long,
    /** True while a note is waiting on the categorizer to finish. */
    val isCategorizing: Boolean = false,
    /** True if [category] was set automatically and hasn't been corrected by hand. */
    val isAutoCategorized: Boolean = true
)
