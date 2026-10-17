package dev.quinton.goa.core.data.backup

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
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
    /** Data from [BackupContributor]s (timer presets, custom draft methods, …) keyed by [BackupContributor.key]. */
    val extras: Map<String, JsonElement> = emptyMap(),
) {
    companion object {
        const val FORMAT = "goa-companion-backup"
        const val CURRENT_VERSION = 3
        const val MIME_TYPE = "application/json"
    }
}

/** How an import treats data already on the phone. */
enum class ImportMode {
    /** Wipe players, matches, drafts and settings covered by the backup, then load it. */
    REPLACE,

    /**
     * Keep local data; add backup items and overwrite local items with the same id. Settings
     * (rating constants, app settings, the selected timer preset) stay as they are on the phone
     * unless the user asks for the backup's settings (`useBackupSettings`).
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
    /** Hero ids this app's catalog doesn't know; they import as "Unknown hero" (DOMAIN.md). */
    val unknownHeroes: Set<String> = emptySet(),
    val appVersion: String? = null,
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
)

/** Hero ids in matches and drafts that aren't in [known] (an empty catalog knows nothing, so none). */
fun BackupDocument.unknownHeroIds(known: Set<String>): Set<String> {
    if (known.isEmpty()) return emptySet()
    val used = matches.flatMap { m -> m.participants.mapNotNull { it.heroId } } +
        drafts.flatMap { d -> d.steps.map { it.heroId } + d.pool }
    return used.filterNot { it in known }.toSortedSet()
}

/**
 * DOMAIN.md's unknown-hero rule applied to an imported file: a seat whose hero this catalog
 * doesn't know keeps its pilot but becomes an "Unknown hero" seat (`heroId = null`), so it never
 * counts as a hero in ratings or meta stats; draft bans, picks and pool entries naming such a hero
 * are dropped. An empty [known] set (catalog unavailable) leaves the document unchanged.
 */
fun BackupDocument.withUnknownHeroesCleared(known: Set<String>): BackupDocument {
    if (unknownHeroIds(known).isEmpty()) return this
    return copy(
        matches = matches.map { m ->
            m.copy(participants = m.participants.map { p -> if (p.heroId in known) p else p.copy(heroId = null) })
        },
        drafts = drafts.map { d ->
            d.copy(pool = d.pool.filter { it in known }, steps = d.steps.filter { it.heroId in known })
        },
    )
}

/** The file isn't a backup this version of the app can read. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
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
            document.drafts.map { it.id }.unique()
        return when {
            document.format != BackupDocument.FORMAT -> "This file isn't a Guards of Atlantis backup."

            document.version > BackupDocument.CURRENT_VERSION ->
                "This backup was made by a newer version of the app. Update the app first."

            document.version < 1 -> "Unknown backup version ${document.version}."

            !idsOk -> "The backup contains duplicate or empty ids."

            else -> invalidMatches(document)
        }
    }

    /**
     * Matches the match editor would never save: a hero listed twice, a player on both teams, or
     * a pilot who isn't in the file (it would become a ghost rated player). Lists each one by date
     * and id so the user can find it. Unknown *heroes* are not an error: they import as
     * "Unknown hero" ([withUnknownHeroesCleared]).
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
        val shown = lines.take(MAX_LISTED) + listOfNotNull(
            (lines.size - MAX_LISTED).takeIf { it > 0 }?.let { "…and $it more." },
        )
        return (listOf("This backup has matches the app can't import:") + shown).joinToString(separator = "\n")
    }

    private const val MAX_LISTED = 5

    private fun dateOf(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
