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
