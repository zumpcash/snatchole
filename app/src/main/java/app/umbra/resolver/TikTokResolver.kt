package app.umbra.resolver

import app.umbra.net.BROWSER_UA
import app.umbra.net.fetchHtml
import app.umbra.net.fetchJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

object TikTokResolver {

    suspend fun resolve(pageUrl: String): ResolvedMedia {
        // Strategy 1: tikwm.com public API (free, no key)
        try {
            val apiUrl = "https://www.tikwm.com/api/?hd=1&url=${java.net.URLEncoder.encode(pageUrl, "UTF-8")}"
            val json = fetchJson(apiUrl, mapOf("User-Agent" to BROWSER_UA, "Accept" to "application/json"))
            val root = Json.parseToJsonElement(json).jsonObject
            if (root["code"]?.jsonPrimitive?.intOrNull == 0) {
                val data = root["data"]?.jsonObject
                val hdplay = data?.get("hdplay")?.jsonPrimitive?.contentOrNull
                val play = data?.get("play")?.jsonPrimitive?.contentOrNull
                val wmplay = data?.get("wmplay")?.jsonPrimitive?.contentOrNull
                val media = listOfNotNull(hdplay, play, wmplay)
                    .firstOrNull { it.startsWith("https://") }
                if (media != null) {
                    val quality = when (media) {
                        hdplay -> "HD"
                        wmplay -> "Watermarklı"
                        else -> null
                    }
                    return ResolvedMedia(
                        mediaUrl = media,
                        title = data?.get("title")?.jsonPrimitive?.contentOrNull ?: "TikTok",
                        platform = Platform.TIKTOK,
                        mimeType = "video/mp4",
                        quality = quality,
                        referer = "https://www.tikwm.com/",
                    )
                }
            }
        } catch (_: Exception) { /* fall through */ }

        // Strategy 2: Follow redirect to get canonical URL, then parse HTML
        val finalUrl = withContext(Dispatchers.IO) {
            try { app.umbra.net.followRedirects(pageUrl) } catch (_: Exception) { pageUrl }
        }

        val html = fetchHtml(finalUrl, mapOf("User-Agent" to BROWSER_UA, "Accept" to "text/html"))

        val downloadAddr = findKeyedUrl(html, "downloadAddr")
            ?: findKeyedUrl(html, "playAddr")
            ?: VideoResolver.metaContent(html, "og:video")
            ?: VideoResolver.metaContent(html, "og:video:secure_url")
            ?: throw RuntimeException("TikTok bu videoda indirilebilir kaynak bulunamadı.")

        return ResolvedMedia(
            mediaUrl = downloadAddr.run { VideoResolver.run { cleanUrl() } },
            title = VideoResolver.metaContent(html, "og:title") ?: "TikTok",
            platform = Platform.TIKTOK,
            mimeType = "video/mp4",
            quality = null,
            referer = "https://www.tiktok.com/",
        )
    }

    private fun findKeyedUrl(html: String, key: String): String? {
        val pattern = Regex("""${Regex.escape(key)}\\?":\s*\\?"(https?:[^"\\]+)""", RegexOption.IGNORE_CASE)
        return pattern.find(html)?.groupValues?.get(1)?.run { VideoResolver.run { cleanUrl() } }
    }
}
