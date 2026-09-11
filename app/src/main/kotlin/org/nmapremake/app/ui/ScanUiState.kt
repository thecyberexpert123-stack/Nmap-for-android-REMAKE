package org.nmapremake.app.ui

import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.HostResult
import org.nmapremake.core.model.PortResult
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.ScanPlan

/** UI phases (ARCHITECTURE §6) plus AUTHORIZING for the consent gate (PLAN §5.1.5). */
enum class ScanPhase { INPUT, AUTHORIZING, RUNNING, CANCELLING, FINISHED, FAILED }

data class ScanProgress(
    val started: Int = 0,
    val finished: Int = 0,
    val open: Int = 0,
)

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.INPUT,
    val targetInput: String = "",
    val portsInput: String = "top-100",
    val timeoutInput: String = ScanPlan.DEFAULT_PROBE_TIMEOUT_MS.toString(),
    val concurrencyInput: String = ScanPlan.DEFAULT_CONCURRENCY.toString(),
    val capabilityBanner: List<CapabilityRow> = emptyList(),
    val progress: ScanProgress = ScanProgress(),
    /** Live per-port results streamed while the scan runs. */
    val liveResults: List<PortResult> = emptyList(),
    /** Final per-host results from the finished report. */
    val hosts: List<HostResult> = emptyList(),
    val error: ScanError? = null,
    val targetError: String? = null,
    val portsError: String? = null,
    val timeoutError: String? = null,
    val concurrencyError: String? = null,
    /** Plan awaiting consent in the authorization dialog. */
    val pendingPlan: ScanPlan? = null,
    val totalPorts: Int = 0,
) {
    val canStart: Boolean
        get() = phase == ScanPhase.INPUT &&
            targetError == null &&
            portsError == null &&
            timeoutError == null &&
            concurrencyError == null &&
            targetInput.isNotBlank()

    /** True when the scan ended in the middle: results must be labeled incomplete. */
    val isIncomplete: Boolean
        get() = phase == ScanPhase.FAILED && error?.errorCode() == ErrorCode.SCAN_CANCELLED
}
