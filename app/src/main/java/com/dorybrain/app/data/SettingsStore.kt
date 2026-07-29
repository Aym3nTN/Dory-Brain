package com.dorybrain.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the NVIDIA API key and model choice encrypted-at-rest. When no key is
 * present, callers should fall back to on-device categorization.
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

    val hasApiKey: Boolean get() = !apiKey.isNullOrBlank()

    companion object {
        private const val PREFS_FILE_NAME = "dory_brain_secure_prefs"
        private const val KEY_API_KEY = "nvidia_api_key"
        private const val KEY_MODEL = "nvidia_model"
        const val DEFAULT_MODEL = "meta/llama-3.1-8b-instruct"
    }
}
