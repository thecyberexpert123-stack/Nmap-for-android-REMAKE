# REMOTE EXECUTOR PROTOCOL — M7 Design (Threat Model + Protocol)

> Status: **Design (docs-only phase)** — no code. This is the reference
> design for PLAN milestone M7 (remote executor / capability delegation).
> It implements the adopted delegation architecture
> (NMAP-SUBSYSTEMS-DEEP §2) and the capability matrix's rule that
> executor claims are never trusted without proof.
>
> Design-honesty note: this is *design*, not an implementation. At the M7
> design gate it must be re-reviewed against current TLS/authentication
> best practices and the chosen libraries; every security claim below is
> a requirement to be verified at implementation time (rule #21).

---

## 1. Goals and Non-Goals

**Goals**
- Let the Android app delegate scan intents it cannot execute locally to
  an authorized, capable executor (LAN agent, appliance, VPN endpoint,
  cloud Linux) — capability delegation, not privilege escalation.
- Provide **evidence**: every delegated result carries executor identity,
  trust level, and capability proof.
- Constrain what an executor may do: structured scan plans only, within
  the user's authorized scope — never arbitrary command execution.

**Non-goals** (M7 does not build)
- A multi-tenant scanning service or public API.
- Executor-to-executor federation.
- Local raw-packet emulation (that is M6's territory).

## 2. Threat Model

Actors: **User** (operates the app), **App**, **Executor** (trusted or
untrusted), **Network attacker** (passive/active on any hop), **Compromised
executor**, **Third parties** (scan targets and their operators).

| # | Threat | Control | Residual risk / notes |
|---|---|---|---|
| T1 | Eavesdropping on plan/results | Mutual TLS 1.3 (AEAD), pinned executor certificate | None if pins intact |
| T2 | MITM impersonating an executor | mTLS + certificate **pinning by fingerprint** (TOFU with out-of-band fingerprint confirmation, SSH-style) | User must verify fingerprint at enrollment; app must warn on pin change |
| T3 | Replay of captured traffic | TLS 1.3 anti-replay + per-request `requestId` + monotonic `nonce`; executor rejects duplicates | TLS-level protection is inherent; app-level nonce protects the delegation layer |
| T4 | Unauthorized executor enrollment | Enrollment tokens: single-use, short-lived (15 min), delivered out-of-band (QR / manual entry); certificate binding at enrollment | Token theft before use is the exposure window |
| T5 | Malicious executor lies about capabilities | **Capability proof**: executor signs a structured sample result demonstrating the capability at enrollment and periodically (see §5.4) | Proof shows capability *was* demonstrated; freshness matters |
| T6 | Malicious executor lies about results (tampered/fabricated output) | Results signed by executor key; schema is versioned and strictly validated; results tagged with `trustLevel`; app shows provenance | A *trusted* executor that goes rogue can still lie — signing proves authorship, not truthfulness. Documented honestly in the UI |
| T7 | Executor exceeds authorization (scans targets the user didn't authorize) | Server-side policy enforcement: executor only runs plans whose targets/ports/rate are within the scope configured for the enrolled app identity; executor-side scan-policy allowlist | Defense lives on the executor (the app is the client) |
| T8 | Command/argument injection via delegated scans | The wire format is a **structured `ScanPlan`** (targets, ports, techniques, limits) — never a raw CLI string. The executor's nmap-wrapper builds arguments from a fixed, validated template | Strong typing + allowlists; no shell interpolation |
| T9 | Denial of service on the executor | Per-app rate limits, max-concurrency, max-duration caps enforced executor-side | A malicious app can still starve others; quotas mitigate |
| T10 | Compromised executor leaks results/credentials | Least privilege: executor holds only its own key + per-app enrollment tokens; no app credentials flow to executors; tokens revocable | App revokes trust (T11) |
| T11 | App needs to stop trusting an executor | **Revocation**: app deletes pin + tokens (immediate, offline); executor revokes app tokens; CRL-style list deferred (2-node trust) | 2-node trust keeps revocation simple |
| T12 | Stolen device replays app identity | App keystore (Android Keystore) holds the client key non-exportable; enrollment tokens short-lived | Device theft = executor trusts that app identity until revoked — document, allow manual revocation per-app on executor |

**Accepted risks (documented):** signing proves authorship, not
truthfulness (T6); TOFU enrollment requires user vigilance at first
contact (T2); 2-node trust model has no cross-executor PKI (acceptable
at M7 scope).

## 3. Trust Model

```text
TrustLevel = UNVERIFIED | VERIFIED | SIGNED_REMOTE
- UNVERIFIED  : enrolled by token only, no pinned certificate yet
- VERIFIED    : certificate fingerprint pinned (SSH-TOFU equivalent) — default working level
- SIGNED_REMOTE: certificate signed by a user-operated CA (optional, post-M7)
```
Every result envelope carries the executor's `TrustLevel`; the UI
renders it. Results from `UNVERIFIED` executors are marked accordingly
and cannot influence capability promotions.

## 4. Enrollment Flow

```text
1. Executor: generate keypair; bind self-signed cert; display QR
   (endpoint URL + cert fingerprint + one-time token) or manual entry.
2. App: scans QR / user types values; user confirms fingerprint.
3. App → Executor: mTLS handshake; app presents client cert; sends
   EnrollmentRequest{token, appName, publicIdentity}.
4. Executor: validates single-use token (15 min TTL); pins app cert;
   issues appId + per-app quota policy; replies with signed
   ExecutorProfile{id, type, capabilities, protocolVersion, nonce}.
5. App: pins executor fingerprint; stores enrollment locally
   (Android Keystore for the client key).
TrustLevel: UNVERIFIED → VERIFIED on successful pin confirmation.
```

## 5. Protocol (versioned, JSON over TLS)

### 5.1 Wire principles
- Versioned envelope: `{protoVersion: 1, requestId, nonce, kind, payload, signature}`.
- All payloads are our own JSON schema (extensions of schema v1,
  ARCHITECTURE §5) — never free-form strings from the client.
- Signature = Ed25519 (or equivalent) over the canonical payload bytes.

### 5.2 Messages (v1)

| Message | Direction | Payload | Notes |
|---|---|---|---|
| `hello` | app → executor | supported proto versions, appId | version negotiation |
| `profile` | executor → app | `ExecutorProfile` (id, type, capabilities, trust, quotas) | signed; cached by app |
| `plan` | app → executor | `ScanPlan` (structured), `authorization` (scope the user confirmed in-app) | executor validates against its policy + app quotas |
| `progress` | executor → app | `ProgressEvent` stream (chunked) | mirrors local engine events |
| `result` | executor → app | versioned `ScanReport` + executor identity + trust + **capability proof ref** | signed |
| `cancel` | app → executor | `requestId` | executor stops promptly, returns partial report |
| `revoke` | either | appId / executorId | trust teardown (T11) |
| `proof` | executor → app | signed capability sample | see §5.4 |

### 5.3 Capability negotiation
1. Executor advertises `CapabilityProfile` in `profile`.
2. App computes required capabilities from the `ScanPlan` (matrix §5).
3. If any required capability is not `SUPPORTED`/`LIMITED` (and not
   covered by a valid `proof`), the app does **not** send the plan —
   same router rule as locally (never fake).
4. Executor re-validates and executes only intents within its advertised
   capability set and the app's authorized scope.

### 5.4 Capability proof
A capability is "proven" when the executor returns a **signed sample
result** demonstrating it against the executor's own reference target
(e.g., a local loopback raw-transmit test for `RAW_PACKET_TRANSMIT`).
Proofs carry a freshness window (e.g., 24 h) and are required for any
capability above the executor's baseline; expired/missing proof ⇒ the
capability degrades to `UNKNOWN` in the app's view.

### 5.5 Authorization (least privilege)
- The executor holds a per-app **scan policy**: allowed target ranges,
  port limits, technique allowlist, rate/concurrency caps, max duration.
- The app sends the user's in-app authorization scope with each plan;
  executor enforces `scope ∩ policy`.
- The nmap-wrapper (reference executor implementation) builds arguments
  from a fixed template over the structured plan — no client strings
  reach the shell (T8).

## 6. Transport Options

| Transport | Use | Notes |
|---|---|---|
| Direct mTLS (LAN/WAN) | LAN agent, cloud Linux | primary path |
| VpnService tunnel carrying the same protocol | VPN-endpoint executor | transport only — per architectural invariants, the tunnel grants nothing |

Both use the identical wire protocol; only the socket's origin differs.

## 7. Acceptance Criteria Mapping (updates PLAN M7)

The PLAN M7 row is extended with this document's requirements:
- Enrollment flow implemented and exercised by two-JVM integration tests
  (happy path + expired token + wrong fingerprint + revoked trust).
- Threat table T1–T12: each control implemented or explicitly waived
  with the waiver recorded (no silent omissions).
- Replay test: duplicated `requestId` rejected by executor.
- Injection test: a plan containing shell metacharacters in every string
  field produces no executor-side command execution (fixed template).
- Capability-proof test: capability without valid proof refuses
  delegation; stale proof degrades to UNKNOWN.
- Signed results verify in the app; tampered payload rejected.

## 8. Open Questions (M7 design gate)

1. Ed25519 vs RSA/ECDSA for executor identity — decided with the
   chosen TLS/auth libraries.
2. Whether `SIGNED_REMOTE` (user CA) is in M7 or deferred.
3. Reference executor implementation language (reuse our JVM engine vs
   wrap genuine nmap) — re-opens the NPSL discussion for the wrapper
   distribution; stakeholder decision at M7.
4. Proof freshness window (24 h proposed).

---

## References

- Delegation architecture & invariants: [`NMAP-SUBSYSTEMS-DEEP.md`](NMAP-SUBSYSTEMS-DEEP.md) §2
- Capability rules: [`CAPABILITY-MATRIX.md`](CAPABILITY-MATRIX.md) §5–§7
- Result schema: [`ARCHITECTURE.md`](ARCHITECTURE.md) §5
- PLAN milestone M7: [`PLAN.md`](PLAN.md) §5.2
