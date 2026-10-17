package dev.quinton.goa.core.domain.rating

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Test

/** Seeded random match logs; every property runs over many generated logs. */
class EloPropertiesTest {
    private val players = (1..10).map { "p$it" }
    private val heroes = (1..32).map { "h$it" }

    /** A random but valid match: 1–5 seats per side, optional double pilots, unknown heroes. */
    private fun Random.match(
        index: Int,
        evenSeats: Boolean = false,
        doublePilots: Boolean = true,
        unknownHeroes: Boolean = true,
    ): Match {
        val redSeats = nextInt(1, 6)
        val blueSeats = if (evenSeats) redSeats else nextInt(1, 6)
        val shuffledPlayers = players.shuffled(this)
        val shuffledHeroes = heroes.shuffled(this)
        val redPlayers = shuffledPlayers.take(5)
        val bluePlayers = shuffledPlayers.drop(5)
        fun seats(team: Team, count: Int, pool: List<String>, heroOffset: Int): List<Participant> {
            val pilots = if (doublePilots && count > 1 && nextBoolean()) {
                pool.take(count - 1).let { it + it.first() }
            } else {
                pool.take(count)
            }
            return pilots.mapIndexed { i, p ->
                val hero = if (unknownHeroes && nextInt(8) == 0) null else shuffledHeroes[heroOffset + i]
                Participant(p, hero, team, handicap = false)
            }
        }
        return Match(
            id = "m$index-${nextInt(1_000_000)}",
            createdAt = nextLong(0, 1_000),
            playedAt = nextLong(0, 50),
            status = if (nextInt(10) == 0) MatchStatus.ABANDONED else MatchStatus.COMPLETED,
            mode = GameMode.entries[nextInt(GameMode.entries.size)],
            winner = if (nextBoolean()) Team.RED else Team.BLUE,
            participants = seats(Team.RED, redSeats, redPlayers, 0) + seats(Team.BLUE, blueSeats, bluePlayers, 10),
        )
    }

    private fun Random.log(size: Int, block: Random.(Int) -> Match = { match(it) }) = (0 until size).map { block(it) }

    private fun Random.config() = EloConfig(
        heroInfluence = nextDouble(),
        pilotInfluence = nextDouble(),
        seatAdvantage = nextDouble(-50.0, 50.0),
        doublePilotWeight = nextDouble(0.0, 2.0),
        longMultiplier = nextDouble(0.5, 2.0),
    )

    @Test
    fun `result does not depend on input order`() {
        repeat(50) { seed ->
            val random = Random(seed)
            val log = random.log(60)
            val config = random.config()
            assertThat(EloCalculator.replay(log.shuffled(random), config)).isEqualTo(EloCalculator.replay(log, config))
        }
    }

    @Test
    fun `replay is deterministic`() {
        val log = Random(7).log(200)
        assertThat(EloCalculator.replay(log)).isEqualTo(EloCalculator.replay(log.toList()))
    }

    @Test
    fun `even seats without double pilots and a single K is zero-sum in both spaces`() {
        repeat(50) { seed ->
            val random = Random(seed)
            val log = random.log(40) { match(it, evenSeats = true, doublePilots = false, unknownHeroes = false) }
            val config = random.config().copy(kProvisional = 32.0, kEstablished = 32.0)
            val result = EloCalculator.replay(log, config)
            for (change in result.matchChanges.values) {
                assertThat(abs(change.playerDeltas.values.sum())).isLessThan(1e-9)
                assertThat(abs(change.heroDeltas.values.sum())).isLessThan(1e-9)
            }
            assertThat(result.players.values.sumOf { it.value - 1200.0 }).isWithin(1e-6).of(0.0)
            assertThat(result.heroes.values.sumOf { it.value - 1200.0 }).isWithin(1e-6).of(0.0)
        }
    }

    @Test
    fun `with influences, seat advantage and weights at 0 it reduces to classic team Elo`() {
        val config = EloConfig(heroInfluence = 0.0, pilotInfluence = 0.0, seatAdvantage = 0.0, doublePilotWeight = 0.0)
        repeat(30) { seed ->
            val random = Random(seed)
            val log = random.log(80) { match(it, doublePilots = false, unknownHeroes = false) }
            val result = EloCalculator.replay(log, config)
            val (players, heroes) = classicTeamElo(log, config)
            for ((id, value) in players) assertThat(result.player(id).value).isWithin(1e-9).of(value)
            for ((id, value) in heroes) assertThat(result.hero(id).value).isWithin(1e-9).of(value)
        }
    }

