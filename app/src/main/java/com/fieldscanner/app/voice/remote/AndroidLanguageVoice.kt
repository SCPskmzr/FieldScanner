package com.fieldscanner.app.voice.remote

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import com.fieldscanner.app.data.speech.SpeechSynthesizer
import com.fieldscanner.app.voice.AppLanguage
import com.fieldscanner.app.voice.LanguageVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Spoken feedback for the language carousel.
 *
 * The endonym of each [AppLanguage] is synthesised once through [synthesizer]
 * (the same cloud TTS the scanner already uses) and cached on disk, so every
 * supported language is voiced — not only the ones whose on-device TTS voice
 * happens to be installed — and later cycles play instantly and offline.
 * [error] is a short non-verbal tone for the watchdog timeout.
 */
class AndroidLanguageVoice(
    context: Context,
    private val synthesizer: SpeechSynthesizer
) : LanguageVoice {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val cacheDir = File(appContext.cacheDir, CACHE_SUBDIR)
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME) }
    private var player: MediaPlayer? = null

    override fun prewarm(languages: List<AppLanguage>) {
        languages.forEach { language ->
            val target = cacheFile(language)
            if (target.isFile && target.length() > 0L) return@forEach
            scope.launch { runCatching { synthesizeInto(language, target) } }
        }
    }

    override fun speak(text: String, language: AppLanguage, onDone: () -> Unit) {
        val done = onlyOnce(onDone)
        val cached = cacheFile(language)
        if (cached.isFile && cached.length() > 0L) {
            play(cached, done)
            return
        }
        scope.launch {
            val ready = runCatching { synthesizeInto(language, cached) }.getOrDefault(false)
            if (ready) play(cached, done) else { error(); done() }
        }
    }

    override fun error() {
        runCatching { tone.startTone(ToneGenerator.TONE_PROP_NACK, TONE_DURATION_MILLIS) }
    }

    override fun release() {
        scope.cancel()
        runCatching { player?.release() }
        player = null
        runCatching { tone.release() }
    }

    /** Synthesises [language]'s endonym and moves it into [target]; true if a non-empty file resulted. */
    private suspend fun synthesizeInto(language: AppLanguage, target: File): Boolean {
        val produced = synthesizer.synthesize(language.endonym)
        return withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            produced.copyTo(target, overwrite = true)
            produced.delete()
            target.isFile && target.length() > 0L
        }
    }

    private fun play(file: File, onDone: () -> Unit) {
        runCatching { player?.release() }
        val mp = MediaPlayer()
        player = mp
        mp.setOnPreparedListener { it.start() }
        mp.setOnCompletionListener { finished -> releasePlayer(finished); onDone() }
        mp.setOnErrorListener { failed, _, _ -> releasePlayer(failed); error(); onDone(); true }
        runCatching {
            mp.setDataSource(file.absolutePath)
            mp.prepareAsync()
        }.onFailure { releasePlayer(mp); error(); onDone() }
    }

    private fun releasePlayer(mp: MediaPlayer) {
        runCatching { mp.release() }
        if (player === mp) player = null
    }

    private fun cacheFile(language: AppLanguage) = File(cacheDir, "endonym-${language.code}.mp3")

    private fun onlyOnce(action: () -> Unit): () -> Unit {
        var done = false
        return {
            if (!done) {
                done = true
                action()
            }
        }
    }

    private companion object {
        const val CACHE_SUBDIR = "language-voice"
        const val TONE_VOLUME = 80
        const val TONE_DURATION_MILLIS = 200
    }
}
