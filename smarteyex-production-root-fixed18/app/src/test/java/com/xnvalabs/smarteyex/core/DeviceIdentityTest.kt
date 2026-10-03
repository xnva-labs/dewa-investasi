package com.xnvalabs.smarteyex.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityTest {
    @Test fun acceptsUuidShapedIds() {
        assertTrue(DeviceIdentity.isValid("11111111-2222-3333-4444-555555555555"))
    }

    @Test fun rejectsShortLongAndUnsafeIds() {
        assertFalse(DeviceIdentity.isValid("short"))
        assertFalse(DeviceIdentity.isValid("a".repeat(65)))
        assertFalse(DeviceIdentity.isValid("11111111-2222-3333-4444-5555555555 5"))
        assertFalse(DeviceIdentity.isValid("11111111-2222-3333-4444-55555555555\n"))
    }
}
