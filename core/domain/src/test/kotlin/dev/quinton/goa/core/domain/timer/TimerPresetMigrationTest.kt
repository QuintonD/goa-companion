package dev.quinton.goa.core.domain.timer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimerPresetMigrationTest {
    private val bundled = bundledPresets.presets

    /** The table-rules preset as version 1 of the app stored it (no revision, no Resolve). */
    private val storedV0 = TimerJson.decodePresets(
        """
        [{"id":"table-rules","name":"Table rules","turnsPerRound":4,"autoAdvance":false,"phases":[
          {"id":"strategy","name":"Pre-round strategy","kind":"strategy","scope":"round_start","seconds":90,"cues":[10,5,0]},
          {"id":"turn","name":"Turn","kind":"turn","scope":"turn","seconds":45,"cues":[10,5,0]},
          {"id":"action","name":"Action","kind":"action","scope":"turn","seconds":30,"enabled":false,"cues":[5,0]},
          {"id":"upgrade","name":"Upgrade","kind":"upgrade","scope":"round_end","seconds":120,"cues":[10,5,0],"endStepId":"level_up"}
        ]}]
        """.trimIndent(),
    ).single()

    @Test
    fun `stored copy of a default gets the resolve step and keeps the user's edits`() {
        val upgraded = TimerPresetMigration.upgrade(listOf(storedV0), bundled).single()

        assertThat(upgraded.phases.map { it.id }).containsExactly("strategy", "turn", "resolve", "action", "upgrade")
            .inOrder()
        assertThat(upgraded.phases.single { it.id == "turn" }.seconds).isEqualTo(45)
        assertThat(upgraded.phases.single { it.id == "turn" }.name).isEqualTo("Play a card")
        assertThat(upgraded.phases.single { it.id == "resolve" }.seconds).isNull()
        assertThat(upgraded.phases.single { it.id == "action" }.enabled).isTrue()
        assertThat(upgraded.revision).isEqualTo(2)
        assertThat(upgraded.shotClockSeconds).isEqualTo(TimerPreset.DEFAULT_SHOT_CLOCK_SECONDS)
    }

    @Test
    fun `migration is idempotent and leaves current presets alone`() {
        val once = TimerPresetMigration.upgrade(listOf(storedV0), bundled)
        assertThat(TimerPresetMigration.upgrade(once, bundled)).isEqualTo(once)
        assertThat(TimerPresetMigration.upgrade(bundled, bundled)).isEqualTo(bundled)
    }

    @Test
    fun `a renamed turn phase keeps its name`() {
        val renamed = storedV0.copy(
            phases = storedV0.phases.map { if (it.id == "turn") it.copy(name = "Cards") else it },
        )

        val upgraded = TimerPresetMigration.upgrade(listOf(renamed), bundled).single()

        assertThat(upgraded.phases.single { it.id == "turn" }.name).isEqualTo("Cards")
    }

    @Test
    fun `a user-added resolve phase is not duplicated`() {
        val custom = phase("x1", PhaseKind.CUSTOM, PhaseScope.TURN, null, cues = emptyList()).copy(name = "Resolve")
        val withCustom = storedV0.copy(phases = storedV0.phases + custom)

        val upgraded = TimerPresetMigration.upgrade(listOf(withCustom), bundled).single()

        assertThat(upgraded.phases.count { it.name == "Resolve" }).isEqualTo(1)
        assertThat(upgraded.phases.none { it.kind == PhaseKind.RESOLVE }).isTrue()
    }

    /** Table rules as version 0.3.0 stored it: revision 1, per-hero Action timer off. */
    private val storedV1 = bundled.single { it.id == "table-rules" }.let { table ->
        table.copy(
            revision = 1,
            phases = table.phases.map {
                if (it.kind == PhaseKind.ACTION) it.copy(enabled = false, seconds = 40) else it
            },
        )
    }

    @Test
    fun `revision 2 switches the per-hero action timer on and keeps its length`() {
        val upgraded = TimerPresetMigration.upgrade(listOf(storedV1), bundled).single()

        val action = upgraded.phases.single { it.kind == PhaseKind.ACTION }
        assertThat(action.enabled).isTrue()
        assertThat(action.seconds).isEqualTo(40)
        assertThat(upgraded.phases.map { it.id }).isEqualTo(storedV1.phases.map { it.id })
        assertThat(upgraded.revision).isEqualTo(2)
        assertThat(TimerPresetMigration.upgrade(listOf(upgraded), bundled)).containsExactly(upgraded)
    }

    @Test
    fun `revision 2 adds the action timer after the turn phases when the copy has none`() {
        val noAction = storedV1.copy(phases = storedV1.phases.filterNot { it.kind == PhaseKind.ACTION })

        val upgraded = TimerPresetMigration.upgrade(listOf(noAction), bundled).single()

        assertThat(upgraded.phases.map { it.id }).containsExactly("strategy", "turn", "resolve", "action", "upgrade")
            .inOrder()
        assertThat(upgraded.phases.single { it.id == "action" }.enabled).isTrue()
    }

    @Test
    fun `presets the user made are never changed`() {
        val mine = storedV0.copy(id = "6b0f-user", name = "Fast")

        assertThat(TimerPresetMigration.upgrade(listOf(mine), bundled)).containsExactly(mine)
    }

    @Test
    fun `untimed default only gets the new turn name`() {
        val untimed = bundled.single { it.id == "untimed" }
        val old = untimed.copy(
            revision = 0,
            phases = untimed.phases.map { if (it.kind == PhaseKind.TURN) it.copy(name = "Turn") else it },
        )

        assertThat(TimerPresetMigration.upgrade(listOf(old), bundled)).containsExactly(untimed)
    }
}
