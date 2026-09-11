package org.nmapremake.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nmapremake.core.capability.Executors
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.PortResult
import org.nmapremake.core.model.PortState
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.model.ScanReport
import org.nmapremake.core.model.Target
import org.nmapremake.core.model.TransportProtocol
import org.nmapremake.engine.ProgressEvent
import org.nmapremake.engine.ScanRunner

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRunner(
        private val events: List<ProgressEvent>,
    ) : ScanRunner {
        var cancelCalls = 0

        override fun scan(plan: ScanPlan): Flow<ProgressEvent> =
            flow {
                events.forEach { emit(it) }
            }

        override fun cancel() {
            cancelCalls++
        }
    }

    private fun openResult(port: Int) =
        PortResult(
            port = port,
            protocol = TransportProtocol.TCP,
            state = PortState.OPEN,
            latencyMs = 5,
            error = null,
            evidence = "TCP connect completed in 5 ms",
        )

    @Test
    fun `initial state shows the capability banner and blocks starting`() {
        val viewModel = ScanViewModel(FakeRunner(emptyList()))
        val state = viewModel.state.value
        assertTrue(state.capabilityBanner.isNotEmpty())
        assertEquals(ScanPhase.INPUT, state.phase)
        assertFalse(state.canStart)
    }

    @Test
    fun `valid inputs enable start`() {
        val viewModel = ScanViewModel(FakeRunner(emptyList()))
        viewModel.updateTarget("example.com")
        val state = viewModel.state.value
        assertTrue(state.canStart)
        assertNull(state.targetError)
    }

    @Test
    fun `invalid target surfaces a typed parse message`() {
        val viewModel = ScanViewModel(FakeRunner(emptyList()))
        viewModel.updateTarget("10.0.0.0/8")
        assertNotNull(viewModel.state.value.targetError)
        assertFalse(viewModel.state.value.canStart)
    }

    @Test
    fun `invalid timeout blocks starting`() {
        val viewModel = ScanViewModel(FakeRunner(emptyList()))
        viewModel.updateTarget("example.com")
        viewModel.updateTimeout("abc")
        assertNotNull(viewModel.state.value.timeoutError)
        assertFalse(viewModel.state.value.canStart)
    }

    @Test
    fun `out-of-range concurrency blocks starting`() {
        val viewModel = ScanViewModel(FakeRunner(emptyList()))
        viewModel.updateTarget("example.com")
        viewModel.updateConcurrency("100000")
        assertNotNull(viewModel.state.value.concurrencyError)
        assertFalse(viewModel.state.value.canStart)
    }

    @Test
    fun `startScan opens the authorization phase without scanning`() {
        val runner = FakeRunner(emptyList())
        val viewModel = ScanViewModel(runner)
        viewModel.updateTarget("example.com")
        viewModel.startScan()
        val state = viewModel.state.value
        assertEquals(ScanPhase.AUTHORIZING, state.phase)
        assertNotNull(state.pendingPlan)
        assertEquals(100, state.totalPorts)
    }

    @Test
    fun `dismissAuthorization returns to input`() {
        val viewModel = ScanViewModel(FakeRunner(emptyList()))
        viewModel.updateTarget("example.com")
        viewModel.startScan()
        viewModel.dismissAuthorization()
        assertEquals(ScanPhase.INPUT, viewModel.state.value.phase)
        assertNull(viewModel.state.value.pendingPlan)
    }

    @Test
    fun `confirmScan runs the plan and reaches FINISHED with hosts`() {
        val target = Target("example.com")
        val report =
            ScanReport(
                schemaVersion = 1,
                generator = "test",
                startedAtEpochMs = 1,
                finishedAtEpochMs = 2,
                scanPlan = ScanPlan(targets = listOf(target)),
                executor = Executors.LOCAL_ANDROID,
                hosts = emptyList(),
            )
        val runner =
            FakeRunner(
                listOf(
                    ProgressEvent.PortStarted(target, 80, TransportProtocol.TCP),
                    ProgressEvent.PortFinished(target, openResult(80)),
                    ProgressEvent.ScanFinished(report),
                ),
            )
        val viewModel = ScanViewModel(runner)
        viewModel.updateTarget("example.com")
        viewModel.startScan()
        viewModel.confirmScan()
        val state = viewModel.state.value
        assertEquals(ScanPhase.FINISHED, state.phase)
        assertEquals(1, state.progress.finished)
        assertEquals(1, state.progress.open)
        assertEquals(report.hosts, state.hosts)
        assertNull(state.error)
    }

    @Test
    fun `cancellation surfaces SCAN_CANCELLED with partial results labeled incomplete`() {
        val target = Target("example.com")
        val runner =
            FakeRunner(
                listOf(
                    ProgressEvent.PortStarted(target, 80, TransportProtocol.TCP),
                    ProgressEvent.PortFinished(target, openResult(80)),
                    ProgressEvent.PortStarted(target, 443, TransportProtocol.TCP),
                    ProgressEvent.ScanFailed(ScanError(ErrorCode.SCAN_CANCELLED, "scan cancelled by user")),
                ),
            )
        val viewModel = ScanViewModel(runner)
        viewModel.updateTarget("example.com")
        viewModel.startScan()
        viewModel.confirmScan()
        val state = viewModel.state.value
        assertEquals(ScanPhase.FAILED, state.phase)
        assertEquals(ErrorCode.SCAN_CANCELLED, state.error?.errorCode())
        assertTrue(state.isIncomplete)
        assertEquals(1, state.liveResults.size)
    }

    @Test
    fun `cancelScan delegates to the runner once`() {
        val runner = FakeRunner(emptyList())
        val viewModel = ScanViewModel(runner)
        viewModel.updateTarget("example.com")
        viewModel.startScan()
        viewModel.confirmScan()
        viewModel.cancelScan()
        assertEquals(1, runner.cancelCalls)
        assertEquals(ScanPhase.CANCELLING, viewModel.state.value.phase)
    }

    @Test
    fun `resetToInput clears results and errors`() {
        val target = Target("example.com")
        val runner =
            FakeRunner(
                listOf(ProgressEvent.ScanFailed(ScanError(ErrorCode.INTERNAL, "boom"))),
            )
        val viewModel = ScanViewModel(runner)
        viewModel.updateTarget("example.com")
        viewModel.startScan()
        viewModel.confirmScan()
        assertEquals(ScanPhase.FAILED, viewModel.state.value.phase)
        viewModel.resetToInput()
        val state = viewModel.state.value
        assertEquals(ScanPhase.INPUT, state.phase)
        assertNull(state.error)
        assertTrue(state.liveResults.isEmpty())
    }
}
