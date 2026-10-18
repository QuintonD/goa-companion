package dev.quinton.goa.core.domain.timer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimerPlanTest {
    @Test
    fun `table rules round is strategy, four turns, then the six-step checklist with upgrade on level up`() {
        val plan = TimerPlan(tableRules(), endOfRound, actionSlots = 4)

        assertThat(plan.steps).hasSize(1 + 4 + 6)
        assertThat(plan.steps[0].phase!!.kind).isEqualTo(PhaseKind.STRATEGY)
        assertThat(plan.steps.subList(1, 5).map { it.turn }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(plan.steps.subList(1, 5).all { it.phase!!.kind == PhaseKind.TURN }).isTrue()
        val end = plan.steps.subList(5, 11)
        assertThat(end.map { it.endStep!!.id }).containsExactly(
            "minion_battle",
            "remove_tokens",
            "push_lane",
            "retrieve_cards",
            "level_up",
            "pity_coin",
        ).inOrder()
        assertThat(end.map { it.endStepNumber }).containsExactly(1, 2, 3, 4, 5, 6).inOrder()
        assertThat(end[4].phase!!.kind).isEqualTo(PhaseKind.UPGRADE)
        assertThat(end[4].seconds).isEqualTo(120)
        assertThat(end.filterIndexed { i, _ -> i != 4 }.all { !it.timed && it.phase == null }).isTrue()
        assertThat(plan.firstEndOfRoundIndex).isEqualTo(5)
        assertThat(plan.steps.map { it.index }).isEqualTo(plan.steps.indices.toList())
    }

    @Test
    fun `action phase expands to one step per hero seat after each turn`() {
        val plan = TimerPlan(tableRules(actionEnabled = true), endOfRound, actionSlots = 3)

        assertThat(plan.steps).hasSize(1 + 4 * (1 + 3) + 6)
        val turn2 = plan.steps.filter { it.turn == 2 }
        assertThat(turn2.map { it.phase!!.kind }).containsExactly(
            PhaseKind.TURN,
            PhaseKind.ACTION,
            PhaseKind.ACTION,
            PhaseKind.ACTION,
        ).inOrder()
        assertThat(turn2.mapNotNull { it.actionSlot }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `disabled phases are skipped entirely`() {
        val preset = tableRules().let { p ->
            p.copy(phases = p.phases.map { if (it.kind == PhaseKind.STRATEGY) it.copy(enabled = false) else it })
        }
        val plan = TimerPlan(preset, endOfRound, actionSlots = 4)

        assertThat(plan.steps.none { it.phase?.kind == PhaseKind.STRATEGY }).isTrue()
        assertThat(plan.steps.first().turn).isEqualTo(1)
    }

    @Test
    fun `disabled upgrade leaves level up as a plain checklist step`() {
        val preset = tableRules().let { p ->
            p.copy(phases = p.phases.map { if (it.kind == PhaseKind.UPGRADE) it.copy(enabled = false) else it })
        }
        val levelUp = TimerPlan(preset, endOfRound, 4).steps.single { it.endStep?.id == "level_up" }

        assertThat(levelUp.phase).isNull()
        assertThat(levelUp.timed).isFalse()
    }

    @Test
    fun `unattached round-end phases run after the checklist, custom phases keep their order`() {
        val preset = tableRules().let { p ->
            p.copy(
                phases = listOf(phase("muster", PhaseKind.CUSTOM, PhaseScope.ROUND_START, 20)) + p.phases +
                    phase("recap", PhaseKind.CUSTOM, PhaseScope.ROUND_END, 60),
            )
        }
        val plan = TimerPlan(preset, endOfRound, 4)

        assertThat(plan.steps[0].phase!!.id).isEqualTo("muster")
        assertThat(plan.steps[1].phase!!.id).isEqualTo("strategy")
        assertThat(plan.steps.last().phase!!.id).isEqualTo("recap")
        assertThat(plan.steps.last().endStep).isNull()
    }

    @Test
    fun `turns per round comes from the preset`() {
        val plan = TimerPlan(tableRules().copy(turnsPerRound = 2), endOfRound, 4)

        assertThat(plan.steps.mapNotNull { it.turn }.distinct()).containsExactly(1, 2)
    }

    @Test
    fun `untimed phases have no duration or cues`() {
        val untimed = bundledPresets.presets.single { it.id == "untimed" }
        val plan = TimerPlan(untimed, endOfRound, 4)

        assertThat(plan.steps.none { it.timed }).isTrue()
        assertThat(plan.steps.all { it.cues.isEmpty() }).isTrue()
    }

    @Test
    fun `each turn is play a card then an untimed resolve step`() {
        val plan = TimerPlan(tableRules(resolve = true), endOfRound, actionSlots = 4)

        assertThat(plan.steps).hasSize(1 + 4 * 2 + 6)
        val turn3 = plan.steps.filter { it.turn == 3 }
        assertThat(turn3.map { it.phase!!.kind }).containsExactly(PhaseKind.TURN, PhaseKind.RESOLVE).inOrder()
        assertThat(turn3[0].seconds).isEqualTo(30)
        assertThat(turn3[1].timed).isFalse()
        assertThat(turn3[1].cues).isEmpty()
    }

    @Test
    fun `with the action timer on, the action steps are the resolution and resolve is dropped`() {
        val plan = TimerPlan(tableRules(actionEnabled = true, resolve = true), endOfRound, actionSlots = 3)

        assertThat(plan.steps.none { it.phase?.kind == PhaseKind.RESOLVE }).isTrue()
        assertThat(plan.steps.filter { it.turn == 1 }.map { it.phase!!.kind }).containsExactly(
            PhaseKind.TURN,
            PhaseKind.ACTION,
            PhaseKind.ACTION,
            PhaseKind.ACTION,
        ).inOrder()
    }

    @Test
    fun `bundled table rules and relaxed time every hero resolving each turn, untimed does not`() {
        fun kinds(id: String) = TimerPlan(bundledPresets.presets.single { it.id == id }, endOfRound, 4)
            .steps.filter { it.turn == 1 }.map { it.phase!!.kind }
        val perHero = listOf(PhaseKind.TURN) + List(4) { PhaseKind.ACTION }

        assertThat(kinds("table-rules")).containsExactlyElementsIn(perHero).inOrder()
        assertThat(kinds("relaxed")).containsExactlyElementsIn(perHero).inOrder()
        assertThat(kinds("untimed")).containsExactly(PhaseKind.TURN)
    }
}
