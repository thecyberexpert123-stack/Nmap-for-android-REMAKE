# Nmap-for-android-REMAKE

An Android-native network scanning engine, rebuilt in Kotlin — inspired by Nmap's
capabilities, reproducing them **where the Android platform permits** and honestly
delegating them where it does not.

> Status: **Planning / plan iteration** — no application code yet. Stack decisions
> approved: Kotlin + Jetpack Compose, `minSdk 26`, licensed **GPL-2.0-or-later**.
> See [`docs/PLAN.md`](docs/PLAN.md) for scope, architecture, roadmap, and
> acceptance criteria. See [`AGENT-EXPERIENCE.md`](AGENT-EXPERIENCE.md) for the
> development journal and [`CHANGELOG.md`](CHANGELOG.md) for changes.

## What this project is (and is not)

- **Is**: a capability-aware Android scanner that performs real network operations
  and reports its capabilities honestly.
- **Is not**: a port of Nmap's source code, a "fake Nmap", or a tool that fabricates
  scan results. Where stock Android cannot perform an operation (e.g., raw packet
  transmission without root), the scanner says so.

## Roadmap (summary)

| Phase | Content |
|---|---|
| 0 | Architecture: ScanPlan, CapabilityProfile, ExecutorNode, ScanResult, router |
| 1 | TCP connect engine: parsing, scheduling, probing, timeouts, concurrency, results |
| 2 | Service detection (application-level probes) |
| 3 | UDP application probing with conservative states |
| 4 | Fingerprinting: features, clean-room DB, matching, confidence |
| 5 | Android capability system |
| 6 | Native boundary (only where a capability requires it) |
| 7 | Remote executor (capability delegation) |
| 8 | Feature-by-feature compatibility measurement vs. Nmap |

First milestone (in review): **Phase 0 + Phase 1** — TCP connect scanning with a
minimal Compose UI, full test and CI setup.

## License

Original code in this repository is licensed under the **GNU General Public
License, version 2 or (at your option) any later version** (`GPL-2.0-or-later`).
See [`LICENSE`](LICENSE). Copyright holder: TBD (set by the project owner before
first release). This project copies no Nmap source code or data files; see
[`docs/adr/ADR-0001-license.md`](docs/adr/ADR-0001-license.md) for the clean-room
policy and NPSL context.
