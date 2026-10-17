package dev.quinton.goa.core.domain.rating

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.rating.RatingPoint
import org.junit.Test

private const val EPS = 1e-9

/** Hand-computed examples (values worked out on paper, see each comment) and the skip rules. */
class EloCalculatorTest {
    @Test
    fun `1v1 between newcomers moves both spaces by K over 2`() {
        val result = EloCalculator.replay(listOf(match("m1", 1, Team.RED, red("p1", "h1"), blue("p2", "h2"))))

        // E = 0.5, K = 40 (provisional): ±20.
        assertThat(result.player("p1").value).isWithin(EPS).of(1220.0)
        assertThat(result.player("p2").value).isWithin(EPS).of(1180.0)
        assertThat(result.hero("h1").value).isWithin(EPS).of(1220.0)
        assertThat(result.hero("h2").value).isWithin(EPS).of(1180.0)
        assertThat(result.player("p1").wins).isEqualTo(1)
        assertThat(result.player("p2").losses).isEqualTo(1)
        assertThat(result.player("p1").provisional).isTrue()
        assertThat(result.matchChanges.getValue("m1").expectedRed).isWithin(EPS).of(0.5)
    }

    @Test
    fun `2v2 blends pilot and hero ratings in both spaces`() {
        val result = EloCalculator.replay(
            listOf(
                match("m1", 1, Team.RED, red("p1", "h1"), blue("p2", "h2")),
                // After m1: p1 = h1 = 1220, p2 = h2 = 1180.
                match("m2", 2, Team.RED, red("p1", "h3"), red("p3", "h4"), blue("p2", "h1"), blue("p4", "h2")),
            ),
        )
        // Player space: Red (1220 + .5·0, 1200 + .5·0) = 1210; Blue (1180 + .5·20, 1200 + .5·−20) = 1190.
        // E_red = 1 / (1 + 10^(−20/400)) = 0.5287505638922686 → Δ = 40 · (1 − E) = 18.849977444309257.
        val change = result.matchChanges.getValue("m2")
        assertThat(change.expectedRed).isWithin(EPS).of(0.5287505638922686)
        assertThat(change.playerDeltas.getValue("p1")).isWithin(EPS).of(18.849977444309257)
        assertThat(change.playerDeltas.getValue("p4")).isWithin(EPS).of(-18.849977444309257)
        assertThat(result.player("p1").value).isWithin(EPS).of(1220 + 18.849977444309257)
        assertThat(result.player("p2").value).isWithin(EPS).of(1180 - 18.849977444309257)

        // Hero space: Red (h3 1200 + .5·20, h4 1200 + .5·0) = 1205; Blue (h1 1220 + .5·−20, h2 1180 + .5·0) = 1195.
        // E_red = 0.5143871841659987 → Δ = 19.424512633360052.
        assertThat(change.expectedRedHero).isWithin(EPS).of(0.5143871841659987)
        assertThat(result.hero("h3").value).isWithin(EPS).of(1200 + 19.424512633360052)
        assertThat(result.hero("h1").value).isWithin(EPS).of(1220 - 19.424512633360052)
    }

    @Test
    fun `2v3 with Handicap cards applies seatAdvantage to the larger team`() {
        val config = EloConfig(seatAdvantage = 25.0)
        val result = EloCalculator.replay(
            listOf(
                match(
                    "m1", 1, Team.BLUE,
                    red("p1", "h1", handicap = true), red("p2", "h2", handicap = true), red("p3", "h3", handicap = true),
                    blue("p4", "h4"), blue("p5", "h5"),
                ),
            ),
            config,
        )
        // T_red = 1200 + 25·(3 − 2) = 1225, T_blue = 1175 → E_red = 0.5714631174083814.
        // Blue wins: Δ_blue = 40 · (1 − (1 − E_red)) = 22.858524696335255.
        val change = result.matchChanges.getValue("m1")
        assertThat(change.expectedRed).isWithin(EPS).of(0.5714631174083814)
        assertThat(result.player("p4").value).isWithin(EPS).of(1200 + 22.858524696335255)
        assertThat(result.player("p1").value).isWithin(EPS).of(1200 - 22.858524696335255)
        assertThat(result.hero("h5").value).isWithin(EPS).of(1200 + 22.858524696335255)
        assertThat(result.largerTeam).isEqualTo(WinLoss(games = 1, wins = 0))
    }

    @Test
    fun `seatAdvantage 0 treats uneven teams as even`() {
        val result = EloCalculator.replay(
            listOf(match("m1", 1, Team.RED, red("p1", "h1"), red("p2", "h2"), blue("p3", "h3"))),
        )
        assertThat(result.matchChanges.getValue("m1").expectedRed).isWithin(EPS).of(0.5)
        assertThat(result.largerTeam).isEqualTo(WinLoss(games = 1, wins = 1))
    }

