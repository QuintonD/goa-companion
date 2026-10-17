package dev.quinton.goa.feature.players.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject

/** Where the rendered stats page is written before it is shared (ADR-027). */
fun interface StatsPageStore {
    /** Writes [html] as [fileName], replacing any earlier page, and returns the file. Blocking I/O. */
    fun write(fileName: String, html: String): File
}

/** The app cache (`cache/stats/`): never backed up, cleared by the system when space runs low. */
internal class CacheStatsPageStore @Inject constructor(@ApplicationContext private val context: Context) :
    StatsPageStore {
    override fun write(fileName: String, html: String): File {
        val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        // Only the latest page is kept; older ones were already shared.
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, fileName).apply { writeText(html, Charsets.UTF_8) }
    }

    companion object {
        /** Must match `res/xml/stats_page_paths.xml`. */
        const val DIRECTORY = "stats"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface StatsPageModule {
    @Binds
    fun store(impl: CacheStatsPageStore): StatsPageStore
}

/**
 * Serves the stats page to the app chosen in the share sheet, read-only and only for that grant.
 * Its own subclass and authority, so it never clashes with another FileProvider in the manifest.
 */
class StatsPageFileProvider : FileProvider()

/** Opens the share sheet for [share] (`text/html` through [StatsPageFileProvider]). */
fun Context.shareStatsPage(share: StatsPageEvent.Share) {
    val uri = FileProvider.getUriForFile(this, "$packageName$AUTHORITY_SUFFIX", share.file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(StatsPageEvent.MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, share.title)
        .putExtra(Intent.EXTRA_TITLE, share.title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The clip carries the read grant to the chooser's preview as well as the target app.
    send.clipData = ClipData.newUri(contentResolver, share.title, uri)
    startActivity(Intent.createChooser(send, "Share stats page"))
}

/** Must match the provider's `android:authorities` in this module's manifest. */
private const val AUTHORITY_SUFFIX = ".statspage"
