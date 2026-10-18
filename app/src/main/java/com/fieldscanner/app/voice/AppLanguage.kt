package com.fieldscanner.app.voice

import java.util.Locale

/**
 * Closed set of languages the scanner can speak, analyse and recognise in.
 *
 * The set is approved by TASK-015 and intentionally small; the child switches
 * between members by voice, never through a screen menu. [DEFAULT] is the safe
 * fallback used when nothing is stored yet.
 *
 * @property code short persisted identifier (also the primary language subtag).
 * @property bcp47 tag handed to `SpeechRecognizer` / `TextToSpeech`.
 * @property endonym the language's own name, spoken back to the child.
 * @property englishName used only inside the English TTS style instructions.
 * @property isRtl true for right-to-left scripts (Hebrew); debug overlays only.
 */
enum class AppLanguage(
    val code: String,
    val bcp47: String,
    val endonym: String,
    val englishName: String,
    val isRtl: Boolean
) {
    RUSSIAN("ru", "ru-RU", "Русский", "Russian", false),
    POLISH("pl", "pl-PL", "Polski", "Polish", false),
    BELARUSIAN("be", "be-BY", "Беларуская", "Belarusian", false),
    ENGLISH("en", "en-US", "English", "English", false),
    UKRAINIAN("uk", "uk-UA", "Українська", "Ukrainian", false),
    HEBREW("he", "he-IL", "עברית", "Hebrew", true);

    fun locale(): Locale {
        val parts = bcp47.split("-")
        return Locale(parts[0], parts.getOrElse(1) { "" })
    }

    /** Human-readable target handed to the analyzer prompt placeholder. */
    val promptDescriptor: String get() = "$endonym ($bcp47)"

    companion object {
        val DEFAULT: AppLanguage = RUSSIAN

        fun fromCode(code: String?): AppLanguage? =
            code?.let { wanted -> values().firstOrNull { it.code.equals(wanted, ignoreCase = true) } }
    }
}
