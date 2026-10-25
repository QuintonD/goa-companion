package dev.quinton.goa.core.game

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.json.Json
import org.junit.Test

/** ADR-039: a computer seat carries a strength 1–10; a save from before the slider loads as its level's. */
class ControllerStrengthTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a save from before the slider carries only the level and loads as that level's strength`() {
        val old = """{"heroId":"wasp","team":"blue","controller":{"type":"ai","level":"hard"}}"""
        val seat = json.decodeFromString(SeatSetup.serializer(), old)
        assertThat(seat.controller).isEqualTo(Controller.Ai(AiLevel.HARD, 6))
        val strengths = AiLevel.entries.map { level ->
            val c = """{"type":"ai","level":"${level.name.lowercase()}"}"""
            (json.decodeFromString(Controller.serializer(), c) as Controller.Ai).strength
        }
        assertThat(strengths).containsExactly(2, 4, 6, 8).inOrder()
    }

    @Test
    fun `a strength round-trips with its band`() {
        for (strength in AiStrength.MIN..AiStrength.MAX) {
            val c = Controller.Ai(strength)
            val back = json.decodeFromString(Controller.serializer(), json.encodeToString(Controller.serializer(), c))
            assertThat(back).isEqualTo(c)
            assertThat(c.level).isEqualTo(AiLevel.of(strength))
        }
        val seat = SeatSetup("wasp", Team.BLUE, Controller.Ai(9))
        val enc = Json { encodeDefaults = true }.encodeToString(SeatSetup.serializer(), seat)
        assertThat(json.decodeFromString(SeatSetup.serializer(), enc)).isEqualTo(seat)
    }

    @Test
    fun `bands are 1-3 Easy, 4-5 Normal, 6-7 Hard, 8-10 Expert, and each level sits in its own band`() {
        assertThat((1..10).map { AiLevel.of(it) }).containsExactly(
            AiLevel.EASY, AiLevel.EASY, AiLevel.EASY, AiLevel.NORMAL, AiLevel.NORMAL,
            AiLevel.HARD, AiLevel.HARD, AiLevel.EXPERT, AiLevel.EXPERT, AiLevel.EXPERT,
        ).inOrder()
        for (level in AiLevel.entries) assertThat(AiLevel.of(level.strength)).isEqualTo(level)
        // Out of range (a damaged save) is clamped, never a crash.
        assertThat(Controller.Ai(0).strength).isEqualTo(AiStrength.MIN)
        assertThat(Controller.Ai(42).strength).isEqualTo(AiStrength.MAX)
    }
}
