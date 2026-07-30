package com.dorybrain.shared.categorize

import com.dorybrain.shared.model.Category

fun interface Categorizer {
    suspend fun categorize(text: String): Category
}
