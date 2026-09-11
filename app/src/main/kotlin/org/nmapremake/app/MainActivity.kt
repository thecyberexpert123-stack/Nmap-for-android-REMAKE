package org.nmapremake.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nmapremake.app.ui.NmapRemakeTheme
import org.nmapremake.app.ui.ScanScreen
import org.nmapremake.app.ui.ScanViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NmapRemakeTheme {
                val viewModel: ScanViewModel = viewModel()
                ScanScreen(viewModel)
            }
        }
    }
}
