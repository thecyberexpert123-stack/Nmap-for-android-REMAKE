package org.nmapremake.core.parse

import org.junit.jupiter.api.Test
import org.nmapremake.core.model.ErrorCode
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PortSpecParserTest {

    private fun success(raw: String): PortSpecParser.ParseOutcome.Success =
        assertIs(PortSpecParser.ParseOutcome.Success::class, PortSpecParser.parse(raw))

    private fun failure(raw: String): PortSpecParser.ParseOutcome.Failure =
        assertIs(PortSpecParser.ParseOutcome.Failure::class, PortSpecParser.parse(raw))

    @Test
    fun `single port`() {
        assertEquals(listOf(PortSpecParser.PortEntry.Single(80)), success("80").entries)
    }

    @Test
    fun `comma list keeps order and duplicates`() {
        assertEquals(
            listOf(
                PortSpecParser.PortEntry.Single(80),
                PortSpecParser.PortEntry.Single(443),
                PortSpecParser.PortEntry.Single(8080),
            ),
            success("80,443,8080").entries,
        )
    }

    @Test
    fun `whitespace around tokens is ignored`() {
        assertEquals(
            listOf(PortSpecParser.PortEntry.Single(80), PortSpecParser.PortEntry.Single(443)),
            success(" 80 , 443 ").entries,
        )
    }

    @Test
    fun `inclusive range`() {
        assertEquals(listOf(PortSpecParser.PortEntry.Range(1, 100)), success("1-100").entries)
    }

    @Test
    fun `range boundaries 1-65535 accepted`() {
        assertEquals(listOf(PortSpecParser.PortEntry.Range(1, 65535)), success("1-65535").entries)
    }

    @Test
    fun `top-100 keyword case insensitive`() {
        assertEquals(listOf(PortSpecParser.PortEntry.Bulk(top = true)), success("top-100").entries)
        assertEquals(listOf(PortSpecParser.PortEntry.Bulk(top = true)), success("TOP-100").entries)
    }

    @Test
    fun `all keyword case insensitive`() {
        assertEquals(listOf(PortSpecParser.PortEntry.Bulk(top = false)), success("all").entries)
        assertEquals(listOf(PortSpecParser.PortEntry.Bulk(top = false)), success("All").entries)
    }

    @Test
    fun `empty spec rejected`() {
        assertEquals(ErrorCode.EMPTY_PORT_SPEC.wire, failure("").error.code)
        assertEquals(ErrorCode.EMPTY_PORT_SPEC.wire, failure(" , ,").error.code)
    }

    @Test
    fun `non-numeric token rejected`() {
        assertEquals(ErrorCode.INVALID_PORT.wire, failure("abc").error.code)
    }

    @Test
    fun `port zero rejected`() {
        assertEquals(ErrorCode.PORT_OUT_OF_RANGE.wire, failure("0").error.code)
    }

    @Test
    fun `port above 65535 rejected`() {
        assertEquals(ErrorCode.PORT_OUT_OF_RANGE.wire, failure("65536").error.code)
    }

    @Test
    fun `range bounds out of range rejected`() {
        assertEquals(ErrorCode.PORT_OUT_OF_RANGE.wire, failure("1-70000").error.code)
    }

    @Test
    fun `descending range rejected`() {
        assertEquals(ErrorCode.INVALID_RANGE_ORDER.wire, failure("90-10").error.code)
    }

    @Test
    fun `non-numeric range bounds rejected`() {
        assertEquals(ErrorCode.INVALID_PORT.wire, failure("a-b").error.code)
    }

    @Test
    fun `multiple dashes rejected`() {
        assertEquals(ErrorCode.INVALID_PORT.wire, failure("80-443-900").error.code)
    }

    @Test
    fun `dangling dash rejected`() {
        assertEquals(ErrorCode.INVALID_PORT.wire, failure("-5").error.code)
        assertEquals(ErrorCode.INVALID_PORT.wire, failure("80-").error.code)
    }

    @Test
    fun `bulk keyword must stand alone`() {
        assertEquals(ErrorCode.INVALID_PORT.wire, failure("top-100,80").error.code)
        assertEquals(ErrorCode.INVALID_PORT.wire, failure("80,all").error.code)
    }

    @Test
    fun `one invalid token rejects the whole spec`() {
        assertEquals(ErrorCode.INVALID_PORT.wire, failure("80,443,abc,8080").error.code)
    }
}
