package org.nmapremake.engine

import org.junit.jupiter.api.Test
import org.nmapremake.core.capability.Executors
import org.nmapremake.core.model.PortSpec
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.model.Target
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultAggregatorTest {
    private val aggregator = ResultAggregator()
    private val plan = ScanPlan(targets = listOf(Target("127.0.0.1")), tcpPorts = PortSpec.Single(80))

    @Test
    fun `report envelope carries plan executor timestamps and hosts`() {
        val host =
            org.nmapremake.core.model.HostResult(
                target = Target("127.0.0.1"),
                portResults = emptyList(),
                executor = Executors.LOCAL_ANDROID,
                capabilities = Executors.LOCAL_ANDROID.capabilities,
                startedAtEpochMs = 100,
                finishedAtEpochMs = 200,
            )
        val report = aggregator.aggregate(plan, Executors.LOCAL_ANDROID, listOf(host), 100, 200)
        assertEquals(1, report.schemaVersion)
        assertEquals(ResultAggregator.GENERATOR, report.generator)
        assertEquals(plan, report.scanPlan)
        assertEquals(Executors.LOCAL_ANDROID, report.executor)
        assertEquals(listOf(host), report.hosts)
        assertEquals(100, report.startedAtEpochMs)
        assertEquals(200, report.finishedAtEpochMs)
    }

    @Test
    fun `empty host list is preserved`() {
        val report = aggregator.aggregate(plan, Executors.LOCAL_ANDROID, emptyList(), 0, 1)
        assertTrue(report.hosts.isEmpty())
    }
}
