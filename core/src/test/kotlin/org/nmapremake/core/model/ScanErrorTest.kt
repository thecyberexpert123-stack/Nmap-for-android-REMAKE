package org.nmapremake.core.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ScanErrorTest {
    @Test
    fun `wire codes are unique and round-trip`() {
        val codes = ErrorCode.entries.map { it.wire }
        assertEquals(codes.size, codes.toSet().size)
        for (entry in ErrorCode.entries) {
            assertSame(entry, ErrorCode.fromWire(entry.wire))
        }
    }

    @Test
    fun `unknown wire code decodes to INTERNAL`() {
        val error = ScanError(code = "FUTURE_CODE", message = "raw preserved")
        assertEquals("FUTURE_CODE", error.code)
        assertEquals(ErrorCode.INTERNAL, error.errorCode())
    }

    @Test
    fun `constructor from ErrorCode uses the wire string`() {
        val error = ScanError(ErrorCode.PROBE_TIMEOUT, "no answer")
        assertEquals("PROBE_TIMEOUT", error.code)
        assertEquals("no answer", error.message)
    }

    @Test
    fun `truncated caps message length`() {
        val long = "x".repeat(1000)
        assertEquals(ScanError.MAX_MESSAGE_LENGTH, ScanError.truncated(ErrorCode.INTERNAL, long).message.length)
    }

    @Test
    fun `truncated accepts null message`() {
        assertEquals("no message", ScanError.truncated(ErrorCode.INTERNAL, null).message)
    }
}
