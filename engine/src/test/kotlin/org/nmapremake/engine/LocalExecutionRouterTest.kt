package org.nmapremake.engine

import org.junit.jupiter.api.Test
import org.nmapremake.core.capability.Capability
import org.nmapremake.core.capability.Executors
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.PortSpec
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.model.Target
import org.nmapremake.core.model.UdpProbeSpec
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LocalExecutionRouterTest {
    private val router = LocalExecutionRouter()
    private val target = Target("example.com")

    @Test
    fun `TCP-only plan selects the local executor`() {
        val selection = router.route(ScanPlan(targets = listOf(target), tcpPorts = PortSpec.Single(80)))
        val selected = assertIs<ExecutorSelection.Selected>(selection)
        assertEquals(Executors.LOCAL_ANDROID, selected.executor)
    }

    @Test
    fun `service detection is rejected as unsupported in M1`() {
        val selection =
            router.route(
                ScanPlan(targets = listOf(target), tcpPorts = PortSpec.Single(80), serviceDetection = true),
            )
        val rejected = assertIs<ExecutorSelection.Rejected>(selection)
        assertEquals(listOf(Capability.SERVICE_DETECTION), rejected.missing)
        assertEquals(ErrorCode.CAPABILITY_UNSUPPORTED, rejected.error.errorCode())
    }

    @Test
    fun `UDP probes are rejected as unsupported in M1`() {
        val selection =
            router.route(
                ScanPlan(
                    targets = listOf(target),
                    udpProbes = listOf(UdpProbeSpec(payloadHex = "ab")),
                ),
            )
        val rejected = assertIs<ExecutorSelection.Rejected>(selection)
        assertEquals(listOf(Capability.UDP_APP_PROBES), rejected.missing)
    }

    @Test
    fun `multiple missing capabilities are reported sorted`() {
        val selection =
            router.route(
                ScanPlan(
                    targets = listOf(target),
                    udpProbes = listOf(UdpProbeSpec(payloadHex = "ab")),
                    serviceDetection = true,
                    fingerprinting = true,
                ),
            )
        val rejected = assertIs<ExecutorSelection.Rejected>(selection)
        assertEquals(
            listOf(
                Capability.FINGERPRINT_INFERENCE,
                Capability.SERVICE_DETECTION,
                Capability.UDP_APP_PROBES,
            ),
            rejected.missing,
        )
    }
}
