package app.umbra.resolver

import app.umbra.net.BROWSER_UA
import app.umbra.net.fetchJson
import kotlinx.serialization.json.*

object XResolver {

    suspend fun resolve(tweetId: String): ResolvedMedia {
        val token = tweetId.takeLast(5)
        val url = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&lang=tr&token=$token"
        val json = fetchJson(url, mapOf("User-Agent" to BROWSER_UA, "Accept" to "application/json"))
        val data = Json.parseToJsonElement(json)

        val variants = mutableListOf<Triple<String, Int, String>>() // url, bitrate, type
        collectVariants(data, variants, 0)

        val videos = variants.filter { (url, _, type) ->
            type.contains("video/mp4") && url.startsWith("https://")
        }.sortedByDescending { (_, bitrate, _) -> bitrate }

        val best = videos.firstOrNull()
            ?: throw RuntimeException("Bu gönderide indirilebilir video bulunamadı.")

        val textObj = data.jsonObject
        val title = (textObj["full_text"] ?: textObj["text"])?.jsonPrimitive?.contentOrNull
            ?.split("\n")?.firstOrNull() ?: "X"
        val quality = if (best.second > 0) "${best.second / 1000} kbps" else null

        return ResolvedMedia(
            mediaUrl = best.first,
            title = title.take(140),
            platform = Platform.X,
            mimeType = "video/mp4",
            quality = quality,
            referer = "https://x.com/",
        )
    }

    private fun collectVariants(node: JsonElement, acc: MutableList<Triple<String, Int, String>>, depth: Int) {
        if (depth > 8) return
        when (node) {
            is JsonArray -> node.forEach { collectVariants(it, acc, depth + 1) }
            is JsonObject -> {
                val url = node["url"]?.jsonPrimitive?.contentOrNull
                val type = (node["content_type"] ?: node["contentType"])?.jsonPrimitive?.contentOrNull
                if (url != null && type != null) {
                    acc.add(Triple(url, node["bitrate"]?.jsonPrimitive?.intOrNull ?: 0, type))
                }
                node.values.forEach { collectVariants(it, acc, depth + 1) }
            }
            else -> {}
        }
    }
}
