package dev.quinton.goa.core.domain.setup

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.domain.timer.bundledRules
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random
import org.junit.Test

class MatchSetupTest {
    private val setup = MatchSetup(bundledRules)

    private fun options(heroes: Int) = setup.modeOptions(heroes).associateBy { it.mode }

    /** Expected (available, required, life counters) per mode for a hero count — DOMAIN.md table. */
    private data class Expect(val available: Boolean, val required: Boolean, val life: Int?)

    private fun expect(heroes: Int, quick: Expect, long: Expect, double: Expect) {
        val o = options(heroes)
        listOf(GameMode.QUICK to quick, GameMode.LONG to long, GameMode.DOUBLE_LANE to double).forEach { (mode, e) ->
            val option = o.getValue(mode)
            assertWithMessage("$mode available @ $heroes heroes").that(option.available).isEqualTo(e.available)
            assertWithMessage("$mode required @ $heroes heroes").that(option.required).isEqualTo(e.required)
            assertWithMessage("$mode life @ $heroes heroes").that(option.lifeCountersPerTeam).isEqualTo(e.life)
        }
    }

    @Test
    fun `wave counters per mode`() {
        val o = options(6)
        assertThat(o.getValue(GameMode.QUICK).totalWaveCounters).isEqualTo(3)
        assertThat(o.getValue(GameMode.LONG).totalWaveCounters).isEqualTo(5)
        assertThat(o.getValue(GameMode.DOUBLE_LANE).lanes).isEqualTo(2)
        assertThat(o.getValue(GameMode.DOUBLE_LANE).waveCountersPerLane).isEqualTo(7)
        assertThat(o.getValue(GameMode.DOUBLE_LANE).totalWaveCounters).isEqualTo(14)
    }

    @Test
    fun `player range is 2 to 10 with 4 and 6 recommended`() {
        assertThat(setup.playerRange).isEqualTo(2..10)
        assertThat((2..10).filter(setup::isRecommended)).containsExactly(4, 6)
        assertThat((2..10).filter(setup::isExtended)).containsExactly(3, 5, 7, 8, 9, 10)
    }

    @Test
    fun `two players control two heroes each`() {
        val layout = setup.layouts(2).single()
        assertThat(layout.kind).isEqualTo(LayoutKind.RULEBOOK)
        assertThat(layout.heroes).isEqualTo(4)
        assertThat(layout.doublePilots).isEqualTo(2)
        expect(4, Expect(true, false, 4), Expect(true, false, 6), Expect(false, false, null))
    }

    @Test
    fun `three players field four heroes with one handicap`() {
        val layout = setup.layouts(3).single()
        assertThat(layout.heroes).isEqualTo(4)
        assertThat(layout.doublePilots).isEqualTo(1)
        assertThat(layout.handicapHeroes).isEqualTo(1)
        assertThat(setup.plan(3).selected.lifeCountersPerTeam).isEqualTo(4)
    }

    @Test
    fun `four players quick and long`() {
        assertThat(setup.layouts(4).single().kind).isEqualTo(LayoutKind.STANDARD)
        expect(4, Expect(true, false, 4), Expect(true, false, 6), Expect(false, false, null))
        assertThat(options(4).getValue(GameMode.DOUBLE_LANE).block).isEqualTo(ModeBlock.TOO_FEW_HEROES)
    }

    @Test
    fun `five players offer handicap or double pilot`() {
        val (handicap, double) = setup.layouts(5)
        assertThat(handicap.kind).isEqualTo(LayoutKind.HANDICAP)
        assertThat(handicap.largerTeamHeroes to handicap.smallerTeamHeroes).isEqualTo(3 to 2)
        assertThat(handicap.handicapHeroes).isEqualTo(3)
        assertThat(double.kind).isEqualTo(LayoutKind.DOUBLE_PILOT)
        assertThat(double.largerTeamHeroes to double.smallerTeamHeroes).isEqualTo(3 to 3)
        assertThat(double.doublePilots).isEqualTo(1)
        expect(5, Expect(true, false, 4), Expect(true, false, 6), Expect(false, false, null))
        // Double pilot puts 6 heroes on the board.
        expect(6, Expect(true, false, 5), Expect(true, false, 8), Expect(true, false, 6))
    }

    @Test
    fun `six players may choose double lane`() {
        expect(6, Expect(true, false, 5), Expect(true, false, 8), Expect(true, false, 6))
        assertThat(setup.plan(6).selected.mode).isEqualTo(GameMode.QUICK)
        assertThat(setup.plan(6, mode = GameMode.DOUBLE_LANE).selected.mode).isEqualTo(GameMode.DOUBLE_LANE)
    }

