# PLAN — Nmap for Android REMAKE

> Status: **PLAN ITERATION (docs-only)** — application code deliberately on hold per stakeholder decision (2026-09-11). Stack decisions approved: Jetpack Compose · GPL-2.0-or-later · minSdk 26.
> Branch: `arena/01a08f45-nmap-for-android-remake` (never merged — project rule #23)
> Date: 2026-09-11
> Author: Agent (Senior Front-End / UI Developer role), per project guidelines 1–23

This document is the concrete, auditable execution plan derived from the task brief
"Rebuilding Nmap for Android — From Kotlin Sockets to Raw Packets". It follows the
project's own roadmap (Phases 0–8) and its "never fake anything" principles.

---

## 1. Scope and Requirements

### 1.1 Project definition (from the brief)

Build a **new Android-native network scanner inspired by Nmap's capabilities**,
progressively reproducing functionality where the Android platform permits it and
explicitly delegating capabilities it cannot provide locally.

Three goals from the brief, with our stance:

| Goal | Stance |
|---|---|
| **A. Android Network Scanner** | In scope. Completely realistic with Kotlin + standard sockets. |
| **B. Android Nmap-like Engine** | In scope, progressively (service detection, fingerprinting, scheduling, databases). |
| **C. Full Nmap capability parity on stock Android** | Out of scope for local execution. Requires root/CAP_NET_RAW. Addressed by *capability delegation* to authorized executors (Phase 7), never by pretending. |

### 1.2 Non-negotiable principles (from the brief, §29)

1. **No fake results** — the engine only reports real network observations.
2. **No "fake Nmap"** — never claim Nmap parity; measure feature-by-feature (§28 Phase 8).
3. **No fake raw packet support** — building packet byte-arrays ≠ transmitting them.
4. **No fake OS detection** — application-level inference is labeled as inference, not Nmap OS fingerprinting.
5. **No fake privilege** — Kotlin/VPN/JNI do not grant privileges Android denies.
6. **Honest capability reporting** — UI shows what is locally available vs. delegated vs. impossible.

### 1.3 Roadmap (mapped from the brief, §28)

| Phase | Content | Executor / capability |
|---|---|---|
| 0 | Architecture: `ScanPlan`, `CapabilityProfile`, `ExecutorNode`, `ScanResult`, `ExecutionRouter`, result model | Local Android |
| 1 | TCP connect engine: target/port parsing, scheduling, probing, timeouts, concurrency, structured results | Local Android (sockets) |
| 2 | Service detection: protocol probes, response parsing, service + basic version inference | Local Android (application layer) |
| 3 | UDP application probing with conservative states (`RESPONDED`, `NO_RESPONSE`, `INCONCLUSIVE`, …) | Local Android (application layer) |
| 4 | Fingerprinting: feature extraction, clean-room fingerprint DB, candidate matching, confidence, evidence | Local Android (inference) |
| 5 | Android capability system: detect interfaces, execution methods, restrictions | Local Android |
| 6 | Native boundary (NDK): **only** where a specific capability requires it | Native, capability-gated |
| 7 | Remote executor: authenticated executor protocol → Linux/Nmap executor → structured results | Delegated capability |
| 8 | Advanced compatibility: measure against Nmap feature-by-feature; honest comparison | Mixed |

### 1.4 First milestone (proposed, awaiting approval)

**Phase 0 + Phase 1 only** — the brief itself says "The first version should be boring":

> Target → Port → TCP connection attempt → Success/failure/timeout → Structured result

Deliverables: multi-module Android project, domain model, TCP connect engine with
controlled concurrency, JSON result output, minimal Compose UI, full unit-test and
CI setup, and this documentation set. *No* service detection, UDP, VPN, native, or
remote-executor code in this milestone (recorded as future work, not implemented —
no scope creep, rule #19).

---

## 2. Architecture Overview

The core architectural rule from the brief: **separate intent from execution.**
`ScanPlan` describes *what* is wanted; the router decides *how* this device can do it.

### 2.1 Layered view

```text
Android UI (Compose)                — capability-aware, never offers what's unavailable
        │
ScanViewModel / ScanController      — lifecycle-aware orchestration
        │
ExecutionRouter (capability-aware)  — matches ScanPlan intents to executors
        │
Engine (pure Kotlin/JVM)            — scheduler, TCP connect prober, aggregator, formatters
        │
Transport (JVM sockets)             — java.net.Socket (works on Android + JVM tests)
        │
Android/Linux network stack         — the platform boundary (capability system lives here)
```

### 2.2 Conceptual module map (from the brief, §4)

```text
Nmap Android REMAKE
├── core/         ScanPlan · Target · Port · ScanResult · ScanError · CapabilityProfile · ExecutorNode
├── engine/       ScanScheduler · TcpTransport · UdpTransport · ResultAggregator
├── detection/    ServiceDetector · VersionDetector · FingerprintEngine      (Phase 2/4)
├── results/      JsonFormatter · XmlFormatter                                (Phase 1: JSON; XML later)
├── capability/   CapabilityDetector · NetworkManager · ExecutionController   (Phase 5)
├── remote/       Executor protocol client                                    (Phase 7)
└── app/          Android UI + ViewModel + transport wiring (Compose)
```

### 2.3 Executor model (Phase 0 skeleton; full routing in Phase 5/7)

```text
ExecutorNode { id, label, type, transport, reachability, capabilities, trustLevel }
CapabilityProfile { capability -> SUPPORTED | LIMITED | UNSUPPORTED | UNKNOWN }
```

Phase 0/1 ships exactly one real executor — the **Android Local Executor** with an
honest profile (TCP connect ✓, service detection ✗-until-Phase-2, raw packet ✗) —
so the router abstraction exists from day one without pretending more.

**Delegation architecture (adopted, stakeholder design 2026-09-11):** where a scan
intent exceeds local capability, the router does not fake it — it routes to the
**closest legitimate packet-generation capability**: a LAN agent, a capable
network appliance, a VPN endpoint, or a remote Linux/Nmap executor. Android is
the controller/planner/analyzer; executors provide capabilities; every delegated
result carries executor identity + capability proof. Architectural invariants:
*encryption ≠ privilege, tunneling ≠ packet crafting, forwarding ≠ packet
generation*. Full analysis: [`NMAP-SUBSYSTEMS-DEEP.md`](NMAP-SUBSYSTEMS-DEEP.md) §2.
The canonical capability × executor matrix with per-cell verdicts, evidence
requirements, routing rules, and UI derivation is
[`CAPABILITY-MATRIX.md`](CAPABILITY-MATRIX.md) — the single source of truth for
routing and UI.

### 2.4 Data flow (Phase 1)

```text
User input → TargetParser / PortSpecParser → ScanPlan
          → ScanScheduler (bounded coroutine pool)
          → TcpConnectProber (socket connect + latency + errno classification)
          → ResultAggregator → HostResult[] → JsonFormatter → UI (and exportable file)
```

---

## 3. Tech Stack Decisions

| Concern | Decision | Justification |
|---|---|---|
| Language | **Kotlin 2.2.x** (built-in Kotlin via AGP 9) | Brief mandates Kotlin; current stable toolchain line [1] |
| Build | **AGP 9.0.x + Gradle 9.1+**, version catalog (`libs.versions.toml`) | Current stable; AGP 9 bundles Kotlin (KGP 2.2.10) [2] [3]. Fallback: AGP 8.13 if blockers are found. |
| SDK levels | `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` — **APPROVED (2026-09-11)** | Play requires API 36 for new apps from 2026-08-31 [4]; minSdk 26 covers ≈ all active devices and modern APIs. ADR-0003. |
| UI | **Jetpack Compose + Material 3** — **APPROVED (2026-09-11)** | Modern, capability-aware UI with far less boilerplate; brief requires a self-describing UI. ADR-0002. |
| Concurrency | **kotlinx.coroutines** (structured concurrency, `Semaphore`-bounded parallelism, `Flow` for progress) | Brief explicitly names coroutines; controlled concurrency, not task storms. |
| Serialization | **kotlinx.serialization** (JSON; XML formatter later) | First-party, no reflection; stable result model with `schemaVersion`. |
| Async model | ViewModel + `StateFlow`; scans cancelled via `viewModelScope` | Android lifecycle-safe (brief §7). |
| Tests | JUnit 5 (JVM), kotlinx-coroutines-test, JaCoCo coverage | Engine is pure JVM → fast, deterministic tests in CI and sandbox. |
| Lint/format | ktlint + detekt, `allWarningsAsErrors` | Quality gates (below). |
| CI | GitHub Actions: build, unit tests, lint, (later) emulator tests | Verification channel; also proves build reproducibility. |
| DI | **Manual DI** for this milestone | Avoids a dependency until complexity justifies one (rule #16). Hilt is recorded as an option if the graph grows. |
| NDK/native | **Not scaffolded now** | Rule from brief §28 Phase 6: native only when a capability demands it. |

**Deliberately excluded** (rule #16, no junk): Retrofit/OkHttp (no HTTP client needed yet),
Room (results are files/JSON, not a DB, until Phase 4 needs fingerprint DB), Dagger/Hilt (see above),
any packet-crafting library (jNetPcap etc. — can't help without privilege; revisit in Phase 6).

### 3.1 Licensing & compliance (brief §30)

Verified facts: Nmap has been licensed under the **NPSL** (based on GPLv2, with extra
conditions) since 7.90 (Oct 2020); earlier versions were GPLv2 [5] [6] [7]. NPSL is not
accepted as free by Fedora [8].

Policy derived for this project:
1. **Clean-room implementation.** We do not copy Nmap source, or Nmap data files
   (`nmap-service-probes`, OS fingerprint DB, etc.). Our own probe/response DB and
   fingerprint format will be original work, inspired by the *concepts*, not the assets.
2. Our own code license: **APPROVED (2026-09-11): GPL-2.0-or-later** — stakeholder
   decision (rule #20). `LICENSE` file added (verbatim GPLv2 text from SPDX
   license-list-data). ADR-0001. Copyright holder to be filled in by the project
   owner before first release.
3. If Phase 7 ever bundles/invokes a real `nmap` binary (e.g., inside an authorized
   Linux executor image or Termux), its distribution is governed by NPSL/GPLv2 for
   those components and must be handled explicitly then — **flagged, not decided now**.

---

## 4. Coding Standards and Quality Gates

1. **Kotlin style**: official Kotlin coding conventions, enforced by ktlint.
2. **Static analysis**: detekt with a curated ruleset (no commented-out code, magic
   numbers banned outside config, complexity thresholds).
3. **Zero-warning builds**: `allWarningsAsErrors = true`.
4. **Tests**: every public API in `core`/`engine` has unit tests. Target: **≥ 80%
   line coverage** on `core` and `engine` (JaCoCo, enforced in CI). UI covered by
   Compose previews + later instrumentation/emulator tests.
5. **Commits**: Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `chore:`),
   one concern per commit, linked to phase.
6. **No merges** (project rule #23): all work stays on the session branch; CI builds
   the branch directly.
7. **Documentation**: CHANGELOG.md (Keep a Changelog) and AGENT-EXPERIENCE.md updated
   *during* work; ADRs in `docs/adr/` for material architecture decisions.
8. **Failure handling**: every probe outcome maps to an explicit state with evidence
   (no silently swallowed exceptions); timeout/cancellation are first-class paths.

---

## 5. Deliverables and Acceptance Criteria

Detailed API sketches and sequence diagrams live in
[`docs/ARCHITECTURE.md`](ARCHITECTURE.md). This section defines what *passing*
means for each milestone. All values marked "default" are design decisions
recorded here; they become locked constants in code and are unit-tested.

### 5.1 Milestone 1 — Phase 0 + Phase 1

| # | Deliverable | Acceptance criteria (testable) |
|---|---|---|
| D1 | Multi-module Gradle project builds clean (`./gradlew build` green) | CI passes; zero warnings (`allWarningsAsErrors`); reproducible wrapper |
| D2 | `core` domain model (`ScanPlan`, `Target`, `PortSpec`, `PortState`, `PortResult`, `HostResult`, `ScanError`, `CapabilityProfile`, `ExecutorNode`) | Unit tests cover parsing (valid + invalid + edge cases), equality, serialization round-trip |
| D3 | Target/port parser (`192.168.1.10`, `example.com`, `22,80,443`, `1-1000`, `all`, `top-100`) | Parsing tables 5.1.1 pass; malformed input rejected with typed `ScanError` |
| D4 | `ScanScheduler` with bounded concurrency, per-probe timeout, cooperative cancellation, progress via `Flow` | Async invariants 5.1.3 pass (deterministic, fake-transport tests) |
| D5 | `TcpConnectProber` with honest classification (`OPEN`/`CLOSED`/`TIMEOUT`/`UNREACHABLE`/`INCONCLUSIVE`) + latency + evidence | Classification matrix 5.1.2 passes against real loopback sockets + fakes |
| D6 | `ResultAggregator` + JSON formatter (schema-versioned) | Round-trip tests; output validates against schema v1 (ARCHITECTURE.md §5) |
| D7 | Compose app: target/ports input, scan, live progress, per-port results, capability banner | UI checks 5.1.5; banner data-driven from `CapabilityProfile` |
| D8 | CI workflow (build + test + lint on push) | Green on this branch; coverage gate 5.1.6 enforced |
| D9 | Docs: plan, ARCHITECTURE.md, CHANGELOG, AGENT-EXPERIENCE, README | Present and maintained |

#### 5.1.1 Target & port parsing (D3) — input/output tables

`PortSpec` grammar (M1): single port · comma list · inclusive range ·
`top-100` (our own curated list — self-authored data constant, not Nmap's) ·
`all` (= 1–65535). Whitespace tolerated around tokens. A spec is parsed
atomically: any invalid token rejects the entire spec.

| Input | Expected result |
|---|---|
| `"22"` | `[22]` |
| `"22,80,443"` | `[22, 80, 443]` (order preserved, duplicates removed) |
| `" 22 , 80 "` | `[22, 80]` |
| `"1-1000"` | `[1..1000]` (1000 entries) |
| `"80-80"` | `[80]` |
| `"top-100"` | our curated list: exactly 100 distinct ports, all in 1–65535 |
| `"all"` | `[1..65535]` (65535 entries) |
| `""` | `ScanError(EMPTY_PORT_SPEC)` |
| `"0"` | `ScanError(PORT_OUT_OF_RANGE)` — valid range is 1–65535 |
| `"65536"` | `ScanError(PORT_OUT_OF_RANGE)` |
| `"1-0"` | `ScanError(INVALID_RANGE_ORDER)` |
| `"1-70000"` | `ScanError(PORT_OUT_OF_RANGE)` |
| `"abc"`, `"-5"`, `"22,abc"` | `ScanError(INVALID_PORT)` |
| `"192.168.1.0/24"` | `ScanError(CIDR_NOT_SUPPORTED_YET)` — honest; CIDR expansion is a planned later milestone (see RECOMMENDATIONS) |

`Target` parsing:

| Input | Expected result |
|---|---|
| `"127.0.0.1"` | IPv4 literal target (always permitted) |
| `"::1"` | IPv6 literal target (loopback; connect supported) |
| `"192.168.1.10"` | IPv4 literal target |
| `"localhost"` | resolved via `InetAddress` at scan start; stored with original label |
| `"example.com"` | resolution at scan start; failure → whole-scan `ScanError(UNRESOLVABLE_HOST)` |
| `"256.256.256.256"` | `ScanError(INVALID_TARGET)` |
| `"10.0.0.0/8"` | `ScanError(CIDR_NOT_SUPPORTED_YET)` |

#### 5.1.2 TCP result classification (D5) — matrix

Defaults: per-probe connect timeout **5 000 ms** (clamped to 100–60 000 ms),
single attempt per port in M1 (no retries — retry policy is a recorded
recommendation, not silently added).

| Observation | `PortState` | `latencyMs` | Evidence (exact format, tested) |
|---|---|---|---|
| `connect()` returns | `OPEN` | measured elapsed | `"TCP connect completed in {n} ms"` |
| `ConnectException` / ECONNREFUSED | `CLOSED` | measured elapsed | `"Connection refused (ECONNREFUSED)"` |
| `SocketTimeoutException` / ETIMEDOUT | `TIMEOUT` | `null` | `"No response within {timeout} ms"` |
| `NoRouteToHostException` (EHOSTUNREACH) / ENETUNREACH | `UNREACHABLE` | `null` | `"No route to host (EHOSTUNREACH)"` |
| any other `IOException` | `INCONCLUSIVE` | `null` | exception class + message, truncated to 256 chars |
| `SecurityException` (missing INTERNET permission) | `INCONCLUSIVE` | `null` | `"Permission denied: INTERNET"` (+ typed `ScanError`) |

Rules tested explicitly: no synthetic states are ever produced; every non-OPEN
result carries a non-null `error`; `evidence` is never empty.

#### 5.1.3 Scheduler & async invariants (D4)

Deterministic tests use a **fake `TcpTransport`** (scriptable outcomes, gated
latches) so timing paths are not dependent on the test network; real-socket
tests are reserved for the classification matrix on loopback only.

1. **Concurrency cap**: scan 64 ports with `concurrency = 8` → observed maximum
   in-flight probes == 8 (instrumented counter in fake), exactly 64 results.
2. **Timeout enforcement**: fake transport that blocks until released → probe
   completes as `TIMEOUT` at ≈ configured timeout (asserted wall-clock bound:
   ≤ timeout + 500 ms slack); no thread leak (coroutine jobs all completed).
3. **Cancellation**: start a 1 000-port scan with a slow fake; cancel at 100 ms
   → scheduler returns within 500 ms; results contain only completed ports;
   all probe jobs terminated (structured-concurrency test).
4. **Progress stream**: events are well-formed — `PortStarted(p)` precedes
   `PortFinished(p)` for every p; exactly one `ScanFinished` or `ScanFailed`.
5. **Watchdog**: optional `maxDuration` (default **10 min** for UI-issued scans,
   clamped 1 s–1 h) forces `ScanFailed(SCAN_DEADLINE_EXCEEDED)`.
6. **Blocking I/O containment**: socket connects run on `Dispatchers.IO`;
   cancellation unblocks in-flight connects by closing the socket (documented
   in ARCHITECTURE.md §4; tested via fake-close semantics).
7. **Defaults**: `concurrency = 32` (clamped 1–256); probe timeout 5 s (clamped
   0.1–60 s).

#### 5.1.4 Output & formatting (D6)

- JSON schema v1 (defined in ARCHITECTURE.md §5): envelope carries
  `schemaVersion`, `generator` (`nmap-android-remake/engine/{version}`),
  `startedAt`, `finishedAt`, `scanPlan`, `hosts`.
- Round-trip: `HostResult == decode(encode(HostResult))` for every state,
  including all-failure and empty-result cases.
- Unresolvable host → `ScanFailed(UNRESOLVABLE_HOST)` envelope, not a fake
  empty host.
- Formatter never throws on valid inputs; malformed model states are rejected
  at construction (factory validation), not at format time.

#### 5.1.5 UI (D7)

- Capability banner rows are **derived from `CapabilityProfile`** (unit-tested
  mapping profile → banner rows). M1 profile: TCP connect = SUPPORTED; service
  detection = UNSUPPORTED (Phase 2); raw packet = UNSUPPORTED on stock Android;
  VPN = UNKNOWN (Phase 6 experiment). No hardcoded banner strings.
- Emulator (CI): launch → scan `127.0.0.1` against a loopback listener started
  by the instrumentation test → OPEN row appears with latency; a refused port
  shows CLOSED. (End-to-end proof on-device.)
- Invalid input shows the typed `ScanError` message; no crash (espresso-level
  assertion).
- Cancel button → UI returns to idle ≤ 1 s after scheduler completes.

#### 5.1.6 Quality gates (enforced in CI, failing = red build)

- JaCoCo **≥ 80 % line coverage** on `core` and `engine`.
- ktlint + detekt: **0 issues** (detekt failThreshold = 0, curated ruleset).
- `allWarningsAsErrors = true`; `lintDebug` **0 errors**.
- CI matrix: (a) JVM build+test+lint; (b) emulator instrumented smoke test on
  **API 26** and **API 36**.

#### 5.1.7 Evidence required for milestone sign-off

- Green CI (above), plus **one real-device verification**: a LAN scan of the
  device's own network (e.g., gateway/known host) performed by the stakeholder
  on real hardware, results recorded in AGENT-EXPERIENCE.md with device model
  and Android version. The agent cannot fabricate this (rule #21) — the
  milestone is not "done" without it.

### 5.2 Later milestones — acceptance criteria (outline, to be finalized at each gate)

| Milestone | Concrete acceptance criteria (preliminary) |
|---|---|
| **M2 (Phase 2)** service & version detection | Local lab of ≥ 5 known services (e.g., nginx, OpenSSH, mosquitto, vsftpd) on loopback/LAN. Probes: HTTP GET/HEAD, TLS ClientHello banner, SSH banner, SMTP greeting, null-probe banner read. **AC**: correct identification ≥ 90 % on the lab; every identification carries evidence = probe name + response prefix (≤ 256 B) + sha256; evidence gains **structured provenance**: typed `StateReason(code, sourceIp?, ttl?)` modeled on Nmap's `state_reason_t` (reason + the observation that determined it); unidentified → `UNKNOWN` state with evidence, never guessed; version inference labeled "probable" with a confidence score in [0,1] and a documented threshold; false-positive rate < 5 % measured on the lab. Engine adopts the verified Nmap techniques as *concepts* with self-authored content: NULL-probe-first, `softmatch` family pruning, `rarity` probe budgets, fallback chains, SSL post-processor, `tcpwrapped` detection, and the srtt/rttvar/timeout formulas (`timeout = srtt + 4·rttvar`) replacing M1's fixed per-probe timeout ([NMAP-SUBSYSTEMS-DEEP.md](NMAP-SUBSYSTEMS-DEEP.md) §1, §3). |
| **M3 (Phase 3)** UDP application probing | Probes: DNS (query against a local resolver), mDNS (PTR `_services._dns-sd._udp.local`), echo. States restricted to `RESPONDED` / `NO_RESPONSE` / `APPLICATION_IDENTIFIED` / `INCONCLUSIVE`. **AC**: `NO_RESPONSE` is never rendered or described as "closed"; each result records the probe payload summary; verified with a local dnsmasq instance (present/absent cases). |
| **M4 (Phase 4)** fingerprinting | Fingerprint DB is self-authored (format: features → candidate weights); features derived from application-layer observations (banner patterns, TLS parameters, timing deltas, header order). Feature-model conventions adopted from the verified IPv6 engine: unavailable features = −1, per-feature scaling into [0,1] ([NMAP-SUBSYSTEMS-DEEP-4.md](NMAP-SUBSYSTEMS-DEEP-4.md) §2). Matching layer reuses the verified Nmap *concepts* on these features: weighted point scoring (per-category weights), logistic score mapping `100/(1+e^x)`, novelty rejection (variance-scaled distance threshold), and the top-two-within-10% ambiguity rule ([NMAP-SUBSYSTEMS-DEEP.md](NMAP-SUBSYSTEMS-DEEP.md) §4). **AC**: every DB entry carries `source: self-authored` + a test vector; matching emits candidates with confidence + evidence; output is explicitly labeled "application-level inference", never "Nmap OS detection"; comparison experiment documented. |
| **M5 (Phase 5)** capability system | On-device detection: socket connect ✓; raw socket attempt → observe `EPERM` and report `UNSUPPORTED` (measured, not assumed); `VpnService` presence → `UNKNOWN` until the M6 experiment; interface enumeration via `ConnectivityManager` only (no extra permissions). **AC**: unit tests with fakes; on-device report matches expectations on emulator + real device; UI banner reflects the measured profile. |
| **M6 (Phase 6)** native/VPN experiments | Experiment matrix: raw-socket attempt (expected `EPERM`), VpnService tun packet-injection test against a controlled LAN responder, and a hop-limited TTL-sweep connect traceroute variant (slow/noisy; feasibility of per-hop observation via sockets). **AC**: results recorded verbatim (device model, Android build, command/output); conclusion updates `CapabilityProfile` defaults; any capability is gated behind proof — no claimed capability without a passing experiment. |
| **M7 (Phase 7)** remote executor | Authenticated executor protocol (mTLS + capability negotiation + versioned result schema) with a written threat model (trust, integrity, replay, injection). **AC**: threat model reviewed and accepted by stakeholder; integration tests between two JVM processes; executor topology discovery (authenticated LAN enrollment + remote enrollment) selects the *closest capable* executor per scan intent; every delegated result carries executor identity, trust level, and a capability proof; delegated results visibly distinguished in the UI; no executor capability claims are trusted without a verification path. Transport is capability-independent (TLS control channel and/or VpnService tunnel) — encryption ≠ privilege ([NMAP-SUBSYSTEMS-DEEP.md](NMAP-SUBSYSTEMS-DEEP.md) §2). |
| **M8 (Phase 8)** compatibility measurement | Feature-by-feature table vs. the Nmap feature set, each row: feature, status (`LOCAL` / `DELEGATED` / `UNAVAILABLE` / `UNVERIFIED`), how it was measured. **AC**: published in docs; every `LOCAL` claim links to a passing test or recorded experiment; no "Nmap-compatible" wording without this table. |

---

## 6. Risk and Mitigation Plan

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Platform boundary misjudged (e.g., VPN raw-injection assumption) | Medium | High | Verify experimentally on-device; never claim unverified capability; capability model defaults to UNKNOWN until proven |
| Unauthorized/unethical scanning | Medium | Legal/ethical | Explicit user consent UI, authorization disclaimer, default target = loopback/local examples; no stealth features |
| Battery/thermal/DoSto the device's own connection | Medium | UX | Bounded concurrency (default ~32), rate limits, lifecycle-aware cancellation, foreground service with notification in later phases |
| Android background restrictions kill scans | High | UX | Phase 1 scans run in foreground activity/ViewModel; Phase 5+ adds foreground service; document behavior honestly |
| NPSL contamination | Low (policy prevents) | Legal | Clean-room rule; dependency/license review gate in CI (dependency license check added when deps grow) |
| Toolchain instability (AGP 9 built-in Kotlin) | Low | Schedule | Versions pinned in catalog; fallback AGP 8.13 documented; CI catches breakage |
| Sandbox has no Android device/emulator | Certain | Verification gap | JVM-level engine tests locally (real sockets on loopback) + GitHub Actions emulator matrix for UI/device behavior; report exactly what was verified where (rule #21) |
| Scope creep | Medium | Schedule | Phases are gated; unrelated improvements go into `docs/RECOMMENDATIONS.md`, never silently into code (rule #19) |

---

## 7. Changelog and Experience-Tracking Workflow

- **CHANGELOG.md** — Keep a Changelog format; an `[Unreleased]` section receives an
  entry for every change set (features, fixes, docs), written at the time of the change.
- **AGENT-EXPERIENCE.md** — dated journal entries covering decisions made, problems
  hit, what was learned, and what failed. Written *during* development (rule #4),
  never retrofitted.
- **docs/adr/** — Architecture Decision Records for material decisions
  (module boundaries, clean-room policy, license choice, formatter choice, …).
- Both files are part of every commit that changes behavior.

---

## 8. Sample Folder Structure

```text
Nmap-for-android-REMAKE/
├── .github/workflows/ci.yml            # build + test + lint on push (branch only)
├── .editorconfig
├── .gitignore
├── LICENSE                              # per approval (Q3)
├── README.md                            # what this project is (and is not)
├── CHANGELOG.md
├── AGENT-EXPERIENCE.md
├── docs/
│   ├── PLAN.md                          # this document
│   ├── ARCHITECTURE.md                  # module map, API sketches, sequence diagrams, schema v1
│   ├── NMAP-DEEP-DIVE.md                # Nmap subsystem analysis + Android feasibility mapping
│   ├── NMAP-SUBSYSTEMS-DEEP.md          # ultra_scan algorithms, raw-send path + delegation architecture, probes format, OS matching
│   ├── NMAP-SUBSYSTEMS-DEEP-2.md        # NSE internals & parallelism, host discovery mechanics, scan-phase state machine + Nsock
│   ├── NMAP-SUBSYSTEMS-DEEP-3.md        # idle scan, traceroute, output system & evidence model, target selection + mass-DNS
│   ├── NMAP-SUBSYSTEMS-DEEP-4.md        # service_scan internals, IPv6 fingerprinting, aux tools, capstone inventory
│   ├── CAPABILITY-MATRIX.md             # capability × executor matrix — single source of truth for routing + UI
│   ├── RECOMMENDATIONS.md               # parked ideas (no scope creep in code)
│   └── adr/                             # ADR-0001 license, ADR-0002 UI, ADR-0003 SDK levels, …
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.{jar,properties}
├── gradlew / gradlew.bat
├── core/                                # pure Kotlin/JVM — domain model
│   ├── build.gradle.kts
│   └── src/{main,test}/kotlin/org/nmapremake/core/
│       ├── model/    (ScanPlan, Target, PortSpec, PortResult, HostResult, ScanError, …)
│       ├── capability/ (CapabilityProfile, Capability, Availability, ExecutorNode)
│       └── util/
├── engine/                              # pure Kotlin/JVM — scan mechanics
│   ├── build.gradle.kts
│   └── src/{main,test}/kotlin/org/nmapremake/engine/
│       ├── scheduler/ (ScanScheduler, ConcurrencyPolicy, ProgressEvent)
│       ├── transport/ (TcpTransport, SocketTcpTransport, UdpTransport-iface)
│       ├── probe/    (TcpConnectProber, PortStateClassifier)
│       ├── aggregate/(ResultAggregator)
│       ├── format/   (ResultFormatter, JsonFormatter)
│       └── router/   (ExecutionRouter, ExecutorRegistry)   # Phase 0 skeleton
├── detection/                           # Phase 2+ (module created when M2 starts)
├── capability/                          # Phase 5+ (module created when M5 starts)
├── remote/                              # Phase 7+ (module created when M7 starts)
└── app/                                 # Android application (Compose)
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml      # INTERNET permission only — nothing exotic
        │   ├── kotlin/org/nmapremake/app/
        │   │   ├── MainActivity.kt
        │   │   ├── ui/   (ScanScreen, ResultsList, PortDetail, CapabilityBanner, theme)
        │   │   ├── viewmodel/ (ScanViewModel, ScanUiState)
        │   │   └── di/  (AppGraph — manual wiring)
        │   └── res/…
        ├── test/       (ViewModel unit tests)
        └── androidTest/(emulator tests — CI)
```

Modules `detection`, `capability`, `remote` are **created when their milestone
starts**, not before — no empty placeholder modules (rules #1, #2, #14).

---

## 9. Minimal End-to-End Starter Implementation Outline (Phase 0 + 1)

This is the concrete build order — each step is independently reviewable and testable.

1. **Repo & tooling**: wrapper (Gradle 9.1+), version catalog, AGP 9.0.x, ktlint +
   detekt config, CI workflow, `.gitignore`, license (per approval), README skeleton.
2. **core module**: `Target`, `PortSpec` (single/range/list/`top-N`), `PortState`,
   `PortResult`, `HostResult`, `ScanPlan`, `ScanError`, `CapabilityProfile`,
   `ExecutorNode`; all with kotlinx.serialization annotations + `schemaVersion = 1`.
3. **engine module**: `SocketTcpTransport` (interface + JVM impl),
   `TcpConnectProber` (connect with timeout, measure latency, classify result via
   exception mapping), `ScanScheduler` (coroutine scope + `Semaphore(limit)` +
   `withTimeout`, emits `ProgressEvent` on a `Flow`), `ResultAggregator`,
   `JsonFormatter`, `ExecutionRouter` (single local executor with honest profile).
4. **Tests (JVM, runnable in sandbox & CI)**: parser table-tests; prober tests
   against real loopback listeners (open/refused/timeout); scheduler concurrency-
   cap test; cancellation test; JSON round-trip; aggregator ordering.
5. **app module**: `ScanViewModel` (StateFlow of `ScanUiState`; collect progress;
   cancel on clear), Compose screens (input → capability banner → progress →
   results with per-port state chips and latency), theme, minimal manual DI.
6. **Verification**: `./gradlew build` in sandbox (after installing JDK 17/21 +
   Android SDK platform 36 — no device needed for unit tests + APK assembly);
   GitHub Actions runs the same plus lint; emulator smoke test added to CI;
   on-device LAN scan verified on real hardware by the stakeholder (or emulator
   + documented results), reported honestly in AGENT-EXPERIENCE.md.
7. **Docs**: CHANGELOG entry, AGENT-EXPERIENCE entry, README update; commit
   (conventional commit) to the session branch; **no merge**.

### Sample of the core result model (illustrative, will be written during D2)

```kotlin
@Serializable
enum class PortState { OPEN, CLOSED, TIMEOUT, UNREACHABLE, INCONCLUSIVE }

@Serializable
data class PortResult(
    val port: Int,
    val protocol: TransportProtocol,        // TCP in M1
    val state: PortState,
    val latencyMs: Long?,                   // null when no response
    val error: ScanError?,                  // present when not OPEN
    val evidence: String                    // e.g. "TCP connect completed in 12 ms"
)

@Serializable
data class HostResult(
    val target: Target,
    val portResults: List<PortResult>,
    val executor: ExecutorNode,
    val capabilities: CapabilityProfile,    // what the executor claims it can do
    val startedAt: Long,
    val finishedAt: Long,
    val schemaVersion: Int = 1
)
```

---

## 10. Verification Honesty (rule #21)

**Verified so far (via research, this planning turn):**
- Nmap licensing history (NPSL since 7.90; GPLv2 before) [5] [6] [7] [8].
- Raw sockets on Linux require root/CAP_NET_RAW; Android apps get EPERM; NDK does
  not change this [9] [10] [11].
- VpnService provides a user-consented L3 tun without root; it is a virtual routing
  boundary, not physical-interface injection [12] [13].
- Current toolchain versions and Play target-API requirements as of 2026-09 [1] [2] [3] [4].

**Not yet verified (will be, with the stated method):**
- Exact tun-packet injection behavior for LAN SYN-style scans → on-device
  experiment in Phase 6; **assumed unsupported until proven otherwise**.
- Engine behavior on real Android devices → emulator CI + stakeholder hardware test.
- APK build in this sandbox → pending JDK + SDK install; CI provides independent proof.

**Sandbox constraints:** no JDK/Android SDK/emulator present (2 vCPU, 3.8 GB RAM).
Mitigation: JVM unit tests + `assembleDebug` locally, emulator matrix in CI.

---

## 11. Decisions Recorded (2026-09-11, stakeholder)

| # | Decision | Status | Reference |
|---|---|---|---|
| 1 | First milestone scope | **HOLD** — refine plan/docs further before any application code ("docs-only") | This section |
| 2 | UI framework | **APPROVED: Jetpack Compose + Material 3** | ADR-0002 |
| 3 | License for clean-room code | **APPROVED: GPL-2.0-or-later** | ADR-0001, `LICENSE` |
| 4 | `minSdk` | **APPROVED: API 26 (Android 8.0)** | ADR-0003 |

**Current status:** plan is in iteration. Next refinement areas to be selected by
the stakeholder (see open questions below); no implementation until milestone
approval is given.

### Open questions / next steps
1. ~~Which plan areas should be deepened next~~ → done: acceptance criteria
   tightened (PLAN §5.1–5.2) and architecture/API design added
   (`docs/ARCHITECTURE.md`), per stakeholder direction (2026-09-11).
2. ~~Deep Nmap understanding~~ → done: `docs/NMAP-DEEP-DIVE.md` maps every
   Nmap subsystem to Android feasibility classes A/B/D/U; §4 of that
   document is the Phase 8 compatibility ledger seed.
3. ~~Subsystem deep-dive~~ → done: `docs/NMAP-SUBSYSTEMS-DEEP.md` covers the
   `ultra_scan` algorithms (RTT/congestion/retransmission/scan-delay), the
   raw TCP send path with the stakeholder's capability-delegation design
   (adopted into PLAN §2.3 and M7), the `nmap-service-probes` directive
   format, and the OS-matching algorithms (IPv4 MatchPoints + IPv6
   logistic/novelty); six integrated design deltas recorded in §5 of that
   document.
4. ~~Subsystem deep-dive part 2~~ → done:
   `docs/NMAP-SUBSYSTEMS-DEEP-2.md` covers NSE internals & script
   parallelism (Lua/C++ split, runlevels, coroutine threads, mutexes),
   host discovery mechanics (`-PS/-PA/-PU/-PY/-PE/-PP/-PM` with
   unprivileged fallbacks), and the scan-phase state machine
   (`UltraScanInfo` phases + Nsock event library); deltas folded into
   RECOMMENDATIONS (connect-based host-presence pre-pass, NIO engine
   option, Lua rejected) and ARCHITECTURE (transport-seam = Nsock-engine
   analog).
5. ~~Subsystem deep-dive part 3~~ → done:
   `docs/NMAP-SUBSYSTEMS-DEEP-3.md` covers the idle-scan IP-ID side
   channel (delegation-class D), traceroute (TTL mechanics; TTL-sweep
   experiment added to M6), the output system (`state_reason_t` evidence
   provenance → typed `StateReason` in M2; bitmask logging; LOG_SKID not
   reproduced), and target selection + mass-DNS (richer target grammar
   and parallel resolver recorded as recommendations).
6. ~~Subsystem deep-dive part 4 (final)~~ → done:
   `docs/NMAP-SUBSYSTEMS-DEEP-4.md` covers `service_scan.cc` internals
   (AllProbes/ServiceProbe/ServiceNFO/ServiceGroup → Phase 2 engine
   mapping), IPv6 OS fingerprinting (18 probes; feature-model
   conventions adopted for M4), the auxiliary tools, and the **capstone
   inventory**: all 18 subsystem groups with verified mechanisms and
   A/B/D/U verdicts, which populates the Phase 8 compatibility ledger.
7. ~~Capability matrix~~ → done: `docs/CAPABILITY-MATRIX.md` defines the
   five availability states, the canonical 16 capabilities, 5 executors,
   the full matrix with per-cell verdicts + evidence requirements, the
   router selection rules, UI derivation, and maintenance rules.
   ARCHITECTURE's `Availability` enum gains `NOT_IMPLEMENTED`.
8. Approve (or further refine) the Phase 0+1 milestone criteria (D1–D9, §5.1)
   before any implementation starts.
9. Copyright holder line for the GPL notices (to be set by the project owner).

---

## Sources

[1] JetBrains, "Update your Kotlin projects for AGP 9.0" — https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/
[2] Android Developers, "Android Gradle plugin 9.0.1 release notes" — https://developer.android.com/build/releases/agp-9-0-0-release-notes
[3] Kotlin docs, "Updating multiplatform projects with Android apps to use AGP 9" — https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
[4] Google Play target API level requirements (Android 16 / API 36 deadline 2026-08-31) — https://developer.android.com/google/play/requirements/target-sdk
[5] NixOS/nixpkgs issue #105119, "nmap licensing has changed" — https://github.com/NixOS/nixpkgs/issues/105119
[6] LinuxReviews, "Nmap 7.90 Is Released… re-licenses under NPSL" — https://linuxreviews.org/Nmap_7.90_Is_Released_With_1,200_New_Fingerprints_And_70+_Bugs_Squashed
[7] LWN.net, "A license change for Nmap" — https://lwn.net/Articles/842436/
[8] Fedora's review concluding NPSL is not free/OSD-compatible — https://blog.desdelinux.net/en/nmap-es-incompatible-con-fedora-debido-a-su-licencia/ (secondary; primary: Fedora legal list)
[9] Unix & Linux SE, "IPTables vs AF_PACKET sockets" (CAP_NET_RAW requirement) — https://unix.stackexchange.com/questions/447657/iptables-vs-af_packet-sockes
[10] Stack Overflow, "Raw Sockets on Android" (EPERM, root needed) — https://stackoverflow.com/questions/55862319/operation-not-permitted-when-creating-a-raw-socket-within-a-rooted-android-dev
[11] android-ndk mailing list, "RAW socket using NDK" (EPERM confirmed) — https://groups.google.com/g/android-ndk/c/7FZCKNwun2I
[12] Stack Overflow, "Capture network traffic programmatically (no root)" (loopback VPN approach) — https://stackoverflow.com/questions/38679188/capture-network-traffic-programmatically-no-root
[13] VpnService/TUN explanation and protect() forwarding model — https://topic.alibabacloud.com/a/method-for-implementing-the-android-root-font-classtopic-s-color00c1defreefont-font-classtopic-s-color00c1defirewallfont_1_21_32567522.html
[14] RFC 5737 — IPv4 address blocks reserved for documentation — https://datatracker.ietf.org/doc/html/rfc5737
