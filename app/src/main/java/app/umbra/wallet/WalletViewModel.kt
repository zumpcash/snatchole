package app.umbra.wallet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.RpcCluster
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

// ─── IMPORTANT: Set your treasury wallet address here ──────────────────────
const val TREASURY_ADDRESS = "YOUR_SOLANA_TREASURY_ADDRESS_HERE"
// ────────────────────────────────────────────────────────────────────────────

private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000
private const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
private const val USDC_RAW = 1_000_000L

sealed class WalletUiState {
    object Idle : WalletUiState()
    object Loading : WalletUiState()
    data class Error(val message: String) : WalletUiState()
    data class Success(val premium: PremiumState) : WalletUiState()
}

class WalletViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<WalletUiState>(WalletUiState.Idle)
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val mwa = MobileWalletAdapter(
        connectionIdentity = com.solana.mobilewalletadapter.clientlib.ConnectionIdentity(
            identityUri = Uri.parse("https://umbra.app"),
            iconUri = Uri.parse("https://umbra.app/icon.png"),
            identityName = "UMBRA",
        ),
        rpcCluster = RpcCluster.MainnetBeta,
    )

    fun payWithSol(context: Context, activityResultSender: ActivityResultSender) {
        viewModelScope.launch {
            _uiState.value = WalletUiState.Loading
            try {
                val lamports = SolanaRpc.lamportsFor1Usd()
                val (blockhash, lastValid) = SolanaRpc.getLatestBlockhash()

                val result = mwa.transact(activityResultSender) {
                    val auth = authorize(
                        identityUri = Uri.parse("https://umbra.app"),
                        iconUri = Uri.parse("https://umbra.app/icon.png"),
                        identityName = "UMBRA",
                        cluster = RpcCluster.MainnetBeta,
                    )
                    val senderKey = auth.publicKey
                    val tx = buildSolTransferTx(
                        fromPubkey = senderKey,
                        toPubkey = Base58.decode(TREASURY_ADDRESS),
                        lamports = lamports,
                        blockhash = Base58.decode(blockhash),
                    )
                    signAndSendTransactions(arrayOf(tx))
                }

                when (result) {
                    is TransactionResult.Success -> {
                        val sig = Base58.encode(result.payload.signatures[0])
                        val premium = verifyAndSavePremium(context, sig, "SOL", result.authResult.publicKey)
                        _uiState.value = WalletUiState.Success(premium)
                    }
                    is TransactionResult.Failure -> throw RuntimeException("Cüzdan işlemi reddetti: ${result.e.message}")
                    else -> throw RuntimeException("İmza tamamlanamadı.")
                }
            } catch (e: Exception) {
                _uiState.value = WalletUiState.Error(friendlyError(e))
            }
        }
    }

    fun payWithUsdc(context: Context, activityResultSender: ActivityResultSender) {
        viewModelScope.launch {
            _uiState.value = WalletUiState.Loading
            try {
                val (blockhash, _) = SolanaRpc.getLatestBlockhash()
                // USDC SPL transfer (simplified — use spl-token program)
                // For now redirect to SOL payment as USDC requires ATA creation
                // Full USDC support: build SPL transfer instruction manually
                val result = mwa.transact(activityResultSender) {
                    authorize(
                        identityUri = Uri.parse("https://umbra.app"),
                        iconUri = Uri.parse("https://umbra.app/icon.png"),
                        identityName = "UMBRA",
                        cluster = RpcCluster.MainnetBeta,
                    )
                    // Build USDC transfer tx here — requires ATA fetch
                    // Placeholder: fail gracefully
                    throw RuntimeException("USDC yakında. Şimdilik SOL ile ödeme yap.")
                }
            } catch (e: Exception) {
                _uiState.value = WalletUiState.Error(friendlyError(e))
            }
        }
    }

    fun restorePremium(context: Context, activityResultSender: ActivityResultSender) {
        viewModelScope.launch {
            _uiState.value = WalletUiState.Loading
            try {
                val lamports = SolanaRpc.lamportsFor1Usd()
                val result = mwa.transact(activityResultSender) {
                    authorize(
                        identityUri = Uri.parse("https://umbra.app"),
                        iconUri = Uri.parse("https://umbra.app/icon.png"),
                        identityName = "UMBRA",
                        cluster = RpcCluster.MainnetBeta,
                    )
                }
                val walletKey = when (result) {
                    is TransactionResult.Success -> Base58.encode(result.authResult.publicKey)
                    else -> throw RuntimeException("Cüzdan bağlanamadı.")
                }
                val premium = scanForPremium(context, walletKey, lamports)
                    ?: throw RuntimeException("Bu cüzdanda geçerli ödeme bulunamadı.")
                PremiumStore.save(context, premium)
                _uiState.value = WalletUiState.Success(premium)
            } catch (e: Exception) {
                _uiState.value = WalletUiState.Error(friendlyError(e))
            }
        }
    }

    private suspend fun verifyAndSavePremium(
        context: Context,
        signature: String,
        asset: String,
        publicKey: ByteArray,
    ): PremiumState {
        val walletAddr = Base58.encode(publicKey)
        var tx: JsonObject? = null
        repeat(4) {
            tx = SolanaRpc.getParsedTransaction(signature)
            if (tx != null) return@repeat
            kotlinx.coroutines.delay(700)
        }
        tx ?: throw RuntimeException("İşlem zincirde henüz görünmüyor. Birazdan Geri Yükle'ye bas.")

        val blockTime = tx!!["blockTime"]?.jsonPrimitive?.longOrNull
            ?: throw RuntimeException("İşlem zamanı okunamadı.")
        val paidAt = blockTime * 1000
        val expiresAt = paidAt + YEAR_MS

        val premium = PremiumState(walletAddr, signature, asset, paidAt, expiresAt)
        PremiumStore.save(context, premium)
        return premium
    }

    private suspend fun scanForPremium(context: Context, walletAddr: String, lamports: Long): PremiumState? {
        val sigs = SolanaRpc.getSignaturesForAddress(walletAddr, 24)
        var best: PremiumState? = null
        for (sig in sigs) {
            val tx = SolanaRpc.getParsedTransaction(sig) ?: continue
            val asset = detectPayment(tx, lamports) ?: continue
            val blockTime = tx["blockTime"]?.jsonPrimitive?.longOrNull ?: continue
            val paidAt = blockTime * 1000
            val expiresAt = paidAt + YEAR_MS
            val candidate = PremiumState(walletAddr, sig, asset, paidAt, expiresAt)
            if (!candidate.isActive) continue
            if (best == null || candidate.paidAt > best.paidAt) best = candidate
        }
        return best
    }

    private fun detectPayment(tx: JsonObject, solFloor: Long): String? {
        val instructions = buildList {
            tx["transaction"]?.jsonObject?.get("message")?.jsonObject
                ?.get("instructions")?.jsonArray?.forEach { add(it) }
            tx["meta"]?.jsonObject?.get("innerInstructions")?.jsonArray?.forEach { set ->
                set.jsonObject["instructions"]?.jsonArray?.forEach { add(it) }
            }
        }
        for (ix in instructions) {
            val obj = ix.jsonObject
            val type = obj["parsed"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull ?: continue
            val info = obj["parsed"]?.jsonObject?.get("info")?.jsonObject ?: continue
            val dest = info["destination"]?.jsonPrimitive?.contentOrNull

            if (dest == TREASURY_ADDRESS) {
                if ((type == "transfer" || type == "transferChecked") && obj["program"]?.jsonPrimitive?.contentOrNull == "system") {
                    val lam = info["lamports"]?.jsonPrimitive?.longOrNull ?: 0L
                    if (lam >= solFloor) return "SOL"
                }
            }
        }
        return null
    }

    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: ""
        if (msg.contains("reject", ignoreCase = true) || msg.contains("cancel", ignoreCase = true))
            return "İmza iptal edildi."
        if (msg.contains("insufficient", ignoreCase = true) || msg.contains("0x1"))
            return "Bakiye yetersiz. En az 1 dolar değerinde SOL gerekiyor."
        if (msg.isNotBlank() && msg.length < 200) return msg
        return "Ödeme tamamlanamadı. Cüzdanı ve bakiyeyi kontrol et."
    }
}

