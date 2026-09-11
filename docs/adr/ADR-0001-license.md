# ADR-0001: License for clean-room code

- **Date:** 2026-09-11
- **Status:** Accepted (stakeholder decision)
- **Deciders:** Project owner (via structured approval), Agent

## Context

The project is a clean-room, Kotlin-first Android scanner inspired by Nmap's
capabilities. Verified research (see PLAN.md §3.1) established that Nmap itself has
been licensed under the **Nmap Public Source License (NPSL)** — based on GPLv2 with
extra conditions — since release 7.90 (October 2020), with earlier releases under
GPLv2. NPSL is rejected as non-free by at least Fedora. We copy **no** Nmap code or
data files, so we are free to choose our own license, but the choice affects the
future path for optionally redistributing a real Nmap executable (Phase 7+).

Options considered:

| Option | Pros | Cons |
|---|---|---|
| GPL-2.0-or-later | Ecosystem-aligned with Nmap's copyleft world; simplest compatibility path for redistributing GPLv2/NPSL components alongside | Copyleft obligations for downstream users |
| GPL-3.0-or-later | Stronger copyleft (patent, anti-tivoization) | Mixing with GPLv2-only components (e.g., any future Nmap-derived parts) is legally tricky |
| Apache-2.0 | Permissive; broad reuse | Weakest protection against proprietary forks of the scanner |
| Defer | No friction now | Legal ambiguity for contributors and future redistribution |

## Decision

License this repository's original code under **GPL-2.0-or-later** (SPDX:
`GPL-2.0-or-later`). The verbatim GPLv2 text (whose section 9 grants the
"any later version" option) is committed as `LICENSE`, sourced from the official
SPDX license-list-data repository.

## Consequences

- Every new source file carries the standard GPLv2-or-later header once code exists.
- Copyright holder line is **TBD** and must be set by the project owner before any
  release; it is not fabricated by the agent.
- If Phase 7+ redistributes a real Nmap binary, its NPSL/GPLv2 terms apply to that
  component separately; this will be re-evaluated at that milestone with a fresh ADR.
- Downstream users must comply with copyleft when distributing modifications.
