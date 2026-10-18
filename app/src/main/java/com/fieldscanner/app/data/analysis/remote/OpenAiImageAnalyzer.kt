package com.fieldscanner.app.data.analysis.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.fieldscanner.app.BuildConfig
import com.fieldscanner.app.data.analysis.ImageAnalyzer
import com.fieldscanner.app.data.finding.AnalysisResult
import com.fieldscanner.app.data.net.HttpClient
import com.fieldscanner.app.data.diagnostics.DiagnosticsLog
import com.fieldscanner.app.voice.AppLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sends one photo to OpenAI using the developer key stored in local.properties. */
class OpenAiImageAnalyzer(
    context: Context,
    private val diagnostics: DiagnosticsLog = DiagnosticsLog.NONE,
    private val languageProvider: () -> AppLanguage = { AppLanguage.DEFAULT }
) : ImageAnalyzer {
    private val appContext = context.applicationContext
    @Volatile private var activeConnection: HttpURLConnection? = null
    @Volatile private var cancelled = false
    private val promptTemplate by lazy {
        appContext.assets.open(FIELD_SCANNER_PROMPT_ASSET).bufferedReader().use { it.readText() }
    }

    override suspend fun analyze(photo: File): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            cancelled = false
            require(BuildConfig.OPENAI_API_KEY.isNotBlank()) { "OpenAI API key is not configured." }
            require(photo.isFile) { "Captured photo does not exist." }
            requestAnalysis(photo).validated()
        } catch (error: Throwable) {
            diagnostics.error("OpenAiImageAnalyzer", "Image analysis request failed", error)
            throw error
        }
    }

    override fun cancel() {
        cancelled = true
        activeConnection?.disconnect()
    }

    private fun requestAnalysis(photo: File): AnalysisResult {
        check(!cancelled) { "Image analysis was cancelled." }
        val promptText = promptTemplate.replace(OUTPUT_LANGUAGE_PLACEHOLDER, languageProvider().promptDescriptor)
        val encodedPhoto = Base64.encodeToString(resizeForVision(photo), Base64.NO_WRAP)
        val requestBody = JSONObject().apply {
            put("model", BuildConfig.OPENAI_MODEL)
            put("store", false)
            put("input", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray()
                    .put(JSONObject().put("type", "input_text").put("text", promptText))
                    .put(JSONObject().put("type", "input_image")
                        .put("image_url", "data:image/jpeg;base64,$encodedPhoto")
                        .put("detail", "low")))
            }))
        }
        val responseText = try {
            HttpClient.execute(
                url = RESPONSES_URL,
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer ${BuildConfig.OPENAI_API_KEY}",
                    "Content-Type" to "application/json"
                ),
                connectTimeoutMillis = TIMEOUT_MILLIS,
                readTimeoutMillis = TIMEOUT_MILLIS,
                body = requestBody.toString().toByteArray(Charsets.UTF_8),
                onConnect = { activeConnection = it }
            ) { stream -> stream.bufferedReader().use { it.readText() } }
        } finally {
            activeConnection = null
        }
        check(!cancelled) { "Image analysis was cancelled." }
        val finding = JSONObject(extractOutputText(JSONObject(responseText)))
        return AnalysisResult(
            title = finding.getString("title"),
            description = finding.getString("description"),
            confidence = finding.optInt("confidence", 0) / 100.0,
            visualId = finding.optString("visual_id")
        )
    }

    private fun extractOutputText(response: JSONObject): String {
        response.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = response.optJSONArray("output") ?: error("OpenAI response contains no output.")
        for (index in 0 until output.length()) {
            val content = output.optJSONObject(index)?.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val text = content.optJSONObject(contentIndex)?.optString("text").orEmpty()
                if (text.isNotBlank()) return text
            }
        }
        error("OpenAI response contains no text output.")
    }

    private fun resizeForVision(photo: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.absolutePath, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Captured photo could not be decoded." }

        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_IMAGE_EDGE * 2) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(photo.absolutePath, decodeOptions)
            ?: error("Captured photo could not be decoded.")
        val longestEdge = maxOf(decoded.width, decoded.height)
        val resized = if (longestEdge > MAX_IMAGE_EDGE) {
            val scale = MAX_IMAGE_EDGE.toFloat() / longestEdge.toFloat()
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt(),
                (decoded.height * scale).toInt(),
                true
            )
        } else {
            decoded
        }
        return try {
            ByteArrayOutputStream().use { output ->
                check(resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Captured photo could not be compressed."
                }
                output.toByteArray()
            }
        } finally {
            if (resized !== decoded) resized.recycle()
            decoded.recycle()
        }
    }

    private companion object {
        const val RESPONSES_URL = "https://api.openai.com/v1/responses"
        const val TIMEOUT_MILLIS = 30_000
        const val FIELD_SCANNER_PROMPT_ASSET = "prompts/FIELD_SCANNER_PROMPT_V5.md"
        const val OUTPUT_LANGUAGE_PLACEHOLDER = "{{OUTPUT_LANGUAGE}}"
        const val MAX_IMAGE_EDGE = 1_280
        const val JPEG_QUALITY = 80
    }
}
