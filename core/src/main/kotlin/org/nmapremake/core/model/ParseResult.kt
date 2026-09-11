package org.nmapremake.core.model

/**
 * Minimal result container for parsers and routers: either a value or a
 * typed [ScanError]. Used instead of exceptions so failures are first-class
 * data and can never be silently dropped.
 */
sealed interface ParseResult<out T> {
    data class Ok<T>(val value: T) : ParseResult<T>
    data class Err(val error: ScanError) : ParseResult<Nothing>
}

inline fun <T, R> ParseResult<T>.map(transform: (T) -> R): ParseResult<R> = when (this) {
    is ParseResult.Ok -> ParseResult.Ok(transform(value))
    is ParseResult.Err -> this
}

fun <T> ParseResult<T>.getOrNull(): T? = (this as? ParseResult.Ok)?.value

fun <T> ParseResult<T>.errorOrNull(): ScanError? = (this as? ParseResult.Err)?.error
