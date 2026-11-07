package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.feature.board.BoardText

/*
 * Who acts and who answers (UI hill climb, round 3): while a hero resolves its card, another hero may have to answer
 * it (defend against its attack, discard a card it asks for). The turn strip then says RESOLVING on the hero whose
 * card it is and ANSWERS (DEFENDS) on the one asked, and the panel's strip names the one asked against the one
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
 * Whose decision it is, as the panel's strip says it: "Wasp decides · Space"; asked during another hero's resolution,
 * "Tigerclaw defends against Arien" or "Wasp answers Tigerclaw · Card"; a team's question, "Red decides · …".
 */
internal fun decisionOwnerLine(table: TableUi): String? {
    val decision = table.decision ?: return null
    val name = table.decider?.let { table.hero(it.heroId).name } ?: BoardText.team(decision.team)
    val acting = responderId(table)?.let { table.track?.acting?.heroId }
        ?: return "$name decides · ${kindTitle(decision.kind)}"
    return if (decision.kind == DecisionKind.DEFEND) {
        val attacker = decision.context.attackerId?.let(table::pieceName) ?: table.hero(acting).name
        "$name defends against $attacker"
    } else {
        "$name answers ${table.hero(acting).name} · ${kindTitle(decision.kind)}"
    }
}
