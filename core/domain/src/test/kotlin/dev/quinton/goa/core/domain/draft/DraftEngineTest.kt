package dev.quinton.goa.core.domain.draft

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class DraftEngineTest {
    private val house = DraftFixtures.method("house")

    private fun houseSession(teamA: Team = Team.BLUE, seed: Long = 3L) =
        DraftEngine.start(DraftFixtures.setup(house, teamA = teamA, seed = seed)).getOrThrow()

    private fun DraftSession.choose(heroId: String) = DraftEngine.apply(this, DraftCommand(heroId)).getOrThrow()

    @Test
    fun `house draft deals 15 heroes deterministically from the seed`() {
        val a = houseSession(seed = 42)
        val b = houseSession(seed = 42)
        val c = houseSession(seed = 43)
        assertThat(a.pool).hasSize(15)
        assertThat(a.pool).containsNoDuplicates()
        assertThat(a.pool).isEqualTo(b.pool)
        assertThat(a.pool).isNotEqualTo(c.pool)
    }

    @Test
    fun `team A comes from the coin and acts first`() {
        val session = houseSession(teamA = Team.BLUE)
        assertThat(session.currentStep).isEqualTo(PlannedStep(Team.BLUE, DraftAction.BAN))
        assertThat(session.teamB).isEqualTo(Team.RED)
    }

    @Test
    fun `small rosters shrink the random pool but still fit the plan`() {
        val session = DraftEngine.start(
            DraftFixtures.setup(house, candidates = DraftFixtures.heroes.take(9)),
        ).getOrThrow()
        assertThat(session.pool).hasSize(9)
    }

    @Test
    fun `typed errors for taken, banned, not in pool, wrong team and wrong action`() {
        var s = houseSession(teamA = Team.RED)
        val (h1, h2, h3) = s.pool
        s = s.choose(h1) // Red bans h1
        assertThat(DraftEngine.apply(s, DraftCommand(h1)).exceptionOrNull()).isEqualTo(DraftError.AlreadyBanned(h1))
        s = s.choose(h2) // Red picks h2
        assertThat(
            DraftEngine.apply(s, DraftCommand(h2)).exceptionOrNull(),
        ).isEqualTo(DraftError.AlreadyPicked(h2, Team.RED))
        assertThat(
            DraftEngine.apply(s, DraftCommand("hero-99")).exceptionOrNull(),
        ).isInstanceOf(DraftError.NotInPool::class.java)
        assertThat(DraftEngine.apply(s, DraftCommand(h3, team = Team.RED)).exceptionOrNull())
            .isEqualTo(DraftError.WrongTeam(Team.BLUE, Team.RED))
        assertThat(DraftEngine.apply(s, DraftCommand(h3, action = DraftAction.PICK)).exceptionOrNull())
            .isEqualTo(DraftError.WrongAction(DraftAction.BAN, DraftAction.PICK))
    }

    @Test
    fun `current run describes Blue picking 2`() {
        var s = houseSession(teamA = Team.RED)
        s = s.choose(s.pool[0]).choose(s.pool[1]).choose(s.pool[2])
        assertThat(s.currentRun()).isEqualTo(StepRun(Team.BLUE, DraftAction.PICK, position = 1, length = 2))
        s = s.choose(s.pool[3])
        assertThat(s.currentRun()).isEqualTo(StepRun(Team.BLUE, DraftAction.PICK, position = 2, length = 2))
    }

    @Test
    fun `undo drops only the last result and nothing to undo at the start`() {
        val start = houseSession()
        assertThat(DraftEngine.undo(start).exceptionOrNull()).isEqualTo(DraftError.NothingToUndo)
        val one = start.choose(start.pool[0])
        assertThat(DraftEngine.undo(one).getOrThrow()).isEqualTo(start)
    }

    @Test
    fun `re-roll and swap only before the first choice`() {
        val start = houseSession()
        val rerolled = DraftEngine.reroll(start, seed = 99).getOrThrow()
        assertThat(rerolled.pool).isNotEqualTo(start.pool)
        val replacement = DraftEngine.swapCandidates(start).first()
        val swapped = DraftEngine.swapPoolHero(start, start.pool[4], replacement).getOrThrow()
        assertThat(swapped.pool[4]).isEqualTo(replacement)
        assertThat(swapped.pool).containsNoDuplicates()
        assertThat(DraftEngine.swapPoolHero(start, start.pool[0], start.pool[1]).isFailure).isTrue()

        val started = start.choose(start.pool[0])
        assertThat(DraftEngine.reroll(started, 1).exceptionOrNull()).isEqualTo(DraftError.AlreadyStarted)
        assertThat(DraftEngine.swapPoolHero(started, started.pool[3], replacement).exceptionOrNull())
            .isEqualTo(DraftError.AlreadyStarted)
    }

    @Test
    fun `single draft restricts each pick to the seat's own hand`() {
        val method = DraftFixtures.method("single-draft")
        val s = DraftEngine.start(DraftFixtures.setup(method, teamA = Team.RED)).getOrThrow()
        assertThat(s.hands).hasSize(4)
        assertThat(s.hands.values.flatten()).containsExactlyElementsIn(s.pool)
        val ownHand = s.handOf("red-0")!!
        val otherHand = s.handOf("blue-0")!!
        assertThat(DraftEngine.available(s)).containsExactlyElementsIn(ownHand)
        assertThat(DraftEngine.apply(s, DraftCommand(otherHand.first())).exceptionOrNull())
            .isEqualTo(DraftError.NotInHand(otherHand.first(), "red-0"))
    }

    @Test
    fun `all random is complete immediately and can be re-dealt`() {
        val method = DraftFixtures.method("all-random")
        val s = DraftEngine.start(DraftFixtures.setup(method, DraftFixtures.seats(3, 2))).getOrThrow()
        assertThat(s.isComplete).isTrue()
        assertThat(s.canUndo).isFalse()
        assertThat(DraftEngine.assignments(s).mapNotNull { it.heroId }).hasSize(5)
        assertThat(DraftEngine.reroll(s, 5).isSuccess).isTrue()
    }

    @Test
    fun `learning game seats fixed heroes and leaves missing ones open`() {
        val rule = SequenceRule.Lineup(listOf(TeamLineup(listOf("r1", "r2"), listOf("b1", "b2"))))
        val method =
            DraftMethod(
                "learn",
                "Learn",
                DraftMethodKind.CUSTOM,
                MethodOrigin.RULEBOOK,
                pool = PoolRule.All,
                sequence = rule,
            )
        val s = DraftEngine.start(
            DraftFixtures.setup(method, teamA = Team.BLUE, candidates = listOf("r1", "r2", "b1")),
        ).getOrThrow()
        assertThat(s.isComplete).isTrue()
        val assignments = DraftEngine.assignments(s)
        fun heroes(team: Team) = assignments.filter { it.seat.team == team }.map { it.heroId }
        assertThat(heroes(Team.RED)).containsExactly("r1", "r2")
        assertThat(heroes(Team.BLUE)).containsExactly("b1", null) // b2 is missing → its seat is open
        assertThat(DraftEngine.start(DraftFixtures.setup(method, DraftFixtures.seats(3, 3))).exceptionOrNull())
            .isInstanceOf(DraftError.NotSupported::class.java)
    }

    @Test
    fun `learning game deals each team's heroes randomly within the team, repeatably per seed`() {
        // RB p.5 step 7: the Red dashboards are distributed randomly to the players on the Red team.
        val method = DraftFixtures.method("learning-game")
        val candidates = listOf("wasp", "xargatha", "tigerclaw", "arien", "brogan", "sabina")
        fun deal(seed: Long): Map<String, String?> = DraftEngine.start(
            DraftFixtures.setup(method, DraftFixtures.seats(3, 3), candidates = candidates, seed = seed),
        ).getOrThrow().let { s -> DraftEngine.assignments(s).associate { it.seat.id to it.heroId } }

        val deals = (0L until 40L).map(::deal)
        for (d in deals) {
            assertThat(listOf("red-0", "red-1", "red-2").map { d[it] })
                .containsExactly("wasp", "xargatha", "tigerclaw")
            assertThat(listOf("blue-0", "blue-1", "blue-2").map { d[it] })
                .containsExactly("arien", "brogan", "sabina")
        }
        assertThat(deals.toSet().size).isGreaterThan(1) // not a fixed order
        assertThat(deal(7L)).isEqualTo(deal(7L)) // same seed, same deal
    }

    @Test
    fun `hidden steps stay concealed until the hidden run ends`() {
        val method = house.copy(
            sequence = SequenceRule.Pattern(
                repeat = listOf(pick(TeamSlot.A, hidden = true), pick(TeamSlot.B, hidden = true)),
            ),
        )
        var s = DraftEngine.start(DraftFixtures.setup(method, DraftFixtures.seats(1, 1))).getOrThrow()
        s = s.choose(s.pool[0])
        assertThat(s.isConcealed(0)).isTrue()
        s = s.choose(s.pool[1])
        assertThat(s.isConcealed(0)).isFalse() // complete → revealed
    }

    @Test
    fun `completed draft becomes a record and a pending match with seats and pilots`() {
        var s = DraftEngine.start(DraftFixtures.setup(house, DraftFixtures.seats(2, 2), teamA = Team.RED)).getOrThrow()
        while (!s.isComplete) s = s.choose(DraftEngine.available(s).first())
        s = DraftEngine.swapSeats(s, "red-0", "red-1").getOrThrow()
        s = DraftEngine.setHandicap(s, "blue-1", true).getOrThrow()

        val record = DraftEngine.toRecord(s, matchId = "m1")
        assertThat(record.methodId).isEqualTo("house")
        assertThat(record.teamA).isEqualTo(Team.RED)
        assertThat(record.steps.first()).isEqualTo(DraftStepRecord(Team.RED, DraftAction.BAN, s.history[0].heroId))
        assertThat(record.steps).hasSize(6)

        val match = DraftEngine.toPendingMatch(s, "m1", now = 5L).getOrThrow()
        assertThat(match.status).isEqualTo(MatchStatus.PENDING)
        assertThat(match.mode).isEqualTo(GameMode.QUICK)
        assertThat(match.draftId).isEqualTo(s.id)
        assertThat(match.tieBreaker).isEqualTo(Team.RED)
        assertThat(match.participants).hasSize(4)
        val redPicks = s.picks.filter { it.team == Team.RED }.map { it.heroId }
        assertThat(match.participants.first { it.playerId == "p-red-0" }.heroId).isEqualTo(redPicks[1])
        assertThat(match.participants.first { it.playerId == "p-blue-1" }.handicap).isTrue()
    }

    @Test
    fun `the pending match keeps the draft's Tie Breaker coin`() {
        var s = DraftEngine.start(DraftFixtures.setup(house, DraftFixtures.seats(2, 2), teamA = Team.BLUE)).getOrThrow()
        while (!s.isComplete) s = s.choose(DraftEngine.available(s).first())

        assertThat(DraftEngine.toPendingMatch(s, "m", 0).getOrThrow().tieBreaker).isEqualTo(Team.BLUE)
        assertThat(DraftEngine.toRecord(s, "m").teamA).isEqualTo(Team.BLUE)
    }

    @Test
    fun `seats can only be swapped within a team after completion`() {
        val s = houseSession()
        assertThat(DraftEngine.swapSeats(s, "red-0", "red-1").exceptionOrNull()).isEqualTo(DraftError.NotComplete)
        var done = s
        while (!done.isComplete) done = done.choose(DraftEngine.available(done).first())
        assertThat(DraftEngine.swapSeats(done, "red-0", "blue-0").exceptionOrNull())
            .isInstanceOf(DraftError.InvalidSeats::class.java)
    }

    @Test
    fun `pending match needs a pilot for every seat`() {
        val method = DraftFixtures.method("all-random")
        val s = DraftEngine.start(DraftFixtures.setup(method, DraftFixtures.seats(2, 2, pilots = false))).getOrThrow()
        assertThat(
            DraftEngine.toPendingMatch(s, "m", 0).exceptionOrNull(),
        ).isInstanceOf(DraftError.InvalidSeats::class.java)
    }

    @Test
    fun `sessions survive a JSON round trip`() {
        var s = houseSession()
        s = s.choose(s.pool[0])
        val json = DraftJson.lenient.encodeToString(DraftSession.serializer(), s)
        assertThat(DraftJson.lenient.decodeFromString(DraftSession.serializer(), json)).isEqualTo(s)
    }

    @Test
    fun `suggested mode matches the match setup default for every hero count`() {
        val setup = dev.quinton.goa.core.domain.setup.MatchSetup(dev.quinton.goa.core.domain.timer.bundledRules)
        for (heroes in 2..10) {
            val options = setup.modeOptions(heroes)
            val suggested = options.single { it.mode == DraftEngine.suggestedMode(heroes) }
            assertThat(suggested.fullySpecified).isTrue()
            options.firstOrNull { it.required }?.let { assertThat(suggested.mode).isEqualTo(it.mode) }
        }
        assertThat(DraftEngine.suggestedMode(6)).isEqualTo(GameMode.QUICK)
        assertThat(DraftEngine.suggestedMode(7)).isEqualTo(GameMode.DOUBLE_LANE)
        assertThat(DraftEngine.suggestedMode(8)).isEqualTo(GameMode.DOUBLE_LANE)
    }

    @Test
    fun `invalid seat layouts are rejected`() {
        val bad = listOf(Seat(Team.RED, 0), Seat(Team.RED, 2), Seat(Team.BLUE, 0))
        assertThat(DraftEngine.start(DraftFixtures.setup(house, bad)).exceptionOrNull())
            .isInstanceOf(DraftError.InvalidSeats::class.java)
        assertThat(DraftEngine.start(DraftFixtures.setup(house, DraftFixtures.seats(2, 0))).exceptionOrNull())
            .isInstanceOf(DraftError.InvalidSeats::class.java)
    }

    @Test
    fun `custom patterns that never fill the seats are rejected`() {
        val onlyBans = SequenceRule.Pattern(repeat = listOf(ban(TeamSlot.A)))
        assertThat(
            DraftPlanner.plan(onlyBans, 2, 2).exceptionOrNull(),
        ).isInstanceOf(DraftError.SequenceTooShort::class.java)
        val onlyA = SequenceRule.Pattern(repeat = listOf(pick(TeamSlot.A)))
        assertThat(
            DraftPlanner.plan(onlyA, 2, 2).exceptionOrNull(),
        ).isInstanceOf(DraftError.SequenceTooShort::class.java)
    }
}
