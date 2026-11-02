package dev.quinton.goa.core.data.online

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.data.sync.await
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardCache
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardFetcher
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/** The platform's GET of a public file: no token, and null on any failure, bad status or file too large. */
class OkHttpLeaderboardFetcher @Inject constructor(@OnlineRestHttp private val client: OkHttpClient) :
    LeaderboardFetcher {
    override suspend fun get(url: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) return@use null
            withContext(Dispatchers.IO) {
                val buffer = Buffer()
                val source = response.body.source()
                var read = 0L
                while (read != -1L && buffer.size <= MAX_FILE_BYTES) read = source.read(buffer, READ_CHUNK)
                if (buffer.size > MAX_FILE_BYTES) null else buffer.readUtf8()
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        // A malformed address, from a host in network.json.
        null
    }

    private companion object {
        const val READ_CHUNK = 64L * 1024

        /** 400 rows of a few hundred bytes each, with room to spare. */
        const val MAX_FILE_BYTES = 4L * 1024 * 1024
    }
}

/**
 * The last good board, in a plain file outside `datastore/`, so not in Auto Backup: it is public, and a new
 * phone reads a fresher one anyway. A write replaces the file whole.
 */
class FileLeaderboardCache internal constructor(private val file: File) : LeaderboardCache {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, FILE_NAME))

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        try {
            file.takeIf { it.isFile }?.readText()
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun write(text: String) = withContext(Dispatchers.IO) {
        val part = File(file.path + ".part")
        try {
            part.writeText(text)
            if (!part.renameTo(file)) part.delete()
        } catch (_: IOException) {
            // Only a copy for when nothing can be reached: the next read writes it again.
            part.delete()
        }
        Unit
    }

    private companion object {
        const val FILE_NAME = "online_leaderboard.json"
    }
}
