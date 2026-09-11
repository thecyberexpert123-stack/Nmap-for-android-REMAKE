package org.nmapremake.engine.testutil

import org.nmapremake.core.model.Target
import org.nmapremake.engine.ConnectOutcome
import org.nmapremake.engine.TcpTransport
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

/**
 * Scriptable fake transport for scheduler/prober tests (PLAN §5.1.3:
 * deterministic tests use a fake; real sockets are reserved for loopback
 * classification tests).
 *
 * Blocking behaviors MUST honor thread interruption (like real socket
 * closes) so cancellation and watchdog paths terminate; they also release
 * early when [abort] is called.
 */
class FakeTcpTransport(
    private val behavior: FakeTcpTransport.(Target, Int, Long) -> ConnectOutcome,
) : TcpTransport {
    private val released = AtomicBoolean(false)

    /** Currently in-flight connect calls (instrumented counter). */
    val inFlight = AtomicInteger(0)

    /** Maximum observed in-flight connects. */
    val maxInFlight = AtomicInteger(0)

    override fun connect(
        target: Target,
        port: Int,
        timeoutMs: Long,
    ): ConnectOutcome {
        val current = inFlight.incrementAndGet()
        maxInFlight.updateAndGet { maxOf(it, current) }
        try {
            return behavior(target, port, timeoutMs)
        } finally {
            inFlight.decrementAndGet()
        }
    }

    override fun abort() {
        released.set(true)
    }

    companion object {
        fun instant(latencyMs: Long = 1L): FakeTcpTransport =
            FakeTcpTransport { _, _, _ -> ConnectOutcome.Established(latencyMs) }

        fun refused(latencyMs: Long = 2L): FakeTcpTransport =
            FakeTcpTransport { _, _, _ -> ConnectOutcome.Refused(latencyMs) }

        fun outcomes(map: Map<Int, ConnectOutcome>): FakeTcpTransport =
            FakeTcpTransport { _, port, _ -> map[port] ?: ConnectOutcome.Established(1L) }

        /** Blocks until aborted or interrupted — used for timeout/cancellation tests. */
        fun blockingUntilReleased(): FakeTcpTransport =
            FakeTcpTransport { _, _, _ ->
                while (!released.get()) {
                    if (Thread.interrupted()) throw InterruptedException("probe interrupted")
                    LockSupport.parkNanos(2_000_000)
                }
                ConnectOutcome.Established(1L)
            }

        /** Each probe takes about [durationMs] of real time; interrupt-responsive. */
        fun slow(durationMs: Long): FakeTcpTransport =
            FakeTcpTransport { _, _, _ ->
                val end = System.currentTimeMillis() + durationMs
                while (System.currentTimeMillis() < end) {
                    if (Thread.interrupted()) throw InterruptedException("probe interrupted")
                    LockSupport.parkNanos(500_000)
                }
                ConnectOutcome.Established(durationMs)
            }
    }
}
