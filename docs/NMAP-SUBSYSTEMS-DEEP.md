# NMAP SUBSYSTEMS — DEEP ANALYSIS (for the Android REMAKE)

> Status: **Research/analysis (docs-only phase)** — no code.
> Scope: the four subsystems that decide how the REMAKE must be built —
> (1) the `ultra_scan` engine and its timing/congestion algorithms,
> (2) the raw TCP packet-send path, (3) the service/version detection
> signature format, (4) the OS-detection matching algorithms — plus the
> stakeholder's capability-delegation design for TCP packet sending, which
> is integrated here as architecture direction.
>
> **Clean-room note:** directive *syntaxes* and *algorithms* below are
> documented from Nmap's public book. Nmap's actual signature lines, probe
> strings, and fingerprint data are NPSL-licensed — we copy none of them;
> our own data files will use the same *concepts* with self-authored content.

---

## 1. The Scan Engine: `ultra_scan` (stateful, adaptive)

Nmap's primary engine, `ultra_scan` (`scan_engine.cc`), drives SYN, connect,
UDP, NULL, FIN, Xmas, ACK, window, Maimon, and IP-protocol scans plus host
discovery. Only idle scan and FTP bounce have separate engines [1].

Key architectural facts, with the exact algorithms:

### 1.1 Stateful, not stateless
Every probe is marked (sequence numbers, source/destination ports, ID
fields depending on probe type) so responses — and therefore **drops** —
are recognized. A stateless flood scanner cannot detect loss and
throttle; Nmap keeps full per-probe state in RAM. [1]

### 1.2 RTT estimation (exact formulas, per host *and* per group)
Loosely based on TCP's retransmission timer (RFC 2988) [1]:

```text
newsrtt   = oldsrtt   + (instanceRTT - oldsrtt) / 8
newrttvar = oldrttvar + (|instanceRTT - oldsrtt| - oldrttvar) / 4
timeout   = newsrtt + 4 * newrttvar
```

Late responses are still matched against probe state after timeout. [1]

### 1.3 Congestion control (modeled on TCP, RFC 2581)
- Per-target **and** per-group congestion window + congestion threshold.
- Slow start → congestion avoidance at the threshold; exponential backoff
  on detected drops (window typically cut to 1).
- **Critical adaptation:** a scan is not a TCP stream (a default-deny
  firewall answers almost nothing), so every window change is multiplied
  by the observed *responses-sent ratio* — each response carries more
  weight when few probes are answered. [1]

### 1.4 Timing probes ("port scan pings")
Against heavily filtered hosts with ≥ 1 known-responsive port, Nmap
re-probes that port every **1.25 s** of silence to keep latency/loss
estimates alive. [1]

### 1.5 Inferred neighbor times
When a host yields no timing data at all, timing is inferred from the
group, weighted by tracked between-host variance (conservative when
hosts differ wildly). [1]

### 1.6 Adaptive retransmission
No loss detected → typically **1 retransmission**; heavy loss → up to
**10**, then a warning and give-up (defense against tarpitting). [1]

### 1.7 Scan delay (rate-limit detection)
When drops are disproportionate (e.g., Linux kernels rate-limit ICMP
errors to ~1/s during UDP scans), Nmap inserts a per-target delay,
starting ~**5 ms** and doubling, up to a default max of **1 s**. [1]

### 1.8 What this means for the REMAKE
Every algorithm above is pure bookkeeping — fully implementable in
Kotlin. But one **honest distinction** must be recorded: for a *connect*
scan, the **kernel** owns SYN retransmissions and RTT-driven retries
(`tcp_syn_retries`); the app only observes `connect()` completion,
timeout, or refusal. Our M1 connect engine therefore **cannot** claim
Nmap-style retransmission control — it reports what the OS did, and the
evidence strings must say so. The srtt/rttvar/timeout formulas and
bounded parallelism are the correct *substitute* for M1's fixed timeout
and become engine defaults in Phase 2 (recorded, not done in M1 — no
scope creep).

