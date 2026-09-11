package org.nmapremake.core.parse

import org.nmapremake.core.model.PortSpec
import org.nmapremake.core.model.ScanError
import org.nmapremake.core.model.ScanPlan

/**
 * Builds an M1 [ScanPlan] from raw UI input: one target text and one port
 * text. Numeric fields are typed values (the UI validates their format);
 * this parser validates text and clamps numbers via [ScanPlan.clamped].
 */
object PlanInputParser {
    data class Input(
        val target: String,
        val ports: String,
        val probeTimeoutMs: Long = ScanPlan.DEFAULT_PROBE_TIMEOUT_MS,
        val concurrency: Int = ScanPlan.DEFAULT_CONCURRENCY,
        val maxDurationMs: Long? = ScanPlan.DEFAULT_MAX_DURATION_MS,
    )

    fun parse(input: Input): ParseOutcome {
        val target =
            when (val outcome = TargetParser.parse(input.target)) {
                is TargetParser.ParseOutcome.Success -> outcome.target
                is TargetParser.ParseOutcome.Failure -> return ParseOutcome.Failure(outcome.error)
            }

        val portsText = input.ports.trim()
        val tcpPorts: PortSpec? =
            if (portsText.isEmpty()) {
                null
            } else {
                when (val outcome = PortSpecParser.parse(portsText)) {
                    is PortSpecParser.ParseOutcome.Failure -> return ParseOutcome.Failure(outcome.error)
                    is PortSpecParser.ParseOutcome.Success -> entriesToSpec(outcome.entries)
                }
            }

        val plan =
            ScanPlan(
                targets = listOf(target),
                tcpPorts = tcpPorts,
                probeTimeoutMs = input.probeTimeoutMs,
                concurrency = input.concurrency,
                maxDurationMs = input.maxDurationMs,
            ).clamped()
        return ParseOutcome.Success(plan)
    }

    private fun entriesToSpec(entries: List<PortSpecParser.PortEntry>): PortSpec {
        if (entries.size == 1) {
            return when (val entry = entries.single()) {
                is PortSpecParser.PortEntry.Single -> PortSpec.Single(entry.port)
                is PortSpecParser.PortEntry.Range -> PortSpec.Range(entry.first, entry.last)
                is PortSpecParser.PortEntry.Bulk -> if (entry.top) PortSpec.TopPorts else PortSpec.All
            }
        }
        val ports =
            entries.flatMap { entry ->
                when (entry) {
                    is PortSpecParser.PortEntry.Single -> listOf(entry.port)
                    is PortSpecParser.PortEntry.Range -> (entry.first..entry.last).toList()
                    is PortSpecParser.PortEntry.Bulk ->
                        if (entry.top) PortSpec.TopPorts.expand() else PortSpec.All.expand()
                }
            }
        return PortSpec.List(ports.distinct())
    }

    sealed interface ParseOutcome {
        data class Success(
            val plan: ScanPlan,
        ) : ParseOutcome

        data class Failure(
            val error: ScanError,
        ) : ParseOutcome
    }
}
