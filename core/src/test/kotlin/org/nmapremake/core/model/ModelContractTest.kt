package org.nmapremake.core.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.nmapremake.core.capability.Availability
import org.nmapremake.core.capability.Capability
import org.nmapremake.core.capability.CapabilityProfile
import org.nmapremake.core.capability.ExecutorNode
import org.nmapremake.core.capability.ExecutorReachability
import org.nmapremake.core.capability.ExecutorType
import org.nmapremake.core.capability.Executors
import org.nmapremake.core.capability.TrustLevel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the core model layer: wire stability of [ErrorCode],
 * JSON schema v1 envelope behavior, and capability-matrix invariants. These
 * complement the parser/plan tests and pin the data classes' generated
 * equals/copy/serialization behavior.
 */
class ModelContractTest {
    private val json =
        Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

    // ------------------------------------------------------------------
    // ScanError / ErrorCode wire contract (JSON schema v1)
    // ------------------------------------------------------------------

    @Test
    fun `every error code round trips through its wire string`() {
        for (code in ErrorCode.entries) {
            assertEquals(code, ErrorCode.fromWire(code.wire), "wire mismatch for $code")
        }
        assertEquals(ErrorCode.entries.size, ErrorCode.entries.map { it.wire }.toSet().size)
    }

    @Test
    fun `unknown wire code decodes to INTERNAL`() {
        val error = ScanError("FUTURE_CODE", "raw")
        assertEquals(ErrorCode.INTERNAL, error.errorCode())
    }

    @Test
    fun `ScanError constructors agree and truncated applies the message contract`() {
        val direct = ScanError(ErrorCode.PROBE_TIMEOUT, "No response")
        val viaWire = ScanError("PROBE_TIMEOUT", "No response")
        assertEquals(direct, viaWire)
        assertEquals(ErrorCode.PROBE_TIMEOUT, viaWire.errorCode())
        assertEquals("no message", ScanError.truncated(ErrorCode.INTERNAL, null).message)
        val longMessage = "x".repeat(500)
        val truncated = ScanError.truncated(ErrorCode.INTERNAL, longMessage)
        assertEquals(ScanError.MAX_MESSAGE_LENGTH, truncated.message.length)
        assertTrue(longMessage.startsWith(truncated.message))
    }

    // ------------------------------------------------------------------
    // Target / IpFamily
    // ------------------------------------------------------------------

    @Test
    fun `Target effective address prefers the resolved literal`() {
        val unresolved = Target("example.com")
        assertEquals("example.com", unresolved.effectiveAddress())
        assertNull(unresolved.resolved)
        assertNull(unresolved.family)
        val resolved = unresolved.copy(resolved = "93.184.216.34", family = IpFamily.IPV4)
        assertEquals("93.184.216.34", resolved.effectiveAddress())
        assertEquals("example.com", resolved.label)
    }

