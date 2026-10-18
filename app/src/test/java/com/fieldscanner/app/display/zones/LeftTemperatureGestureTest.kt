package com.fieldscanner.app.display.zones

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeftTemperatureGestureTest {
    @Test
    fun `temperature is revealed after a normal long press`() {
        assertFalse(LeftTemperatureGesture.shouldShowTemperature(599L))
        assertTrue(LeftTemperatureGesture.shouldShowTemperature(600L))
    }

    @Test
    fun `weather refresh requires five full seconds`() {
        assertFalse(LeftTemperatureGesture.shouldRefreshTemperature(4_999L))
        assertTrue(LeftTemperatureGesture.shouldRefreshTemperature(5_000L))
    }
}
