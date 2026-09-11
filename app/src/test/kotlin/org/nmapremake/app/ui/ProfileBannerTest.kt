package org.nmapremake.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nmapremake.core.capability.Availability
import org.nmapremake.core.capability.Capability
import org.nmapremake.core.capability.CapabilityProfile

class ProfileBannerTest {
    @Test
    fun `banner lists only capabilities with a known availability`() {
        val rows = profileToBanner(CapabilityProfile.ANDROID_LOCAL_M1)
        // E1 marks TRACEROUTE as UNKNOWN; every other capability is known.
        assertEquals(Capability.entries.size - 1, rows.size)
        assertTrue(rows.none { it.availability == Availability.UNKNOWN })
    }

    @Test
    fun `banner order follows the canonical capability order`() {
        val rows = profileToBanner(CapabilityProfile.ANDROID_LOCAL_M1)
        assertEquals(rows.map { it.capability }, rows.map { it.capability }.sortedBy { it.ordinal })
    }

    @Test
    fun `every row has non-blank title and rationale`() {
        val rows = profileToBanner(CapabilityProfile.ANDROID_LOCAL_M1)
        assertTrue(rows.isNotEmpty())
        for (row in rows) {
            assertTrue(row.title.isNotBlank(), "blank title for ${row.capability}")
            assertTrue(row.rationale.isNotBlank(), "blank rationale for ${row.capability}")
        }
    }

    @Test
    fun `TCP connect row reflects matrix E1`() {
        val row = profileToBanner(CapabilityProfile.ANDROID_LOCAL_M1)
            .first { it.capability == Capability.TCP_CONNECT }
        assertEquals(Availability.SUPPORTED, row.availability)
        assertEquals("TCP connect scan", row.title)
    }

    @Test
    fun `empty profile yields an empty banner`() {
        assertTrue(profileToBanner(CapabilityProfile()).isEmpty())
    }
}
