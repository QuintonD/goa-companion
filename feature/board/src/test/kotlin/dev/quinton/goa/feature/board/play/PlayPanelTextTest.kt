package dev.quinton.goa.feature.board.play

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardValues
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.play.game.recentSince
import dev.quinton.goa.feature.board.play.game.roundTitle
import dev.quinton.goa.feature.board.play.table.attackMissing
import dev.quinton.goa.feature.board.play.table.defenseVerdict
import dev.quinton.goa.feature.board.play.table.sourceReach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The panel's words found missing at the table (hands-on round, 2026-10-24). */
class PlayPanelTextTest {
    private val heroes = setOf("wasp", "arien", "brogan", "xargatha")

    @Test
    fun whatHappenedSinceYourLastChoiceKeepsTheDefeatAndTheRoundEnd() {
        val events = listOf(
            GameEvent.Note("Wasp uses Magnetic Dagger: hold."),
            GameEvent.Attacked("brogan", "wasp", 6, "Brogan attacks Wasp: Attack 6."),
            GameEvent.Defeated("wasp", "brogan", "Wasp is defeated by Brogan."),
            GameEvent.LifeSpent(Team.RED, 1, 3, "Red spends 1 Life counter (3 left)."),
            GameEvent.Note("Xargatha moves 2 spaces."),
            GameEvent.Note("End of round 2."),
            GameEvent.Note("Minion Battle: 6 against 6, no effect."),
            GameEvent.Note("Everyone retrieves their cards."),
            GameEvent.CoinsGained("wasp", 1, "Wasp gains 1 coin (pity coin)."),
            GameEvent.CoinsGained("arien", 1, "Arien gains 1 coin (pity coin)."),
            GameEvent.CoinsGained("brogan", 1, "Brogan gains 1 coin (pity coin)."),
            GameEvent.RoundStarted(3, "Round 3 begins."),
            GameEvent.TurnStarted(3, 1, "Turn 1 of round 3."),
        )
        val recent = recentSince(events, from = 1, heroIds = heroes)
        assertEquals(
            listOf(
                "Wasp is defeated by Brogan.",
                "Red spends 1 Life counter (3 left).",
                "Minion Battle: 6 against 6, no effect.",
                "Wasp, Arien and Brogan gain 1 coin each (pity coin).",
            ),
            recent.lines,
        )
        assertEquals(setOf(0, 1, 2), recent.strong)
    }

    @Test
    fun aQuietStretchShowsTheLatestLinesWithoutHeadings() {
        val events = listOf(
            GameEvent.TurnStarted(1, 2, "Turn 2 of round 1."),
            GameEvent.Note("Wasp plays a card facedown."),
            GameEvent.Note("Cards revealed: Wasp Shock (initiative 8)."),
            GameEvent.Note("Wasp uses Shock: movement."),
        )
        val recent = recentSince(events, from = 0, heroIds = heroes)
        assertEquals(listOf("Cards revealed: Wasp Shock (initiative 8).", "Wasp uses Shock: movement."), recent.lines)
        assertEquals(emptySet<Int>(), recent.strong)
    }

    @Test
    fun theTitleShortensToTheRoomItHas() {
        assertEquals("Round 2 · Turn 3", roundTitle(2, 3, room = 411f))
        assertEquals("R2 · Turn 3", roundTitle(2, 3, room = 384f))
        assertEquals("R2 · T3", roundTitle(2, 3, room = 384f / 1.3f))
    }

    @Test
    fun aBlockTheEngineSettlesDecidesTheVerdict() {
        // Stop Projectiles against a melee attack: no Defense value, the Block does not apply (p.14).
        assertEquals(false, defenseVerdict(total = null, blocks = false, attack = 6))
        assertEquals(true, defenseVerdict(total = null, blocks = true, attack = 9))
        assertEquals(false, defenseVerdict(total = 5, blocks = null, attack = 6))
        assertNull(defenseVerdict(total = null, blocks = null, attack = 6))
    }

    @Test
    fun countersReadAsWords() {
        assertEquals("Spells removed", PlayText.counter("spellsRemoved"))
        assertEquals("Spellbook", PlayText.counter("spellbook"))
        assertEquals("Wish casts", PlayText.counter("wishCasts"))
    }

    @Test
    fun aMissingAttackIsExplainedAndTheAskingCardsReachIsSaid() {
        val dagger = Card(
            id = "wasp-gold",
            heroId = "wasp",
            name = "Magnetic Dagger",
            color = CardColor.GOLD,
            primary = PrimaryAction.ATTACK,
            values = CardValues(attack = 3, movement = 1, radius = 3),
        )
        val noAttack = listOf(Choice.Action(ActionType.MOVEMENT), Choice.Action(ActionType.HOLD))
        assertEquals(true, attackMissing(dagger, noAttack))
        assertEquals(false, attackMissing(dagger, noAttack + Choice.Action(ActionType.PRIMARY)))
        assertEquals(false, attackMissing(dagger.copy(values = CardValues(movement = 2)), noAttack))
        assertEquals("Radius 3", sourceReach("Move a unit, or a token, in radius 1 space", dagger))
        assertNull(sourceReach("Move up to 4 spaces", dagger))
    }
}
