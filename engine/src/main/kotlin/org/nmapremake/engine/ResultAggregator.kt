package org.nmapremake.engine

import org.nmapremake.core.capability.ExecutorNode
import org.nmapremake.core.model.HostResult
import org.nmapremake.core.model.ScanPlan
import org.nmapremake.core.model.ScanReport

/** Assembles the schema-v1 [ScanReport] envelope (ARCHITECTURE §5). */
class ResultAggregator {
    fun aggregate(
        plan: ScanPlan,
        executor: ExecutorNode,
        hostResults: List<HostResult>,
        startedAtEpochMs: Long,
        finishedAtEpochMs: Long,
    ): ScanReport = ScanReport(
        schemaVersion = 1,
        generator = GENERATOR,
        startedAtEpochMs = startedAtEpochMs,
        finishedAtEpochMs = finishedAtEpochMs,
        scanPlan = plan,
        executor = executor,
        hosts = hostResults,
    )

    companion object {
        const val GENERATOR = "nmap-android-remake/engine/0.1.0"
    }
}
