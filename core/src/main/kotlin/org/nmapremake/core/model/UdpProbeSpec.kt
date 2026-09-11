package org.nmapremake.core.model

import kotlinx.serialization.Serializable

/**
 * Planned Phase 3 (M3) application-level UDP probe. In M1 the engine always
 * runs with an empty list, which skips the UDP pass entirely; any non-empty
 * list makes the plan require [org.nmapremake.core.capability.Capability.UDP_APP_PROBES],
 * which the local M1 executor does not support — so a non-empty list is
 * rejected by routing, never silently attempted. Exact fields are finalized
 * during M3 research.
 */
@Serializable
data class UdpProbeSpec(
    val payloadHex: String,
)
