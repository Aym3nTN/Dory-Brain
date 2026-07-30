package com.dorybrain.shared.refine

import com.dorybrain.shared.nvidia.NvidiaApiException
import com.dorybrain.shared.nvidia.NvidiaChatClient
import com.dorybrain.shared.settings.SettingsRepository

/**
 * Rewrites note text with an NVIDIA NIM model.
 *
 * Unlike categorization there is no on-device fallback here: genuinely
 * rewriting prose needs a language model, and quietly substituting a
 * regex tidy-up would misrepresent what happened. When no key is set, or
 * the call fails, this reports the reason instead.
 */
class RefinerRepository(private val settings: SettingsRepository) {

    sealed interface Result {
        data class Success(val refinedText: String) : Result
        data class Failure(val message: String) : Result
        data object NoApiKey : Result
    }

    suspend fun refine(text: String, mode: RefineMode): Result {
        val apiKey = settings.apiKey ?: return Result.NoApiKey

        return runCatching {
            NvidiaChatClient(apiKey, settings.model).complete(
                systemPrompt = mode.systemPrompt(),
                userText = text,
                maxTokens = MAX_TOKENS,
                temperature = TEMPERATURE
            )
        }.fold(
            onSuccess = { raw ->
                val cleaned = stripWrapping(raw)
                if (cleaned.isBlank()) {
                    Result.Failure("The model returned an empty rewrite.")
                } else {
                    Result.Success(cleaned)
                }
            },
            onFailure = { throwable ->
                Result.Failure(
                    (throwable as? NvidiaApiException)?.message
                        ?: "Couldn't reach NVIDIA. Check your connection and try again."
                )
            }
        )
    }

    /** Models like to wrap answers in quotes or a code fence; peel those off. */
    private fun stripWrapping(raw: String): String {
        var text = raw.trim()

        if (text.startsWith("```")) {
            text = text.removePrefix("```")
                .substringAfter('\n', text)
                .substringBeforeLast("```")
                .trim()
        }

        val quotePairs = listOf('"' to '"', '\'' to '\'', '“' to '”')
        for ((open, close) in quotePairs) {
            if (text.length >= 2 && text.first() == open && text.last() == close) {
                text = text.substring(1, text.length - 1).trim()
                break
            }
        }

        return text
    }

    private companion object {
        const val MAX_TOKENS = 500
        const val TEMPERATURE = 0.2
    }
}
