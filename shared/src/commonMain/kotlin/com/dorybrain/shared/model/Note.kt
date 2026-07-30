package com.dorybrain.shared.model

/**
 * A captured thought. This is the platform-neutral domain model — each
 * platform's storage layer maps its own rows onto it.
 */
data class Note(
    val id: Long = 0,
    val text: String,
    val category: Category,
    val createdAt: Long,
    /** True while a note is waiting on the categorizer to finish. */
    val isCategorizing: Boolean = false,
    /** True if [category] was set automatically and hasn't been corrected by hand. */
    val isAutoCategorized: Boolean = true
)
