package app.umbra.wallet

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "premium")

data class PremiumState(
    val wallet: String,
    val signature: String,
    val asset: String,
    val paidAt: Long,
    val expiresAt: Long,
) {
    val isActive: Boolean get() = expiresAt > System.currentTimeMillis()
}

object PremiumStore {

    private val KEY_WALLET = stringPreferencesKey("wallet")
    private val KEY_SIGNATURE = stringPreferencesKey("signature")
    private val KEY_ASSET = stringPreferencesKey("asset")
    private val KEY_PAID_AT = longPreferencesKey("paidAt")
    private val KEY_EXPIRES_AT = longPreferencesKey("expiresAt")

    fun flow(context: Context): Flow<PremiumState?> =
        context.dataStore.data.map { prefs ->
            val wallet = prefs[KEY_WALLET] ?: return@map null
            val sig = prefs[KEY_SIGNATURE] ?: return@map null
            val asset = prefs[KEY_ASSET] ?: return@map null
            val paidAt = prefs[KEY_PAID_AT] ?: return@map null
            val expiresAt = prefs[KEY_EXPIRES_AT] ?: return@map null
            PremiumState(wallet, sig, asset, paidAt, expiresAt)
        }

    suspend fun save(context: Context, state: PremiumState) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WALLET] = state.wallet
            prefs[KEY_SIGNATURE] = state.signature
            prefs[KEY_ASSET] = state.asset
            prefs[KEY_PAID_AT] = state.paidAt
            prefs[KEY_EXPIRES_AT] = state.expiresAt
        }
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { it.clear() }
    }
}
