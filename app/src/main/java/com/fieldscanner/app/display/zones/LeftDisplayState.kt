package com.fieldscanner.app.display.zones

/** Immutable system readings used by the left display. Null means Android did not provide it. */
internal data class DeviceIndicators(
    val batteryPercent: Int? = null,
    val outdoorTemperatureCelsius: Float? = null
)

internal enum class LeftDisplayContent { BATTERY, TEMPERATURE, TEMPERATURE_WARNING, UNAVAILABLE }

/**
 * Pure priority rule for the independent left display.
 *
 * Analysis progress is intentionally NOT shown here: during a scan the left zone
 * keeps its default battery indicator (per user request; supersedes TASK-014).
 */
internal object LeftDisplayState {
    const val OUTDOOR_COLD_WARNING_CELSIUS = 0f
    const val OUTDOOR_HEAT_WARNING_CELSIUS = 30f

    fun contentFor(indicators: DeviceIndicators, showTemperature: Boolean = false): LeftDisplayContent = when {
        showTemperature -> LeftDisplayContent.TEMPERATURE
        indicators.outdoorTemperatureCelsius?.let(::isOutdoorTemperatureWarning) == true -> LeftDisplayContent.TEMPERATURE_WARNING
        indicators.batteryPercent != null -> LeftDisplayContent.BATTERY
        else -> LeftDisplayContent.UNAVAILABLE
    }

    fun isOutdoorTemperatureWarning(temperatureCelsius: Float): Boolean =
        temperatureCelsius <= OUTDOOR_COLD_WARNING_CELSIUS || temperatureCelsius >= OUTDOOR_HEAT_WARNING_CELSIUS
}
