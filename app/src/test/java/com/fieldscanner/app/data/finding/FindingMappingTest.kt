package com.fieldscanner.app.data.finding

import org.junit.Assert.assertEquals
import org.junit.Test

class FindingMappingTest {
    @Test
    fun `entity round trip preserves nullable, location and visual fields`() {
        val entity = FindingEntity(
            "id", "file:///photo.jpg", "Leaf", "Green leaf", 0.86,
            ScanVisual.LEAF.id, null, 52.23, 21.01, 12.5f, 123L
        )

        val restored = entity.toDomain().toEntity()

        assertEquals(entity, restored)
        assertEquals(ScanVisual.LEAF.id, restored.visualId)
    }

    @Test
    fun `UNKNOWN_OBJECT visual id survives the round trip`() {
        val entity = FindingEntity(
            "id", "file:///photo.jpg", "Old", "Legacy row", 0.0,
            ScanVisual.UNKNOWN_OBJECT.id, null, null, null, null, 1L
        )

        assertEquals(entity, entity.toDomain().toEntity())
    }
}
