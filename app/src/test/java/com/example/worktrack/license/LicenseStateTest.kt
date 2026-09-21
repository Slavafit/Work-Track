package com.example.worktrack.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseStateTest {
    @Test fun `license states map to the expected access mode`() {
        assertEquals(LicenseAccessMode.FULL, LicenseState.Active(1000).accessMode())
        assertEquals(LicenseAccessMode.FULL, LicenseState.Trial(1000).accessMode())
        assertEquals(LicenseAccessMode.EXPIRED_READ_ONLY, LicenseState.Invalid("expired").accessMode())
        assertEquals(LicenseAccessMode.EXPIRED_READ_ONLY, LicenseState.Invalid("trial_expired").accessMode())
        assertEquals(LicenseAccessMode.INVALID_READ_ONLY, LicenseState.Invalid("revoked").accessMode())
        assertEquals(LicenseAccessMode.NETWORK_READ_ONLY, LicenseState.Error("offline").accessMode())
        assertEquals(LicenseAccessMode.BLOCKED, LicenseState.Loading.accessMode())
        assertEquals(LicenseAccessMode.BLOCKED, LicenseState.NeedActivation.accessMode())
    }

    @Test fun `active license retains its server expiration`() {
        assertEquals(VerifyResult.Active(2000), localLicenseResult("active", 2000, nowSeconds = 1000))
    }

    @Test fun `zero remaining server time expires immediately`() {
        val result = localLicenseResult("active", 1000, nowSeconds = 1000)
        assertTrue(result is VerifyResult.Invalid)
        assertEquals("expired", (result as VerifyResult.Invalid).reason)
    }
}
