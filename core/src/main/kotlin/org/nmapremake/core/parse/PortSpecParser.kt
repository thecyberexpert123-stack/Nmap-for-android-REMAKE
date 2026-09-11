package org.nmapremake.core.parse

import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.ScanError

/**
 * Grammar for a single port token: number, range a-b, or the keywords
 * `top-100` and `all` (case-insensitive). PLAN section 5.1.1.
 */
object PortSpecParser {
    fun parse(raw: String): ParseOutcome {
        val tokens = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return ParseOutcome.error(
                ScanError(ErrorCode.EMPTY_PORT_SPEC, "port spec must not be empty"),
            )
        }

        var sawBulk = false
        val entries = mutableListOf<PortEntry>()
        for (token in tokens) {
            if (sawBulk) {
                return ParseOutcome.error(
                    ScanError(
                        ErrorCode.INVALID_PORT,
                        "bulk keyword must stand alone: got extra token after it",
                    ),
                )
            }
            when (val e = parseToken(token)) {
                is ParseTokenResult.Ok -> {
                    entries += e.entry
                    if (e.entry is PortEntry.Bulk) sawBulk = true
                }
                is ParseTokenResult.Error -> return ParseOutcome.error(e.error)
            }
        }
        return ParseOutcome.Success(entries)
    }

    private fun parseToken(token: String): ParseTokenResult {
        val lower = token.lowercase()
        if (lower == "top-100") return ParseTokenResult.Ok(PortEntry.Bulk(top = true))
        if (lower == "all") return ParseTokenResult.Ok(PortEntry.Bulk(top = false))

        val dash = token.indexOf('-')
        if (dash >= 0) {
            val first = token.substring(0, dash)
            val last = token.substring(dash + 1)
            val a = first.toIntOrNull()
            val b = last.toIntOrNull()
            if (a == null || b == null) {
                return ParseTokenResult.Error(
                    ScanError(ErrorCode.INVALID_PORT, "range bounds must be numbers: '$token'"),
                )
            }
            val boundsValid = a in MIN..MAX && b in MIN..MAX
            if (!boundsValid) {
                return ParseTokenResult.Error(
                    ScanError(
                        ErrorCode.PORT_OUT_OF_RANGE,
                        "port out of range $MIN..$MAX: '$token'",
                    ),
                )
            }
            if (a > b) {
                return ParseTokenResult.Error(
                    ScanError(ErrorCode.INVALID_RANGE_ORDER, "range start > end: '$token'"),
                )
            }
            return ParseTokenResult.Ok(PortEntry.Range(a, b))
        }

        val n = token.toIntOrNull()
            ?: return ParseTokenResult.Error(
                ScanError(ErrorCode.INVALID_PORT, "not a number or known keyword: '$token'"),
            )
        if (n < MIN || n > MAX) {
            return ParseTokenResult.Error(
                ScanError(ErrorCode.PORT_OUT_OF_RANGE, "port out of range $MIN..$MAX: '$token'"),
            )
        }
        return ParseTokenResult.Ok(PortEntry.Single(n))
    }

    private const val MIN = 1
    private const val MAX = 65535

    sealed interface ParseOutcome {
        data class Success(val entries: List<PortEntry>) : ParseOutcome
        data class Failure(val error: ScanError) : ParseOutcome

        companion object {
            fun error(error: ScanError): ParseOutcome = Failure(error)
        }
    }

    sealed interface ParseTokenResult {
        data class Ok(val entry: PortEntry) : ParseTokenResult
        data class Error(val error: ScanError) : ParseTokenResult
    }

    /** Normalized entry. Single ports and ranges may repeat across tokens (kept in order). */
    sealed interface PortEntry {
        data class Single(val port: Int) : PortEntry
        data class Range(val first: Int, val last: Int) : PortEntry

        /** Bulk keywords: top = top-100, !top = all. */
        data class Bulk(val top: Boolean) : PortEntry
    }
}
