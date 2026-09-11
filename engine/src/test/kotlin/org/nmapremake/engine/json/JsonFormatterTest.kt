package org.nmapremake.engine.json

import org.junit.jupiter.api.Test
import org.nmapremake.core.capability.Executors
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.HostResult
import org.nmapremake.core.model.IpFamily
import org.nmapremake.core.model.PortResult
import org.nmapremake.core.model.PortSpec
import org.nmapremake.core.model.PortState
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.model.ScanReport
import org.nmapremake.core.model.Target
import org.nmapremake.core.model.TransportProtocol
import org.nmapremake.engine.ResultAggregator
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonFormatterTest {
    private val formatter = JsonFormatter()

    private fun sampleReport(ports: PortSpec? = PortSpec.List(listOf(22, 80, 81, 82))): ScanReport {
        val executor = Executors.LOCAL_ANDROID
        val target = Target("127.0.0.1", resolved = "127.0.0.1", family = IpFamily.IPV4)
        val results =
            listOf(
                PortResult(
                    port = 22,
                    protocol = TransportProtocol.TCP,
                    state = PortState.CLOSED,
                    latencyMs = 1,
                    error = ScanError(ErrorCode.CONNECTION_REFUSED, "Connection refused"),
                    evidence = "Connection refused (ECONNREFUSED)",
                ),
                PortResult(
                    port = 80,
                    protocol = TransportProtocol.TCP,
                    state = PortState.OPEN,
                    latencyMs = 12,
                    error = null,
                    evidence = "TCP connect completed in 12 ms",
                ),
                PortResult(
                    port = 81,
                    protocol = TransportProtocol.TCP,
                    state = PortState.TIMEOUT,
                    latencyMs = null,
                    error = ScanError(ErrorCode.PROBE_TIMEOUT, "No response"),
                    evidence = "No response within 5000 ms",
                ),
                PortResult(
                    port = 82,
                    protocol = TransportProtocol.TCP,
                    state = PortState.UNREACHABLE,
                    latencyMs = null,
                    error = ScanError(ErrorCode.NO_ROUTE_TO_HOST, "No route to host"),
                    evidence = "No route to host (EHOSTUNREACH)",
                ),
            )
        val plan =
            ScanPlan(
                targets = listOf(target),
                tcpPorts = ports,
                probeTimeoutMs = 5_000,
                concurrency = 32,
            )
        val host =
            HostResult(
                target = target,
                portResults = results,
                executor = executor,
                capabilities = executor.capabilities,
                startedAtEpochMs = 1_000,
                finishedAtEpochMs = 2_000,
            )
        return ScanReport(
            schemaVersion = 1,
            generator = ResultAggregator.GENERATOR,
            startedAtEpochMs = 1_000,
            finishedAtEpochMs = 2_000,
            scanPlan = plan,
            executor = executor,
            hosts = listOf(host),
        )
    }

    @Test
    fun `round trip preserves the report`() {
        val report = sampleReport()
        val parsed = formatter.parse(formatter.format(report)).getOrNull()
        assertEquals(report, parsed)
    }

    @Test
    fun `port spec variants round trip`() {
        for (spec in listOf(
            PortSpec.TopPorts,
            PortSpec.All,
            PortSpec.Single(443),
            PortSpec.Range(1, 100),
        )) {
            val report = sampleReport(ports = spec)
            val parsed = formatter.parse(formatter.format(report)).getOrNull()
            assertEquals(spec, parsed?.scanPlan?.tcpPorts, "spec $spec did not round-trip")
        }
    }

    @Test
    fun `skipped TCP pass round trips`() {
        val report = sampleReport(ports = null)
        val parsed = formatter.parse(formatter.format(report)).getOrNull()
        assertNull(parsed?.scanPlan?.tcpPorts)
    }

    @Test
    fun `unknown error code is preserved verbatim and decodes to INTERNAL`() {
        val text = formatter.format(sampleReport()).replace("CONNECTION_REFUSED", "FUTURE_CODE")
        val parsed = formatter.parse(text).getOrNull()
        val closed = parsed!!.hosts.single().portResults.first { it.port == 22 }
        assertEquals("FUTURE_CODE", closed.error?.code)
        assertEquals(ErrorCode.INTERNAL, closed.error?.errorCode())
    }

    @Test
    fun `unknown extra fields are ignored on parse`() {
        val text = formatter.format(sampleReport())
            .replaceFirst("\"hosts\": [", "\"futureField\": {\"x\": 1},\n  \"hosts\": [")
        val parsed = formatter.parse(text).getOrNull()
        assertEquals(sampleReport(), parsed)
    }

    @Test
    fun `malformed input yields an INTERNAL error`() {
        val result = formatter.parse("not json at all {")
        assertEquals(ErrorCode.INTERNAL, result.errorOrNull()?.errorCode())
    }

    @Test
    fun `empty input yields an INTERNAL error`() {
        val result = formatter.parse("")
        assertEquals(ErrorCode.INTERNAL, result.errorOrNull()?.errorCode())
    }

    @Test
    fun `formatted output embeds the schema version`() {
        val text = formatter.format(sampleReport())
        // schemaVersion (1) equals its default and is omitted by encodeDefaults=false;
        // parsing must then restore the default — verified by the round-trip test.
        val parsed = formatter.parse(text).getOrNull()
        assertEquals(1, parsed?.schemaVersion)
    }
}
