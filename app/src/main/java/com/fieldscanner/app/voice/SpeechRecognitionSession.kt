package com.fieldscanner.app.voice

/**
 * One short-lived speech-recognition attempt, used by the journal voice search.
 * The Android implementation lives in
 * `voice/remote/AndroidSpeechRecognitionSession`; tests use a fake.
 */
interface SpeechRecognitionSession {
    fun start(config: RecognitionConfig, listener: Listener)
    fun cancel()

    interface Listener {
        /** All recogniser hypotheses, not just the first. */
        fun onHypotheses(hypotheses: List<String>)
        fun onUnavailable()
        fun onError(errorCode: Int)
    }
}

data class RecognitionConfig(
    val bcp47: String,
    val maxResults: Int = 5
)
