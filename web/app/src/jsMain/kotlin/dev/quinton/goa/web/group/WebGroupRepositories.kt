package dev.quinton.goa.web.group

import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.data.player.PlayerMergeRules
import dev.quinton.goa.core.data.player.PlayerNames
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.data.rating.StampedEloConfigStore
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.EditStamp
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncFileCodec
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.domain.sync.protocol.SyncApplyResult
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.web.group.GroupDocumentStore.Change
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * The group's synced data on the web (ADR-043): the same contracts and the same rules as the phone's
 * Room repositories (core:data `RoomPlayerRepository`, `RoomMatchRepository`,
 * `RoomTriviaRoundRepository`, `DataStoreEloConfigRepository`, `RoomSyncRepository`), over one
 * [GroupDocumentStore]. Every write stamps `updatedAt` with [EditStamp.next] (later than the previous
 * edit and any tombstone), a delete leaves a tombstone in the same transaction, reads resolve player
 * merges, and lists come in the phone's orders.
 */

/** SQLite's `COLLATE NOCASE`: only ASCII letters fold. */
private fun nocase(text: String): String = buildString(text.length) {
    text.forEach { append(if (it in 'A'..'Z') it + ('a' - 'A') else it) }
}

private val BY_NAME = compareBy<Player>({ nocase(it.name) }, { it.id })
private val BY_AGE = compareBy<Player>({ it.createdAt }, { it.id })

/** The tombstone of [kind] [id], if any. */
private fun SyncDocument.deletedAt(kind: DeletionKind, id: String): Long? =
    deletions.firstOrNull { it.kind == kind && it.id == id }?.deletedAt

/** [deletions] with [deletion] replacing any earlier one of the same record (Room's upsert by key). */
private fun SyncDocument.withDeletion(deletion: Deletion): List<Deletion> =
    deletions.filterNot { it.kind == deletion.kind && it.id == deletion.id } + deletion

private fun <T> List<T>.replacing(item: T, id: (T) -> String): List<T> =
    filterNot { id(it) == id(item) } + item

/** Players: archived, never deleted; a merged-away duplicate reads as the player it joined. */
class WebPlayerRepository(private val docs: GroupDocumentStore, private val clock: EditClock) :
    PlayerRepository,
    PlayerMergeRepository {
    /** Serialises [ensurePlayer] so two quick adds of one name can't both insert. */
    private val ensureLock = Mutex()

    override fun observeMerges(): Flow<PlayerMerges> =
        docs.document.map { it.playerMerges }.distinctUntilChanged().map { PlayerMerges(it) }

    override fun observePlayers(includeArchived: Boolean): Flow<List<Player>> = docs.document.map { doc ->
        val list = doc.players.filter { includeArchived || !it.archived }.sortedWith(BY_NAME)
        PlayerMerges(doc.playerMerges).visible(list)
    }.distinctUntilChanged()

    override fun observePlayer(id: String): Flow<Player?> = docs.document.map { doc ->
        val resolved = PlayerMerges(doc.playerMerges).resolve(id)
        doc.players.firstOrNull { it.id == resolved }
    }.distinctUntilChanged()

    override suspend fun upsert(player: Player) {
        require(player.name.isNotBlank()) { "Player name must not be blank" }
        docs.update { doc ->
            val stamp = EditStamp.next(clock.now(), doc.players.firstOrNull { it.id == player.id }?.updatedAt)
            val saved = player.copy(name = player.name.trim(), updatedAt = stamp)
            Change(doc.copy(players = doc.players.replacing(saved, Player::id)), Unit)
        }
    }

    override suspend fun archive(id: String, archived: Boolean) {
        docs.update { doc ->
            val previous = doc.players.firstOrNull { it.id == id } ?: return@update Change(doc, Unit)
            val saved = previous.copy(archived = archived, updatedAt = EditStamp.next(clock.now(), previous.updatedAt))
            Change(doc.copy(players = doc.players.replacing(saved, Player::id)), Unit)
        }
    }

    /** Oldest match first, so a group that already has duplicates keeps using the original. */
    override suspend fun findByName(name: String): Player? {
        val doc = docs.current()
        return PlayerNames.find(doc.players.sortedWith(BY_AGE), PlayerMerges(doc.playerMerges), name)
    }

    override suspend fun ensurePlayer(candidate: Player): Player = ensureLock.withLock {
        require(candidate.name.isNotBlank()) { "Player name must not be blank" }
        val existing = findByName(candidate.name)
        when {
            existing == null -> candidate.copy(name = candidate.name.trim()).also { upsert(it) }
            existing.archived -> existing.copy(archived = false).also { archive(it.id, false) }
            else -> existing
        }
    }

    override suspend fun merge(fromId: String, toId: String): PlayerMergeResult = docs.update { doc ->
        val merges = PlayerMerges(doc.playerMerges)
        val bothExist = doc.players.any { it.id == fromId } && doc.players.any { it.id == toId }
        val result = PlayerMergeRules.check(fromId, toId, bothExist, merges) { doc.matches }
        if (result != PlayerMergeResult.Merged) return@update Change(doc, result)
        val previous = doc.playerMerges.firstOrNull { it.fromId == fromId }?.mergedAt
        val merge = PlayerMergeRules.merge(fromId, toId, clock.now(), previous)
        Change(doc.copy(playerMerges = doc.playerMerges.filterNot { it.fromId == fromId } + merge), result)
    }
}

