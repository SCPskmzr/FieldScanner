package com.fieldscanner.app.data.finding

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisResultTest {
    @Test
    fun `missing confidence defaults to zero`() {
        val result = AnalysisResult("  Leaf  ", "  A green leaf  ").validated()

        assertEquals("Leaf", result.title)
        assertEquals("A green leaf", result.description)
        assertEquals(0.0, result.confidence ?: -1.0, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of range confidence is rejected`() {
        AnalysisResult("Leaf", "A green leaf", 1.1).validated()
    }

    @Test
    fun `missing visual id defaults to UNKNOWN_OBJECT`() {
        assertEquals(ScanVisual.UNKNOWN_OBJECT.id, AnalysisResult("Leaf", "A green leaf").validated().visualId)
    }

    @Test
    fun `supported visual id is normalised and kept`() {
        val result = AnalysisResult("Cat", "A cat", visualId = "  cat ").validated()

        assertEquals(ScanVisual.CAT.id, result.visualId)
    }

    @Test
    fun `unknown and legacy visual ids collapse to UNKNOWN_OBJECT`() {
        assertEquals(
            ScanVisual.UNKNOWN_OBJECT.id,
            AnalysisResult("Thing", "Something", visualId = "DRAGON").validated().visualId
        )
        assertEquals(
            ScanVisual.UNKNOWN_OBJECT.id,
            AnalysisResult("Thing", "Something", visualId = "NEUTRAL").validated().visualId
        )
    }
}
