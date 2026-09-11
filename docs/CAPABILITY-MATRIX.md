# CAPABILITY MATRIX — single source of truth for routing & UI

> Status: **Design (docs-only phase)** — no code.
> This matrix is the operational form of the project's honesty rule: **no
> capability is claimed without a verified basis**. It drives (a) the
> `ExecutionRouter` selection rules, (b) the UI capability banner, and
> (c) the Phase 8 compatibility ledger. Every cell carries its basis
> (which analysis established it) and the evidence required to change it.

---

## 1. Availability states

| State | Meaning | UI treatment |
|---|---|---|
| `SUPPORTED` | Verified available; implemented or implementable now | "Available" (green) |
| `LIMITED` | Available with documented constraints (e.g., app-layer inference only) | "Limited" (amber) + constraint text |
| `NOT_IMPLEMENTED` | The platform *can* do it, but our engine hasn't built it yet | "Planned — milestone X" (neutral) |
| `UNKNOWN` | No verified data; requires an on-device experiment | "Unverified" (grey) — never shown as available |
| `UNSUPPORTED` | Verified impossible on this executor | "Requires capable executor" (red) — with delegation offer |

**Rule:** the only way out of `UNKNOWN` is a recorded experiment (device
model, Android build, raw output) in AGENT-EXPERIENCE.md. `NOT_IMPLEMENTED`
promotes to `SUPPORTED`/`LIMITED` only via passing milestone gates (PLAN §5).

## 2. Capabilities (canonical list)

| # | Capability | Definition |
|---|---|---|
| C1 | `TCP_CONNECT` | TCP connect scan + connect-based probes (`-sT` class) |
| C2 | `UDP_APP_PROBES` | Application-level UDP probes (DNS/mDNS/echo…) with conservative states |
| C3 | `SERVICE_DETECTION` | App-layer service & version identification (self-authored signatures) |
| C4 | `FINGERPRINT_INFERENCE` | Application-level fingerprint inference (labeled, never "Nmap OS detection") |
| C5 | `SCRIPT_PIPELINE` | Typed Kotlin probe pipeline (rule/action; the NSE-concept substitute) |
| C6 | `HOST_DISCOVERY_CONNECT` | Host-presence via connect probes (80/443, unprivileged `-PS`-fallback analog) |
| C7 | `HOST_DISCOVERY_RAW` | ARP/ND, ICMP, UDP, SCTP host discovery |
| C8 | `RAW_PACKET_BUILD` | IP/TCP/UDP serialization with checksums (bytes out — no send) |
| C9 | `RAW_PACKET_TRANSMIT` | Transmit crafted packets (SYN/ACK/FIN/Xmas/Maimon/window/UDP scans) |
| C10 | `PACKET_CAPTURE` | Capture responses/ICMP errors off the wire |
| C11 | `OS_DETECTION_NATIVE` | Stack-fingerprint-based OS detection (IPv4 tests / IPv6 ML) |
| C12 | `TRACEROUTE` | TTL-based route tracing |
| C13 | `IDLE_SCAN` | IP-ID side-channel scan via a zombie |
| C14 | `FTP_BOUNCE` | Scan via FTP PORT (deprecated) |
| C15 | `REMOTE_DELEGATION` | Authenticated delegation of scan intents to capable executors |
| C16 | `RESULT_EXPORT_JSON` | Schema-versioned JSON reports |

## 3. Executors

| Executor | Nature |
|---|---|
| `E1` ANDROID_LOCAL | Our stock-Android app engine (sockets, no root, no VPN) |
| `E2` ANDROID_VPN_TUN | The same app using VpnService tun for packet-level experiments |
| `E3` LAN_AGENT | Authorized LAN device running our own engine/agent software |
| `E4` REMOTE_LINUX_NMAP | Authorized Linux host with genuine Nmap (or equivalent capable tooling) |
| `E5` ANDROID_ROOTED | Rooted device (listed for completeness — **not a project target**) |

## 4. The Matrix

Cell format: `verdict — basis (phase / evidence required)`. `—` = not applicable.

