package app.umbra.wallet

import app.umbra.net.BROWSER_UA
import app.umbra.net.postJson
import kotlinx.serialization.json.*

private val RPCS = listOf(
    "https://api.mainnet-beta.solana.com",
    "https://solana-rpc.publicnode.com",
)

object SolanaRpc {

    suspend fun getLatestBlockhash(): Pair<String, Long> {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getLatestBlockhash")
            putJsonArray("params") {
                addJsonObject { put("commitment", "confirmed") }
            }
        }.toString()

        return withRpc(body) { text ->
            val root = Json.parseToJsonElement(text).jsonObject
            val value = root["result"]?.jsonObject?.get("value")?.jsonObject
                ?: throw RuntimeException("Blockhash alınamadı.")
            val blockhash = value["blockhash"]?.jsonPrimitive?.content
                ?: throw RuntimeException("Blockhash eksik.")
            val lastValid = value["lastValidBlockHeight"]?.jsonPrimitive?.long ?: 0L
            Pair(blockhash, lastValid)
        }
    }

    suspend fun getBalance(address: String): Long {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getBalance")
            putJsonArray("params") { add(address) }
        }.toString()
        return withRpc(body) { text ->
            Json.parseToJsonElement(text).jsonObject["result"]?.jsonObject?.get("value")?.jsonPrimitive?.long ?: 0L
        }
    }

    suspend fun getSolPrice(): Double {
        // Kraken public ticker
        val body = app.umbra.net.fetchJson(
            "https://api.kraken.com/0/public/Ticker?pair=SOLUSD",
            mapOf("Accept" to "application/json", "User-Agent" to BROWSER_UA),
        )
        val root = Json.parseToJsonElement(body).jsonObject
        val result = root["result"]?.jsonObject ?: throw RuntimeException("SOL kuru alınamadı.")
        val pair = result["SOLUSD"] ?: result.values.firstOrNull()
            ?: throw RuntimeException("SOL kuru bulunamadı.")
        val price = pair.jsonObject["c"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?: throw RuntimeException("SOL fiyatı okunamadı.")
        if (price < 5 || price > 100_000) throw RuntimeException("SOL kuru geçersiz.")
        return price
    }

    suspend fun lamportsFor1Usd(): Long {
        val price = getSolPrice()
        return Math.ceil((1.02 / price) * 1_000_000_000.0).toLong()
    }

    suspend fun confirmTransaction(signature: String, blockhash: String, lastValid: Long): Boolean {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "confirmTransaction")
            putJsonArray("params") {
                addJsonObject {
                    put("signature", signature)
                    put("blockhash", blockhash)
                    put("lastValidBlockHeight", lastValid)
                }
                put("confirmed")
            }
        }.toString()
        return withRpc(body) { text ->
            val root = Json.parseToJsonElement(text).jsonObject
            root["result"]?.jsonObject?.get("value")?.jsonObject?.get("err")?.jsonNull != null
                || root["result"]?.jsonObject?.get("value")?.jsonObject?.get("err") == JsonNull
        }
    }

    suspend fun getParsedTransaction(signature: String): JsonObject? {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getTransaction")
            putJsonArray("params") {
                add(signature)
                addJsonObject {
                    put("encoding", "jsonParsed")
                    put("commitment", "confirmed")
                    put("maxSupportedTransactionVersion", 0)
                }
            }
        }.toString()
        return withRpc(body) { text ->
            Json.parseToJsonElement(text).jsonObject["result"]?.jsonObject
        }
    }

    suspend fun getSignaturesForAddress(address: String, limit: Int = 24): List<String> {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getSignaturesForAddress")
            putJsonArray("params") {
                add(address)
                addJsonObject { put("limit", limit) }
            }
        }.toString()
        return withRpc(body) { text ->
            Json.parseToJsonElement(text).jsonObject["result"]?.jsonArray
                ?.mapNotNull { it.jsonObject["signature"]?.jsonPrimitive?.contentOrNull }
                ?: emptyList()
        }
    }

    private val JSON_HEADERS = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
    )

    private suspend fun <T> withRpc(body: String, parse: (String) -> T): T {
        var last: Exception? = null
        for (rpc in RPCS) {
            try {
                return parse(postJson(rpc, body, JSON_HEADERS))
            } catch (e: Exception) { last = e }
        }
        throw last ?: RuntimeException("Solana RPC ulaşılamadı.")
    }
}
