package com.fieldscanner.app.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMeteoTemperatureClientTest {
    @Test
    fun `extracts current two metre temperature from Open Meteo response`() {
        assertEquals(17.4f, OpenMeteoTemperatureClient.parseTemperature("""{"current_units":{"temperature_2m":"°C"},"current":{"temperature_2m":17.4}}"""))
    }

    @Test
    fun `missing temperature is safe`() {
        assertNull(OpenMeteoTemperatureClient.parseTemperature("""{"current":{}}"""))
    }
}
