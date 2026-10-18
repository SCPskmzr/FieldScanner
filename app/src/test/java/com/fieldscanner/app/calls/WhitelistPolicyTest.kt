package com.fieldscanner.app.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistPolicyTest {
    @Test fun `allows both configured contacts`() {
        assertTrue(WhitelistPolicy.isAllowed("+48883264766"))
        assertTrue(WhitelistPolicy.isAllowed("+48883532683"))
    }

    @Test fun `normalizes harmless formatting`() {
        assertTrue(WhitelistPolicy.isAllowed("+48 883-264-766"))
        assertTrue(WhitelistPolicy.isAllowed("(+48) 883 532 683"))
    }

    @Test fun `rejects unknown hidden and malformed callers`() {
        assertFalse(WhitelistPolicy.isAllowed("+48111222333"))
        assertFalse(WhitelistPolicy.isAllowed(null))
        assertFalse(WhitelistPolicy.isAllowed("not a number"))
    }
}
