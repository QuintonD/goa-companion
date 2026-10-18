package dev.quinton.goa.core.data.backup

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.data.rating.StampedEloConfigStore
import dev.quinton.goa.core.data.sync.LocalSyncData
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.domain.backup.BackupFiles
import dev.quinton.goa.core.domain.backup.summary
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncMerge
import java.io.InputStream
import java.io.OutputStream
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

@Singleton
internal class RoomBackupRepository @Inject constructor(
    private val database: GoaDatabase,
    private val eloConfig: EloConfigRepository,
    private val stampedElo: StampedEloConfigStore,
    private val contributors: Set<@JvmSuppressWildcards BackupContributor>,
    private val catalog: CatalogRepository,
    private val appInfo: AppInfo,
) : BackupRepository {
    private val local = LocalSyncData(database)

    override suspend fun export(): BackupDocument {
        val extras = contributors.sortedBy { it.key }.mapNotNull { c -> c.export()?.let { c.key to it } }.toMap()
        val config = eloConfig.config.first()
        val stamp = stampedElo.stamped().updatedAt
        val data = database.withReadTransaction { local.read() }
        return BackupFiles.export(
            data.copy(eloConfig = StampedEloConfig(config, stamp)),
            exportedAt = System.currentTimeMillis(),
            appVersion = appInfo.versionName(),
            extras = extras,
        )
    }

    override suspend fun summarize(document: BackupDocument): BackupSummary = document.summary(knownHeroIds())

    override suspend fun import(document: BackupDocument, mode: ImportMode, useBackupSettings: Boolean) {
        val settings = useBackupSettings || mode == ImportMode.REPLACE
        // Stored as they are: unknown hero ids included (read as "Unknown hero"), updatedAt kept.
        database.withWriteTransaction {
            if (mode == ImportMode.REPLACE) {
                local.clear()
                local.write(SyncDocument.EMPTY, BackupFiles.records(document))
            } else {
                val before = SyncMerge.normalize(local.read())
                local.write(before, BackupFiles.merge(before, document))
            }
        }
        if (settings) {
            if (mode == ImportMode.MERGE) {
                // Asked for explicitly: a change made now, which the group gets at the next sync.
                val config = BackupFiles.eloConfig(document)
                if (config != eloConfig.config.first()) eloConfig.update { config }
            } else {
                // Replace restores the file as it was (BackupFiles.restoredEloConfig).
                stampedElo.applySynced(BackupFiles.restoredEloConfig(document))
            }
        }
        for (contributor in contributors) {
            document.extras[contributor.key]?.let { contributor.import(it, mode, settings) }
        }
    }

    private suspend fun knownHeroIds(): Set<String> = catalog.catalog.first().heroes.mapTo(HashSet()) { it.id }
}
