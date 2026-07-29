package com.dorybrain.app.data.categorize

import com.dorybrain.app.data.Category

fun interface Categorizer {
    suspend fun categorize(text: String): Category
}
