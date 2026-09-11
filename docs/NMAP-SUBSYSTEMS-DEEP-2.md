# NMAP SUBSYSTEMS — DEEP ANALYSIS, PART 2

> Status: **Research/analysis (docs-only phase)** — no code.
> Scope: (1) NSE internals & script parallelism, (2) host discovery mechanics,
> (3) the scan-phase state machine (`UltraScanInfo`) and the Nsock event
> library — each followed by what the Android REMAKE takes from it.
> Clean-room rule unchanged: concepts yes, Nmap data/assets no.

---

## 1. NSE Internals & Script Parallelism

### 1.1 Architecture: Lua + C++ split [1][2]
- `open_nse()` creates **one Lua state** that persists across host groups for
  the whole run; loads standard Lua libs (`debug, io, math, os, package,
  string, table`) and **compiled C++ libraries**: `nmap, pcre, db, lpeg,
  debug, zlib, libssh2, openssl`.
- The **NSE core is `nse_main.lua`** — Lua as glue/scheduler; C++ provides
  the network framework (Nsock) and low-level libraries.
- NSE replaces the standard **coroutine** functions so script yields are
  caught and propagated to the NSE scheduler.
- Script selection (`get_chosen_scripts`) compiles the `--script`
  expression into Lua (hence `and/or/not` operators), matches against
  `script.db` categories/filenames/directories, then orders scripts by the
  `dependencies` field into **runlevels** (no deps = level 1, and so on).
  Each runlevel is executed separately and in order.
- `Script` and `Thread` classes: a *script* = loaded file (analogous to an
  executable); a *thread* = a Lua coroutine instantiating it against a
  host/port (analogous to a process).

### 1.2 The scheduler [1]
`script_scan(type ∈ {PRE_SCAN, SCAN, POST_SCAN}, targets)` → generate one
thread per script whose **rule** returns true → `run(threads)` per runlevel.
`run` cycles three queues until both running and waiting are empty:
**running** → (yield) → **waiting** → **pending** → running. A C binding
(`_R[WAITING_TO_RUNNING]`) moves threads between queues; iteration ends
when a thread quits or errors.

### 1.3 Parallelism model [2]
- Parallelism is **cooperative**: a Lua coroutine yields at blocking socket
  operations (in the `nmap` library); the yield/resume is transparent.
- `stdnse.new_thread(main, ...)` creates **worker threads** (coroutines)
  for finer concurrency (e.g., parallel HTTP pipelining in a spider);
  workers may not report script output; coordination via **mutexes**
  (`nmap.mutex`) and **condition variables** (`nmap.condvar`).
- Nmap's event loop is **single-threaded** — no memory synchronization
  issues, only resource contention (bandwidth, sockets); the "base thread"
  wraps each coroutine.

### 1.4 What the REMAKE takes from this
- **Kotlin coroutines are a near-1:1 analog**: script threads ≈ child
  coroutines; yielding socket ops ≈ suspending functions; mutexes/condvars
  exist in `kotlinx.coroutines.sync`; runlevels ≈ explicit dependency
  ordering of probe plugins.
