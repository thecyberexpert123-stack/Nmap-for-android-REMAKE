package org.nmapremake.core.model

import kotlinx.serialization.Serializable
import org.nmapremake.core.capability.ExecutorNode

/**
 * Aggregated result of one scan run — the JSON schema v1 envelope
 * (ARCHITECTURE §5): schemaVersion, generator, timestamps, the plan, the
 * executor that ran it, and per-host results.
 */
@Serializable
data class ScanReport(
    val schemaVersion: Int = 1,
    val generator: String,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val scanPlan: ScanPlan,
    val executor: ExecutorNode,
    val hosts: List<HostResult>,
)
