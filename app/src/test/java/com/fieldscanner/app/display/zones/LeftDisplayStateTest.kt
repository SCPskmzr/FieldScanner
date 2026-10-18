package com.fieldscanner.app.display.zones

import org.junit.Assert.assertEquals
import org.junit.Test

class LeftDisplayStateTest {
    @Test
    fun `battery is the default content when its reading is available`() {
        assertEquals(LeftDisplayContent.BATTERY, LeftDisplayState.contentFor(DeviceIndicators(batteryPercent = 74)))
    }

    @Test
    fun `analysis does not change the left zone`() {
        // Analysis progress was removed from the left display; it stays on battery.
        assertEquals(LeftDisplayContent.BATTERY, LeftDisplayState.contentFor(DeviceIndicators(batteryPercent = 74)))
    }

    @Test
    fun `outdoor temperature warning has priority over battery`() {
        assertEquals(LeftDisplayContent.TEMPERATURE_WARNING, LeftDisplayState.contentFor(DeviceIndicators(batteryPercent = 74, outdoorTemperatureCelsius = 31f)))
    }

    @Test
    fun `only unsafe outdoor temperatures trigger a warning`() {
        assertEquals(false, LeftDisplayState.isOutdoorTemperatureWarning(12f))
        assertEquals(true, LeftDisplayState.isOutdoorTemperatureWarning(0f))
        assertEquals(true, LeftDisplayState.isOutdoorTemperatureWarning(30f))
    }

    @Test
    fun `missing system readings are represented safely`() {
        assertEquals(LeftDisplayContent.UNAVAILABLE, LeftDisplayState.contentFor(DeviceIndicators()))
    }
}
