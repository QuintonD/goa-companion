package dev.quinton.goa.core.game.puzzles

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.KnownPlay
import dev.quinton.goa.core.game.engine.GameState

/**
 * A full-round puzzle's convention: "you know what they will play" (ENCODING.md "Full rounds", ADR-052).
 *
 * Each enemy hero's card for each turn is listed, so those card plays are not choices. For the turns in
 * [actions], what the hero does with its card is known too: its answers in order, as long as they stay
 * possible. Everything else stays adversarial: the free turns, defenses and other questions asked while
 * someone else resolves a card, and any known answer the player has made impossible. A listed card that
 * has left the enemy's hand (spent on a defense, discarded by a card) is simply not offered: that enemy
 * then plays anything it has left. Falling back to a free choice only makes the puzzle harder for the
 * player, so a proof stays sound.
 */
data class KnownPlays(
    /** Hero id → the card for turns 1, 2, 3, 4. */
    val cards: Map<String, List<String>>,
    /** Hero id → turn → its answers while resolving that turn's card; a turn not listed is free. */
    val actions: Map<String, Map<Int, List<Choice>>> = emptyMap(),
) {
    /**
     * The option index fixed at [d], or null when the enemy chooses freely there. [state] is the game at
     * the decision, [stepAnswers] the answers given so far in the current step.
     */
    fun optionFor(d: Decision, state: GameState, stepAnswers: List<Choice>): Int? {
        if (d.kind == DecisionKind.PLAY_CARD) {
            // A second card ("Play another card?", which can be declined) is the enemy's own choice.
            if (d.options.any { it is Choice.Decline }) return null
            val card = cards[d.seat]?.getOrNull(state.turn - 1) ?: return null
            return d.options.indexOf(Choice.Card(card)).takeIf { it >= 0 }
        }
        if (state.acting != d.seat || d.kind == DecisionKind.DEFEND) return null
        val script = actions[d.seat]?.get(state.turn) ?: return null
        // The next known answer: the script advances over the answers it recognises in this step.
        var next = 0
        for (a in stepAnswers) if (next < script.size && a == script[next]) next++
        val want = script.getOrNull(next) ?: return null
        return d.options.indexOf(want).takeIf { it >= 0 }
    }

    /** What the player is shown: every listed card; [plans] (hero → text per known turn) say what it does. */
    fun display(plans: Map<String, Map<Int, String>>): List<KnownPlay> = cards.flatMap { (hero, list) ->
        list.mapIndexed { i, card ->
            val answers = actions[hero]?.get(i + 1).orEmpty()
            KnownPlay(hero, i + 1, card, plans[hero]?.get(i + 1), answers.filterIsInstance<Choice.Space>().map { it.hex })
        }
    }
}
