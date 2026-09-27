package app.umbra.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.umbra.download.DownloadWorker
import app.umbra.resolver.LinkInspector
import app.umbra.resolver.VideoResolver
import app.umbra.wallet.PremiumState
import app.umbra.wallet.PremiumStore
import app.umbra.wallet.WalletViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class DownloadPhase { RESOLVE, DOWNLOAD, DONE, ERROR }

data class UmbraState(
    val busy: Boolean = false,
    val phase: DownloadPhase? = null,
    val progress: Int? = null,
    val errorMessage: String? = null,
    val lastTitle: String? = null,
    val showLibrary: Boolean = false,
    val showPaywall: Boolean = false,
    val premium: PremiumState? = null,
    val injectedUrl: String? = null,
)

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(UmbraState())
    val state: StateFlow<UmbraState> = _state.asStateFlow()

    val walletViewModel = WalletViewModel()

    fun observePremium(context: Context) {
        viewModelScope.launch {
            PremiumStore.flow(context).collect { premium ->
                _state.update { it.copy(premium = premium) }
            }
        }
    }

    fun injectUrl(url: String) {
        _state.update { it.copy(injectedUrl = url) }
    }

    fun onHolePress(context: Context) {
        if (_state.value.busy) return

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val raw = _state.value.injectedUrl
            ?: clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
            ?: return reportError("Panoda video bağlantısı bulunamadı.")

        val seen = LinkInspector.extractFromText(raw)
            ?: LinkInspector.inspect(raw)?.url
            ?: return reportError("Geçerli video bağlantısı bulunamadı.")

        _state.update { it.copy(busy = true, phase = DownloadPhase.RESOLVE, progress = null, errorMessage = null, injectedUrl = null) }

        viewModelScope.launch {
            try {
                val media = VideoResolver.resolve(seen)
                _state.update { it.copy(phase = DownloadPhase.DOWNLOAD, lastTitle = media.title) }

                val workRequest = DownloadWorker.buildRequest(
                    mediaUrl = media.mediaUrl,
                    title = media.title,
                    mimeType = media.mimeType,
                    referer = media.referer ?: VideoResolver.refererFor(media.mediaUrl),
                    userAgent = media.userAgent ?: VideoResolver.userAgentFor(media.mediaUrl),
                )
                WorkManager.getInstance(context).enqueue(workRequest)

                _state.update { it.copy(busy = false, phase = DownloadPhase.DONE) }

                // Reset after brief delay
                kotlinx.coroutines.delay(2000)
                _state.update { it.copy(phase = null) }

            } catch (e: Exception) {
                reportError(e.message ?: "İndirme başarısız.")
            }
        }
    }

    fun showLibrary() = _state.update { it.copy(showLibrary = true) }
    fun hideLibrary() = _state.update { it.copy(showLibrary = false) }
    fun showPaywall() = _state.update { it.copy(showPaywall = true) }
    fun hidePaywall() = _state.update { it.copy(showPaywall = false) }

    fun payWithSol(context: Context) {
        val sender = (context as? FragmentActivity)?.let {
            com.solana.mobilewalletadapter.clientlib.ActivityResultSender(it)
        } ?: return reportError("Aktivite bulunamadı.")
        walletViewModel.payWithSol(context, sender)
        observeWalletState(context)
    }

    fun payWithUsdc(context: Context) {
        val sender = (context as? FragmentActivity)?.let {
            com.solana.mobilewalletadapter.clientlib.ActivityResultSender(it)
        } ?: return reportError("Aktivite bulunamadı.")
        walletViewModel.payWithUsdc(context, sender)
        observeWalletState(context)
    }

    fun restore(context: Context) {
        val sender = (context as? FragmentActivity)?.let {
            com.solana.mobilewalletadapter.clientlib.ActivityResultSender(it)
        } ?: return reportError("Aktivite bulunamadı.")
        walletViewModel.restorePremium(context, sender)
        observeWalletState(context)
    }

    private fun observeWalletState(context: Context) {
        viewModelScope.launch {
            walletViewModel.uiState.collect { ws ->
                when (ws) {
                    is app.umbra.wallet.WalletUiState.Success -> {
                        PremiumStore.save(context, ws.premium)
                        _state.update { it.copy(premium = ws.premium, showPaywall = false) }
                    }
                    is app.umbra.wallet.WalletUiState.Error -> {
                        _state.update { it.copy(errorMessage = ws.message) }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun reportError(msg: String) {
        _state.update { it.copy(busy = false, phase = DownloadPhase.ERROR, errorMessage = msg) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(3500)
            _state.update { it.copy(phase = null, errorMessage = null) }
        }
    }
}
