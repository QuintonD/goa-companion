package dev.quinton.goa.core.data.backup

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.database.participantEntities
import dev.quinton.goa.core.database.stepEntities
import dev.quinton.goa.core.database.toEntity
import dev.quinton.goa.core.database.toModel
import dev.quinton.goa.core.domain.rating.EloConfig
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement

/**
 * Lets another feature put its own data in the backup file (e.g. timer presets, custom draft
 * methods). Implement it and bind it into the set with Hilt:
 * `@Binds @IntoSet fun timerPresets(impl: TimerPresetBackup): BackupContributor`.
 */
interface BackupContributor {
    /** Stable key in [BackupDocument.extras]; never rename it once shipped. */
    val key: String

    /** This contributor's data, or null to leave it out of the file. */
    suspend fun export(): JsonElement?

    /** Called only when the file contains [key]. In [ImportMode.REPLACE] drop local data first. */
    suspend fun import(data: JsonElement, mode: ImportMode)
}

/** Export/import of all user data as a [BackupDocument]. Streams come from the Storage Access Framework. */
interface BackupRepository {
    suspend fun export(): BackupDocument

    suspend fun import(document: BackupDocument, mode: ImportMode)

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
    private val contributors: Set<@JvmSuppressWildcards BackupContributor>,
) : BackupRepository {
    private val players get() = database.playerDao()
    private val matches get() = database.matchDao()
    private val drafts get() = database.draftRecordDao()

    override suspend fun export(): BackupDocument {
        val extras = contributors.sortedBy { it.key }.mapNotNull { c -> c.export()?.let { c.key to it } }.toMap()
        val config = eloConfig.config.first()
        return database.withReadTransaction {
            BackupDocument(
                exportedAt = System.currentTimeMillis(),
                players = players.getAll().map { it.toModel() },
                matches = matches.getAll().map { it.toModel() },
                drafts = drafts.getAll().map { it.toModel() },
                eloConfig = config.takeIf { it != EloConfig.DEFAULT },
                extras = extras,
            )
        }
    }

    override suspend fun import(document: BackupDocument, mode: ImportMode) {
        database.withWriteTransaction {
            if (mode == ImportMode.REPLACE) {
                matches.deleteAll()
                drafts.deleteAll()
                players.deleteAll()
            }
            players.upsertAll(document.players.map { it.toEntity() })
            document.matches.forEach { matches.upsert(it.toEntity(), it.participantEntities()) }
            document.drafts.forEach { drafts.upsert(it.toEntity(), it.stepEntities()) }
        }
        if (mode == ImportMode.REPLACE) {
            val config = document.eloConfig
            if (config == null) eloConfig.reset() else eloConfig.update { config }
        }
        for (contributor in contributors) {
            document.extras[contributor.key]?.let { contributor.import(it, mode) }
        }
    }
}
