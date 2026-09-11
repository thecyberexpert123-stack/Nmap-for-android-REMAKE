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

### Milestone 1 — Phase 0 + Phase 1 (proposed scope)

| # | Deliverable | Acceptance criteria (testable) |
|---|---|---|
| D1 | Multi-module Gradle project builds clean (`./gradlew build` green) | CI passes; no warnings; reproducible wrapper |
| D2 | `core` domain model (`ScanPlan`, `Target`, `PortSpec`, `PortState`, `PortResult`, `HostResult`, `ScanError`, `CapabilityProfile`, `ExecutorNode`) | Unit tests cover parsing (valid + invalid + edge cases), equality, serialization round-trip |
| D3 | Target/port parser (`192.168.1.10`, `example.com`, `22,80,443`, `1-1000`, CIDR later) | Unit tests: all syntaxes; rejects malformed input with typed `ScanError` |
| D4 | `ScanScheduler` with bounded concurrency, per-probe timeout, cooperative cancellation, progress via `Flow` | Tests: max in-flight ≤ limit (instrumented counter); timeout honored (blocking listener); cancel stops promptly with no leaked coroutines |
| D5 | `TcpConnectProber` with honest classification: `OPEN` / `CLOSED` / `TIMEOUT` / `UNREACHABLE` / `INCONCLUSIVE` + latency + evidence | JVM tests against real loopback sockets (listener = OPEN, refused = CLOSED, unroutable/black-hole = TIMEOUT, no fake states) |
| D6 | `ResultAggregator` + JSON formatter (schema-versioned) | Round-trip tests; JSON validates against defined schema |
| D7 | Compose app: target/ports input, scan, live progress, per-port results, **capability banner** ("TCP Connect — available locally; Service detection — Phase 2; Raw packet — unavailable on stock Android") | Manual + emulator verification; banner is data-driven from `CapabilityProfile` (no hardcoded lies) |
| D8 | CI workflow (build + test + lint on push) | Green run on this branch |
| D9 | Docs: this plan, CHANGELOG, AGENT-EXPERIENCE, README | Present and maintained |

### Later milestones (outline only — planned, not started)

- **M2 (Phase 2)**: HTTP/TLS/SSH/… probes; service identification; basic version
  inference with confidence and evidence. AC: identification only from observed
  responses; false-positive rate measured on a local test-lab of known services.
- **M3 (Phase 3)**: selected UDP probes (DNS, mDNS, …); conservative states; no
  claims about "closed UDP ports".
- **M4 (Phase 4)**: clean-room fingerprint DB (our own probe set and format),
  candidate matching, confidence scoring, evidence storage.
- **M5 (Phase 5)**: device capability detection (interfaces, APIs, restrictions)
  driving the router and UI.
- **M6 (Phase 6)**: on-device experiments: raw sockets w/o root (expect `EPERM`),
  VpnService tun behavior — documented as measured results, not assumptions.
- **M7 (Phase 7)**: authenticated remote executor protocol (mTLS/HTTPS, capability
  negotiation, structured results) — designed with least privilege.
- **M8 (Phase 8)**: feature-by-feature comparison table vs. Nmap, with honest
  labels for delegated/unavailable capabilities.

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
│   ├── RECOMMENDATIONS.md               # parked ideas (no scope creep in code)
│   └── adr/                             # ADR-0001 module boundaries, etc.
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

### Open questions for plan refinement
1. Which plan areas should be deepened next (acceptance criteria per phase,
   architecture detail / API sketches, ethics & safety section, remote-executor
   protocol design, capability matrix, or other)?
2. Are the milestone acceptance criteria (D1–D9) acceptable as written, or do they
   need tightening before Phase 0+1 can be approved?
3. Copyright holder line for the GPL notices (to be set by the project owner).

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
