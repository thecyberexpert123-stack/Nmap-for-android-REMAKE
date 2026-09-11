# ARCHITECTURE — Nmap for Android REMAKE

> Status: **Design (docs-only phase)** — API sketches below define *intent*;
> signatures may be refined during implementation, but the behaviors they
> describe are locked by the acceptance criteria in PLAN.md §5.1.
> Companion documents: [`PLAN.md`](PLAN.md) (roadmap, gates, risks),
> [`adr/`](adr/) (decisions).

---

## 1. Module Map and Dependency Rules

```text
app            (Android application — Compose UI, ViewModel, DI wiring)
 └── engine     (pure Kotlin/JVM — scheduler, probes, transport, aggregation, format, router)
      └── core  (pure Kotlin/JVM — domain model, capability model, errors)

detection / capability / remote     (future modules; created when M2 / M5 / M7 start)
```

**Rules**
- `core` and `engine` have **no Android dependencies** → fully testable on the
  JVM (sandbox + CI) and reusable by a future remote-executor host.
- `app` is the only module that touches `android.*` APIs (socket transport uses
  `java.net.Socket`, which is JVM — it lives in `engine`).
- Dependencies point only downward. Future modules (`detection`, `capability`,
  `remote`) attach to `core`/`engine` per the same rule.
- No module is scaffolded before its milestone starts (no empty placeholders).

## 2. Domain Model (`core`) — API Sketch

```kotlin
// core/src/main/kotlin/org/nmapremake/core/model/

@Serializable data class Target(val label: String, val resolved: String?, val family: IpFamily)
// label = user input; resolved = InetAddress text after resolution; family = IPV4 | IPV6

sealed interface PortSpec {
    @Serializable data class Single(val port: Int) : PortSpec
    @Serializable data class List(val ports: List<Int>) : PortSpec      // deduped, order kept
    @Serializable data class Range(val first: Int, val last: Int) : PortSpec // inclusive, first<=last
    @Serializable data object TopPorts : PortSpec                        // self-authored top-100
    @Serializable data object All : PortSpec                             // 1..65535
}
// parsePortSpec(raw: String): Either<ScanError, PortSpec>  — atomic, tables in PLAN §5.1.1

enum class TransportProtocol { TCP, UDP }

@Serializable enum class PortState { OPEN, CLOSED, TIMEOUT, UNREACHABLE, INCONCLUSIVE }

@Serializable data class PortResult(
    val port: Int,
    val protocol: TransportProtocol,
    val state: PortState,
    val latencyMs: Long?,          // null unless a response was observed
    val error: ScanError?,         // non-null for every non-OPEN result
    val evidence: String           // exact formats in PLAN §5.1.2
)

@Serializable data class HostResult(
    val target: Target,
    val portResults: List<PortResult>,
    val executor: ExecutorNode,
    val capabilities: CapabilityProfile,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long
)

@Serializable data class ScanPlan(
    val targets: List<Target>,
    val tcpPorts: PortSpec? = null,        // null ⇒ TCP pass skipped
    val udpProbes: List<UdpProbeSpec> = emptyList(),   // M3
    val serviceDetection: Boolean = false,             // M2
    val fingerprinting: Boolean = false,               // M4
    val probeTimeoutMs: Long = 5_000,      // clamped 100..60_000
    val concurrency: Int = 32,             // clamped 1..256
    val maxDurationMs: Long? = 600_000     // watchdog; UI-issued default 10 min
)

@Serializable data class ScanError(val code: ErrorCode, val message: String, val cause: Throwable? = null) {
    enum class ErrorCode {
        INVALID_TARGET, UNRESOLVABLE_HOST, CIDR_NOT_SUPPORTED_YET,
        INVALID_PORT, PORT_OUT_OF_RANGE, INVALID_RANGE_ORDER, EMPTY_PORT_SPEC,
        PERMISSION_DENIED, SCAN_DEADLINE_EXCEEDED, EXECUTOR_UNAVAILABLE,
        CAPABILITY_UNSUPPORTED, INTERNAL
    }
}
```

