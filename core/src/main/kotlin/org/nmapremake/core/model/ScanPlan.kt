package org.nmapremake.core.model

import kotlinx.serialization.Serializable
import org.nmapremake.core.capability.Capability

/**
 * Description of *what* the user wants — separated from *how* any executor
 * accomplishes it (the project's core architectural rule, PLAN section 2).
 */
@Serializable
data class ScanPlan(
    val targets: List<Target>,
    /** TCP ports to connect-scan; null means the TCP pass is skipped. */
    val tcpPorts: PortSpec? = null,
    /** Phase 3 (planned; always empty in M1): application-level UDP probes. */
    val udpProbes: List<UdpProbeSpec> = emptyList(),
    /** Phase 2 (not implemented in M1): service & version detection. */
    val serviceDetection: Boolean = false,
    /** Phase 4 (not implemented in M1): application-level fingerprint inference. */
    val fingerprinting: Boolean = false,
    /** Per-probe connect timeout, clamped to 100..60_000 ms. */
    val probeTimeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS,
    /** Maximum in-flight probes, clamped to 1..256. */
    val concurrency: Int = DEFAULT_CONCURRENCY,
    /** Whole-scan watchdog; null disables. Clamped to 1_000..3_600_000 ms. */
    val maxDurationMs: Long? = DEFAULT_MAX_DURATION_MS,
) {
    /** The capabilities this plan requires of its executor. */
    fun requiredCapabilities(): Set<Capability> = buildSet {
        if (tcpPorts != null) add(Capability.TCP_CONNECT)
        if (udpProbes.isNotEmpty()) add(Capability.UDP_APP_PROBES)
        if (serviceDetection) add(Capability.SERVICE_DETECTION)
        if (fingerprinting) add(Capability.FINGERPRINT_INFERENCE)
    }

    /** Normalized copy with clamped limits; preserves the requested intent otherwise. */
    fun clamped(): ScanPlan = copy(
        probeTimeoutMs = probeTimeoutMs.coerceIn(MIN_PROBE_TIMEOUT_MS, MAX_PROBE_TIMEOUT_MS),
        concurrency = concurrency.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY),
        maxDurationMs = maxDurationMs?.coerceIn(MIN_MAX_DURATION_MS, MAX_MAX_DURATION_MS),
    )

    companion object {
        const val DEFAULT_PROBE_TIMEOUT_MS = 5_000L
        const val MIN_PROBE_TIMEOUT_MS = 100L
        const val MAX_PROBE_TIMEOUT_MS = 60_000L

        const val DEFAULT_CONCURRENCY = 32
        const val MIN_CONCURRENCY = 1
        const val MAX_CONCURRENCY = 256

        const val DEFAULT_MAX_DURATION_MS = 600_000L
        const val MIN_MAX_DURATION_MS = 1_000L
        const val MAX_MAX_DURATION_MS = 3_600_000L
    }
}
