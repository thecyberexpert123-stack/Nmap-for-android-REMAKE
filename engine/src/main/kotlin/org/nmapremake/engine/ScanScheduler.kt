package org.nmapremake.engine

import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.nmapremake.core.capability.ExecutorNode
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.HostResult
import org.nmapremake.core.model.PortResult
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.model.TransportProtocol
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Schedules probes against a plan (ARCHITECTURE §3, PLAN §5.1.3).
 *
 * Guarantees: at most [ScanPlan.concurrency] in-flight probes; blocking
 * connects run on Dispatchers.IO via [TcpConnectProber]; `PortStarted(p)`
 * precedes `PortFinished(p)` for every completed p; the stream terminates
 * with exactly one `ScanFinished` or `ScanFailed`; cooperative cancellation
 * ([cancel]) aborts in-flight connects and yields `ScanFailed(SCAN_CANCELLED)`
 * containing only completed ports; the optional watchdog yields
 * `ScanFailed(SCAN_DEADLINE_EXCEEDED)`.
 */
interface ScanScheduler {
    fun scan(
        plan: ScanPlan,
        executor: ExecutorNode,
        transport: TcpTransport,
    ): Flow<ProgressEvent>

    /** Cooperative; guarantees completion within bounded time (PLAN §5.1.3.3). */
    suspend fun cancel()
}

class DefaultScanScheduler(
    private val aggregator: ResultAggregator = ResultAggregator(),
    private val clock: EngineClock = SystemClock,
) : ScanScheduler {
    private class Session(
        val transport: TcpTransport,
    ) {
        val cancelled = AtomicBoolean(false)
    }

    private val active = AtomicReference<Session?>(null)

    override fun scan(
        plan: ScanPlan,
        executor: ExecutorNode,
        transport: TcpTransport,
    ): Flow<ProgressEvent> {
        val session = Session(transport)
        check(active.compareAndSet(null, session)) {
            "one scan at a time per scheduler instance"
        }
        val prober = TcpConnectProber(transport)
        return channelFlow {
            try {
                runScan(plan, executor, prober, session, this)
            } finally {
                active.compareAndSet(session, null)
            }
        }
    }

    override suspend fun cancel() {
        active.get()?.let { session ->
            session.cancelled.set(true)
            session.transport.abort()
        }
    }

    private suspend fun runScan(
        plan: ScanPlan,
        executor: ExecutorNode,
        prober: TcpConnectProber,
        session: Session,
        scope: ProducerScope<ProgressEvent>,
    ) {
        val startedAt = clock.now()
        val hostResults = mutableListOf<HostResult>()
        var deadlineReached = false

        val portLoop: suspend () -> Unit = {
            for (target in plan.targets) {
                val ports = plan.tcpPorts?.expand().orEmpty()
                val results = mutableListOf<PortResult>()
                val resultsLock = Mutex()
                coroutineScope {
                    val semaphore = Semaphore(plan.concurrency)
                    for (port in ports) {
                        if (session.cancelled.get()) break
                        launch {
                            semaphore.withPermit {
                                if (session.cancelled.get()) return@withPermit
                                scope.send(ProgressEvent.PortStarted(target, port, TransportProtocol.TCP))
                                val result = prober.probe(target, port, plan.probeTimeoutMs)
                                if (!session.cancelled.get()) {
                                    resultsLock.withLock { results += result }
                                    scope.send(ProgressEvent.PortFinished(target, result))
                                }
                            }
                        }
                    }
                }
                hostResults +=
                    HostResult(
                        target = target,
                        portResults = results.toList(),
                        executor = executor,
                        capabilities = executor.capabilities,
                        startedAtEpochMs = startedAt,
                        finishedAtEpochMs = clock.now(),
                    )
            }
        }

        // Local copy: plan.maxDurationMs is a cross-module public property,
        // so it cannot be smart-cast to Long directly.
        val maxDurationMs = plan.maxDurationMs
        val ranToCompletion =
            if (maxDurationMs != null) {
                withTimeoutOrNull(maxDurationMs) { portLoop() } != null
            } else {
                portLoop()
                true
            }
        if (!ranToCompletion) deadlineReached = true

        when {
            session.cancelled.get() ->
                scope.send(
                    ProgressEvent.ScanFailed(ScanError(ErrorCode.SCAN_CANCELLED, "scan cancelled by user")),
                )
            deadlineReached ->
                scope.send(
                    ProgressEvent.ScanFailed(ScanError(ErrorCode.SCAN_DEADLINE_EXCEEDED, "scan watchdog exceeded")),
                )
            else -> {
                val report = aggregator.aggregate(plan, executor, hostResults, startedAt, clock.now())
                scope.send(ProgressEvent.ScanFinished(report))
            }
        }
    }
}
