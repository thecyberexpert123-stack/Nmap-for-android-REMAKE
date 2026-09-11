# NMAP SUBSYSTEMS — DEEP ANALYSIS, PART 3

> Status: **Research/analysis (docs-only phase)** — no code.
> Scope: (1) TCP idle scan, (2) traceroute, (3) the output system & XML
> structure, (4) target selection & the mass-DNS engine. Clean-room rule
> unchanged: concepts yes, Nmap data/assets no.

---

## 1. TCP Idle Scan (`-sI`) — the IP-ID Side Channel

Verified from the book's dedicated chapter [1]. Idle scan lets a scanner
probe a target **without sending it a single packet from its own address**,
bouncing the scan off a "zombie" host. Per port, three steps:

```text
1. Probe zombie's IP ID (SYN/ACK → zombie answers RST, disclosing its IP ID)
2. Forge SYN from the zombie's address to target:port
   - open port   → target sends SYN/ACK to zombie → zombie sends RST → IP ID +1
   - closed port → target sends RST to zombie    → zombie ignores it   → IP ID +0
   - filtered    → nothing reaches the zombie                       → IP ID +0
3. Probe zombie's IP ID again; compare with step 1
   - increase of 1 → closed|filtered (indistinguishable by design)
   - increase of 2 → open
   - increase > 2  → bad zombie (unpredictable ID / unrelated traffic)
```

Consequences verified in the chapter: IDS blames the zombie; trust
relationships (e.g., DB reachable only from web server) become visible by
choosing the trusted host as zombie; the scan is far slower than SYN scan
(15 s → 15 min+); zombies must have predictable, incrementing IP IDs and
idle traffic; the attacker must be able to spoof the zombie's source
address end-to-end.

**REMAKE verdict:** class **D** (delegation only). Needs raw spoofing
(with a foreign source address — beyond even plain CAP_NET_RAW; requires
no egress filtering on the path) + capture of the zombie's replies. This
is the clearest case for the remote-executor architecture: the *concept*
and result model (open vs closed|filtered, zombie metadata, trust
inference) can be rendered by our UI from a delegated executor's output;
the mechanism cannot exist locally.

## 2. Traceroute

Source roles verified: `traceroute.cc`/`traceroute.h` implement it; the
engine reuses the scan's transport and runs traceroutes in parallel to
all discovered hosts (`--traceroute`). [2][3]

The mechanism is the standard TTL-progression technique (public protocol
knowledge, RFC 792): send probes with increasing IP TTL; each hop that
expires TTL answers ICMP time-exceeded, disclosing the path; the final
host answers the probe itself. Nmap's variant traces **in reverse
parallel** with the port scan (per-host state, per-hop timing stats, RTT
measurements per hop), and for connect scans uses TCP probes.

**REMAKE verdict:** class **U→B** (already in the experiment matrix).
The two prerequisites are (a) setting IP TTL on outgoing probes
(`Os.setsockoptInt(IPPROTO_IP, IP_TTL, …)`) and (b) *observing* ICMP
time-exceeded replies. (b) is not visible through normal sockets — that
is exactly what the M6 device experiment must determine; on a plain
socket, ICMP errors surface only coarsely (e.g., EHOSTUNREACH), not as
per-hop trace data. Realistic local option: **hop-limited TCP connect
runs** (TTL sweep, one hop at a time, using connect timeouts) — slow and
noisy; recorded as an M6 experiment variant, not promised functionality.

## 3. Output System & XML Structure

Verified internals [4]:

- **Bitmask-driven logging**: `LOG_NORMAL` (human, `-oN`), `LOG_MACHINE`
  (grepable, `-oG`), `LOG_XML` (`-oX`), `LOG_SKID` (leetspeak variant —
  an easter-egg-ish transform in `skid_output()`), `LOG_STDOUT`.
  `log_write()`/`log_vwrite()` fan out to every enabled stream.
- **Evidence model**: `state_reason_t` per port = `reason_id` (e.g.,
  `ER_SYNACK`, `ER_PORTUNREACH`) **+ the source IP and TTL of the packet
  that determined the state**. `serviceDeductions` stores product,
  version, info, hostname, devicetype, CPE strings, formatted by
  `populateFullVersionString()`.
- **Presentation**: `NmapOutputTable` does column alignment/multi-line
  wrapping; `printportoutput()` orchestrates PortList → normal/XML/grepable;
  ignored states summarized ("Not shown: X closed ports").
