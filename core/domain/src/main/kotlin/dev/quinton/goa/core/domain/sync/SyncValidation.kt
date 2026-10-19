package dev.quinton.goa.core.domain.sync

import dev.quinton.goa.core.domain.trivia.TriviaRoundValidation
import dev.quinton.goa.core.model.match.Team
import java.time.Instant
import java.time.ZoneId

/**
 * The same validity rules as a backup import (BackupCodec): a received document that breaks one
 * is refused as a whole, so a bad match or trivia round from one phone never spreads through the
 * group.
 */
object SyncValidation {
    /** A user-readable reason [doc] can't be merged, or null if it's fine. */
    fun problem(doc: SyncDocument): String? {
        fun List<String>.unique() = toSet().size == size && none { it.isBlank() }
        val idsOk = doc.players.map { it.id }.unique() && doc.matches.map { it.id }.unique() &&
            doc.drafts.map { it.id }.unique() && doc.triviaRounds.map { it.id }.unique() &&
            doc.playerMerges.map { it.fromId }.unique()
        val playerIds = doc.players.mapTo(HashSet()) { it.id }
        return when {
            doc.format != SyncDocument.FORMAT -> "This isn't group sync data."

            doc.version > SyncDocument.VERSION ->
                "The other phone has a newer version of the app. Update this one first."

            doc.version < SyncDocument.VERSION -> "The other phone has an older version of the app. Update it first."

            !idsOk -> "The data contains duplicate or empty ids."

            doc.playerMerges.any { it.fromId !in playerIds || it.toId !in playerIds } ->
                "The data merges a player who isn't in it."

            else -> invalidMatches(doc, playerIds) ?: invalidRounds(doc, playerIds)
        }
    }

    private fun invalidRounds(doc: SyncDocument, playerIds: Set<String>): String? {
        val lines = TriviaRoundValidation.lines(doc.triviaRounds, playerIds) { "player $it is missing" }
        if (lines.isEmpty()) return null
        return (listOf("The other phone has trivia rounds this app can't take:") + listed(lines)).joinToString("\n")
    }

    private fun listed(lines: List<String>): List<String> = lines.take(MAX_LISTED) + listOfNotNull(
        (lines.size - MAX_LISTED).takeIf { it > 0 }?.let { "…and $it more." },
    )

    private fun invalidMatches(doc: SyncDocument, playerIds: Set<String>): String? {
        val names = doc.players.associate { it.id to it.name }
        val lines = doc.matches.sortedWith(compareBy({ it.playedAt }, { it.id })).flatMap { match ->
            val label = "${dateOf(match.playedAt)} (id ${match.id})"
            val heroes = match.participants.mapNotNull { it.heroId }
            val twice = heroes.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
            val red = match.team(Team.RED).map { it.playerId }.toSet()
            val both = match.team(Team.BLUE).map { it.playerId }.distinct().filter { it in red }
            val missing = match.participants.map { it.playerId }.distinct().filterNot { it in playerIds }
            twice.map { "• $label: hero $it is listed twice." } +
                both.map { "• $label: ${names[it] ?: it} is on both teams." } +
                missing.map { "• $label: player $it is missing." }
        }
        if (lines.isEmpty()) return null
        return (listOf("The other phone has matches this app can't take:") + listed(lines)).joinToString("\n")
    }

    private const val MAX_LISTED = 5

    private fun dateOf(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