    @Test
    fun `seven players - life counters for single lane are not listed by the rules`() {
        assertThat(setup.layouts(7).map { it.kind }).containsExactly(LayoutKind.HANDICAP, LayoutKind.DOUBLE_PILOT)
        expect(7, Expect(true, false, null), Expect(true, false, null), Expect(true, false, 6))
        // Double pilot: 8 heroes, double lane required.
        val plan = setup.plan(7, layoutId = "double-pilot-7")
        assertThat(plan.heroes).isEqualTo(8)
        assertThat(plan.selected.mode).isEqualTo(GameMode.DOUBLE_LANE)
        assertThat(plan.selected.required).isTrue()
    }

    @Test
    fun `eight players require double lane`() {
        expect(8, Expect(false, false, null), Expect(false, false, null), Expect(true, true, 6))
        assertThat(options(8).getValue(GameMode.QUICK).block).isEqualTo(ModeBlock.OTHER_MODE_REQUIRED)
        assertThat(setup.plan(8, mode = GameMode.QUICK).selected.mode).isEqualTo(GameMode.DOUBLE_LANE)
    }

    @Test
    fun `nine players`() {
        assertThat(setup.layouts(9).map { it.heroes }).containsExactly(9, 10).inOrder()
        expect(9, Expect(false, false, null), Expect(false, false, null), Expect(true, true, 7))
    }

    @Test
    fun `ten players`() {
        assertThat(setup.layouts(10).single().heroes).isEqualTo(10)
        expect(10, Expect(false, false, null), Expect(false, false, null), Expect(true, true, 7))
    }

    @Test
    fun `every player count 2 to 10 has a valid plan`() {
        for (players in 2..10) {
            val layouts = setup.layouts(players)
            assertThat(layouts).isNotEmpty()
            layouts.forEach { layout ->
                val plan = setup.plan(players, layout.id)
                assertThat(plan.layout).isEqualTo(layout)
                assertThat(plan.selected.available).isTrue()
                assertThat(plan.heroes).isAtLeast(players)
                assertThat(layout.largerTeamHeroes - layout.smallerTeamHeroes).isIn(0..1)
                // Heroes = players + extra heroes controlled by double pilots.
                assertThat(layout.heroes).isEqualTo(players + layout.doublePilots)
            }
            if (players % 2 == 1 && players >= 5) {
                assertThat(layouts.map { it.kind }).containsExactly(LayoutKind.HANDICAP, LayoutKind.DOUBLE_PILOT)
            }
        }
    }

    @Test
    fun `default mode has wave and life counts for every hero count 2 to 10`() {
        for (heroes in 2..10) {
            val plan = setup.planForSeats(players = heroes, redHeroes = (heroes + 1) / 2, blueHeroes = heroes / 2)
            assertWithMessage("default mode @ $heroes heroes").that(plan.selected.available).isTrue()
            assertWithMessage("wave counters @ $heroes heroes").that(plan.selected.waveCountersPerLane).isGreaterThan(0)
            assertWithMessage("life counters @ $heroes heroes").that(plan.selected.lifeCountersPerTeam).isNotNull()
        }
        for (players in 2..10) {
            setup.layouts(players).forEach { layout ->
                val selected = setup.plan(players, layout.id).selected
                assertWithMessage("life counters for ${layout.id}").that(selected.lifeCountersPerTeam).isNotNull()
            }
        }
    }

    @Test
    fun `seven heroes with the handicap layout default to double lane`() {
        val plan = setup.plan(7, layoutId = "handicap-7")
        assertThat(plan.selected.mode).isEqualTo(GameMode.DOUBLE_LANE)
        assertThat(plan.selected.lifeCountersPerTeam).isEqualTo(6)
        // An explicit choice is still honoured, even without listed Life counters.
        assertThat(
            setup.plan(7, layoutId = "handicap-7", mode = GameMode.QUICK).selected.mode,
        ).isEqualTo(GameMode.QUICK)
    }

    @Test
    fun `a suggested mode is only used when its counters are fully specified`() {
        val sevenQuick = setup.planForSeats(players = 7, redHeroes = 4, blueHeroes = 3, suggestedMode = GameMode.QUICK)
        assertThat(sevenQuick.selected.mode).isEqualTo(GameMode.DOUBLE_LANE)
        val sixLong = setup.planForSeats(players = 6, redHeroes = 3, blueHeroes = 3, suggestedMode = GameMode.LONG)
        assertThat(sixLong.selected.mode).isEqualTo(GameMode.LONG)
    }

