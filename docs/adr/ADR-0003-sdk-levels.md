# ADR-0003: SDK levels — compile/target 36, minSdk 26

- **Date:** 2026-09-11
- **Status:** Accepted (stakeholder decision)
- **Deciders:** Project owner (via structured approval), Agent

## Context

Verified research (PLAN.md sources [1]–[4]): Google Play requires new apps and
updates to target **Android 16 (API level 36)** from 2026-08-31; AGP 9.0.x is the
current stable line and supports compileSdk 36. Minimum SDK determines device reach
vs. modern-API availability.

Options considered:

| minSdk | Reach | Cost |
|---|---|---|
| 26 (Android 8.0) | ≈ all active devices | Minimal; modern APIs available (foreground services, notification channels) |
| 29 (Android 10) | Drops older devices | Slightly stricter privacy baseline |
| 24 (Android 7.0) | Marginal extra reach | Compatibility shims |

## Decision

- `compileSdk = 36`, `targetSdk = 36`
- `minSdk = 26`

## Consequences

- No legacy support libraries needed; Compose works without extra configuration.
- Scans run in foreground contexts per Android 8+ background limits (foreground
  service with notification channel from Phase 5+; Phase 1 runs in-app).
- New Play submissions remain compliant with the API 36 requirement.
