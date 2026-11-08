package dev.quinton.goa.core.domain.draft

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.board.DraftBoardText
import dev.quinton.goa.feature.draft.ui.steps
import dev.quinton.goa.feature.draft.ui.tokens
import org.junit.Test

/** The draft's one step numbering: runs of one side's bans or picks, counted (the board and the method list). */
class DraftStepsTest {
    private val house = DraftFixtures.method("house")

    private fun session() = DraftEngine.start(DraftFixtures.setup(house, teamA = Team.RED)).getOrThrow()

    private fun DraftSession.choose(): DraftSession {
        val free = pool.first { id -> history.none { it.heroId == id } }
        return DraftEngine.apply(this, DraftCommand(free)).getOrThrow()
    }

    @Test
    fun `house draft 2v2 is five steps with their counts`() {
        val steps = session().let { DraftBoardText.steps(it) }
        assertThat(steps.map { it.label() }).containsExactly(
            "1 · ◆ Red bans 1",
            "2 · ◆ Red picks 1",
            "3 · ● Blue bans 1",
            "4 · ● Blue picks 2",
            "5 · ◆ Red picks 1",
        ).inOrder()
        assertThat(steps[3].first).isEqualTo(3)
        assertThat(4 in steps[3]).isTrue()
        assertThat(steps[3].spoken).isEqualTo("Step 4, Blue picks 2")
    }

    @Test
    fun `before the coin the steps say A and B, and the method list can drop the names`() {
        val plan = DraftPlanner.plan(house, 2, 2).getOrThrow()
        val steps = plan.tokens().steps()
        assertThat(steps.first().label()).isEqualTo("1 · A bans 1")
        val coloured = plan.tokens(Team.BLUE).steps()
        assertThat(coloured[3].label(named = false)).isEqualTo("4 · ◆ picks 2")
    }

    @Test
    fun `the turn line says whose pick within the step`() {
        var s = session()
        val name: (String?) -> String = { id -> id?.substringAfter("p-") ?: "?" }
        assertThat(DraftBoardText.turnLine(s, name)).isEqualTo("Step 1 of 5")
        s = s.choose()
        assertThat(DraftBoardText.turnLine(s, name)).isEqualTo("red-0's pick")
        s = s.choose().choose()
        assertThat(DraftBoardText.turnLine(s, name)).isEqualTo("blue-0: pick 1 of 2")
        s = s.choose()
        assertThat(DraftBoardText.turnLine(s, name)).isEqualTo("blue-1: pick 2 of 2")
    }
}
