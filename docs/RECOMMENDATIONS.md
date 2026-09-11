# RECOMMENDATIONS

Parked ideas and useful-but-unrelated improvements. Per project guideline #19
(no scope creep), these are **recorded, not implemented** — each entry states
when it might become relevant.

| # | Recommendation | Rationale | Revisit at |
|---|---|---|---|
| 1 | TCP probe retry policy (e.g., retry ×1 on `TIMEOUT` before reporting) | Improves reliability on lossy networks; M1 deliberately does single attempts for honest, simple semantics | M2 or first real-device evidence |
| 2 | CIDR target expansion (`192.168.1.0/24`) | Common scanning ergonomics; M1 returns `CIDR_NOT_SUPPORTED_YET` honestly | M2/M3 |
| 3 | Configurable `top-N` port lists with a data file | `top-100` is self-authored; a data-driven list avoids hardcoding | M2 |
| 4 | IPv6 LAN testing beyond loopback | IPv6 parsing is supported in M1, but LAN IPv6 evidence requires a v6 network | Real-device verification (§5.1.7) |
| 5 | Hilt for DI if the manual graph grows beyond ~10 bindings | Keep M1 minimal (guideline #16) | M5 (capability system adds bindings) |
| 6 | Room/DB-backed fingerprint store | Phase 4 fingerprint DB may outgrow a flat file | M4 design gate |
| 7 | Dependency license-check CI plugin (e.g., license scanning of deps) | Enforces the clean-room/NPSL policy as dependencies grow | When first external runtime dep is added |
| 8 | Kotlin Multiplatform evaluation for `core`/`engine` | A JVM remote-executor host could reuse the engine; not needed for M1 | M7 design gate |
| 9 | Foreground service with notification for long scans | Required for background scans by Android 8+; M1 runs in-app | M5 |
| 10 | Export UI (share JSON report, save to storage) | Natural UX for scan reports; not required by M1 criteria | M2 |
