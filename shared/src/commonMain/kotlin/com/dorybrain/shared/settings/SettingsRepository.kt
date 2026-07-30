package com.dorybrain.shared.settings

/** How the app picks between light and dark. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

object SettingsDefaults {
    const val MODEL = "meta/llama-3.1-8b-instruct"

    /** Offered in the model picker; the field stays editable for others. */
    val SUGGESTED_MODELS = listOf(
        "meta/llama-3.1-8b-instruct",
        "meta/llama-3.1-70b-instruct",
        "mistralai/mistral-7b-instruct-v0.3",
        "microsoft/phi-3-mini-4k-instruct"
    )
}

/**
 * Where the API key and preferences live. Each platform supplies its own
 * storage; see the implementations for what protection they actually offer.
 */
interface SettingsRepository {
    var apiKey: String?
    var model: String

    /** When on, categorization stays on-device even if a key is configured. */
    var forceOnDevice: Boolean
    var themeMode: ThemeMode

    val hasApiKey: Boolean get() = !apiKey.isNullOrBlank()

    /** A redacted form safe to show on screen, e.g. `nvapi-••••••••8f2a`. */
    fun maskedApiKey(): String? {
        val key = apiKey ?: return null
        return if (key.length <= 10) {
            "•".repeat(key.length)
        } else {
            "${key.take(6)}${"•".repeat(8)}${key.takeLast(4)}"
        }
    }
}
