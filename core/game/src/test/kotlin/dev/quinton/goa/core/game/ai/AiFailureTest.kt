package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.TestData
import org.junit.Test

/** A failure inside a search (a sampled position real play never reaches) must not end the game. */
class AiFailureTest {
    @Test
    fun `a search that fails answers with the fallback's option`() {
        val session = TestData.newGame()
        val decision = session.pending!!
        var asked = 0
        val choice = AiPlayers.forApp(MOST_SEARCHING).choose(session, decision) {
            asked++
            error("A rules failure inside a playout")
        }
        assertThat(asked).isAtLeast(1)
        assertThat(choice).isEqualTo(Fallback.choose(decision))
    }
}

private const val MOST_SEARCHING = 10
