package dev.quinton.goa.core.domain.teams

import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.Seat
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.random.Random

/** How a 5/7/9 player count is evened out (docs/DOMAIN.md, Double piloting). */
@Serializable
enum class OddSeatMode {
    /** Uneven seats; the larger team's heroes play with their Handicap card [RB p.20]. */
    HANDICAP,

    /** Even seats; one player on the smaller team controls two heroes [APP — house rule]. */
    DOUBLE_PILOT,
}

object SeatLayouts {
    /**
     * Seats for a team split:
     * - equal teams: one seat per player, except 1 v 1 where each player controls two heroes
     *   [RB: 2 players = 2 heroes each];
     * - 2 v 1 (3 players): the official setup — the solo player double-pilots and one of their
     *   heroes carries a Handicap card [RB p.20]; [mode] is ignored;
     * - teams one apart (5/7/9): [mode]. [doublePilot] (on the smaller team) defaults to that
     *   team's first player.
     */
    fun build(
        red: List<String>,
        blue: List<String>,
        mode: OddSeatMode,
        doublePilot: String? = null,
    ): Result<List<Seat>> = runCatching {
        if (red.isEmpty() || blue.isEmpty()) throw DraftError.InvalidSeats("Both teams need a player")
        if (abs(red.size - blue.size) > 1) throw DraftError.InvalidSeats("Teams may differ by at most one player")
        val pilots = mapOf(Team.RED to red.toMutableList(), Team.BLUE to blue.toMutableList())
        val smaller = if (red.size < blue.size) Team.RED else Team.BLUE
        var handicapTeam: Team? = null
        var handicapSeat: Pair<Team, Int>? = null
        when {
            red.size == 1 && blue.size == 1 -> {
                pilots.getValue(Team.RED) += red.first()
                pilots.getValue(Team.BLUE) += blue.first()
            }

            red.size == blue.size -> Unit

            red.size + blue.size == 3 -> {
                val solo = pilots.getValue(smaller)
                solo += solo.first()
                handicapSeat = smaller to 1
            }

            mode == OddSeatMode.HANDICAP -> handicapTeam = smaller.opponent

            else -> {
                val team = pilots.getValue(smaller)
                val pilot = doublePilot?.takeIf { it in team } ?: team.first()
                // The double pilot's two seats sit together.
                team.add(team.indexOf(pilot) + 1, pilot)
            }
        }
        Team.entries.flatMap { team ->
            pilots.getValue(team).mapIndexed { i, id ->
                Seat(team, i, id, handicap = team == handicapTeam || handicapSeat == (team to i))
            }
        }
    }

    /** True when the table should choose between Handicap and Double pilot (5, 7 or 9 players). */
    fun offersOddChoice(players: Int): Boolean = players >= 5 && players % 2 == 1
}

data class TeamSplit(val red: List<String>, val blue: List<String>, val meanRed: Double, val meanBlue: Double) {
    val difference: Double get() = abs(meanRed - meanBlue)
}

/**
 * Balanced split by rating [APP]: enumerate every split into ⌈n/2⌉ / ⌊n/2⌋ (≤ 1,024 masks for
 * n ≤ 10), minimise |mean(Red) − mean(Blue)|, honour locked players, break ties with [seed].
 */
object TeamBalancer {
    private const val EPSILON = 1e-9
    private const val MAX_PLAYERS = 16

    fun balance(
        players: List<String>,
        ratings: Map<String, Double>,
        locked: Map<String, Team> = emptyMap(),
        seed: Long = 0L,
        defaultRating: Double = 1200.0,
    ): TeamSplit? {
        val n = players.size
        if (n < 2 || n > MAX_PLAYERS) return null
        val rating = players.map { ratings[it] ?: defaultRating }
        val sizes = setOf(n / 2, (n + 1) / 2)
        var best = Double.MAX_VALUE
        val optimal = mutableListOf<Int>()
        for (mask in 0 until (1 shl n)) {
            if (mask.countOneBits() !in sizes) continue
            if (!respectsLocks(mask, players, locked)) continue
            val diff = diff(mask, rating)
            when {
                diff < best - EPSILON -> {
                    best = diff
                    optimal.clear()
                    optimal += mask
                }

                abs(diff - best) <= EPSILON -> optimal += mask
            }
        }
        if (optimal.isEmpty()) return null
        val mask = optimal[Random(seed).nextInt(optimal.size)]
        val red = players.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
        val blue = players.filterIndexed { i, _ -> mask and (1 shl i) == 0 }
        fun mean(ids: List<String>) = ids.map { ratings[it] ?: defaultRating }.average()
        return TeamSplit(red, blue, mean(red), mean(blue))
    }

