package org.nmapremake.core.parse

import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.Target
import java.net.IDN
import java.util.regex.Pattern

/**
 * M1 target grammar (PLAN section 5.1.1): a non-empty string containing no
 * whitespace or URI-scheme prefixes. Accepted: IPv4 literal, IPv6 literal
 * (bracketed or not), DNS hostname (including IDN unicode, folded with IDN
 * mapping). Everything else — CIDR blocks, ranges, commas — is rejected with
 * the exact code CIDR_NOT_SUPPORTED_YET so the UI can say so honestly.
 */
object TargetParser {
    fun parse(raw: String): ParseOutcome {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ParseOutcome.failure(
                ScanError(ErrorCode.INVALID_TARGET, "target must not be empty"),
            )
        }
        if (SCHEME.matcher(trimmed).find() || trimmed.any { it.isWhitespace() }) {
            return ParseOutcome.failure(
                ScanError(ErrorCode.INVALID_TARGET, "target must be an IP or hostname, no URL/whitespace"),
            )
        }

        if (looksCidr(trimmed)) {
            return ParseOutcome.failure(
                ScanError(
                    ErrorCode.CIDR_NOT_SUPPORTED_YET,
                    "CIDR ranges are planned (Phase 6); scan single targets in M1",
                ),
            )
        }
        if (looksV4(trimmed)) return ParseOutcome.success(Target(trimmed))
        if (looksLikeQuad(trimmed)) {
            // Dotted quad with out-of-range octets: not a valid literal and must
            // not silently become a "hostname" (PLAN §5.1.1: INVALID_TARGET).
            return ParseOutcome.failure(ScanError(ErrorCode.INVALID_TARGET, "invalid IPv4 literal"))
        }
        if (looksV6Literal(trimmed)) return ParseOutcome.success(Target(trimmed))
        val bracketed =
            trimmed.startsWith('[') &&
                trimmed.endsWith(']') &&
                looksV6Literal(trimmed.substring(1, trimmed.length - 1))
        if (bracketed) {
            return ParseOutcome.success(Target(trimmed.substring(1, trimmed.length - 1)))
        }
        return parseHostname(trimmed)
    }

    private fun looksCidr(s: String): Boolean = CIDR.matcher(s).matches()

    private fun looksV4(s: String): Boolean {
        val parts = s.split('.')
        return parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
    }

    private fun looksLikeQuad(s: String): Boolean {
        val parts = s.split('.')
        return parts.size == 4 && parts.all { it.toIntOrNull() != null }
    }

    private fun looksV6Literal(s: String): Boolean = s.contains(':') && s.all { it.isDigit() || it in "abcdefABCDEF:." }

    private fun parseHostname(trimmed: String): ParseOutcome {
        val ascii: String =
            try {
                IDN.toASCII(trimmed, IDN.ALLOW_UNASSIGNED).lowercase()
            } catch (_: IllegalArgumentException) {
                return ParseOutcome.failure(
                    ScanError(ErrorCode.INVALID_TARGET, "invalid hostname characters"),
                )
            }
        if (ascii.length > MAX_HOST_LENGTH) {
            return ParseOutcome.failure(ScanError(ErrorCode.INVALID_TARGET, "hostname too long"))
        }
        val labels = ascii.split('.')
        val hasInvalidLabel =
            labels.any { label ->
                label.isEmpty() || label.length > LABEL_MAX || !LABEL.matcher(label).matches()
            }
        if (labels.size < 2 || hasInvalidLabel) {
            return ParseOutcome.failure(
                ScanError(
                    ErrorCode.INVALID_TARGET,
                    "hostname must be fully qualified (e.g. example.com)",
                ),
            )
        }
        return ParseOutcome.success(Target(trimmed))
    }

    sealed interface ParseOutcome {
        data class Success(
            val target: Target,
        ) : ParseOutcome

        data class Failure(
            val error: ScanError,
        ) : ParseOutcome

        companion object {
            fun success(target: Target): ParseOutcome = Success(target)

            fun failure(error: ScanError): ParseOutcome = Failure(error)
        }
    }

    private const val MAX_HOST_LENGTH = 253
    private const val LABEL_MAX = 63

    private val SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
    private val CIDR = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}/\\d{1,2}$")
}
