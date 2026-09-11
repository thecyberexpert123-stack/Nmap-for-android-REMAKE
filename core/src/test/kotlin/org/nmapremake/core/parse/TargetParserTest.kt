package org.nmapremake.core.parse

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TargetParserTest {
    private fun success(raw: String): TargetParser.ParseOutcome.Success =
        assertIs(TargetParser.parse(raw))

    private fun failure(raw: String): TargetParser.ParseOutcome.Failure =
        assertIs(TargetParser.parse(raw))

    @Test
    fun `ipv4 literal`() {
        assertEquals("127.0.0.1", success("127.0.0.1").target.label)
        assertEquals("192.168.1.10", success("192.168.1.10").target.label)
    }

    @Test
    fun `ipv6 literal`() {
        assertEquals("::1", success("::1").target.label)
        assertEquals("2001:db8::1", success("2001:db8::1").target.label)
    }

    @Test
    fun `bracketed ipv6 literal is unwrapped`() {
        assertEquals("::1", success("[::1]").target.label)
    }

    @Test
    fun `hostname`() {
        assertEquals("example.com", success("example.com").target.label)
        assertEquals("scanme.nmap.org", success("scanme.nmap.org").target.label)
    }

    @Test
    fun `unicode hostname is folded with idna`() {
        val target = success("bücher.example").target
        assertEquals("bücher.example", target.label)
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("example.com", success("  example.com  ").target.label)
    }

    @Test
    fun `empty target rejected`() {
        val error = failure("").error
        assertEquals("INVALID_TARGET", error.code)
    }

    @Test
    fun `whitespace-only target rejected`() {
        assertEquals("INVALID_TARGET", failure("   ").error.code)
    }

    @Test
    fun `internal whitespace rejected`() {
        assertEquals("INVALID_TARGET", failure("exam ple.com").error.code)
    }

    @Test
    fun `url scheme rejected`() {
        assertEquals("INVALID_TARGET", failure("http://example.com").error.code)
    }

    @Test
    fun `path suffix rejected`() {
        assertEquals("INVALID_TARGET", failure("example.com/path").error.code)
    }

    @Test
    fun `invalid ipv4 octets rejected as INVALID_TARGET`() {
        assertEquals("INVALID_TARGET", failure("256.256.256.256").error.code)
    }

    @Test
    fun `cidr rejected with dedicated code`() {
        val error = failure("10.0.0.0/8").error
        assertEquals("CIDR_NOT_SUPPORTED_YET", error.code)
        assertEquals("CIDR_NOT_SUPPORTED_YET", failure("192.168.1.0/24").error.code)
    }

    @Test
    fun `single label rejected`() {
        assertEquals("INVALID_TARGET", failure("example").error.code)
    }

    @Test
    fun `underscore label rejected`() {
        assertEquals("INVALID_TARGET", failure("foo_bar.example.com").error.code)
    }

    @Test
    fun `overlong label rejected`() {
        val label = "a".repeat(64)
        assertEquals("INVALID_TARGET", failure("$label.example.com").error.code)
    }

    @Test
    fun `overlong hostname rejected`() {
        val host = (0 until 4).joinToString(".") { "a".repeat(63) }
        assertEquals("INVALID_TARGET", failure(host).error.code)
    }

    @Test
    fun `resolved address defaults are null before resolution`() {
        val target = success("example.com").target
        assertNull(target.resolved)
        assertNull(target.family)
    }
}
