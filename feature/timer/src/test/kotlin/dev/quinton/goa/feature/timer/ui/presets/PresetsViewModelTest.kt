package dev.quinton.goa.feature.timer.ui.presets

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.PresetIssue
import dev.quinton.goa.feature.timer.MainDispatcherRule
import dev.quinton.goa.feature.timer.TimerHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PresetsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = TimerHarness()

    @Test
    fun `select, duplicate, delete and restore defaults`() = runTest {
        val vm = PresetsViewModel(h.repository)
        assertThat(vm.uiState.first { !it.loading }.presets).hasSize(4)

        vm.select("relaxed")
        assertThat(vm.uiState.first { it.selectedId == "relaxed" }.selectedId).isEqualTo("relaxed")

        vm.duplicate("table-rules")
        val copy = vm.uiState.first { it.presets.size == 5 }.presets.last()
        assertThat(copy.name).isEqualTo("Table rules copy")

        vm.delete("table-rules")
        vm.delete("untimed")
        assertThat(vm.uiState.first { it.presets.size == 3 }.presets.map { it.id }).doesNotContain("table-rules")

        vm.restoreDefaults()
        assertThat(
            vm.uiState.first {
                it.presets.size == 5
            }.presets.map { it.id },
        ).containsAtLeast("table-rules", "untimed")
    }

    @Test
    fun `the last preset cannot be deleted`() = runTest {
        val vm = PresetsViewModel(h.repository)
        vm.delete("relaxed")
        vm.delete("speed-guards")
        vm.delete("untimed")
        vm.delete("table-rules")
        val state = vm.uiState.first { it.presets.size == 1 }
        assertThat(state.canDelete).isFalse()
        assertThat(state.selectedId).isEqualTo("table-rules")
    }

    @Test
    fun `editing durations, cues and order then saving`() = runTest {
        val vm = PresetEditViewModel("table-rules", h.repository)
        val loaded = vm.uiState.first { !it.loading }
        assertThat(loaded.isNew).isFalse()
        assertThat(loaded.endSteps).hasSize(6)

        vm.setSeconds("turn", 45)
        vm.setSeconds("strategy", 8) // cues 10 and 5 no longer fit
        vm.toggleCue("turn", 30)
        vm.setEnabled("action", true)
        vm.addPhase(PhaseScope.TURN)
        val added = vm.uiState.value.preset!!.phases.last()
        vm.move(added.id, -1) // above Action
        vm.move(added.id, -1) // above Resolve
        vm.move(added.id, -1) // above Turn
        vm.setAutoAdvance(true)
        vm.setShotClock(45)
        vm.setTimeOutsPerRound(1)
        vm.setTimeOutSeconds(60)
        vm.setVolumeKeys(true)

        val preset = vm.uiState.value.preset!!
        assertThat(preset.phases.single { it.id == "strategy" }.cues).containsExactly(5, 0).inOrder()
        assertThat(preset.phases.single { it.id == "turn" }.cues).containsExactly(30, 10, 5, 0).inOrder()
        assertThat(preset.phases.filter { it.scope == PhaseScope.TURN }.map { it.kind })
            .containsExactly(PhaseKind.CUSTOM, PhaseKind.TURN, PhaseKind.RESOLVE, PhaseKind.ACTION).inOrder()

        vm.navigation.test {
            vm.save()
            assertThat(awaitItem()).isEqualTo(PresetEditEvent.Saved)
        }
        val saved = h.repository.preset("table-rules")!!
        assertThat(saved.phases.single { it.id == "turn" }.seconds).isEqualTo(45)
        assertThat(saved.autoAdvance).isTrue()
        assertThat(saved.shotClockSeconds).isEqualTo(45)
        assertThat(saved.timeOutsPerRound).isEqualTo(1)
        assertThat(saved.timeOutSeconds).isEqualTo(60)
        assertThat(saved.volumeKeys).isTrue()
    }

    @Test
    fun `untimed clears cues and removing a custom phase works`() = runTest {
        val vm = PresetEditViewModel("table-rules", h.repository)
        vm.uiState.first { !it.loading }
        vm.setSeconds("upgrade", null)
        assertThat(vm.uiState.value.preset!!.phases.single { it.id == "upgrade" }.cues).isEmpty()

        vm.addPhase(PhaseScope.ROUND_END)
        val custom = vm.uiState.value.preset!!.phases.last()
        vm.removePhase(custom.id)
        vm.removePhase("upgrade") // built-in kinds are disabled, not removed
        assertThat(
            vm.uiState.value.preset!!.phases.map {
                it.id
            },
        ).containsExactly("strategy", "turn", "resolve", "action", "upgrade")
    }

    @Test
    fun `invalid preset is not saved and shows issues`() = runTest {
        val vm = PresetEditViewModel(null, h.repository)
        val loaded = vm.uiState.first { !it.loading }
        assertThat(loaded.isNew).isTrue()
        // Blank, with a placeholder, so the user doesn't have to delete prefilled text.
        assertThat(loaded.preset!!.name).isEmpty()
        assertThat(loaded.showIssues).isFalse()
        // The new preset copies the selected one, Resolve step and table rules included.
        assertThat(loaded.preset!!.phases.map { it.id }).contains("resolve")
        assertThat(loaded.preset!!.revision).isEqualTo(0)

        vm.rename("  ")
        vm.save()
        val state = vm.uiState.value
        assertThat(state.showIssues).isTrue()
        assertThat(state.issues.map { it.kind }).contains(PresetIssue.Kind.BLANK_NAME)
        assertThat(h.repository.presets.first()).hasSize(4)

        vm.rename("Tournament")
        vm.navigation.test {
            vm.save()
            awaitItem()
        }
        assertThat(h.repository.presets.first().map { it.name }).contains("Tournament")
    }
}
