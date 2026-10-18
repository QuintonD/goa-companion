package dev.quinton.goa.core.domain.player

import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import java.text.Normalizer
import java.util.Locale

/**
 * "Same people?" (ADR-031): players whose names suggest one person entered twice, e.g. once by
 * the owner and once by the friend on their own phone. Names are compared by [matchKey]: trimmed,
 * lowercased, accents and everything but letters stripped ("José B." → "joseb"). Two keys are
 * alike when they are equal, when one starts with the other and the shorter has at least
 * [MIN_PREFIX] letters ("Quin"/"Quinton", never "Q"/"Quinton"), or when they are one
 * Damerau–Levenshtein edit apart and the shorter has at least [MIN_ONE_EDIT] letters (two edits
 * from [MIN_TWO_EDITS]). Three-letter names one letter apart ("Rob"/"Bob", "Tom"/"Tim") are too
 * often two people to suggest; a personal invite links those.
 *
 * Pairs that played in the same match are never suggested: one person can't sit on both teams,
 * and two teammates are two people (the merge would be refused anyway).
 */
object PlayerMatchUp {
    const val MIN_PREFIX = 3
    const val MIN_ONE_EDIT = 4
    const val MIN_TWO_EDITS = 8

    /** How alike two names are, closest first. */
    enum class Likeness { SAME_NAME, PREFIX, SIMILAR }

    /**
     * Two players that might be one person. [keep] is the older one (created first, then the
     * smaller id): "Same person" merges [other] into it, so the player everyone has had longest stays.
     */
    data class PlayerPair(val keep: Player, val other: Player, val likeness: Likeness) {
        /** Remembers "Different people" for this pair (order-free). */
        val key: String get() = pairKey(keep.id, other.id)
    }

    /** Older first: the one a merge keeps. */
    val OLDER_FIRST: Comparator<Player> = compareBy<Player>({ it.createdAt }, { it.id })

    private val MARKS = Regex("\\p{M}+")

    /** The name as compared: trimmed, lowercased, accents and non-letters stripped. */
    fun matchKey(name: String): String =
        Normalizer.normalize(name.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .filter(Char::isLetter)

    /** How alike names [a] and [b] are, or null when they don't look like one person. */
    fun likeness(a: String, b: String): Likeness? = likenessOfKeys(matchKey(a), matchKey(b), a, b)

    private fun likenessOfKeys(ka: String, kb: String, a: String, b: String): Likeness? {
        // A name with no letters at all (digits, emoji) only matches itself.
        if (ka.isEmpty() || kb.isEmpty()) return Likeness.SAME_NAME.takeIf { PlayerNames.same(a, b) }
        if (ka == kb) return Likeness.SAME_NAME
        val (short, long) = if (ka.length <= kb.length) ka to kb else kb to ka
        if (short.length >= MIN_PREFIX && long.startsWith(short)) return Likeness.PREFIX
        val allowed = when {
            short.length >= MIN_TWO_EDITS -> 2
            short.length >= MIN_ONE_EDIT -> 1
            else -> 0
        }
        if (allowed == 0 || long.length - short.length > allowed) return null
        return Likeness.SIMILAR.takeIf { editDistance(ka, kb) <= allowed }
    }

    /**
     * Pairs among the active [players] (archived and merged-away ones left out) that might be one
     * person, closest first, then by name. Never a pair that played in one of [matches] together,
     * nor one whose [pairKey] is in [dismissed] ("Different people", resolved through [merges]).
     * The result doesn't depend on the order of [players] or [matches].
     */
    fun suggest(
        players: List<Player>,
        matches: List<Match>,
        merges: PlayerMerges = PlayerMerges.NONE,
        dismissed: Set<String> = emptySet(),
    ): List<PlayerPair> {
        val active = merges.visible(players).filterNot { it.archived }.distinctBy { it.id }.sortedWith(OLDER_FIRST)
        if (active.size < 2) return emptyList()
        val keys = active.associate { it.id to matchKey(it.name) }
        val playedIn = HashMap<String, MutableSet<String>>()
        merges.resolve(matches).forEach { match ->
            match.playerIds.forEach { id -> playedIn.getOrPut(id, ::HashSet) += match.id }
        }
        val different = dismissed.mapNotNullTo(HashSet()) { key ->
            splitKey(key)?.let { (a, b) -> pairKey(merges.resolve(a), merges.resolve(b)) }
        }
        val pairs = ArrayList<PlayerPair>()
        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val keep = active[i]
                val other = active[j]
                val likeness = likenessOfKeys(keys.getValue(keep.id), keys.getValue(other.id), keep.name, other.name)
                    ?: continue
                if (pairKey(keep.id, other.id) in different) continue
                val mine = playedIn[keep.id].orEmpty()
                if (playedIn[other.id].orEmpty().any { it in mine }) continue
                pairs += PlayerPair(keep, other, likeness)
            }
        }
        return pairs.sortedWith(
            compareBy<PlayerPair>(
                { it.likeness },
                { keys.getValue(it.keep.id) },
                { keys.getValue(it.other.id) },
                { it.keep.id },
                { it.other.id },
            ),
        )
    }

    /**
     * "Add me" checks first: the [players] whose names are like [name] (same rule as [suggest],
     * an exact name included), closest first. The caller passes the players to offer.
     */
    fun similar(name: String, players: List<Player>): List<Player> {
        val key = matchKey(name)
        return players.mapNotNull { p -> likenessOfKeys(key, matchKey(p.name), name, p.name)?.let { p to it } }
            .sortedWith(compareBy({ it.second }, { matchKey(it.first.name) }, { it.first.id }))
            .map { it.first }
    }

    /** One key per unordered pair of ids. */
    fun pairKey(a: String, b: String): String = if (a <= b) "$a$SEPARATOR$b" else "$b$SEPARATOR$a"

    private fun splitKey(key: String): Pair<String, String>? {
        val at = key.indexOf(SEPARATOR)
        if (at <= 0 || at == key.lastIndex) return null
        return key.substring(0, at) to key.substring(at + 1)
    }

    // Ids never hold whitespace (UUIDs here, checked in invites), so a newline can't be inside one.
    private const val SEPARATOR = '\n'

    /** Damerau–Levenshtein distance (optimal string alignment: a swap of two neighbours is one edit). */
    internal fun editDistance(a: String, b: String): Int {
        val rows = a.length + 1
        val cols = b.length + 1
        val d = Array(rows) { IntArray(cols) }
        for (i in 0 until rows) d[i][0] = i
        for (j in 0 until cols) d[0][j] = j
        for (i in 1 until rows) {
            for (j in 1 until cols) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = minOf(best, d[i - 2][j - 2] + 1)
                }
                d[i][j] = best
            }
        }
        return d[a.length][b.length]
    }
}
