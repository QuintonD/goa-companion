package dev.quinton.goa.core.presentation.draft

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.model.draft.DraftMethodKind
import org.junit.Test

/** The methods list and the method editor's form, as the phone and the web share them. */
class DraftMethodsTest {
    @Test
    fun `the list shows each method's sequence for the chosen layout`() {
        val state = MethodsUiState.of(library, PreviewLayout(2, 2))
        val house = state.builtIn.first { it.method.id == "house" }
        assertThat(house.isDefault).isTrue()
        assertThat(house.preview.tokens.map { it.label })
            .containsExactly("Ban A", "Pick A", "Ban B", "Pick B", "Pick B", "Pick A").inOrder()
        assertThat(poolText(house.method.pool) + heroesText(house.preview)).isEqualTo("Pool: 15 at random · needs 6")
        val learning = MethodsUiState.of(library, PreviewLayout(4, 4)).builtIn.first { it.method.id == "learning-game" }
        assertThat(learning.preview.error).isNotNull()
        assertThat(PreviewLayout.fromKey("3v2")).isEqualTo(PreviewLayout(3, 2))
    }

    @Test
    fun `duplicating makes an editable custom copy, and saving replaces by id`() {
        val copy = library.duplicate("pick-and-ban", "x")!!
        assertThat(copy.id).isEqualTo("custom-x")
        assertThat(copy.name).isEqualTo("Pick and Ban (copy)")
        assertThat(copy.origin).isEqualTo(MethodOrigin.CUSTOM)
        assertThat(copy.kind).isEqualTo(DraftMethodKind.CUSTOM)
        assertThat(copy.sequence).isInstanceOf(SequenceRule.ByCount::class.java)
        assertThat(library.duplicate("nope", "x")).isNull()
        val saved = listOf(copy).withSaved(copy.copy(name = "Mine")).withSaved(copy.copy(id = "custom-y"))
        assertThat(saved.map { it.name }).containsExactly("Mine", "Pick and Ban (copy)").inOrder()
    }

    @Test
    fun `old custom summaries are repaired when the library is read`() {
        val old = library.duplicate("house", "x")!!.copy(summary = "Pool of 15. 2 ban(s). Custom order.")
        assertThat(MethodLibrary.of(bundledMethods, listOf(old)).custom.single().summary)
            .isEqualTo("Pool of 15. 2 bans. Custom order.")
        assertThat(MethodLibrary.decodeBundled("not json").methods).isEmpty()
    }

    @Test
    fun `the editor opens a new method, a copy of a built-in, or a custom method in place`() {
        val fresh = MethodEditUiState.forEditing(library, null, "n")
        assertThat(fresh.id).isEqualTo("custom-n")
        assertThat(fresh.error).isEqualTo("Give the method a name")
        val copy = MethodEditUiState.forEditing(library, "house", "c")
        assertThat(copy.isNew).isTrue()
        assertThat(copy.copyOf).isEqualTo("House draft")
        assertThat(copy.name).isEqualTo("House draft (copy)")
        assertThat(copy.toMethod().notes).containsExactly("Based on House draft.")
        val mine = library.duplicate("all-pick", "m")!!
        val custom = MethodEditUiState.forEditing(MethodLibrary.of(bundledMethods, listOf(mine)), mine.id, "ignored")
        assertThat(custom.isNew).isFalse()
        assertThat(custom.id).isEqualTo(mine.id)
    }

    @Test
    fun `edits are validated - picks for both teams, no bans with dealt hands`() {
        var form = MethodEditUiState.forEditing(library, null, "n").withName("Blind pairs")
        assertThat(form.error).isNull()
        form = form.removeStep(StepSection.REPEAT, 1)
        assertThat(form.error).contains("Never fills")
        form = form.addStep(StepSection.REPEAT, pick(TeamSlot.B, hidden = true))
            .toggleHidden(StepSection.REPEAT, 0)
            .moveStep(StepSection.REPEAT, 1, -1)
        assertThat((form.sequence as SequenceRule.Pattern).repeat)
            .containsExactly(pick(TeamSlot.B, hidden = true), pick(TeamSlot.A, hidden = true)).inOrder()
        assertThat(form.moveStep(StepSection.REPEAT, 5, -1)).isEqualTo(form)
        assertThat(form.removeStep(StepSection.REPEAT, 9)).isEqualTo(form)

        val dealt = form.withPoolKind(PoolKind.DEAL)
        assertThat(dealt.poolValue).isEqualTo(3)
        assertThat(dealt.error).isEqualTo("Dealt hands can't be combined with bans: a ban could empty a player's hand")
        assertThat(dealt.withPoolValue(500).poolValue).isEqualTo(MethodEditUiState.MAX_POOL)
        assertThat(form.withName("x".repeat(60)).name).hasLength(MethodEditUiState.MAX_NAME)
        assertThat(form.toMethod().summary).isEqualTo("Pool of 15. 2 bans. Custom order.")
        assertThat(form.withStepSeconds(30).toMethod().stepSeconds).isEqualTo(30)
        assertThat(form.toggleTeam(StepSection.OPENING, 0).opening.first().team).isEqualTo(TeamSlot.B)
    }

    @Test
    fun `fixed sequences only edit the pool and timer`() {
        val copy = MethodEditUiState.forEditing(library, "pick-and-ban", "c")
        assertThat(copy.addStep(StepSection.REPEAT, pick(TeamSlot.A))).isEqualTo(copy)
        assertThat(copy.withPoolKind(PoolKind.RANDOM).withPoolValue(20).toMethod().pool).isEqualTo(PoolRule.Random(20))
        assertThat(copy.toMethod().summary).isEqualTo("All eligible heroes.")
    }
}
