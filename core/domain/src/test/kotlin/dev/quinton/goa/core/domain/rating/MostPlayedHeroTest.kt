package dev.quinton.goa.core.domain.rating

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** A player's hero badge is their most-played hero, derived from the log (most games, then wins, then hero id). */
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
    fun `two players may share a most-played hero`() {
        val result = EloCalculator.replay(
            listOf(
                // p1 plays h1 twice and h2 once; p2 plays h1 twice too, then h3.
                match("m1", 1, Team.RED, red("p1", "h1"), blue("p2", "h3")),
                match("m2", 2, Team.RED, red("p1", "h1"), blue("p2", "h1")),
                match("m3", 3, Team.RED, red("p1", "h2"), blue("p2", "h1")),
            ),
        )
        val faces = result.mostPlayedHeroes()

        // The badge tells what each plays most, so both show h1: the crest tells them apart.
        assertThat(faces["p1"]).isEqualTo("h1")
        assertThat(faces["p2"]).isEqualTo("h1")
    }

    @Test
    fun `an empty log gives no faces`() {
        assertThat(RatingsResult.EMPTY.mostPlayedHeroes()).isEmpty()
    }
}
