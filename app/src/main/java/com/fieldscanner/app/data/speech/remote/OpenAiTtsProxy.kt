package com.fieldscanner.app.data.speech.remote

import android.content.Context
import com.fieldscanner.app.BuildConfig
import com.fieldscanner.app.data.net.HttpClient
import com.fieldscanner.app.data.diagnostics.DiagnosticsLog
import com.fieldscanner.app.data.speech.SpeechSynthesizer
import com.fieldscanner.app.voice.AppLanguage
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Generates speech with the developer key supplied in local.properties. */
class OpenAiTtsProxy(
    context: Context,
    private val diagnostics: DiagnosticsLog = DiagnosticsLog.NONE,
    private val languageProvider: () -> AppLanguage = { AppLanguage.DEFAULT }
) : SpeechSynthesizer {
    private val appContext = context.applicationContext

    override suspend fun synthesize(text: String): File = withContext(Dispatchers.IO) {
        require(BuildConfig.OPENAI_API_KEY.isNotBlank()) { "OpenAI API key is not configured." }
        require(text.isNotBlank()) { "There is no text to speak." }
        requestSpeech(text.trim().take(MAX_INPUT_LENGTH), languageProvider())
    }

    private fun requestSpeech(text: String, language: AppLanguage): File {
        val body = JSONObject().apply {
            put("model", "gpt-4o-mini-tts")
            put("input", text)
            put("voice", "marin")
            put("instructions", voiceInstructions(language))
            put("response_format", "mp3")
        }.toString()
        val audioFile = File.createTempFile("field-scanner-", ".mp3", appContext.cacheDir)
        try {
            HttpClient.execute(
                url = SPEECH_URL,
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "application/json; charset=utf-8",
                    "Accept" to "audio/mpeg",
                    "Authorization" to "Bearer ${BuildConfig.OPENAI_API_KEY}"
                ),
                connectTimeoutMillis = TIMEOUT_MILLIS,
                readTimeoutMillis = TIMEOUT_MILLIS,
                body = body.toByteArray(Charsets.UTF_8)
            ) { input -> audioFile.outputStream().use { input.copyTo(it) } }
            check(audioFile.length() > 0L) { "TTS proxy returned an empty audio file." }
            return audioFile
        } catch (httpError: HttpClient.HttpException) {
            audioFile.delete()
            diagnostics.error("OpenAiTtsProxy", "OpenAI TTS returned HTTP ${httpError.statusCode}", httpError)
            throw httpError
        } catch (error: Throwable) {
            audioFile.delete()
            diagnostics.error("OpenAiTtsProxy", "Speech synthesis failed", error)
            throw error
        }
    }

    private fun voiceInstructions(language: AppLanguage): String =
        "Speak ${language.englishName} with clear, neutral pronunciation. " +
            "Speak faster than normal 15%, at an energetic but comfortable pace."

    private companion object {
        const val MAX_INPUT_LENGTH = 4_096
        const val TIMEOUT_MILLIS = 30_000
        const val SPEECH_URL = "https://api.openai.com/v1/audio/speech"
    }
}
