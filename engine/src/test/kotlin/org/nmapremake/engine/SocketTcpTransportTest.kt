package org.nmapremake.engine

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.Target
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SocketTcpTransportTest {
    private val target = Target("127.0.0.1")

    private class ThrowingSocket(private val throwable: Throwable) : Socket() {
        override fun connect(endpoint: SocketAddress, timeout: Int) {
            throw throwable
        }
    }

    @Test
    fun `loopback listener establishes`() = runBlocking {
        val transport = SocketTcpTransport()
        val server = ServerSocket(0)
        try {
            val outcome = transport.connect(target, server.localPort, 2_000)
            assertTrue(outcome is ConnectOutcome.Established, "outcome was $outcome")
            assertTrue((outcome as ConnectOutcome.Established).latencyMs >= 0)
        } finally {
            server.close()
        }
    }

    @Test
    fun `closed loopback port refuses`() = runBlocking {
        val transport = SocketTcpTransport()
        // Reserve a port, close it, then connect: no listener -> ECONNREFUSED.
        val server = ServerSocket(0)
        val port = server.localPort
        server.close()
        val outcome = transport.connect(target, port, 2_000)
        assertTrue(outcome is ConnectOutcome.Refused, "outcome was $outcome")
        assertTrue((outcome as ConnectOutcome.Refused).latencyMs >= 0)
    }

    @Test
    fun `connect exception maps to Refused`() {
        val transport = SocketTcpTransport(socketFactory = { ThrowingSocket(ConnectException("refused")) })
        assertTrue(transport.connect(target, 80, 1_000) is ConnectOutcome.Refused)
    }

    @Test
    fun `socket timeout maps to TimedOut`() {
        val transport =
            SocketTcpTransport(
                socketFactory = { ThrowingSocket(SocketTimeoutException("timeout")) },
            )
        assertEquals(ConnectOutcome.TimedOut, transport.connect(target, 80, 1_000))
    }

    @Test
    fun `no route maps to NoRoute`() {
        val transport =
            SocketTcpTransport(
                socketFactory = { ThrowingSocket(NoRouteToHostException("unreachable")) },
            )
        assertEquals(ConnectOutcome.NoRoute, transport.connect(target, 80, 1_000))
    }

    @Test
    fun `security exception maps to permission error with exact evidence`() {
        val transport =
            SocketTcpTransport(
                socketFactory = { ThrowingSocket(SecurityException("no INTERNET permission")) },
            )
        val outcome = transport.connect(target, 80, 1_000)
        assertTrue(outcome is ConnectOutcome.Failed)
        assertEquals(ErrorCode.PERMISSION_DENIED, (outcome as ConnectOutcome.Failed).error.errorCode())
        assertEquals("Permission denied: INTERNET", outcome.evidence)
    }

    @Test
    fun `other IO errors map to INCONCLUSIVE with class and message in evidence`() {
        val transport =
            SocketTcpTransport(
                socketFactory = { ThrowingSocket(IOException("something odd")) },
            )
        val outcome = transport.connect(target, 80, 1_000)
        assertTrue(outcome is ConnectOutcome.Failed)
        assertEquals(ErrorCode.INTERNAL, (outcome as ConnectOutcome.Failed).error.errorCode())
        assertTrue(outcome.evidence.contains("IOException"))
        assertTrue(outcome.evidence.contains("something odd"))
    }

    @Test
    fun `abort closes tracked sockets unblocking in-flight connects`() {
        val transport =
            SocketTcpTransport(
                socketFactory = {
                    object : Socket() {
                        override fun connect(endpoint: SocketAddress, timeout: Int) {
                            // Simulates a connect blocked inside the OS call: only
                            // close() (from abort) can unblock it, mirroring how the
                            // JVM aborts a real pending connect.
                            while (!isClosed) {
                                Thread.sleep(10)
                            }
                            throw IOException("closed by abort")
                        }
                    }
                },
            )
        val thread = Thread { transport.connect(target, 80, 60_000) }
        thread.isDaemon = true
        thread.start()
        Thread.sleep(200)
        transport.abort()
        thread.join(2_000)
        assertTrue(!thread.isAlive, "aborted connect did not unblock")
    }
}