**Capability model** (`core/capability/`):

```kotlin
enum class Capability { TCP_CONNECT, APPLICATION_PROBES, SERVICE_DETECTION,
                        FINGERPRINT_INFERENCE, RAW_PACKET_BUILD, RAW_PACKET_TRANSMIT,
                        PACKET_CAPTURE, PRIVILEGED_SCAN, UDP_APP_PROBES, REMOTE_DELEGATION }

enum class Availability { SUPPORTED, LIMITED, UNSUPPORTED, UNKNOWN }  // default UNKNOWN until proven

data class CapabilityProfile(val entries: Map<Capability, Availability>) {
    fun supports(c: Capability): Boolean   // SUPPORTED or LIMITED
}

data class ExecutorNode(
    val id: String,            // e.g. "local-android"
    val label: String,
    val type: ExecutorType,    // ANDROID_LOCAL | NATIVE | REMOTE_LINUX | REMOTE_AGENT
    val transport: String,     // "direct" | "https+mTLS" (M7)
    val reachability: ExecutorReachability,  // LOCAL | ONLINE | OFFLINE
    val capabilities: CapabilityProfile,
    val trustLevel: TrustLevel // UNVERIFIED | VERIFIED | SIGNED_REMOTE (M7)
)
```

## 3. Engine (`engine`) — API Sketch

```kotlin
// scheduler
interface ScanScheduler {
    fun scan(plan: ScanPlan, executor: ExecutorNode, transport: TcpTransport): Flow<ProgressEvent>
    suspend fun cancel()   // cooperative; guarantees completion within bounded time
}

sealed interface ProgressEvent {
    data class PortStarted(val target: Target, val port: Int, val protocol: TransportProtocol) : ProgressEvent
    data class PortFinished(val result: PortResult) : ProgressEvent
    data class ScanFinished(val report: ScanReport) : ProgressEvent   // aggregator output
    data class ScanFailed(val error: ScanError) : ProgressEvent
}

// transport — one small seam, replaced by fakes in scheduler tests
interface TcpTransport {
    /** Blocking connect attempt; returns classified outcome. Runs on Dispatchers.IO. */
    fun connect(target: Target, port: Int, timeoutMs: Long): ConnectOutcome
}
sealed interface ConnectOutcome {
    data class Established(val latencyMs: Long) : ConnectOutcome
    data class Refused(val latencyMs: Long) : ConnectOutcome
    data object TimedOut : ConnectOutcome
    data object NoRoute : ConnectOutcome
    data class Failed(val error: ScanError) : ConnectOutcome
}
class SocketTcpTransport : TcpTransport   // java.net.Socket impl; maps exceptions per PLAN §5.1.2

// probing
class TcpConnectProber(transport: TcpTransport) { fun probe(...): PortResult }  // evidence formatting

// aggregation & format
class ResultAggregator { fun aggregate(plan, hostResults, executor, capabilities): ScanReport }
interface ResultFormatter { fun format(report: ScanReport): String }
class JsonFormatter : ResultFormatter   // kotlinx.serialization, schema v1

// router (Phase 0 skeleton; real routing logic in M5/M7)
class ExecutionRouter(registry: ExecutorRegistry) {
    fun route(plan: ScanPlan): ExecutorSelection  // local executor now; selection rule documented
}
```

**Concurrency model** (locked by PLAN §5.1.3):
- One `SupervisorJob`-rooted scope per scan; probes run under
  `Semaphore(concurrency)` + `withTimeout(probeTimeoutMs)`.
- Blocking socket calls run on `Dispatchers.IO`; cancellation closes the socket
  (`invokeOnCancellation`) so in-flight connects unblock.
- Results flow through a buffered channel (`BUFFERED`, capacity 64) so a slow
  UI never blocks probing.
- `cancel()` cancels the scan job; the scheduler joins all children before
  returning (bounded completion).

