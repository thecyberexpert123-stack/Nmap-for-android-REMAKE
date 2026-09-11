package org.nmapremake.core.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ParseResultTest {

    @Test
    fun `map transforms Ok values`() {
        val result: ParseResult<Int> = ParseResult.Ok(2)
        assertEquals("2!", result.map { "$it!" }.getOrNull())
    }

    @Test
    fun `map passes errors through`() {
        val error = ScanError(ErrorCode.INTERNAL, "boom")
        val result: ParseResult<Int> = ParseResult.Err(error)
        val mapped = result.map { it + 1 }
        assertSame(error, mapped.errorOrNull())
        assertNull(mapped.getOrNull())
    }

    @Test
    fun `errorOrNull is null for Ok`() {
        assertNull(ParseResult.Ok(1).errorOrNull())
    }
}
