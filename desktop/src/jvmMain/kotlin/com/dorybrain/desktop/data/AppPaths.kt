package com.dorybrain.desktop.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where the desktop app keeps its database and settings, following each OS's
 * usual convention rather than dumping files in the home directory.
 */
object AppPaths {
    private const val APP_DIR_NAME = "DoryBrain"

    private val osName: String get() = System.getProperty("os.name").orEmpty().lowercase()
    private val home: Path get() = Paths.get(System.getProperty("user.home"))

    val dataDir: Path by lazy {
        val dir = when {
            osName.contains("win") ->
                System.getenv("APPDATA")?.let { Paths.get(it, APP_DIR_NAME) }
                    ?: home.resolve(APP_DIR_NAME)

            osName.contains("mac") ->
                home.resolve("Library/Application Support").resolve(APP_DIR_NAME)

            else ->
                System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                    ?.let { Paths.get(it, APP_DIR_NAME) }
                    ?: home.resolve(".local/share").resolve(APP_DIR_NAME)
        }
        Files.createDirectories(dir)
        dir
    }

    val databaseFile: Path get() = dataDir.resolve("dory_brain.db")
    val settingsFile: Path get() = dataDir.resolve("settings.properties")
}
