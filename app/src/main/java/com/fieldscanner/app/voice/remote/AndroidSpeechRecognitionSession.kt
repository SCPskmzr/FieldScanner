package com.fieldscanner.app.voice.remote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.fieldscanner.app.voice.RecognitionConfig
import com.fieldscanner.app.voice.SpeechRecognitionSession

/**
 * Android `SpeechRecognizer` behind [SpeechRecognitionSession].
 *
 * One instance can be reused for successive attempts; each [start] destroys the
 * previous recogniser first. Must be called from the main thread.
 */
class AndroidSpeechRecognitionSession(context: Context) : SpeechRecognitionSession {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    override fun start(config: RecognitionConfig, listener: SpeechRecognitionSession.Listener) {
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            listener.onUnavailable()
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) {
                    cancel()
                    listener.onError(error)
                }
                override fun onResults(results: Bundle?) {
                    val hypotheses = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    cancel()
                    listener.onHypotheses(hypotheses)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speech.startListening(buildIntent(config))
        }
    }

    override fun cancel() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun buildIntent(config: RecognitionConfig): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.bcp47)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, config.bcp47)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxResults)
        }
}
