package com.fieldscanner.app.data.finding

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanOutlineCatalogTest {
    @Test
    fun `catalogue has exactly 100 entries with UNKNOWN_OBJECT last`() {
        val values = ScanVisual.values()
        assertEquals(100, values.size)
        assertEquals(ScanVisual.UNKNOWN_OBJECT, values.last())
    }

    @Test
    fun `ids equal names and asset names are unique and derived`() {
        val values = ScanVisual.values()
        assertTrue(values.all { it.id == it.name })
        assertTrue(values.all { it.assetName == it.name.lowercase(Locale.US) + ".svg" })
        val assets = values.map { it.assetName }
        assertEquals(assets.size, assets.toSet().size)
        assertFalse(values.any { it.assetName.isBlank() || !it.assetName.endsWith(".svg") })
    }

    @Test
    fun `sample identifiers map to lower-case svg assets`() {
        assertEquals("cat.svg", ScanVisual.CAT.assetName)
        assertEquals("pen_pencil.svg", ScanVisual.PEN_PENCIL.assetName)
        assertEquals("unknown_object.svg", ScanVisual.UNKNOWN_OBJECT.assetName)
    }

    @Test
    fun `resolve maps a known id case-insensitively`() {
        assertEquals(ScanVisual.CAT, ScanVisual.resolve("  cat "))
        assertEquals(ScanVisual.PEN_PENCIL, ScanVisual.resolve("pen_pencil"))
    }

    @Test
    fun `resolve collapses unknown, blank, null and legacy input to UNKNOWN_OBJECT`() {
        assertEquals(ScanVisual.UNKNOWN_OBJECT, ScanVisual.resolve("DRAGON"))
        assertEquals(ScanVisual.UNKNOWN_OBJECT, ScanVisual.resolve("NEUTRAL"))
        assertEquals(ScanVisual.UNKNOWN_OBJECT, ScanVisual.resolve("ANIMAL_GENERIC"))
        assertEquals(ScanVisual.UNKNOWN_OBJECT, ScanVisual.resolve("  "))
        assertEquals(ScanVisual.UNKNOWN_OBJECT, ScanVisual.resolve(null))
    }

    @Test
    fun `fromId returns null for unknown input`() {
        assertNull(ScanVisual.fromId("DRAGON"))
        assertNull(ScanVisual.fromId(null))
    }

    @Test
    fun `a specific archetype falls back only to UNKNOWN_OBJECT`() {
        assertEquals(listOf(ScanVisual.CAT, ScanVisual.UNKNOWN_OBJECT), ScanVisual.CAT.fallbackChain())
        assertEquals(listOf(ScanVisual.BONE, ScanVisual.UNKNOWN_OBJECT), ScanVisual.BONE.fallbackChain())
    }

    @Test
    fun `UNKNOWN_OBJECT only falls back to itself`() {
        assertEquals(listOf(ScanVisual.UNKNOWN_OBJECT), ScanVisual.UNKNOWN_OBJECT.fallbackChain())
    }

    @Test
    fun `no category-generic entry exists`() {
        assertTrue(ScanVisual.values().none { it.name.endsWith("_GENERIC") || it.name == "NEUTRAL" })
    }
}
