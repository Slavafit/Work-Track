package com.example.worktrack.license

import org.junit.Assert.*
import org.junit.Test

class LicenseCacheTest {
    @Test fun `manual retry always bypasses a fresh cache`() {
        assertFalse(shouldUseLicenseCache(true, 1000, 1001))
        assertTrue(shouldUseLicenseCache(false, 1000, 1001))
    }

    @Test fun `missing expired and future cache timestamps trigger verification`() {
        assertFalse(shouldUseLicenseCache(false, 0, 1000))
        assertFalse(shouldUseLicenseCache(false, 1000, 1000 + 86_400_000))
        assertFalse(shouldUseLicenseCache(false, 2000, 1000))
    }
}