    @Test
    fun `plan for known seats finds the matching layout`() {
        val plan = setup.planForSeats(players = 5, redHeroes = 3, blueHeroes = 3, mode = GameMode.LONG)
        assertThat(plan.layout.id).isEqualTo("double-pilot-5")
        assertThat(plan.selected.mode).isEqualTo(GameMode.LONG)
        assertThat(plan.selected.lifeCountersPerTeam).isEqualTo(8)

        val custom = setup.planForSeats(players = 4, redHeroes = 3, blueHeroes = 2)
        assertThat(custom.layout.kind).isEqualTo(LayoutKind.HANDICAP)
        assertThat(custom.heroes).isEqualTo(5)
    }

    @Test
    fun `rulebook per-player values match pages 5 and 20`() {
        fun life(mode: GameMode) = (2..10).map { bundledRules.mode(mode).rulebookLifeCountersFor(it) }
        // Players 2..10. p.5 (4 and 6 players); p.20: 2 players use the 4-player setup, 3 and 5 use
        // 4-player Life counters; double-lane table 6-8 players 6, 9-10 players 7.
        assertThat(life(GameMode.QUICK)).containsExactly(4, 4, 4, 4, 5, null, null, null, null).inOrder()
        assertThat(life(GameMode.LONG)).containsExactly(6, 6, 6, 6, 8, null, null, null, null).inOrder()
        assertThat(life(GameMode.DOUBLE_LANE)).containsExactly(null, null, null, null, 6, 6, 6, 7, 7).inOrder()
        val double = bundledRules.mode(GameMode.DOUBLE_LANE)
        assertThat((2..10).filter(double::listedFor)).containsExactly(6, 7, 8, 9, 10)
        assertThat((2..10).filter(double::requiredFor)).containsExactly(8, 9, 10)
        assertThat(double.rulesNote!!.title).contains("p.20")
        assertThat(double.rulesNote!!.points).isNotEmpty()
    }

    @Test
    fun `official layouts never differ from the rulebook`() {
        for (players in 2..10) {
            setup.layouts(players).filter { it.kind != LayoutKind.DOUBLE_PILOT }.forEach { layout ->
                setup.modeOptions(layout.heroes).filter { it.available }.forEach { option ->
                    val plan = setup.plan(players, layout.id, option.mode)
                    assertWithMessage("${layout.id} ${option.mode}").that(setup.houseRuleDifference(plan)).isNull()
                }
            }
        }
    }

    @Test
    fun `double pilot differences from the rulebook are reported`() {
        val fiveQuick = setup.houseRuleDifference(setup.plan(5, "double-pilot-5", GameMode.QUICK))!!
        assertThat(fiveQuick.heroes).isEqualTo(6)
        assertThat(fiveQuick.lifeDiffers).isTrue()
        assertThat(fiveQuick.rulebookLife).isEqualTo(mapOf(GameMode.QUICK to 4, GameMode.LONG to 6))
        assertThat(fiveQuick.modeNotListed).isFalse()

        val fiveDouble = setup.houseRuleDifference(setup.plan(5, "double-pilot-5", GameMode.DOUBLE_LANE))!!
        assertThat(fiveDouble.modeNotListed).isTrue()

        val seven = setup.houseRuleDifference(setup.plan(7, "double-pilot-7"))!!
        assertThat(seven.mode).isEqualTo(GameMode.DOUBLE_LANE)
        assertThat(seven.requiredByHouseRule).isTrue()
        assertThat(seven.lifeDiffers).isFalse()

        // 9 players as 5 v 5: same counters and the rulebook requires Double lane from 8 players anyway.
        assertThat(setup.houseRuleDifference(setup.plan(9, "double-pilot-9"))).isNull()
    }

    @Test
    fun `every end of round step explains itself`() {
        bundledRules.endOfRound.forEach { assertWithMessage(it.id).that(it.detail).isNotEmpty() }
        val byId = bundledRules.endOfRound.associateBy { it.id }
        assertThat(byId.getValue("minion_battle").detail).contains("Heavy minion goes last")
        assertThat(byId.getValue("retrieve_cards").detail).contains("Next Turn")
        assertThat(byId.getValue("level_up").detail).startsWith("Mandatory")
    }

    @Test
    fun `end of round has the six official steps`() {
        assertThat(bundledRules.endOfRound.map { it.title }).containsExactly(
            "Minion Battle",
            "Remove Tokens, Return Markers",
            "Push the Lane",
            "Retrieve Cards",
            "Level Up",
            "Collect a Pity Coin",
        ).inOrder()
    }

    @Test
    fun `tie breaker flip is fair and deterministic per seed`() {
        val random = Random(1)
        val flips = List(10_000) { MatchSetup.flipTieBreaker(random) }
        val red = flips.count { it == Team.RED }
        assertThat(red).isIn(4_800..5_200)
        assertThat(MatchSetup.flipTieBreaker(Random(9))).isEqualTo(MatchSetup.flipTieBreaker(Random(9)))
    }
}
