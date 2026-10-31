package com.chatlite.charroom.core

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class AndroidUpdateStorageTest {
    @Test
    fun reclaimableCacheCanSupplyAnOtherwiseFullVolume() {
        var allocated = false
        ensureUpdateStorage(
            100,
            allocatable = { 100 },
            allocate = { allocated = true },
            available = { 0 },
        )
        assertTrue(allocated)
    }

    @Test
    fun insufficientReclaimableSpaceDoesNotStartAllocation() {
        var allocated = false
        assertThrows(IllegalArgumentException::class.java) {
            ensureUpdateStorage(
                100,
                allocatable = { 99 },
                allocate = { allocated = true },
                available = { 1000 },
            )
        }
        assertFalse(allocated)
    }

    @Test
    fun unsupportedVolumeFallsBackToActualFreeBytes() {
        ensureUpdateStorage(
            100,
            allocatable = { throw IOException("unsupported volume") },
            allocate = { fail("allocation must not run") },
            available = { 100 },
        )
        assertThrows(IllegalArgumentException::class.java) {
            ensureUpdateStorage(
                100,
                allocatable = { throw IOException("unsupported volume") },
                allocate = { fail("allocation must not run") },
                available = { 99 },
            )
        }
    }

    @Test
    fun failedAllocationStillRequiresEnoughActualFreeSpace() {
        assertThrows(IllegalArgumentException::class.java) {
            ensureUpdateStorage(
                100,
                allocatable = { 100 },
                allocate = { throw IOException("allocation failed") },
                available = { 0 },
            )
        }
    }

    @Test
    fun securityFailuresCannotBeConvertedIntoSuccessfulCapacityChecks() {
        assertThrows(SecurityException::class.java) {
            ensureUpdateStorage(
                100,
                allocatable = { throw SecurityException("fixture") },
                allocate = {},
                available = { 1000 },
            )
        }
    }
}
