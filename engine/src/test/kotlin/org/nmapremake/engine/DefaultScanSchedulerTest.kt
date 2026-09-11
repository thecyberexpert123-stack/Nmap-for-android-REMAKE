package org.nmapremake.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.nmapremake.core.capability.Executors
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.PortSpec
import org.nmapremake.core.model.PortState
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.model.Target
import org.nmapremake.engine.testutil.FakeTcpTransport
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultScanSchedulerTest {
    private val scheduler = DefaultScanScheduler()
    private val executor = Executors.LOCAL_ANDROID
    private val target = Target("127.0.0.1")

    private fun plan(
        ports: List<Int>,
        concurrency: Int,
        probeTimeoutMs: Long = 5_000,
        maxDurationMs: Long? = null,
        targets: List<Target> = listOf(target),
    ): ScanPlan = ScanPlan(
        targets = targets,
        tcpPorts = PortSpec.List(ports),
        probeTimeoutMs = probeTimeoutMs,
        concurrency = concurrency,
        maxDurationMs = maxDurationMs,
    )

    @Test
    fun `concurrency is capped at the plan value`() {
        val transport = FakeTcpTransport.slow(5)
        val events = runBlocking {
            scheduler.scan(plan((1..64).toList(), concurrency = 8), executor, transport).toList()
        }
        assertEquals(8, transport.maxInFlight.get())
        val finished = events.filterIsInstance<ProgressEvent.PortFinished>()
        assertEquals(64, finished.size)
        assertTrue(finished.all { it.result.state == PortState.OPEN })
    }

    @Test
    fun `PortStarted precedes PortFinished for every port`() {
        val transport = FakeTcpTransport.instant()
        val events = runBlocking {
            scheduler.scan(plan((1..16).toList(), concurrency = 4), executor, transport).toList()
        }
        val seenStart = mutableSetOf<Int>()
        val seenFinish = mutableSetOf<Int>()
        for (event in events) {
            when (event) {
                is ProgressEvent.PortStarted -> seenStart += event.port
                is ProgressEvent.PortFinished -> {
                    assertTrue(event.port in seenStart, "PortFinished(${event.port}) before PortStarted")
                    assertTrue(seenFinish.add(event.port), "duplicate PortFinished(${event.port})")
                }
                else -> Unit
            }
        }
        assertEquals(16, seenFinish.size)
    }

    @Test
    fun `stream terminates with exactly one ScanFinished on success`() {
        val events = runBlocking {
            scheduler.scan(plan(listOf(80, 443), concurrency = 2), executor, FakeTcpTransport.instant()).toList()
        }
        assertEquals(1, events.filterIsInstance<ProgressEvent.ScanFinished>().size)
        assertEquals(0, events.filterIsInstance<ProgressEvent.ScanFailed>().size)
        val report = assertIs<ProgressEvent.ScanFinished>(events.last()).report
        assertEquals("nmap-android-remake/engine/0.1.0", report.generator)
        assertEquals(1, report.schemaVersion)
        assertEquals(executor, report.executor)
        assertEquals(1, report.hosts.size)
        assertEquals(listOf(80, 443), report.hosts.single().portResults.map { it.port })
        assertTrue(report.startedAtEpochMs <= report.finishedAtEpochMs)
    }

    @Test
    fun `outcome classification flows into host results with evidence`() {
        val transport =
            FakeTcpTransport.outcomes(
                mapOf(
                    22 to ConnectOutcome.Refused(1),
                    81 to ConnectOutcome.TimedOut,
                    82 to ConnectOutcome.NoRoute,
                    80 to ConnectOutcome.Established(3),
                ),
            )
        val events = runBlocking {
            scheduler.scan(plan(listOf(80, 22, 81, 82), concurrency = 2), executor, transport).toList()
        }
        val results = assertIs<ProgressEvent.ScanFinished>(events.last()).report.hosts.single().portResults
        val byPort = results.associateBy { it.port }
        assertEquals(PortState.OPEN, byPort.getValue(80).state)
        assertEquals("TCP connect completed in 3 ms", byPort.getValue(80).evidence)
        assertEquals(PortState.CLOSED, byPort.getValue(22).state)
        assertEquals("Connection refused (ECONNREFUSED)", byPort.getValue(22).evidence)
        assertEquals(ErrorCode.CONNECTION_REFUSED, byPort.getValue(22).error?.errorCode())
        assertEquals(PortState.TIMEOUT, byPort.getValue(81).state)
        assertEquals(PortState.UNREACHABLE, byPort.getValue(82).state)
        assertTrue(results.all { it.evidence.isNotEmpty() })
    }

    @Test
    fun `every non-OPEN result carries a typed error`() {
        val transport =
            FakeTcpTransport.outcomes(
                mapOf(
                    22 to ConnectOutcome.Refused(1),
                    81 to ConnectOutcome.TimedOut,
                    82 to ConnectOutcome.NoRoute,
                    80 to ConnectOutcome.Established(3),
                ),
            )
        val events = runBlocking {
            scheduler.scan(plan(listOf(80, 22, 81, 82), concurrency = 2), executor, transport).toList()
        }
        val results = assertIs<ProgressEvent.ScanFinished>(events.last()).report.hosts.single().portResults
        for (result in results) {
            if (result.state != PortState.OPEN) {
                assertTrue(result.error != null, "port ${result.port} in ${result.state} lacks an error")
            }
        }
    }

    @Test
    fun `timeout enforcement produces TIMEOUT results at about the probe timeout`() {
        val transport = FakeTcpTransport.blockingUntilReleased()
        val started = System.currentTimeMillis()
        val events = runBlocking {
            scheduler.scan(
                plan(listOf(1, 2, 3, 4), concurrency = 2, probeTimeoutMs = 300),
                executor,
                transport,
            ).toList()
        }
        val elapsed = System.currentTimeMillis() - started
        val finished = events.filterIsInstance<ProgressEvent.PortFinished>()
        assertEquals(4, finished.size)
        assertTrue(finished.all { it.result.state == PortState.TIMEOUT })
        assertTrue(finished.all { it.result.evidence == "No response within 300 ms" })
        // Two rounds of ~300 ms probes: generous bound, still proves prompt termination.
        assertTrue(elapsed <= 2_500, "scan took ${elapsed}ms")
    }

    @Test
    fun `cancellation stops the scan and reports SCAN_CANCELLED with completed ports only`() {
        val transport = FakeTcpTransport.slow(10)
        val events = runBlocking {
            val flow = scheduler.scan(plan((1..200).toList(), concurrency = 32), executor, transport)
            val collector = launch {
                val list = mutableListOf<ProgressEvent>()
                flow.toList(list)
                list
            }
            delay(30)
            scheduler.cancel()
            collector.join()
            collector.getCompleted()
        }
        val failed = events.filterIsInstance<ProgressEvent.ScanFailed>()
        assertEquals(1, failed.size)
        assertEquals(ErrorCode.SCAN_CANCELLED, failed.single().error.errorCode())
        assertEquals(0, events.filterIsInstance<ProgressEvent.ScanFinished>().size)
        val finishedCount = events.filterIsInstance<ProgressEvent.PortFinished>().size
        assertTrue(finishedCount < 200, "expected partial results, got $finishedCount")
    }

    @Test
    fun `watchdog forces SCAN_DEADLINE_EXCEEDED`() {
        val transport = FakeTcpTransport.blockingUntilReleased()
        val started = System.currentTimeMillis()
        val events = runBlocking {
            scheduler.scan(
                plan((1..20).toList(), concurrency = 8, maxDurationMs = 150),
                executor,
                transport,
            ).toList()
        }
        val elapsed = System.currentTimeMillis() - started
        val failed = events.filterIsInstance<ProgressEvent.ScanFailed>()
        assertEquals(1, failed.size)
        assertEquals(ErrorCode.SCAN_DEADLINE_EXCEEDED, failed.single().error.errorCode())
        assertEquals(0, events.filterIsInstance<ProgressEvent.ScanFinished>().size)
        assertTrue(elapsed <= 2_000, "watchdog took ${elapsed}ms")
    }

    @Test
    fun `empty port list completes with an empty host result`() {
        val events = runBlocking {
            scheduler.scan(plan(emptyList(), concurrency = 4), executor, FakeTcpTransport.instant()).toList()
        }
        val report = assertIs<ProgressEvent.ScanFinished>(events.last()).report
        assertEquals(1, report.hosts.size)
        assertTrue(report.hosts.single().portResults.isEmpty())
    }

    @Test
    fun `skipped TCP pass completes with an empty host result`() {
        val noPortsPlan = ScanPlan(targets = listOf(target), tcpPorts = null)
        val events = runBlocking {
            scheduler.scan(noPortsPlan, executor, FakeTcpTransport.instant()).toList()
        }
        val report = assertIs<ProgressEvent.ScanFinished>(events.last()).report
        assertTrue(report.hosts.single().portResults.isEmpty())
    }

    @Test
    fun `multi-target plan aggregates one HostResult per target`() {
        val second = Target("10.0.0.1")
        val events = runBlocking {
            scheduler.scan(
                plan(listOf(80, 443), concurrency = 4, targets = listOf(target, second)),
                executor,
                FakeTcpTransport.instant(),
            ).toList()
        }
        val report = assertIs<ProgressEvent.ScanFinished>(events.last()).report
        assertEquals(2, report.hosts.size)
        assertEquals(setOf("127.0.0.1", "10.0.0.1"), report.hosts.map { it.target.label }.toSet())
        assertTrue(report.hosts.all { it.portResults.size == 2 })
    }

    @Test
    fun `one scan at a time per scheduler instance`() {
        val other = DefaultScanScheduler()
        runBlocking {
            val flow =
                scheduler.scan(
                    plan(listOf(80), concurrency = 1),
                    executor,
                    FakeTcpTransport.instant(),
                )
            val thrown = runCatching {
                other.scan(plan(listOf(80), concurrency = 1), executor, FakeTcpTransport.instant())
            }
            assertTrue(thrown.isSuccess, "separate instances may scan concurrently")
            val second = runCatching {
                scheduler.scan(plan(listOf(80), concurrency = 1), executor, FakeTcpTransport.instant())
            }
            assertTrue(second.isFailure, "same instance must reject a concurrent scan")
            flow.toList()
        }
    }
}
