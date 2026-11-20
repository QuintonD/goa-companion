package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.ai.CardProfile
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat

/**
 * The printed numbers a playbook may read for one card ([CardProfile], from the catalog).
 * Strategy only: the engine still resolves the text. A future hero is a plain card here.
 */
data class HCard(
    val id: String,
    val color: CardColor,
    val tier: Int?,
    val initiative: Int,
    val attack: Int,
    val defense: Int?,
    val movement: Int,
    val range: Int,
    val radius: Int,
    val ranged: Boolean,
    val block: Boolean,
    val attackCard: Boolean,
    val skillCard: Boolean,
    val hasMovement: Boolean,
    val canDefend: Boolean,
    val forcesDiscard: Boolean,
    val discardOrDefeat: Boolean,
    /** Spaces at which the primary can hit, including a printed "Before the attack: Move". */
    val reach: Int,
    val premove: Int,
    val blocksRanged: Boolean,
    val blocksMelee: Boolean,
    val ignoresMinionModifiers: Boolean,
    /** Rough worth of keeping the card for the rest of the round (discards, upgrades). */
    val rating: Double,
    /** The item icon this card grants when tucked; null on a basic card. */
    val item: Stat?,
) {
    /** An attack, a forced discard, or any primary that reaches a unit. */
    val threatens: Boolean get() = attackCard || forcesDiscard || reach > 0

    companion object {
        internal fun from(p: CardProfile): HCard = HCard(
            id = p.id,
            color = p.color,
            tier = p.tier,
            initiative = p.initiative,
            attack = p.attack,
            defense = p.defense,
            movement = p.movement,
            range = p.range,
            radius = p.radius,
            ranged = p.ranged,
            block = p.block,
            attackCard = p.attackCard,
            skillCard = p.skillCard,
            hasMovement = p.hasMovement,
            canDefend = p.canDefend,
            forcesDiscard = p.forcesDiscard,
            discardOrDefeat = p.discardOrDefeat,
            reach = p.reach,
            premove = p.premove,
            blocksRanged = p.blocksRanged,
            blocksMelee = p.blocksMelee,
            ignoresMinionModifiers = p.ignoresMinionModifiers,
            rating = p.rating,
            item = p.item,
        )
    }
}

/**
 * One card an enemy might still hold, from public facts (level, faceup cards, item icons).
 * [weight] is that colour's share: the cards of one colour sum to about 1, because a hero
 * keeps one card of each colour in the round. [playingNow] is the revealed card of this turn.
 */
data class PossibleCard(
    val card: HCard,
    val weight: Double,
    val playingNow: Boolean,
)

/**
 * An enemy card that can reach a hero. [weight] is the chance it is the card they use, not a
 * certainty; after the reveal a played attack is still only a chance they aim it here.
 */
data class Threat(
    val enemyId: String,
    val card: HCard,
    val attackTotal: Int,
    val reach: Int,
    val weight: Double,
    val thisTurn: Boolean,
)
