package dev.quinton.goa.core.data.backup

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.UnknownHeroes
import dev.quinton.goa.core.domain.trivia.TriviaRoundValidation
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.core.model.trivia.TriviaRound
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Versioned, human-readable backup of all user data (PRODUCT.md §3). Ratings are not included:
 * they are rebuilt from the match log after import (ADR-003).
 *
 * Versioning: bump [CURRENT_VERSION] whenever an older app would lose data from a newer file
 * (it ignores unknown keys, so it would silently drop a new field), and keep [BackupCodec]
 * reading every older version. Older files always import: new fields take their defaults.
 *
 * - v1: players, matches, drafts, Elo config, extras.
 * - v2: `Match.tieBreaker` (the Tie Breaker coin at setup). v1 files import with it null.
 * - v3: `extras` filled by the contributors (`settings`, `timer-presets`, `draft-methods`) and
 *   `appVersion` set. v1/v2 files carry no extras: the phone's settings, presets and custom
 *   methods stay as they are (Replace keeps them too, it only resets the rating constants).
 * - v4 (group sync, ADR-025): `updatedAt` on players, matches and drafts, the `deletions`
 *   tombstones, `playerMerges` and `eloConfigUpdatedAt`. Older files import with each record
 *   last edited when it was created (matches: created or played, whichever is later), no
 *   tombstones or merges, and rating constants that never win over the group's.
 * - v5: `triviaRounds` (the trivia leaderboard, ADR-032; their tombstones are `deletions`) and
 *   `boards` (the board sandbox, ADR-033; null when written without it). Older files have
 *   neither: Replace then clears the rounds with the rest of the synced data (the group brings its
 *   own back at the next sync) but keeps the phone's boards, which a file without a `boards` list
 *   says nothing about.
 */
@Serializable
data class BackupDocument(
    val format: String = FORMAT,
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val appVersion: String? = null,
    val players: List<Player> = emptyList(),
    val matches: List<Match> = emptyList(),
    val drafts: List<DraftRecord> = emptyList(),
    val eloConfig: EloConfig? = null,
    /** When the rating constants were last changed (v4); null in older files. */
    val eloConfigUpdatedAt: Long? = null,
    /** Tombstones of deleted matches and drafts (v4). */
    val deletions: List<Deletion> = emptyList(),
    /** Duplicate players merged into others (v4). */
    val playerMerges: List<PlayerMerge> = emptyList(),
    /** Finished trivia rounds played as a player (v5). */
    val triviaRounds: List<TriviaRound> = emptyList(),
    /**
     * Board sandbox boards (v5; this phone's own, never synced). Null when the file has none to
     * say anything about: written by a build without the board sandbox, or older than v5. Then
     * the phone's boards are left alone.
     */
    val boards: List<SavedBoard>? = null,
    /** Data from [BackupContributor]s (timer presets, custom draft methods, …) keyed by [BackupContributor.key]. */
    val extras: Map<String, JsonElement> = emptyMap(),
) {
    companion object {
        const val FORMAT = "goa-companion-backup"
        const val CURRENT_VERSION = 5
        const val MIME_TYPE = "application/json"
    }
}

/** How an import treats data already on the phone. */
enum class ImportMode {
    /**
     * Wipe players, matches, drafts, trivia rounds, tombstones, merges, the boards (when the file
     * carries them, v5) and the settings covered by the backup, then load it as it is (records
     * keep their `updatedAt`, so the group's newer edits win at the next sync: restoring an old
     * backup never overwrites the group).
     */
    REPLACE,

    /**
     * Keep local data and merge the backup in like a group sync: new items are added, and for an
     * item on both, the later edit wins (ADR-025). Settings (rating constants, app settings, the
     * selected timer preset) stay as they are on the phone unless the user asks for the backup's
     * settings (`useBackupSettings`).
     */
    MERGE,
}

/** What a backup file contains, shown before the user confirms an import. */
data class BackupSummary(
    val version: Int,
    val exportedAt: Long,
    val players: Int,
    val matches: Int,
    val drafts: Int,
    val hasEloConfig: Boolean,
    val extras: Set<String>,
    /** Hero ids this app's catalog doesn't know; they show as "Unknown hero" (DOMAIN.md). */
    val unknownHeroes: Set<String> = emptySet(),
    val appVersion: String? = null,
    val triviaRounds: Int = 0,
    /** Boards in the file; null when the file has no boards list or this build has no board sandbox. */
    val boards: Int? = null,
) {
    /** The file carries settings beyond the match log (rating constants, app settings, presets…). */
    val hasSettings: Boolean get() = hasEloConfig || extras.isNotEmpty()
}

