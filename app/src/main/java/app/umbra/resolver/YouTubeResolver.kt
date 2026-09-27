package app.umbra.resolver

import app.umbra.net.ANDROID_YT_UA
import app.umbra.net.BROWSER_UA
import app.umbra.net.fetchHtml
import app.umbra.net.postJson
import kotlinx.serialization.json.*

object YouTubeResolver {

    private data class YtFormat(
        val url: String?,
        val mimeType: String?,
        val height: Int,
        val bitrate: Int,
        val qualityLabel: String?,
        val contentLength: Long,
    )

    suspend fun resolve(videoId: String): ResolvedMedia {
        val watchHtml = fetchHtml(
            "https://www.youtube.com/watch?v=$videoId&bpctr=9999999999&has_verified=1",
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                "Accept-Language" to "en-US,en;q=0.5",
            )
        )
        val visitorData = Regex(""""visitorData":"([^"]+)"""").find(watchHtml)?.groupValues?.get(1)
        val sts = Regex(""""STS":(\d+)""").find(watchHtml)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val requestHeaders = buildMap {
            put("Content-Type", "application/json")
            put("User-Agent", ANDROID_YT_UA)
            put("Accept", "application/json")
            put("Accept-Language", "en-US,en;q=0.5")
            put("X-YouTube-Client-Name", "3")
            put("X-YouTube-Client-Version", "21.26.364")
            put("Origin", "https://www.youtube.com")
            if (visitorData != null) put("X-Goog-Visitor-Id", visitorData)
        }

        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID")
                    put("clientVersion", "21.26.364")
                    put("androidSdkVersion", 30)
                    put("osName", "Android")
                    put("osVersion", "13")
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", videoId)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                    if (sts > 0) put("signatureTimestamp", sts)
                }
            }
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()

        val responseText = postJson(
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            body,
            requestHeaders,
        )

        val json = Json.parseToJsonElement(responseText).jsonObject
        val status = json["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
        val reason = json["playabilityStatus"]?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
        val isLive = json["videoDetails"]?.jsonObject?.get("isLiveContent")?.jsonPrimitive?.booleanOrNull

        if (isLive == true) throw RuntimeException("Canlı yayın tek dosya olarak indirilemez.")
        if (status != null && status != "OK") throw RuntimeException(youtubeReason(status, reason))

        val formats = json["streamingData"]?.jsonObject?.get("formats")?.jsonArray ?: JsonArray(emptyList())
        val parsed = formats.mapNotNull { el ->
            val obj = el.jsonObject
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
            if (!mime.contains("video")) return@mapNotNull null
            YtFormat(
                url = url,
                mimeType = mime,
                height = obj["height"]?.jsonPrimitive?.intOrNull ?: 0,
                bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0,
                qualityLabel = obj["qualityLabel"]?.jsonPrimitive?.contentOrNull,
                contentLength = obj["contentLength"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }.sortedWith(compareByDescending<YtFormat> { it.height }.thenByDescending { it.bitrate })

        val best = parsed.firstOrNull()
            ?: throw RuntimeException(youtubeReason(status, reason))

        val mime = best.mimeType?.split(";")?.firstOrNull() ?: "video/mp4"
        val title = json["videoDetails"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull ?: "YouTube"

        return ResolvedMedia(
            mediaUrl = best.url!!,
            title = decodeTitle(title),
            platform = Platform.YOUTUBE,
            mimeType = mime,
            quality = best.qualityLabel ?: if (best.height > 0) "${best.height}p" else null,
            userAgent = ANDROID_YT_UA,
        )
    }

    private fun youtubeReason(status: String?, reason: String?): String {
        if (status == "LOGIN_REQUIRED" || reason?.contains("bot", ignoreCase = true) == true)
            return "YouTube bu isteği bot sandı. Birazdan yeniden dene."
        if (reason?.contains("live", ignoreCase = true) == true) return "Canlı yayın indirilemez."
        if (reason?.contains("unavailable", ignoreCase = true) == true || reason?.contains("private", ignoreCase = true) == true)
            return "Bu YouTube videosu kapalı ya da gizli."
        if (!reason.isNullOrBlank() && reason.length < 140) return reason
        return "Bu YouTube videosundan ses+görüntü stream alınamadı."
    }

    private fun decodeTitle(title: String) = title
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("https?://\\S+"), "").trim().take(140)
}
