package dev.quinton.goa.core.game.effects

import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.DefenseScope

/*
 * Card scripts (EFFECTS.md "Card scripts"). One [HeroModule] per hero, in `heroes/<Hero>.kt`,
 * registers a [CardScript] for every card id of that hero (deck, Handicap, Ultimate, extras) and the
 * behaviours of its tokens and markers. The engine never parses card text: what a card does is the
 * Kotlin here, written clause by clause from the verbatim text (cited in a comment above each card).
 */

/** What one card does. */
interface CardScript {
    /**
     * The Primary action (p.7: "The card text must be applied when, and only when, performing the
     * Primary action"). For an Attack card this targets and calls [ActionScope.attack]; for a
     * Movement card the engine performs the movement after this returns unless the script already
     * called [ActionScope.movementAction] (p.11: "the card text comes into effect before movement").
     * Not called for a Defense card played on the board (p.14: no Defense action on a played card).
     */
    fun primary(a: ActionScope) {}

    /**
     * Defense action text (p.14 step 4), called when the card is discarded to defend and its primary
     * action is Defense or Defense/Skill (p.14: "The card text is applied whether you play this card
     * as a Skill card, or discard it to defend").
     */
    fun defense(d: DefenseScope) {}

    /** True when [primary] is implemented (an Ultimate or a Defense card has none). */
    val hasPrimary: Boolean get() = true

    /** Hooks while an Active effect of this card is in force, or always for an unlocked Ultimate. */
    val behavior: Behavior? get() = null
}

/** A hero's implementation: registered once, looked up by the engine. */
interface HeroModule {
    val heroId: String

    /**
     * True only when every card of the hero (deck, Handicap, Ultimate, extras) has a script and a
     * test following its text clause by clause ([dev.quinton.goa.core.game.GameFactory.playableHeroes]).
     */
    val complete: Boolean

    fun register(r: Registry)
}

/** A limited token supply (p.18 "Place": "Tokens are intentionally limited"). */
class TokenKind(
    val kind: String,
    val label: String,
    /** How many such tokens the hero's box holds; null = not limited by the rules we know. */
    val supply: Int?,
    /** p.19: removed at the end of round unless the text says otherwise. */
    val removeAtEndOfRound: Boolean = true,
    /** "Units may move through them" (Min's mines). */
    val passable: Boolean = false,
)

/** Everything the hero modules register. Built once per engine; read-only afterwards. */
class Registry {
    private val cards = HashMap<String, CardScript>()
    private val behaviors = HashMap<String, Behavior>()
    private val tokens = HashMap<String, TokenKind>()
    private val heroRules = HashMap<String, Behavior>()
    private val modules = LinkedHashMap<String, HeroModule>()

    fun card(cardId: String, script: CardScript) {
        check(cardId !in cards) { "Card $cardId registered twice" }
        cards[cardId] = script
        script.behavior?.let { behaviors.putIfAbsent(cardId, it) }
    }

    /** A behaviour under a key other than a card id: a token kind, a marker kind, a second effect of a card. */
    fun behavior(key: String, behavior: Behavior) {
        check(key !in behaviors) { "Behaviour $key registered twice" }
        behaviors[key] = behavior
    }

    fun token(kind: TokenKind) {
        tokens[kind.kind] = kind
    }

    /** A rule always in force for [heroId] while it is in the game (rare: hero-wide rules). */
    fun heroRule(heroId: String, behavior: Behavior) {
        heroRules[heroId] = behavior
    }

    fun module(module: HeroModule) {
        modules[module.heroId] = module
        module.register(this)
    }

    fun script(cardId: String): CardScript? = cards[cardId]

    fun behavior(key: String): Behavior? = behaviors[key]

    fun tokenKind(kind: String): TokenKind? = tokens[kind]

    fun heroRule(heroId: String): Behavior? = heroRules[heroId]

    fun module(heroId: String): HeroModule? = modules[heroId]

    val heroModules: Collection<HeroModule> get() = modules.values
}

/** Shorthand for a script written as lambdas (EFFECTS.md shows both styles). */
fun script(
    behavior: Behavior? = null,
    defense: (DefenseScope.() -> Unit)? = null,
    primary: (ActionScope.() -> Unit)? = null,
): CardScript = object : CardScript {
    override fun primary(a: ActionScope) {
        primary?.invoke(a)
    }

    override fun defense(d: DefenseScope) {
        defense?.invoke(d)
    }

    override val hasPrimary: Boolean get() = primary != null

    override val behavior: Behavior? get() = behavior
}
