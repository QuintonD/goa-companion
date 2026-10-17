package dev.quinton.goa.feature.draft

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.feature.draft.methods.DraftMethodEditViewModel
import dev.quinton.goa.feature.draft.methods.DraftMethodsViewModel
import dev.quinton.goa.feature.draft.methods.MethodEditEvent
import dev.quinton.goa.feature.draft.methods.MethodsEvent
import dev.quinton.goa.feature.draft.methods.PoolKind
import dev.quinton.goa.feature.draft.methods.PreviewLayout
import dev.quinton.goa.feature.draft.methods.StepSection
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DraftMethodsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val drafts = FakeDraftRepository()

    @Test
    fun `lists built-in methods with previews for the chosen layout`() = runTest {
        val vm = DraftMethodsViewModel(drafts, FakeDraftEnvironment())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val house = vm.uiState.value.builtIn.first { it.method.id == "house" }
        assertThat(house.isDefault).isTrue()
        assertThat(house.preview.tokens.map { it.label })
            .containsExactly("Ban A", "Pick A", "Ban B", "Pick B", "Pick B", "Pick A").inOrder()
        vm.setLayout(PreviewLayout(3, 3))
        assertThat(vm.uiState.value.builtIn.first { it.method.id == "house" }.preview.tokens).hasSize(8)
        val learning = vm.uiState.value.builtIn.first { it.method.id == "learning-game" }
        vm.setLayout(PreviewLayout(4, 4))
        assertThat(vm.uiState.value.builtIn.first { it.method.id == learning.method.id }.preview.error).isNotNull()
    }

    @Test
    fun `duplicate saves an editable custom copy and opens it`() = runTest {
        val vm = DraftMethodsViewModel(drafts, FakeDraftEnvironment())
        vm.methodEvents.test {
            vm.duplicate("pick-and-ban")
            val event = awaitItem() as MethodsEvent.Edit
            val copy = drafts.customState.value.single()
            assertThat(copy.id).isEqualTo(event.methodId)
            assertThat(copy.origin).isEqualTo(MethodOrigin.CUSTOM)
            assertThat(copy.kind).isEqualTo(DraftMethodKind.CUSTOM)
            assertThat(copy.sequence).isInstanceOf(SequenceRule.ByCount::class.java)
        }
        vm.delete(drafts.customState.value.single().id)
        assertThat(drafts.customState.value).isEmpty()
    }

    @Test
    fun `editing a built-in creates a copy and never changes the original`() = runTest {
        val vm = DraftMethodEditViewModel("house", drafts, FakeDraftEnvironment())
        val state = vm.uiState.value
        assertThat(state.isNew).isTrue()
        assertThat(state.copyOf).isEqualTo("House draft")
        assertThat(state.poolKind).isEqualTo(PoolKind.RANDOM)
        assertThat(state.poolValue).isEqualTo(15)
        vm.editEvents.test {
            vm.setPoolValue(12)
            vm.save()
            assertThat(awaitItem()).isEqualTo(MethodEditEvent.Saved)
        }
        val saved = drafts.customState.value.single()
        assertThat(saved.id).isNotEqualTo("house")
        assertThat(saved.pool).isEqualTo(PoolRule.Random(12))
    }

    @Test
    fun `new methods are validated before saving`() = runTest {
        val vm = DraftMethodEditViewModel(null, drafts, FakeDraftEnvironment())
        assertThat(vm.uiState.value.error).isEqualTo("Give the method a name")
        vm.setName("Blind pairs")
        assertThat(vm.uiState.value.error).isNull()
        vm.removeStep(StepSection.REPEAT, 1) // only Team A picks remain → Team B can never fill
        assertThat(vm.uiState.value.error).contains("Never fills")
        vm.addStep(StepSection.REPEAT, pick(TeamSlot.B, hidden = true))
        vm.toggleHidden(StepSection.REPEAT, 0)
        vm.moveStep(StepSection.REPEAT, 1, -1)
        val repeat = (vm.uiState.value.sequence as SequenceRule.Pattern).repeat
        assertThat(repeat).containsExactly(pick(TeamSlot.B, hidden = true), pick(TeamSlot.A, hidden = true)).inOrder()
        vm.save()
        assertThat(drafts.customState.value.single().name).isEqualTo("Blind pairs")
    }

    @Test
    fun `editing a custom method updates it in place`() = runTest {
        val existing = bundledMethods.methods.first { it.id == "all-pick" }
            .copy(id = "mine", name = "Mine", origin = MethodOrigin.CUSTOM, kind = DraftMethodKind.CUSTOM)
        val repo = FakeDraftRepository(custom = listOf(existing))
        val vm = DraftMethodEditViewModel("mine", repo, FakeDraftEnvironment())
        assertThat(vm.uiState.value.isNew).isFalse()
        vm.setName("Mine v2")
        vm.save()
        assertThat(repo.customState.value.single().name).isEqualTo("Mine v2")
        assertThat(repo.customState.value.single().id).isEqualTo("mine")
    }
}
