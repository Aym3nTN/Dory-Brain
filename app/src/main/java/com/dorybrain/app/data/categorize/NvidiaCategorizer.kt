package com.dorybrain.app.data.categorize

import com.dorybrain.app.data.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Categorizes a note by calling an NVIDIA NIM model through its
 * OpenAI-compatible chat completions endpoint. Throws on any network,
 * auth, or parsing failure so the caller can fall back to on-device
 * categorization.
 */
class NvidiaCategorizer(
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = defaultClient
) : Categorizer {

    override suspend fun categorize(text: String): Category = withContext(Dispatchers.IO) {
        val categoryList = Category.entries.joinToString(", ") { it.label }
        val systemPrompt = "You sort short personal notes into exactly one bucket. " +
            "Reply with only the bucket name, nothing else. " +
            "Valid buckets: $categoryList."

        val requestJson = JSONObject().apply {
            put("model", model)
            put("temperature", 0)
            put("max_tokens", 8)
            put("stream", false)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", text) })
                }
            )
        }

        val request = Request.Builder()
            .url(CHAT_COMPLETIONS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("NVIDIA API request failed with HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val content = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            parseCategory(content) ?: error("Could not parse a category from: $content")
        }
    }

    private fun parseCategory(content: String): Category? {
        val cleaned = content.trim()
        Category.fromLabel(cleaned)?.let { return it }
        return Category.entries.firstOrNull { cleaned.contains(it.label, ignoreCase = true) }
    }

    companion object {
        private const val CHAT_COMPLETIONS_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
