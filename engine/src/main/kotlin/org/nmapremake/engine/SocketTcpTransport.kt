package org.nmapremake.engine

import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.Target

/**
 * [java.net.Socket]-backed [TcpTransport]. Maps exceptions per the
 * classification matrix (PLAN §5.1.2). Thread-safe; one scan at a time per
 * instance is enforced by the scheduler.
 *
 * @param socketFactory injectable for tests (e.g. sockets that throw on connect).
 */
class SocketTcpTransport(
    private val clock: EngineClock = SystemClock,
    private val socketFactory: () -> Socket = { Socket() },
) : TcpTransport {

    private val openSockets = ConcurrentHashMap.newKeySet<Socket>()

    override fun connect(target: Target, port: Int, timeoutMs: Long): ConnectOutcome {
        val started = clock.now()
        val socket = socketFactory()
        openSockets += socket
        return try {
            socket.connect(InetSocketAddress(target.effectiveAddress(), port), timeoutMs.toInt())
            ConnectOutcome.Established(clock.now() - started)
        } catch (e: ConnectException) {
            ConnectOutcome.Refused(clock.now() - started)
        } catch (e: NoRouteToHostException) {
            ConnectOutcome.NoRoute
        } catch (e: SocketTimeoutException) {
            ConnectOutcome.TimedOut
        } catch (e: SecurityException) {
            ConnectOutcome.Failed(
                ScanError(ErrorCode.PERMISSION_DENIED, "Permission denied: INTERNET"),
                "Permission denied: INTERNET",
            )
        } catch (e: IOException) {
            val message = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                .take(ScanError.MAX_MESSAGE_LENGTH)
            ConnectOutcome.Failed(
                ScanError(ErrorCode.INTERNAL, message),
                message,
            )
        } finally {
            openSockets -= socket
            runCatching { socket.close() }
        }
    }

    override fun abort() {
        openSockets.toList().forEach { runCatching { it.close() } }
    }
}
