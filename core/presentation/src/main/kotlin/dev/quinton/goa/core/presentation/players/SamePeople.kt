package dev.quinton.goa.core.presentation.players

import dev.quinton.goa.core.domain.player.PlayerMatchUp
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import java.time.ZoneId

/** One side of a pair: who, how many matches, when last. */
data class PairSide(val player: Player, val games: Int, val lastPlayed: Long?)

data class SamePeoplePair(val key: String, val keep: PairSide, val other: PairSide)

sealed interface SamePeopleOutcome {
    data class Merged(val fromName: String, val intoName: String) : SamePeopleOutcome

    /** Refused: the two played in these matches (one line each). */
    data class PlayedTogether(val matches: List<String>) : SamePeopleOutcome
}

data class SamePeopleUiState(
    val loading: Boolean = true,
    /** Closest first; "Same person" merges [SamePeoplePair.other] into [SamePeoplePair.keep]. */
    val pairs: List<SamePeoplePair> = emptyList(),
    /** Pairs this phone was told are different people ("Ask again" forgets them). */
    val differentCount: Int = 0,
    val outcome: SamePeopleOutcome? = null,
) {
    /** "Ask again about the pair you said were different", or null when there is none. */
    val askAgainText: String?
        get() = when (differentCount) {
            0 -> null
            1 -> "Ask again about the pair you said were different"
            else -> "Ask again about the $differentCount pairs you said were different"
        }
}

/**
 * "Same people?" (ADR-031): pairs of players whose names suggest one person entered twice, from the
 * current data (any sync, any device). "Same person" merges [SamePeoplePair.other] (the newer) into
 * [SamePeoplePair.keep]; "Different people" answers are kept per device.
 */
object SamePeople {
    const val INTRO =
        "These names look alike and the two never played in the same match. If it's one person " +
            "(say, a friend you added who then joined from their own phone), merge them: their " +
            "matches and ratings become one, on every phone after the next sync."

    const val EMPTY_TITLE = "No look-alikes"
    const val EMPTY_MESSAGE =
        "No two players have names this alike. To merge any two players, open one and use Merge into…"

    /**
     * [people]: active players (merged-away hidden); [log]: the stored matches (resolved here through
     * [merges]); [different]: this device's "Different people" pair keys.
     */
    fun pairs(
        people: List<Player>,
        log: List<Match>,
        merges: PlayerMerges,
        different: Set<String>,
    ): List<SamePeoplePair> {
        val resolved = merges.resolve(log)
        return PlayerMatchUp.suggest(people, resolved, merges, different).map { pair ->
            SamePeoplePair(pair.key, side(pair.keep, resolved), side(pair.other, resolved))
        }
    }

    private fun side(player: Player, matches: List<Match>): PairSide {
        val mine = matches.filter { player.id in it.playerIds }
        return PairSide(player, mine.size, mine.maxOfOrNull { it.playedAt })
    }

    /** "12 matches · last Sat 3 Oct 2026", "1 match · Sat 3 Oct 2026", or "No matches yet". */
    fun summary(side: PairSide, zone: ZoneId = ZoneId.systemDefault()): String = when (side.games) {
        0 -> "No matches yet"
        1 -> "1 match · ${Dates.day(side.lastPlayed ?: 0, zone)}"
        else -> "${side.games} matches · last ${Dates.day(side.lastPlayed ?: 0, zone)}"
    }
}
