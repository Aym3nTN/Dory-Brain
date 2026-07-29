package com.dorybrain.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dorybrain.app.ui.theme.ThemeMode

/**
 * Stores the NVIDIA API key and app preferences encrypted-at-rest. When no key
 * is present, callers fall back to on-device categorization.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_API_KEY, value?.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    /** When on, categorization stays on-device even if a key is configured. */
    var forceOnDevice: Boolean
        get() = prefs.getBoolean(KEY_FORCE_ON_DEVICE, false)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_ON_DEVICE, value).apply()

    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME_MODE, null)
            ?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    val hasApiKey: Boolean get() = !apiKey.isNullOrBlank()

    /** A redacted form safe to show on screen, e.g. `nvapi-…8f2a`. */
    fun maskedApiKey(): String? {
        val key = apiKey ?: return null
        return if (key.length <= 10) {
            "•".repeat(key.length)
        } else {
            "${key.take(6)}${"•".repeat(8)}${key.takeLast(4)}"
        }
    }

    companion object {
        private const val PREFS_FILE_NAME = "dory_brain_secure_prefs"
        private const val KEY_API_KEY = "nvidia_api_key"
        private const val KEY_MODEL = "nvidia_model"
        private const val KEY_FORCE_ON_DEVICE = "force_on_device"
        private const val KEY_THEME_MODE = "theme_mode"
        const val DEFAULT_MODEL = "meta/llama-3.1-8b-instruct"

        /** Offered in the model picker; the field stays editable for others. */
        val SUGGESTED_MODELS = listOf(
            "meta/llama-3.1-8b-instruct",
            "meta/llama-3.1-70b-instruct",
            "mistralai/mistral-7b-instruct-v0.3",
            "microsoft/phi-3-mini-4k-instruct"
        )
    }
}
