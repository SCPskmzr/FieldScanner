package com.fieldscanner.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalAnalysisTest {
    @Test
    fun `selects prominent extrema in display order`() {
        val samples = listOf(
            SignalSample(0f, 10f), SignalSample(10f, 10f), SignalSample(20f, 10f),
            SignalSample(30f, 10f), SignalSample(40f, 1f), SignalSample(50f, 10f),
            SignalSample(60f, 10f), SignalSample(70f, 10f), SignalSample(80f, 10f),
            SignalSample(90f, 10f), SignalSample(100f, 19f), SignalSample(110f, 10f),
            SignalSample(120f, 10f), SignalSample(130f, 10f), SignalSample(140f, 10f)
        )

        val extrema = SignalAnalysis.selectKeyExtrema(samples, 5, 20f, 5f)

        assertEquals(listOf(40f, 100f), extrema.map { it.sample.x })
        assertEquals(listOf(true, false), extrema.map { it.isMaximum })
    }

    @Test
    fun `interpolates sample for analysis cursor`() {
        val sample = SignalAnalysis.sampleAtX(
            listOf(SignalSample(0f, 0f), SignalSample(10f, 20f)),
            2.5f
        )

        assertEquals(2.5f, sample.x)
        assertEquals(5f, sample.y)
    }
}
