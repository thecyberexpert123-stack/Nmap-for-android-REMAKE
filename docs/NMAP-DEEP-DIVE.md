# NMAP DEEP-DIVE — Understanding Nmap for the Android REMAKE

> Status: **Research/analysis document (docs-only phase)** — no code.
> Purpose: pinpoint how Nmap actually works, subsystem by subsystem, so the
> REMAKE plan maps **exactly** which pieces Kotlin-on-Android can rebuild
> locally, which need platform facilities, and which must be delegated.
>
> **Clean-room note (rule #11/#30):** this analysis is derived from Nmap's
> public book, reference guide, changelog, and published source-code
> structure (file names, module roles). We copy **nothing** — no Nmap source,
> no `nmap-service-probes`/`nmap-os-db` data. All data we later ship is
> self-authored or from unencumbered public registries (e.g., IANA).

---

## 1. What Nmap Is, Architecturally

Nmap is not one scanner; it is a **pipeline of cooperating subsystems** driven
by an event-based scan engine. A full scan runs through ordered phases [1][2]:

```text
1. Script pre-scanning (NSE)     7. OS detection (-O)
2. Target enumeration            8. Traceroute
3. Host discovery (ping scan)    9. Script scanning (NSE)
4. Reverse-DNS resolution       10. Output
5. Port scanning                11. Script post-scanning (NSE)
6. Version detection (-sV)
```

Source tree roles (verified from `nmap/nmap` on GitHub) [3]:

| Area | Files | Role |
|---|---|---|
| Core engine | `nmap.cc`, `scan_engine.cc`, `scan_engine_raw.cc`, `scan_engine_connect.cc`, `nsock/` | event-driven probe engine; raw vs connect execution |
| Packet layer | `tcpip.cc`, `struct_ip.h`, `libdnet-stripped/`, `libpcap/` | packet construction + capture (the "raw meat") |
| Targets | `targets.cc`, `TargetGroup.cc`, `nmap_dns.cc` | parsing, grouping, custom DNS resolver |
| Ports/services | `portlist.cc`, `services.cc`, `protocols.cc` | port state machine, service/port DB access |
| Timing | `timing.cc` | congestion control, timeouts, parallelism |
| Version detection | `service_scan.cc` | probe/signature matching engine |
| OS detection | `osscan2.cc` (IPv4), `FPEngine.cc`/`FPModel.cc` + `liblinear/` (IPv6 ML) | fingerprinting |
| Scripting | `nse_main.lua`, `nse_*.cc`, `nselib/`, `scripts/`, `liblua/` | embedded Lua 5.4 engine |
| Output | `output.cc`, `xml.cc` | normal/greppable/XML reports |
| Data files | `nmap-services`, `nmap-protocols`, `nmap-rpc`, `nmap-service-probes`, `nmap-os-db`, `nmap-mac-prefixes`, `nmap-payloads` | knowledge bases |

## 2. Subsystem Deep-Dive

### 2.1 Target engine
Parses hostnames, IPs, ranges, CIDR into a `TargetGroup`; resolves names with
its own parallel DNS stub resolver (`nmap_dns.cc`), does reverse-DNS during
the scan. **Mechanism:** ordinary UDP/TCP DNS queries — no raw packets. [3]

### 2.2 Host discovery
Default probes: ICMP echo, TCP SYN to 443, TCP ACK to 80, ICMP timestamp
(`-PE -PS443 -PA80 -PP`); on local Ethernet it uses **ARP** (IPv4) / **ND**
(IPv6) first. **Unprivileged users fall back to `connect()` probes to ports
80 and 443.** [4] Most probe types need raw packets; the connect fallback does
not.

### 2.3 Port scanning techniques
The book is explicit: **of all scan types, unprivileged users can only run
connect (`-sT`) and FTP bounce (`-b`) scans**; everything else sends/receives
raw packets requiring root [5]:

| Technique | Mechanism | Requires |
|---|---|---|
| `-sT` connect | OS `connect()` syscall per port; kernel does the TCP handshake | sockets only |
| `-sS` SYN (default) | send crafted SYN; classify on SYN/ACK vs RST vs ICMP-unreachable | raw send + capture |
| `-sU` UDP | UDP packets; closed ⇒ ICMP port-unreachable; else `open\|filtered` | raw capture to see ICMP errors |
| `-sA` ACK / `-sW` window | ACK probes; RST response maps firewall rules | raw + capture |
| `-sN/-sF/-sX` NULL/FIN/Xmas | flag tricks exploiting RFC 793 behavior | raw + capture |
| `-sM` Maimon | FIN/ACK probe | raw + capture |
| `-sI` idle | spoof zombie host's address; measure zombie IP-ID deltas | raw + capture + zombie |
| `-sO` IP protocol | raw IP packets per protocol number | raw + capture |
| `-sY/-sZ` SCTP | SCTP INIT / COOKIE-ECHO | raw + capture |
| `-b` FTP bounce | abuse FTP PORT to scan via proxy | sockets (deprecated) |

The scan engine (`scan_engine_raw.cc`) is an **event-driven state machine**
over libpcap + libdnet: it sends probes, tracks retransmissions, and matches
responses per host/port. The connect engine (`scan_engine_connect.cc`) wraps
non-blocking `connect()` + `select()` with the same state model.

### 2.4 Timing and congestion control
`timing.cc` implements adaptive behavior [6]: host groups start small (~5)
and grow to 1024; "ideal parallelism" rises/falls with observed reliability;
per-host RTT timeout is computed from probe history; default retransmission
limit is 10 but typically only 1 on reliable networks; `-T0..-T5` templates
preset these. **This is pure algorithm + bookkeeping — no special privilege.**

### 2.5 Version detection (`-sV`)
Algorithm verified from the book [7]:
1. Exclude ports per `Exclude` directive.
2. TCP: connect, then **NULL probe** — listen ~5 s for a banner; match
   against ~3,000 NULL signatures (regex + version substrings).
3. Soft match → restrict remaining probes to that service family.
4. Port-directed probes (`GetRequest`, `GenericLines`, `SSLSessionReq`,
   …) with `rarity`-based ordering; fallback chains between probe families
   (e.g., RTSP response may match GetRequest signatures).
5. SSL post-processor: detect TLS, reconnect over SSL, re-run scan behind
   encryption; `sslports` directive.
6. RPC grinder for SunRPC program/version enumeration; NSE fallback for
   services too complex for regex matching.
Unidentified responses are printed as fingerprints for community submission.

**Mechanism: application-layer only — sockets + string matching.**
This is the single most rebuildable-in-Kotlin subsystem in all of Nmap.

### 2.6 OS detection (`-O`)
IPv4 (`osscan2.cc`): sends crafted probes (SEQ/OPS/WIN/T1–T7, ECN, U1, IE)
and extracts dozens of response tests (ISN GCD/counter/predictability, IP-ID
sequence, TCP timestamp behavior, options ordering, initial windows, TTL,
flags, RST checksum quirks, …), then **scores each test against
`nmap-os-db` entries** (point-based matching, complex expressions) [8][9].
IPv6 (`FPEngine.cc`/`FPModel.cc`): features fed to a **linear classifier
(liblinear, logistic regression)**; novelty threshold `FP_NOVELTY_THRESHOLD
= 15.0` guards against overconfident guesses [10][11].

**Mechanism: raw packet send + capture + statistical matching.** Without
root, none of the underlying observations exist. Application-level inference
(our Phase 4) is a *different measurement* and must be labeled as such
(brief §11 — never fake OS detection).

### 2.7 Traceroute
`traceroute.cc` runs parallel TTL-progression traces to all discovered hosts
using the scan's transport. Requires TTL control on probes. (On Android,
`Os.setsockoptInt(IPPROTO_IP, IP_TTL, …)` is plausibly available without
root — **verify experimentally**, see §5.)

