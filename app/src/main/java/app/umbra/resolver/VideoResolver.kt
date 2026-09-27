package app.umbra.resolver

data class ResolvedMedia(
    val mediaUrl: String,
    val title: String,
    val platform: Platform,
    val mimeType: String,
    val quality: String?,
    val referer: String? = null,
    val userAgent: String? = null,
)

object VideoResolver {

    suspend fun resolve(rawUrl: String): ResolvedMedia {
        val seen = LinkInspector.inspect(rawUrl)
            ?: throw RuntimeException("Video bağlantısı bulunamadı.")

        val normalized = normalizeSeen(seen)

        return when (normalized.platform) {
            Platform.YOUTUBE -> YouTubeResolver.resolve(
                normalized.id ?: throw RuntimeException("YouTube video ID bulunamadı.")
            )
            Platform.TIKTOK -> TikTokResolver.resolve(normalized.url)
            Platform.INSTAGRAM -> InstagramResolver.resolve(
                normalized.id ?: throw RuntimeException("Instagram shortcode bulunamadı.")
            )
            Platform.X -> XResolver.resolve(
                normalized.id ?: throw RuntimeException("Tweet ID bulunamadı.")
            )
            Platform.DIRECT -> ResolvedMedia(
                mediaUrl = normalized.url,
                title = normalized.url.substringAfterLast('/').substringBefore('?'),
                platform = Platform.DIRECT,
                mimeType = "video/mp4",
                quality = null,
            )
            Platform.WEB -> resolveOpenGraph(normalized.url)
        }
    }

    private suspend fun normalizeSeen(seen: InspectedLink): InspectedLink {
        val needsFollow = seen.platform == Platform.TIKTOK && seen.id == null
            || seen.url.contains("/share/")
            || seen.url.contains("vm.tiktok.com")
            || seen.url.contains("vt.tiktok.com")
        if (!needsFollow) return seen
        return try {
            val finalUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                app.umbra.net.followRedirects(seen.url)
            }
            LinkInspector.inspect(finalUrl) ?: seen
        } catch (_: Exception) { seen }
    }

    private suspend fun resolveOpenGraph(url: String): ResolvedMedia {
        val html = app.umbra.net.fetchHtml(url)
        val media = metaContent(html, "og:video")
            ?: metaContent(html, "og:video:secure_url")
            ?: metaContent(html, "twitter:player:stream")
            ?: throw RuntimeException("Bu sayfada doğrudan video yok.")
        return ResolvedMedia(
            mediaUrl = media,
            title = metaContent(html, "og:title") ?: url.substringAfterLast('/'),
            platform = Platform.WEB,
            mimeType = "video/mp4",
            quality = null,
        )
    }

    fun metaContent(html: String, key: String): String? {
        val p1 = Regex("""<meta[^>]+(?:property|name)=["']${Regex.escape(key)}["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val p2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']${Regex.escape(key)}["']""", RegexOption.IGNORE_CASE)
        return (p1.find(html) ?: p2.find(html))?.groupValues?.get(1)?.cleanUrl()
    }

    fun String.cleanUrl() = replace("\\u0026", "&").replace("\\/", "/").replace("&amp;", "&")

    fun refererFor(mediaUrl: String): String? {
        val host = try { java.net.URL(mediaUrl).host } catch (_: Exception) { return null }
        return when {
            host.endsWith("googlevideo.com") -> null
            host.contains("tiktok") -> "https://www.tiktok.com/"
            host.contains("tikwm.com") -> "https://www.tikwm.com/"
            host.contains("cdninstagram.com") || host.contains("fbcdn.net") -> "https://www.instagram.com/"
            host.contains("twimg.com") -> "https://x.com/"
            else -> null
        }
    }

    fun userAgentFor(mediaUrl: String): String {
        val host = try { java.net.URL(mediaUrl).host } catch (_: Exception) { return app.umbra.net.BROWSER_UA }
        return if (host.endsWith("googlevideo.com")) app.umbra.net.ANDROID_YT_UA else app.umbra.net.BROWSER_UA
    }
}
