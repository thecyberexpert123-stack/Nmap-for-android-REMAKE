# AGENT-EXPERIENCE

Dated journal of the development agent's experience: decisions made, challenges hit,
things learned, things that failed, and open questions. Written **during** the work,
per project guideline #4. Entries are chronological.

---

## 2026-09-11 — Planning phase

### Context
Received the full task brief ("Rebuilding Nmap for Android — From Kotlin Sockets to
Raw Packets") with 23 project guidelines. Repository is empty except a one-line
README; branch `arena/01a08f45-nmap-for-android-remake`; rule #23: never merge.

### What I did
1. Inspected the repository first (guideline #12): empty project, single commit,
   clean tree. Confirmed nothing exists that could be overwritten.
2. Ran structured research (guideline #3) before any code:
   - Nmap licensing: verified NPSL since 7.90 (Oct 2020), GPLv2 earlier, Fedora
     rejected NPSL. Consequence: strict clean-room policy; we cannot copy Nmap
     source or data files.
   - Android raw sockets: verified EPERM without root/CAP_NET_RAW; NDK changes
     nothing. Confirms the brief's "packet construction ≠ packet transmission".
   - VpnService: verified it is a user-consented L3 tun without root, and that it
     is a virtual boundary, not raw NIC access. On-device behavior for crafted
     packets remains **unverified** — will be measured experimentally in Phase 6.
   - Toolchain: AGP 9.0.x with built-in Kotlin (KGP 2.2.10), Gradle 9.1+, API 36
     target required for new Play submissions since 2026-08-31.
   - Sandbox audit: no JDK, no Gradle, no Android SDK, no emulator (2 vCPU,
     3.8 GB RAM). Verification plan must be honest about this (guideline #21).
3. Wrote `docs/PLAN.md` covering every element guideline #10 requires, then this
   journal and `CHANGELOG.md`.

### Decisions
- First milestone = **Phase 0 + Phase 1 only** ("boring first version"), exactly as
  the brief itself recommends. Everything else is planned, not started — no scope
  creep (guideline #19).
- Engine and core are **pure Kotlin/JVM modules** so they can be tested in this
  sandbox and CI without an Android device. The Android module is a thin Compose
  shell + lifecycle wiring.
- Manual DI for the first milestone; dependencies are added only when justified
  (guideline #16).
- Capability model defaults to `UNKNOWN` until experimentally verified — we never
  claim a capability we have not demonstrated.

### Challenges / open questions
- Whether tun-injected crafted packets can meaningfully probe arbitrary LAN hosts
  is unresolved and cannot be resolved without a device. It stays open, honestly.
- License choice for our clean-room code is a stakeholder decision (options
  presented in PLAN.md §11).

### Learning
- The brief's central insight — build the packet vs. transmit the packet, and
  delegate capability vs. acquire privilege — maps cleanly onto a concrete
  executor/router architecture; the research validated every boundary the brief
  predicted.

---

## 2026-09-11 (later) — Stakeholder decisions recorded

### Context
Presented the plan and four approval questions (guideline #9). Stakeholder answers:
1. Milestone: **docs-only** — refine the plan further before any application code.
2. UI: **Jetpack Compose**.
3. License: **GPL-2.0-or-later**.
4. minSdk: **API 26**.

### What I did
1. Applied the three stack decisions across `docs/PLAN.md` (§3, §3.1, §11).
2. Added `LICENSE` — verbatim GPLv2 text fetched from the official SPDX
   license-list-data mirror (gnu.org direct download was blocked in the sandbox;
   the SPDX copy is the canonical text — sections 0–12 all verified present).
   Copyright-holder line deliberately left unset for the project owner.
3. Created three ADRs: `docs/adr/ADR-0001-license.md`,
   `ADR-0002-ui-framework.md`, `ADR-0003-sdk-levels.md`.
4. Updated `CHANGELOG.md` and this journal. Committed and pushed to the session
   branch (no merge).

### Decisions
- Plan status is now **PLAN ITERATION (docs-only)**; no implementation starts
  without explicit milestone approval.

### Challenges / open questions
- Which areas of the plan to deepen next is the stakeholder's call — options
  offered: acceptance criteria per phase, architecture/API sketches, ethics &
  safety section, remote-executor protocol design, capability matrix.
- Copyright holder for the GPL notice remains unset (project owner decision).

### Learning
- Rule #20 (human authority) applied concretely: a license is an irreversible,
  materially consequential decision, so it went through structured approval
  instead of being assumed.
- Sandbox networking is filtered for some hosts (gnu.org, raw.githubusercontent.com
  blocked for curl; api.github.com worked). Verbatim licensing text therefore came
  from SPDX via the GitHub API — provenance recorded, no transcription.

---

## 2026-09-11 (final part) — Plan refinement: acceptance criteria + architecture

### Context
Stakeholder directed: tighten acceptance criteria & test strategy, and deepen
architecture & API design. Still docs-only (no application code).

### What I did
1. Rewrote PLAN §5 into measurable criteria: input/output tables for target and
   port parsing (incl. explicit `ScanError` codes), a TCP observation →
   state/evidence classification matrix, deterministic scheduler invariants
   using fake transports (so tests never depend on the sandbox network), JSON
   schema checks, UI checks, enforced quality gates (≥80 % coverage, 0 lint
   issues, CI emulator matrix on API 26 + 36), a real-device sign-off evidence
   requirement, and preliminary per-milestone criteria for M2–M8.
2. Authored `docs/ARCHITECTURE.md`: module map with one-way dependency rules,
   Kotlin API sketches for `core`/`engine`/`app`, the coroutine concurrency
   model, four sequence diagrams (lifecycle, cancellation, failure
   classification, Phase-7 delegation intent), JSON result schema v1 with
   forward-compatible wire error codes, error taxonomy, and a test-strategy
   placement matrix.
3. Updated CHANGELOG, PLAN §8 (folder tree) and §11 (open questions), added
   RFC 5737 source. Committed and pushed (no merge).

### Decisions
- Wire-format `ScanError.code` is a stable string enum while the internal enum
  may grow — forward-compatible decoding, unknown codes decode to `INTERNAL`
  with the raw value preserved.
- Tests never depend on external networks: real sockets only on loopback;
  TIMEOUT/UNREACHABLE paths are exercised via scripted fakes.
- `PortSpec` parsing is atomic — one bad token rejects the whole spec (no
  partial silent behavior).
- Default limits locked in the plan: probe timeout 5 s (clamped 0.1–60 s),
  concurrency 32 (clamped 1–256), scan watchdog 10 min for UI-issued scans,
  progress channel capacity 64.

### Challenges / open questions
- Whether the 5 s default timeout fits typical LAN scanning is a hypothesis
  until the first real-device run (§5.1.7) — the value is configurable, and the
  real-device evidence will confirm or adjust it.
- Emulator CI matrix (API 26 + 36) requires GitHub-hosted runners with KVM; if
  the repo's plan lacks them, the matrix is documented as a gap and reduced to
  API 36 — decided when CI is actually set up (honesty rule #21).

### Learning
- Turning "acceptance criteria" into input/output tables and invariant lists
  forced concrete decisions (defaults, error codes, evidence formats) that
  would otherwise have been made ad hoc during coding — the tables are now the
  test specification, which is exactly what rule #1 (complete, tested,
  purposeful) demands.

---

## 2026-09-11 (deep-dive) — Understanding Nmap for the REMAKE

### Context
Stakeholder task (still docs-only): "Understanding and pinpointing the whole
Nmap deeply, so that we can create our REMAKE Nmap on Android."

### What I did
1. Researched Nmap from authoritative sources: book chapters (port scanning
   techniques, host discovery, version detection technique, OS detection,
   NSE, timing/performance), the changelog/license history, and the
   `nmap/nmap` source tree structure on GitHub.
2. Wrote `docs/NMAP-DEEP-DIVE.md`: the 11 scan phases; subsystem-by-subsystem
   breakdown with the underlying mechanism of each; the kernel-facility
   boundary table; the **feature → Android feasibility mapping** with classes
   A (Kotlin now) / B (platform experiment) / D (delegation) / U (unverified);
   a clean-room data-file strategy; and six architecture adjustments the
   analysis implies.
3. Updated PLAN (§8, §11), README, CHANGELOG. Committed and pushed (no merge).

### Decisions
- Phase 2 will adopt Nmap's NULL-probe + softmatch + rarity *concepts* with a
  self-authored signature DB — the intellectual structure, not the assets.
- NSE-equivalent will be a Kotlin typed probe pipeline (rule + action +
  evidence), not an embedded Lua interpreter: avoids a heavy dependency and
  removes any temptation to run NPSL-licensed NSE scripts.
- Port→service names will be generated from the IANA registry (public data).
- Traceroute-via-TTL (`Os.setsockoptInt`) and VpnService observation limits
  added to the M6 experiment matrix as class-U items.
- Nmap data files are NPSL — confirmed again from the repo listing that they
  are shipped files; nothing is copied.

### Challenges / open questions
- The exact behavior of the Android tun interface for observing ICMP errors
  or ARP remains genuinely unknown until a device experiment — the doc keeps
  it class U instead of guessing.
- Whether the remote executor (M7) wraps the real nmap binary or our own
  engine re-opens the NPSL discussion — flagged for a stakeholder decision at
  that milestone, not decided now.

### Learning
- Pinpointing *mechanism* (what kernel facility each Nmap feature actually
  needs) is what makes the feasibility mapping trustworthy: every "can't do
  this on stock Android" claim in the doc is tied to a specific verified
  privilege boundary, not to vague platform pessimism.

---

## 2026-09-11 (subsystem deep-dive) — ultra_scan, raw send path, probes format, OS matching

### Context
Stakeholder supplied a design essay for TCP packet sending (capability
delegation: Android as controller; the packet is constructed wherever a
legitimate capability exists — LAN agent, appliance, VPN endpoint, cloud
Linux) and asked to go deeper on specific Nmap subsystems. Still docs-only.

### What I did
1. Fetched the exact algorithm chapters from the official Nmap book: "Scan
   Code and Algorithms" (both chunks), "nmap-service-probes File Format"
   (both chunks), "OS Matching Algorithms" (full).
2. Wrote `docs/NMAP-SUBSYSTEMS-DEEP.md` with: the ultra_scan stateful design
   and exact timing formulas; congestion control with the responses-ratio
   weighting adaptation; timing probes; adaptive retransmission; scan delay;
   the raw send path (build ≠ send) and the integrated delegation design;
   the full service-probes directive grammar with helper functions; IPv4
   MatchPoints scoring and IPv6 logistic regression with novelty/ambiguity
   thresholds.
3. Folded six design deltas into PLAN (§2.3, §5.2 M2/M4/M7, §8, §11) and
   ARCHITECTURE (§1 invariants, ExecutorNode fields, §4.4 sequence).
4. Updated README, CHANGELOG, this journal. Committed and pushed (no merge).

### Decisions
- Delegation architecture adopted as stated: router selects the *closest*
  capable executor; results carry executor identity + capability proof.
- Phase 2 will implement srtt/rttvar/timeout (timeout = srtt + 4·rttvar) as
  the engine's adaptive default; M1 keeps the simpler fixed timeout.
- Connect-scan honesty: our evidence model must not imply retransmission
  control — the kernel owns SYN retries for connect scans.
- Phase 4 reuses weighted scoring + logistic mapping + novelty/ambiguity
  rejection on application-level features only.

### Challenges / open questions
- `java.util.regex` vs PCRE divergences need an explicit documented list +
  tests when the self-authored signature DB arrives (M2).
- Whether tun-injected crafted probes can be sent at all remains class U —
  only a Phase 6 device experiment can answer it.

### Learning
- The stakeholder essay's central move — asking "where is the closest
  legitimate packet-generation capability?" — turned the Android
  limitation from a dead end into a routing problem. It maps 1:1 onto the
  executor/router architecture already planned, which validated the
  intent/execution separation chosen in Phase 0.

---

## 2026-09-11 (subsystem deep-dive, part 2) — NSE, host discovery, scan-phase state machine

### Context
Stakeholder chose to continue deepening Nmap subsystems before any code.

### What I did
1. Fetched: the official book's "Implementation Details" (NSE) and
   "Script Parallelism in NSE" chapters, the remainder of "Host
   Discovery" (all `-P*` probe semantics), plus DeepWiki source-level
   analyses of Port Scanning (UltraScanInfo phases) and the Nsock library.
2. Wrote `docs/NMAP-SUBSYSTEMS-DEEP-2.md`: NSE architecture (Lua/C++
   split, single persistent Lua state, runlevels, queue scheduler),
   parallelism model (coroutine threads, worker threads, mutexes,
   condvars, single-threaded loop), host discovery mechanics table with
   unprivileged fallbacks, and the ultra_scan state machine with the
   Nsock event library.
3. Folded deltas: RECOMMENDATIONS gained the connect-based host-presence
   pre-pass (M2), the NIO-engine option, and the Lua-rejection rationale;
   ARCHITECTURE documents `TcpTransport` as the Nsock-engine analog.
4. Updated PLAN (§8 tree, §11), CHANGELOG, this journal. Committed and
   pushed (no merge). Fixed a CHANGELOG bullet that my edit had
   overwritten (caught during self-review — rule #22).

### Decisions
- Kotlin coroutines are confirmed as the NSE-thread analog: structured
  concurrency for probe jobs; `Mutex`/condvars from kotlinx when needed.
- Host discovery on Android starts as the verified unprivileged connect
  fallback (SYN via connect to 80/443); ARP/ICMP/UDP/SCTP pings remain
  delegation-class.
- The `TcpTransport` seam (socket / NIO / fake) mirrors Nsock's engine
  pluggability — one contract, no privilege assumptions baked in.

### Challenges / open questions
- Whether to expose an optional "host discovery" pre-pass in the UI
  before port scans is a UX decision for the stakeholder at M2.
- DeepWiki is a secondary, AI-generated source: it was cross-checked
  against file names verified directly on GitHub's nmap/nmap tree; any
  claim sourced only to DeepWiki is labeled as such in the doc.

### Learning
- Source-level indexes (DeepWiki) and the official book complement each
  other: the book gives *why* and the exact algorithms; the index gives
  *where* (file/function mapping). Using one without the other would
  have produced either mechanism without location or location without
  mechanism.

---

## 2026-09-11 (subsystem deep-dive, part 3) — idle scan, traceroute, output system, target selection

### Context
Stakeholder again chose to continue subsystem research before any code.

### What I did
1. Fetched the book's idle-scan chapter, DeepWiki's target-selection and
   output-system pages, and cross-referenced the traceroute file roles
   already verified on the GitHub tree.
2. Wrote `docs/NMAP-SUBSYSTEMS-DEEP-3.md`: idle-scan step mechanics with
   the IP-ID +1/+2 logic and zombie requirements; traceroute TTL
   mechanics with an honest split between verified file roles and
   standard protocol knowledge; the output system's evidence model
   (`state_reason_t` = reason + source IP + TTL) and bitmask logging;
   the NetBlock target grammar and mass-DNS flow.
3. Folded deltas: typed `StateReason` provenance into M2 criteria;
   TTL-sweep traceroute variant into the M6 experiment matrix; richer
   target grammar, parallel DNS resolver, and non-reproduction of
   leetspeak output recorded in RECOMMENDATIONS.
4. Updated PLAN (§5.2 M2/M6, §8, §11), CHANGELOG, this journal.
   Committed and pushed (no merge).

### Decisions
- Evidence provenance becomes structured in Phase 2 (reason code +
  source + TTL), mirroring `state_reason_t` — evidence must be
  queryable, not just printable.
- Idle scan is the purest delegation case: mechanism impossible locally,
  result model fully renderable from a remote executor.
- `LOG_SKID` and similar modes are deliberately excluded (rule #2).

### Challenges / open questions
- The M6 traceroute experiment now has two variants (VPN observation and
  TTL-sweep); both need real hardware, keeping M6 dependent on device
  access.
- Whether `Os.setsockoptInt(IPPROTO_IP, IP_TTL, …)` works unprivileged on
  Android remains class U until measured.

### Learning
- Nmap's `state_reason_t` is a small but high-value design: recording
  *why* a state was assigned (with the packet that proved it) is what
  makes output auditable. Our evidence strings were a start; the typed
  reason model is the generalization.

---

## 2026-09-11 (subsystem deep-dive, part 4 — final) — service_scan, IPv6 fingerprinting, aux tools, capstone

### Context
Stakeholder again chose to continue subsystem research before any code.

### What I did
1. Fetched the service-detection internals index and the book's IPv6
   fingerprinting chapter; cross-referenced auxiliary tool roles against
   the GitHub tree already verified.
2. Wrote `docs/NMAP-SUBSYSTEMS-DEEP-4.md`: the `ServiceNFO` per-port
   state machine and object graph; all 18 IPv6 OS probes with their
   quirks (invalid extension headers, RFC 4620 NI, NS hop-limit 255) and
   the feature-model conventions (missing = −1, scaling to [0,1]); the
   auxiliary tools inventory; and the **capstone table** — all 18
   subsystem groups with mechanisms and final A/B/D/U verdicts.
3. Folded deltas: Phase 2 object mapping into ARCHITECTURE; M4
   feature-model conventions into PLAN; FTP-bounce deferral and
   ndiff-style diff view into RECOMMENDATIONS.
4. Updated PLAN (§5.2 M4, §8, §11), CHANGELOG, this journal. Committed
   and pushed (no merge).

### Decisions
- The Nmap analysis series is now closed: every subsystem has a
  verified mechanism and a verdict; the Phase 8 ledger can be
  populated mechanically from the capstone table.
- FTP bounce is deliberately not a milestone despite being
  socket-level: deprecated, low-value, and abusable against
  third-party FTP servers (rule #2/#15).

### Challenges / open questions
- The Phase 8 ledger will still need per-feature measurement data at
  that milestone; the capstone table is the *structure*, not the
  evidence.
- With analysis closed, the remaining gate is stakeholder approval of
  the Phase 0+1 milestone criteria.

### Learning
- Closing the analysis with an explicit inventory table (not prose)
  makes the whole series auditable: each verdict can be traced back
  to the specific source that established the mechanism, which is the
  practical form of rule #11 (never fabricate).

---

## 2026-09-11 (gap fill) — the capability matrix

### Context
Stakeholder asked to fill the capability-matrix gap: one canonical table
driving both the router and the UI.

### What I did
1. Authored `docs/CAPABILITY-MATRIX.md`: five availability states with
   promotion rules; a canonical list of 16 capabilities (each mapped to
   its analysis source); 5 executors including the VPN-tun and rooted
   rows; the full matrix where every cell carries verdict + basis +
   evidence requirement; router selection rules; UI banner derivation;
   and maintenance rules (matrix changes only via milestone gates or
   recorded experiments).
2. Added `NOT_IMPLEMENTED` to the `Availability` enum in ARCHITECTURE —
   the distinction between "platform can't" and "we haven't built it
   yet" is now explicit in the model.
3. Updated PLAN (§2.3 pointer, §8 tree, §11), CHANGELOG, this journal.
   Committed and pushed (no merge).

### Decisions
- The VPN-tun executor row is deliberately almost entirely UNKNOWN: the
  matrix encodes the brief's "VPN ≠ raw access" as a data property, not
  a slogan. Only M6 experiments may promote any of its cells.
- Remote executors default to UNKNOWN until enrollment + capability
  proof — claimed capabilities are never trusted.
- A rooted-device row exists for completeness but is explicitly not a
  project target.

### Challenges / open questions
- Whether `NOT_IMPLEMENTED`-locally + `SUPPORTED`-remotely should
  auto-delegate or ask the user first is a UX decision — recorded for
  the M7 design gate.
- The matrix is a living artifact; its maintenance rules must be
  enforced by the CI/PR checklist once code exists.

### Learning
- Expressing the honesty principle as *data with promotion rules*
  (UNKNOWN can only change via recorded experiments) turns a
  philosophical commitment into an enforceable workflow — which is
  exactly how rule #21 (verification honesty) should be operationalized.

---

## 2026-09-11 (gap fill) — remote-executor protocol design (M7)

### Context
Stakeholder asked for the remote-executor protocol deep design.

### What I did
1. Authored `docs/REMOTE-EXECUTOR-PROTOCOL.md`: goals/non-goals; a
   12-threat model (T1–T12) with controls, residual risks, and
   explicitly accepted risks; three-level trust model; TOFU enrollment
   flow with pinning and short-lived tokens; a versioned, signed
   message set (hello/profile/plan/progress/result/cancel/revoke/proof);
   capability negotiation + freshness-windowed capability proofs;
   least-privilege authorization where only structured ScanPlans reach
   the executor (never raw CLI strings); transport options; and an
   acceptance-criteria mapping that tightens PLAN M7.
2. Updated PLAN (M7 criteria reference the design; tree; open
   questions), CHANGELOG, this journal. Committed and pushed (no merge).

### Decisions
- Signing proves *authorship*, not truthfulness — a trusted-but-rogue
  executor can still fabricate results; the UI must say so. This is
  recorded as an accepted, documented risk (T6).
- The executor-side nmap-wrapper builds arguments from a fixed template
  over the structured plan — injection resistance by construction.
- 2-node trust (no cross-executor PKI) keeps revocation simple at M7
  scope.

### Challenges / open questions
- Wrapping genuine nmap on the reference executor re-opens the NPSL
  distribution question — parked as an explicit M7 design-gate item
  for the stakeholder.
- All security claims in the design are requirements to be verified at
  implementation time; the doc says so explicitly (rule #21).

### Learning
- A threat model written *before* the protocol forced several decisions
  early (structured plans instead of CLI strings, signed results,
  proof freshness) that would have been much harder to retrofit after
  the first working delegation — prevention by ordering.

---

## 2026-09-11 (gap fill) — UI/UX concept

### Context
Stakeholder asked for the UI/UX concept before Compose implementation.

### What I did
1. Authored `docs/UI-UX-CONCEPT.md`: five design principles (honesty,
   evidence-visible, self-describing, consent-before-traffic,
   provenance-labeled, minimal-M1); the screen map (M1 = two screens +
   two dialogs); screen-by-screen specs with ASCII wireframes; an
   authorization dialog that gates every scan behind a scope summary
   and ownership acknowledgment; the Material 3 state-chip vocabulary
   incl. M3 UDP states and M7 trust badges; banner derivation from the
   capability matrix; accessibility requirements; and the M1 scope line.
2. Tightened PLAN §5.1.5: the consent dialog is now an acceptance
   criterion, and cancelled scans must label partial results
   "incomplete — cancelled by user".
3. Updated PLAN (tree, open questions), CHANGELOG, this journal.
   Committed and pushed (no merge).

### Decisions
- Unavailable scan options stay visible but disabled with reasons —
   they are never hidden (the brief's self-describing UI requirement).
- The authorization dialog is not a legal shield; it's an informed-
   consent affordance. The real guardrails are technical (scope limits)
   — the UI makes them explicit.
- M1 deliberately excludes the export sheet unless the copy-JSON
   affordance stays trivial (no scope creep).

### Challenges / open questions
- Whether the authorization checkbox wording should be reviewed by a
  legal professional is flagged for the stakeholder (the wording is
  engineering-appropriate, not legal advice).
- Live-streaming results while scrolling (LazyColumn + diffs) is a
  known Compose performance area; the buffered-channel design (§3) is
  the mitigation, to be validated by the emulator test.

### Learning
- Designing the UI *before* the ViewModel made the data contract
  concrete: every screen element traces to a state field or a matrix
  row, which means the Compose layer will have no invented states —
  the front-end equivalent of the honesty rule.
