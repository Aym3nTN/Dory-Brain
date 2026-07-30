package com.dorybrain.desktop.data

import com.dorybrain.shared.settings.SettingsDefaults
import com.dorybrain.shared.settings.ThemeMode
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSettingsRepositoryTest {

    private fun newFile() = Files.createTempDirectory("dory-settings").resolve("settings.properties")

    @Test
    fun `defaults apply when nothing has been saved`() {
        val settings = FileSettingsRepository(newFile())

        assertNull(settings.apiKey)
        assertFalse(settings.hasApiKey)
        assertEquals(SettingsDefaults.MODEL, settings.model)
        assertFalse(settings.forceOnDevice)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
    }

    @Test
    fun `values round-trip through a reopened file`() {
        val file = newFile()

        FileSettingsRepository(file).apply {
            apiKey = "nvapi-abcdef1234567890"
            model = "meta/llama-3.1-70b-instruct"
            forceOnDevice = true
            themeMode = ThemeMode.DARK
        }

        val reopened = FileSettingsRepository(file)
        assertEquals("nvapi-abcdef1234567890", reopened.apiKey)
        assertEquals("meta/llama-3.1-70b-instruct", reopened.model)
        assertTrue(reopened.forceOnDevice)
        assertEquals(ThemeMode.DARK, reopened.themeMode)
    }

    @Test
    fun `blank key clears rather than storing whitespace`() {
        val file = newFile()
        val settings = FileSettingsRepository(file)

        settings.apiKey = "nvapi-secret-value"
        assertTrue(settings.hasApiKey)

        settings.apiKey = "   "
        assertNull(settings.apiKey)
        assertFalse(FileSettingsRepository(file).hasApiKey)
    }

    @Test
    fun `masking keeps only the ends of the key visible`() {
        val settings = FileSettingsRepository(newFile())
        settings.apiKey = "nvapi-abcdefghijkl9876"

        val masked = settings.maskedApiKey()!!
        assertTrue(masked.startsWith("nvapi-"), "prefix should stay readable")
        assertTrue(masked.endsWith("9876"), "last four should stay readable")
        assertFalse(masked.contains("abcdefghijkl"), "middle must not leak: $masked")
    }

    @Test
    fun `settings file is not world readable where posix permissions apply`() {
        val file = newFile()
        FileSettingsRepository(file).apiKey = "nvapi-secret"

        val view = runCatching { Files.getPosixFilePermissions(file) }.getOrNull()
        if (view == null) return // Windows — nothing to assert.

        assertFalse(view.contains(PosixFilePermission.OTHERS_READ), "others must not read: $view")
        assertFalse(view.contains(PosixFilePermission.GROUP_READ), "group must not read: $view")
        assertTrue(view.contains(PosixFilePermission.OWNER_READ))
    }
}
