package com.dorybrain.app.data.categorize

import com.dorybrain.app.data.Category
import com.dorybrain.app.data.nvidia.NvidiaChatClient

/**
 * Categorizes a note with an NVIDIA NIM model. Throws on any network, auth,
 * or parsing failure so the caller can fall back to on-device categorization.
 */
class NvidiaCategorizer(private val chatClient: NvidiaChatClient) : Categorizer {

    override suspend fun categorize(text: String): Category {
        val categoryList = Category.entries.joinToString(", ") { it.label }
        val systemPrompt = "You sort short personal notes into exactly one bucket. " +
            "Reply with only the bucket name, nothing else. " +
            "Valid buckets: $categoryList."

        val content = chatClient.complete(
            systemPrompt = systemPrompt,
            userText = text,
            maxTokens = 8,
            temperature = 0.0
        )

        return parseCategory(content) ?: error("Could not parse a category from: $content")
    }

    private fun parseCategory(content: String): Category? {
        val cleaned = content.trim()
        Category.fromLabel(cleaned)?.let { return it }
        return Category.entries.firstOrNull { cleaned.contains(it.label, ignoreCase = true) }
    }
}
