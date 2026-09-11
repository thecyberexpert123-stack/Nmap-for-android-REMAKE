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
