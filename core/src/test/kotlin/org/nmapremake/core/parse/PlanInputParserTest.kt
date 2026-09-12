package org.nmapremake.core.parse

import org.junit.jupiter.api.Test
import org.nmapremake.core.model.PortSpec
import org.nmapremake.core.model.ScanPlan
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanInputParserTest {
    private fun success(input: PlanInputParser.Input): PlanInputParser.ParseOutcome.Success {
        return assertIs(PlanInputParser.parse(input))
    }

    private fun failure(input: PlanInputParser.Input): PlanInputParser.ParseOutcome.Failure {
        return assertIs(PlanInputParser.parse(input))
    }

    @Test
    fun `happy path with top-100`() {
        val plan = success(PlanInputParser.Input("example.com", "top-100")).plan
        assertEquals(1, plan.targets.size)
        assertEquals("example.com", plan.targets.single().label)
        assertIs<PortSpec.TopPorts>(plan.tcpPorts)
    }

    @Test
    fun `blank ports means TCP pass skipped`() {
        val plan = success(PlanInputParser.Input("example.com", "  ")).plan
        assertNull(plan.tcpPorts)
    }

    @Test
    fun `single port parsed`() {
        assertEquals(PortSpec.Single(80), success(PlanInputParser.Input("h", "80")).plan.tcpPorts)
    }

    @Test
    fun `range parsed`() {
        assertEquals(PortSpec.Range(1, 5), success(PlanInputParser.Input("h", "1-5")).plan.tcpPorts)
    }

    @Test
    fun `all parsed`() {
        assertEquals(PortSpec.All, success(PlanInputParser.Input("h", "all")).plan.tcpPorts)
    }

    @Test
    fun `list parsed with duplicates removed and order kept`() {
        val ports = assertIs<PortSpec.List>(success(PlanInputParser.Input("h", "80,443,80")).plan.tcpPorts)
        assertEquals(listOf(80, 443), ports.ports)
    }

    @Test
    fun `mixed list expands ranges in place`() {
        val ports = assertIs<PortSpec.List>(success(PlanInputParser.Input("h", "80,1-3")).plan.tcpPorts)
        assertEquals(listOf(80, 1, 2, 3), ports.ports)
    }

    @Test
    fun `defaults applied`() {
        val plan = success(PlanInputParser.Input("h", "80")).plan
        assertEquals(ScanPlan.DEFAULT_PROBE_TIMEOUT_MS, plan.probeTimeoutMs)
        assertEquals(ScanPlan.DEFAULT_CONCURRENCY, plan.concurrency)
        assertEquals(ScanPlan.DEFAULT_MAX_DURATION_MS, plan.maxDurationMs)
    }

    @Test
    fun `numeric fields clamped`() {
        val plan =
            success(
                PlanInputParser.Input("h", "80", probeTimeoutMs = 50, concurrency = 10_000, maxDurationMs = 500),
            ).plan
        assertEquals(100L, plan.probeTimeoutMs)
        assertEquals(256, plan.concurrency)
        assertEquals(1_000L, plan.maxDurationMs)
    }

    @Test
    fun `null max duration preserved`() {
        val plan = success(PlanInputParser.Input("h", "80", maxDurationMs = null)).plan
        assertNull(plan.maxDurationMs)
    }

    @Test
    fun `invalid target error passed through`() {
        val error = failure(PlanInputParser.Input("10.0.0.0/8", "80")).error
        assertEquals("CIDR_NOT_SUPPORTED_YET", error.code)
    }

    @Test
    fun `invalid ports error passed through`() {
        val error = failure(PlanInputParser.Input("example.com", "top-100,80")).error
        assertEquals("INVALID_PORT", error.code)
    }

    @Test
    fun `single target per plan in M1`() {
        val plan = success(PlanInputParser.Input("example.com", "80")).plan
        assertTrue(plan.targets.size == 1)
    }
}
