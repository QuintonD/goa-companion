package dev.quinton.goa.core.game.heroes.gydion

import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.ActorDefeated
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.actionAllowed
import dev.quinton.goa.core.game.engine.cancelEffect
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.primaryKind
import dev.quinton.goa.core.game.engine.primaryPossible
import dev.quinton.goa.core.game.engine.win

/*
 * Gydion's spellbook (gydion-silver "Prepare Spells": "Add all spell cards to the spellbook. Only
 * Gydion can look at the cards in the spellbook. As each spell is cast, remove it from the spellbook
 * faceup. The action on the spell card is performed by the hero casting the spell.").
 *
 * The 22 spell cards are the hero JSON `extras`, always in [Zone.EXTRA]; [dev.quinton.goa.core.game
 * .engine.CardInst.tag] says where: [BOX] (not added yet: the game starts with an empty spellbook,
 * designer on BGG thread 3491113), [SPELLBOOK], or [REMOVED] (removed faceup: cast, or Find Familiar).
 * Readings: core/game/rulings/gydion.md.
 */
object Spellbook {
    const val BOX: String = "box"
    const val SPELLBOOK: String = "spellbook"
    const val REMOVED: String = "removed"

    /** Public counters on Gydion's dashboard ([HeroState.counters]). */
    const val COUNT_IN_BOOK: String = "spellbook"
    const val COUNT_REMOVED: String = "spellsRemoved"
    const val COUNT_WISH: String = "wishCasts"

    /** "After you cast the 'Wish' spell three times your team wins the game." */
    const val WISHES_TO_WIN: Int = 3

    const val ULTIMATE: String = "gydion-purple-4"

    private const val S = "gydion-spell-"
    const val MAGIC_MISSILE: String = "${S}magic-missile"
    const val SHOCKING_GRASP: String = "${S}shocking-grasp"
    const val EXPEDITIOUS_RETREAT: String = "${S}expeditious-retreat"
    const val BURNING_HANDS: String = "${S}burning-hands"
    const val FIREBALL: String = "${S}fireball"
    const val SUNBURST: String = "${S}sunburst"
    const val VAMPIRIC_TOUCH: String = "${S}vampiric-touch"
    const val CREATE_UNDEAD: String = "${S}create-undead"
    const val ENERGY_DRAIN: String = "${S}energy-drain"
    const val SHIELD: String = "${S}shield"
    const val BANISHMENT: String = "${S}banishment"
    const val INVULNERABILITY: String = "${S}invulnerability"
    const val DIMENSION_DOOR: String = "${S}dimension-door"
    const val FIND_FAMILIAR: String = "${S}find-familiar"
    const val CLOUD_KILL: String = "${S}cloud-kill"
    const val SUGGESTION: String = "${S}suggestion"
    const val DOMINATE_PERSON: String = "${S}dominate-person"
    const val POWER_WORD_KILL: String = "${S}power-word-kill"
    const val MIDAS_TOUCH: String = "${S}midas-touch"
    const val DISINTEGRATE: String = "${S}disintegrate"
    const val POLYMORPH: String = "${S}polymorph"
    const val WISH: String = "${S}wish"

    /** Every spell card, in the hero JSON's order (option lists use it). */
    val ALL: List<String> = listOf(
        MAGIC_MISSILE, SHOCKING_GRASP, EXPEDITIOUS_RETREAT, BURNING_HANDS, FIREBALL, SUNBURST, VAMPIRIC_TOUCH,
        CREATE_UNDEAD, ENERGY_DRAIN, SHIELD, BANISHMENT, INVULNERABILITY, DIMENSION_DOOR, FIND_FAMILIAR,
        CLOUD_KILL, SUGGESTION, DOMINATE_PERSON, POWER_WORD_KILL, MIDAS_TOUCH, DISINTEGRATE, POLYMORPH, WISH,
    )

    /** The hero whose spellbook holds [spellId]: the owner of the spell cards (Gydion), whoever casts. */
    fun owner(g: Game, spellId: String): HeroState? = g.state.heroes.firstOrNull { it.card(spellId) != null }

    fun tagOf(g: Game, spellId: String): String? = owner(g, spellId)?.card(spellId)?.tag

    fun inSpellbook(g: Game, spellId: String): Boolean = tagOf(g, spellId) == SPELLBOOK

