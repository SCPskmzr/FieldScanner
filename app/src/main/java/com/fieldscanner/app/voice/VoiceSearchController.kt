package com.fieldscanner.app.voice

import android.content.Context
import com.fieldscanner.app.voice.remote.AndroidSpeechRecognitionSession

/**
 * Journal voice search. Delegates the recognition mechanics to a shared
 * [SpeechRecognitionSession] and asks [languageProvider] for the current locale
 * on every attempt, so switching the app language (TASK-015) also switches the
 * language the journal listens in.
 */
class VoiceSearchController(
    context: Context,
    private val languageProvider: () -> AppLanguage,
    private val onResults: (List<String>) -> Unit,
    private val session: SpeechRecognitionSession = AndroidSpeechRecognitionSession(context)
) {
    fun start() {
        session.start(
            RecognitionConfig(languageProvider().bcp47),
            object : SpeechRecognitionSession.Listener {
                override fun onHypotheses(hypotheses: List<String>) = onResults(hypotheses)
                override fun onUnavailable() = Unit
                override fun onError(errorCode: Int) = Unit
            }
        )
    }

    fun stop() {
        session.cancel()
    }
}