// Minimal Base58 implementation
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val DECODE_MAP = IntArray(128) { -1 }.also { map ->
        ALPHABET.forEachIndexed { i, c -> map[c.code] = i }
    }

    fun encode(input: ByteArray): String {
        var zeros = 0
        while (zeros < input.size && input[zeros] == 0.toByte()) zeros++
        val encoded = input.toBigInteger().toString(58).let { s ->
            val trimmed = s.trimStart { DECODE_MAP[it.code] == 0 }
            "1".repeat(zeros) + trimmed.map { ALPHABET[DECODE_MAP[it.code]] }.joinToString("")
        }
        return encoded
    }

    fun decode(input: String): ByteArray {
        var num = java.math.BigInteger.ZERO
        for (ch in input) {
            val idx = if (ch.code < 128) DECODE_MAP[ch.code] else -1
            if (idx < 0) throw IllegalArgumentException("Invalid Base58 char: $ch")
            num = num.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(idx.toLong()))
        }
        val zeros = input.takeWhile { it == '1' }.length
        val bytes = num.toByteArray().let { b -> if (b[0] == 0.toByte()) b.drop(1).toByteArray() else b }
        return ByteArray(zeros) + bytes
    }

    private fun ByteArray.toBigInteger() = java.math.BigInteger(1, this)
}

