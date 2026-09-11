# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `docs/PLAN.md` — full project plan derived from the task brief
  ("Rebuilding Nmap for Android — From Kotlin Sockets to Raw Packets"):
  scope/requirements, architecture, tech stack, coding standards & quality gates,
  deliverables & acceptance criteria, risk register, phased roadmap (Phase 0–8),
  folder structure, starter implementation outline, and verified research with sources.
- `AGENT-EXPERIENCE.md` — experience journal (started at planning time).
- `README.md` — project status and pointer to the plan.
- Research grounding for the plan:
  - Nmap license history (NPSL since 7.90, GPLv2 earlier) → clean-room policy.
  - Raw sockets on Android require root/CAP_NET_RAW (EPERM otherwise; NDK does not help).
  - VpnService = user-consented L3 tun without root; virtual boundary, not raw NIC access.
  - Toolchain: AGP 9.0.x (built-in Kotlin, KGP 2.2.10), Gradle 9.1+, API 36 target.
- `docs/ARCHITECTURE.md` — module map & dependency rules, domain/engine/app API
  sketches, concurrency model, sequence diagrams (lifecycle, cancellation,
  failure classification, Phase-7 delegation intent), JSON result schema v1,
  error taxonomy, and test-strategy placement matrix.
- PLAN §5 rewritten with tightened, measurable acceptance criteria:
  target/port parsing input–output tables (§5.1.1), TCP classification matrix
  (§5.1.2), scheduler/async invariants with fake-transport determinism rules
  (§5.1.3), formatting checks (§5.1.4), UI checks (§5.1.5), enforced quality
  gates incl. coverage thresholds and CI emulator matrix (§5.1.6), real-device
  sign-off evidence requirement (§5.1.7), and per-milestone criteria for M2–M8
  (§5.2).
- `docs/NMAP-DEEP-DIVE.md` — deep Nmap subsystem analysis from authoritative
  public sources (book chapters, changelog, source-tree structure): scan
  phases, target engine, host discovery, all port-scan techniques with
  privilege requirements, timing/congestion control, version-detection
  algorithm (NULL probe, softmatch, rarity, SSL post-processor, RPC grinder),
  OS detection (IPv4 tests + IPv6 ML classifier), NSE, output, and the data
  files inventory. Includes the kernel-capability boundary table and the
  **Nmap feature → Android feasibility mapping** (classes A/B/D/U) that seeds
  the Phase 8 compatibility ledger, plus clean-room data strategy (IANA
  registries, self-authored signatures).
- `docs/NMAP-SUBSYSTEMS-DEEP.md` — subsystem deep-dive: (1) `ultra_scan`
  algorithms with exact formulas (srtt/rttvar/timeout per RFC 2988 lineage,
  congestion window + response-ratio weighting, timing probes at 1.25 s,
  adaptive retransmission cap 10, scan-delay doubling to 1 s max);
  (2) the raw TCP packet-send path and the stakeholder's **capability-
  delegation architecture** (closest legitimate packet-generation
  capability; encryption ≠ privilege; topology discovery); (3) the
  `nmap-service-probes` directive format (Exclude/Probe/match/softmatch/
  ports/sslports/totalwaitms/tcpwrappedms/rarity/fallback + versioninfo
  helpers); (4) OS-matching algorithms (IPv4 MatchPoints weighting; IPv6
  logistic regression + novelty threshold 15 + 10% ambiguity rule).
- `docs/NMAP-SUBSYSTEMS-DEEP-2.md` — subsystem deep-dive part 2: NSE
  internals & script parallelism (single Lua state, nse_main.lua core,
  Script/Thread classes, dependency runlevels, running/waiting/pending
  queues, coroutine threads, worker threads, mutexes/condvars), host
  discovery mechanics (all `-P*` probes with defaults, response semantics,
  unprivileged `connect()` fallbacks, firewall rationale), and the
  scan-phase state machine (`ultra_scan` → `UltraScanInfo` phases,
  HostScanStats/GroupScanStats/UltraProbe, connect/raw engine split) with
  the Nsock event library (pools/IODs/events/engine backends). Deltas:
  Kotlin coroutines ≈ NSE threads (typed probe pipeline reconfirmed, Lua
  rejected), connect-based host-presence pre-pass recorded for M2,
  `TcpTransport` seam documented as the Nsock-engine analog.