---

## 2. The Raw TCP Packet-Send Path (and the Delegation Architecture)

### 2.1 How Nmap actually sends a raw probe
`ultra_scan` (raw mode, `scan_engine_raw.cc`) per probe: construct the
packet (`tcpip.cc`: IP/TCP headers, checksums, identifier fields) →
transmit via libdnet (link-layer/IP send) → capture responses via
libpcap with a per-host-group **BPF filter** (`FPEngine::bpf_filter`)
→ match responses to probe state → classify. [2][3]

Two separable problems, exactly as the brief says:

```text
Packet construction (bytes: IP+TCP+checksum)   ≠   Packet transmission (needs CAP_NET_RAW / root)
```

On stock Android the first is pure Kotlin-representable (and useful for
Phase 6 VPN experiments); the second fails with `EPERM` — verified [4][5][6].
Nmap itself confirms the boundary: unprivileged users can run only
connect (`-sT`) and FTP bounce scans [7].

### 2.2 The stakeholder's design: "closest legitimate packet-generation capability"
Stakeholder essay (2026-09-11) — integrated here as architecture direction:

- Forwarding ≠ packet generation. A router forwards existing packets;
  it exposes no generic "build me a packet" API. Wi-Fi is a link
  technology (association/ACK/retransmission/power control), **not** an
  application-level raw-packet API.
- But network devices *are computers*. A router, LAN machine, appliance,
  VPN endpoint, or cloud Linux box already possesses the networking
  capability the app lacks. So the problem is **capability delegation**,
  not privilege escalation.
- Identities to keep separate:

```text
Encryption  ≠ privilege
Tunneling   ≠ packet crafting
Routing     ≠ packet generation
Wi-Fi       ≠ application-level raw-packet API
```

- The question becomes: **where is the closest legitimate
  packet-generation capability available to the Android application?**
  (local process · LAN device · router with suitable software · VPN
  endpoint · cloud Linux)

### 2.3 Resulting architecture (now adopted into the plan)

```text
                    Scan Plan
                       │
                 Capability Router
                 /       |        \
        Android       LAN Agent    Remote Nmap
         Engine          │             │
      normal sockets     │        genuine Nmap
                         │        full capabilities
                \        |        /
                    Results → Android analysis/UI
```

The Android app becomes the **controller, planner, analyzer, and UI**;
execution nodes provide capabilities; every result carries executor
identity + capability proof. Local execution remains first-class:
everything Android legitimately permits (connect scan, app-layer probes,
service ID, local observations) runs locally — delegation is only for
what local execution cannot do.

### 2.4 Design deltas this introduces (M7-shaped, planned now, built later)
1. `ExecutorNode` gains `discoveredVia` and `capabilityProof` (evidence a
   claimed capability was demonstrated — e.g., a signed result sample).
2. `ExecutionRouter` grows a **topology discovery** stage (Phase 7):
   LAN executors enroll via authenticated discovery (mDNS/SSDP beacon +
   token), remote executors via TLS/mTLS enrollment; the router picks
   the *closest capable* executor per ScanPlan intent.
3. Transport options are independent of capability: TLS control channel
   and/or a VpnService tunnel carrying the executor protocol. The doc's
   rule stands — the tunnel protects the instruction, it does not grant
   the endpoint new privileges.
4. UI labels every delegated result with its executor and trust level.

---

## 3. Service/Version Detection Internals (`nmap-service-probes`)

### 3.1 The file format (concepts, not copied content) [8]
Line-oriented directives, each applying to the most recent `Probe`:

