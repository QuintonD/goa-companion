package dev.quinton.goa.feature.board.play.game

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.match.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What not defending comes to is read from the engine's events, and only the defeat's own (sol #7). */
class DefeatPreviewTest {
    private val defend = Decision(
        id = 7,
        seat = "tigerclaw",
        team = Team.BLUE,
        kind = DecisionKind.DEFEND,
        prompt = "Arien attacks you",
        options = listOf(Choice.Card("dodge"), Choice.Decline("Don't defend")),
    )

    @Test
    fun theDefeatsLifeCountersAndCoinsAreRead() {
        val events = listOf(
            GameEvent.Defeated("tigerclaw", "arien", "Tigerclaw is defeated by Arien."),
            GameEvent.Note("Tigerclaw's Hit and Run is resolved without an action."),
            GameEvent.CoinsGained("arien", 2, "Arien gains 2 coins."),
            GameEvent.CoinsGained("brogan", 1, "Brogan gains 1 coin."),
            GameEvent.LifeSpent(Team.BLUE, 1, 1, "Blue spends 1 Life counter (1 left)."),
            // The attack card goes on: not the defeat's.
            GameEvent.Moved("arien", emptyList(), "Arien moves 1 space."),
            GameEvent.CoinsGained("arien", 1, "Arien gains 1 coin."),
        )
        val preview = defeatPreviewOf(defend, events)!!
        assertEquals(7, preview.decisionId)
        assertEquals("arien", preview.byHero)
        assertEquals(Team.BLUE, preview.team)
        assertEquals(1, preview.lifeSpent)
        assertEquals(1, preview.lifeLeft)
        assertEquals(listOf("arien" to 2, "brogan" to 1), preview.coins)
        assertNull(preview.winner)
    }

    @Test
    fun theLastLifeCounterEndsTheGame() {
        val events = listOf(
            GameEvent.Defeated("tigerclaw", "arien", "Tigerclaw is defeated by Arien."),
            GameEvent.LifeSpent(Team.BLUE, 1, 0, "Blue spends 1 Life counter (0 left)."),
            GameEvent.GameOver(Team.RED, WinReason.LIFE_COUNTERS, "Red wins."),
        )
        assertEquals(Team.RED, defeatPreviewOf(defend, events)?.winner)
    }

    @Test
    fun noDefeatNoPreview() {
        assertNull(defeatPreviewOf(defend, listOf(GameEvent.Note("Something prevents the defeat."))))
    }
}
