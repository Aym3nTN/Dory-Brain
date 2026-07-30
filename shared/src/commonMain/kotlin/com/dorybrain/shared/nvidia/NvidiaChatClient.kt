package com.dorybrain.shared.nvidia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", false)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", userText)
                        }
                    )
                }
            )
        }

        val request = Request.Builder()
            .url(CHAT_COMPLETIONS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw NvidiaApiException(describeFailure(response.code, body))
            }
            parseContent(body)
        }
    }

    private fun parseContent(body: String): String {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw NvidiaApiException("NVIDIA returned a response that wasn't valid JSON.")

        val choices = root["choices"] as? JsonArray
        val message = (choices?.firstOrNull() as? JsonObject)?.get("message") as? JsonObject
        val content = message?.get("content")?.jsonPrimitive?.contentOrNullSafe()

        return content ?: throw NvidiaApiException("NVIDIA's response had no message content.")
    }

    /** Turns the common HTTP failures into something worth showing a user. */
    private fun describeFailure(code: Int, body: String): String {
        val apiMessage = runCatching {
            (json.parseToJsonElement(body).jsonObject["error"] as? JsonObject)
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNullSafe()
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
        private const val CHAT_COMPLETIONS_URL =
            "https://integrate.api.nvidia.com/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val json = Json { ignoreUnknownKeys = true }

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

/** Blank content is as useless as missing content, so treat both as absent. */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    content.takeIf { it.isNotBlank() }

class NvidiaApiException(message: String) : Exception(message)
