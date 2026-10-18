package com.fieldscanner.app.data.net

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal shared wrapper over [HttpURLConnection]. It centralises the request
 * setup, timeouts, 2xx check and stream handling that the remote clients used to
 * repeat, without adding an HTTP dependency (see AGENTS.md, "Development rules").
 *
 * Request specifics — JSON bodies, response parsing, cancellation — stay in the
 * calling client. [onConnect] exposes the live connection so a caller can
 * [HttpURLConnection.disconnect] it to abort an in-flight request.
 */
internal object HttpClient {

    /** Raised for a non-2xx response; [body] is the decoded error stream. */
    class HttpException(val statusCode: Int, val body: String) :
        RuntimeException("HTTP request failed ($statusCode): $body")

    fun <T> execute(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
        readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
        body: ByteArray? = null,
        onConnect: (HttpURLConnection) -> Unit = {},
        readResponse: (InputStream) -> T
    ): T {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            doOutput = body != null
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        onConnect(connection)
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            if (connection.responseCode !in 200..299) {
                throw HttpException(
                    connection.responseCode,
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                )
            }
            return connection.inputStream.use(readResponse)
        } finally {
            connection.disconnect()
        }
    }

    private const val DEFAULT_TIMEOUT_MILLIS = 30_000
}
