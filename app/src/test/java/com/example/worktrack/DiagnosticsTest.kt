package com.example.worktrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test fun `safe event excludes exception messages and personal data`() {
        val secret = "token=abc phone=+34123456789 address=Private Street"
        val error = IllegalStateException(secret, IllegalArgumentException("customer note"))

        val event = Diagnostics.safeEvent("Data write / customer", error, 1234)

        assertTrue(event.contains("TYPE java.lang.IllegalStateException"))
        assertTrue(event.contains("CAUSE java.lang.IllegalArgumentException"))
        assertTrue(event.contains("data_write___customer"))
        assertFalse(event.contains("abc"))
        assertFalse(event.contains("123456789"))
        assertFalse(event.contains("Private Street"))
        assertFalse(event.contains("customer note"))
    }

    @Test fun `retention removes old events and keeps only the newest fifty`() {
        val now = 40L * 24 * 60 * 60 * 1000
        val old = Diagnostics.safeEvent("old", RuntimeException("hidden"), 1)
        val recent = (1..60).map { index ->
            Diagnostics.safeEvent("event_$index", RuntimeException("hidden-$index"), now - 1000 + index)
        }

        val retained = Diagnostics.retainedEvents((listOf(old) + recent).joinToString("\n"), now)

        assertFalse(retained.contains(" old\n"))
        assertEquals(50, Regex("(?m)^BEGIN ").findAll(retained).count())
        assertFalse(retained.contains("event_10\n"))
        assertTrue(retained.contains("event_11\n"))
        assertTrue(retained.contains("event_60\n"))
        assertTrue(retained.toByteArray().size <= 128 * 1024)
    }
}
