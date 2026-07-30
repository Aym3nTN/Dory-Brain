package com.dorybrain.shared.nvidia

import com.dorybrain.shared.settings.SettingsRepository

/**
 * Verifies the saved key and model actually work by making one tiny
 * completion, so Settings can report a real result rather than just
 * whether a key string is present.
 */
class NvidiaConnectionTester(private val settings: SettingsRepository) {

    sealed interface Result {
        data object Connected : Result
        data object NoApiKey : Result
        data class Failed(val message: String) : Result
    }

    suspend fun test(): Result {
        val apiKey = settings.apiKey ?: return Result.NoApiKey

        return runCatching {
            NvidiaChatClient(apiKey, settings.model).complete(
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
