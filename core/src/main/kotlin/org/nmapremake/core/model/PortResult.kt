package org.nmapremake.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransportProtocol { TCP, UDP }

/**
 * The honest, conservative result vocabulary for a single probe.
 *
 * States match PLAN section 5.1.2: OPEN and CLOSED are verified responses;
 * TIMEOUT means no response within the probe budget — never proof of closure;
 * UNREACHABLE means a network-level error was observed; INCONCLUSIVE covers
 * anything ambiguous. No synthetic states are ever produced.
 */
@Serializable
enum class PortState { OPEN, CLOSED, TIMEOUT, UNREACHABLE, INCONCLUSIVE }

@Serializable
data class PortResult(
    val port: Int,
    val protocol: TransportProtocol,
    val state: PortState,
    /** Measured latency in milliseconds; null when no response was observed. */
    val latencyMs: Long?,
    /** Typed error — non-null for every non-OPEN result (see PLAN 5.1.2). */
    val error: ScanError?,
    /** Human-readable, auditable evidence of why the state was assigned. Never empty. */
    val evidence: String,
)