| Directive | Meaning |
|---|---|
| `Exclude <ports>` | ports never version-scanned (default: printers, T:9100-9107); `--allports` overrides |
| `Probe TCP|UDP <name> q\|<bytes>\| [no-payload]` | probe string with `\n \r \t \xHH` escapes; empty = NULL probe; `no-payload` = don't reuse as UDP payload |
| `match <service> m/<PCRE>/[is] p/…/ v/…/ i/…/ h/…/ o/…/ d/…/ cpe:/…/` | hard match: PCRE + versioninfo fields; `ssl/<service>` prefix for SSL-tunneled services |
| `softmatch <service> m/<PCRE>/` | soft match: identifies family, scanning continues restricted to that service's probes |
| `ports <list>` / `sslports <list>` | ports this probe is most effective on / SSL-wrapped ports |
| `totalwaitms <ms>` | probe wait (NULL probe default ~5000 ms) |
| `tcpwrappedms <ms>` | NULL-probe only: connection closing before this ⇒ `tcpwrapped` |
| `rarity 1..9` | probe utility; higher = rarer = tried only at higher `--version-intensity` |
| `fallback <probe,probe,…>` | response match-order fallbacks (TCP: probe → fallbacks → NULL; UDP: never NULL) |

Versioninfo substitution helpers: `$1`… group captures; `$P(n)` strips
unprintables (UTF-16→ASCII); `$SUBST(n,"a","b")` replaces substrings;
`$I(n,">"/"<")` unpacks big/little-endian integers. CPE fields emit
`cpe:/a`, `/o`, `/h` names. [8]

### 3.2 The algorithm (verified from the book) [9][10]
```
port scan → open/open|filtered ports
  → Exclude filter
  → TCP: connect; NULL probe: listen ~5 s for banner
      → match vs ~3,000 NULL signatures        → done
      → softmatch                                → restrict probe set
  → port-directed probes (ports/sslports) in file order, filtered by rarity
      → match → done | softmatch → restrict | fallback chain → NULL (TCP only)
  → SSL post-processor: detect TLS → reconnect over SSL → re-run scan
  → RPC grinder (SunRPC program/version enumeration) for RPC services
  → NSE fallback for complex services
  → unidentified: print response as fingerprint for submission
```
UDP ports that respond during version detection flip `open|filtered` →
`open`. One connection usually suffices (NULL probe shares the socket
with the probable-port probe). [9]

### 3.3 What the REMAKE adopts (Phase 2)
- Same *directive grammar* in a **self-authored** DB (our own probes and
  signatures; `java.util.regex` as the PCRE-equivalent — its divergences
  from PCRE get documented and covered by tests).
- NULL probe first, softmatch pruning, rarity budgets, fallback chains,
  SSL post-processor via `SSLSocket`, `tcpwrapped` detection, evidence =
  probe name + response prefix + sha256 (already in PLAN §5.2 M2).
- We do **not** ship `nmap-service-probes` (NPSL) — see
  [`NMAP-DEEP-DIVE.md`](NMAP-DEEP-DIVE.md) §2.10.

---

## 4. OS-Detection Matching Internals

### 4.1 IPv4: weighted point matching [11]
- Subject fingerprint is tested against **every** reference fingerprint
  in `nmap-os-db`; per probe-category line (SEQ, T1–T7, IE, U1, ECN…),
  then per test (R, DF, W, T, S, A, F, O, RD, Q, …).
- Each test carries a weight from the `MatchPoints` structure, e.g.:
  `SEQ(SP=25%GCD=75%ISR=25%TI=100%…)`, `T1(R=100%DF=20%…S=20%A=20%F=30%RD=20%Q=20)`
  (weights are per-category, not global).
- Score = `NumMatchPoints / PossiblePoints` → a confidence in [0,1];
  reference tests support exact values and operators `| - > <`.
- Perfect/very-close matches printed; `--osscan-guess` lowers the bar. [11]

### 4.2 IPv6: logistic regression (LIBLINEAR) + novelty detection [11]
- Database is **embedded in C++** (`FPModel.cc`): scaling constants +
  one boundary vector per OS class, trained from labeled user-submitted
  fingerprints.
