package com.dorybrain.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dorybrain.shared.settings.SettingsDefaults
import com.dorybrain.shared.settings.SettingsRepository
import com.dorybrain.shared.settings.ThemeMode

/**
 * Android's [SettingsRepository]: encrypted-at-rest via
 * `EncryptedSharedPreferences`, and excluded from Android backups.
 */
class SettingsStore(context: Context) : SettingsRepository {

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

    override var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_API_KEY, value?.trim()).apply()

    override var model: String
        get() = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: SettingsDefaults.MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    override var forceOnDevice: Boolean
        get() = prefs.getBoolean(KEY_FORCE_ON_DEVICE, false)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_ON_DEVICE, value).apply()

    override var themeMode: ThemeMode
        get() = ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null))
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    private companion object {
        const val PREFS_FILE_NAME = "dory_brain_secure_prefs"
        const val KEY_API_KEY = "nvidia_api_key"
        const val KEY_MODEL = "nvidia_model"
        const val KEY_FORCE_ON_DEVICE = "force_on_device"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
