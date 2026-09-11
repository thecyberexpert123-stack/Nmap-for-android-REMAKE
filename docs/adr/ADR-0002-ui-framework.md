# ADR-0002: UI framework — Jetpack Compose

- **Date:** 2026-09-11
- **Status:** Accepted (stakeholder decision)
- **Deciders:** Project owner (via structured approval), Agent

## Context

The brief (§26) requires a **capability-aware, self-describing UI**: scan options
must not be presented as available when they are not, and results must reflect what
actually ran. The UI is data-driven (live progress, per-port state chips, capability
banners), which favors a declarative framework.

Options considered:

| Option | Pros | Cons |
|---|---|---|
| Jetpack Compose + Material 3 | Declarative, state-driven (fits `StateFlow` progress streams), far less boilerplate, first-class in modern Android (API 26+ baseline is fine) | Younger ecosystem; Compose compiler tooling tied to Kotlin version |
| XML Views + RecyclerView | Mature, predictable | Verbose for dynamic lists/state; more code to maintain for the same UI |

## Decision

Build the UI with **Jetpack Compose + Material 3**. minSdk 26 (ADR-0003) is fully
supported by Compose.

## Consequences

- `app` module depends on Compose BOM, activity-compose, and Material 3.
- UI state flows one-way from `ScanViewModel` (`StateFlow<ScanUiState>`); no
  imperative UI mutation.
- Compose UI tests via CI emulator matrix; previews kept alongside screens.
- Team (agent) must keep Compose compiler/Kotlin alignment in the version catalog.
