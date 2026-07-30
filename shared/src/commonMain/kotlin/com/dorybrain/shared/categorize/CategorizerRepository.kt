package com.dorybrain.shared.categorize

import com.dorybrain.shared.logWarning
import com.dorybrain.shared.model.Category
import com.dorybrain.shared.nvidia.NvidiaChatClient
import com.dorybrain.shared.settings.SettingsRepository

/**
 * Picks the NVIDIA cloud categorizer when an API key is configured, and
 * transparently falls back to the on-device keyword classifier if no key
 * is set or the cloud call fails for any reason (offline, bad key, rate
 * limit, unexpected response, etc). Callers always get a category back.
 */
class CategorizerRepository(
    private val settings: SettingsRepository,
    private val localCategorizer: Categorizer = LocalKeywordCategorizer()
) {
    suspend fun categorize(text: String): Category {
        val apiKey = settings.apiKey
        if (!apiKey.isNullOrBlank() && !settings.forceOnDevice) {
            runCatching {
                NvidiaCategorizer(NvidiaChatClient(apiKey, settings.model)).categorize(text)
            }.onSuccess { return it }
                .onFailure {
                    logWarning(TAG, "NVIDIA categorization failed, falling back locally", it)
                }
        }
        return localCategorizer.categorize(text)
    }

    private companion object {
        const val TAG = "CategorizerRepository"
    }
}
