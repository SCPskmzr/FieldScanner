package com.fieldscanner.app.display.zones

/** Timing rules for the lower-left temperature gesture, isolated from Android touch events. */
internal object LeftTemperatureGesture {
    const val SHOW_TEMPERATURE_AFTER_MILLIS = 600L
    const val REFRESH_TEMPERATURE_AFTER_MILLIS = 5_000L

    fun shouldShowTemperature(elapsedMillis: Long): Boolean = elapsedMillis >= SHOW_TEMPERATURE_AFTER_MILLIS
    fun shouldRefreshTemperature(elapsedMillis: Long): Boolean = elapsedMillis >= REFRESH_TEMPERATURE_AFTER_MILLIS
}