    @Test
    fun `3v3 with a double pilot weights the double pilot once, by doublePilotWeight`() {
        val result = EloCalculator.replay(
            listOf(
                match(
                    "m1", 1, Team.RED,
                    red("p1", "h1"), red("p1", "h2"), red("p2", "h3"),
                    blue("p3", "h4"), blue("p4", "h5"), blue("p5", "h6"),
                ),
            ),
        )
        // E = 0.5. p1 pilots 2 seats: w = 1 + 0.5·1 = 1.5 → Δ = 40·1.5·0.5 = 30. Others ±20.
        assertThat(result.player("p1").value).isWithin(EPS).of(1230.0)
        assertThat(result.player("p1").games).isEqualTo(1)
        assertThat(result.player("p2").value).isWithin(EPS).of(1220.0)
        assertThat(result.player("p3").value).isWithin(EPS).of(1180.0)
        // Every hero seat is updated individually.
        assertThat(result.hero("h1").value).isWithin(EPS).of(1220.0)
        assertThat(result.hero("h2").value).isWithin(EPS).of(1220.0)
        assertThat(result.doublePilotGames).containsExactly("p1", 1)
        assertThat(result.playerHistory.getValue("p1")).hasSize(1)
    }

    @Test
    fun `double pilot strength uses each hero it controls`() {
        val config = EloConfig(heroInfluence = 1.0)
        val result = EloCalculator.replay(
            listOf(
                match("m1", 1, Team.RED, red("a", "strong"), blue("b", "weak")),
                // strong = 1220, weak = 1180, a = 1220, b = 1180.
                match("m2", 2, Team.BLUE, red("c", "strong"), red("c", "weak"), blue("d", "x"), blue("e", "y")),
            ),
            config,
        )
        // Red seats: c(1200) + 1.0·(+20) = 1220 and c + 1.0·(−20) = 1180 → mean 1200; Blue 1200 → E = 0.5.
        assertThat(result.matchChanges.getValue("m2").expectedRed).isWithin(EPS).of(0.5)
        // c loses with weight 1.5: −30.
        assertThat(result.player("c").value).isWithin(EPS).of(1170.0)
    }

    @Test
    fun `unknown hero seats count for the pilot but are left out of the hero space`() {
        val result = EloCalculator.replay(
            listOf(match("m1", 1, Team.RED, red("p1", null), red("p2", "h1"), blue("p3", "h2"), blue("p4", "h3"))),
        )
        assertThat(result.player("p1").value).isWithin(EPS).of(1220.0)
        assertThat(result.player("p2").value).isWithin(EPS).of(1220.0)
        assertThat(result.heroes.keys).containsExactly("h1", "h2", "h3")
        assertThat(result.hero("h1").value).isWithin(EPS).of(1220.0)
        assertThat(result.hero("h2").value).isWithin(EPS).of(1180.0)
        assertThat(result.playerHeroRecords.keys.map { it.playerId }).doesNotContain("p1")
    }

    @Test
    fun `unknown hero counts at the starting rating in the player space`() {
        val config = EloConfig(heroInfluence = 1.0)
        val result = EloCalculator.replay(
            listOf(
                match("m1", 1, Team.RED, red("a", "h1"), blue("b", "h2")),
                // h1 = 1220, h2 = 1180; a = 1220, b = 1180.
                match("m2", 2, Team.RED, red("a", null), blue("b", "h1")),
            ),
            config,
        )
        // Red: 1220 + 1.0·(1200 − 1200) = 1220. Blue: 1180 + 1.0·(1220 − 1200) = 1200.
        val expected = EloCalculator.expectedScore(1220.0, 1200.0)
        assertThat(result.matchChanges.getValue("m2").expectedRed).isWithin(EPS).of(expected)
        // Hero space skipped: Red has no known hero.
        assertThat(result.matchChanges.getValue("m2").expectedRedHero).isNull()
        assertThat(result.hero("h1").games).isEqualTo(1)
    }

    @Test
    fun `a hero listed twice in one match skips the hero space only`() {
        val result = EloCalculator.replay(listOf(match("m1", 1, Team.RED, red("p1", "h1"), blue("p2", "h1"))))
        assertThat(result.heroes).isEmpty()
        assertThat(result.player("p1").value).isWithin(EPS).of(1220.0)
    }

    @Test
    fun `skip rules ignore pending, abandoned, winnerless, one-sided and cross-team matches`() {
        val matches = listOf(
            match("pending", 1, Team.RED, red("p1", "h1"), blue("p2", "h2"), status = MatchStatus.PENDING),
            match("abandoned", 2, Team.RED, red("p1", "h1"), blue("p2", "h2"), status = MatchStatus.ABANDONED),
            match("nowinner", 3, null, red("p1", "h1"), blue("p2", "h2")),
            match("onesided", 4, Team.RED, red("p1", "h1"), red("p2", "h2")),
            match("cross", 5, Team.RED, red("p1", "h1"), blue("p1", "h2")),
        )
        val result = EloCalculator.replay(matches)
        assertThat(result.players).isEmpty()
        assertThat(result.heroes).isEmpty()
        assertThat(result.replayOrder).isEmpty()
        assertThat(result.skipped).containsExactly(
            "pending", SkipReason.NOT_COMPLETED,
            "abandoned", SkipReason.NOT_COMPLETED,
            "nowinner", SkipReason.NO_WINNER,
            "onesided", SkipReason.EMPTY_TEAM,
            "cross", SkipReason.PLAYER_ON_BOTH_TEAMS,
        )
    }

