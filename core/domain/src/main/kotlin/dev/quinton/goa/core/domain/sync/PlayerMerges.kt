package dev.quinton.goa.core.domain.sync

import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.PlayerMerge

/**
 * Resolves merged player ids (DOMAIN.md *Duplicate players*). Matches keep the ids they were
 * logged with; every read maps them through [resolve], so the match log stays the source of
 * truth and ratings come out the same on every phone holding the same merges.
 *
 * A phone only merges a live player into another live player, so a chain never loops on one
 * phone. Two phones can still merge X → Y and Y → X before they sync; the union then holds a
 * cycle, and every id in it resolves to the cycle's smallest id, which every phone computes alike.
 */
class PlayerMerges(merges: Collection<PlayerMerge>) {
    /** One target per id: the same winner [SyncMerge] keeps for a conflicting pair. */
    private val next: Map<String, String> = merges
        .filter { it.fromId != it.toId }
        .groupBy { it.fromId }
        .mapValues { (_, list) -> list.reduce(SyncMerge::newerMerge).toId }

    /** Every merged id and the id it now stands for (immutable, so one instance can be shared). */
    private val resolved: Map<String, String> = next.keys.associateWith(::follow)

    val isEmpty: Boolean get() = next.isEmpty()

    /** Ids that were merged into someone else (they no longer show as players). */
    val mergedAway: Set<String> = resolved.filterTo(HashMap()) { (from, to) -> from != to }.keys

    /** The player [id] now stands for. */
    fun resolve(id: String): String = resolved[id] ?: id

    private fun follow(id: String): String {
        val path = ArrayList<String>()
        val seen = HashMap<String, Int>()
        var current = id
        while (current !in seen) {
            seen[current] = path.size
            path += current
            current = next[current] ?: return current
        }
        // Cycle guard: every id on the loop resolves to its smallest member.
        return path.subList(seen.getValue(current), path.size).min()
    }

    /** [match] with every pilot resolved. A match without merged pilots is returned as is. */
    fun resolve(match: Match): Match {
        if (match.participants.none { it.playerId in mergedAway }) return match
        return match.copy(participants = match.participants.map { it.copy(playerId = resolve(it.playerId)) })
    }

    fun resolve(matches: List<Match>): List<Match> = if (isEmpty) matches else matches.map(::resolve)

    /** Players still standing for themselves (merged-away duplicates are hidden). */
    fun visible(players: List<Player>): List<Player> =
        if (mergedAway.isEmpty()) players else players.filterNot { it.id in mergedAway }

    companion object {
        val NONE = PlayerMerges(emptyList())

        /**
         * Players as the app counts them ("8 players" on the Group screen): active ones, merged-away
         * duplicates left out. A file's summary uses this, not its raw player list.
         */
        fun shownCount(players: List<Player>, merges: Collection<PlayerMerge>): Int =
            PlayerMerges(merges).visible(players.filterNot { it.archived }).size

        /**
         * Why [from] can't be merged into [to]: the matches both played in. On opposite teams the
         * merge would put one player on both sides (no rating can count that); on one team they
         * were clearly two people. Empty = the merge is allowed.
         */
        fun conflicts(from: String, to: String, matches: List<Match>): List<Match> = matches.filter { m ->
            m.participants.any { it.playerId == from } && m.participants.any { it.playerId == to }
        }
    }
}
