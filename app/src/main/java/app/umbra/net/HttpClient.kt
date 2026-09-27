package app.umbra.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
const val ANDROID_YT_UA = "com.google.android.youtube/21.26.364 (Linux; U; Android 13) gzip"

val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

fun buildRequest(
    url: String,
    headers: Map<String, String> = emptyMap(),
    method: String = "GET",
    body: okhttp3.RequestBody? = null,
): Request {
    val builder = Request.Builder().url(url)
    headers.forEach { (k, v) -> builder.header(k, v) }
    when (method.uppercase()) {
        "POST" -> builder.post(body ?: okhttp3.RequestBody.create(null, ByteArray(0)))
        "GET" -> builder.get()
    }
    return builder.build()
}

suspend fun fetchHtml(
    url: String,
    headers: Map<String, String> = mapOf("User-Agent" to BROWSER_UA, "Accept" to "text/html"),
    maxBytes: Int = 1_400_000,
): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val req = buildRequest(url, headers)
    val resp = httpClient.newCall(req).execute()
    resp.use {
        if (!it.isSuccessful) throw RuntimeException("Sayfa açılmadı (${it.code}).")
        it.body?.source()?.let { src ->
            val sink = okio.Buffer()
            src.read(sink, maxBytes.toLong())
            sink.readUtf8()
        } ?: ""
    }
}

suspend fun fetchJson(
    url: String,
    headers: Map<String, String> = mapOf("User-Agent" to BROWSER_UA, "Accept" to "application/json"),
): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val req = buildRequest(url, headers)
    httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw RuntimeException("İstek başarısız (${resp.code}).")
        resp.body?.string() ?: ""
    }
}

suspend fun postJson(
    url: String,
    jsonBody: String,
    headers: Map<String, String>,
): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val body = okhttp3.RequestBody.create(
        okhttp3.MediaType.parse("application/json; charset=utf-8"),
        jsonBody
    )
    val req = buildRequest(url, headers, "POST", body)
    httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw RuntimeException("POST başarısız (${resp.code}).")
        resp.body?.string() ?: ""
    }
}

suspend fun postForm(
    url: String,
    params: Map<String, String>,
    headers: Map<String, String>,
): Pair<Int, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val formBuilder = okhttp3.FormBody.Builder()
    params.forEach { (k, v) -> formBuilder.add(k, v) }
    val req = buildRequest(url, headers, "POST", formBuilder.build())
    httpClient.newCall(req).execute().use { resp ->
        Pair(resp.code, resp.body?.string() ?: "")
    }
}

fun followRedirects(url: String): String {
    val req = Request.Builder().url(url)
        .header("User-Agent", BROWSER_UA)
        .head()
        .build()
    return try {
        httpClient.newCall(req).execute().use { it.request.url.toString() }
    } catch (_: Exception) { url }
}
