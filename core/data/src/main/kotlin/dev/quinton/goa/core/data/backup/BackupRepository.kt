package dev.quinton.goa.core.data.backup

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.data.rating.StampedEloConfigStore
import dev.quinton.goa.core.data.sync.LocalSyncData
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncMerge
import java.io.InputStream
import java.io.OutputStream
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement

/**
 * Puts one kind of non-match-log data in the backup file (settings, timer presets, custom draft
 * methods). Implement it and bind it into the set with Hilt:
 * `@Binds @IntoSet fun timerPresets(impl: TimerPresetsBackup): BackupContributor`.
 */
interface BackupContributor {
    /** Stable key in [BackupDocument.extras]; never rename it once shipped. */
    val key: String

    /** This contributor's data, or null to leave it out of the file. */
    suspend fun export(): JsonElement?

    /**
     * Called only when the file contains [key]. In [ImportMode.REPLACE] drop local data first.
     * [useBackupSettings]: the user wants the file's *settings* (choices such as the theme or the
     * selected preset) rather than the phone's; always true for [ImportMode.REPLACE]. Lists of
     * items (presets, methods) are merged by id either way in [ImportMode.MERGE].
     */
    suspend fun import(data: JsonElement, mode: ImportMode, useBackupSettings: Boolean)
}

/** Facts about the running app recorded in a backup. */
fun interface AppInfo {
    fun versionName(): String?
}

internal class AndroidAppInfo @Inject constructor(@ApplicationContext private val context: Context) : AppInfo {
    override fun versionName(): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
}

/** Export/import of all user data as a [BackupDocument]. Streams come from the Storage Access Framework. */
interface BackupRepository {
    suspend fun export(): BackupDocument

    /**
     * Loads [document]. Hero ids the catalog doesn't know are kept and read as "Unknown hero"
     * seats (DOMAIN.md). [useBackupSettings] (always on for [ImportMode.REPLACE]) applies the
     * file's rating constants and settings; otherwise the phone's are kept.
     */
    suspend fun import(
        document: BackupDocument,
        mode: ImportMode,
        useBackupSettings: Boolean = mode == ImportMode.REPLACE,
    )

    /** What [document] contains, including heroes this app doesn't know. */
    suspend fun summarize(document: BackupDocument): BackupSummary = document.summary()

    suspend fun exportTo(output: OutputStream) {
        output.writer(Charsets.UTF_8).use { it.write(BackupCodec.encode(export())) }
    }

    /** Reads and validates a file; throws [InvalidBackupException]. */
    suspend fun read(input: InputStream): BackupDocument =
        BackupCodec.decode(input.reader(Charsets.UTF_8).use { it.readText() })
}

/**
 * [boardRepository]: the board sandbox's boards (ADR-033), optional so the backup works in a build
 * without the board feature's binding (`@BindsOptionalOf`); absent = no boards in the file and none
 * imported.
 */
@Singleton
internal class RoomBackupRepository @Inject constructor(
    private val database: GoaDatabase,
    private val eloConfig: EloConfigRepository,
    private val stampedElo: StampedEloConfigStore,
    private val contributors: Set<@JvmSuppressWildcards BackupContributor>,
    private val catalog: CatalogRepository,
    private val appInfo: AppInfo,
    boardRepository: Optional<BoardRepository>,
) : BackupRepository {
    private val local = LocalSyncData(database)
    private val boards: BoardRepository? = boardRepository.orElse(null)

    override suspend fun export(): BackupDocument {
        val extras = contributors.sortedBy { it.key }.mapNotNull { c -> c.export()?.let { c.key to it } }.toMap()
        val config = eloConfig.config.first()
        val stamp = stampedElo.stamped().updatedAt
        val data = database.withReadTransaction { local.read() }
        return BackupDocument(
            exportedAt = System.currentTimeMillis(),
            appVersion = appInfo.versionName(),
            players = data.players,
            matches = data.matches,
            drafts = data.drafts,
            eloConfig = config.takeIf { it != EloConfig.DEFAULT },
            eloConfigUpdatedAt = stamp.takeIf { it > 0 },
            deletions = data.deletions,
            playerMerges = data.playerMerges,
            triviaRounds = data.triviaRounds,
            boards = boards?.boards?.first().orEmpty().sortedBy { it.id },
            extras = extras,
        )
    }

    override suspend fun summarize(document: BackupDocument): BackupSummary = document.summary(knownHeroIds())

    override suspend fun import(document: BackupDocument, mode: ImportMode, useBackupSettings: Boolean) {
        val settings = useBackupSettings || mode == ImportMode.REPLACE
        // Stored as they are: unknown hero ids included (read as "Unknown hero"), updatedAt kept.
        val file = SyncMerge.normalize(
            SyncDocument(
                players = document.players,
                matches = document.matches,
                drafts = document.drafts,
                triviaRounds = document.triviaRounds,
                deletions = document.deletions,
                playerMerges = document.playerMerges,
            ),
        )
        database.withWriteTransaction {
            if (mode == ImportMode.REPLACE) {
                local.clear()
                local.write(SyncDocument.EMPTY, file)
            } else {
                val before = SyncMerge.normalize(local.read())
                local.write(before, SyncMerge.merge(before, file))
            }
        }
        // Boards live in their own store (not synced). A file from before boards (v1–v4) has no list
        // at all, so even Replace leaves the phone's boards alone; Merge keeps the later edit of each.
        if (document.version >= BackupDocument.BOARDS_VERSION || document.boards.isNotEmpty()) {
            boards?.import(document.boards, replace = mode == ImportMode.REPLACE)
        }
        if (settings) {
            // A file without rating constants was made with the defaults.
            val config = (document.eloConfig ?: EloConfig.DEFAULT).sanitized()
            if (mode == ImportMode.MERGE) {
                // Asked for explicitly: a change made now, which the group gets at the next sync.
                if (config != eloConfig.config.first()) eloConfig.update { config }
            } else {
                // Replace restores the file as it was: its change time is kept, so an old backup's
                // constants never override newer ones from the group.
                val stamp = document.eloConfigUpdatedAt ?: if (document.eloConfig != null) 1 else 0
                stampedElo.applySynced(StampedEloConfig(config, stamp))
            }
        }
        for (contributor in contributors) {
            document.extras[contributor.key]?.let { contributor.import(it, mode, settings) }
        }
    }

    private suspend fun knownHeroIds(): Set<String> = catalog.catalog.first().heroes.mapTo(HashSet()) { it.id }
}