    @Test
    fun `provisional K applies below 10 games and established K from the 11th`() {
        val matches = (1..11).map { i -> match("m$i", i.toLong(), Team.RED, red("p", "h"), blue("q$i", "g$i")) }
        // heroInfluence 0 so the expected score depends on the pilots only.
        val config = EloConfig(heroInfluence = 0.0)
        val result = EloCalculator.replay(matches, config)
        val history = result.playerHistory.getValue("p")

        // Games 1–10 are played with < 10 prior games → K = 40; the 11th has 10 prior games → K = 20.
        val k = (0 until 11).map { i -> history[i].delta / (1 - EloCalculator.expectedScore(rBefore(history, i), 1200.0)) }
        k.take(10).forEach { assertThat(it).isWithin(1e-6).of(40.0) }
        assertThat(k[10]).isWithin(1e-6).of(20.0)

        val nine = EloCalculator.replay(matches.take(9), config)
        assertThat(nine.player("p").provisional).isTrue()
        val ten = EloCalculator.replay(matches.take(10), config)
        assertThat(ten.player("p").provisional).isFalse()
        assertThat(ten.player("p").games).isEqualTo(10)
    }

    private fun rBefore(history: List<RatingPoint>, i: Int) =
        if (i == 0) 1200.0 else history[i - 1].value

    @Test
    fun `mode multiplier scales every delta`() {
        val config = EloConfig(longMultiplier = 1.5, doubleLaneMultiplier = 0.5)
        val long = EloCalculator.replay(
            listOf(match("m1", 1, Team.RED, red("p1", "h1"), blue("p2", "h2"), mode = GameMode.LONG)),
            config,
        )
        assertThat(long.player("p1").value).isWithin(EPS).of(1230.0)
        assertThat(long.hero("h2").value).isWithin(EPS).of(1170.0)
        val lane = EloCalculator.replay(
            listOf(match("m1", 1, Team.RED, red("p1", "h1"), blue("p2", "h2"), mode = GameMode.DOUBLE_LANE)),
            config,
        )
        assertThat(lane.player("p1").value).isWithin(EPS).of(1210.0)
    }

    @Test
    fun `replay order is playedAt then createdAt then id`() {
        val a = match("b", playedAt = 5, winner = Team.RED, red("p1", "h1"), blue("p2", "h2"), createdAt = 1)
        val b = match("a", playedAt = 5, winner = Team.BLUE, red("p1", "h1"), blue("p2", "h2"), createdAt = 1)
        val c = match("c", playedAt = 5, winner = Team.BLUE, red("p1", "h1"), blue("p2", "h2"), createdAt = 0)
        val d = match("d", playedAt = 1, winner = Team.RED, red("p1", "h1"), blue("p2", "h2"), createdAt = 9)
        val result = EloCalculator.replay(listOf(a, b, c, d))
        assertThat(result.replayOrder).containsExactly("d", "c", "a", "b").inOrder()
        assertThat(result.playerHistory.getValue("p1").map { it.matchId }).containsExactly("d", "c", "a", "b").inOrder()
    }

    @Test
    fun `player x hero records and wins are tracked`() {
        val result = EloCalculator.replay(
            listOf(
                match("m1", 1, Team.RED, red("p1", "h1"), blue("p2", "h2")),
                match("m2", 2, Team.BLUE, red("p1", "h1"), blue("p2", "h2")),
                match("m3", 3, Team.RED, red("p1", "h1"), blue("p2", "h3")),
            ),
        )
        assertThat(result.record("p1", "h1")).isEqualTo(WinLoss(games = 3, wins = 2))
        assertThat(result.record("p2", "h2")).isEqualTo(WinLoss(games = 2, wins = 1))
        assertThat(result.record("p2", "h9")).isEqualTo(WinLoss.NONE)
        assertThat(result.hero("h1").games).isEqualTo(3)
        assertThat(result.player("p2").wins).isEqualTo(1)
    }

    @Test
    fun `empty log yields empty result and unrated defaults`() {
        val result = EloCalculator.replay(emptyList(), EloConfig(initialRating = 1500.0))
        assertThat(result.players).isEmpty()
        assertThat(result.player("anyone").value).isEqualTo(1500.0)
        assertThat(result.player("anyone").provisional).isTrue()
    }

    @Test
    fun `sanitized clamps out-of-range and NaN values`() {
        val config = EloConfig(heroInfluence = 7.0, kProvisional = Double.NaN, provisionalGames = -3).sanitized()
        assertThat(config.heroInfluence).isEqualTo(1.0)
        assertThat(config.kProvisional).isEqualTo(40.0)
        assertThat(config.provisionalGames).isEqualTo(0)
        assertThat(EloConfig.DEFAULT.sanitized()).isEqualTo(EloConfig.DEFAULT)
    }
}