/** [knownHeroIds]: the catalog's hero ids, or null when the catalog isn't available. */
fun BackupDocument.summary(knownHeroIds: Set<String>? = null) = BackupSummary(
    version = version,
    exportedAt = exportedAt,
    players = players.size,
    matches = matches.size,
    drafts = drafts.size,
    hasEloConfig = eloConfig != null,
    extras = extras.keys,
    unknownHeroes = knownHeroIds?.let { unknownHeroIds(it) }.orEmpty(),
    appVersion = appVersion,
    triviaRounds = triviaRounds.size,
    boards = boards?.size,
)

/**
 * Hero ids in matches and drafts that aren't in [known] (an empty catalog knows nothing, so none).
 * They are stored as they are and read as "Unknown hero" ([UnknownHeroes]).
 */
fun BackupDocument.unknownHeroIds(known: Set<String>): Set<String> = UnknownHeroes.ids(matches, drafts, known)

/** The file isn't a backup this version of the app can read. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
        // Board pieces are polymorphic and a minion has a `type` property, which the default
        // discriminator ("type") would clash with. Nothing else in the file is polymorphic.
        classDiscriminator = "piece"
    }

    fun encode(document: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), document)

    /** Parses and validates; throws [InvalidBackupException] with a user-readable message. */
    fun decode(text: String): BackupDocument {
        val document = try {
            json.decodeFromString(BackupDocument.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            throw InvalidBackupException("This file isn't a Guards of Atlantis backup.", e)
        }
        problem(document)?.let { throw InvalidBackupException(it) }
        return document
    }

    /** A user-readable reason the document can't be imported, or null if it's fine. */
    private fun problem(document: BackupDocument): String? {
        fun List<String>.unique() = toSet().size == size && none { it.isBlank() }
        val idsOk = document.players.map { it.id }.unique() &&
            document.matches.map { it.id }.unique() &&
            document.drafts.map { it.id }.unique() &&
            document.triviaRounds.map { it.id }.unique() &&
            document.boards.orEmpty().map { it.id }.unique() &&
            document.playerMerges.map { it.fromId }.unique()
        val playerIds = document.players.mapTo(HashSet()) { it.id }
        return when {
            document.format != BackupDocument.FORMAT -> "This file isn't a Guards of Atlantis backup."

            document.version > BackupDocument.CURRENT_VERSION ->
                "This backup was made by a newer version of the app. Update the app first."

            document.version < 1 -> "Unknown backup version ${document.version}."

            !idsOk -> "The backup contains duplicate or empty ids."

            // The phone would refuse to sync data like this (SyncValidation).
            document.playerMerges.any { it.fromId !in playerIds || it.toId !in playerIds } ->
                "The backup merges a player who isn't in it."

            else -> invalidMatches(document) ?: invalidRounds(document)
        }
    }

    /** Rounds the app would never write, or whose player isn't in the file (SyncValidation's rule). */
    private fun invalidRounds(document: BackupDocument): String? {
        val playerIds = document.players.mapTo(HashSet()) { it.id }
        val lines = TriviaRoundValidation.lines(document.triviaRounds, playerIds) { "player $it isn't in this backup" }
        if (lines.isEmpty()) return null
        return (listOf("This backup has trivia rounds the app can't import:") + listed(lines)).joinToString("\n")
    }

    private fun listed(lines: List<String>): List<String> = lines.take(MAX_LISTED) + listOfNotNull(
        (lines.size - MAX_LISTED).takeIf { it > 0 }?.let { "…and $it more." },
    )

    /**
     * Matches the match editor would never save: a hero listed twice, a player on both teams, or
     * a pilot who isn't in the file (it would become a ghost rated player). Lists each one by date
     * and id so the user can find it. Unknown *heroes* are not an error: they are kept and read as
     * "Unknown hero" ([UnknownHeroes]).
     */
    private fun invalidMatches(document: BackupDocument): String? {
        val names = document.players.associate { it.id to it.name }
        val lines = document.matches.sortedWith(compareBy({ it.playedAt }, { it.id })).flatMap { match ->
            val label = "${dateOf(match.playedAt)} (id ${match.id})"
            val heroes = match.participants.mapNotNull { it.heroId }
            val twice = heroes.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
            val red = match.team(Team.RED).map { it.playerId }.toSet()
            val both = match.team(Team.BLUE).map { it.playerId }.distinct().filter { it in red }
            val missing = match.participants.map { it.playerId }.distinct().filterNot { it in names }
            twice.map { "• $label: hero $it is listed twice." } +
                both.map { "• $label: ${names[it] ?: it} is on both teams." } +
                missing.map { "• $label: player $it isn't in this backup." }
        }
        if (lines.isEmpty()) return null
        return (listOf("This backup has matches the app can't import:") + listed(lines)).joinToString(separator = "\n")
    }

    private const val MAX_LISTED = 5

    private fun dateOf(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
