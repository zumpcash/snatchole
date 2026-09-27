package app.umbra.resolver

enum class Platform { YOUTUBE, TIKTOK, INSTAGRAM, X, DIRECT, WEB }

data class InspectedLink(
    val url: String,
    val platform: Platform,
    val id: String?,
)

object LinkInspector {

    private val YT_PATTERNS = listOf(
        Regex("""(?:youtube\.com/(?:watch\?v=|shorts/|embed/)|youtu\.be/)([A-Za-z0-9_-]{11})"""),
    )
    private val IG_PATTERNS = listOf(
        Regex("""instagram\.com/(?:p|reel|reels)/([A-Za-z0-9_-]+)"""),
    )
    private val TT_PATTERNS = listOf(
        Regex("""tiktok\.com/@[^/]+/video/(\d+)"""),
        Regex("""tiktok\.com/t/([A-Za-z0-9]+)"""),
        Regex("""vm\.tiktok\.com/([A-Za-z0-9]+)"""),
        Regex("""vt\.tiktok\.com/([A-Za-z0-9]+)"""),
    )
    private val X_PATTERNS = listOf(
        Regex("""(?:twitter|x)\.com/\w+/status/(\d+)"""),
    )
    private val DIRECT_EXT = setOf("mp4", "m4v", "mov", "webm", "mkv", "avi")

    fun inspect(raw: String): InspectedLink? {
        val url = raw.trim().let {
            if (!it.startsWith("http")) "https://$it" else it
        }
        if (!url.startsWith("http")) return null

        for (p in YT_PATTERNS) {
            val m = p.find(url) ?: continue
            return InspectedLink(url, Platform.YOUTUBE, m.groupValues[1])
        }
        for (p in IG_PATTERNS) {
            val m = p.find(url) ?: continue
            return InspectedLink(url, Platform.INSTAGRAM, m.groupValues[1])
        }
        for (p in X_PATTERNS) {
            val m = p.find(url) ?: continue
            return InspectedLink(url, Platform.X, m.groupValues[1])
        }
        for (p in TT_PATTERNS) {
            val m = p.find(url) ?: continue
            val id = m.groupValues[1]
            return InspectedLink(url, Platform.TIKTOK, id.ifEmpty { null })
        }
        if (url.contains("tiktok.com")) return InspectedLink(url, Platform.TIKTOK, null)

        val ext = url.substringAfterLast('.').substringBefore('?').lowercase()
        if (ext in DIRECT_EXT) return InspectedLink(url, Platform.DIRECT, null)

        return InspectedLink(url, Platform.WEB, null)
    }

    fun extractFromText(text: String): String? {
        val urlRegex = Regex("""https?://\S+""")
        val candidates = urlRegex.findAll(text).map { it.value.trimEnd(')', ']', '.', ',') }.toList()
        for (url in candidates) {
            val seen = inspect(url) ?: continue
            if (seen.platform != Platform.WEB) return seen.url
        }
        return candidates.firstOrNull()
    }
}
