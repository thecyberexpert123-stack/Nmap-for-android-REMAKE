package org.nmapremake.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nmapremake.core.capability.Availability
import org.nmapremake.core.model.ErrorCode
import org.nmapremake.core.model.PortResult
import org.nmapremake.core.model.PortState

@Composable
fun ScanScreen(viewModel: ScanViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScanScreenContent(
        state = state,
        onTargetChange = viewModel::updateTarget,
        onPortsChange = viewModel::updatePorts,
        onTimeoutChange = viewModel::updateTimeout,
        onConcurrencyChange = viewModel::updateConcurrency,
        onStartClick = viewModel::startScan,
        onCancelClick = viewModel::cancelScan,
        onConfirmAuthorization = viewModel::confirmScan,
        onDismissAuthorization = viewModel::dismissAuthorization,
        onReset = viewModel::resetToInput,
    )
}

@Composable
fun ScanScreenContent(
    state: ScanUiState,
    onTargetChange: (String) -> Unit,
    onPortsChange: (String) -> Unit,
    onTimeoutChange: (String) -> Unit,
    onConcurrencyChange: (String) -> Unit,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
    onConfirmAuthorization: () -> Unit,
    onDismissAuthorization: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text("Nmap Android REMAKE", style = MaterialTheme.typography.titleLarge)
        Text(
            "Capability-honest network scanning for Android. Only unprivileged TCP " +
                "connect scans are available on this device — nothing is emulated.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.targetInput,
            onValueChange = onTargetChange,
            label = { Text("Target (IP or hostname)") },
            isError = state.targetError != null,
            supportingText = state.targetError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.portsInput,
            onValueChange = onPortsChange,
            label = { Text("Ports (e.g. 22,80,443 · 1-100 · top-100 · all)") },
            isError = state.portsError != null,
            supportingText = state.portsError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.timeoutInput,
                onValueChange = onTimeoutChange,
                label = { Text("Timeout (ms)") },
                isError = state.timeoutError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = state.concurrencyInput,
                onValueChange = onConcurrencyChange,
                label = { Text("Parallel probes") },
                isError = state.concurrencyError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))
        CapabilityBanner(state.capabilityBanner)
        Spacer(Modifier.height(12.dp))

        Button(onClick = onStartClick, enabled = state.canStart) {
            Text("Start scan")
        }

        when (state.phase) {
            ScanPhase.RUNNING, ScanPhase.CANCELLING -> ProgressSection(state, onCancelClick)
            ScanPhase.FINISHED -> FinishedSection(state, onReset)
            ScanPhase.FAILED -> FailedSection(state, onReset)
            ScanPhase.INPUT, ScanPhase.AUTHORIZING -> Unit
        }

        val showLiveResults =
            state.liveResults.isNotEmpty() &&
                state.phase in setOf(ScanPhase.RUNNING, ScanPhase.CANCELLING, ScanPhase.FAILED)
        if (showLiveResults) {
            Spacer(Modifier.height(16.dp))
            PortList(
                results = state.liveResults,
                header =
                    if (state.isIncomplete) {
                        "Partial results — incomplete: cancelled by user"
                    } else {
                        "Results so far"
                    },
            )
        }
        if (state.hosts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            PortList(
                results = state.hosts.flatMap { it.portResults },
                header = "Results",
            )
        }
    }

    val pendingPlan = state.pendingPlan
    if (state.phase == ScanPhase.AUTHORIZING && pendingPlan != null) {
        AuthorizationDialog(
            plan = pendingPlan,
            onConfirm = onConfirmAuthorization,
            onDismiss = onDismissAuthorization,
        )
    }
}

@Composable
private fun CapabilityBanner(rows: List<CapabilityRow>) {
    Column {
        Text("This device can and cannot do:", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (row in rows) {
                StateChip(
                    label = row.title,
                    color =
                        when (row.availability) {
                            Availability.SUPPORTED, Availability.LIMITED -> openGreen
                            Availability.NOT_IMPLEMENTED -> plannedAmber
                            Availability.UNSUPPORTED -> unavailableRed
                            Availability.UNKNOWN -> neutralGray
                        },
                    status = availabilityLabel(row.availability),
                )
            }
        }
    }
}

@Composable
private fun StateChip(
    label: String,
    color: Color,
    status: String,
) {
    Column(
        modifier =
            Modifier
                .background(color, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        Text(status, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ProgressSection(
    state: ScanUiState,
    onCancelClick: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Column {
        Text(
            "Scanning ${state.targetInput} — ${state.progress.finished}/${state.totalPorts} probes",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "${state.progress.open} open so far",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        val fraction =
            if (state.totalPorts > 0) {
                state.progress.finished.toFloat() / state.totalPorts
            } else {
                0f
            }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancelClick, enabled = state.phase == ScanPhase.RUNNING) {
            Text(if (state.phase == ScanPhase.CANCELLING) "Cancelling…" else "Cancel scan")
        }
    }
}

@Composable
private fun FinishedSection(
    state: ScanUiState,
    onReset: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Column {
        val total = state.hosts.sumOf { it.portResults.size }
        val open = state.hosts.sumOf { host -> host.portResults.count { it.state == PortState.OPEN } }
        Text(
            "Scan finished: $total ports probed, $open open.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onReset) { Text("New scan") }
    }
}

@Composable
private fun FailedSection(
    state: ScanUiState,
    onReset: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Column {
        val error = state.error
        Text(
            when {
                error == null -> "Scan failed."
                error.errorCode() == ErrorCode.SCAN_CANCELLED -> "Scan incomplete — cancelled by user."
                else -> "Scan failed: ${error.code} — ${error.message}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onReset) { Text("New scan") }
    }
}

@Composable
private fun PortList(
    results: List<PortResult>,
    header: String,
) {
    Column {
        Text(header, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        for (result in results) {
            PortRow(result)
        }
    }
}

@Composable
private fun PortRow(result: PortResult) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${result.port}/${result.protocol.name.lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            StateChip(
                label = result.state.name,
                color =
                    when (result.state) {
                        PortState.OPEN -> openGreen
                        PortState.CLOSED -> closedRed
                        PortState.TIMEOUT -> plannedAmber
                        PortState.UNREACHABLE, PortState.INCONCLUSIVE -> neutralGray
                    },
                status = result.latencyMs?.let { "$it ms" } ?: "no response",
            )
        }
        Text(result.evidence, style = MaterialTheme.typography.bodySmall)
        val error = result.error
        if (error != null) {
            Text(
                "${error.code}: ${error.message}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun availabilityLabel(availability: Availability): String =
    when (availability) {
        Availability.SUPPORTED -> "available"
        Availability.LIMITED -> "limited"
        Availability.NOT_IMPLEMENTED -> "planned"
        Availability.UNSUPPORTED -> "not available"
        Availability.UNKNOWN -> "unknown"
    }

private val openGreen = Color(0xFF2E7D32)
private val closedRed = Color(0xFFC62828)
private val plannedAmber = Color(0xFFB26A00)
private val unavailableRed = Color(0xFF8E2E2E)
private val neutralGray = Color(0xFF5F6368)
