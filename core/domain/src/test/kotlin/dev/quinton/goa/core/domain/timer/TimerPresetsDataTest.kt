package dev.quinton.goa.core.domain.timer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimerPresetsDataTest {
    private val endIds = endOfRound.map { it.id }.toSet()

    @Test
    fun `bundled presets parse, are unique and valid`() {
        val catalog = bundledPresets
        assertThat(catalog.presets.map { it.id }).containsNoDuplicates()
        assertThat(catalog.presets.map { it.id }).contains(catalog.defaultPresetId)
        catalog.presets.forEach { preset ->
            assertThat(TimerPresetValidator.validate(preset, endIds)).isEmpty()
            assertThat(preset.turnsPerRound).isEqualTo(TimerPreset.RULEBOOK_TURNS_PER_ROUND)
        }
        assertThat(catalog.presets.map { it.name })
            .containsAtLeast("Table rules", "Speed guards", "Relaxed", "Untimed (counters only)")
    }

    @Test
    fun `default preset matches the owner's table rules`() {
        val table = bundledPresets.presets.single { it.id == bundledPresets.defaultPresetId }
        fun kind(k: PhaseKind) = table.phases.single { it.kind == k }

        assertThat(table.name).isEqualTo("Table rules")
        assertThat(kind(PhaseKind.STRATEGY).seconds).isEqualTo(90)
        assertThat(kind(PhaseKind.STRATEGY).scope).isEqualTo(PhaseScope.ROUND_START)
        assertThat(kind(PhaseKind.TURN).seconds).isEqualTo(30)
        assertThat(kind(PhaseKind.TURN).scope).isEqualTo(PhaseScope.TURN)
        assertThat(kind(PhaseKind.UPGRADE).seconds).isEqualTo(120)
        assertThat(kind(PhaseKind.UPGRADE).scope).isEqualTo(PhaseScope.ROUND_END)
        assertThat(kind(PhaseKind.UPGRADE).endStepId).isEqualTo("level_up")
        // Owner's table rules (2026-10-18): every hero resolving gets 0:30, each turn.
        assertThat(kind(PhaseKind.ACTION).enabled).isTrue()
        assertThat(kind(PhaseKind.ACTION).seconds).isEqualTo(30)
        assertThat(kind(PhaseKind.ACTION).scope).isEqualTo(PhaseScope.TURN)
        assertThat(table.autoAdvance).isFalse()
        // Rulebook p.7: cards are revealed, then resolved one by one in initiative order. The
        // untimed Resolve step stays in the preset for when the Action timer is switched off.
        assertThat(kind(PhaseKind.TURN).name).isEqualTo("Play a card")
        assertThat(kind(PhaseKind.RESOLVE).scope).isEqualTo(PhaseScope.TURN)
        assertThat(kind(PhaseKind.RESOLVE).seconds).isNull()
        val turnAt = table.phases.indexOf(kind(PhaseKind.TURN))
        assertThat(table.phases.indexOf(kind(PhaseKind.RESOLVE))).isEqualTo(turnAt + 1)
        // Table rules (not in the rulebook): hurry-up on demand, no time-outs, volume keys off.
        assertThat(table.shotClockSeconds).isEqualTo(30)
        assertThat(table.timeOutsPerRound).isEqualTo(0)
        assertThat(table.volumeKeys).isFalse()
    }

    @Test
    fun `every bundled preset carries the current data revision`() {
        assertThat(bundledPresets.presets.map { it.revision }.distinct()).containsExactly(3)
    }

    @Test
    fun `table rules resolve every hero on their own clock each turn`() {
        val table = bundledPresets.presets.single { it.id == bundledPresets.defaultPresetId }
        val plan = TimerPlan(table, endOfRound, actionSlots = 6)
        val turn = plan.steps.filter { it.turn == 1 }

        assertThat(turn.map { it.phase?.kind }).containsExactly(
            PhaseKind.TURN,
            PhaseKind.ACTION,
            PhaseKind.ACTION,
            PhaseKind.ACTION,
            PhaseKind.ACTION,
            PhaseKind.ACTION,
            PhaseKind.ACTION,
        ).inOrder()
        assertThat(turn.drop(1).map { it.seconds }.toSet()).containsExactly(30)
        assertThat(plan.steps.count { it.isAction }).isEqualTo(6 * TimerPreset.RULEBOOK_TURNS_PER_ROUND)
        assertThat(plan.steps.none { it.phase?.kind == PhaseKind.RESOLVE }).isTrue()
    }

    @Test
    fun `speed guards is table rules with 0_30 to strategise and 0_10 per hero resolving`() {
        val speed = bundledPresets.presets.single { it.id == "speed-guards" }
        val table = bundledPresets.presets.single { it.id == bundledPresets.defaultPresetId }
        fun seconds(p: TimerPreset, k: PhaseKind) = p.phases.single { it.kind == k }.seconds

        assertThat(speed.name).isEqualTo("Speed guards")
        assertThat(seconds(speed, PhaseKind.STRATEGY)).isEqualTo(30)
        assertThat(seconds(speed, PhaseKind.ACTION)).isEqualTo(10)
        assertThat(speed.phases.single { it.kind == PhaseKind.ACTION }.enabled).isTrue()
        // Everything else as at the table: same steps, Play a card 0:30, Upgrade 2:00.
        assertThat(speed.phases.map { it.id }).isEqualTo(table.phases.map { it.id })
        assertThat(seconds(speed, PhaseKind.TURN)).isEqualTo(seconds(table, PhaseKind.TURN))
        assertThat(seconds(speed, PhaseKind.UPGRADE)).isEqualTo(seconds(table, PhaseKind.UPGRADE))
        val turn = TimerPlan(speed, endOfRound, actionSlots = 4).steps.filter { it.turn == 1 }
        assertThat(turn.drop(1).map { it.seconds }).containsExactly(10, 10, 10, 10)
    }

    @Test
    fun `saved presets round trip`() {
        val presets = bundledPresets.presets
        assertThat(TimerJson.decodePresets(TimerJson.encodePresets(presets))).isEqualTo(presets)
    }

    @Test
    fun `validator reports each problem`() {
        val bad = TimerPreset(
            id = "x",
            name = " ",
            turnsPerRound = 0,
            phases = listOf(
                phase("a", PhaseKind.CUSTOM, PhaseScope.TURN, 2, cues = emptyList()),
                phase("a", PhaseKind.ACTION, PhaseScope.ROUND_START, 30),
                phase("c", PhaseKind.CUSTOM, PhaseScope.ROUND_END, 30, endStepId = "nope"),
                phase("d", PhaseKind.CUSTOM, PhaseScope.TURN, 20, cues = listOf(25)),
                PhaseSpec("e", "", PhaseKind.CUSTOM, PhaseScope.TURN, null, cues = emptyList()),
            ),
        )
        val kinds = TimerPresetValidator.validate(bad, endIds).map { it.kind }

        assertThat(kinds).containsExactly(
            PresetIssue.Kind.BLANK_NAME,
            PresetIssue.Kind.TURNS_OUT_OF_RANGE,
            PresetIssue.Kind.DURATION_OUT_OF_RANGE,
            PresetIssue.Kind.DUPLICATE_PHASE_ID,
            PresetIssue.Kind.ACTION_NOT_PER_TURN,
            PresetIssue.Kind.UNKNOWN_END_STEP,
            PresetIssue.Kind.CUE_OUT_OF_RANGE,
            PresetIssue.Kind.BLANK_PHASE_NAME,
        )
    }
}
