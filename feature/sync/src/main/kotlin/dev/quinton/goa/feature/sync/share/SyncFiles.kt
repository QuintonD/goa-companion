package dev.quinton.goa.feature.sync.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.quinton.goa.core.domain.sync.SyncCodec
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** The share sheet's view of `cache/sync/` (a subclass, so it never clashes with another FileProvider). */
class SyncFileProvider : FileProvider()

/** Writing, sharing and reading sync files ("Share sync file", ADR-025). */
object SyncFiles {
    /** Refuse anything bigger: a group's sync file is tens to hundreds of KB. */
    private const val MAX_BYTES = 32L * 1024 * 1024

    fun fileName(groupName: String, today: LocalDate = LocalDate.now()): String {
        val safe = groupName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "group" }.take(30)
        return "goa-sync-$safe-$today.${SyncCodec.FILE_EXTENSION}"
    }

    /** Writes [bytes] to the cache and returns a share-sheet intent for it. */
    fun shareIntent(context: Context, bytes: ByteArray, fileName: String, title: String): Intent {
        val dir = File(context.cacheDir, "sync").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only the latest file is kept
        val file = File(dir, fileName).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.syncfiles", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = SyncCodec.FILE_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    /** Reads a picked or received file; throws [IOException] when it can't be read. */
    fun read(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open $uri")
        return input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_BYTES) throw IOException("File too large")
            }
            out.toByteArray()
        }
    }
}
