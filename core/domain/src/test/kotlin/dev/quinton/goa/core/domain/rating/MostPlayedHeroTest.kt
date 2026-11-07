package dev.quinton.goa.core.domain.rating

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** A player's face is their most-played hero, derived from the log (most games, then wins, then hero id). */
class MostPlayedHeroTest {
    @Test
    fun `most games wins, then most wins, then hero id`() {
        val result = EloCalculator.replay(
            listOf(
                match("m1", 1, Team.RED, red("p1", "h2"), blue("p2", "h1")),
                match("m2", 2, Team.BLUE, red("p1", "h2"), blue("p2", "h3")),
                match("m3", 3, Team.RED, red("p1", "h1"), blue("p2", "h4")),
                match("m4", 4, Team.BLUE, red("p3", "h5"), blue("p2", "h6")),
            ),
        )
        val faces = result.mostPlayedHeroes()

        // p1: h2 twice beats h1 once.
        assertThat(faces["p1"]).isEqualTo("h2")
        // p2: four heroes once each; h3 and h6 were wins, so the lower id of those.
        assertThat(faces["p2"]).isEqualTo("h3")
        assertThat(faces["p3"]).isEqualTo("h5")
        assertThat(faces).doesNotContainKey("nobody")
    }

    @Test
    fun `an empty log gives no faces`() {
        assertThat(RatingsResult.EMPTY.mostPlayedHeroes()).isEmpty()
    }
}
