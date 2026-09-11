package org.nmapremake.core.capability

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutorType { ANDROID_LOCAL, NATIVE, REMOTE_LINUX, REMOTE_AGENT }

@Serializable
enum class ExecutorReachability { LOCAL, ONLINE, OFFLINE }

@Serializable
enum class TrustLevel { LOCAL, UNVERIFIED, VERIFIED, SIGNED_REMOTE }

/**
 * The executor that performed (or will perform) a probe.
 *
 * [discoveredVia] documents how the engine learned about the node and
 * [capabilityProof] records the evidence backing the node's claimed
 * capabilities — both are persisted into results so outputs stay auditable
 * and delegation stays honest (ARCHITECTURE §1, PLAN §2.3). Remote fields
 * (reachability ONLINE/OFFLINE, VERIFIED/SIGNED_REMOTE) become meaningful in
 * M5/M7; the M1 executor is local.
 */
@Serializable
data class ExecutorNode(
    val id: String,
    val label: String,
    val type: ExecutorType,
    val transport: String,
    val reachability: ExecutorReachability,
    val trustLevel: TrustLevel,
    val capabilities: CapabilityProfile,
    val discoveredVia: String? = null,
    val capabilityProof: String? = null,
)

object Executors {
    /** The local, built-in stock-Android executor (capability matrix column E1). */
    val LOCAL_ANDROID = ExecutorNode(
        id = "local-android",
        label = "This device (Android)",
        type = ExecutorType.ANDROID_LOCAL,
        transport = "direct",
        reachability = ExecutorReachability.LOCAL,
        trustLevel = TrustLevel.LOCAL,
        capabilities = CapabilityProfile.ANDROID_LOCAL_M1,
        discoveredVia = "built-in",
        capabilityProof = "Matrix E1: stock Android, no root. See docs/CAPABILITY-MATRIX.md",
    )
}
