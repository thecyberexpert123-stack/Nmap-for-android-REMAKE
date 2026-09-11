package org.nmapremake.app.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.nmapremake.core.model.PortState

/**
 * Full-app flow on a cloud emulator (CI §5.1.6): the real MainActivity
 * screen state machine driven by hand, scanning real TCP listeners that the
 * CI job starts on the runner host (reachable from the emulator as
 * 10.0.2.2). Ports 18080/18443 listen (OPEN); 18081/18082 do not (CLOSED).
 */
class ScanEndToEndTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app runs a real connect scan against the CI host and renders honest results`() {
        val viewModel = ScanViewModel()
        composeRule.setContent {
            NmapRemakeTheme { ScanScreen(viewModel) }
        }

        composeRule.onNodeWithText("Target (IP or hostname)").performTextInput("10.0.2.2")
        composeRule.onNodeWithText("Ports (e.g. 22,80,443 · 1-100 · top-100 · all)")
            .performTextReplacement("18080,18081,18443,18082")

        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(hasText("Start scan") and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Start scan").performClick()

        // Consent gate: the scan must not start until authorized.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Authorize scan")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("Authorize and start").performClick()

        // Real scan of 4 ports (probe timeout 5 s each, run in parallel).
        composeRule.waitUntil(timeoutMillis = 120_000) {
            composeRule.onAllNodes(hasText("Scan finished", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodesWithText("OPEN").assertCountEquals(2)
        composeRule.onAllNodesWithText("CLOSED").assertCountEquals(2)
        composeRule.onAllNodes(hasText("TCP connect completed in", substring = true))
            .assertCountEquals(2)
        composeRule.onAllNodes(hasText("Connection refused (ECONNREFUSED)", substring = true))
            .assertCountEquals(2)

        val results = viewModel.state.value.hosts.single().portResults.associateBy { it.port }
        assertEquals(PortState.OPEN, results.getValue(18080).state)
        assertEquals(PortState.CLOSED, results.getValue(18081).state)
        assertEquals(PortState.OPEN, results.getValue(18443).state)
        assertEquals(PortState.CLOSED, results.getValue(18082).state)
        assertNotNull(results.getValue(18080).latencyMs)
        for (result in results.values) {
            Log.i(
                "ScanE2E",
                "port ${result.port}: state=${result.state} latencyMs=${result.latencyMs} " +
                    "error=${result.error?.code} evidence=\"${result.evidence}\"",
            )
        }

        // Visual evidence for the GitHub Actions artifact.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = composeRule.onRoot().captureToImage()
        FileOutputStream(File(context.filesDir, "scan-results.png")).use { out ->
            screenshot.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Log.i("ScanE2E", "screenshot written to files/scan-results.png")
    }
}
