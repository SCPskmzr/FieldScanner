package com.fieldscanner.app.voice

/**
 * Single source of the selected [AppLanguage].
 *
 * Every language-sensitive consumer (analyzer prompt, TTS, `SpeechRecognizer`
 * locale, journal matching locale) reads [current] at the point of use, so a
 * change made through [update] takes effect on the next scan / narration /
 * recognition without any listener plumbing. Concrete storage lives in
 * `voice/remote/`.
 */
interface LanguagePreference {
    fun current(): AppLanguage
    fun update(language: AppLanguage)
}
