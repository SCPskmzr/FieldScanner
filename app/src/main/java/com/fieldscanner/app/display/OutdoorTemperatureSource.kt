package com.fieldscanner.app.display

import android.os.Handler
import android.os.Looper
import com.fieldscanner.app.data.location.DeviceLocationProvider
import com.fieldscanner.app.data.net.HttpClient
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Swappable seam for the outdoor-temperature lookup so tests never touch the network. */
internal fun interface TemperatureClient {
    fun currentTemperature(latitude: Double, longitude: Double): Float?
}

/**
 * Best-effort outdoor temperature from Open-Meteo for the device's current location.
 * It keeps neither the coordinates nor the result on disk and refreshes only while visible.
 *
 * Assembled behind [OutdoorTemperatureProvider] in `AppContainer`; the concrete
 * location and HTTP clients are injected rather than constructed per fetch.
 */
internal class OutdoorTemperatureSource(
    private val locationProvider: DeviceLocationProvider,
    private val temperatureClient: TemperatureClient = OpenMeteoTemperatureClient()
) : OutdoorTemperatureProvider {
    override var onTemperatureChanged: (Float?) -> Unit = {}
    private val mainHandler = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null
    @Volatile private var active = false
    private val refresh = Runnable { fetch() }

    override fun start() {
        if (active) return
        active = true
        executor = Executors.newSingleThreadExecutor()
        fetch()
    }

    override fun stop() {
        active = false
        mainHandler.removeCallbacks(refresh)
        executor?.shutdownNow()
        executor = null
    }

    /** Forces an immediate fresh lookup; used only after the explicit five-second gesture. */
    override fun refreshNow() {
        if (!active) return
        mainHandler.removeCallbacks(refresh)
        fetch()
    }

    private fun fetch() {
        executor?.execute {
            val temperature = runCatching {
                val location = locationProvider.getCurrentLocation() ?: return@runCatching null
                temperatureClient.currentTemperature(location.latitude, location.longitude)
            }.getOrNull()
            mainHandler.post {
                if (!active) return@post
                onTemperatureChanged(temperature)
                mainHandler.postDelayed(refresh, REFRESH_MILLIS)
            }
        }
    }

    private companion object { const val REFRESH_MILLIS = 30 * 60 * 1_000L }
}

/** Minimal Open-Meteo client; only the current 2 m outdoor air temperature is requested. */
internal class OpenMeteoTemperatureClient : TemperatureClient {
    override fun currentTemperature(latitude: Double, longitude: Double): Float? {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m&temperature_unit=celsius",
            latitude,
            longitude
        )
        return try {
            HttpClient.execute(
                url = url,
                connectTimeoutMillis = TIMEOUT_MILLIS,
                readTimeoutMillis = TIMEOUT_MILLIS
            ) { stream -> parseTemperature(stream.bufferedReader().use { it.readText() }) }
        } catch (_: HttpClient.HttpException) {
            null
        }
    }

    companion object {
        private const val TIMEOUT_MILLIS = 10_000
        private val TEMPERATURE_PATTERN = Regex("\\\"temperature_2m\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")

        internal fun parseTemperature(response: String): Float? = TEMPERATURE_PATTERN
            .find(response)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }
}
