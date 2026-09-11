package org.nmapremake.engine

import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.Target

/**
 * The single transport seam — the REMAKE's analog of Nsock's pluggable
 * engines (epoll/select/iocp behind one interface): one contract, multiple
 * realizations (socket / future NIO / fakes), no privilege assumption baked
 * in. See docs/NMAP-SUBSYSTEMS-DEEP-2.md §3.
 *
 * Implementations are BLOCKING and must be called on [kotlinx.coroutines.Dispatchers.IO].
 * They must also respond to thread interruption or [abort], otherwise
 * cancellation cannot unblock in-flight connects.
 */
interface TcpTransport {
    /** Blocking connect attempt with its own timeout; returns a classified outcome. */
    fun connect(
        target: Target,
        port: Int,
        timeoutMs: Long,
    ): ConnectOutcome

    /**
     * Optional hook: close any sockets of in-flight connects so cooperative
     * cancellation returns quickly. Default no-op keeps fakes trivial.
     */
    fun abort() = Unit
}

sealed interface ConnectOutcome {
    data class Established(
        val latencyMs: Long,
    ) : ConnectOutcome

    data class Refused(
        val latencyMs: Long,
    ) : ConnectOutcome

    data object TimedOut : ConnectOutcome

    data object NoRoute : ConnectOutcome

    /** Unclassified failure: typed error plus the exact evidence string to record. */
    data class Failed(
        val error: ScanError,
        val evidence: String,
    ) : ConnectOutcome
}
