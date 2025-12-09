package com.tencent.tinker.test.internal.utils

import com.tencent.tinker.internal.util.CurrentSdk
import org.junit.Test

import org.junit.Assert.*

class SystemTest {

    /**
     * Tests comparing current sdk with or without preview version.
     */
    @Test
    fun compareCurrentSdk() {
        assertTrue(CurrentSdk(2, 0) > 1)
        assertFalse(CurrentSdk(2, 0) < 1)
        assertFalse(CurrentSdk(2, 0) <= 1)

        assertTrue(CurrentSdk(2, 0) >= 2)
        assertFalse(CurrentSdk(2, 0) < 2)

        assertTrue(CurrentSdk(2, 0) <= 2)
        assertFalse(CurrentSdk(2, 0) > 2)

        assertTrue(CurrentSdk(2, 0) < 3)
        assertFalse(CurrentSdk(2, 0) >= 3)

        assertTrue(CurrentSdk(2, 1) > 1)
        assertFalse(CurrentSdk(2, 1) < 1)
        assertFalse(CurrentSdk(2, 1) <= 1)

        assertTrue(CurrentSdk(2, 1) >= 2)
        assertFalse(CurrentSdk(2, 1) < 2)

        assertTrue(CurrentSdk(2, 1) <= 2)
        assertFalse(CurrentSdk(2, 1) > 2)

        assertTrue(CurrentSdk(2, 1) >= 3)
        assertFalse(CurrentSdk(2, 1) < 3)

        assertTrue(CurrentSdk(2, 1) < 4)
        assertFalse(CurrentSdk(2, 1) >= 4)
    }
}