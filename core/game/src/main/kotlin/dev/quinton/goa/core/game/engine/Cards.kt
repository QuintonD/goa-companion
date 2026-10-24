package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.effects.Replacement
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.PrimaryAction

/*
 * Card states and how they change (p.10 "Card States", "Changing Card State", "Facedown").
 */

/**
 * Put a card of [heroId] into [zone] (slot for [Zone.RESOLVED]). A state change cancels the card's
 * Active effect (p.17) unless [keepEffects] (resolving a played card keeps its effect; p.7 turns it
 * sideways; some texts swap "without canceling active effects").
 */
fun Game.moveCard(heroId: String, cardId: String, zone: Zone, slot: Int = 0, keepEffects: Boolean = false) {
    val c = hero(heroId).card(cardId) ?: error("$heroId has no card $cardId")
    if (c.zone == zone && c.slot == slot) return
    if (!keepEffects) cancelEffectsOf(heroId, cardId)
    c.zone = zone
    c.slot = if (zone == Zone.RESOLVED) slot else 0
    c.stamp = stamp()
    when (zone) {
        Zone.PLAYED -> {
            c.played = true
            // FAQ (Brogan, Sabina): "played an attack card" is remembered for the turn.
            if (state.revealed && isAttackCard(heroId, cardId)) state.turnRecord.playedAttack += heroId
        }

        Zone.RESOLVED, Zone.DISCARD -> Unit

        else -> {
            c.played = false
            c.faceDown = false
        }
    }
    touch()
}

fun Game.hand(heroId: String): List<String> = hero(heroId).cards.filter { it.zone == Zone.HAND }.map { it.id }

fun Game.discardPile(heroId: String): List<String> =
    hero(heroId).cards.filter { it.zone == Zone.DISCARD }.sortedBy { it.stamp }.map { it.id }

/** Resolved cards in Turn slot order (the "rightmost" is last). */
fun Game.resolvedCards(heroId: String): List<String> =
    hero(heroId).cards.filter { it.zone == Zone.RESOLVED }.sortedBy { it.slot }.map { it.id }

fun Game.unresolvedCard(heroId: String): String? = hero(heroId).cards.firstOrNull { it.zone == Zone.PLAYED }?.id

/** The card's type now (p.9, p.14): null when it is facedown (p.10: it "loses its type, color and actions"). */
fun Game.cardTypeOf(heroId: String, cardId: String): PrimaryAction? {
    val c = hero(heroId).card(cardId) ?: return card(cardId).primary
    if (c.faceDown) return null
    return card(cardId).primary
}

fun Game.cardColorOf(heroId: String, cardId: String): CardColor? {
    val c = hero(heroId).card(cardId)
    if (c?.faceDown == true) return null
    return card(cardId).color
}

/** An Attack card (p.9: "A card with an Attack Primary action is an Attack card"). */
fun Game.isAttackCard(heroId: String, cardId: String): Boolean = cardTypeOf(heroId, cardId) == PrimaryAction.ATTACK

/** A Skill card; a Defense/Skill card counts as both in hand or discard and as Skill when played (p.14). */
fun Game.isSkillCard(heroId: String, cardId: String): Boolean = when (cardTypeOf(heroId, cardId)) {
    PrimaryAction.SKILL, PrimaryAction.DEFENSE_SKILL -> true
    else -> false
}

fun Game.isDefenseCard(heroId: String, cardId: String): Boolean {
    val type = cardTypeOf(heroId, cardId)
    if (type == PrimaryAction.DEFENSE) return true
    if (type != PrimaryAction.DEFENSE_SKILL) return false
    val zone = hero(heroId).card(cardId)?.zone
    return zone != Zone.PLAYED && zone != Zone.RESOLVED
}

/**
 * The lowest Tier of the hero's cards (p.13, p.15): basic cards "are not considered when checking
 * your lowest card Tier". The hero's cards in play this round (not deck, items, box) of colours R/G/B.
 */
