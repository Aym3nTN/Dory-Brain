package com.dorybrain.app.data.nvidia

import com.dorybrain.app.data.SettingsStore

/**
 * Verifies the saved key and model actually work by making one tiny
 * completion, so Settings can report a real result rather than just
 * whether a key string is present.
 */
class NvidiaConnectionTester(private val settingsStore: SettingsStore) {

    sealed interface Result {
        data object Connected : Result
        data object NoApiKey : Result
        data class Failed(val message: String) : Result
    }

    suspend fun test(): Result {
        val apiKey = settingsStore.apiKey ?: return Result.NoApiKey

        return runCatching {
            NvidiaChatClient(apiKey, settingsStore.model).complete(
                systemPrompt = "Reply with the single word: ok",
                userText = "ping",
                maxTokens = 5,
                temperature = 0.0
            )
        }.fold(
            onSuccess = { Result.Connected },
            onFailure = { throwable ->
                Result.Failed(
                    (throwable as? NvidiaApiException)?.message
                        ?: "Couldn't reach NVIDIA. Check your connection."
                )
            }
        )
    }
}