- RECOMMENDATIONS extended: host-presence pre-pass, NIO engine option,
  Lua-embedding rejection rationale.
- `docs/NMAP-SUBSYSTEMS-DEEP-3.md` — subsystem deep-dive part 3: TCP idle
  scan (IP-ID side channel, +1/+2 logic, zombie requirements — class D),
  traceroute (TTL mechanics; TTL-sweep connect variant added to M6
  experiments), the output system (bitmask logging; `state_reason_t`
  provenance model → typed `StateReason` in M2; `LOG_SKID` not
  reproduced), and target selection + mass-DNS (`NetBlock` hierarchy,
  lookahead buffering; richer grammar + parallel resolver recorded as
  recommendations).
- `docs/NMAP-SUBSYSTEMS-DEEP-4.md` — subsystem deep-dive part 4 (final):
  `service_scan.cc` internals (AllProbes/ServiceProbe/ServiceProbeMatch/
  ServiceNFO/ServiceGroup → Phase 2 engine mapping in ARCHITECTURE),
  IPv6 OS fingerprinting (18 probes; feature-model conventions adopted
  for M4), auxiliary tools inventory (ncat/nping/ndiff/zenmap), and the
  **capstone subsystem inventory**: all 18 subsystem groups with
  mechanisms and A/B/D/U verdicts, seeding the Phase 8 compatibility
  ledger. RECOMMENDATIONS gains FTP-bounce deferral and the ndiff-style
  result-diff view.
- `docs/CAPABILITY-MATRIX.md` — canonical capability × executor matrix:
  five availability states (incl. `NOT_IMPLEMENTED`), 16 capabilities,
  5 executors, per-cell verdicts with evidence requirements, router
  selection rules, UI banner derivation, and maintenance rules.
  ARCHITECTURE's `Availability` enum gains `NOT_IMPLEMENTED`.
- `docs/REMOTE-EXECUTOR-PROTOCOL.md` — M7 reference design: threat model
  (T1–T12 with controls, residual risks, accepted risks), trust levels,
  TOFU enrollment flow, versioned signed message set, capability
  negotiation + proofs, least-privilege structured-plan authorization
  (no raw CLI strings), transport options, acceptance-criteria mapping,
  and M7 design-gate open questions. PLAN M7 criteria reference it.
- `docs/UI-UX-CONCEPT.md` — Compose app design: honesty/evidence/
  consent/provenance principles, screen map, screen specs with
  wireframes (ScanInput, authorization dialog, Scanning, Results,
  Executors, CapabilityExplainer), Material 3 state-chip vocabulary,
  capability-banner derivation, accessibility requirements, and the M1
  scope line. PLAN §5.1.5 references it (authorization dialog gating
  every scan; partial-result cancellation labeling).
- PLAN/ARCHITECTURE deltas folded in (previous round): delegation model in
  PLAN §2.3 and M7 criteria (topology discovery, capability proof);
  Phase 2 adopts adaptive RTT timeouts + self-authored signature DB
  grammar; Phase 4 adopts weighted scoring + logistic + novelty on
  application-level features; connect-scan honesty (kernel owns SYN
  retransmission); architectural invariants added to ARCHITECTURE §1 and
  delegation sequence in §4.4.

### Decided (stakeholder approval, 2026-09-11)
- License: **GPL-2.0-or-later** — `LICENSE` added (verbatim GPLv2 text from SPDX
  license-list-data). ADR-0001.
- UI framework: **Jetpack Compose + Material 3**. ADR-0002.
- SDK levels: `compileSdk/targetSdk = 36`, `minSdk = 26`. ADR-0003.
- Milestone: application code **on hold** until plan refinement is complete
  (stakeholder chose docs-only iteration).

### Fixed
- (none yet)

### Changed
- (none yet)

### Removed
- (none yet)