fun Game.lowestTier(heroId: String): Int {
    val tiers = hero(heroId).cards.filter {
        it.zone in IN_PLAY_ZONES && card(it.id).tier in 1..3 && card(it.id).color in COLOURED
    }.map { card(it.id).tier!! }
    return tiers.minOrNull() ?: 1
}

private val IN_PLAY_ZONES = setOf(Zone.HAND, Zone.PLAYED, Zone.RESOLVED, Zone.DISCARD)
private val COLOURED = setOf(CardColor.RED, CardColor.GREEN, CardColor.BLUE)

/**
 * Discard a card (p.10 "Discard a Card": "put it into the discard pile. Cards in different states can
 * be discarded. Unless specified in the card text, the card has to be discarded from your hand").
 * From hand, a replacement may discard another card instead (Mrak's Stone Carapace).
 */
fun Game.discard(heroId: String, cardId: String, allowReplacement: Boolean = true): String {
    val h = hero(heroId)
    val c = h.card(cardId) ?: error("$heroId has no card $cardId")
    var actual = cardId
    if (allowReplacement && c.zone == Zone.HAND) {
        replacement(Replacement.Discard(heroId, cardId)).cardId?.let { actual = it }
    }
    val inst = h.card(actual)!!
    val fromResolved = inst.zone == Zone.RESOLVED
    moveCard(heroId, actual, Zone.DISCARD)
    state.turnRecord.discarded.getOrPut(heroId) { ArrayList() } += actual
    // "Discarded cards are open information" (p.10), unless the card is facedown (hidden, p.10).
    val what = if (inst.faceDown) "a facedown card" else cardName(actual)
    emit(GameEvent.CardsChanged(heroId, "${heroName(heroId)} discards $what."))
    trigger(Trigger.CardDiscarded(heroId, actual, fromResolved))
    return actual
}

/**
 * "[victim] discards a card, if able" (p.10 "Making Choices": "that hero chooses a card to discard,
 * if they have one") or, with [orDefeated], "discards a card, or is defeated" ([APP] RULINGS.md: a
 * hero with a matching card in hand must discard; one without is defeated, credited to [byHero]).
 * [filter] narrows the cards ("a card of that color", "a non-basic card"). Returns the card discarded.
 */
fun Game.forceDiscard(
    victim: String,
    byHero: String?,
    orDefeated: Boolean,
    sourceCardId: String? = null,
    what: String = "a card",
    filter: (Card) -> Boolean = { true },
): String? {
    val heroId = heroBodyOf(victim) ?: return null
    val h = hero(heroId)
    val options = h.cards.filter { it.zone == Zone.HAND && filter(card(it.id)) }.map { it.id }
    if (options.isEmpty()) {
        if (orDefeated && (h.at != null || state.pieces.any { it.heroBody == heroId && it.at != null })) {
            note("${heroName(heroId)} cannot discard $what.")
            defeatHero(heroId, byHero)
        }
        return null
    }
    val choice = ask(
        heroId,
        DecisionKind.CHOOSE_CARD,
        "Discard $what",
        options.map { Choice.Card(it) },
        sourceCardId,
    ) as Choice.Card
    return discard(heroId, choice.cardId)
}

/** p.10 "Retrieve a Card": take one of your cards back into your hand. */
fun Game.retrieve(heroId: String, cardId: String) {
    // A facedown card was hidden information and goes to the hand, which is hidden too (p.4, p.10).
    val hidden = hero(heroId).card(cardId)?.let { !isPublic(it) } ?: true
    moveCard(heroId, cardId, Zone.HAND)
    val what = if (hidden) "a facedown card" else cardName(cardId)
    emit(GameEvent.CardsChanged(heroId, "${heroName(heroId)} retrieves $what."))
}

/**
 * p.4 "Hidden Information": anyone may read "unresolved cards, resolved cards, and discarded cards";
 * cards in hand or deck and "any facedown cards" are hidden (event text never names them); a played
 * card is facedown until the reveal (p.7).
 */
