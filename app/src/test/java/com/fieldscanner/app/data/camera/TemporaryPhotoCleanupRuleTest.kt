package com.fieldscanner.app.data.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryPhotoCleanupRuleTest {
    private val sessionStart = 1_000L

    @Test
    fun `removes scan jpg from an earlier session`() {
        assertTrue(TemporaryPhotoCleanupRule.shouldDelete("SCAN_20260831_120000.jpg", 999L, sessionStart))
    }

    @Test
    fun `keeps current session scan jpg`() {
        assertFalse(TemporaryPhotoCleanupRule.shouldDelete("SCAN_20260831_120000.jpg", sessionStart, sessionStart))
    }

    @Test
    fun `does not select durable finding media`() {
        assertFalse(TemporaryPhotoCleanupRule.shouldDelete("finding-id.jpg", 1L, sessionStart))
        assertFalse(TemporaryPhotoCleanupRule.shouldDelete("finding-id.mp3", 1L, sessionStart))
    }
}
