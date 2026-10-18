package com.fieldscanner.app.display

import com.fieldscanner.app.display.zones.DeviceIndicators

/**
 * Ports for the independent left system display. Concrete implementations
 * (Open-Meteo, the sticky battery broadcast) live behind these seams and are
 * assembled in [com.fieldscanner.app.AppContainer]; `DisplayZonesView` never
 * constructs them.
 */
internal interface OutdoorTemperatureProvider {
    /** Invoked on the main thread with the latest reading, or null when unavailable. */
    var onTemperatureChanged: (Float?) -> Unit
    fun start()
    fun stop()
    /** Forces an immediate refresh; used only after the explicit five-second gesture. */
    fun refreshNow()
}

internal interface DeviceIndicatorProvider {
    /** Invoked on the main thread whenever a system reading changes. */
    var onIndicatorsChanged: (DeviceIndicators) -> Unit
    fun start()
    fun stop()
}

/**
 * Pure aggregation of the left-display signals: it merges the battery reading and
 * the outdoor temperature into one [DeviceIndicators] and forwards lifecycle
 * calls to both providers. Holds no Android or I/O types, so it is unit-testable
 * with fake providers.
 */
internal class LeftDisplayInputs(
    private val temperature: OutdoorTemperatureProvider,
    private val indicators: DeviceIndicatorProvider
) {
    /** Invoked on the main thread with the merged indicators. */
    var onIndicatorsChanged: (DeviceIndicators) -> Unit = {}
    private var current = DeviceIndicators()

    init {
        indicators.onIndicatorsChanged = { value ->
            current = value.copy(outdoorTemperatureCelsius = current.outdoorTemperatureCelsius)
            onIndicatorsChanged(current)
        }
        temperature.onTemperatureChanged = { value ->
            current = current.copy(outdoorTemperatureCelsius = value)
            onIndicatorsChanged(current)
        }
    }

    fun start() {
        indicators.start()
        temperature.start()
    }

    fun stop() {
        indicators.stop()
        temperature.stop()
    }

    fun refreshTemperatureNow() = temperature.refreshNow()
}
