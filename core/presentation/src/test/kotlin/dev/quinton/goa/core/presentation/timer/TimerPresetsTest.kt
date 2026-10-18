package dev.quinton.goa.core.presentation.timer

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.PhaseScope
import org.junit.Test

class TimerPresetsTest {
    private val catalog = TimerTestData.presets
    private val bundled = catalog.presets

    @Test
    fun `nothing stored means the bundled presets and default`() {
        val list = TimerPresets.effective(null, catalog)
        assertThat(list).isEqualTo(bundled)
        assertThat(TimerPresets.effective(emptyList(), catalog)).isEqualTo(bundled)
        assertThat(TimerPresets.selectedId(list, null, catalog)).isEqualTo(catalog.defaultPresetId)
        assertThat(TimerPresets.selectedId(list, "gone", catalog)).isEqualTo(catalog.defaultPresetId)
        assertThat(TimerPresets.selectedId(list, bundled.last().id, catalog)).isEqualTo(bundled.last().id)
    }

    @Test
    fun `a stored copy of a bundled preset from before revision 1 is upgraded on read`() {
        val old = bundled.first().let { p ->
            p.copy(revision = 0, phases = p.phases.filterNot { it.kind == PhaseKind.RESOLVE })
        }
        val list = TimerPresets.effective(listOf(old), catalog)
        assertThat(list.first { it.id == old.id }.phases.map { it.kind }).contains(PhaseKind.RESOLVE)
    }

    @Test
    fun `save, duplicate, delete and restore defaults`() {
        val copy = TimerPresets.duplicate(bundled.first(), "x")
        assertThat(copy.name).isEqualTo("${bundled.first().name} copy")
        val saved = TimerPresets.save(bundled, copy)
        assertThat(saved.last().id).isEqualTo("x")
        assertThat(TimerPresets.save(saved, copy.copy(name = "Mine")).map { it.id }).isEqualTo(saved.map { it.id })

        val fewer = TimerPresets.delete(saved, bundled.first().id)!!
        assertThat(fewer.map { it.id }).doesNotContain(bundled.first().id)
        assertThat(TimerPresets.delete(listOf(copy), "x")).isNull()
        assertThat(TimerPresets.delete(saved, "nope")).isNull()
        assertThat(TimerPresets.restoreDefaults(fewer, catalog).first().id).isEqualTo(bundled.first().id)
    }

    @Test
    fun `tonight changes timed turn phases only and drops cues that no longer fit`() {
        val table = bundled.first { it.id == "table-rules" }
        val tonight = TimerPresets.withTonight(table, 8)
        val turn = tonight.phases.first { it.kind == PhaseKind.TURN }
        assertThat(turn.seconds).isEqualTo(8)
        assertThat(turn.cues).containsExactly(5, 0)
        assertThat(tonight.phases.first { it.kind == PhaseKind.STRATEGY }).isEqualTo(table.phases.first())
        assertThat(TimerPresets.withTonight(table, null)).isSameInstanceAs(table)
        assertThat(TimerPresets.turnSeconds(table)).isEqualTo(table.phases.first { it.kind == PhaseKind.TURN }.seconds)
    }

    @Test
    fun `editor changes`() {
        val table = bundled.first { it.id == "table-rules" }
        val turn = table.phases.first { it.kind == PhaseKind.TURN }.copy(cues = listOf(10, 5, 0))
        assertThat(PresetEdits.setSeconds(turn, 8).cues).containsExactly(5, 0)
        assertThat(PresetEdits.setSeconds(turn, 3).cues).containsExactly(0)
        assertThat(PresetEdits.setSeconds(turn, null).cues).isEmpty()
        assertThat(PresetEdits.toggleCue(turn, 30).cues).containsExactly(30, 10, 5, 0).inOrder()
        assertThat(PresetEdits.toggleCue(turn, 10).cues).containsExactly(5, 0).inOrder()

        val added = PresetEdits.addPhase(table, PhaseScope.TURN, "c1")
        assertThat(added.phases.last().kind).isEqualTo(PhaseKind.CUSTOM)
        val moved = PresetEdits.move(added, "c1", -1)
        val before = added.phases.filter { it.scope == PhaseScope.TURN }.map { it.id }
        val after = moved.phases.filter { it.scope == PhaseScope.TURN }.map { it.id }
        assertThat(after.indexOf("c1")).isEqualTo(before.indexOf("c1") - 1)
        assertThat(PresetEdits.removePhase(added, "c1")).isEqualTo(table)
        assertThat(PresetEdits.removePhase(table, turn.id)).isEqualTo(table)
        assertThat(PresetEdits.setTimeOutsPerRound(table, 9).timeOutsPerRound).isEqualTo(3)

        val fresh = PresetEdits.newPreset(table, "n")
        assertThat(fresh.name).isEmpty()
        assertThat(fresh.revision).isEqualTo(0)
        assertThat(fresh.phases).isEqualTo(table.phases)
    }
}
