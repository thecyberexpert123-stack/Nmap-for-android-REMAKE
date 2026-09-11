package org.nmapremake.core.capability

import kotlinx.serialization.Serializable

/**
 * An executor's advertised capabilities. Missing entries mean UNKNOWN
 * (never assumed available). See docs/CAPABILITY-MATRIX.md.
 */
@Serializable
data class CapabilityProfile(
    val entries: Map<Capability, Availability> = emptyMap(),
) {
    fun availability(capability: Capability): Availability =
        entries[capability] ?: Availability.UNKNOWN

    /** True only when the capability is actually executable (SUPPORTED or LIMITED). */
    fun supports(capability: Capability): Boolean =
        availability(capability) in EXECUTABLE

    /** The canonical profile of the stock-Android local executor in M1 (matrix column E1). */
    companion object {
        private val EXECUTABLE = setOf(Availability.SUPPORTED, Availability.LIMITED)

        val ANDROID_LOCAL_M1: CapabilityProfile = CapabilityProfile(
            mapOf(
                Capability.TCP_CONNECT to Availability.SUPPORTED,
                Capability.RESULT_EXPORT_JSON to Availability.SUPPORTED,
                Capability.UDP_APP_PROBES to Availability.NOT_IMPLEMENTED,
                Capability.SERVICE_DETECTION to Availability.NOT_IMPLEMENTED,
                Capability.FINGERPRINT_INFERENCE to Availability.NOT_IMPLEMENTED,
                Capability.SCRIPT_PIPELINE to Availability.NOT_IMPLEMENTED,
                Capability.HOST_DISCOVERY_CONNECT to Availability.NOT_IMPLEMENTED,
                Capability.HOST_DISCOVERY_RAW to Availability.UNSUPPORTED,
                Capability.RAW_PACKET_BUILD to Availability.SUPPORTED,
                Capability.RAW_PACKET_TRANSMIT to Availability.UNSUPPORTED,
                Capability.PACKET_CAPTURE to Availability.UNSUPPORTED,
                Capability.OS_DETECTION_NATIVE to Availability.UNSUPPORTED,
                Capability.TRACEROUTE to Availability.UNKNOWN,
                Capability.IDLE_SCAN to Availability.UNSUPPORTED,
                Capability.FTP_BOUNCE to Availability.NOT_IMPLEMENTED,
                Capability.REMOTE_DELEGATION to Availability.NOT_IMPLEMENTED,
            ),
        )
    }
}