    private fun respectsLocks(mask: Int, players: List<String>, locked: Map<String, Team>): Boolean =
        players.withIndex().all { (i, id) ->
            when (locked[id]) {
                Team.RED -> mask and (1 shl i) != 0
                Team.BLUE -> mask and (1 shl i) == 0
                null -> true
            }
        }

    private fun diff(mask: Int, rating: List<Double>): Double {
        var sumRed = 0.0
        var sumBlue = 0.0
        var nRed = 0
        for (i in rating.indices) {
            if (mask and (1 shl i) != 0) {
                sumRed += rating[i]
                nRed++
            } else {
                sumBlue += rating[i]
            }
        }
        val nBlue = rating.size - nRed
        return abs(sumRed / nRed - sumBlue / nBlue)
    }
}

/**
 * Captains' half of the official Player Draft [RB p.22]: Captains draft players one at a time,
 * Team A's captain first, alternating.
 */
@Serializable
data class PlayerDraftState(
    val redCaptain: String,
    val blueCaptain: String,
    /** Team showing on the Tie Breaker coin; its captain picks first. */
    val teamA: Team,
    /** Players still to be drafted, in display order. */
    val remaining: List<String>,
    val picks: List<PlayerPick> = emptyList(),
) {
    val isComplete: Boolean get() = remaining.isEmpty()

    /** Team whose captain is on the clock; null once everyone is drafted. */
    val nextTeam: Team? get() = if (isComplete) null else if (picks.size % 2 == 0) teamA else teamA.opponent

    val red: List<String> get() = listOf(redCaptain) + picks.filter { it.team == Team.RED }.map { it.playerId }

    val blue: List<String> get() = listOf(blueCaptain) + picks.filter { it.team == Team.BLUE }.map { it.playerId }

    fun captainOf(team: Team): String = if (team == Team.RED) redCaptain else blueCaptain
}

@Serializable
data class PlayerPick(val team: Team, val playerId: String)

object PlayerDraft {
    /** The official draft needs two captains plus at least two other players. */
    const val MIN_PLAYERS = 4

    fun start(players: List<String>, redCaptain: String, blueCaptain: String, teamA: Team): Result<PlayerDraftState> =
        runCatching {
            if (players.size < MIN_PLAYERS) throw DraftError.NotSupported("Player Draft needs at least $MIN_PLAYERS players")
            if (redCaptain == blueCaptain) throw DraftError.InvalidSeats("Captains must be different players")
            if (redCaptain !in players || blueCaptain !in players) throw DraftError.InvalidSeats("Captains must be playing")
            PlayerDraftState(redCaptain, blueCaptain, teamA, players.filter { it != redCaptain && it != blueCaptain })
        }

    fun pick(state: PlayerDraftState, playerId: String): Result<PlayerDraftState> = runCatching {
        val team = state.nextTeam ?: throw DraftError.Complete
        if (playerId !in state.remaining) throw DraftError.NotInPool(playerId)
        state.copy(remaining = state.remaining - playerId, picks = state.picks + PlayerPick(team, playerId))
    }

    fun undo(state: PlayerDraftState, order: List<String>): Result<PlayerDraftState> {
        val last = state.picks.lastOrNull() ?: return Result.failure(DraftError.NothingToUndo)
        val remaining = (state.remaining + last.playerId).sortedBy { order.indexOf(it) }
        return Result.success(state.copy(remaining = remaining, picks = state.picks.dropLast(1)))
    }

    /**
     * Default captains: "the two most experienced players" [RB] — most games played, then
     * rating, then the given order.
     */
    fun suggestCaptains(players: List<String>, games: Map<String, Int>, ratings: Map<String, Double>): List<String> =
        players.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<String>> { games[it.value] ?: 0 }
                    .thenByDescending { ratings[it.value] ?: 0.0 }
                    .thenBy { it.index },
            )
            .take(2)
            .map { it.value }
}

/** Complexity filter [RB]: remove heroes above the least experienced player's complexity. */
object ComplexityFilter {
    /** Official complexity names (Hero Profile card, p.21), index = stars − 1. */
    val NAMES: List<String> = listOf("Challenging", "Complex", "Very Complex", "Wizard")

    fun name(stars: Int): String? = NAMES.getOrNull(stars - 1)

    /** The cap for a table: the lowest experience level among the players; null = no cap. */
    fun capFor(playerLevels: Collection<Int>): Int? = playerLevels.minOrNull()

    /** Heroes with unknown complexity are kept (never guessed); null [max] keeps everyone. */
    fun filter(heroes: List<Hero>, max: Int?): List<Hero> =
        if (max == null) heroes else heroes.filter { hero -> hero.complexity.let { it == null || it <= max } }
}
