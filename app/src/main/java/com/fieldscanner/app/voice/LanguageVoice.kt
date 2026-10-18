package com.fieldscanner.app.voice

/**
 * Non-network spoken feedback for the language-change flow.
 *
 * Uses the on-device TTS engine so confirmation still works with no connection;
 * [error] is a short non-verbal tone (no text, no translation) used for "not
 * understood" and rollback. The Android implementation is
 * `voice/remote/AndroidLanguageVoice`.
 */
interface LanguageVoice {
    /** Speaks [text] in [language]; [onDone] runs on completion or if speech could not start. */
    fun speak(text: String, language: AppLanguage, onDone: () -> Unit)
    fun error()
    fun release()

    /** Optional hint to pre-synthesise the endonyms of [languages] so cycling is instant. */
    fun prewarm(languages: List<AppLanguage>) {}
}
