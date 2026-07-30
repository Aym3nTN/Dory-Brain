package com.dorybrain.desktop.data

import com.dorybrain.shared.settings.SettingsDefaults
import com.dorybrain.shared.settings.SettingsRepository
import com.dorybrain.shared.settings.ThemeMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties

/**
 * Desktop settings, stored as a properties file in the app data directory.
 *
 * **This is not encrypted at rest.** Android can lean on the platform
 * keystore via `EncryptedSharedPreferences`; there is no equivalent here
 * without binding to an OS keychain, and encrypting with a key sitting
 * next to the ciphertext would be obfuscation dressed up as security. What
 * this does instead is restrict the file to the current user (`0600` where
 * POSIX permissions are supported). Treat the key as readable by anything
 * running as you.
 */
class FileSettingsRepository(private val file: Path) : SettingsRepository {

    private val properties = Properties().apply {
        if (Files.exists(file)) {
            Files.newInputStream(file).use { load(it) }
        }
    }

    override var apiKey: String?
        get() = read(KEY_API_KEY)
        set(value) = write(KEY_API_KEY, value?.trim())

    override var model: String
        get() = read(KEY_MODEL) ?: SettingsDefaults.MODEL
        set(value) = write(KEY_MODEL, value.trim().ifBlank { SettingsDefaults.MODEL })

    override var forceOnDevice: Boolean
        get() = read(KEY_FORCE_ON_DEVICE)?.toBooleanStrictOrNull() ?: false
        set(value) = write(KEY_FORCE_ON_DEVICE, value.toString())

    override var themeMode: ThemeMode
        get() = ThemeMode.fromName(read(KEY_THEME_MODE))
        set(value) = write(KEY_THEME_MODE, value.name)

    private fun read(key: String): String? =
        properties.getProperty(key)?.takeIf { it.isNotBlank() }

    private fun write(key: String, value: String?) {
        if (value.isNullOrBlank()) properties.remove(key) else properties.setProperty(key, value)
        persist()
    }

    /** Writes to a temp file first so a crash can't leave a half-written file. */
    private fun persist() {
        val temp = Files.createTempFile(file.parent, "settings", ".tmp")
        restrictToOwner(temp)
        Files.newOutputStream(temp).use { properties.store(it, "Dory Brain settings") }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        restrictToOwner(file)
    }

    private fun restrictToOwner(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            )
        } // Not POSIX (Windows) — nothing to do.
    }

    private companion object {
        const val KEY_API_KEY = "nvidia.apiKey"
        const val KEY_MODEL = "nvidia.model"
        const val KEY_FORCE_ON_DEVICE = "general.forceOnDevice"
        const val KEY_THEME_MODE = "general.themeMode"
    }
}