## 4. Sequence Diagrams

### 4.1 Scan lifecycle (happy path)

```text
UI                ScanViewModel        ExecutionRouter       ScanScheduler         TcpTransport(IO)
 │  user input      │                       │                     │                      │
 ├─────────────────▶│ buildScanPlan()       │                     │                      │
 │                  ├──────────────────────▶│ route(plan)         │                      │
 │                  │  ExecutorSelection    │                     │                      │
 │                  │◀──────────────────────┤                     │                      │
 │                  ├────────────────────────────────────────────▶│ scan(plan, exec, t)  │
 │                  │  Flow<ProgressEvent>                        │                      │
 │                  │◀────────────────────── PortStarted(p1..pn) ─┤                      │
 │                  │                       │                     ├──connect(t,p,5s)────▶│
 │                  │                       │                     │◀──Established(12ms)──┤
 │                  │◀────────────────────── PortFinished(OPEN) ──┤                      │
 │                  │                       │                     ├──connect(t,p,5s)────▶│
 │                  │                       │                     │◀──Refused(1ms)───────┤
 │                  │◀────────────────────── PortFinished(CLOSED)─┤                      │
 │                  │◀────────────────────── ScanFinished(report)─┤                      │
 │  render results  │                       │                     │                      │
```

### 4.2 Cancellation

```text
UI                     ScanViewModel           ScanScheduler          TcpTransport(IO)
 │ cancel clicked        │                         │                        │
 ├──────────────────────▶│ onCancel()              │                        │
 │                       ├────────────────────────▶│ cancel()               │
 │                       │                         ├─ cancel scan Job ─────▶│ socket.close()
 │                       │                         │   (unblocks connect)   │
 │                       │                         │◀─ completes immediately─┤
 │                       │                         ├─ join children (bounded)│
 │                       │◀── ScanFailed(SCAN_CANCELLED) — completed ports only
 │ idle state            │                         │                        │
```

### 4.3 Failure classification (one probe)

```text
ScanScheduler            TcpTransport(Socket)               Network
    │  connect(t, p, 5000)      │                               │
    ├──────────────────────────▶│ Socket.connect(addr, 5000)    │
    │                           ├──────────────────────────────▶│  refused / dropped / unreachable
    │                           │◀── exception (typed) ──────────┤
    │                           │ map: ConnectException→Refused  │
    │                           │      SocketTimeout→TimedOut    │
    │                           │      NoRouteToHost→NoRoute     │
    │                           │      other IO→Failed(ScanError)│
    │◀── ConnectOutcome ────────┤                               │
    │  PortStateClassifier → PortResult(state, latency, error, evidence)
```

### 4.4 Capability delegation (Phase 7 — design intent, not implemented)

```text
UI → ScanViewModel → ExecutionRouter
        │  ScanPlan requests RAW_PACKET_TRANSMIT
        ├─ local profile: UNSUPPORTED ────────────────► route to executor
        │                                             whose profile == SUPPORTED
        ▼
   RemoteExecutorClient (mTLS) ── capability negotiation ──▶ Linux/Nmap executor
        │◀── structured result (versioned schema, executor identity, trust tag) ──┤
        ▼
   ResultAggregator → ScanReport { executor, capabilities, delegated = true } → UI (labeled)
```

## 5. Result Schema v1 (JSON)

