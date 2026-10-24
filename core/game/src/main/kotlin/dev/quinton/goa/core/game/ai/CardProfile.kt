package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.core.model.catalog.Stat
import java.util.concurrent.ConcurrentHashMap

/**
 * What the AI's heuristics need to know about a card, read once from its printed values and text
 * (AI.md "Evaluation"). Generic for every hero: nothing here names a hero. The engine still resolves
 * the real text; these numbers only steer the cheap heuristics (threat maps, rollout policy, upgrade
 * ratings). Where the text is too special to read, the profile errs on "plain card".
 */
internal class CardProfile(card: Card) {
    val id: String = card.id
    val color: CardColor = card.color
    val tier: Int? = card.tier
    val initiative: Int = card.initiative ?: 0
    val attackCard: Boolean = card.primary == PrimaryAction.ATTACK
    val skillCard: Boolean = card.primary == PrimaryAction.SKILL || card.primary == PrimaryAction.DEFENSE_SKILL
    val attack: Int = card.values.attack ?: 0
    val defense: Int? = card.values.defense
    val movement: Int = card.values.movement ?: 0
    val range: Int = card.values.range ?: 0
    val radius: Int = card.values.radius ?: 0
    val ranged: Boolean = card.ranged
    val block: Boolean = card.block
    val hasMovement: Boolean = card.primary == PrimaryAction.MOVEMENT || SecondaryAction.MOVEMENT in card.secondary
    val canDefend: Boolean = card.primary == PrimaryAction.DEFENSE || card.primary == PrimaryAction.DEFENSE_SKILL ||
        SecondaryAction.DEFENSE in card.secondary
    val basic: Boolean = card.tier == null || card.color == CardColor.GOLD || card.color == CardColor.SILVER
    val item: Stat? = card.item

    private val text: String = card.text.lowercase()

    /** Spaces the card's own text moves its hero before it targets ("Before the attack: Move 2 or 3 spaces"). */
    val premove: Int = premoveOf(text)

    /** "… discards a card, or is defeated": lethal against an empty hand whatever the values. */
    val discardOrDefeat: Boolean = "discards a card, or is defeated" in text || "discard a card, or be defeated" in text

    /** "… discards a card, if able" / "… discards a card": costs the victim a card. */
    val forcesDiscard: Boolean = discardOrDefeat || "discards a card" in text

    /** Block cards: which attacks they answer (null = only the engine knows). */
    val blocksRanged: Boolean = block && "block a ranged attack" in text
    val blocksMelee: Boolean = block && ("block a non-ranged attack" in text || "block a melee attack" in text)

    /** "Ignore all minion defense modifiers" on a defense card. */
    val ignoresMinionModifiers: Boolean = "ignore all minion defense modifiers" in text

    /** Spaces from its hero at which this card can hit a unit with its primary action (no other movement). */
    val reach: Int = when {
        attackCard -> premove + if (ranged) maxOf(range, 1) else 1
        discardOrDefeat || forcesDiscard -> premove + maxOf(range, radius, 1)
        else -> 0
    }

    /** A rough worth of having this card for the rest of a round (upgrade choice, discards). */
    val rating: Double = rate()

    private fun rate(): Double {
        var r = 0.0
        r += (defense ?: 0) * 0.35
        if (block) r += 1.6
        if (attackCard) r += attack * 0.55 + (if (ranged) range * 0.35 else 0.0) + premove * 0.4
        if (skillCard) r += 1.5
        r += movement * 0.25
        r += initiative * 0.05
        if (discardOrDefeat) {
            r += 1.5
        } else if (forcesDiscard) {
            r += 0.8
        }
        return r
    }

    companion object {
        private val cache = ConcurrentHashMap<String, CardProfile>()

        fun of(card: Card): CardProfile = cache.getOrPut(card.id) { CardProfile(card) }

        private val selfMove = Regex(
            "^(?:if [^,:]*, )?(?:you may )?move (?:up to )?((?:\\d+(?:, | or |, or ))*\\d+) spaces?",
        )
        private val number = Regex("\\d+")

        private fun premoveOf(text: String): Int {
            val at = text.indexOf("before the attack:")
            if (at < 0) return 0
            val clause = text.substring(at + "before the attack:".length).trim()
            val m = selfMove.find(clause) ?: return 0
            return number.findAll(m.groupValues[1]).maxOfOrNull { it.value.toInt() } ?: 0
        }
    }
}
