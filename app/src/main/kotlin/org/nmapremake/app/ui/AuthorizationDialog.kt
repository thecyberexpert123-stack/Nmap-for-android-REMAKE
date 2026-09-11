package org.nmapremake.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import org.nmapremake.core.model.ScanPlan

/**
 * Consent gate required before every scan (PLAN §5.1.5): shows exactly what
 * will be probed and requires the user to affirm authorization before Start
 * becomes enabled.
 */
@Composable
fun AuthorizationDialog(
    plan: ScanPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var acknowledged by remember { mutableStateOf(false) }
    val target = plan.targets.firstOrNull()?.label.orEmpty()
    val ports = plan.tcpPorts?.expand()?.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Authorize scan") },
        text = {
            Column {
                Text(
                    "You are about to connect to: $target",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Ports to probe: ${ports ?: "none (TCP pass skipped)"}, " +
                        "timeout ${plan.probeTimeoutMs} ms, ${plan.concurrency} parallel probes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Only the TCP connect scan is available on this device. " +
                        "Scanning hosts you do not own or lack permission for may be illegal. " +
                        "Proceed only for systems you are authorized to test.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = { acknowledged = it },
                    )
                    Text(
                        "I confirm I own this target or have permission to scan it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = acknowledged) {
                Text("Authorize and start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
