package app.umbra.download

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.*
import app.umbra.R
import app.umbra.net.BROWSER_UA
import app.umbra.net.httpClient
import app.umbra.resolver.VideoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val CHANNEL_ID = "umbra_downloads"
        const val KEY_MEDIA_URL = "mediaUrl"
        const val KEY_TITLE = "title"
        const val KEY_MIME = "mime"
        const val KEY_REFERER = "referer"
        const val KEY_UA = "ua"
        const val KEY_RESULT_URI = "resultUri"

        fun buildRequest(
            mediaUrl: String,
            title: String,
            mimeType: String,
            referer: String?,
            userAgent: String?,
        ): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_MEDIA_URL to mediaUrl,
                KEY_TITLE to title,
                KEY_MIME to mimeType,
                KEY_REFERER to referer,
                KEY_UA to userAgent,
            )
            return OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        }
    }

    private val notifManager = ctx.getSystemService(NotificationManager::class.java)
    private val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

    override suspend fun doWork(): Result {
        val mediaUrl = inputData.getString(KEY_MEDIA_URL) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Video"
        val mime = inputData.getString(KEY_MIME) ?: "video/mp4"
        val referer = inputData.getString(KEY_REFERER)
        val ua = inputData.getString(KEY_UA) ?: VideoResolver.userAgentFor(mediaUrl)

        showProgress(title, 0)

        return try {
            val uri = withContext(Dispatchers.IO) {
                val req = Request.Builder().url(mediaUrl)
                    .header("User-Agent", ua)
                    .header("Accept", "*/*")
                    .apply { referer?.let { header("Referer", it) } }
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw RuntimeException("Sunucu ${resp.code} döndürdü.")
                    val body = resp.body ?: throw RuntimeException("Boş yanıt.")
                    val totalBytes = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                    val filename = MediaStoreWriter.safeFilename(title, mime)
                    MediaStoreWriter.write(
                        context = applicationContext,
                        inputStream = body.byteStream(),
                        filename = filename,
                        mimeType = mime,
                        totalBytes = totalBytes,
                    ) { progress ->
                        showProgress(title, progress)
                    }
                }
            }
            showDone(title)
            Result.success(workDataOf(KEY_RESULT_URI to uri.toString()))
        } catch (e: Exception) {
            showError(title, e.message ?: "İndirme başarısız.")
            Result.failure(workDataOf("error" to (e.message ?: "İndirme başarısız.")))
        }
    }

    private fun showProgress(title: String, progress: Int) {
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("İndiriliyor: $title")
            .setContentText(if (progress > 0) "%$progress" else "Bağlanıyor…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        setForegroundAsync(ForegroundInfo(notifId, notif))
        notifManager?.notify(notifId, notif)
    }

    private fun showDone(title: String) {
        notifManager?.notify(notifId,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("İndirildi")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun showError(title: String, message: String) {
        notifManager?.notify(notifId,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("İndirilemedi: $title")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .build()
        )
    }
}
