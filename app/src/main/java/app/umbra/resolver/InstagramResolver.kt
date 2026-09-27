package app.umbra.resolver

import app.umbra.net.BROWSER_UA
import app.umbra.net.fetchHtml
import app.umbra.net.postForm
import kotlinx.serialization.json.*

object InstagramResolver {

    private const val IG_APP = "936619743392459"
    private const val IG_DOC_SHORTCODE = "8845758582119845"
    private const val IG_DOC_MEDIA = "27130156389949648"

    suspend fun resolve(shortcode: String): ResolvedMedia {
        // Strategy 1: Embed page — no login needed
        val embedPages = listOf(
            "https://www.instagram.com/reel/$shortcode/embed/captioned/",
            "https://www.instagram.com/p/$shortcode/embed/captioned/",
        )
        var html = ""
        for (page in embedPages) {
            try {
                html = fetchHtml(page, mapOf("User-Agent" to BROWSER_UA, "Referer" to "https://www.instagram.com/"))
                if (html.isNotEmpty()) break
            } catch (_: Exception) { html = "" }
        }
        if (html.isEmpty()) throw RuntimeException("Instagram sayfayı açmadı. Birazdan yeniden dene.")

        val fromEmbed = findKeyedUrl(html, "video_url")
            ?: VideoResolver.metaContent(html, "og:video")
            ?: VideoResolver.metaContent(html, "og:video:secure_url")
        if (fromEmbed != null) {
            return ResolvedMedia(
                mediaUrl = fromEmbed,
                title = VideoResolver.metaContent(html, "og:title") ?: "Instagram",
                platform = Platform.INSTAGRAM,
                mimeType = "video/mp4",
                quality = null,
                referer = "https://www.instagram.com/",
            )
        }

        // Strategy 2: GraphQL (needs lsd + csrf from embed page)
        val lsd = readIgToken(html, "lsd")
        val csrf = readIgToken(html, "csrf")
        if (lsd == null || csrf == null) throw RuntimeException("Instagram bu bağlantıyı giriş duvarının arkasına aldı.")

        val fallbackTitle = VideoResolver.metaContent(html, "og:title") ?: "Instagram"
        val headers = mapOf(
            "User-Agent" to BROWSER_UA,
            "Accept" to "*/*",
            "Content-Type" to "application/x-www-form-urlencoded",
            "X-IG-App-ID" to IG_APP,
            "X-FB-LSD" to lsd,
            "X-CSRFToken" to csrf,
            "X-ASBD-ID" to "129477",
            "X-IG-WWW-Claim" to "0",
            "Origin" to "https://www.instagram.com",
            "Referer" to "https://www.instagram.com/reel/$shortcode/",
            "Cookie" to "csrftoken=$csrf; ig_nrcb=1",
        )

        val queries = listOf(
            Triple(IG_DOC_SHORTCODE, "PolarisPostActionLoadPostQueryQuery",
                mapOf("shortcode" to shortcode, "fetch_tagged_user_count" to "null", "hoisted_comment_id" to "null", "hoisted_reply_id" to "null")),
            Triple(IG_DOC_MEDIA, "PolarisLoggedOutDesktopWWWPostRootContentQuery",
                mapOf("media_id" to shortcodeToMediaId(shortcode))),
        )

        for ((docId, friendlyName, variables) in queries) {
            val params = mapOf(
                "av" to "0", "__d" to "www", "__user" to "0", "__a" to "1", "__req" to "b",
                "dpr" to "2", "__ccg" to "EXCELLENT", "lsd" to lsd,
                "fb_api_caller_class" to "RelayModern",
                "fb_api_req_friendly_name" to friendlyName,
                "variables" to kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    variables.entries.fold(buildJsonObject {}) { acc, (k, v) ->
                        buildJsonObject { acc.entries.forEach { (ek, ev) -> put(ek, ev) }; put(k, v) }
                    }
                ),
                "server_timestamps" to "true",
                "doc_id" to docId,
            )
            val (code, text) = postForm("https://www.instagram.com/graphql/query", params, headers)
            if (code == 401 || code == 429 || text.contains("rate limit", ignoreCase = true)) {
                throw RuntimeException("Instagram kısa süreliğine bekleme koydu. Birazdan yeniden dene.")
            }
            val media = parseInstagramResponse(text, fallbackTitle) ?: continue
            return media
        }

        throw RuntimeException("Bu Instagram gönderisinde herkese açık video bulunamadı.")
    }

    private fun shortcodeToMediaId(code: String): String {
        val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        var id = 0L
        for (ch in code) {
            val idx = alpha.indexOf(ch)
            if (idx < 0) throw RuntimeException("Instagram bağlantısı bozuk.")
            id = id * 64 + idx
        }
        return id.toString()
    }

    private fun readIgToken(html: String, name: String): String? = when (name) {
        "lsd" -> Regex("""\["LSD",\[\],\{"token":"([^"]+)"""").find(html)?.groupValues?.get(1)
        "csrf" -> Regex(""""csrf_token":"([^"]+)"""").find(html)?.groupValues?.get(1)
        else -> null
    }

    private fun findKeyedUrl(html: String, key: String): String? {
        val pattern = Regex("""${Regex.escape(key)}\\?":\s*\\?"(https?:[^"\\]+)""", RegexOption.IGNORE_CASE)
        return pattern.find(html)?.groupValues?.get(1)?.run { VideoResolver.run { cleanUrl() } }
    }

    private data class IgPick(val url: String, val area: Int, val height: Int)

    private fun parseInstagramResponse(text: String, fallbackTitle: String): ResolvedMedia? {
        val cleaned = text.removePrefix("for(;;);").trim()
        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) return null
        val data = try { Json.parseToJsonElement(cleaned) } catch (_: Exception) { return null }
        val picks = mutableListOf<IgPick>()
        collectIgVideos(data, picks, 0)
        picks.sortWith(compareByDescending<IgPick> { it.area }.thenByDescending { it.height })
        val best = picks.firstOrNull() ?: return null
        return ResolvedMedia(
            mediaUrl = best.url,
            title = fallbackTitle,
            platform = Platform.INSTAGRAM,
            mimeType = "video/mp4",
            quality = if (best.height > 0) "${best.height}p" else null,
            referer = "https://www.instagram.com/",
        )
    }

    private fun collectIgVideos(node: JsonElement, acc: MutableList<IgPick>, depth: Int) {
        if (depth > 9) return
        when (node) {
            is JsonArray -> node.forEach { collectIgVideos(it, acc, depth + 1) }
            is JsonObject -> {
                val videoUrl = node["video_url"]?.jsonPrimitive?.contentOrNull
                if (videoUrl?.startsWith("http") == true) {
                    val w = node["width"]?.jsonPrimitive?.intOrNull ?: 0
                    val h = node["height"]?.jsonPrimitive?.intOrNull ?: 0
                    acc.add(IgPick(url = videoUrl.run { VideoResolver.run { cleanUrl() } }, area = w * h, height = h))
                }
                val versions = node["video_versions"]?.jsonArray
                versions?.forEach { v ->
                    val obj = v.jsonObject
                    val u = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    if (!u.startsWith("http")) return@forEach
                    val w = obj["width"]?.jsonPrimitive?.intOrNull ?: 0
                    val h = obj["height"]?.jsonPrimitive?.intOrNull ?: 0
                    acc.add(IgPick(url = u.run { VideoResolver.run { cleanUrl() } }, area = w * h, height = h))
                }
                node.values.forEach { collectIgVideos(it, acc, depth + 1) }
            }
            else -> {}
        }
    }
}