/** The match log, newest first, merged players resolved (source of truth, ADR-003). */
class WebMatchRepository(private val docs: GroupDocumentStore, private val clock: EditClock) : MatchRepository {
    override fun observeMatches(): Flow<List<Match>> = docs.document.map { doc ->
        val newestFirst = doc.matches.sortedWith(
            compareByDescending<Match> { it.playedAt }.thenByDescending { it.createdAt }.thenByDescending { it.id },
        )
        PlayerMerges(doc.playerMerges).resolve(newestFirst)
    }.distinctUntilChanged()

    override fun observeMatch(id: String): Flow<Match?> = docs.document.map { doc ->
        doc.matches.firstOrNull { it.id == id }?.let(PlayerMerges(doc.playerMerges)::resolve)
    }.distinctUntilChanged()

    override suspend fun upsert(match: Match) {
        docs.update { doc ->
            val stamp = EditStamp.next(
                clock.now(),
                doc.matches.firstOrNull { it.id == match.id }?.updatedAt,
                doc.deletedAt(DeletionKind.MATCH, match.id),
            )
            Change(doc.copy(matches = doc.matches.replacing(match.copy(updatedAt = stamp), Match::id)), Unit)
        }
    }

    override suspend fun delete(id: String) {
        docs.update { doc ->
            val previous = doc.matches.firstOrNull { it.id == id } ?: return@update Change(doc, Unit)
            val at = EditStamp.next(clock.now(), previous.updatedAt, doc.deletedAt(DeletionKind.MATCH, id))
            Change(
                doc.copy(
                    matches = doc.matches.filterNot { it.id == id },
                    deletions = doc.withDeletion(Deletion(DeletionKind.MATCH, id, at)),
                ),
                Unit,
            )
        }
    }
}

/** Finished drafts, newest first; writes are stamped like matches. */
class WebDraftRecordRepository(private val docs: GroupDocumentStore, private val clock: EditClock) :
    DraftRecordRepository {
    override fun observeDrafts(): Flow<List<DraftRecord>> = docs.document.map { doc ->
        doc.drafts.sortedWith(compareByDescending<DraftRecord> { it.createdAt }.thenByDescending { it.id })
    }.distinctUntilChanged()

    override suspend fun save(record: DraftRecord) {
        docs.update { doc ->
            val stamp = EditStamp.next(
                clock.now(),
                doc.drafts.firstOrNull { it.id == record.id }?.updatedAt,
                doc.deletedAt(DeletionKind.DRAFT, record.id),
            )
            Change(doc.copy(drafts = doc.drafts.replacing(record.copy(updatedAt = stamp), DraftRecord::id)), Unit)
        }
    }
}

