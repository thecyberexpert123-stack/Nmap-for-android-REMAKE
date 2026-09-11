package org.nmapremake.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.PortResult
import org.nmapremake.core.model.PortState
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.Target
import org.nmapremake.core.model.TransportProtocol

/**
 * Classifies transport outcomes into honest [PortResult]s with the exact
 * evidence formats locked in PLAN §5.1.2. Per-probe timeout enforcement
 * lives here (single attempt per port in M1 — no retries).
 */
class TcpConnectProber(private val transport: TcpTransport) {

    // Each catch maps a specific exception TYPE to a classification; the
    // exception object itself carries no extra information the report needs.
    @Suppress("SwallowedException")
    suspend fun probe(target: Target, port: Int, timeoutMs: Long): PortResult {
        val outcome = try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) { transport.connect(target, port, timeoutMs) }
            }
        } catch (e: TimeoutCancellationException) {
            // Only withTimeout's own timer surfaces as TimeoutCancellationException;
            // external (scan-level) cancellation is JobCancellationException and
            // propagates untouched.
            ConnectOutcome.TimedOut
        }
        return classify(port, timeoutMs, outcome)
    }

    internal fun classify(port: Int, timeoutMs: Long, outcome: ConnectOutcome): PortResult =
        when (outcome) {
            is ConnectOutcome.Established -> PortResult(
                port = port,
                protocol = TransportProtocol.TCP,
                state = PortState.OPEN,
                latencyMs = outcome.latencyMs,
                error = null,
                evidence = "TCP connect completed in ${outcome.latencyMs} ms",
            )
            is ConnectOutcome.Refused -> PortResult(
                port = port,
                protocol = TransportProtocol.TCP,
                state = PortState.CLOSED,
                latencyMs = outcome.latencyMs,
                error = ScanError(ErrorCode.CONNECTION_REFUSED, "Connection refused"),
                evidence = "Connection refused (ECONNREFUSED)",
            )
            ConnectOutcome.TimedOut -> PortResult(
                port = port,
                protocol = TransportProtocol.TCP,
                state = PortState.TIMEOUT,
                latencyMs = null,
                error = ScanError(ErrorCode.PROBE_TIMEOUT, "No response"),
                evidence = "No response within $timeoutMs ms",
            )
            ConnectOutcome.NoRoute -> PortResult(
                port = port,
                protocol = TransportProtocol.TCP,
                state = PortState.UNREACHABLE,
                latencyMs = null,
                error = ScanError(ErrorCode.NO_ROUTE_TO_HOST, "No route to host"),
                evidence = "No route to host (EHOSTUNREACH)",
            )
            is ConnectOutcome.Failed -> PortResult(
                port = port,
                protocol = TransportProtocol.TCP,
                state = PortState.INCONCLUSIVE,
                latencyMs = null,
                error = outcome.error,
                evidence = outcome.evidence,
            )
        }
}
