package com.fieldscanner.app.data.diagnostics

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.fieldscanner.app.data.net.HttpClient

/** Formats bounded, secret-redacted records independently of Android APIs. */
class DiagnosticsFormatter(private val apiKeyProvider: () -> String) {
    fun format(level: DiagnosticsLevel, tag: String, message: String, error: Throwable? = null): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
        val trace = error?.let(::safeTrace).orEmpty()
        return sanitize("$timestamp ${level.name}/$tag: $message${if (trace.isEmpty()) "" else "\n$trace"}")
            .take(MAX_RECORD_CHARS) + "\n"
    }

    fun sanitize(value: String): String {
        val key = apiKeyProvider()
        var safe = if (key.isBlank()) value else value.replace(key, REDACTED)
        safe = AUTHORIZATION.replace(safe, "Authorization: $REDACTED")
        return safe
    }

    private fun safeTrace(error: Throwable): String = if (error is HttpClient.HttpException) {
        "${error.javaClass.name}: HTTP ${error.statusCode}; body=${sanitize(error.body).take(MAX_SERVICE_BODY_CHARS)}\n" +
            error.stackTrace.joinToString("\n") { "\tat $it" }
    } else {
        StringWriter().also { writer -> error.printStackTrace(PrintWriter(writer)) }.toString()
    }

    private companion object {
        const val REDACTED = "[REDACTED]"
        const val MAX_RECORD_CHARS = 4_096
        const val MAX_SERVICE_BODY_CHARS = 1_024
        val AUTHORIZATION = Regex("(?im)(authorization\\s*[:=]\\s*)([^\\r\\n]+)")
    }
}
