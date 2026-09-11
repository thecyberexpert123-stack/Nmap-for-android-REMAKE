# Nmap-for-android-REMAKE

An Android-native network scanning engine, rebuilt in Kotlin — inspired by Nmap's
capabilities, reproducing them **where the Android platform permits** and honestly
delegating them where it does not.

> Status: **M1 (Phase 0 + Phase 1) implemented and CI-verified** — `core` +
> `engine` (pure JVM) and the Compose `app` are on the session branch with a
> green GitHub Actions pipeline: unit tests, JaCoCo ≥80 % coverage gates on
> core/engine, ktlint + detekt, app lint (warnings-as-errors), and the
> instrumentation suite on emulators at API 26 and API 36. The real-device
> LAN-scan sign-off (PLAN §5.1.7) remains pending stakeholder hardware.
> The development sandbox has no Java/Android toolchain and no
> Maven/Google egress, so CI is the build channel.
> Stack decisions approved: Kotlin + Jetpack Compose, `minSdk 26`, licensed
> **GPL-2.0-or-later**.
> See [`docs/PLAN.md`](docs/PLAN.md) for scope, architecture, roadmap, and
> acceptance criteria; [`docs/NMAP-DEEP-DIVE.md`](docs/NMAP-DEEP-DIVE.md) for
> the subsystem-by-subsystem Nmap analysis and Android feasibility mapping;
> [`docs/NMAP-SUBSYSTEMS-DEEP.md`](docs/NMAP-SUBSYSTEMS-DEEP.md) for the
> engine algorithms, the capability-delegation architecture, and the
> detection/matching formats.
> See [`AGENT-EXPERIENCE.md`](AGENT-EXPERIENCE.md) for the development journal
> and [`CHANGELOG.md`](CHANGELOG.md) for changes.

## Build & verification

Local builds need JDK 17 (the wrapper downloads Gradle 9.1.0 and the
Android SDK components it needs; accept the SDK licenses first).

```bash
./gradlew check ktlintCheck detekt :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # requires a device/emulator
```

`check` includes the JaCoCo ≥80 % line-coverage gates on `:core` and
`:engine` (PLAN §5.1.6). CI (`.github/workflows/ci.yml`) runs all of the
above on every push, plus the instrumentation suite on emulators at API 26
and API 36.

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
