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
| 11 | Connect-based host-presence pre-pass (Nmap `-PS` unprivileged-fallback analog: `connect()` to 80/443, quick success/ECONNREFUSED = up) | Verified socket-level technique (NMAP-SUBSYSTEMS-DEEP-2 §2); genuinely useful before port scans | M2 |
| 12 | `java.nio` Selector-based transport realization | Nsock-style pluggable engine; only if profiling shows `Dispatchers.IO` thread cost is a bottleneck | M2+ (profile first) |
| 13 | Embedding Lua for a script engine | **Rejected** — heavy dependency + would invite NPSL NSE scripts; typed Kotlin probe pipeline chosen instead (NMAP-SUBSYSTEMS-DEEP-2 §1.4) | Reopen only if stakeholder demands script compatibility |
| 14 | Richer target grammar (octet ranges, input files, excludes, random targets) | Nmap's `NetBlock` grammar (NMAP-SUBSYSTEMS-DEEP-3 §4); M1 grammar is a deliberate subset | M3/M5 (multi-target milestones) |
| 15 | Self-hosted parallel DNS stub resolver (mass-DNS analog) | Platform resolver first; justified only if profiling shows resolution as the bottleneck | M5+ (profile first) |
| 16 | Leetspeak/skid output mode and similar non-purposeful formats | Explicitly **not** reproduced (rule #2 — no junk) | never |
| 17 | Idle scan / full traceroute local emulation | Confirmed delegation-class D; TTL-sweep connect traceroute is an M6 experiment only | M6 (experiment) |
| 18 | FTP bounce scan (`-b`) | Technically socket-level (class A-later) but deprecated, low-value, and abusable against third-party FTP servers — not a milestone | Revisit only on stakeholder request |
| 19 | Result-diff view (ndiff concept over JSON schema v1) | Two JSON v1 reports are diffable by design; a UI diff view adds value after results export exists | M2+ |
