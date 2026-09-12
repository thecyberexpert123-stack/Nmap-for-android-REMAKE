package org.nmapremake.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.nmapremake.core.capability.ExecutorNode
import org.nmapremake.core.capability.Executors
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.IpFamily
import org.nmapremake.core.model.PortSpec
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.model.ScanReport
import org.nmapremake.core.model.Target
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// advanceUntilIdle is @ExperimentalCoroutinesApi; the opt-in is local to
// this test class and its single use in `cancel delegates to the scheduler`.
@OptIn(ExperimentalCoroutinesApi::class)
class ScanEngineTest {
    private val target = Target("example.com")

    private fun tcpPlan() = ScanPlan(targets = listOf(target), tcpPorts = PortSpec.Single(80))

    private class FakeRouter(
        private val selection: ExecutorSelection,
    ) : ExecutionRouter {
        override fun route(plan: ScanPlan): ExecutorSelection = selection
    }

    private class FakeResolver(
        private val resolvedTarget: Target =
            Target(
                "example.com",
                resolved = "192.0.2.1",
                family = IpFamily.IPV4,
            ),
        private val failure: UnresolvedHostException? = null,
    ) : HostResolver {
        override suspend fun resolve(target: Target): Target {
            failure?.let { throw it }
            return resolvedTarget
        }
    }

    private class FakeScheduler : ScanScheduler {
        var lastPlan: ScanPlan? = null
        var lastExecutor: ExecutorNode? = null
        var cancelCalls = 0

        override fun scan(
            plan: ScanPlan,
            executor: ExecutorNode,
            transport: TcpTransport,
        ): Flow<ProgressEvent> =
            flow {
                lastPlan = plan
                lastExecutor = executor
                emit(
                    ProgressEvent.ScanFinished(
                        ScanReport(
                            schemaVersion = 1,
                            generator = "test",
                            startedAtEpochMs = 1,
                            finishedAtEpochMs = 2,
                            scanPlan = plan,
                            executor = executor,
                            hosts = emptyList(),
                        ),
                    ),
                )
            }

        override suspend fun cancel() {
            cancelCalls++
        }
    }

    private object FakeTransportStub : TcpTransport {
        override fun connect(
            target: Target,
            port: Int,
            timeoutMs: Long,
        ): ConnectOutcome = ConnectOutcome.Established(0)
    }

    @Test
    fun `rejected plan emits a single ScanFailed and never schedules`() =
        runTest {
            val error = ScanError(ErrorCode.CAPABILITY_UNSUPPORTED, "nope")
            val scheduler = FakeScheduler()
            val engine =
                ScanEngine(
                    router = FakeRouter(ExecutorSelection.Rejected(emptyList(), error)),
                    resolver = FakeResolver(),
                    scheduler = scheduler,
                    transport = FakeTransportStub,
                )
            val events = engine.scan(tcpPlan()).toList()
            assertEquals(1, events.size)
            val failed = assertIs<ProgressEvent.ScanFailed>(events.single())
            assertEquals(ErrorCode.CAPABILITY_UNSUPPORTED, failed.error.errorCode())
            assertEquals(null, scheduler.lastPlan)
        }

    @Test
    fun `unresolvable target emits ScanFailed UNRESOLVABLE_HOST`() =
        runTest {
            val engine =
                ScanEngine(
                    router = FakeRouter(ExecutorSelection.Selected(Executors.LOCAL_ANDROID)),
                    resolver =
                        FakeResolver(
                            failure =
                                UnresolvedHostException(
                                    ScanError(ErrorCode.UNRESOLVABLE_HOST, "cannot resolve"),
                                ),
                        ),
                    scheduler = FakeScheduler(),
                    transport = FakeTransportStub,
                )
            val events = engine.scan(tcpPlan()).toList()
            val failed = assertIs<ProgressEvent.ScanFailed>(events.single())
            assertEquals(ErrorCode.UNRESOLVABLE_HOST, failed.error.errorCode())
        }

    @Test
    fun `happy path resolves targets then streams scheduler events`() =
        runTest {
            val scheduler = FakeScheduler()
            val engine =
                ScanEngine(
                    router = FakeRouter(ExecutorSelection.Selected(Executors.LOCAL_ANDROID)),
                    resolver = FakeResolver(),
                    scheduler = scheduler,
                    transport = FakeTransportStub,
                )
            val events = engine.scan(tcpPlan()).toList()
            assertEquals(1, events.size)
            assertIs<ProgressEvent.ScanFinished>(events.single())
            val plan = scheduler.lastPlan
            assertEquals("192.0.2.1", plan?.targets?.single()?.resolved)
            assertEquals(IpFamily.IPV4, plan?.targets?.single()?.family)
            assertEquals(Executors.LOCAL_ANDROID, scheduler.lastExecutor)
        }

    @Test
    fun `cancel delegates to the scheduler`() =
        runTest {
            val scheduler = FakeScheduler()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val engine =
                ScanEngine(
                    router = FakeRouter(ExecutorSelection.Selected(Executors.LOCAL_ANDROID)),
                    resolver = FakeResolver(),
                    scheduler = scheduler,
                    transport = FakeTransportStub,
                    cancelDispatcher = dispatcher,
                )
            engine.cancel()
            advanceUntilIdle()
            assertEquals(1, scheduler.cancelCalls)
        }

    @Test
    fun `plan is clamped before routing`() =
        runTest {
            val scheduler = FakeScheduler()
            val router = FakeRouter(ExecutorSelection.Selected(Executors.LOCAL_ANDROID))
            val engine =
                ScanEngine(
                    router = router,
                    resolver = FakeResolver(),
                    scheduler = scheduler,
                    transport = FakeTransportStub,
                )
            engine.scan(tcpPlan().copy(concurrency = 1_000_000)).toList()
            assertEquals(256, scheduler.lastPlan?.concurrency)
            assertTrue(scheduler.lastPlan?.probeTimeoutMs == 5_000L)
        }
}