### 2.8 Nmap Scripting Engine (NSE)
Embedded **Lua 5.4** (`liblua/`), scripts = description + categories +
**rule** (when to run) + **action** (what to do), executed by `nse_main.lua`
with an event system (`nsock`), libraries (`nselib/`: http, ssl, dns,
smb, …), and two network I/O APIs: **connect-style** and **raw packets**
[12]. Scripts run in phases (pre-scan, scan, post-scan), in parallel with
worker threads + collaborative multithreading.

**Implication for us:** we cannot ship Nmap's scripts (NPSL). The concept
(rule/action probes with a network I/O API) is reproducible in Kotlin as a
typed probe pipeline — recommended over embedding Lua (dependency question,
see §5).

### 2.9 Output
Normal, greppable, and XML output (`output.cc`, `xml.cc`), plus Zenmap/ndiff
tooling. Pure formatting — fully reproducible (we chose JSON as primary).

### 2.10 Data files (knowledge bases)

| File | Content | Clean-room substitute |
|---|---|---|
| `nmap-services` | ~2,200 well-known port→service names | IANA Service Name & Port Number Registry (public CSV) |
| `nmap-protocols` | IP protocol numbers | IANA Protocol Numbers registry |
| `nmap-rpc` | SunRPC program numbers | self-authored from public docs if needed |
| `nmap-service-probes` | probe strings + regex signatures | **self-authored** (our own probes/signatures) |
| `nmap-os-db` | OS fingerprints | **self-authored features only** (we cannot reproduce Nmap's raw-packet features anyway) |
| `nmap-mac-prefixes` | OUI list | IEEE OUI registry (public) |
| `nmap-payloads` | UDP payloads for common ports | IANA/self-authored |

All Nmap data files are NPSL-licensed [13][14] — **do not copy**.

## 3. Kernel-Level Capability Requirements (the boundary)

| Facility | Nmap uses it for | Stock Android app? |
|---|---|---|
| Sockets API (`connect`, UDP send) | connect scan, version detection, NSE connect I/O | ✅ yes |
| Raw sockets (`SOCK_RAW`/`AF_PACKET`, `CAP_NET_RAW`) | SYN/UDP/ACK/FIN/etc. scans, OS detection, ARP, ICMP | ❌ `EPERM` without root [15][16][17] |
| Packet capture (libpcap) | reading responses, ICMP errors, idle-scan IP-ID | ❌ root required |
| ICMP send (echo/timestamp) | `-PE`, `-PP` | ❌ root required |
| ARP send | local-Ethernet discovery | ❌ raw needed |
| VpnService tun | (not used by Nmap; Android-specific) | ✅ user-consented L3 tunnel, **virtual boundary** [18] |
| TTL setsockopt | traceroute | ⚠️ plausible; verify experimentally |

## 4. THE MAPPING — Nmap Feature → Android REMAKE

Feasibility classes: **A** = rebuild now in Kotlin (sockets/app-layer),
**B** = rebuild with platform facility + experiment, **D** = delegate to
capable executor only, **U** = unverified (measure first, then decide).

| Nmap feature | Core mechanism | Class | Our phase | Executor |
|---|---|---|---|---|
| Target/port parsing, CIDR | string parsing + DNS | A | 0/1 (+CIDR later) | local |
| `-sT` connect scan | `connect()` | A | 1 | local |
| Host discovery via connect probes (80/443) | `connect()` | A | 1 (subset) | local |
| Reverse-DNS | DNS queries | A | 2 | local |
| Version detection (NULL + probes + softmatch + SSL postprocessor) | app-layer probes + regex matching | A (self-authored DB) | 2 | local |
| NSE-like script probes (subset) | typed probe pipeline in Kotlin | A (subset) | 4+ | local |
| Timing/congestion control (RTT timeouts, parallelism, groups) | algorithm | A | 2 (subset) | local |
| UDP application probing (DNS/mDNS/…) | UDP sockets; conservative states | A (subset) | 3 | local |
| Full `-sU` classification (ICMP unreachable) | raw capture of ICMP errors | D | — | remote Linux |
| SYN/ACK/FIN/Xmas/Maimon/window scans | raw send + capture | D | — | remote Linux |
| Idle scan | raw + zombie IP-ID | D | — | remote Linux |
| ARP/ND local discovery | raw L2 | D (maybe B via VPN experiment) | 6/7 | remote Linux |
| OS detection (IPv4 tests + `nmap-os-db`) | raw probes + capture | D | — | remote Linux |
| IPv6 ML OS classification | needs the same raw features | D | — | remote Linux |
| Traceroute (TTL) | TTL setsockopt | U→B | experiment | local if proven |
| VpnService-based packet observation/injection | tun | B (experiment) | 6 | local, capability-gated |
| Full Nmap run (`-A`, all scans, NSE pack) | Nmap binary on Linux | D | 7 | remote Linux executor |
| Output formats | formatting | A | 1 (JSON), later XML | local |

**Bottom line:** the brief's boundary prediction is exactly right. The local
Android executor can rebuild the *whole upper half* (parsing, connect scan,
version detection, conservative UDP probing, timing logic, scripting-pipeline
concept) — everything that is application-level. The *lower half* (raw
packets, capture, ICMP/ARP, OS detection's observations) is unreachable on
stock Android and is the delegation surface for Phase 7.

## 5. Decisions / Adjustments This Analysis Implies

1. **Phase 2 must include the NULL probe + softmatch + rarity ideas** — they
   are the core of Nmap's version detection and are purely algorithmic; our
   self-authored signature DB uses the same *concepts* with our own probe
   strings and regexes.
2. **Timing engine is promoted**: RTT-based timeout estimation (PLAN §5.1.2's
   fixed 5 s default) should evolve into Nmap-style adaptive timeouts in
   Phase 2 — recorded, not done in M1 (no scope creep).
3. **NSE equivalent**: recommend a **Kotlin typed-probe pipeline** (rule +
   action + evidence), not embedding Lua. Embedding a Lua interpreter is a
   heavy dependency (rule #16) and would invite running NPSL-licensed NSE
   scripts — against the clean-room policy.
4. **Traceroute experiment** added to the M6 experiment matrix (TTL via
   `Os.setsockoptInt`).
5. **Data strategy**: port→service names generated from the IANA registry
   (public data); all signatures/fingerprints self-authored.
6. **Phase 8 table already drafted** (§4) — it becomes the living
   compatibility ledger.

## 6. Open Questions for Later Milestones

- Does tun-injected traffic (VpnService) let us observe ICMP errors or
  ARP responses at all? (M6 experiment — currently U.)
- Can `Os.setsockoptInt` set IP_TTL on a TCP socket unprivileged? (M6.)
- Should the remote-executor protocol (M7) wrap the real `nmap` binary or
  our own engine? (Stakeholder decision at M7; NPSL redistribution
  implications re-open at that point.)

---

## Sources

[1] Nmap Network Scanning, scan phase order (book reference guide) — https://nmap.org/book/
[2] Scan phases listed (secondary, mirrors the book) — https://linuxhint.com/nmap_network_scanning/
[3] Nmap source tree, `nmap/nmap` repository file listing — https://github.com/nmap/nmap
[4] Nmap book, "Host Discovery" — https://nmap.org/book/man-host-discovery.html
[5] Nmap book, "Port Scanning Techniques" — https://nmap.org/book/man-port-scanning-techniques.html
[6] Nmap book, "Timing and Performance" — https://nmap.org/book/man-performance.html
[7] Nmap book, "Service and Application Version Detection" (technique) — https://nmap.org/book/vscan.html , https://nmap.org/book/vscan-technique.html
[8] Nmap book, "Remote OS Detection" (test catalogue) — https://nmap.org/book/osdetect.html
[9] OS detection matching, source-level analysis — https://deepwiki.com/nmap/nmap/2.5-os-detection
[10] FPEngine/FPModel source analysis (AMOSSYS) — https://blog.amossys.fr/nmap-ml.html
[11] FPEngine summary (secondary) — http://www.jinglingshu.org/?p=12254
[12] Nmap book, "Nmap Scripting Engine" — https://nmap.org/book/nse.html
[13] Nmap license change to NPSL (7.90+) — https://lwn.net/Articles/842436/ , https://nmap.org/changelog.html
[14] Fedora's NPSL review (non-free) — https://blog.desdelinux.net/en/nmap-es-incompatible-con-fedora-debido-a-su-licencia/
[15] Raw sockets need root/CAP_NET_RAW — https://unix.stackexchange.com/questions/447657/iptables-vs-af_packet-sockes
[16] Android raw sockets EPERM — https://stackoverflow.com/questions/55862319/operation-not-permitted-when-creating-a-raw-socket-within-a-rooted-android-dev
[17] NDK raw socket EPERM — https://groups.google.com/g/android-ndk/c/7FZCKNwun2I
[18] VpnService = user-consented L3 tun, virtual boundary — https://stackoverflow.com/questions/38679188/capture-network-traffic-programmatically-no-root
