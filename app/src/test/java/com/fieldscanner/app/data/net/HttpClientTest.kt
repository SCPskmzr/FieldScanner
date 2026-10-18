package com.fieldscanner.app.data.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.ServerSocket

/**
 * Exercises the shared HTTP helper against a hand-rolled localhost socket server.
 * Android unit tests compile against a restricted bootclasspath, so this avoids
 * `com.sun.net.httpserver` and uses only `java.net` / `java.io`.
 */
class HttpClientTest {
    private var server: ServerSocket? = null
    @Volatile private var requestLine: String? = null
    @Volatile private var requestHeaders: Map<String, String> = emptyMap()
    @Volatile private var requestBody: String? = null

    @After
    fun tearDown() {
        server?.close()
    }

    private fun start(statusLine: String, responseBody: String) {
        val socketServer = ServerSocket(0)
        server = socketServer
        Thread {
            runCatching {
                socketServer.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    requestLine = input.readLine()
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        val separator = line.indexOf(':')
                        if (separator > 0) {
                            headers[line.substring(0, separator).trim().lowercase()] =
                                line.substring(separator + 1).trim()
                        }
                    }
                    requestHeaders = headers
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) {
                        val count = input.read(body, read, length - read)
                        if (count < 0) break
                        read += count
                    }
                    requestBody = String(body, 0, read)

                    val payload = responseBody.toByteArray()
                    val head = "HTTP/1.1 $statusLine\r\n" +
                        "Content-Length: ${payload.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(head.toByteArray())
                        write(payload)
                        flush()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun url(path: String = "/") = "http://localhost:${server!!.localPort}$path"

    @Test
    fun `returns decoded body on a 2xx response`() {
        start("200 OK", "hello")

        val result = HttpClient.execute(url = url()) { it.bufferedReader().readText() }

        assertEquals("hello", result)
    }

    @Test
    fun `sends method, headers and request body`() {
        start("200 OK", "")

        HttpClient.execute(
            url = url("/echo"),
            method = "POST",
            headers = mapOf("X-Test" to "yes"),
            body = "payload".toByteArray()
        ) { it.readBytes() }

        assertTrue(requestLine!!.startsWith("POST "))
        assertEquals("yes", requestHeaders["x-test"])
        assertEquals("payload", requestBody)
    }

    @Test
    fun `non-2xx raises HttpException carrying status and error body`() {
        start("503 Service Unavailable", "nope")

        try {
            HttpClient.execute(url = url()) { it.bufferedReader().readText() }
            fail("expected HttpException")
        } catch (error: HttpClient.HttpException) {
            assertEquals(503, error.statusCode)
            assertEquals("nope", error.body)
        }
    }

    @Test
    fun `onConnect receives the live connection before the response is read`() {
        start("200 OK", "ok")
        var captured = false

        HttpClient.execute(
            url = url(),
            onConnect = { captured = true }
        ) { it.readBytes() }

        assertTrue(captured)
    }
}
