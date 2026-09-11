# UI/UX CONCEPT — Capability-Aware Scanner App

> Status: **Design (docs-only phase)** — no code.
> Scope: screen map, screen specs, state vocabulary, capability banner,
> authorization UX, and M1 scope lines for the Compose + Material 3 app
> (ADR-0002, ADR-0003). Complements ARCHITECTURE §6 and implements PLAN
> D7 / §5.1.5 UI checks and the brief's §26 (self-describing UI).

---

## 1. Design Principles

1. **Honesty first**: the UI never shows an option the engine can't
   perform; unavailable things are visible with reasons (never hidden,
   never fake).
2. **Evidence visible**: every result row explains *why* the state was
   assigned (evidence string verbatim; structured `StateReason` from M2).
3. **Self-describing capabilities**: one banner, one vocabulary, derived
   from `CAPABILITY-MATRIX.md` by a pure function — no hardcoded strings.
4. **Consent before traffic**: no probe leaves the device until the user
   confirms an authorization summary (targets, ports, techniques).
5. **Provenance always labeled**: local vs delegated results are
   visually distinct; delegated rows carry executor + trust badge.
6. **Minimal M1**: input → consent → scan → progress → results → export.
   Everything else is designed here but implemented in its milestone.

## 2. Screen Map

```text
M1 (implemented now)
 ScanInput ──consent dialog──▶ Scanning ──▶ Results
                                   ▲          │
                                   └──cancel──┘

M2+: Results gains service/version detail; export/share sheet
M5:  CapabilityExplainer screen (device report)
M7:  Executors screen (enroll/manage/revoke), delegated result badges
```

Navigation: single-activity, Compose Navigation, bottom bar only when
the Executors screen exists (M7). M1 = two screens + two dialogs.

## 3. Screen Specs

### 3.1 ScanInput (M1)

```text
┌────────────────────────────────────────────┐
│ Nmap for Android REMAKE          (menu ⋮)  │
│                                            │
│  Capability banner                         │
│  ┌──────────────────────────────────────┐  │
│  │ TCP connect scan     ✓ Available     │  │
│  │ Service detection    ○ Planned · M2  │  │
│  │ Raw packet transmit  ✗ Requires      │  │
│  │                      capable executor│  │
│  └──────────────────────────────────────┘  │
│                                            │
│  Target                                      │
│  [ 192.168.1.10                    ]        │
│   hint: single host (CIDR planned)          │
│                                            │
│  Ports                                      │
│  [ 22,80,443                ]  chips: 1-1000│
│                                top-100  all│
│                                            │
│  Scan type (M1: one available)             │
│  ● TCP connect scan                        │
│  ○ SYN scan      (requires capable executor)│
│                                            │
│  [ Start scan ]                             │
│  Scan will contact N targets × M ports.    │
│  Only scan networks you own or are         │
│  authorized to test.                       │
└────────────────────────────────────────────┘
```

- Port chips insert common specs; validation errors surface inline as
  typed messages from `ScanError` (PLAN §5.1.1) — no toasts for errors.
- Disabled options (SYN scan) stay **visible**, disabled, with the
  reason — principle 1.
- Menu: "About / capabilities", "Export last result" (M2).

### 3.2 Authorization dialog (M1, before every scan)

```text
┌─ Confirm scan scope ──────────────────────┐
│ Targets     192.168.1.10                  │
│ Ports       22, 80, 443 (3 ports)         │
│ Technique   TCP connect scan (local)      │
│                                            │
│ ▢ I own these hosts or have written       │
│   authorization to scan them.             │
│                                            │
│ Warning: this device will send connection │
│ attempts to the listed targets.           │
│                                            │
│ [ Cancel ]              [ Authorize scan ] │
└────────────────────────────────────────────┘
```

- Start is disabled until the checkbox is checked (principles 1/4).
- Private-range detection (RFC 1918) adds a "local network" note;
  public IPs add a stronger caution line. No blocking — informing.

### 3.3 Scanning (M1)

```text
┌────────────────────────────────────────────┐
│ Scanning 192.168.1.10            [Cancel]  │
│                                            │
│ progress  ▓▓▓▓▓▓▓░░░░░░░  62/100 ports     │
│                                            │
│ live results (streaming in)                │
│ 22   CLOSED    1 ms   refused              │
│ 53   OPEN      12 ms  connect completed    │
│ 80   TIMEOUT   —      no response in 5000ms│
│ 443  … probing …                           │
└────────────────────────────────────────────┘
```