| Capability | E1 ANDROID_LOCAL | E2 ANDROID_VPN_TUN | E3 LAN_AGENT | E4 REMOTE_LINUX_NMAP | E5 ANDROID_ROOTED (not targeted) |
|---|---|---|---|---|---|
| C1 TCP_CONNECT | **SUPPORTED** — M1 deliverable; unprivileged sockets (DEEP §2.1) | SUPPORTED (same) | UNKNOWN → proof | SUPPORTED (any Linux host) | SUPPORTED |
| C2 UDP_APP_PROBES | **NOT_IMPLEMENTED** → M3; UDP sockets unprivileged (DEEP §10) | SUPPORTED (same) | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C3 SERVICE_DETECTION | **NOT_IMPLEMENTED** → M2; app-layer only (DEEP §2.5) | SUPPORTED (same) | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C4 FINGERPRINT_INFERENCE | **NOT_IMPLEMENTED** → M4; app-layer only (DEEP §11) | SUPPORTED (same) | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C5 SCRIPT_PIPELINE | **NOT_IMPLEMENTED** → M4 | SUPPORTED (same) | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C6 HOST_DISCOVERY_CONNECT | **NOT_IMPLEMENTED** → M2 rec; connect fallback verified (DEEP-2 §2) | SUPPORTED (same) | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C7 HOST_DISCOVERY_RAW | **UNSUPPORTED** — ARP/ICMP/raw pings need CAP_NET_RAW (DEEP-2 §2) | **UNKNOWN** — M6 experiment (ARP via tun is the open question) | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C8 RAW_PACKET_BUILD | **SUPPORTED** (pure code; Phase 6 builds it for experiments) — serialization is math (DEEP §14) | SUPPORTED | SUPPORTED | SUPPORTED | SUPPORTED |
| C9 RAW_PACKET_TRANSMIT | **UNSUPPORTED** — EPERM verified (DEEP §2) | **UNKNOWN** — M6 experiment (tun injection vs. virtual boundary) | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C10 PACKET_CAPTURE | **UNSUPPORTED** — root/pcap needed (DEEP §2) | **UNKNOWN** — M6 (tun sees only routed traffic, if anything) | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C11 OS_DETECTION_NATIVE | **UNSUPPORTED** — needs C9+C10 (DEEP §2.6) | UNKNOWN (same chain) | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C12 TRACEROUTE | **UNKNOWN** — TTL setsockopt + ICMP observation unverified (DEEP-3 §2) | UNKNOWN — M6 | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C13 IDLE_SCAN | **UNSUPPORTED** — spoof + capture (DEEP-3 §1) | UNKNOWN | UNKNOWN → proof | SUPPORTED | SUPPORTED |
| C14 FTP_BOUNCE | **NOT_IMPLEMENTED** — deliberately deferred (deprecated, abusable) | — | — | SUPPORTED (nmap `-b`) | — |
| C15 REMOTE_DELEGATION | **NOT_IMPLEMENTED** → M7 (client side) | — | UNKNOWN → proof (server side) | SUPPORTED (server side) | — |
| C16 RESULT_EXPORT_JSON | **SUPPORTED** — M1 deliverable | SUPPORTED | SUPPORTED | SUPPORTED | SUPPORTED |

**Honesty notes**
- `E2` (VPN tun) is almost entirely `UNKNOWN` **by design**: the brief and
  the stakeholder essay both insist VPN ≠ raw access; only the M6 device
  experiments may promote any cell, and only with recorded evidence.
- `E3/E4` cells are `UNKNOWN` until enrollment + capability proof —
  a remote executor's *claims* are never trusted without a verification
  path (M7 threat model).
- `E5` (rooted) is listed only so the table is complete; the project
  targets stock Android and does not depend on root.

## 5. Routing Rules (ExecutionRouter)

```text
route(ScanPlan):
  for each required capability C in plan:
    candidates = executors where availability(C) ∈ {SUPPORTED, LIMITED}
    if none → fail ScanError(CAPABILITY_UNSUPPORTED, C)   # never fake
  select executor minimizing (reachability distance, latency, trust cost)
    with LOCAL executors preferred at equal cost
  return ExecutorSelection(executor, capabilitiesProofRequired = remote?)
```

- `NOT_IMPLEMENTED` locally + `SUPPORTED` remotely ⇒ **delegation is
  legitimate** (the user asked for a capability; we honestly route it).
- `UNKNOWN` locally + `SUPPORTED` remotely ⇒ delegate and label the local
  cell "unverified" — no local claim is ever made from an unknown cell.
- Results always carry executor identity + capability profile (already in
  the schema, ARCHITECTURE §5).

## 6. UI Derivation

- The capability banner (§5.1.5 UI checks) is computed **from this matrix**
  via a pure mapping function; rows show: capability → state → basis text
  ("Available · Phase 1" / "Unverified — requires device experiment" /
  "Requires capable executor").
- Unavailable scan options are disabled with the reason, never silently
  hidden (the brief's self-describing UI requirement, §26).

## 7. Maintenance Rules

- Matrix changes land only via: a milestone gate passing (promote
  NOT_IMPLEMENTED), or an M6-style recorded experiment (change UNKNOWN),
  or a verified platform finding (new UNSUPPORTED/SUPPORTED).
- Every change must update: this file, the affected `CapabilityProfile`
  defaults in code (when code exists), the CHANGELOG, and the Phase 8
  ledger row.

---

Basis keys: DEEP = `docs/NMAP-DEEP-DIVE.md` · DEEP-2 = `docs/NMAP-SUBSYSTEMS-DEEP-2.md` ·
DEEP-3 = `docs/NMAP-SUBSYSTEMS-DEEP-3.md` · DEEP-4 = `docs/NMAP-SUBSYSTEMS-DEEP-4.md`
(section numbers in cells refer to those documents).
