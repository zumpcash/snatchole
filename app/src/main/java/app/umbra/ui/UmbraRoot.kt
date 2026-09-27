package app.umbra.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.umbra.ui.theme.UmbraTheme
import app.umbra.wallet.WalletUiState

@Composable
fun UmbraRoot(
    state: UmbraState,
    onHolePress: () -> Unit,
    onLibraryOpen: () -> Unit,
    onLibraryClose: () -> Unit,
    onPaywallOpen: () -> Unit,
    onPaywallClose: () -> Unit,
    onPaySol: () -> Unit,
    onPayUsdc: () -> Unit,
    onRestore: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = remember { MainViewModel().also { it.observePremium(context) } }
    val walletState by viewModel.walletViewModel.uiState.collectAsState()

    UmbraTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            HoleScreen(
                state = state,
                onHolePress = onHolePress,
                onLibraryOpen = onLibraryOpen,
                onAdsPress = onPaywallOpen,
            )

            AnimatedVisibility(
                visible = state.showLibrary,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                LibraryScreen(onClose = onLibraryClose)
            }

            AnimatedVisibility(
                visible = state.showPaywall,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                PaywallSheet(
                    walletState = walletState,
                    onPaySol = onPaySol,
                    onPayUsdc = onPayUsdc,
                    onRestore = onRestore,
                    onDismiss = onPaywallClose,
                )
            }
        }
    }
}