/** Finished trivia rounds, oldest first, players resolved through merges (ADR-032). */
class WebTriviaRoundRepository(private val docs: GroupDocumentStore, private val clock: EditClock) :
    TriviaRoundRepository {
    override fun observeRounds(): Flow<List<TriviaRound>> = docs.document.map { doc ->
        val merges = PlayerMerges(doc.playerMerges)
        doc.triviaRounds.sortedWith(compareBy<TriviaRound>({ it.playedAt }, { it.id })).map { round ->
            val playerId = merges.resolve(round.playerId)
            if (playerId == round.playerId) round else round.copy(playerId = playerId)
        }
    }.distinctUntilChanged()

    override suspend fun add(round: TriviaRound) {
        docs.update { doc ->
            val stamp = EditStamp.next(
                clock.now(),
                doc.triviaRounds.firstOrNull { it.id == round.id }?.updatedAt,
                doc.deletedAt(DeletionKind.TRIVIA_ROUND, round.id),
            )
            Change(
                doc.copy(triviaRounds = doc.triviaRounds.replacing(round.copy(updatedAt = stamp), TriviaRound::id)),
                Unit,
            )
        }
    }

    override suspend fun delete(id: String) {
        docs.update { doc ->
            val previous = doc.triviaRounds.firstOrNull { it.id == id } ?: return@update Change(doc, Unit)
            val at = EditStamp.next(clock.now(), previous.updatedAt, doc.deletedAt(DeletionKind.TRIVIA_ROUND, id))
            Change(
                doc.copy(
                    triviaRounds = doc.triviaRounds.filterNot { it.id == id },
                    deletions = doc.withDeletion(Deletion(DeletionKind.TRIVIA_ROUND, id, at)),
                ),
                Unit,
            )
        }
    }
}

/**
 * The group's rating constants (Settings → Ratings), stamped like every synced write: they travel
 * with the group's data and the later change wins (ADR-025). Stored sanitised, as on the phone.
 */
class WebEloConfigRepository(private val docs: GroupDocumentStore, private val clock: EditClock) :
    EloConfigRepository,
    StampedEloConfigStore {
    override val config: Flow<EloConfig> = docs.document.map { it.eloConfig.config.sanitized() }.distinctUntilChanged()

    override suspend fun update(transform: (EloConfig) -> EloConfig) {
        docs.update { doc ->
            val current = doc.eloConfig
            val next = StampedEloConfig(
                transform(current.config.sanitized()).sanitized(),
                EditStamp.next(clock.now(), current.updatedAt),
            )
            Change(doc.copy(eloConfig = next), Unit)
        }
    }

    override suspend fun reset() {
        docs.update { doc ->
            Change(
                doc.copy(
                    eloConfig = StampedEloConfig(
                        EloConfig.DEFAULT,
                        EditStamp.next(clock.now(), doc.eloConfig.updatedAt),
                    ),
                ),
                Unit,
            )
        }
    }

    override suspend fun stamped(): StampedEloConfig = docs.current().eloConfig.let {
        it.copy(config = it.config.sanitized())
    }

    override suspend fun applySynced(config: StampedEloConfig) {
        docs.update { doc -> Change(doc.copy(eloConfig = config.copy(config = config.config.sanitized())), Unit) }
    }
}

/**
 * This browser's side of group sync ([dev.quinton.goa.core.domain.sync.protocol.SyncDataPort]): the
 * whole shareable data as a canonical [SyncDocument], and merging a received one in, in one
 * transaction, exactly as the phone's `RoomSyncRepository` (rating constants included: here they
 * live in the same document, so they merge in the same write).
 */
class WebSyncRepository(private val docs: GroupDocumentStore, private val clock: EditClock) : SyncRepository {
    override suspend fun export(): SyncDocument = SyncMerge.normalize(docs.current())

    override suspend fun apply(incoming: SyncDocument): SyncApplyResult = docs.update { doc ->
        val before = SyncMerge.normalize(doc)
        val merged = SyncMerge.merge(before, incoming)
        val stored = merged.copy(eloConfig = merged.eloConfig.copy(config = merged.eloConfig.config.sanitized()))
        Change(stored, SyncApplyResult(merged, SyncMerge.changes(before, merged)))
    }

    override suspend fun exportFile(group: GroupCredentials): ByteArray =
        SyncFileCodec.encodeFile(export(), group, createdAt = clock.now(), cipher = WebCryptoCipher)

    override suspend fun importFile(bytes: ByteArray, group: GroupCredentials?): SyncApplyResult =
        apply(SyncFileCodec.decodeFile(bytes, group, WebCryptoCipher))

    /** The stored document right now (tests, diagnostics). */
    suspend fun stored(): SyncDocument = docs.document.first()
}
