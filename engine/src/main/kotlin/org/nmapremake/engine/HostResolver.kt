package org.nmapremake.engine

import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.IpFamily
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.Target

/** Resolves target labels at scan start (PLAN §5.1.1); literals pass through. */
interface HostResolver {
    suspend fun resolve(target: Target): Target
}

class UnresolvedHostException(val error: ScanError) : Exception(error.message)

class InetHostResolver : HostResolver {
    // The caught type is the whole signal here: resolution failure is
    // re-thrown as a typed UnresolvedHostException with an honest error.
    @Suppress("SwallowedException")
    override suspend fun resolve(target: Target): Target = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getAllByName(target.label).firstOrNull()
                ?: throw UnknownHostException(target.label)
            target.copy(
                resolved = address.hostAddress,
                family = if (address is Inet6Address) IpFamily.IPV6 else IpFamily.IPV4,
            )
        } catch (e: UnknownHostException) {
            throw UnresolvedHostException(
                ScanError(ErrorCode.UNRESOLVABLE_HOST, "cannot resolve '${target.label}'"),
            )
        }
    }
}
