package org.nmapremake.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nmapremake.core.capability.Executors
import org.nmapremake.core.model.PortState
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.parse.PlanInputParser
import org.nmapremake.core.parse.PortSpecParser
import org.nmapremake.core.parse.TargetParser
import org.nmapremake.engine.DefaultScanScheduler
import org.nmapremake.engine.InetHostResolver
import org.nmapremake.engine.LocalExecutionRouter
import org.nmapremake.engine.ProgressEvent
import org.nmapremake.engine.ScanEngine
import org.nmapremake.engine.ScanRunner
import org.nmapremake.engine.SocketTcpTransport

/**
 * Drives one scan through the engine (ARCHITECTURE §6). The consent gate is
 * enforced here: a plan becomes runnable only after [confirmScan] — which
 * the UI can only reach through the authorization dialog (PLAN §5.1.5).
 */
class ScanViewModel(
    private val runner: ScanRunner =
        ScanEngine(
            LocalExecutionRouter(),
            InetHostResolver(),
            DefaultScanScheduler(),
            SocketTcpTransport(),
        ),
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            ScanUiState(capabilityBanner = profileToBanner(Executors.LOCAL_ANDROID.capabilities)),
        )
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun updateTarget(text: String) {
        val error = (TargetParser.parse(text) as? TargetParser.ParseOutcome.Failure)?.error?.message
        _state.update { it.copy(targetInput = text, targetError = error) }
    }

    fun updatePorts(text: String) {
        val error =
            if (text.isBlank()) {
                null
            } else {
                (PortSpecParser.parse(text) as? PortSpecParser.ParseOutcome.Failure)?.error?.message
            }
        _state.update { it.copy(portsInput = text, portsError = error) }
    }

    fun updateTimeout(text: String) {
        _state.update {
            it.copy(
                timeoutInput = text,
                timeoutError =
                    validateNumber(
                        text,
                        ScanPlan.MIN_PROBE_TIMEOUT_MS,
                        ScanPlan.MAX_PROBE_TIMEOUT_MS,
                    ),
            )
        }
    }

    fun updateConcurrency(text: String) {
        _state.update {
            it.copy(
                concurrencyInput = text,
                concurrencyError =
                    validateNumber(
                        text,
                        ScanPlan.MIN_CONCURRENCY.toLong(),
                        ScanPlan.MAX_CONCURRENCY.toLong(),
                    ),
            )
        }
    }

    /** Validates raw input, then opens the authorization dialog (never scans yet). */
    fun startScan() {
        val current = _state.value
        if (!current.canStart) return
        val timeout = current.timeoutInput.toLongOrNull() ?: return
        val concurrency = current.concurrencyInput.toIntOrNull() ?: return
        when (
            val outcome =
                PlanInputParser.parse(
                    PlanInputParser.Input(
                        target = current.targetInput,
                        ports = current.portsInput,
                        probeTimeoutMs = timeout,
                        concurrency = concurrency,
                        maxDurationMs = ScanPlan.DEFAULT_MAX_DURATION_MS,
                    ),
                )
        ) {
            is PlanInputParser.ParseOutcome.Failure -> {
                _state.update { it.copy(error = outcome.error) }
            }
            is PlanInputParser.ParseOutcome.Success -> {
                _state.update {
                    it.copy(
                        phase = ScanPhase.AUTHORIZING,
                        pendingPlan = outcome.plan,
                        error = null,
                        totalPorts =
                            outcome.plan.tcpPorts
                                ?.expand()
                                ?.size ?: 0,
                    )
                }
            }
        }
    }

    /** Consent acknowledged in the authorization dialog: run the pending plan. */
    fun confirmScan() {
        val plan = _state.value.pendingPlan ?: return
        if (_state.value.phase != ScanPhase.AUTHORIZING) return
        scanJob?.cancel()
        _state.update {
            it.copy(
                phase = ScanPhase.RUNNING,
                progress = ScanProgress(),
                liveResults = emptyList(),
                hosts = emptyList(),
                error = null,
                pendingPlan = null,
            )
        }
        scanJob =
            viewModelScope.launch {
                runner.scan(plan).collect { event -> onEvent(event) }
            }
    }

    /** Dismisses the authorization dialog without scanning. */
    fun dismissAuthorization() {
        if (_state.value.phase != ScanPhase.AUTHORIZING) return
        _state.update { it.copy(phase = ScanPhase.INPUT, pendingPlan = null, error = null) }
    }

    fun cancelScan() {
        if (_state.value.phase != ScanPhase.RUNNING) return
        _state.update { it.copy(phase = ScanPhase.CANCELLING) }
        runner.cancel()
    }

    fun resetToInput() {
        scanJob?.cancel()
        _state.update {
            it.copy(
                phase = ScanPhase.INPUT,
                progress = ScanProgress(),
                liveResults = emptyList(),
                hosts = emptyList(),
                error = null,
                pendingPlan = null,
                totalPorts = 0,
            )
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
    }

    private fun onEvent(event: ProgressEvent) {
        when (event) {
            is ProgressEvent.PortStarted ->
                _state.update {
                    it.copy(progress = it.progress.copy(started = it.progress.started + 1))
                }
            is ProgressEvent.PortFinished ->
                _state.update { current ->
                    current.copy(
                        progress =
                            current.progress.copy(
                                finished = current.progress.finished + 1,
                                open =
                                    current.progress.open +
                                        if (event.result.state == PortState.OPEN) 1 else 0,
                            ),
                        liveResults = current.liveResults + event.result,
                    )
                }
            is ProgressEvent.ScanFinished ->
                _state.update {
                    it.copy(
                        phase = ScanPhase.FINISHED,
                        hosts = event.report.hosts,
                        liveResults = emptyList(),
                        progress = it.progress.copy(started = it.progress.finished),
                    )
                }
            is ProgressEvent.ScanFailed -> _state.update { it.copy(phase = ScanPhase.FAILED, error = event.error) }
        }
    }

    private fun validateNumber(
        text: String,
        min: Long,
        max: Long,
    ): String? =
        when {
            text.isBlank() -> "required"
            text.toLongOrNull() == null -> "must be a number"
            text.toLongOrNull()!! < min || text.toLongOrNull()!! > max -> "must be $min–$max"
            else -> null
        }
}
