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
 * What the decision asks, short and imperative for the person ("Choose an action", "Who acts first?", "Defend against
 * Arien", "Answer Tigerclaw · Discard a card"), or, while someone else decides, what they are doing ("Choosing an
 * action"). The panel says it once, in its head ([decisionHead]); nothing under the head repeats it (show, don't tell).
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
    val own = if (asking) askPrompt(decision.kind, decision.prompt) else doingWords(decision.kind)
    val acting = responderId(table)?.let { table.track?.acting?.heroId } ?: return own
    return listOfNotNull("${if (asking) "Answer" else "Answering"} ${table.hero(acting).name}", own).joinToString(" · ")
}

/**
 * The person's question in the fewest words that still say it: the engine's prompt without a title the panel needs
 * no more ("Tied initiative: who acts first?" is "Who acts first?": the track shows the tie), an action question as
 * "Choose an action" (the card is beside its chips).
 */
internal fun askPrompt(kind: DecisionKind, prompt: String): String {
    if (kind == DecisionKind.CHOOSE_ACTION) return askWords(kind)
    // "Take one card into your hand (the other becomes an item)": the aside is the rule, in the head's rules (p.15).
    if (kind == DecisionKind.KEEP_UPGRADE) return prompt.substringBefore(" (").trim()
    val parts = promptParts(prompt)
    // "Choose one —" introduces the card's options: the dash points at the buttons under it.
    return (parts.text?.takeIf { parts.title != null } ?: prompt).trimEnd(' ', '—')
}

/**
 * The panel's one head line (2026-11-08, show, don't tell): the person's question ([decisionDirective]); while the
 * computer, another player or the other side decides, who and what ("Computer · choosing an action"). The portrait
 * beside it says which hero; the name is on the turn track. TalkBack hears [decisionHeaderSpoken].
 */
internal fun decisionHead(table: TableUi): String? {
    val decision = table.decision ?: return null
    val directive = decisionDirective(table)
    if (table.asking) return directive
    val hero = table.decider ?: return "${BoardText.team(decision.team)} is choosing"
    val who = when {
        hero.heroId in table.players -> table.players.getValue(hero.heroId)
        hero.controller is Controller.Ai -> "Computer"
        else -> table.controllerLabel(hero)
    }
    return listOfNotNull(who, directive?.replaceFirstChar { it.lowercase() }).joinToString(" · ")
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
    DecisionKind.REMOVE_MINION -> "Minion Battle"
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
