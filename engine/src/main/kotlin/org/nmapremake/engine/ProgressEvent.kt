package org.nmapremake.engine

import org.nmapremake.core.model.PortResult
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.ScanReport
import org.nmapremake.core.model.Target
import org.nmapremake.core.model.TransportProtocol

/**
 * Progress stream of one scan (ARCHITECTURE §3, PLAN §5.1.3 invariants):
 * per-port lifecycle events, terminated by exactly one [ScanFinished] or
 * [ScanFailed].
 *
 * [PortFinished] carries the target in addition to the architecture sketch's
 * [PortResult] so the stream stays well-formed for multi-target plans
 * (recorded in CHANGELOG).
 */
sealed interface ProgressEvent {
    data class PortStarted(
        val target: Target,
        val port: Int,
        val protocol: TransportProtocol,
    ) : ProgressEvent

    data class PortFinished(
        val target: Target,
        val result: PortResult,
    ) : ProgressEvent

    data class ScanFinished(
        val report: ScanReport,
    ) : ProgressEvent

    data class ScanFailed(
        val error: ScanError,
    ) : ProgressEvent
}