```json
{
  "schemaVersion": 1,
  "generator": "nmap-android-remake/engine/0.1.0",
  "startedAtEpochMs": 1726080000000,
  "finishedAtEpochMs": 1726080045000,
  "scanPlan": { "targets": [{"label": "127.0.0.1", "resolved": "127.0.0.1", "family": "IPV4"}],
                "tcpPorts": {"type": "List", "ports": [22, 80, 443]},
                "probeTimeoutMs": 5000, "concurrency": 32 },
  "executor": { "id": "local-android", "type": "ANDROID_LOCAL", "trustLevel": "LOCAL",
                "capabilities": { "TCP_CONNECT": "SUPPORTED", "RAW_PACKET_TRANSMIT": "UNSUPPORTED" } },
  "hosts": [
    { "target": {"label": "127.0.0.1", "resolved": "127.0.0.1", "family": "IPV4"},
      "portResults": [
        {"port": 22,  "protocol": "TCP", "state": "CLOSED", "latencyMs": 1,
         "error": {"code": "CONNECTION_REFUSED", "message": "Connection refused"},
         "evidence": "Connection refused (ECONNREFUSED)"},
        {"port": 443, "protocol": "TCP", "state": "OPEN", "latencyMs": 12,
         "error": null, "evidence": "TCP connect completed in 12 ms"}
      ],
      "executor": "local-android",
      "startedAtEpochMs": 1726080000000, "finishedAtEpochMs": 1726080045000 }
  ]
}
```

Note: `ScanError.code` on the wire is a string (`CONNECTION_REFUSED`, …) so
schema v1 is stable while the internal enum may grow. Unknown wire codes decode
to `INTERNAL` with the raw value preserved in the message (forward-compatible).

## 6. App Layer (`app`)

```kotlin
// ViewModel contract
data class ScanUiState(
    val phase: ScanPhase,                 // INPUT | RUNNING | CANCELLING | FINISHED | FAILED
    val targetInput: String, val portsInput: String,
    val capabilityBanner: List<CapabilityRow>,   // derived from CapabilityProfile
    val progress: ScanProgress,           // started / finished counts
    val hosts: List<HostResult>,          // rendered per-port: state chip + latency + evidence
    val error: ScanError?
)
class ScanViewModel(...) : ViewModel() {
    val state: StateFlow<ScanUiState>
    fun updateInputs(target: String, ports: String)   // validates lazily; typed errors surfaced
    fun startScan()                                    // builds ScanPlan, launches scope
    fun cancelScan()
}
```

Compose screens: `ScanScreen` (inputs + capability banner + progress),
`ResultsList`, `PortDetail` (evidence string shown verbatim — the "why" of
every state), theme (Material 3). Banner rows are produced by a pure function
`profileToBanner(CapabilityProfile): List<CapabilityRow>` (unit-tested; never
hardcoded strings).

## 7. Test Strategy (where each gate runs)

| Layer | Where it runs | Technique |
|---|---|---|
| `core` parsing/model | Sandbox JVM + CI | Table-driven unit tests (PLAN §5.1.1) |
| `engine` classification | Sandbox JVM + CI | Real loopback sockets: listener ⇒ OPEN, refused ⇒ CLOSED; fakes for TIMEOUT/UNREACHABLE so tests are deterministic off-network |
| `engine` scheduler | Sandbox JVM + CI | Fake `TcpTransport` with latches/counters (invariants §5.1.3) |
| `engine` JSON | Sandbox JVM + CI | Round-trip + schema fixture validation |
| `app` ViewModel/UI logic | CI (JVM + Robolectric where needed) | Pure state-machine tests; banner mapping tests |
| `app` end-to-end | CI emulator (API 26 + 36) | Instrumentation: in-test loopback listener, app scans it; OPEN/CLOSED rows asserted |
| Device behavior (LAN, VPN, raw) | Stakeholder device | Manual runs recorded in AGENT-EXPERIENCE.md; M6 experiments |

Determinism rules: tests never depend on external networks. Non-routable
documentation ranges ([RFC 5737] TEST-NET addresses) are used only as fakes'
scripted outcomes, not as live probes.

## 8. Error Taxonomy (internal, typed)

`ScanError.ErrorCode` is the single source of truth (core). Transport-level
observations are mapped to it in exactly one place (`SocketTcpTransport`), so
classification logic can never diverge between UI, formatter, and tests.

---

## Sources

[RFC 5737] IPv4 Address Blocks Reserved for Documentation (203.0.113.0/24 etc.) —
https://datatracker.ietf.org/doc/html/rfc5737
