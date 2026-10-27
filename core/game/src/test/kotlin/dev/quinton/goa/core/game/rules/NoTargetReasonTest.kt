package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.engine.noTargetReason
import org.junit.Test

/**
 * Why an attack can't start, in the card's words (hands-on re-check: Fireball said only "no target"
 * while units were in range, next to Gydion or to a friendly unit). The action panel shows it as
 * "Its Attack can't start: …" (p.10 "Picking Targets", FAQ).
 */
class NoTargetReasonTest {
    @Test
    fun `a target prompt gives its own condition`() {
        assertThat(noTargetReason("Target a hero adjacent to you")).isEqualTo("no hero adjacent to you")
        assertThat(noTargetReason("Target a unit in range of both you and the Turret"))
            .isEqualTo("no unit in range of both you and the Turret")
        assertThat(noTargetReason("Target an enemy unit adjacent to you")).isEqualTo("no enemy unit adjacent to you")
        assertThat(noTargetReason("Target that hero")).isEqualTo("that hero can't be targeted")
        assertThat(noTargetReason("Attack a unit adjacent to you")).isEqualTo("no unit adjacent to you")
        assertThat(noTargetReason("Choose one")).isEqualTo("no target")
    }

    @Test
    fun `a Choose one attack names every bullet's condition`() {
        assertThat(
            noTargetReason(listOf("Target a hero in range with a Bounty marker", "Target a unit adjacent to you")),
        ).isEqualTo("no hero in range with a Bounty marker and no unit adjacent to you")
    }
}
