package dev.quinton.goa.hub.store

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncJson
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.domain.sync.SyncValidation
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.hub.HubClock
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

/** A change that would leave the group's data invalid; nothing was stored. [message] is user-readable. */
class InvalidGroupDataException(message: String) : IllegalStateException(message)

/**
 * The group's shared data (ADR-030 §3): one canonical [SyncDocument] in `data.json`, every change
 * under one mutex, validated ([SyncValidation]) and written atomically, plus a copy per day kept
 * [KEEP_DAYS] days in `backups/`.
 *
 * Read with [read] or [data] (merged players resolved, as the phones' screens see them). Write
 * only through the helpers below ([upsertPlayer], [upsertMatch], [deleteMatch], [mergePlayers], …):
 * they stamp exactly like the phones' repositories ([SharedWrites]), so a web edit syncs like a
 * phone edit. [update] is the low-level escape hatch; [mergeIn] is what a sync does.
 */
class GroupStore(
    private val file: Path,
    private val backups: Path,
    private val clock: HubClock,
) {
    private val mutex = Mutex()
    private val state: MutableStateFlow<SyncDocument> = MutableStateFlow(load())

    /** The stored document; a new value after every change (handy as a cache key). */
    val document: StateFlow<SyncDocument> = state.asStateFlow()

    /** The stored document as it is: raw player ids, every hero id, tombstones and merges. */
    fun read(): SyncDocument = state.value

    /** The stored data as the screens read it (merged players resolved, newest match first). */
    fun data(): GroupData = GroupData(state.value)

    /**
     * Applies [transform] under the lock, then normalizes, validates and stores the result.
     * Throws [InvalidGroupDataException] (nothing stored) if the result breaks a sync rule.
     */
    suspend fun update(transform: (SyncDocument) -> SyncDocument): SyncDocument = mutex.withLock {
        val current = state.value
        val next = SyncMerge.normalize(transform(current))
        SyncValidation.problem(next)?.let { throw InvalidGroupDataException(it) }
        if (next != current) {
            store(next)
            state.value = next
        }
        next
    }

    /** Merges another member's document in (a sync or an imported sync file); returns the merged data. */
    suspend fun mergeIn(incoming: SyncDocument): SyncDocument = update { SyncMerge.merge(it, incoming) }

    suspend fun upsertPlayer(player: Player): Player = edit { SharedWrites.upsertPlayer(it, player, clock.nowMillis()) }

    suspend fun archivePlayer(id: String, archived: Boolean) {
        update { SharedWrites.archivePlayer(it, id, archived, clock.nowMillis()) }
    }

    fun findPlayerByName(name: String): Player? = SharedWrites.findByName(read(), name)

    suspend fun ensurePlayer(candidate: Player): Player = edit {
        SharedWrites.ensurePlayer(it, candidate, clock.nowMillis())
    }

    suspend fun upsertMatch(match: Match): Match = edit { SharedWrites.upsertMatch(it, match, clock.nowMillis()) }

    suspend fun deleteMatch(id: String) {
        update { SharedWrites.deleteMatch(it, id, clock.nowMillis()) }
    }

    suspend fun saveDraft(record: DraftRecord): DraftRecord = edit {
        SharedWrites.saveDraft(it, record, clock.nowMillis())
    }

    suspend fun deleteDraft(id: String) {
        update { SharedWrites.deleteDraft(it, id, clock.nowMillis()) }
    }

    suspend fun mergePlayers(fromId: String, toId: String): PlayerMergeOutcome =
        edit { SharedWrites.mergePlayers(it, fromId, toId, clock.nowMillis()) }

    suspend fun updateEloConfig(transform: (EloConfig) -> EloConfig) {
        update { SharedWrites.updateEloConfig(it, clock.nowMillis(), transform) }
    }

    suspend fun resetEloConfig() {
        update { SharedWrites.resetEloConfig(it, clock.nowMillis()) }
    }

    private suspend fun <T> edit(write: (SyncDocument) -> Pair<SyncDocument, T>): T {
        var result: T? = null
        update { doc -> write(doc).also { result = it.second }.first }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun load(): SyncDocument {
        AtomicFiles.sweepTemps(file.toAbsolutePath().parent).takeIf { it > 0 }?.let {
            log.warn("Removed {} unfinished write(s) left by a crash; data.json is intact", it)
        }
        val bytes = AtomicFiles.readOrNull(file) ?: return SyncDocument.EMPTY
        return SyncMerge.normalize(SyncJson.decodeFromString(SyncDocument.serializer(), bytes.decodeToString()))
    }

    private fun store(doc: SyncDocument) {
        val bytes = SyncJson.encodeToString(SyncDocument.serializer(), doc).toByteArray()
        AtomicFiles.write(file, bytes)
        val today = clock.today()
        AtomicFiles.write(backups.resolve("$BACKUP_PREFIX$today$BACKUP_SUFFIX"), bytes)
        pruneBackups(today)
    }

    /** Deletes daily copies older than [KEEP_DAYS] days before [today]; returns how many. */
    fun pruneBackups(today: LocalDate = clock.today()): Int {
        if (!Files.isDirectory(backups)) return 0
        val oldest = today.minusDays(KEEP_DAYS - 1L)
        val stale = Files.list(backups).use { files ->
            files.filter { path ->
                val date = backupDate(path.fileName.toString())
                date != null && date.isBefore(oldest)
            }.toList()
        }
        return stale.count { Files.deleteIfExists(it) }
    }

    companion object {
        const val KEEP_DAYS = 30
        private const val BACKUP_PREFIX = "data-"
        private const val BACKUP_SUFFIX = ".json"
        private val log = LoggerFactory.getLogger(GroupStore::class.java)

        internal fun backupDate(name: String): LocalDate? {
            if (!name.startsWith(BACKUP_PREFIX) || !name.endsWith(BACKUP_SUFFIX)) return null
            return try {
                LocalDate.parse(name.removePrefix(BACKUP_PREFIX).removeSuffix(BACKUP_SUFFIX))
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
