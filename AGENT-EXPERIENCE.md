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
