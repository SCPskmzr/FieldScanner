package com.fieldscanner.app.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-arithmetic checks for the uniform letterbox fit used by DisplayZonesView (TASK-033). */
class DisplayViewportTest {

    private val artworkW = DisplayGeometry.WIDTH
    private val artworkH = DisplayGeometry.HEIGHT

    @Test
    fun `wider than artwork viewport gets vertical bars`() {
        val t = DisplayViewport.of(viewWidth = 2000, viewHeight = 745)

        // Height is the limiting dimension, so scale locks to the vertical fit.
        assertEquals(745f / artworkH, t.scale, EPS)
        assertEquals((2000f - artworkW * t.scale) / 2f, t.offsetX, EPS)
        assertTrue("expected left/right letterbox bars", t.offsetX > 0f)
        assertEquals(0f, t.offsetY, EPS)

        // A tap in the left bar is outside the artwork; just inside the bar edge is on it.
        assertFalse(t.contains(t.offsetX - 1f, 400f))
        assertTrue(t.contains(t.offsetX + 1f, 400f))
    }

    @Test
    fun `taller than artwork viewport gets horizontal bars`() {
        val t = DisplayViewport.of(viewWidth = 1286, viewHeight = 1500)

        assertEquals(1286f / artworkW, t.scale, EPS)
        assertEquals(0f, t.offsetX, EPS)
        assertEquals((1500f - artworkH * t.scale) / 2f, t.offsetY, EPS)
        assertTrue("expected top/bottom letterbox bars", t.offsetY > 0f)

        assertFalse(t.contains(600f, t.offsetY - 1f))
        assertTrue(t.contains(600f, t.offsetY + 1f))
    }

    @Test
    fun `exact ratio viewport has no offset`() {
        val t = DisplayViewport.of(viewWidth = 2572, viewHeight = 1490)

        assertEquals(2f, t.scale, EPS)
        assertEquals(0f, t.offsetX, EPS)
        assertEquals(0f, t.offsetY, EPS)
        assertTrue(t.contains(0f, 0f))
        assertTrue(t.contains(2572f, 1490f))
        assertFalse(t.contains(2573f, 745f))
    }

    @Test
    fun `safe area inset shrinks and shifts the fit`() {
        val plain = DisplayViewport.of(viewWidth = 2000, viewHeight = 745)
        val inset = DisplayViewport.of(viewWidth = 2000, viewHeight = 745, insetLeft = 100)

        // Height still limits, so scale is unchanged; the artwork is centred in the
        // narrower safe box, so it shifts right by half the removed width plus the inset.
        assertEquals(plain.scale, inset.scale, EPS)
        assertEquals(100f + (1900f - artworkW * inset.scale) / 2f, inset.offsetX, EPS)
        assertEquals(0f, inset.offsetY, EPS)

        // The inset strip is now letterbox margin, not artwork.
        assertFalse(inset.contains(50f, 400f))
        assertTrue(inset.contains(inset.offsetX + 1f, 400f))
    }

    @Test
    fun `virtual point round trips through screen space`() {
        val t = DisplayViewport.of(viewWidth = 1930, viewHeight = 1000, insetTop = 40)

        val vx = 643f
        val vy = 372.5f
        val backX = t.toVirtualX(t.toScreenX(vx))
        val backY = t.toVirtualY(t.toScreenY(vy))

        assertEquals(vx, backX, EPS)
        assertEquals(vy, backY, EPS)
    }

    @Test
    fun `degenerate viewport falls back to identity`() {
        val t = DisplayViewport.of(viewWidth = 0, viewHeight = 0)
        assertEquals(1f, t.scale, EPS)
        assertEquals(0f, t.offsetX, EPS)
        assertEquals(0f, t.offsetY, EPS)
    }

    private companion object {
        const val EPS = 1e-3f
    }
}
