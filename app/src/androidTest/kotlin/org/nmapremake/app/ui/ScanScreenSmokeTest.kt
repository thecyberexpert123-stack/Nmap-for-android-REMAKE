package org.nmapremake.app.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.nmapremake.core.capability.CapabilityProfile

/**
 * Minimal render smoke test for the emulator matrix (CI §5.1.6): the input
 * screen renders, the capability banner is data-driven, and Start stays
 * disabled until inputs are valid.
 */
class ScanScreenSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `scan_screen_renders_inputs_banner_and_disabled_start_button`() {
        composeRule.setContent {
            NmapRemakeTheme {
                ScanScreenContent(
                    state =
                        ScanUiState(
                            capabilityBanner = profileToBanner(CapabilityProfile.ANDROID_LOCAL_M1),
                        ),
                    onTargetChange = {},
                    onPortsChange = {},
                    onTimeoutChange = {},
                    onConcurrencyChange = {},
                    onStartClick = {},
                    onCancelClick = {},
                    onConfirmAuthorization = {},
                    onDismissAuthorization = {},
                    onReset = {},
                )
            }
        }
        composeRule.onNodeWithText("Target (IP or hostname)").assertExists()
        composeRule.onNodeWithText("Start scan").assertExists().assertIsNotEnabled()
        composeRule.onNodeWithText("TCP connect scan").assertExists()
    }
}
