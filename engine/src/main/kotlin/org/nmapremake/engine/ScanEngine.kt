package org.nmapremake.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.model.Target

/** The seam the UI drives: one plan in, a progress stream out. */
interface ScanRunner {
    fun scan(plan: ScanPlan): Flow<ProgressEvent>

    /** Cooperative cancellation of the active scan (aborts in-flight connects). */
    fun cancel()
}

/**
 * M1 scan engine: routes the plan, resolves targets, then streams the
 * scheduler's progress events. Failures before scheduling surface as a
 * single [ProgressEvent.ScanFailed].
 */
class ScanEngine(
    private val router: ExecutionRouter,
    private val resolver: HostResolver,
    private val scheduler: ScanScheduler,
    private val transport: TcpTransport,
    private val cancelDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ScanRunner {
    private val cancelScope = CoroutineScope(SupervisorJob() + cancelDispatcher)

    override fun scan(plan: ScanPlan): Flow<ProgressEvent> =
        flow {
            val clamped = plan.clamped()
            val selection = router.route(clamped)
            if (selection is ExecutorSelection.Rejected) {
                emit(ProgressEvent.ScanFailed(selection.error))
                return@flow
            }
            val executor = (selection as ExecutorSelection.Selected).executor

            val resolvedTargets = mutableListOf<Target>()
            for (target in clamped.targets) {
                try {
                    resolvedTargets += resolver.resolve(target)
                } catch (e: UnresolvedHostException) {
                    emit(ProgressEvent.ScanFailed(e.error))
                    return@flow
                }
            }

            emitAll(scheduler.scan(clamped.copy(targets = resolvedTargets), executor, transport))
        }

    override fun cancel() {
        cancelScope.launch { scheduler.cancel() }
    }
}
