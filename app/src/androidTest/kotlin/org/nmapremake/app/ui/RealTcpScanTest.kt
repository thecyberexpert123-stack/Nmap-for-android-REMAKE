package org.nmapremake.app.ui

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.PortSpec
import org.nmapremake.core.model.PortState
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.model.ScanReport
import org.nmapremake.core.model.Target
import org.nmapremake.engine.DefaultScanScheduler
import org.nmapremake.engine.InetHostResolver
import org.nmapremake.engine.LocalExecutionRouter
import org.nmapremake.engine.ProgressEvent
import org.nmapremake.engine.ScanEngine
import org.nmapremake.engine.SocketTcpTransport
import org.nmapremake.engine.json.JsonFormatter

/**
 * Engine-level scan against the CI host's real TCP stack (10.0.2.2): the
 * classification matrix (PLAN §5.1.2) exercised over genuine network I/O on
 * the emulator, with the schema-v1 JSON report logged as evidence.
 */
class RealTcpScanTest {
    private fun scan(ports: List<Int>, timeoutMs: Long): ScanReport = runBlocking {
        val engine =
            ScanEngine(
                LocalExecutionRouter(),
                InetHostResolver(),
                DefaultScanScheduler(),
                SocketTcpTransport(),
            )
        val plan =
            ScanPlan(
                targets = listOf(Target("10.0.2.2")),
                tcpPorts = PortSpec.List(ports),
                probeTimeoutMs = timeoutMs,
                concurrency = 4,
            )
        val events = engine.scan(plan).toList()
        events.filterIsInstance<ProgressEvent.ScanFinished>().single().report
    }

    @Test
    fun `real connect scan classifies open and closed ports on the CI host`() {
        val report = scan(listOf(18080, 18081, 18443, 18082), timeoutMs = 5_000)
        val results = report.hosts.single().portResults.associateBy { it.port }
        assertEquals(PortState.OPEN, results.getValue(18080).state)
        assertEquals(PortState.CLOSED, results.getValue(18081).state)
        assertEquals(PortState.OPEN, results.getValue(18443).state)
        assertEquals(PortState.CLOSED, results.getValue(18082).state)

        val open = results.getValue(18080)
        assertTrue(open.latencyMs != null && open.latencyMs!! >= 0)
        assertEquals("TCP connect completed in ${open.latencyMs} ms", open.evidence)
        assertEquals(ErrorCode.CONNECTION_REFUSED, results.getValue(18081).error?.errorCode())
        assertEquals("Connection refused (ECONNREFUSED)", results.getValue(18081).evidence)

        for (result in results.values) {
            Log.i(
                "ScanReal",
                "port ${result.port}: state=${result.state} latencyMs=${result.latencyMs} " +
                    "error=${result.error?.code}",
            )
        }
        JsonFormatter().format(report).chunked(500).forEach {
            Log.i("ScanReal", "json-chunk: $it")
        }
    }

    @Test
    fun `dropped port yields an honest TIMEOUT after the probe deadline`() {
        // Only enabled when CI successfully applied an iptables DROP for
        // port 18099 on the runner host (SYN silently dropped => TIMEOUT).
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(
            "timeout port not configured on this run (no iptables DROP)",
            args.getString("timeoutPortEnabled") == "true",
        )
        val report = scan(listOf(18099), timeoutMs = 3_000)
        val result = report.hosts.single().portResults.single()
        assertEquals(PortState.TIMEOUT, result.state)
        assertEquals(null, result.latencyMs)
        assertEquals(ErrorCode.PROBE_TIMEOUT, result.error?.errorCode())
        assertEquals("No response within 3000 ms", result.evidence)
        Log.i("ScanTimeout", "port 18099: state=${result.state} evidence=\"${result.evidence}\"")
    }
}