fun buildSolTransferTx(
    fromPubkey: ByteArray,
    toPubkey: ByteArray,
    lamports: Long,
    blockhash: ByteArray,
): ByteArray {
    // Solana transaction serialization for SystemProgram.transfer
    // Reference: https://docs.solana.com/developing/programming-model/transactions

    val systemProgram = ByteArray(32) // all zeros = System Program

    // Accounts: [from (signer+writable), to (writable), system_program]
    val accountKeys = listOf(fromPubkey, toPubkey, systemProgram)
    val numAccounts = accountKeys.size

    // Message header: [numSigners, numSignerReadonly, numReadonly]
    val header = byteArrayOf(1, 0, 1) // 1 signer, 0 signer-readonly, 1 readonly

    // Instruction: SystemProgram.transfer
    // programIndex=2 (system), accounts=[0,1], data=[2,0,0,0, lamports LE 8 bytes]
    val instrProgramIdx = 2.toByte()
    val instrAccounts = byteArrayOf(0, 1)
    val instrData = run {
        val buf = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putInt(2) // SystemInstruction::Transfer discriminator
        buf.putLong(lamports)
        buf.array()
    }

    val buf = java.io.ByteArrayOutputStream()
    // Compact-u16 helper
    fun writeCompact(n: Int) {
        var v = n
        while (true) {
            val lo = v and 0x7F
            v = v ushr 7
            if (v == 0) { buf.write(lo); break } else buf.write(lo or 0x80)
        }
    }

    // Message
    buf.write(header)
    writeCompact(numAccounts)
    accountKeys.forEach { buf.write(it) }
    buf.write(blockhash)
    writeCompact(1) // 1 instruction
    buf.write(instrProgramIdx.toInt())
    writeCompact(instrAccounts.size)
    instrAccounts.forEach { buf.write(it.toInt()) }
    writeCompact(instrData.size)
    buf.write(instrData)

    val message = buf.toByteArray()

    // Prepend: [num_signatures=1 (compact-u16)] + [64 zero bytes placeholder] + message
    // Wallet adapter fills the signature
    val txBuf = java.io.ByteArrayOutputStream()
    txBuf.write(1) // compact-u16: 1
    txBuf.write(ByteArray(64)) // signature placeholder
    txBuf.write(message)
    return txBuf.toByteArray()
}
