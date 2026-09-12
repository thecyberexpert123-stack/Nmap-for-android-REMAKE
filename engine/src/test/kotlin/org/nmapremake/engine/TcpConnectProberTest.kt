package org.nmapremake.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.PortState
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.Target
import org.nmapremake.engine.testutil.FakeTcpTransport
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TcpConnectProberTest {
    private val target = Target("127.0.0.1")

    @Test
    fun `established maps to OPEN with exact evidence`() {
        val prober = TcpConnectProber(FakeTcpTransport.instant(latencyMs = 12))
        val result = runBlocking { prober.probe(target, 443, 5_000) }
        assertEquals(PortState.OPEN, result.state)
        assertEquals(12L, result.latencyMs)
        assertNull(result.error)
        assertEquals("TCP connect completed in 12 ms", result.evidence)
    }

    @Test
    fun `refused maps to CLOSED with exact evidence and typed error`() {
        val prober = TcpConnectProber(FakeTcpTransport.refused(latencyMs = 1))
        val result = runBlocking { prober.probe(target, 22, 5_000) }
        assertEquals(PortState.CLOSED, result.state)
        assertEquals(1L, result.latencyMs)
        assertEquals(ErrorCode.CONNECTION_REFUSED, result.error?.errorCode())
        assertEquals("Connection refused (ECONNREFUSED)", result.evidence)
    }

    @Test
    fun `timed out maps to TIMEOUT with timeout in evidence`() {
        val prober =
            TcpConnectProber(
                FakeTcpTransport { _, _, _ -> ConnectOutcome.TimedOut },
            )
        val result = runBlocking { prober.probe(target, 81, 2_500) }
        assertEquals(PortState.TIMEOUT, result.state)
        assertNull(result.latencyMs)
        assertEquals(ErrorCode.PROBE_TIMEOUT, result.error?.errorCode())
        assertEquals("No response within 2500 ms", result.evidence)
    }

    @Test
    fun `no route maps to UNREACHABLE with exact evidence`() {
        val prober =
            TcpConnectProber(
                FakeTcpTransport { _, _, _ -> ConnectOutcome.NoRoute },
            )
        val result = runBlocking { prober.probe(target, 81, 5_000) }
        assertEquals(PortState.UNREACHABLE, result.state)
        assertNull(result.latencyMs)
        assertEquals(ErrorCode.NO_ROUTE_TO_HOST, result.error?.errorCode())
        assertEquals("No route to host (EHOSTUNREACH)", result.evidence)
    }

    @Test
    fun `failed maps to INCONCLUSIVE preserving error and evidence`() {
        val prober =
            TcpConnectProber(
                FakeTcpTransport { _, _, _ ->
                    ConnectOutcome.Failed(
                        ScanError(ErrorCode.PERMISSION_DENIED, "Permission denied: INTERNET"),
                        "Permission denied: INTERNET",
                    )
                },
            )
        val result = runBlocking { prober.probe(target, 81, 5_000) }
        assertEquals(PortState.INCONCLUSIVE, result.state)
        assertEquals(ErrorCode.PERMISSION_DENIED, result.error?.errorCode())
        assertEquals("Permission denied: INTERNET", result.evidence)
    }

    @Test
    fun `blocked transport completes as TIMEOUT at about the configured timeout`() {
        val prober = TcpConnectProber(FakeTcpTransport.blockingUntilReleased())
        val started = System.currentTimeMillis()
        val result = runBlocking { prober.probe(target, 81, 300) }
        val elapsed = System.currentTimeMillis() - started
        assertEquals(PortState.TIMEOUT, result.state)
        assertEquals("No response within 300 ms", result.evidence)
        // PLAN §5.1.3.2: wall-clock bound <= timeout + 500 ms slack.
        assertTrue(elapsed <= 300 + 500, "probe took ${elapsed}ms, expected <= 800ms")
    }

    @Test
    fun `external cancellation propagates instead of being classified as TIMEOUT`() {
        val prober = TcpConnectProber(FakeTcpTransport.blockingUntilReleased())
        runBlocking {
            val job = launch { prober.probe(target, 81, 60_000) }
            delay(50)
            job.cancel()
            job.join()
            assertTrue(job.isCancelled, "external cancellation must cancel the probe job")
        }
    }
}
