package com.fieldscanner.app.data.finding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingsDatabaseMigrationTest {
    @Test
    fun `migration 2 to 3 bridges the shipped schema versions`() {
        assertEquals(2, FindingsDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, FindingsDatabase.MIGRATION_2_3.endVersion)
    }

    @Test
    fun `migration 2 to 3 adds a non-null visualId column defaulting to UNKNOWN_OBJECT`() {
        val sql = FindingsDatabase.MIGRATION_2_3_SQL

        assertTrue(sql, sql.contains("ADD COLUMN visualId TEXT"))
        assertTrue(sql, sql.contains("NOT NULL"))
        assertTrue(sql, sql.contains("DEFAULT 'UNKNOWN_OBJECT'"))
        assertEquals("UNKNOWN_OBJECT", ScanVisual.UNKNOWN_OBJECT.id)
    }

    @Test
    fun `the migration default resolves to the terminal catalogue entry for legacy rows`() {
        assertEquals(ScanVisual.UNKNOWN_OBJECT, ScanVisual.resolve(ScanVisual.UNKNOWN_OBJECT.id))
        assertEquals(
            listOf(ScanVisual.UNKNOWN_OBJECT),
            ScanVisual.resolve(ScanVisual.UNKNOWN_OBJECT.id).fallbackChain()
        )
    }
}
