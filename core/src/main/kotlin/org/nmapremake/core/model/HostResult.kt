package org.nmapremake.core.model

import kotlinx.serialization.Serializable
import org.nmapremake.core.capability.CapabilityProfile
import org.nmapremake.core.capability.ExecutorNode

@Serializable
data class HostResult(
    val target: Target,
    val portResults: List<PortResult>,
    val executor: ExecutorNode,
    val capabilities: CapabilityProfile,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
)
