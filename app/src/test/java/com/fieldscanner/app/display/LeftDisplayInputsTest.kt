package com.fieldscanner.app.display

import com.fieldscanner.app.display.zones.DeviceIndicators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeftDisplayInputsTest {
    private class FakeTemperature : OutdoorTemperatureProvider {
        override var onTemperatureChanged: (Float?) -> Unit = {}
        var started = false
        var refreshed = 0
        override fun start() { started = true }
        override fun stop() { started = false }
        override fun refreshNow() { refreshed++ }
    }

    private class FakeIndicators : DeviceIndicatorProvider {
        override var onIndicatorsChanged: (DeviceIndicators) -> Unit = {}
        var started = false
        override fun start() { started = true }
        override fun stop() { started = false }
    }

    private val temperature = FakeTemperature()
    private val indicators = FakeIndicators()
    private var merged: DeviceIndicators? = null
    private val inputs = LeftDisplayInputs(temperature, indicators).apply {
        onIndicatorsChanged = { merged = it }
    }

    @Test
    fun `a temperature update keeps the last battery reading`() {
        indicators.onIndicatorsChanged(DeviceIndicators(batteryPercent = 61))
        temperature.onTemperatureChanged(-4f)

        assertEquals(DeviceIndicators(batteryPercent = 61, outdoorTemperatureCelsius = -4f), merged)
    }

    @Test
    fun `a battery update keeps the last temperature reading`() {
        temperature.onTemperatureChanged(21f)
        indicators.onIndicatorsChanged(DeviceIndicators(batteryPercent = 40))

        assertEquals(DeviceIndicators(batteryPercent = 40, outdoorTemperatureCelsius = 21f), merged)
    }

    @Test
    fun `a null temperature clears only the temperature`() {
        indicators.onIndicatorsChanged(DeviceIndicators(batteryPercent = 55))
        temperature.onTemperatureChanged(12f)
        temperature.onTemperatureChanged(null)

        assertEquals(DeviceIndicators(batteryPercent = 55, outdoorTemperatureCelsius = null), merged)
    }

    @Test
    fun `lifecycle and refresh calls are delegated to both providers`() {
        inputs.start()
        assertTrue(temperature.started)
        assertTrue(indicators.started)

        inputs.refreshTemperatureNow()
        assertEquals(1, temperature.refreshed)

        inputs.stop()
        assertEquals(false, temperature.started)
        assertEquals(false, indicators.started)
    }
}