- Score per class = dot product(feature vector, class vector) mapped
  through `100 / (1 + e^x)`.
- **Novelty detection**: variance-scaled Euclidean distance from the
  class mean; report only if novelty < **15**.
- **Ambiguity rule**: top two classes within **10%** ⇒ no match.
  Example run (Mac OS X): 61.05% / novelty 1.00 accepted; runners-up
  rejected by novelty/ambiguity. [11]

### 4.3 What the REMAKE adopts (Phase 4)
The *matching layer* — weighted point scoring, logistic mapping, novelty
thresholding, ambiguity rejection — is pure mathematics and fully
implementable in Kotlin. The *observations* are not (raw probes +
capture; see §2). So our Phase 4 fingerprint engine will reuse these
scoring concepts on **application-level features** (banner patterns, TLS
parameters, timing deltas, header order) with a self-authored feature
model and database, explicitly labeled **"application-level inference"**,
never "Nmap OS detection" (brief §11; PLAN §5.2 M4).

---

## 5. Integrated Design Deltas (to be folded into PLAN/ARCHITECTURE)

| # | Delta | Where |
|---|---|---|
| 1 | Delegation architecture adopted: closest-capability routing, executor topology discovery, capability proof | PLAN §2/§6; ARCHITECTURE §4.4 (M7) |
| 2 | Phase 2 engine adopts srtt/rttvar/timeout formulas + bounded parallelism (replacing fixed M1 timeout) | PLAN §5.2 M2 |
| 3 | Connect-scan honesty: kernel owns retransmission; evidence reflects OS behavior | ARCHITECTURE §4; PLAN §5.1.2 |
| 4 | Phase 2 signature DB: self-authored, directive grammar per §3.1, `java.util.regex` + documented PCRE divergences | PLAN §5.2 M2 |
| 5 | Phase 4 matching layer: weighted points + logistic + novelty + ambiguity on app-layer features | PLAN §5.2 M4 |
| 6 | Transport ≠ capability; encryption ≠ privilege documented as architectural invariant | ARCHITECTURE (invariants) |

---

## Sources

[1] Nmap book, "Scan Code and Algorithms" (ultra_scan; RTT, congestion,
    timing probes, adaptive retransmission, scan delay) —
    https://nmap.org/book/port-scanning-algorithms.html
[2] Nmap source tree roles (tcpip.cc, libdnet, libpcap, scan_engine_raw.cc,
    FPEngine bpf_filter) — https://github.com/nmap/nmap
[3] OS detection runtime flow (probe → capture → fingerprint) —
    https://deepwiki.com/nmap/nmap/2.5-os-detection
[4] Raw sockets require root/CAP_NET_RAW —
    https://unix.stackexchange.com/questions/447657/iptables-vs-af_packet-sockes
[5] Android raw sockets: EPERM —
    https://stackoverflow.com/questions/55862319/operation-not-permitted-when-creating-a-raw-socket-within-a-rooted-android-dev
[6] NDK raw socket: EPERM confirmed —
    https://groups.google.com/g/android-ndk/c/7FZCKNwun2I
[7] Nmap book, "Port Scanning Techniques" (only -sT and -b unprivileged) —
    https://nmap.org/book/man-port-scanning-techniques.html
[8] Nmap book, "nmap-service-probes File Format" (all directives) —
    https://nmap.org/book/vscan-fileformat.html
[9] Nmap book, "Technique Described" (version detection algorithm) —
    https://nmap.org/book/vscan-technique.html
[10] Nmap book, "Service and Application Version Detection" —
     https://nmap.org/book/vscan.html
[11] Nmap book, "OS Matching Algorithms" (IPv4 MatchPoints; IPv6 logistic
     regression + novelty threshold 15 + 10% ambiguity rule) —
     https://nmap.org/book/osdetect-guess.html