    /** Independent reference: team rating = mean member rating, everyone gets K(S − E). */
    private fun classicTeamElo(log: List<Match>, config: EloConfig): Pair<Map<String, Double>, Map<String, Double>> {
        val players = HashMap<String, Double>()
        val heroes = HashMap<String, Double>()
        val playerGames = HashMap<String, Int>()
        val heroGames = HashMap<String, Int>()
        val rated = log.filter { it.status == MatchStatus.COMPLETED && it.winner != null }
        for (m in rated.sortedWith(compareBy({ it.playedAt }, { it.createdAt }, { it.id }))) {
            val winner = requireNotNull(m.winner)
            fun space(ratings: HashMap<String, Double>, games: HashMap<String, Int>, ids: (Team) -> List<String>) {
                val red = ids(Team.RED).map { ratings[it] ?: 1200.0 }.average()
                val blue = ids(Team.BLUE).map { ratings[it] ?: 1200.0 }.average()
                val eRed = 1 / (1 + Math.pow(10.0, (blue - red) / 400))
                val updates = Team.entries.flatMap { t ->
                    val s = if (t == winner) 1.0 else 0.0
                    val e = if (t == Team.RED) eRed else 1 - eRed
                    ids(t).map { id ->
                        val k = config.k(games[id] ?: 0)
                        id to k * config.multiplier(m.mode) * (s - e)
                    }
                }
                for ((id, d) in updates) {
                    ratings[id] = (ratings[id] ?: 1200.0) + d
                    games[id] = (games[id] ?: 0) + 1
                }
            }
            space(players, playerGames) { t -> m.team(t).map { it.playerId } }
            space(heroes, heroGames) { t -> m.team(t).map { requireNotNull(it.heroId) } }
        }
        return players to heroes
    }

    @Test
    fun `winners never lose rating and losers never gain`() {
        repeat(30) { seed ->
            val random = Random(seed)
            val log = random.log(100)
            val result = EloCalculator.replay(log, random.config())
            val byId = log.associateBy { it.id }
            for (change in result.matchChanges.values) {
                val m = byId.getValue(change.matchId)
                for ((playerId, delta) in change.playerDeltas) {
                    val team = m.participants.first { it.playerId == playerId }.team
                    if (team == change.winner) assertThat(delta).isAtLeast(0.0) else assertThat(delta).isAtMost(0.0)
                }
                for ((heroId, delta) in change.heroDeltas) {
                    val team = m.participants.first { it.heroId == heroId }.team
                    if (team == change.winner) assertThat(delta).isAtLeast(0.0) else assertThat(delta).isAtMost(0.0)
                }
            }
        }
    }

    @Test
    fun `game and history counts match the rated matches`() {
        val log = Random(3).log(300)
        val result = EloCalculator.replay(log)
        val rated = log.filter { EloCalculator.skipReason(it) == null }
        assertThat(result.replayOrder).hasSize(rated.size)
        assertThat(result.skipped.size + result.replayOrder.size).isEqualTo(log.size)
        for ((playerId, rating) in result.players) {
            assertThat(rating.games).isEqualTo(rated.count { m -> m.participants.any { it.playerId == playerId } })
            assertThat(result.playerHistory.getValue(playerId)).hasSize(rating.games)
        }
    }

    @Test
    fun `replays 1000 matches of 6 seats in well under a second`() {
        val random = Random(1)
        val log = (0 until 1_000).map { i ->
            val pilots = players.shuffled(random)
            val picks = heroes.shuffled(random)
            Match(
                id = "m$i",
                createdAt = i.toLong(),
                playedAt = i.toLong(),
                status = MatchStatus.COMPLETED,
                mode = GameMode.QUICK,
                winner = if (random.nextBoolean()) Team.RED else Team.BLUE,
                participants = (0 until 6).map { s ->
                    Participant(pilots[s], picks[s], if (s < 3) Team.RED else Team.BLUE)
                },
            )
        }
        repeat(5) { EloCalculator.replay(log) } // JIT warm-up
        val runs = (1..5).map {
            val start = System.nanoTime()
            val result = EloCalculator.replay(log)
            check(result.replayOrder.size == 1_000)
            (System.nanoTime() - start) / 1_000_000
        }
        // Median of 5 runs, so one GC pause on a busy build machine doesn't fail the build.
        assertThat(runs.sorted()[2]).isLessThan(300L)
    }
}
