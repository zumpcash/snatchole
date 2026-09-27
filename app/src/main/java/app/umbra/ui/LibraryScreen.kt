package app.umbra.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.umbra.ui.theme.*

data class LibraryEntry(
    val uri: Uri,
    val title: String,
    val sizeBytes: Long,
    val dateAdded: Long,
)

@Composable
fun LibraryScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val entries = remember { loadLibraryEntries(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackArrow(onClose)
                Text(
                    "B L A C K H O L E",
                    color = White.copy(0.5f),
                    fontSize = 11.sp,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.size(32.dp))
            }

            // Search hint
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BlackSurface2),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "  🔍  İndirilenlerde ara",
                    color = WhiteMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Henüz indirme yok.", color = WhiteMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(entries) { entry ->
                        LibraryRow(entry, onOpen = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(entry.uri, "video/*")
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.startActivity(Intent.createChooser(intent, "Videoyu Aç"))
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(entry: LibraryEntry, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail placeholder
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(BlackSurface2),
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.title.take(60), color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "${(entry.sizeBytes / (1024 * 1024.0)).let { "%.1f".format(it) }} MB",
                color = WhiteMuted,
                fontSize = 11.sp,
            )
        }

        // Dots menu placeholder
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { Box(Modifier.size(4.dp).background(Gold, RoundedCornerShape(2.dp))) }
        }
    }
}

@Composable
private fun BackArrow(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("←", color = Gold, fontSize = 20.sp)
    }
}

private fun loadLibraryEntries(context: Context): List<LibraryEntry> {
    val entries = mutableListOf<LibraryEntry>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.RELATIVE_PATH,
    )
    val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%UMBRA%")
    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection, selection, selectionArgs, sortOrder,
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
            entries.add(
                LibraryEntry(
                    uri = uri,
                    title = cursor.getString(nameCol) ?: "Video",
                    sizeBytes = cursor.getLong(sizeCol),
                    dateAdded = cursor.getLong(dateCol) * 1000,
                )
            )
        }
    }
    return entries
}
