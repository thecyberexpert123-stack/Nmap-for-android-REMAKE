package org.nmapremake.core.capability

import kotlinx.serialization.Serializable

/**
 * The canonical capability list. Must stay in sync with
 * docs/CAPABILITY-MATRIX.md section 2 (the matrix is the single source of
 * truth for verdicts; this enum is its code form).
 */
@Serializable
enum class Capability {
    TCP_CONNECT,
    UDP_APP_PROBES,
    SERVICE_DETECTION,
    FINGERPRINT_INFERENCE,
    SCRIPT_PIPELINE,
    HOST_DISCOVERY_CONNECT,
    HOST_DISCOVERY_RAW,
    RAW_PACKET_BUILD,
    RAW_PACKET_TRANSMIT,
    PACKET_CAPTURE,
    OS_DETECTION_NATIVE,
    TRACEROUTE,
    IDLE_SCAN,
    FTP_BOUNCE,
    REMOTE_DELEGATION,
    RESULT_EXPORT_JSON,
}

/**
 * Availability of a capability on an executor.
 *
 * SUPPORTED / LIMITED / UNSUPPORTED / UNKNOWN are platform truth;
 * NOT_IMPLEMENTED means the platform could do it but the engine has not
 * built it yet (see docs/CAPABILITY-MATRIX.md section 1). UNKNOWN is the
 * default and can only be changed by a recorded experiment.
 */
@Serializable
enum class Availability { SUPPORTED, LIMITED, UNSUPPORTED, UNKNOWN, NOT_IMPLEMENTED }
