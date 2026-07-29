package com.dorybrain.app.data.nvidia

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
 * Thin wrapper over NVIDIA NIM's OpenAI-compatible chat completions endpoint.
 * Throws on any network, auth, or parsing failure so callers can decide
 * whether to fall back or surface the error.
 */
class NvidiaChatClient(
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = defaultClient
) {

    suspend fun complete(
        systemPrompt: String,
        userText: String,
        maxTokens: Int,
        temperature: Double
    ): String = withContext(Dispatchers.IO) {
        val requestJson = JSONObject().apply {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", false)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userText) })
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
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw NvidiaApiException(describeFailure(response.code, body))
            }
            JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    /** Turns the common HTTP failures into something worth showing a user. */
    private fun describeFailure(code: Int, body: String): String {
        val apiMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        }.getOrNull()

        return when (code) {
            401, 403 -> "NVIDIA rejected the API key. Check it in Settings."
            404 -> "Model \"$model\" wasn't found. Check the model name in Settings."
            429 -> "NVIDIA rate limit reached. Try again in a moment."
            in 500..599 -> "NVIDIA's API is having trouble (HTTP $code). Try again shortly."
            else -> apiMessage ?: "NVIDIA request failed (HTTP $code)."
        }
    }

    companion object {
        private const val CHAT_COMPLETIONS_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

class NvidiaApiException(message: String) : Exception(message)
