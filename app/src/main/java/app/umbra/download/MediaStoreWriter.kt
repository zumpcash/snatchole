package app.umbra.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

object MediaStoreWriter {

    fun write(
        context: Context,
        inputStream: InputStream,
        filename: String,
        mimeType: String,
        totalBytes: Long,
        onProgress: (Int) -> Unit,
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeQ(context, inputStream, filename, mimeType, totalBytes, onProgress)
        } else {
            writeLegacy(context, inputStream, filename, mimeType, totalBytes, onProgress)
        }
    }

    private fun writeQ(
        context: Context,
        inputStream: InputStream,
        filename: String,
        mimeType: String,
        totalBytes: Long,
        onProgress: (Int) -> Unit,
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/UMBRA")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw RuntimeException("MediaStore insert başarısız.")

        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val buf = ByteArray(128 * 1024)
                var written = 0L
                var read: Int
                while (inputStream.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    written += read
                    if (totalBytes > 0) onProgress(((written * 100) / totalBytes).toInt().coerceIn(0, 99))
                }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            onProgress(100)
            return uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun writeLegacy(
        context: Context,
        inputStream: InputStream,
        filename: String,
        mimeType: String,
        totalBytes: Long,
        onProgress: (Int) -> Unit,
    ): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "UMBRA"
        ).also { it.mkdirs() }
        val file = File(dir, filename)
        file.outputStream().use { out ->
            val buf = ByteArray(128 * 1024)
            var written = 0L
            var read: Int
            while (inputStream.read(buf).also { read = it } != -1) {
                out.write(buf, 0, read)
                written += read
                if (totalBytes > 0) onProgress(((written * 100) / totalBytes).toInt().coerceIn(0, 99))
            }
        }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        onProgress(100)
        return Uri.fromFile(file)
    }

    fun safeFilename(title: String, mime: String): String {
        val ext = when {
            mime.contains("mp4") -> "mp4"
            mime.contains("webm") -> "webm"
            mime.contains("mov") -> "mov"
            else -> "mp4"
        }
        val cleaned = title.replace(Regex("[^A-Za-z0-9\\-_ğüşöçıİĞÜŞÖÇ ]"), " ")
            .trim().replace(Regex("\\s+"), "_").take(80)
        val ts = System.currentTimeMillis().toString().takeLast(6)
        return "${cleaned}_$ts.$ext"
    }
}
