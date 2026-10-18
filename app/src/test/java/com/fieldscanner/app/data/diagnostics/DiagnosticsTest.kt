package com.fieldscanner.app.data.diagnostics

import com.fieldscanner.app.data.diagnostics.remote.FileDiagnosticsLog
import com.fieldscanner.app.data.diagnostics.remote.AsyncDiagnosticsWriter
import com.fieldscanner.app.data.net.HttpClient
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test fun `analysis failures have stable diagnostic categories`() {
        assertEquals(AnalysisFailureReason.NOT_CONFIGURED, classifyAnalysisFailure(IllegalArgumentException("OpenAI API key is not configured.")))
        assertEquals(AnalysisFailureReason.NETWORK, classifyAnalysisFailure(IOException("offline")))
        assertEquals(AnalysisFailureReason.SERVICE_STATUS, classifyAnalysisFailure(HttpClient.HttpException(503, "unavailable")))
        assertEquals(AnalysisFailureReason.RESPONSE_PARSE, classifyAnalysisFailure(IllegalStateException("OpenAI response contains no output.")))
        assertEquals(AnalysisFailureReason.OTHER, classifyAnalysisFailure(IllegalStateException("unexpected")))
    }

    @Test fun `formatter redacts api key and authorization header`() {
        val secret = "sk-super-secret"
        val formatted = DiagnosticsFormatter { secret }.format(
            DiagnosticsLevel.ERROR, "Test", "Authorization: Bearer $secret", IllegalStateException("key=$secret")
        )
        assertFalse(formatted.contains(secret))
        assertFalse(formatted.contains("Bearer $secret"))
        assertTrue(formatted.contains("[REDACTED]"))
    }

    @Test fun `file log remains below two generation cap`() {
        val directory = Files.createTempDirectory("diagnostics-test").toFile()
        try {
            val log = FileDiagnosticsLog(directory)
            repeat(200) { log.append("x".repeat(4_000)) }
            assertTrue(log.files().sumOf { it.length() } <= FileDiagnosticsLog.MAX_TOTAL_BYTES)
        } finally { directory.deleteRecursively() }
    }

    @Test fun `uncaught handler writes then delegates to previous handler`() {
        val directory = Files.createTempDirectory("uncaught-test").toFile()
        try {
            val file = FileDiagnosticsLog(directory)
            val formatter = DiagnosticsFormatter { "" }
            val log = object : DiagnosticsLog {
                override fun log(level: DiagnosticsLevel, tag: String, message: String, error: Throwable?) = logNow(level, tag, message, error)
                override fun logNow(level: DiagnosticsLevel, tag: String, message: String, error: Throwable?) { file.append(formatter.format(level, tag, message, error)) }
            }
            var delegated = false
            val handler = DiagnosticsUncaughtExceptionHandler(log, Thread.UncaughtExceptionHandler { _, _ -> delegated = true })
            handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
            assertTrue(delegated)
            assertTrue(file.files().flatMap { it.takeIf(File::exists)?.readLines().orEmpty() }.joinToString().contains("boom"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun `normal diagnostic work runs off the calling thread`() {
        val caller = Thread.currentThread().name
        var worker = ""
        val lock = Object()
        AsyncDiagnosticsWriter.submit { synchronized(lock) { worker = Thread.currentThread().name; lock.notifyAll() } }
        synchronized(lock) { if (worker.isEmpty()) lock.wait(2_000) }
        assertFalse(worker.isEmpty())
        assertFalse(worker == caller)
    }
}
