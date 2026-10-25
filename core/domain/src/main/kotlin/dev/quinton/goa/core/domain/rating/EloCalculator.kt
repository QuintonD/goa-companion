package dev.quinton.goa.core.domain.rating

import dev.quinton.goa.core.domain.PortableMath
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.rating.RatingPoint

/**
 * Seat-based Elo with double piloting (docs/DOMAIN.md *Elo*, ADR-011). A pure, deterministic
 * replay of the match log: the same matches and config always give the same result, whatever
 * order the input list is in.
 *
 * Per rated match, with `B` = [EloConfig.initialRating]:
 * - Player space: seat strength `s = R_pilot + heroInfluence · (R_hero − B)`.
 * - Hero space: seat strength `s = R_hero + pilotInfluence · (R_pilot − B)`.
 * - Team strength `T = mean(s) + seatAdvantage · (seats − opponentSeats)`;
 *   `E = 1 / (1 + 10^((T_opp − T) / 400))`; `S` = 1 win / 0 loss. Both teams get the seat term
 *   (with opposite signs), so the strength gap widens by `2 · seatAdvantage` per seat of difference.
 * - Each distinct player: `Δ = K · m · w · (S − E)`, `w = 1 + doublePilotWeight · (seats − 1)`.
 * - Each hero seat: `Δ = K · m · (S − E)`.
 * - `K` = [EloConfig.kProvisional] below [EloConfig.provisionalGames] rated games, else
 *   [EloConfig.kEstablished]; `m` = mode multiplier.
 *
 * Skip rules: matches that are not COMPLETED, have no winner, have an empty team or put one
 * player on both teams are ignored. A seat with an unknown hero still counts in the player space
 * (at hero rating `B`) but is left out of the hero space; the hero space is skipped for a match
 * when a team has no known hero or a hero appears twice ([heroSpaceApplies]; player × hero
 * records follow the hero space, as do the meta stats). Seat counts used for `seatAdvantage` are
 * always the real seat counts.
 */
object EloCalculator {
    /** Replay order: `(playedAt, createdAt, id)`. */
    val REPLAY_ORDER: Comparator<Match> = compareBy<Match>({ it.playedAt }, { it.createdAt }, { it.id })

    /** Classic Elo expected score of a side with strength [own] against [opponent]. */
    fun expectedScore(own: Double, opponent: Double): Double =
        // The same bits on every phone and in the browser (PortableMath), so replays agree everywhere.
        1.0 / (1.0 + PortableMath.pow10((opponent - own) / EloConfig.SCALE))

    /** Why [match] would be skipped, or null if it is rated. */
    fun skipReason(match: Match): SkipReason? {
        val red = match.team(Team.RED)
        val blue = match.team(Team.BLUE)
        return when {
            match.status != MatchStatus.COMPLETED -> SkipReason.NOT_COMPLETED
            match.winner == null -> SkipReason.NO_WINNER
            red.isEmpty() || blue.isEmpty() -> SkipReason.EMPTY_TEAM
            red.any { seat -> blue.any { it.playerId == seat.playerId } } -> SkipReason.PLAYER_ON_BOTH_TEAMS
            else -> null
        }
    }

    /**
     * Whether the hero part of a rated [match] counts: both teams have a known hero and no hero
     * appears twice. Shared with the meta stats so both skip the same matches.
     */
    fun heroSpaceApplies(match: Match): Boolean {
        val red = match.team(Team.RED)
        val blue = match.team(Team.BLUE)
        val known = match.participants.mapNotNull { it.heroId }
        return red.any { it.heroId != null } && blue.any { it.heroId != null } && known.size == known.toSet().size
    }

    fun replay(matches: Collection<Match>, config: EloConfig = EloConfig.DEFAULT): RatingsResult =
        Replay(config).run(matches.sortedWith(REPLAY_ORDER))

    private class Entry(var value: Double) {
        var games = 0
        var wins = 0
        val history = ArrayList<RatingPoint>()
    }

    private class Replay(private val config: EloConfig) {
        private val players = LinkedHashMap<String, Entry>()
        private val heroes = LinkedHashMap<String, Entry>()
        private val playerHero = LinkedHashMap<PlayerHeroKey, WinLoss>()
        private val doublePilot = LinkedHashMap<String, Int>()
        private var largerTeam = WinLoss.NONE
        private val changes = LinkedHashMap<String, MatchRatingChange>()
        private val skipped = LinkedHashMap<String, SkipReason>()
        private val order = ArrayList<String>()
        private val base = config.initialRating

        fun run(sorted: List<Match>): RatingsResult {
            for (match in sorted) {
                val reason = skipReason(match)
                if (reason != null) skipped[match.id] = reason else rate(match, requireNotNull(match.winner))
            }
            return RatingsResult(
                players = players.mapValues { (_, e) -> e.toRating() },
                heroes = heroes.mapValues { (_, e) -> e.toRating() },
                playerHistory = players.mapValues { (_, e) -> e.history.toList() },
                heroHistory = heroes.mapValues { (_, e) -> e.history.toList() },
                playerHeroRecords = playerHero.toMap(),
                doublePilotGames = doublePilot.toMap(),
                largerTeam = largerTeam,
                matchChanges = changes.toMap(),
                skipped = skipped.toMap(),
                replayOrder = order.toList(),
                initialRating = base,
            )
        }

