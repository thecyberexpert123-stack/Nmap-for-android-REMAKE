package org.nmapremake.core.model

import org.junit.jupiter.api.Test
import org.nmapremake.core.capability.Capability
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanPlanTest {
    private val target = Target("example.com")

    @Test
    fun `defaults match the locked M1 values`() {
        val plan = ScanPlan(targets = listOf(target), tcpPorts = PortSpec.Single(80))
        assertEquals(5_000L, plan.probeTimeoutMs)
        assertEquals(32, plan.concurrency)
        assertEquals(600_000L, plan.maxDurationMs)
        assertTrue(plan.udpProbes.isEmpty())
        assertEquals(false, plan.serviceDetection)
        assertEquals(false, plan.fingerprinting)
    }

    @Test
    fun `clamped leaves valid values untouched`() {
        val plan =
            ScanPlan(
                targets = listOf(target),
                probeTimeoutMs = 2_000,
                concurrency = 16,
                maxDurationMs = 120_000,
            )
        val clamped = plan.clamped()
        assertEquals(2_000L, clamped.probeTimeoutMs)
        assertEquals(16, clamped.concurrency)
        assertEquals(120_000L, clamped.maxDurationMs)
    }

    @Test
    fun `clamped floors and caps numeric fields`() {
        val plan =
            ScanPlan(
                targets = listOf(target),
                probeTimeoutMs = 50,
                concurrency = 10_000,
                maxDurationMs = 500,
            ).clamped()
        assertEquals(100L, plan.probeTimeoutMs)
        assertEquals(256, plan.concurrency)
        assertEquals(1_000L, plan.maxDurationMs)
    }

    @Test
    fun `clamped preserves a null max duration`() {
        val plan = ScanPlan(targets = listOf(target), maxDurationMs = null).clamped()
        assertNull(plan.maxDurationMs)
    }

    @Test
    fun `required capabilities for a TCP-only plan`() {
        val plan = ScanPlan(targets = listOf(target), tcpPorts = PortSpec.Single(80))
        assertEquals(setOf(Capability.TCP_CONNECT), plan.requiredCapabilities())
    }

    @Test
    fun `required capabilities grow with plan features`() {
        val plan =
            ScanPlan(
                targets = listOf(target),
                tcpPorts = PortSpec.Single(80),
                udpProbes = listOf(UdpProbeSpec(payloadHex = "ab")),
                serviceDetection = true,
                fingerprinting = true,
            )
        assertEquals(
            setOf(
                Capability.TCP_CONNECT,
                Capability.UDP_APP_PROBES,
                Capability.SERVICE_DETECTION,
                Capability.FINGERPRINT_INFERENCE,
            ),
            plan.requiredCapabilities(),
        )
    }

    @Test
    fun `plan without passes requires nothing`() {
        val plan = ScanPlan(targets = listOf(target))
        assertTrue(plan.requiredCapabilities().isEmpty())
    }
}
