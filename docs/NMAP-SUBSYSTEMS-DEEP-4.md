# NMAP SUBSYSTEMS — DEEP ANALYSIS, PART 4 (final)

> Status: **Research/analysis (docs-only phase)** — no code.
> Scope: (1) `service_scan.cc` internals, (2) IPv6 OS fingerprinting,
> (3) auxiliary tools (Nping/Ncat/ndiff/Zenmap) — then the capstone
> subsystem inventory closing the Nmap analysis series.
> Clean-room rule unchanged: concepts yes, Nmap data/assets no.

---

## 1. `service_scan.cc` Internals

Verified from the source-level index [1] + the book (parts 1–3 of this
series):

- **`AllProbes`** — loads the entire `nmap-service-probes` file into
  `vector<ServiceProbe*>` plus a dedicated `nullProbe`; `getProbeByName()`
  serves the `fallback` directive.
- **`ServiceProbe`** — probe string (bytes + length), `probableports`
  (from `ports`/`sslports`), its `match`/`softmatch` list, rarity.
- **`ServiceProbeMatch`** — a **compiled PCRE2 regex** + templates for
  service name / product / version / info / CPE.
- **`ServiceNFO`** — per-open-port state machine: `probe_state`,
  `tunnel` (service tunnel type: none / SSL / …), `currentProbe()`,
  `nextProbe()`, `resetProbes()` — this object embodies the NULL →
  probable-port → rarity-ordered → fallback walk.
- **`ServiceGroup`** — parallel execution: `services_in_progress` /
  `services_remaining` / `services_finished` lists; the group is the
  concurrency unit, mirroring host groups in the scan engine.

**REMAKE mapping:** our Phase 2 engine design maps 1:1:
`AllProbes` → self-authored ProbeDb (parsed file); `ServiceProbe` →
`ProbeDefinition`; `ServiceProbeMatch` → `Signature(compiled Pattern,
templates)`; `ServiceNFO` → per-port `DetectionSession` state machine;
`ServiceGroup` → the scheduler's bounded probe concurrency. The only
substituted component is the regex engine (`java.util.regex` instead of
PCRE2 — divergences documented + tested; already recorded).

## 2. IPv6 OS Fingerprinting

Verified from the book [2]. Same high-level technique as IPv4 (probes →
responses → database), but a **separate engine** and different matching.

**Probes (up to 18, in order):**
- `S1`–`S6` — the six sequence-generation probes (IPv4's "T1" set),
  100 ms apart; skipped if no open port.
- `IE1` — ICMPv6 echo, code 9 (deliberately wrong), ID 0xabcd, 120
  zero bytes + one Hop-By-Hop padding header.
- `IE2` — ICMPv6 echo, code 0, with **four erroneous extension
  headers** (HBH, DestOpts, Routing, HBH — invalid ordering); OSes
  answer with *different ICMPv6 errors* — a quirk fingerprint.
- `NI` — RFC 4620 Node Information Query (type 139), asking for IPv4
  addresses, fixed nonce; some OSes return a DNS name instead.
- `NS` — Neighbor Solicitation (type 135), hop limit forced to 255
  (RFC 2461); same-subnet only.
- `U1` — UDP to a closed port, 300 × 0x43 bytes, to elicit ICMPv6
  port-unreachable.
- `TECN` — IPv4's ECN probe in IPv6 form (SYN with ECE+CWR, urgent
  0xF7F5, window 3, WScale 10/MSS 1460/SACK options).
- `T2`–`T7` — the IPv4 T2–T7 family.

**Features:** `TCP_ISR` (ISN counter rate from S1–S6), per-response
`PLEN`/`TC`/`HLIM`, per-TCP-response `TCP_WINDOW`, the 8 TCP flag bits,
reserved bits, first-16 TCP option type codes and lengths, `TCP_MSS`,
`TCP_SACKOK`, `TCP_WSCALE`; unavailable features = **−1**; then scaled
into [0,1] using training-derived constants. The flow label is set to
0x12345 where the platform allows it (recorded in fingerprints).
Matching: logistic regression + novelty + ambiguity (covered in
NMAP-SUBSYSTEMS-DEEP §4.2).

**REMAKE verdict:** class **D** — every probe is raw ICMPv6/TCP with
controlled headers; no stock-Android path exists. The feature-vector
engineering ideas (missing = −1, per-feature scaling, quirk probes) are
adopted as *concepts* for our Phase 4 application-level feature model.

## 3. Auxiliary Tools (inventory)

Verified from the source tree [3] and DeepWiki overview [4]:

| Tool | Role | REMAKE relevance |
|---|---|---|
| `ncat/` | netcat-style connect/listen/redirect tool over the Nsock engine | Inspirational only — our engine reuses the *transport seam* idea; no local ncat clone planned |
| `nping/` | dedicated packet-generation/ping tool (custom TCP/UDP/ICMP/ARP, payloads, timing) | Its *capability surface* (what one can do with raw access) is already in our feasibility table; not rebuilt |
| `ndiff/` | compares two scan XML outputs (diff of hosts/ports/services) | **Class A concept**: our JSON schema v1 is diffable by design; a result-diff view is a recorded recommendation (M2+) |
| `zenmap/` | Python/GTK GUI + topology views | Superseded by our Compose UI; its *capability-aware presentation* goal is what our banner UI implements natively |

All four are NPSL-licensed components of the Nmap project; we copy
nothing, and none of them are milestones.

## 4. Capstone — Nmap Subsystem Inventory (complete)

| # | Subsystem | Files | Mechanism class | REMAKE verdict |
|---|---|---|---|---|
| 1 | Target selection | targets.cc, TargetGroup.cc, NetBlock | parsing + DNS | **A** (subset in M1; richer grammar later) |
| 2 | Mass DNS | nmap_dns.cc, massdns.cc | parallel stub resolver | A-later (platform resolver first) |
| 3 | Host discovery | scan_engine (pings) | raw/ICMP/ARP + connect fallback | **A** (connect subset) / **D** (rest) |
| 4 | ultra_scan engine | scan_engine.cc | stateful probe engine | **A** (algorithms adopted) |
| 5 | Connect scan | scan_engine_connect.cc | non-blocking connect | **A** (M1) |
| 6 | Raw scans (SYN/UDP/ACK/FIN/Xmas/Maimon/window/IPproto/SCTP) | scan_engine_raw.cc, tcpip.cc | raw send + pcap capture | **D** |
| 7 | Idle scan | idle_scan.cc | IP-ID side channel | **D** |
| 8 | FTP bounce | nmap_ftp.cc | FTP PORT via sockets | **A-later** (technically socket-level; deprecated/low-value — recorded) |
| 9 | Timing/congestion | timing.cc | adaptive algorithms | **A** (Phase 2) |
| 10 | Version detection | service_scan.cc | app-layer probes + PCRE | **A** (Phase 2, self-authored DB) |
| 11 | IPv4 OS detection | osscan2.cc | raw probes + weighted match | **D** |
| 12 | IPv6 OS detection | FPEngine.cc, FPModel.cc | raw probes + logistic regression | **D** |
| 13 | Traceroute | traceroute.cc | TTL progression | **U→B** (M6 experiments) |
| 14 | NSE | nse_*.cc, nse_main.lua | Lua scripting over Nsock | **A-subset** (typed Kotlin probe pipeline) / scripts themselves NPSL-excluded |
| 15 | Output/XML | output.cc, xml.cc | formatting + DTD/XSL | **A** (JSON v1; own schema) |
| 16 | Data files | nmap-* | knowledge bases | **A-substitutes** (IANA + self-authored) |
| 17 | Nsock | nsock/ | event library | **A-analog** (coroutines + transport seam) |
| 18 | Aux tools | ncat/, nping/, ndiff/, zenmap/ | various | ndiff-concept recommended; rest out of scope |

This inventory closes the analysis: every Nmap subsystem now has a
verified mechanism and an explicit REMAKE verdict, and the Phase 8
compatibility ledger (PLAN/NMAP-DEEP-DIVE §4) can be populated
mechanically from it.

## 5. Integrated Design Deltas (final round)

| # | Delta | Where |
|---|---|---|
| 1 | Phase 2 engine maps to AllProbes/ServiceProbe/ServiceNFO/ServiceGroup | ARCHITECTURE (implementation note) |
| 2 | Phase 4 feature model adopts missing-value (−1) + scaling conventions | PLAN M4 |
| 3 | FTP bounce recorded as technically-feasible-but-deprecated (A-later), not a milestone | RECOMMENDATIONS |
| 4 | Result-diff view (ndiff concept over JSON v1) recorded | RECOMMENDATIONS |

---

## Sources

[1] DeepWiki, "Service Detection" (AllProbes, ServiceProbe,
    ServiceProbeMatch, ServiceNFO, ServiceGroup) —
    https://deepwiki.com/nmap/nmap/2.4-service-detection
[2] Nmap book, "IPv6 fingerprinting" (probes S1–S6, IE1/IE2, NI, NS,
    U1, TECN, T2–T7; feature list and scaling) —
    https://nmap.org/book/osdetect-ipv6-methods.html
[3] Nmap source tree (ncat/, nping/, ndiff/, zenmap/) —
    https://github.com/nmap/nmap
[4] DeepWiki, "Overview of Nmap" (auxiliary tool roles) —
    https://deepwiki.com/nmap/nmap
