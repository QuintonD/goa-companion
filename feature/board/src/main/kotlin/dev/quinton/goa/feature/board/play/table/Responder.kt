package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.feature.board.BoardText

/*
 * Who acts and who answers (UI hill climb, round 3): while a hero resolves its card, another hero may have to answer
 * it (defend against its attack, discard a card it asks for). The turn strip then says RESOLVING on the hero whose
 * card it is and ANSWERS (DEFENDS) on the one asked, and the panel's header names the one asked against the one
 * resolving, so "who acts now?" never has two answers.
 */

/**
 * The hero asked a question during another hero's resolution (a defence, a discard), or null: the deciding hero when
 * it isn't the hero whose card is resolving (the turn strip's NOW), outside the resolve phase never.
 */
internal fun responderId(table: TableUi): String? {
    val seat = table.decision?.seat ?: return null
    val acting = table.track?.acting?.heroId ?: return null
    return seat.takeIf { it != acting && table.heroView(it) != null }
}

/** The word on a responder's band on the turn strip: DEFENDS for a defence, else ANSWERS. */
internal fun responderBand(table: TableUi): String =
    if (table.decision?.kind == DecisionKind.DEFEND) "DEFENDS" else "ANSWERS"

/**
 * Whose decision it is, the header's first line (UI hill climb, round 4): "You choose · Wasp" when it is for the
 * person at this phone; "Computer · Wasp acting" when the computer decides for that hero; online, the player's name
 * ("Eve · Wasp acting"); a puzzle's other side, "The other side · Wasp acting"; a team's question, "You choose · Red".
 */
internal fun decisionOwner(table: TableUi): String? {
    val decision = table.decision ?: return null
    val hero = table.decider
    val name = hero?.let { table.hero(it.heroId).name } ?: BoardText.team(decision.team)
    if (table.asking) return "You choose · $name"
    if (hero == null) return "$name is choosing"
    val who = when {
        hero.heroId in table.players -> table.players.getValue(hero.heroId)
        hero.controller is Controller.Ai -> "Computer"
        else -> table.controllerLabel(hero)
    }
    return "$who · $name acting"
}

/**
 * What the decision asks, the header's second line, short and imperative for the person ("Choose a space", "Defend
 * against Arien", "Answer Tigerclaw · Discard a card"); a short prompt is said as the engine words it ("Discard a
 * card", [shortPrompt]), and the panel then doesn't repeat it on a plate. While someone
 * else decides, what they are doing ("Choosing an action").
 */
internal fun decisionDirective(table: TableUi): String? {
    val decision = table.decision ?: return null
    val asking = table.asking
    if (decision.kind == DecisionKind.DEFEND) {
        val attacker = decision.context.attackerId?.let(table::pieceName)
            ?: table.track?.acting?.heroId?.let { table.hero(it).name }
        val verb = if (asking) "Defend" else "Defending"
        return attacker?.let { "$verb against $it" } ?: verb
    }
    // An action, a respawn, a card to play or to level up: the panel itself says it (chips, the card's box, the cards).
    val worded = decision.kind !in setOf(
        DecisionKind.CHOOSE_ACTION,
        DecisionKind.RESPAWN,
        DecisionKind.PLAY_CARD,
        DecisionKind.UPGRADE_CARD,
        DecisionKind.KEEP_UPGRADE,
    )
    val own = when {
        !asking -> doingWords(decision.kind)
        worded -> shortPrompt(decision.prompt) ?: askWords(decision.kind)
        else -> askWords(decision.kind)
    }
    val acting = responderId(table)?.let { table.track?.acting?.heroId } ?: return own
    return "${if (asking) "Answer" else "Answering"} ${table.hero(acting).name} · $own"
}

/** A prompt short enough to be the header's instruction as the engine words it ("Discard a card"), or null. */
internal fun shortPrompt(prompt: String): String? {
    val parts = promptParts(prompt)
    return parts.title?.takeIf { parts.text == null }
}

private fun askWords(kind: DecisionKind): String = when (kind) {
    DecisionKind.PLAY_CARD -> "Play a card"
    DecisionKind.ACT_FIRST -> "Choose who acts first"
    DecisionKind.RESPAWN -> "Choose where to respawn"
    DecisionKind.CHOOSE_ACTION -> "Choose an action"
    DecisionKind.CHOOSE_SPACE -> "Choose a space"
    DecisionKind.CHOOSE_UNIT -> "Choose a unit"
    DecisionKind.CHOOSE_TOKEN -> "Choose a token"
    DecisionKind.CHOOSE_CARD -> "Choose a card"
    DecisionKind.DEFEND -> "Defend"
    DecisionKind.CHOOSE_OPTION -> "Choose one"
    DecisionKind.CONFIRM -> "Yes or no"
    DecisionKind.CHOOSE_NUMBER -> "Choose a number"
    DecisionKind.REMOVE_MINION -> "Minion Battle · remove a minion"
    DecisionKind.MINION_RETURN -> "Minion back"
    DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE -> "Level up"
}

private fun doingWords(kind: DecisionKind): String = when (kind) {
    DecisionKind.PLAY_CARD -> "Choosing a card"
    DecisionKind.ACT_FIRST -> "Choosing who acts first"
    DecisionKind.RESPAWN -> "Respawning"
    DecisionKind.CHOOSE_ACTION -> "Choosing an action"
    DecisionKind.CHOOSE_SPACE -> "Choosing a space"
    DecisionKind.CHOOSE_UNIT -> "Choosing a unit"
    DecisionKind.CHOOSE_TOKEN -> "Choosing a token"
    DecisionKind.CHOOSE_CARD -> "Choosing a card"
    DecisionKind.DEFEND -> "Defending"
    DecisionKind.CHOOSE_OPTION -> "Choosing"
    DecisionKind.CONFIRM -> "Deciding"
    DecisionKind.CHOOSE_NUMBER -> "Choosing a number"
    DecisionKind.REMOVE_MINION -> "Minion Battle · removing a minion"
    DecisionKind.MINION_RETURN -> "Minion back"
    DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE -> "Levelling up"
}

/** The header as TalkBack hears it: "You choose · Wasp. Choose a space". */
internal fun decisionHeaderSpoken(table: TableUi): String? = decisionOwner(table)?.let { owner ->
    listOfNotNull(owner, decisionDirective(table)).joinToString(". ")
}
