package org.nmapremake.engine

import org.nmapremake.core.capability.Capability
import org.nmapremake.core.capability.ExecutorNode
import org.nmapremake.core.capability.Executors
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.ScanPlan

/**
 * Selects the executor for a plan (ARCHITECTURE §3/§4.4). M1 ships the local
 * executor only; delegation routing (M5/M7) will extend this interface with
 * reachability + capability-proof selection rules.
 */
interface ExecutionRouter {
    fun route(plan: ScanPlan): ExecutorSelection
}

sealed interface ExecutorSelection {
    data class Selected(
        val executor: ExecutorNode,
    ) : ExecutorSelection

    data class Rejected(
        val missing: List<Capability>,
        val error: ScanError,
    ) : ExecutorSelection
}

/**
 * M1 router: the local stock-Android executor. Rejects plans whose required
 * capabilities the executor does not actually support — capability claims are
 * never silently downgraded or faked (PLAN §2.3).
 */
class LocalExecutionRouter(
    private val executor: ExecutorNode = Executors.LOCAL_ANDROID,
) : ExecutionRouter {
    override fun route(plan: ScanPlan): ExecutorSelection {
        val required = plan.requiredCapabilities()
        val missing =
            required
                .filterNot { executor.capabilities.supports(it) }
                .sortedBy { it.name }
        return if (missing.isEmpty()) {
            ExecutorSelection.Selected(executor)
        } else {
            ExecutorSelection.Rejected(
                missing = missing,
                error =
                    ScanError(
                        ErrorCode.CAPABILITY_UNSUPPORTED,
                        "local executor cannot run this plan; missing: " +
                            missing.joinToString(", ") { it.name },
                    ),
            )
        }
    }
}
