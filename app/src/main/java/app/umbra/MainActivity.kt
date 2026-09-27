package app.umbra

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.umbra.ui.MainViewModel
import app.umbra.ui.UmbraRoot

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            val state by viewModel.state.collectAsState()
            UmbraRoot(
                state = state,
                onHolePress = { viewModel.onHolePress(this) },
                onLibraryOpen = { viewModel.showLibrary() },
                onLibraryClose = { viewModel.hideLibrary() },
                onPaywallOpen = { viewModel.showPaywall() },
                onPaywallClose = { viewModel.hidePaywall() },
                onPaySol = { viewModel.payWithSol(this) },
                onPayUsdc = { viewModel.payWithUsdc(this) },
                onRestore = { viewModel.restore(this) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val url = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            viewModel.injectUrl(url)
        }
    }
}