- Reconfirms the earlier decision: build a **typed Kotlin probe pipeline**
  (plugin = `rule(context): Boolean` + `action(context): Result`, with
  dependency ordering and evidence output) rather than embedding Lua —
  no Lua dependency (rule #16) and no risk of running NPSL NSE scripts.
- The "single-threaded event loop" property maps to our structured
  concurrency model: one dispatcher of state + blocking I/O confined to
  `Dispatchers.IO`.

---

## 2. Host Discovery Mechanics (the `-P*` family)

Verified probe semantics [3][4]:

| Probe | Default port(s) | Response that means "up" | Unprivileged fallback |
|---|---|---|---|
| `-PS` TCP SYN ping | 80 | RST (closed) **or** SYN/ACK (open); kernel sends the teardown RST | `connect()`; quick success **or ECONNREFUSED** = up; hang = down |
| `-PA` TCP ACK ping | 80 | RST (no connection exists, so RST is expected) | `connect()` — **imperfect**: kernel sends SYN, not ACK |
| `-PU` UDP ping | 40125 | ICMP port-unreachable = up; other ICMP errors = down/unreachable; silence = down (empty packet to a closed, uncommon port) | none |
| `-PY` SCTP INIT ping | 80 | ABORT (closed) or INIT-ACK (open) | none |
| `-PE/-PP/-PM` ICMP echo/timestamp/netmask | — | matching ICMP reply | none |

Defaults when unspecified: ICMP echo + TCP SYN 443 + TCP ACK 80 + ICMP
timestamp (`-PE -PS443 -PA80 -PP`); on local Ethernet, **ARP** (IPv4) /
**Neighbor Discovery** (IPv6) runs first. Unprivileged default = SYN
connect to 80 and 443. `-PU` payloads come from `nmap-service-probes`
(the same probes as version detection).

Rationale for the SYN/ACK pair: stateless firewalls block SYN to closed
ports (ACK cuts through); stateful firewalls drop unexpected ACKs (SYN
works) — sending both maximizes discovery chances.

### 2.1 What the REMAKE takes from this
- **Class A now**: the unprivileged SYN-ping fallback is exactly a
  `connect()` probe — implementable locally. Proposed as a *recorded
  recommendation* (host-presence pre-pass before port scans), not
  silently added (no scope creep).
- Everything else (ACK, UDP, SCTP, ICMP, ARP/ND) needs raw packets or
  ICMP capture → delegation class D (already in the feasibility map).
- Our conservative-state principle extends naturally: a connect-ping
  "down" verdict is evidence-bound (timeout ≠ proof of absence).

---

## 3. The Scan-Phase State Machine & Nsock

### 3.1 `ultra_scan` control flow [5]
```text
ultra_scan() → UltraScanInfo
   ├─ init()
   ├─ doAnyPings()          # host discovery probes
   ├─ doAnyScans()          # port probes (raw or connect engine)
   ├─ processResults()      # match responses → update port states
   └─ markCompletedHosts()
shared state: HostScanStats per host · GroupScanStats per group ·
UltraProbe per in-flight probe · send_rate_meter · ultra_timing_vals
```
Port states tracked: `PORT_OPEN, PORT_CLOSED, PORT_FILTERED,
PORT_UNFILTERED`. The raw and connect engines (`scan_engine_raw.cc`,
`scan_engine_connect.cc`) implement the same interface: `sendProbes()`,
`processResponses()`, `updateState()` — one with libpcap/libdnet, one
with non-blocking `connect()` + readiness polling.

### 3.2 Nsock — the event library [6]
Nsock is a **parallel socket event library**: the networking engine for
all of Nmap (TCP/UDP/SCTP/SSL/raw). Four components:
- **Pools** (`nsock_pool`) — event managers aggregating events per I/O
  descriptor;
- **IODs** (`nsock_iod`) — socket wrappers holding socket state;
- **Events** (`nsock_event`) — operation requests (connect/read/write);
- **Engines** — platform backends: `epoll`, `kqueue`, `poll`, `select`,
  `iocp`; plus timers (`nsock_timers.c`), pcap integration, and proxy
  support.

### 3.3 What the REMAKE takes from this
Our `ScanScheduler` design already mirrors the `ultra_scan` phase
structure; the mapping is:

| Nmap | REMAKE (M1) | REMAKE (later) |
|---|---|---|
| `UltraScanInfo` + stats | `ScanState` + progress counters | + srtt/rttvar per target (M2) |
| `UltraProbe` in-flight set | coroutine jobs under `Semaphore` | same, with adaptive limits |
| `doAnyScans()` raw/connect engines | `TcpConnectProber` | probe plugins (M2/M4) |
| Nsock pool + engine (epoll…) | `Dispatchers.IO` + blocking sockets | optional `java.nio` Selector engine if profiling demands (recommendation) |
| `processResults()` | classifier + aggregator | same, evidence-bound |

The Nsock "engine pluggability" idea (epoll vs select vs iocp behind one
interface) is a good architectural lesson: our `TcpTransport` seam is the
equivalent abstraction — one interface, multiple realizations (socket /
NIO / fake for tests), no privilege assumption baked in.

---

## 4. Integrated Design Deltas (folded into the doc set)

| # | Delta | Where |
|---|---|---|
| 1 | Kotlin coroutines ≈ NSE threads; typed probe pipeline confirmed (rule/action + dependencies); Lua embedding rejected | RECOMMENDATIONS (reconfirmed), PLAN M4 |
| 2 | Connect-based host presence pre-pass (`-PS`-fallback analog) recorded as M2 recommendation — not implemented in M1 | RECOMMENDATIONS |
| 3 | `TcpTransport` seam documented as the Nsock-engine analog (pluggable realizations) | ARCHITECTURE §3 comment |
| 4 | Phase-structure mapping table (UltraScanInfo → ScanState) as implementation guide | this document §3.3 |

---

## Sources

[1] Nmap book, "Implementation Details" (NSE: open_nse, nse_main.lua,
    runlevels, Script/Thread, running/waiting/pending queues) —
    https://nmap.org/book/nse-implementation.html
[2] Nmap book, "Script Parallelism in NSE" (coroutines, worker threads,
    mutexes, condition variables, base thread) —
    https://nmap.org/book/nse-parallelism.html
[3] Nmap book, "Host Discovery" (probe defaults, firewall rationale) —
    https://nmap.org/book/man-host-discovery.html
[4] Nmap book, "Host Discovery" part 2 (-PS/-PA/-PU/-PY/-PE/-PP/-PM
    semantics, unprivileged connect fallbacks) — same URL, later sections
[5] DeepWiki, "Port Scanning" (ultra_scan → UltraScanInfo phases,
    HostScanStats/GroupScanStats/UltraProbe, connect/raw engines) —
    https://deepwiki.com/nmap/nmap/2.3-port-scanning
[6] DeepWiki, "Nsock Library" (pools, IODs, events, engine backends) —
    https://deepwiki.com/nmap/nmap/4.1-nsock-library
