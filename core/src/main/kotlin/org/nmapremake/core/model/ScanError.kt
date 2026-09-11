package org.nmapremake.core.model

import kotlinx.serialization.Serializable

/**
 * Typed, wire-stable scan error.
 *
 * The [code] field is a plain string on the wire (JSON schema v1, see
 * docs/ARCHITECTURE.md section 5): unknown wire codes decode to
 * [ErrorCode.INTERNAL] with the raw value preserved in the message, so the
 * schema stays forward-compatible while the internal enum may grow.
 */
@Serializable
data class ScanError(
    val code: String,
    val message: String,
) {
    constructor(errorCode: ErrorCode, message: String) : this(errorCode.wire, message)

    /** Maps the wire code back to the internal enum; unknown codes yield INTERNAL. */
    fun errorCode(): ErrorCode = ErrorCode.fromWire(code)

    companion object {
        const val MAX_MESSAGE_LENGTH = 256

        /** Builds a ScanError with a message truncated to the documented maximum length. */
        fun truncated(
            errorCode: ErrorCode,
            message: String?,
        ): ScanError = ScanError(errorCode, (message ?: "no message").take(MAX_MESSAGE_LENGTH))
    }
}

/** Internal error taxonomy — the single source of truth for scan failure codes. */
enum class ErrorCode(
    val wire: String,
) {
    INVALID_TARGET("INVALID_TARGET"),
    UNRESOLVABLE_HOST("UNRESOLVABLE_HOST"),
    CIDR_NOT_SUPPORTED_YET("CIDR_NOT_SUPPORTED_YET"),
    INVALID_PORT("INVALID_PORT"),
    PORT_OUT_OF_RANGE("PORT_OUT_OF_RANGE"),
    INVALID_RANGE_ORDER("INVALID_RANGE_ORDER"),
    EMPTY_PORT_SPEC("EMPTY_PORT_SPEC"),
    CONNECTION_REFUSED("CONNECTION_REFUSED"),
    PROBE_TIMEOUT("PROBE_TIMEOUT"),
    NO_ROUTE_TO_HOST("NO_ROUTE_TO_HOST"),
    PERMISSION_DENIED("PERMISSION_DENIED"),
    SCAN_DEADLINE_EXCEEDED("SCAN_DEADLINE_EXCEEDED"),
    SCAN_CANCELLED("SCAN_CANCELLED"),
    EXECUTOR_UNAVAILABLE("EXECUTOR_UNAVAILABLE"),
    CAPABILITY_UNSUPPORTED("CAPABILITY_UNSUPPORTED"),
    INTERNAL("INTERNAL"),
    ;

    companion object {
        fun fromWire(wire: String): ErrorCode = entries.firstOrNull { it.wire == wire } ?: INTERNAL
    }
}