- **XML**: schema in `docs/nmap.dtd`, HTML/other transforms in
  `docs/nmap.xsl`. (Both files are NPSL-licensed; we define our own JSON
  schema — already done in ARCHITECTURE §5.)

**REMAKE verdict / delta:** our evidence model should mirror
`state_reason_t` explicitly: every `PortResult` already carries evidence
+ `ScanError`; the missing piece is **reason provenance** (which
observation produced the state). M1's evidence strings encode this
("Connection refused (ECONNREFUSED)") — good enough for M1, but Phase 2
should introduce a typed `StateReason(code, sourceIp?, ttl?)` to make
evidence queryable, matching Nmap's model. Recorded as a Phase 2 design
requirement (PLAN M2 already lists evidence; this adds structure).
`LOG_SKID` is obviously **not** reproduced (rule #2 — no junk).

## 4. Target Selection & the Mass-DNS Engine

Verified internals [5]:

- **NetBlock hierarchy**: `NetBlockIPv4Ranges` (octet ranges + CIDR),
  `NetBlockIPv6Netmask`, `NetBlockHostname`, `NetBlockRandomIPv4`
  (`-iR`); `TargetGroup` contains NetBlocks; `HostGroupState` manages a
  lookahead buffer (enough targets for parallel scanning within
  memory/timing constraints).
- **Mass DNS**: hostnames flow through a request queue into
  `nmap_mass_dns()` (`libnetutil/massdns.cc`, `nmap_dns.cc` wrapper) —
  a parallel, mass-capable stub resolver rather than serial
  `getaddrinfo` calls; reverse-DNS during scan uses the same engine.
- **Exclusions** (`--exclude`, `--excludefile`) via an addrset before
  scanning; `nmap-services` supplies open-frequency priors used to order
  port scanning.

**REMAKE verdict / delta:**
- Target-spec parsing: our PLAN §5.1.1 grammar (single/list/range/
  top-100/all + CIDR deferred) is a deliberate subset of Nmap's richer
  grammar (octet ranges, input files, random targets, excludes). The
  richer forms are recorded recommendations (CIDR already; add octet
  ranges + `--exclude` analog when multi-target scans arrive — M3/M5).
- Mass-DNS: `java.net.InetAddress` uses the platform resolver; a
  self-hosted parallel stub resolver is *not* justified for M1
  (dependency/correctness cost, rule #14/#16) — recorded as an option
  only if profiling shows resolution as the bottleneck with many
  hostnames (M5+).
- `nmap-services` "open-frequency" port ordering: our self-authored
  `top-100` list is the same *idea* from public frequency knowledge —
  honest, self-authored, already in the plan.

## 5. Integrated Design Deltas

| # | Delta | Where |
|---|---|---|
| 1 | Typed `StateReason(code, sourceIp?, ttl?)` evidence provenance in Phase 2 (mirrors `state_reason_t`) | PLAN M2 criteria |
| 2 | Idle scan + full traceroute confirmed delegation-class D; TTL-sweep connect traceroute added as M6 experiment variant | RECOMMENDATIONS / M6 |
| 3 | Richer target grammar (octet ranges, input files, excludes, `-iR`-style) recorded for multi-target milestones | RECOMMENDATIONS |
| 4 | Mass-DNS parallel resolver deferred (platform resolver first; revisit only on profiling evidence) | RECOMMENDATIONS |
| 5 | `LOG_SKID` and similar non-purposeful output modes not reproduced | RECOMMENDATIONS (explicit no) |

---

## Sources

[1] Nmap book, "TCP Idle Scan (-sI)" (IP-ID side channel, +1/+2 logic,
    zombie requirements, trust mapping, speed) —
    https://nmap.org/book/idlescan.html
[2] Nmap source tree (traceroute.cc/traceroute.h roles) —
    https://github.com/nmap/nmap
[3] DeepWiki, "Port Scanning" (traceroute integration with the scan
    engine) — https://deepwiki.com/nmap/nmap/2.3-port-scanning
[4] DeepWiki, "Output System" (bitmask logging, state_reason_t,
    serviceDeductions, NmapOutputTable, printportoutput, nmap.dtd/xsl) —
    https://deepwiki.com/nmap/nmap/2.6-output-system
[5] DeepWiki, "Target Selection and Host Discovery" (NetBlock hierarchy,
    TargetGroup, HostGroupState, nmap_mass_dns) —
    https://deepwiki.com/nmap/nmap/2.2-target-selection-and-host-discovery