    @Test
    fun `Target equals and hashCode follow the data class contract`() {
        val a = Target("scanme.nmap.org", resolved = "45.33.32.156", family = IpFamily.IPV4)
        val b = Target("scanme.nmap.org", resolved = "45.33.32.156", family = IpFamily.IPV4)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == b.copy(resolved = null))
    }

    // ------------------------------------------------------------------
    // PortResult / PortState / TransportProtocol
    // ------------------------------------------------------------------

    @Test
    fun `PortResult data class and enums behave as documented`() {
        val open =
            PortResult(
                port = 80,
                protocol = TransportProtocol.TCP,
                state = PortState.OPEN,
                latencyMs = 12,
                error = null,
                evidence = "TCP connect completed in 12 ms",
            )
        assertNull(open.error)
        assertEquals(12L, open.latencyMs)
        val closed = PortResult(
            port = 81,
            protocol = TransportProtocol.TCP,
            state = PortState.CLOSED,
            latencyMs = 3,
            error = ScanError(ErrorCode.CONNECTION_REFUSED, "Connection refused"),
            evidence = "Connection refused (ECONNREFUSED)",
        )
        assertEquals(ErrorCode.CONNECTION_REFUSED, closed.error?.errorCode())
        assertEquals(PortState.TIMEOUT, PortState.valueOf("TIMEOUT"))
        assertEquals(TransportProtocol.UDP, TransportProtocol.valueOf("UDP"))
    }

    // ------------------------------------------------------------------
    // Capability profile / executor node invariants
    // ------------------------------------------------------------------

    @Test
    fun `missing capability entries mean UNKNOWN and never assumed executable`() {
        val empty = CapabilityProfile()
        assertEquals(Availability.UNKNOWN, empty.availability(Capability.TCP_CONNECT))
        assertFalse(empty.supports(Capability.TCP_CONNECT))
        val limited =
            CapabilityProfile(
                mapOf(
                    Capability.TCP_CONNECT to Availability.LIMITED,
                    Capability.PACKET_CAPTURE to Availability.UNSUPPORTED,
                    Capability.UDP_APP_PROBES to Availability.NOT_IMPLEMENTED,
                ),
            )
        assertTrue(limited.supports(Capability.TCP_CONNECT))
        assertFalse(limited.supports(Capability.PACKET_CAPTURE))
        assertFalse(limited.supports(Capability.UDP_APP_PROBES))
    }

    @Test
    fun `local M1 executor advertises exactly the E1 matrix column`() {
        val executor = Executors.LOCAL_ANDROID
        assertEquals("local-android", executor.id)
        assertEquals(ExecutorType.ANDROID_LOCAL, executor.type)
        assertEquals(ExecutorReachability.LOCAL, executor.reachability)
        assertEquals(TrustLevel.LOCAL, executor.trustLevel)
        assertEquals("direct", executor.transport)
        assertTrue(executor.capabilities.supports(Capability.TCP_CONNECT))
        assertTrue(executor.capabilities.supports(Capability.RESULT_EXPORT_JSON))
        assertFalse(executor.capabilities.supports(Capability.UDP_APP_PROBES))
        assertFalse(executor.capabilities.supports(Capability.PACKET_CAPTURE))
        assertFalse(executor.capabilities.supports(Capability.SERVICE_DETECTION))
        assertFalse(executor.discoveredVia.isNullOrBlank())
        assertFalse(executor.capabilityProof.isNullOrBlank())
    }

    // ------------------------------------------------------------------
    // ScanPlan capability derivation
    // ------------------------------------------------------------------

    @Test
    fun `required capabilities are derived from the plan only`() {
        val plain = ScanPlan(targets = listOf(Target("127.0.0.1")), tcpPorts = null)
        assertTrue(plain.requiredCapabilities().isEmpty())
        val tcp = plain.copy(tcpPorts = PortSpec.Single(80))
        assertEquals(setOf(Capability.TCP_CONNECT), tcp.requiredCapabilities())
        val udp = tcp.copy(udpProbes = listOf(UdpProbeSpec("00")))
        assertEquals(
            setOf(Capability.TCP_CONNECT, Capability.UDP_APP_PROBES),
            udp.requiredCapabilities(),
        )
        val full = udp.copy(serviceDetection = true, fingerprinting = true)
        assertEquals(
            setOf(
                Capability.TCP_CONNECT,
                Capability.UDP_APP_PROBES,
                Capability.SERVICE_DETECTION,
                Capability.FINGERPRINT_INFERENCE,
            ),
            full.requiredCapabilities(),
        )
    }

    @Test
    fun `clamped preserves the requested plan otherwise`() {
        val plan =
            ScanPlan(
                targets = listOf(Target("127.0.0.1")),
                tcpPorts = PortSpec.Range(1, 10),
                probeTimeoutMs = 50,
                concurrency = 10_000,
                maxDurationMs = 500,
            )
        val clamped = plan.clamped()
        assertEquals(plan.targets, clamped.targets)
        assertEquals(plan.tcpPorts, clamped.tcpPorts)
        assertEquals(ScanPlan.MIN_PROBE_TIMEOUT_MS, clamped.probeTimeoutMs)
        assertEquals(ScanPlan.MAX_CONCURRENCY, clamped.concurrency)
        assertEquals(ScanPlan.MIN_MAX_DURATION_MS, clamped.maxDurationMs)
    }

    // ------------------------------------------------------------------
    // JSON schema v1 envelope (hosts/report/spec round-trips)
    // ------------------------------------------------------------------

    @Test
    fun `PortSpec variants round trip through JSON`() {
        val variants =
            listOf(
                PortSpec.Single(443),
                PortSpec.List(listOf(80, 443)),
                PortSpec.Range(1, 100),
                PortSpec.TopPorts,
                PortSpec.All,
            )
        for (spec in variants) {
            val text = json.encodeToString(PortSpec.serializer(), spec)
            val decoded = json.decodeFromString(PortSpec.serializer(), text)
            assertEquals(spec, decoded, "variant $spec did not round-trip")
        }
    }

    @Test
    fun `UdpProbeSpec round trips through JSON`() {
        val spec = UdpProbeSpec("0a0b")
        val text = json.encodeToString(UdpProbeSpec.serializer(), spec)
        val decoded = json.decodeFromString(UdpProbeSpec.serializer(), text)
        assertEquals(spec, decoded)
    }

    @Test
    fun `ScanReport envelope round trips with defaults restored`() {
        val report = sampleReport()
        val text = json.encodeToString(ScanReport.serializer(), report)
        val decoded = json.decodeFromString(ScanReport.serializer(), text)
        assertEquals(report, decoded)
    }

    @Test
    fun `schema version default is restored when omitted on the wire`() {
        val report = sampleReport()
        val text = json.encodeToString(ScanReport.serializer(), report)
        assertFalse(text.contains("\"schemaVersion\""))
        val decoded = json.decodeFromString(ScanReport.serializer(), text)
        assertEquals(1, decoded.schemaVersion)
        assertEquals(report.generator, decoded.generator)
        assertEquals(report.hosts, decoded.hosts)
    }

    @Test
    fun `unknown wire fields are ignored and port results keep their evidence`() {
        val text = json.encodeToString(ScanReport.serializer(), sampleReport())
        val withExtra = text.replaceFirst("\"hosts\": [", "\"futureField\": {\"x\": 1}, \"hosts\": [")
        val decoded = json.decodeFromString(ScanReport.serializer(), withExtra)
        val closed = decoded.hosts.single().portResults.first { it.port == 81 }
        assertEquals("Connection refused (ECONNREFUSED)", closed.evidence)
    }

    private fun sampleReport(): ScanReport {
        val executor = Executors.LOCAL_ANDROID
        val target = Target("127.0.0.1", resolved = "127.0.0.1", family = IpFamily.IPV4)
        val host =
            HostResult(
                target = target,
                portResults =
                    listOf(
                        PortResult(
                            port = 80,
                            protocol = TransportProtocol.TCP,
                            state = PortState.OPEN,
                            latencyMs = 12,
                            error = null,
                            evidence = "TCP connect completed in 12 ms",
                        ),
                        PortResult(
                            port = 81,
                            protocol = TransportProtocol.TCP,
                            state = PortState.CLOSED,
                            latencyMs = 3,
                            error = ScanError(ErrorCode.CONNECTION_REFUSED, "Connection refused"),
                            evidence = "Connection refused (ECONNREFUSED)",
                        ),
                    ),
                executor = executor,
                capabilities = executor.capabilities,
                startedAtEpochMs = 1_000,
                finishedAtEpochMs = 2_000,
            )
        return ScanReport(
            generator = "nmap-android-remake/engine/test",
            startedAtEpochMs = 1_000,
            finishedAtEpochMs = 2_000,
            scanPlan =
                ScanPlan(
                    targets = listOf(target),
                    tcpPorts = PortSpec.List(listOf(80, 81)),
                    probeTimeoutMs = 5_000,
                    concurrency = 32,
                ),
            executor = executor,
            hosts = listOf(host),
        )
    }
}
