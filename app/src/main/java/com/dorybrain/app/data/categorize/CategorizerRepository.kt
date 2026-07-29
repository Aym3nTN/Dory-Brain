package com.dorybrain.app.data.categorize

import android.util.Log
import com.dorybrain.app.data.Category
import com.dorybrain.app.data.SettingsStore
import com.dorybrain.app.data.nvidia.NvidiaChatClient

/**
 * Picks the NVIDIA cloud categorizer when an API key is configured, and
 * transparently falls back to the on-device keyword classifier if no key
 * is set or the cloud call fails for any reason (offline, bad key, rate
 * limit, unexpected response, etc). Callers always get a category back.
 */
class CategorizerRepository(
    private val settingsStore: SettingsStore,
    private val localCategorizer: Categorizer = LocalKeywordCategorizer()
) {
    suspend fun categorize(text: String): Category {
        val apiKey = settingsStore.apiKey
        if (!apiKey.isNullOrBlank() && !settingsStore.forceOnDevice) {
            runCatching {
                NvidiaCategorizer(NvidiaChatClient(apiKey, settingsStore.model)).categorize(text)
            }.onSuccess { return it }
                .onFailure { Log.w(TAG, "NVIDIA categorization failed, falling back locally", it) }
        }
        return localCategorizer.categorize(text)
    }

    companion object {
        private const val TAG = "CategorizerRepository"
    }
}