        private fun Entry.toRating() = Rating(
            value = value,
            games = games,
            wins = wins,
            provisional = config.isProvisional(games),
        )

        private fun player(id: String) = players.getOrPut(id) { Entry(base) }

        private fun hero(id: String) = heroes.getOrPut(id) { Entry(base) }

        private fun heroRating(seat: Participant): Double = seat.heroId?.let { heroes[it]?.value } ?: base

        private fun playerRating(seat: Participant): Double = players[seat.playerId]?.value ?: base

        private fun rate(match: Match, winner: Team) {
            val red = match.team(Team.RED)
            val blue = match.team(Team.BLUE)
            val m = config.multiplier(match.mode)

            // Strengths are computed from pre-match ratings for both spaces before any update.
            val expectedRed = expected(red, blue) { playerRating(it) + config.heroInfluence * (heroRating(it) - base) }
            val heroSpace = heroSpaceApplies(match)
            val expectedRedHero = if (heroSpace) {
                expected(red.filter { it.heroId != null }, blue.filter { it.heroId != null }, red.size, blue.size) {
                    heroRating(it) + config.pilotInfluence * (playerRating(it) - base)
                }
            } else {
                null
            }

            val playerDeltas = playerDeltas(red, blue, winner, expectedRed, m)
            val heroDeltas = expectedRedHero?.let { heroDeltas(red + blue, winner, it, m) }.orEmpty()

            val redWon = winner == Team.RED
            playerDeltas.forEach { (id, delta) ->
                player(id).apply(match, delta, red.any { it.playerId == id } == redWon)
            }
            heroDeltas.forEach { (id, delta) -> hero(id).apply(match, delta, red.any { it.heroId == id } == redWon) }
            trackRecords(red, blue, winner, heroSpace)
            changes[match.id] = MatchRatingChange(
                matchId = match.id,
                playedAt = match.playedAt,
                winner = winner,
                expectedRed = expectedRed,
                expectedRedHero = expectedRedHero,
                playerDeltas = playerDeltas,
                heroDeltas = heroDeltas,
            )
            order += match.id
        }

        /** One delta per distinct player, weighted for double pilots; counts double-pilot games. */
        private fun playerDeltas(
            red: List<Participant>,
            blue: List<Participant>,
            winner: Team,
            expectedRed: Double,
            m: Double,
        ): Map<String, Double> {
            val deltas = LinkedHashMap<String, Double>()
            for (team in listOf(red, blue)) {
                val side = team.first().team
                val score = if (side == winner) 1.0 else 0.0
                val expected = if (side == Team.RED) expectedRed else 1.0 - expectedRed
                for ((playerId, seats) in team.groupingBy { it.playerId }.eachCount()) {
                    val weight = 1.0 + config.doublePilotWeight * (seats - 1)
                    deltas[playerId] = config.k(player(playerId).games) * m * weight * (score - expected)
                    if (seats > 1) doublePilot[playerId] = (doublePilot[playerId] ?: 0) + 1
                }
            }
            return deltas
        }

        /** One delta per known-hero seat. */
        private fun heroDeltas(
            seats: List<Participant>,
            winner: Team,
            expectedRed: Double,
            m: Double,
        ): Map<String, Double> {
            val deltas = LinkedHashMap<String, Double>()
            for (seat in seats) {
                val heroId = seat.heroId ?: continue
                val score = if (seat.team == winner) 1.0 else 0.0
                val expected = if (seat.team == Team.RED) expectedRed else 1.0 - expectedRed
                deltas[heroId] = config.k(hero(heroId).games) * m * (score - expected)
            }
            return deltas
        }

        private fun trackRecords(red: List<Participant>, blue: List<Participant>, winner: Team, heroSpace: Boolean) {
            if (heroSpace) {
                for (seat in red + blue) {
                    val heroId = seat.heroId ?: continue
                    val key = PlayerHeroKey(seat.playerId, heroId)
                    playerHero[key] = (playerHero[key] ?: WinLoss.NONE) + (seat.team == winner)
                }
            }
            if (red.size != blue.size) {
                val larger = if (red.size > blue.size) Team.RED else Team.BLUE
                largerTeam += (larger == winner)
            }
        }

        private fun Entry.apply(match: Match, delta: Double, won: Boolean) {
            value += delta
            games += 1
            if (won) wins += 1
            history += RatingPoint(matchId = match.id, playedAt = match.playedAt, value = value, delta = delta)
        }

        /** Expected score of [red]; seat counts for the advantage term default to the real seat counts. */
        private inline fun expected(
            red: List<Participant>,
            blue: List<Participant>,
            redSeats: Int = red.size,
            blueSeats: Int = blue.size,
            strength: (Participant) -> Double,
        ): Double {
            val redT = red.sumOf { strength(it) } / red.size + config.seatAdvantage * (redSeats - blueSeats)
            val blueT = blue.sumOf { strength(it) } / blue.size + config.seatAdvantage * (blueSeats - redSeats)
            return expectedScore(redT, blueT)
        }
    }
}
