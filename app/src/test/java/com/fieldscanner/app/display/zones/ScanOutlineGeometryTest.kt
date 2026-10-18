package com.fieldscanner.app.display.zones

import com.fieldscanner.app.SignalSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanOutlineGeometryTest {
    @Test
    fun `extracts the requested outline path from SVG`() {
        val svg = """<svg><path id="outline" d="M1 2 L3 4"/><path id="ignored" d="M5 6"/></svg>"""

        assertEquals("M1 2 L3 4", ScanOutlineAsset.extractPathData(svg))
    }

    @Test
    fun `projects native 640x200 template points onto the lower zone`() {
        val topLeft = ScanMorphGeometry.projectTemplatePoint(0f, 0f)
        val bottomRight = ScanMorphGeometry.projectTemplatePoint(
            ScanMorphGeometry.TEMPLATE_WIDTH,
            ScanMorphGeometry.TEMPLATE_HEIGHT
        )

        assertEquals(ScanMorphGeometry.TARGET_LEFT, topLeft.x)
        assertEquals(ScanMorphGeometry.TARGET_TOP, topLeft.y)
        assertEquals(ScanMorphGeometry.TARGET_LEFT + ScanMorphGeometry.TARGET_WIDTH, bottomRight.x)
        assertEquals(ScanMorphGeometry.TARGET_TOP + ScanMorphGeometry.TARGET_HEIGHT, bottomRight.y)
        assertTrue(topLeft.x < bottomRight.x && topLeft.y < bottomRight.y)
    }

    @Test
    fun `template centre maps to the middle of the lower zone`() {
        val centre = ScanMorphGeometry.projectTemplatePoint(320f, 100f)

        assertEquals(ScanMorphGeometry.TARGET_LEFT + ScanMorphGeometry.TARGET_WIDTH / 2f, centre.x, 0.001f)
        assertEquals(ScanMorphGeometry.TARGET_TOP + ScanMorphGeometry.TARGET_HEIGHT / 2f, centre.y, 0.001f)
    }

    @Test
    fun `interpolation preserves endpoints at zero and one`() {
        val source = listOf(SignalSample(10f, 20f), SignalSample(30f, 40f))
        val target = listOf(SignalSample(50f, 60f), SignalSample(70f, 80f))

        assertEquals(source, ScanMorphGeometry.interpolate(source, target, 0f))
        assertEquals(target, ScanMorphGeometry.interpolate(source, target, 1f))
    }

    @Test
    fun `interpolation places intermediate coordinates between paths`() {
        val source = listOf(SignalSample(10f, 20f))
        val target = listOf(SignalSample(50f, 60f))

        val halfway = ScanMorphGeometry.interpolate(source, target, .5f).single()

        assertEquals(30f, halfway.x)
        assertEquals(40f, halfway.y)
    }
}