internal fun Game.isPublic(c: CardInst): Boolean = when (c.zone) {
    Zone.PLAYED -> state.revealed && !c.faceDown
    Zone.RESOLVED, Zone.DISCARD -> !c.faceDown
    Zone.ULTIMATE -> true
    else -> false
}

/**
 * p.10 "Swap a Card": "all their card states are swapped" (zone, slot, played: in hand, played,
 * unresolved, resolved, discarded). Both cards' Active effects are cancelled unless [keepEffects].
 * Facedown is not one of those states (p.10 "Facedown": a property of a resolved or discarded card, lost
 * when it "changes state to in hand"), so each card keeps its own: a facedown card swapped between two
 * Turn slots stays facedown, and a card entering the hand or play is faceup (RULINGS 51). The log never
 * names a card that is hidden both before and after (hand, deck, facedown: events are public, p.4);
 * [text] replaces the log line.
 */
fun Game.swapCards(heroId: String, a: String, b: String, keepEffects: Boolean = false, text: String? = null) {
    val h = hero(heroId)
    val ca = h.card(a)!!
    val cb = h.card(b)!!
    if (!keepEffects) {
        cancelEffectsOf(heroId, a)
        cancelEffectsOf(heroId, b)
    }
    val hiddenBefore = listOf(ca, cb).associate { it.id to isHiddenCard(it) }
    val z = ca.zone
    val s = ca.slot
    val p = ca.played
    val t = ca.tag
    ca.zone = cb.zone
    ca.slot = cb.slot
    ca.played = cb.played
    ca.tag = cb.tag
    cb.zone = z
    cb.slot = s
    cb.played = p
    cb.tag = t
    for (c in listOf(ca, cb)) {
        if (c.zone != Zone.RESOLVED && c.zone != Zone.DISCARD) c.faceDown = false
    }
    ca.stamp = stamp()
    cb.stamp = stamp()
    touch()
    // A card hidden both before and after the swap is never named (p.4, RULINGS 51/54).
    fun hidden(c: CardInst): Boolean = hiddenBefore.getValue(c.id) && isHiddenCard(c)
    val names = when {
        hidden(ca) && hidden(cb) -> "two hidden cards"
        else -> listOf(ca, cb).joinToString(" and ") { if (hidden(it)) "a hidden card" else cardName(it.id) }
    }
    emit(GameEvent.CardsChanged(heroId, text ?: "${heroName(heroId)} swaps $names."))
}

/** A card other seats cannot see (p.4: hands and decks; p.10: facedown cards). */
private fun isHiddenCard(c: CardInst): Boolean = c.faceDown || c.zone == Zone.HAND || c.zone == Zone.DECK

/** Turn a resolved or discarded card facedown (p.10): it is hidden and its Active effect is cancelled. */
fun Game.turnFaceDown(heroId: String, cardId: String) {
    val c = hero(heroId).card(cardId)!!
    c.faceDown = true
    cancelEffectsOf(heroId, cardId)
    touch()
}

/** Gain coins (p.13, p.15). */
fun Game.gainCoins(heroId: String, amount: Int, why: String = "") {
    if (amount <= 0) return
    val h = hero(heroId)
    h.coins += amount
    val tail = if (why.isEmpty()) "" else " ($why)"
    emit(
        GameEvent.CoinsGained(
            heroId,
            amount,
            "${heroName(heroId)} gains $amount coin${if (amount == 1) "" else "s"}$tail.",
        ),
    )
}

/** Take up to [amount] coins from [fromHero] (Tigerclaw); returns the coins taken. */
fun Game.takeCoins(heroId: String, fromHero: String, amount: Int): Int {
    val from = hero(fromHero)
    val taken = minOf(amount, from.coins)
    if (taken <= 0) return 0
    from.coins -= taken
    hero(heroId).coins += taken
    emit(
        GameEvent.CoinsGained(
            heroId,
            taken,
            "${heroName(heroId)} takes $taken coin${if (taken == 1) "" else "s"} from ${heroName(fromHero)}.",
        ),
    )
    return taken
}
