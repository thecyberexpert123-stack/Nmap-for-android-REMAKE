package org.nmapremake.engine.json

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.ParseResult
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.ScanReport

/**
 * Formats/parses JSON schema v1 (ARCHITECTURE §5). [ScanReport] is the
 * envelope model; unknown wire keys are ignored on parse (forward
 * compatible), and unknown `ScanError.code` strings are preserved verbatim
 * while decoding to INTERNAL.
 */
class JsonFormatter(private val json: Json = Default) {

    fun format(report: ScanReport): String = json.encodeToString(ScanReport.serializer(), report)

    fun parse(text: String): ParseResult<ScanReport> =
        try {
            ParseResult.Ok(json.decodeFromString(ScanReport.serializer(), text))
        } catch (e: SerializationException) {
            ParseResult.Err(
                ScanError(
                    ErrorCode.INTERNAL,
                    "invalid JSON report: ${e.message.orEmpty().take(MAX_ERROR_DETAIL)}",
                ),
            )
        } catch (e: IllegalArgumentException) {
            ParseResult.Err(
                ScanError(
                    ErrorCode.INTERNAL,
                    "invalid JSON report: ${e.message.orEmpty().take(MAX_ERROR_DETAIL)}",
                ),
            )
        }

    companion object {
        private const val MAX_ERROR_DETAIL = 160

        val Default: Json = Json {
            prettyPrint = true
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
    }
}
