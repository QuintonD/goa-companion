package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * The determinized copy can offer an option the real question does not (a space that depends on a
 * hidden card). Whatever the copy offers, the answer is one of the real question's options: the
 * real question here lacks the answer the player would give, so a player that answered from its
 * copy would answer outside it.
 */
class HeuristicLegalityTest {
    @Test
    fun `the answer comes from the real question, not the sampled copy`() {
        var checked = 0
        for ((i, lineup) in AiHarness.mixedLineups(2).withIndex()) {
            val (red, blue) = lineup
            for (strength in listOf(3, 6, 10)) {
                AiHarness.play(
                    AiHarness.setup(red, blue, AiLevel.of(strength), AiLevel.of(strength), Team.RED),
                    mapOf(Team.RED to HeuristicAi(strength, 11L + i), Team.BLUE to HeuristicAi(strength, 13L + i)),
                    maxDecisions = 160,
                    observe = { s, d ->
                        if (d.options.size >= 2) {
                            val ai = HeuristicAi(strength, 5L)
                            val usual = ai.choose(s, d)
                            val narrowed = d.copy(options = d.options - usual)
                            assertThat(narrowed.options).contains(ai.choose(s, narrowed))
                            checked++
                        }
                    },
                )
            }
        }
        assertThat(checked).isGreaterThan(100)
    }
}
