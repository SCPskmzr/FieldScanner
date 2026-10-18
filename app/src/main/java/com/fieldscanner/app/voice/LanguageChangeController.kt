package com.fieldscanner.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.fieldscanner.app.data.speech.SpeechSynthesizer
import com.fieldscanner.app.voice.remote.AndroidLanguageVoice

/**
 * Runs the app-language switch mode (TASK-015).
 *
 * Entered by a long-press on the journal `ASTRA` rune, which puts rune input into
 * [com.fieldscanner.app.runes.RuneContext.LANGUAGE_CHANGE]. The child does NOT
 * speak: on entry the mode speaks the **current** language endonym and sits on
 * it, then reacts only to rune keys:
 *
 * - `ASTRA` — move to the next language, spoken aloud;
 * - `EMBER` — apply the language now being spoken;
 * - `DRIFT` — leave without changing anything.
 *
 * Applying just persists the choice and speaks it once as confirmation. All
 * methods run on the main thread; [LanguageVoice] / [LanguagePreference] are the
 * only collaborators so the flow is unit-tested with fakes.
 */
class LanguageChangeController(
    private val preference: LanguagePreference,
    private val voice: LanguageVoice,
    private val timeout: FlowTimeout = HandlerFlowTimeout()
) {
    /** Set by the owner (coordinator) to resume normal rune input once the mode ends. */
    var onFinished: () -> Unit = {}

    private sealed interface State {
        object Idle : State
        data class Cycling(val candidates: List<AppLanguage>, val index: Int) : State
    }

    private var state: State = State.Idle

    fun isActive(): Boolean = state != State.Idle

    fun start() {
        if (isActive()) return
        armTimeout()
        val all = allLanguages()
        voice.prewarm(all)
        state = State.Cycling(all, all.indexOf(preference.current()).coerceAtLeast(0))
        speakCurrentCandidate()
    }

    /** `ASTRA`: next language, spoken aloud. */
    fun onCycleKey() {
        val current = state as? State.Cycling ?: return
        armTimeout()
        state = current.copy(index = (current.index + 1).mod(current.candidates.size))
        speakCurrentCandidate()
    }

    /** `EMBER`: apply the language now being spoken. */
    fun onConfirmKey() {
        val current = state as? State.Cycling ?: return
        apply(current.candidates[current.index])
    }

    /** `DRIFT`: leave the mode, language unchanged. */
    fun onCancelKey() {
        if (isActive()) finish()
    }

    /** Releases the shared voice engine; call once from the owner's destroy(). */
    fun stop() {
        finish()
        voice.release()
    }

    private fun apply(language: AppLanguage) {
        if (language != preference.current()) preference.update(language)
        voice.speak(language.endonym, language) { finish() }
    }

    private fun speakCurrentCandidate() {
        val current = state as? State.Cycling ?: return
        val candidate = current.candidates[current.index]
        voice.speak(candidate.endonym, candidate) {}
    }

    private fun armTimeout() = timeout.arm(::onTimeout)

    private fun onTimeout() {
        if (!isActive()) return
        voice.error()
        finish()
    }

    private fun finish() {
        state = State.Idle
        timeout.cancel()
        onFinished()
    }

    private fun allLanguages(): List<AppLanguage> = AppLanguage.values().toList()

    /** Small indirection so the safety timeout is deterministic in tests. */
    interface FlowTimeout {
        fun arm(onFire: () -> Unit)
        fun cancel()
    }

    class HandlerFlowTimeout(
        private val delayMillis: Long = 25_000L
    ) : FlowTimeout {
        private val handler = Handler(Looper.getMainLooper())
        private var scheduled: Runnable? = null

        override fun arm(onFire: () -> Unit) {
            cancel()
            val runnable = Runnable { scheduled = null; onFire() }
            scheduled = runnable
            handler.postDelayed(runnable, delayMillis)
        }

        override fun cancel() {
            scheduled?.let(handler::removeCallbacks)
            scheduled = null
        }
    }

    companion object {
        /** Convenience factory for the Android wiring in MainActivity. */
        fun create(
            context: Context,
            preference: LanguagePreference,
            synthesizer: SpeechSynthesizer
        ): LanguageChangeController =
            LanguageChangeController(preference, AndroidLanguageVoice(context.applicationContext, synthesizer))
    }
}
