package org.nmapremake.engine

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.IpFamily
import org.nmapremake.core.model.Target
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InetHostResolverTest {
    private val resolver = InetHostResolver()

    @Test
    fun `IPv4 literal resolves to itself`() =
        runTest {
            val resolved = resolver.resolve(Target("127.0.0.1"))
            assertEquals("127.0.0.1", resolved.resolved)
            assertEquals(IpFamily.IPV4, resolved.family)
            assertEquals("127.0.0.1", resolved.label)
        }

    @Test
    fun `IPv6 literal resolves to itself`() =
        runTest {
            val resolved = resolver.resolve(Target("::1"))
            assertEquals("0:0:0:0:0:0:0:1", resolved.resolved)
            assertEquals(IpFamily.IPV6, resolved.family)
        }

    @Test
    fun `localhost resolves with a family-consistent address`() =
        runTest {
            val resolved = resolver.resolve(Target("localhost"))
            val address = requireNotNull(resolved.resolved)
            when (resolved.family) {
                IpFamily.IPV4 -> assertTrue(!address.contains(':'))
                IpFamily.IPV6 -> assertTrue(address.contains(':'))
                null -> error("family must be set after resolution")
            }
        }

    @Test
    fun `reserved invalid TLD raises UNRESOLVABLE_HOST`() =
        runTest {
            // RFC 2606 reserves ".invalid" — guaranteed not to resolve.
            val exception =
                assertFailsWith<UnresolvedHostException> {
                    resolver.resolve(Target("definitely-not-a-host.invalid"))
                }
            assertEquals(ErrorCode.UNRESOLVABLE_HOST, exception.error.errorCode())
        }
}