- Live rows appear as `PortFinished` events arrive (buffered channel,
  ARCHITECTURE §3); scanning is lifecycle-aware (ViewModel scope).
- Cancel → CANCELLING state → bounded completion (PLAN §5.1.3-3), then
  partial results are shown, labeled "incomplete — cancelled by user".

### 3.4 Results (M1)

```text
┌────────────────────────────────────────────┐
│ Results — 192.168.1.10   [Export JSON]     │
│ Scan: 100 ports · 5.2 s · executor: local  │
│                                            │
│ ┌ Host card ────────────────────────────┐  │
│ │ 192.168.1.10   executor: local-android│  │
│ │ 22  CLOSED    1 ms    Connection      │  │
│ │                       refused          │  │
│ │ 53  OPEN      12 ms   TCP connect      │  │
│ │                       completed in 12ms│  │
│ │ 80  TIMEOUT   —       No response      │  │
│ │ 443 OPEN      9 ms    …                │  │
│ └────────────────────────────────────────┘  │
└────────────────────────────────────────────┘
```

- Row → expands to full evidence + latency + (M2) `StateReason`
  provenance + (M7) executor/trust badges and capability-proof link.
- Export writes JSON schema v1 to a share sheet (M2; M1 keeps a
  "Copy JSON" affordance if trivial — otherwise defer, no scope creep).

### 3.5 Executors (M7 — designed now, built later)

Enrollment wizard (QR scan / manual entry + fingerprint confirmation per
REMOTE-EXECUTOR-PROTOCOL §4), executor list with trust badges
(`UNVERIFIED`/`VERIFIED`/`SIGNED_REMOTE`), capability table per executor,
revoke action with confirmation. Delegated results in Results screen get
a distinct card tint + executor badge.

### 3.6 CapabilityExplainer (M5)

Reads the device's measured profile (M5 capability system) and renders
the matrix rows with basis text — the same data as the banner, full
detail. The "About" menu item (M1) shows the static M1 profile.

## 4. Visual Vocabulary (state chips, Material 3)

| PortState | Chip style | Semantics |
|---|---|---|
| OPEN | filled, primary/teal | verified response observed |
| CLOSED | outline, neutral | verified refusal observed |
| TIMEOUT | tinted, amber | no response within budget — *not* proof of closure |
| UNREACHABLE | tinted, orange | network-level error observed |
| INCONCLUSIVE | dashed outline, grey | observation ambiguous; evidence shown |

UDP states (M3): RESPONDED (teal) / NO_RESPONSE (amber, with explicit
"not closed — no response" caption) / APPLICATION_IDENTIFIED / INCONCLUSIVE.
Delegated rows (M7): chip + small executor badge; trust level colored
(VERIFIED = neutral, UNVERIFIED = amber warning).

Capability banner rows (from matrix §1 states): SUPPORTED → "Available";
LIMITED → "Limited" + constraint text; NOT_IMPLEMENTED → "Planned · Mx";
UNKNOWN → "Unverified — device experiment needed"; UNSUPPORTED →
"Requires capable executor" (+ delegate action when an executor exists).

## 5. Accessibility & Polish

- Dynamic type up to 200 % (Compose defaults); all evidence text
  selectable; chips ≥ 48 dp touch targets; color never the only signal
  (state text always accompanies chips); dark theme from M1; content
  descriptions on state chips ("Port 80: open, 12 milliseconds").

## 6. M1 Scope Line (no scope creep)

M1 implements: ScanInput, authorization dialog, Scanning, Results,
capability banner (static M1 profile), cancel, copy-JSON only if it
stays trivial. Deferred to their milestones: export sheet (M2), service
detail (M2), UDP chips (M3), CapabilityExplainer (M5), Executors (M7).
UI acceptance criteria remain PLAN §5.1.5; the banner-mapping function
is unit-tested against CAPABILITY-MATRIX §1 state table.

---

## References

- Plan UI checks: [`PLAN.md`](PLAN.md) §5.1.5, deliverables D7
- ViewModel contract: [`ARCHITECTURE.md`](ARCHITECTURE.md) §6
- Banner data source: [`CAPABILITY-MATRIX.md`](CAPABILITY-MATRIX.md)
- Delegation UX: [`REMOTE-EXECUTOR-PROTOCOL.md`](REMOTE-EXECUTOR-PROTOCOL.md)