    /** The spells now in [owner]'s spellbook, in [ALL] order. */
    fun contents(owner: HeroState): List<String> =
        ALL.filter { id -> owner.card(id)?.let { it.zone == Zone.EXTRA && it.tag == SPELLBOOK } == true }

    /** "for each other spell card removed from the spellbook" (Sunburst, Dimension Door, Midas' Touch). */
    fun otherRemoved(g: Game, spellId: String): Int {
        val owner = owner(g, spellId) ?: return 0
        return owner.cards.count { it.zone == Zone.EXTRA && it.tag == REMOVED && it.id != spellId }
    }

    /**
     * Put [spellId] into [tag]. A spell returned to the spellbook changes state, so its Active effect
     * ends (p.17; Shield's reminder "Cancelled if the spell is returned to the spellbook"), whoever cast it.
     */
    fun setTag(g: Game, owner: HeroState, spellId: String, tag: String) {
        val c = owner.card(spellId) ?: return
        if (c.tag == tag) return
        if (tag == SPELLBOOK) g.state.effects.filter { it.cardId == spellId }.forEach { g.cancelEffect(it) }
        c.tag = tag
        c.faceDown = false
        g.touch()
        refreshCounters(owner)
    }

    fun refreshCounters(owner: HeroState) {
        owner.counters[COUNT_IN_BOOK] = owner.cards.count { it.zone == Zone.EXTRA && it.tag == SPELLBOOK }
        owner.counters[COUNT_REMOVED] = owner.cards.count { it.zone == Zone.EXTRA && it.tag == REMOVED }
    }

    /**
     * Can [caster] cast [spellId] now? It must be in the spellbook, and its action must be one the
     * caster may perform whose first instruction can be done (FAQ "Can I play an action if only some of
     * its instructions can be performed?"; [APP] rulings/gydion.md: a spell whose action cannot start
     * cannot be cast).
     */
    fun castable(g: Game, caster: String, spellId: String): Boolean {
        if (!inSpellbook(g, spellId)) return false
        val kind = primaryKind(g.card(spellId))
        return g.actionAllowed(caster, spellId, kind, true) && g.primaryPossible(caster, spellId)
    }

    /** "Choose and cast one spell in the spellbook —" / "Cast the X spell in the spellbook." (mandatory). */
    fun castOneOf(a: ActionScope, spells: List<String>) {
        val available = spells.filter { castable(a.g, a.heroId, it) }
        if (available.isEmpty()) a.fizzle("no spell of this card can be cast from the spellbook")
        val spell = if (available.size == 1) {
            available.single()
        } else {
            a.chooseCard("Choose and cast one spell in the spellbook", available)!!
        }
        cast(a.g, a.heroId, spell)
    }

    /**
     * Cast [spellId]: remove it from the spellbook faceup, then [caster] performs the action on the spell
     * card (a complete action of its own type: its Before/After action triggers, immunity by that type,
     * basic for the gold spells, p.9). Wish counts toward the win.
     */
    fun cast(g: Game, caster: String, spellId: String) {
        val owner = owner(g, spellId) ?: g.fizzle("no spellbook")
        setTag(g, owner, spellId, REMOVED)
        g.emit(GameEvent.CardsChanged(caster, "${g.heroName(caster)} casts ${g.cardName(spellId)}."))
        val wish = spellId == WISH
        if (wish) owner.counters[COUNT_WISH] = (owner.counters[COUNT_WISH] ?: 0) + 1
        val done = g.performAction(caster, spellId, primaryKind(g.card(spellId)), primary = true)
        // The caster was defeated during the spell: the action around it ends too.
        if (done == null) throw ActorDefeated(caster)
        if (wish && (owner.counters[COUNT_WISH] ?: 0) >= WISHES_TO_WIN) {
            val team = g.teamOf(caster) ?: return
            g.win(team, WinReason.CARD_TEXT, "${g.heroName(caster)} has cast Wish three times.")
        }
    }

    /** "Add all spell cards to the spellbook." */
    fun addAll(g: Game, owner: HeroState) {
        for (id in ALL) setTag(g, owner, id, SPELLBOOK)
        refreshCounters(owner)
        g.emit(GameEvent.CardsChanged(owner.id, "All spell cards are added to ${g.heroName(owner.id)}'s spellbook."))
    }
}
