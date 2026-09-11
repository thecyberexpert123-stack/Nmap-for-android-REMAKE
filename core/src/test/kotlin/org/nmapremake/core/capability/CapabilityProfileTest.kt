package org.nmapremake.core.capability

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapabilityProfileTest {
    @Test
    fun `missing entries are UNKNOWN`() {
        val profile = CapabilityProfile()
        assertEquals(Availability.UNKNOWN, profile.availability(Capability.TCP_CONNECT))
    }

    @Test
    fun `supports is true only for executable states`() {
        val profile =
            CapabilityProfile(
                mapOf(
                    Capability.TCP_CONNECT to Availability.SUPPORTED,
                    Capability.UDP_APP_PROBES to Availability.LIMITED,
                    Capability.SERVICE_DETECTION to Availability.NOT_IMPLEMENTED,
                    Capability.RAW_PACKET_TRANSMIT to Availability.UNSUPPORTED,
                ),
            )
        assertTrue(profile.supports(Capability.TCP_CONNECT))
        assertTrue(profile.supports(Capability.UDP_APP_PROBES))
        assertFalse(profile.supports(Capability.SERVICE_DETECTION))
        assertFalse(profile.supports(Capability.RAW_PACKET_TRANSMIT))
        assertFalse(profile.supports(Capability.TRACEROUTE))
    }

    @Test
    fun `local M1 profile matches capability matrix column E1`() {
        val profile = CapabilityProfile.ANDROID_LOCAL_M1
        assertEquals(Availability.SUPPORTED, profile.availability(Capability.TCP_CONNECT))
        assertEquals(Availability.SUPPORTED, profile.availability(Capability.RESULT_EXPORT_JSON))
        assertEquals(Availability.SUPPORTED, profile.availability(Capability.RAW_PACKET_BUILD))
        assertEquals(Availability.NOT_IMPLEMENTED, profile.availability(Capability.UDP_APP_PROBES))
        assertEquals(Availability.NOT_IMPLEMENTED, profile.availability(Capability.SERVICE_DETECTION))
        assertEquals(Availability.NOT_IMPLEMENTED, profile.availability(Capability.FINGERPRINT_INFERENCE))
        assertEquals(Availability.NOT_IMPLEMENTED, profile.availability(Capability.SCRIPT_PIPELINE))
        assertEquals(Availability.NOT_IMPLEMENTED, profile.availability(Capability.HOST_DISCOVERY_CONNECT))
        assertEquals(Availability.NOT_IMPLEMENTED, profile.availability(Capability.FTP_BOUNCE))
        assertEquals(Availability.NOT_IMPLEMENTED, profile.availability(Capability.REMOTE_DELEGATION))
        assertEquals(Availability.UNSUPPORTED, profile.availability(Capability.HOST_DISCOVERY_RAW))
        assertEquals(Availability.UNSUPPORTED, profile.availability(Capability.RAW_PACKET_TRANSMIT))
        assertEquals(Availability.UNSUPPORTED, profile.availability(Capability.PACKET_CAPTURE))
        assertEquals(Availability.UNSUPPORTED, profile.availability(Capability.OS_DETECTION_NATIVE))
        assertEquals(Availability.UNSUPPORTED, profile.availability(Capability.IDLE_SCAN))
        assertEquals(Availability.UNKNOWN, profile.availability(Capability.TRACEROUTE))
    }

    @Test
    fun `local executor node carries matrix E1 identity`() {
        val executor = Executors.LOCAL_ANDROID
        assertEquals("local-android", executor.id)
        assertEquals(ExecutorType.ANDROID_LOCAL, executor.type)
        assertEquals(ExecutorReachability.LOCAL, executor.reachability)
        assertEquals(TrustLevel.LOCAL, executor.trustLevel)
        assertEquals("direct", executor.transport)
        assertEquals("built-in", executor.discoveredVia)
        assertEquals(
            "Matrix E1: stock Android, no root. See docs/CAPABILITY-MATRIX.md",
            executor.capabilityProof,
        )
    }

    @Test
    fun `remote node fields default to null until M7`() {
        val node =
            ExecutorNode(
                id = "agent-1",
                label = "LAN agent",
                type = ExecutorType.REMOTE_AGENT,
                transport = "https+mTLS",
                reachability = ExecutorReachability.OFFLINE,
                trustLevel = TrustLevel.UNVERIFIED,
                capabilities = CapabilityProfile(),
            )
        assertNull(node.discoveredVia)
        assertNull(node.capabilityProof)
    }
}
