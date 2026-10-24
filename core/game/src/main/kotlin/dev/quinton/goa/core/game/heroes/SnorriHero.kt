package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.AffectQuery
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.MoveQuery
import dev.quinton.goa.core.game.effects.PassMode
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.script
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.AttackInfo
import dev.quinton.goa.core.game.engine.DefenseScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.giveMarker
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.retrieve
import dev.quinton.goa.core.game.engine.swapCards
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.model.catalog.CardColor

/**
 * Snorri the Runescribe. Card text quoted verbatim above each registration
 * (`catalog/heroes/snorri.json`); `SnorriTest` follows it clause by clause with the FAQ. Readings
 * the text leaves open are in `core/game/rulings/snorri.md`.
 *
 * Runes are persistent markers Snorri gives himself (Inscribe the Runes), one per Turn slot
 * ([Marker.data] `slot`); the rune under the slot of the current turn is active. Rune Mastery
 * (Ultimate) adds a second active rune, chosen for each action.
 */
object SnorriHero : HeroModule {
    override val heroId: String = "snorri"
    override val complete: Boolean = true

    /** The four runes, in the order the silver card prints them. */
    enum class Rune(val kind: String, val label: String) {
        AXE("marker_rune_axe", "Axe"),
        BIRD("marker_rune_bird", "Bird"),
        ANVIL("marker_rune_anvil", "Anvil"),
        HORN("marker_rune_horn", "Horn"),
    }

    private const val ULTIMATE = "snorri-purple-4"
    private const val SLOT = "slot"
    private const val NOTE = "snorri.runes"
    const val IMMUNE = "snorri.immune"
    const val IGNORE_OBSTACLES = "snorri.ignoreObstacles"

    override fun register(r: Registry) {
        // Rune Sigils: "Target a unit adjacent to you; if a rune is active: {rune_bird}: You may target a
        // minion in range instead. {rune_axe}: +3 Attack. {rune_anvil}: If you target a hero, gain 3
        // coins. {rune_horn}: Repeat once on a different hero in range."
        r.card("snorri-gold", Sigils(handicap = false))
        // Rune Shards (Handicap): "Target a hero adjacent to you; if a rune is active: {rune_axe}: +3
        // Attack. {rune_anvil}: Gain 2 coins. {rune_horn}: Repeat once on a different hero in range."
        r.card("snorri-handicap", Sigils(handicap = true))
        // Inscribe the Runes: "Give yourself 4 Rune markers and place one below each of your turn slots.
        // A rune is active as long as it is below the turn slot matching the current turn. Rune markers
        // are not removed at the end of round, nor if you are defeated."
        r.card("snorri-silver", script { inscribe(this) })
        // Runic Dagger: "Target a unit adjacent to you. After the attack: If the {rune_anvil} rune is
        // active, you may retrieve a discarded card."
        r.card("snorri-red-1", Melee(hornMove = false, axeRepeat = false))
        // Runic Hammer: "Before the attack: If the {rune_horn} rune is active, you may move 1 space.
        // Target a unit adjacent to you. After the attack: If the {rune_anvil} rune is active, you may
        // retrieve a discarded card."
        r.card("snorri-red-2a", Melee(hornMove = true, axeRepeat = false))
        // Runic Battleaxe: "Before the attack: If the {rune_horn} rune is active, you may move 1 space.
        // Target a unit adjacent to you. After the attack: If a rune is active: {rune_axe}: May repeat
        // once on an enemy minion. {rune_anvil}: You may retrieve a discarded card."
        r.card("snorri-red-3a", Melee(hornMove = true, axeRepeat = true))
        // Runecaster: "Target a unit at maximum range. After the attack: If a rune is active: {rune_horn}:
        // Move up to 2 spaces. {rune_axe}: An enemy hero who was adjacent to the target discards a card,
        // or is defeated."
        r.card("snorri-red-2b", Caster(birdInRange = false))
        // Runeblaster: "If the {rune_bird} rune is active, target a unit in range, otherwise target a unit
        // at maximum range. After the attack: …" (as Runecaster).
        r.card("snorri-red-3b", Caster(birdInRange = true))
        // Oath of Endurance: "If a rune is active: {rune_horn}: Block a basic attack. {rune_axe}: Block a
        // non-ranged attack. This Turn: You are immune to enemy actions."
        r.card("snorri-green-1", Oath(listOf(Rune.HORN, Rune.AXE), chooseOne = false))
        // Oath of Fortitude: "If a rune is active: {rune_horn}: Block a basic attack; {rune_axe}: Block a
        // non-ranged attack; {rune_bird}: Block a ranged attack; This Turn: You are immune to enemy actions."
        r.card("snorri-green-2a", Oath(listOf(Rune.HORN, Rune.AXE, Rune.BIRD), chooseOne = false))
        // Oath of Perseverance: "Choose one active rune: {rune_horn}: Block a basic attack; {rune_axe}:
        // Block a non-ranged attack; {rune_bird}: Block a ranged attack; {rune_anvil}: Block a non-basic
        // attack; This Turn: You are immune to enemy actions."
        r.card("snorri-green-3a", Oath(listOf(Rune.HORN, Rune.AXE, Rune.BIRD, Rune.ANVIL), chooseOne = true))
        // Runetrap: "If a rune is active, an enemy hero in radius: {rune_horn}: Discards a green card, if
        // able. {rune_axe}: Discards a silver card, if able. {rune_anvil}: Discards a blue card, if able."
        r.card("snorri-green-2b", Trap(bomb = false))
        // Runebomb: "Choose one active rune; depending on that rune, an enemy hero in radius: {rune_horn}:
        // Discards a green card, if able. {rune_axe}: Discards a silver card, if able. {rune_anvil}:
        // Discards a blue card, if able. {rune_bird}: Discards a gold card, if able."
        r.card("snorri-green-3b", Trap(bomb = true))
        // Safe Passage: "If the {rune_bird} rune is active, This turn: You may ignore obstacles."
        r.card("snorri-blue-1", Passage(anvilImmune = false, hornBonus = false))
        // Hidden Passage: "If a rune is active: {rune_bird}: This turn: You may ignore obstacles.
        // {rune_anvil}: This turn: You are immune to enemy actions."
        r.card("snorri-blue-2a", Passage(anvilImmune = true, hornBonus = false))
        // Deep Passage: "… {rune_horn}: Gain +2 Movement." (as Hidden Passage)
        r.card("snorri-blue-3a", Passage(anvilImmune = true, hornBonus = true))
        // Ancestral Boon: "If a rune is active, a friendly hero in radius may: {rune_axe}: Swap a resolved
        // card with a card in hand. {rune_anvil}: Retrieve all their discarded cards."
        r.card("snorri-blue-2b", Boon(grace = false))
        // Ancestral Grace: "… {rune_bird}: Swap one of their items with an item on their card of the same
        // tier and color." (as Ancestral Boon)
        r.card("snorri-blue-3b", Boon(grace = true))
        // Rune Mastery (Ultimate): "Each time you perform an action, choose one inactive rune; that rune
        // counts as a second active rune for this action." Read by [runes] when an action's text reads
        // the runes.
        r.card(ULTIMATE, script())
        r.behavior(IMMUNE, Immune)
        r.behavior(IGNORE_OBSTACLES, IgnoreObstacles)
    }

    // ---- runes -------------------------------------------------------------------------------

    /** Runes placed below [holder]'s Turn slots: slot -> rune. */
    fun placedRunes(g: Game, holder: String): Map<Int, Rune> {
        val h = g.heroOrNull(holder) ?: return emptyMap()
        val out = LinkedHashMap<Int, Rune>()
        for (m in h.markers) {
            if (m.from != holder) continue
            val rune = Rune.entries.firstOrNull { it.kind == m.kind } ?: continue
            m.data[SLOT]?.toIntOrNull()?.let { out[it] = rune }
        }
        return out
    }

    /** "A rune is active as long as it is below the turn slot matching the current turn." */
    fun turnRune(g: Game, holder: String): Rune? = placedRunes(g, holder)[g.state.turn]

    /**
     * [APP] Whose runes a card reads: the runes belong to the hero who owns the card (Snorri), also
     * when another hero performs it (NebKher's Mind Grip, rulings/snorri.md).
     */
    private fun holderOf(g: Game, performer: String, cardId: String): String =
        g.state.heroes.firstOrNull { it.card(cardId) != null }?.id ?: performer

    /**
     * The active runes for an action of [performer] with [cardId]: the rune of the current turn and,
     * with Rune Mastery, one inactive rune [performer] chooses (asked once per action, only when the
     * text reads the runes). A probe run (is the Primary action possible?) counts every inactive rune
     * as a candidate instead of asking.
     */
    private fun activeRunes(g: Game, performer: String, cardId: String, ask: (List<Rune>) -> Rune): Set<Rune> {
        val holder = holderOf(g, performer, cardId)
        val placed = placedRunes(g, holder)
        val base = placed[g.state.turn]
        val out = LinkedHashSet<Rune>()
        base?.let { out += it }
        val h = g.hero(holder)
        val mastery = performer == holder && h.at != null && h.cards.any { it.id == ULTIMATE && it.zone == Zone.ULTIMATE }
        if (!mastery) return out
        val inactive = Rune.entries.filter { it in placed.values && it != base }
        if (inactive.isEmpty()) return out
        if (g.mode == RunMode.PROBE) return out + inactive
        out += ask(inactive)
        return out
    }

    private fun askMastery(g: Game, heroId: String, cardId: String, inactive: List<Rune>): Rune {
        val c = g.ask(
            heroId,
            DecisionKind.CHOOSE_OPTION,
            "Rune Mastery: choose an inactive rune; it counts as a second active rune for this action",
            inactive.mapIndexed { i, r -> Choice.Option(i, r.label) },
            cardId,
        ) as Choice.Option
        return inactive[c.index]
    }

    /** The active runes for this action (asked once per action). */
    @Suppress("UNCHECKED_CAST")
    private fun ActionScope.runes(): Set<Rune> {
        (notes[NOTE] as? Set<Rune>)?.let { return it }
        val res = activeRunes(g, heroId, cardId) { askMastery(g, heroId, cardId, it) }
        notes[NOTE] = res
        return res
    }

    private fun DefenseScope.runes(): Set<Rune> = activeRunes(g, heroId, cardId) { askMastery(g, heroId, cardId, it) }

    /** "Choose one active rune" among [among] (FAQ: no choice when only one rune is active). */
    private fun chooseActive(g: Game, heroId: String, cardId: String, among: List<Rune>): Rune? {
        if (among.size <= 1) return among.firstOrNull()
        val c = g.ask(
            heroId,
            DecisionKind.CHOOSE_OPTION,
            "Choose one active rune",
            among.mapIndexed { i, r -> Choice.Option(i, r.label) },
            cardId,
        ) as Choice.Option
        return among[c.index]
    }

    // ---- Inscribe the Runes --------------------------------------------------------------------

    private fun inscribe(a: ActionScope) {
        // "Give yourself 4 Rune markers and place one below each of your turn slots."
        val left = Rune.entries.toMutableList()
        val bySlot = LinkedHashMap<Int, Rune>()
        for (slot in 1..4) {
            val rune = if (left.size == 1) {
                left.single()
            } else {
                left[a.chooseOption("Which rune goes below turn slot $slot?", left.map { it.label })!!]
            }
            bySlot[slot] = rune
            left -= rune
        }
        // "Rune markers are not removed at the end of round, nor if you are defeated." Giving them again
        // moves them (one marker of a kind per giver).
        for ((slot, rune) in bySlot) {
            val m = a.g.giveMarker(a.heroId, rune.kind, a.heroId, persistent = true)
            m.data[SLOT] = slot.toString()
        }
    }

    // ---- attacks -------------------------------------------------------------------------------

    private class Sigils(private val handicap: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // The horn's repeat: "Repeat once on a different hero in range" (the repeat targets a hero in
            // range; the rest of the action, axe and anvil included, is performed again).
            if (a.isRepeat) {
                val t = a.attackTarget("Target a different hero in range") { a.g.isHero(it) && a.inRange(it) } ?: return
                hit(a, t, a.runes(), valid = { a.g.isHero(it) && a.inRange(it) })
                return
            }
            if (handicap) {
                // "Target a hero adjacent to you"
                val valid = { id: String -> a.g.isHero(id) && a.isAdjacent(id) }
                val t = a.attackTarget("Target a hero adjacent to you") { valid(it) } ?: return
                hit(a, t, a.runes(), valid)
            } else {
                // "Target a unit adjacent to you; … {rune_bird}: You may target a minion in range instead."
                val runes = a.runes()
                val valid = { id: String ->
                    a.isAdjacent(id) || (Rune.BIRD in runes && a.g.isMinion(id) && a.inRange(id))
                }
                val prompt = if (Rune.BIRD in runes) {
                    "Target a unit adjacent to you, or a minion in range"
                } else {
                    "Target a unit adjacent to you"
                }
                val t = a.attackTarget(prompt) { valid(it) } ?: return
                hit(a, t, runes, valid)
            }
        }

        private fun hit(a: ActionScope, t: String, runes: Set<Rune>, valid: (String) -> Boolean) {
            // {rune_anvil}: "If you target a hero, gain 3 coins." / Handicap: "Gain 2 coins."
            if (Rune.ANVIL in runes) {
                if (handicap) {
                    a.g.gainCoins(a.heroId, 2, a.card.name)
                } else if (a.g.isHero(t)) {
                    a.g.gainCoins(a.heroId, 3, a.card.name)
                }
            }
            // {rune_axe}: "+3 Attack."
            a.attack(t, valid = valid, bonus = { if (Rune.AXE in runes) 3 else 0 })
            // {rune_horn}: "Repeat once on a different hero in range." (p.10: a repeat is optional.)
            if (Rune.HORN in runes && !a.isRepeat) {
                val others = a.unitsWithin(a.range).filter {
                    a.g.isHero(it) && a.isEnemy(it) && it !in a.targets && a.canTarget(it)
                }
                if (others.isNotEmpty()) {
                    a.repeat(
                        differentTarget = true,
                        filter = { a.g.isHero(it) },
                        prompt = "Horn rune: repeat once on a different hero in range?",
                    )
                }
            }
        }
    }

    private class Melee(private val hornMove: Boolean, private val axeRepeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Before the attack: If the {rune_horn} rune is active, you may move 1 space."
            if (hornMove && Rune.HORN in a.runes()) a.moveSelf(1, optional = true)
            // "Target a unit adjacent to you."
            val t = a.attackTarget("Target a unit adjacent to you") { a.isAdjacent(it) } ?: return
            val res = a.attack(t)
            if (!res.connected) return
            val runes = a.runes()
            // Battleaxe {rune_axe}: "May repeat once on an enemy minion."
            if (axeRepeat && Rune.AXE in runes && !a.isRepeat) {
                val minions = a.unitsWithin(2).filter { a.g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) }
                if (minions.isNotEmpty()) {
                    a.repeat(filter = { a.g.isMinion(it) }, prompt = "Axe rune: repeat once on an enemy minion?")
                }
            }
            // {rune_anvil}: "you may retrieve a discarded card."
            if (Rune.ANVIL in runes) a.mayRetrieve()
        }
    }

    private class Caster(private val birdInRange: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // Runeblaster: "If the {rune_bird} rune is active, target a unit in range, otherwise target a
            // unit at maximum range." Runecaster: "Target a unit at maximum range."
            val anyRange = birdInRange && Rune.BIRD in a.runes()
            val valid = { id: String -> if (anyRange) a.inRange(id) else a.distanceToTarget(id) == a.range }
            val prompt = if (anyRange) "Target a unit in range" else "Target a unit at maximum range"
            val t = a.attackTarget(prompt) { valid(it) } ?: return
            // "An enemy hero who was adjacent to the target": the heroes next to it before the attack.
            val adjacentBefore = a.g.unitsAdjacentTo(a.g.posOf(t)!!).filter { a.g.isHero(it) && a.isEnemy(it) }
            val res = a.attack(t, valid = valid)
            if (!res.connected) return
            val runes = a.runes()
            // {rune_horn}: "Move up to 2 spaces."
            if (Rune.HORN in runes) a.moveSelf(0, 2)
            // {rune_axe}: "An enemy hero who was adjacent to the target discards a card, or is defeated."
            if (Rune.AXE in runes) {
                val victims = adjacentBefore.filter { a.g.posOf(it) != null && a.canTarget(it) }
                val v = a.chooseUnit("An enemy hero who was adjacent to the target discards a card, or is defeated", victims)
                if (v != null) a.discardOrDefeat(v)
            }
        }
    }

    private fun ActionScope.distanceToTarget(id: String): Int? {
        val me = hero.at ?: return null
        return g.posOf(id)?.let { g.distance(me, it) }
    }

    // ---- Oaths (Defense, Block) ------------------------------------------------------------------

    private class Oath(private val runes: List<Rune>, private val chooseOne: Boolean) : CardScript {
        override val hasPrimary: Boolean get() = false

        override fun defense(d: DefenseScope) {
            val active = d.runes()
            val usable = runes.filter { it in active }
            val blocked = if (chooseOne) {
                // "Choose one active rune:" (FAQ: with one active rune there is no choice).
                val pick = chooseActive(d.g, d.heroId, d.cardId, active.toList().filter { it in runes })
                pick != null && blocks(pick, d.attack)
            } else {
                // "If a rune is active:" every active rune's line applies.
                usable.any { blocks(it, d.attack) }
            }
            d.block(blocked)
            // "This Turn: You are immune to enemy actions."
            d.activeEffect(Timing.THIS_TURN, key = IMMUNE)
        }

        private fun blocks(rune: Rune, attack: AttackInfo): Boolean = when (rune) {
            Rune.HORN -> attack.basic // "Block a basic attack"
            Rune.AXE -> !attack.ranged // "Block a non-ranged attack"
            Rune.BIRD -> attack.ranged // "Block a ranged attack"
            Rune.ANVIL -> !attack.basic // "Block a non-basic attack"
        }
    }

    /** "You are immune to enemy actions." */
    private object Immune : Behavior {
        override fun immune(g: Game, src: Src, q: AffectQuery): Boolean {
            val owner = src.owner ?: return false
            val actor = q.actorId ?: return false
            return g.heroBodyOf(q.targetId) == owner && g.isEnemy(owner, actor)
        }
    }

    /** "You may ignore obstacles." [APP] Snorri's own moves (not when another hero moves or pushes him). */
    private object IgnoreObstacles : Behavior {
        override fun passMode(g: Game, src: Src, q: MoveQuery): PassMode? {
            val owner = src.owner ?: return null
            return if (q.unitId == owner && q.actorId == owner) PassMode.IGNORE_OBSTACLES else null
        }
    }

    // ---- skills --------------------------------------------------------------------------------

    private class Trap(private val bomb: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val heroes = a.unitsWithin(a.radius).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
            if (heroes.isEmpty()) a.fizzle("no enemy hero in radius")
            val table = if (bomb) BOMB else TRAP
            val active = a.runes()
            val colours = if (bomb) {
                // "Choose one active rune; depending on that rune, …"
                val pick = chooseActive(a.g, a.heroId, a.cardId, active.toList().filter { it in table.keys })
                    ?: a.fizzle("no rune with an effect is active")
                listOf(table.getValue(pick))
            } else {
                // "If a rune is active, …": each active rune's line ([APP] the same hero for both runes).
                table.filterKeys { it in active }.values.toList()
            }
            if (colours.isEmpty()) a.fizzle("no rune with an effect is active")
            val v = a.chooseUnit("An enemy hero in radius discards a card", heroes)!!
            for (c in colours) {
                a.discardIfAble(v, "a ${c.name.lowercase()} card") { it.color == c }
            }
        }

        companion object {
            /** Runetrap, in printed order. */
            val TRAP: Map<Rune, CardColor> = linkedMapOf(
                Rune.HORN to CardColor.GREEN,
                Rune.AXE to CardColor.SILVER,
                Rune.ANVIL to CardColor.BLUE,
            )

            /** Runebomb, in printed order. */
            val BOMB: Map<Rune, CardColor> = TRAP + (Rune.BIRD to CardColor.GOLD)
        }
    }

    private class Passage(private val anvilImmune: Boolean, private val hornBonus: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // Card text first, then the movement (p.11).
            val runes = a.runes()
            // {rune_bird}: "This turn: You may ignore obstacles."
            if (Rune.BIRD in runes) a.activeEffect(Timing.THIS_TURN, IGNORE_OBSTACLES)
            // {rune_anvil}: "This turn: You are immune to enemy actions."
            if (anvilImmune && Rune.ANVIL in runes) a.activeEffect(Timing.THIS_TURN, IMMUNE)
            // {rune_horn}: "Gain +2 Movement."
            a.movementAction(bonus = if (hornBonus && Rune.HORN in runes) 2 else 0)
        }
    }

    private class Boon(private val grace: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "If a rune is active, a friendly hero in radius may: …"
            val friends = a.unitsWithin(a.radius).filter { a.g.isHero(it) && a.isFriendly(it) && a.canTarget(it) }
                .map { a.g.heroBodyOf(it)!! }.distinct()
            if (friends.isEmpty()) a.fizzle("no friendly hero in radius")
            val runes = a.runes()
            val lines = listOfNotNull(
                Rune.AXE.takeIf { it in runes },
                Rune.ANVIL.takeIf { it in runes },
                Rune.BIRD.takeIf { grace && it in runes },
            )
            if (lines.isEmpty()) a.fizzle("no rune with an effect is active")
            val f = a.target(a.chooseUnit("A friendly hero in radius", friends)!!)
            for (rune in lines) {
                when (rune) {
                    Rune.AXE -> swapResolved(a, f)
                    Rune.ANVIL -> retrieveAll(a, f)
                    Rune.BIRD -> swapItem(a, f)
                    Rune.HORN -> Unit
                }
            }
        }

        /** {rune_axe}: "Swap a resolved card with a card in hand." (the friend may) */
        private fun swapResolved(a: ActionScope, f: String) {
            val h = a.g.hero(f)
            val resolved = h.cards.filter { it.zone == Zone.RESOLVED }.sortedBy { it.slot }.map { it.id }
            val hand = h.cards.filter { it.zone == Zone.HAND }.map { it.id }
            if (resolved.isEmpty() || hand.isEmpty()) return
            val r = a.chooseCard("You may swap a resolved card with a card in hand", resolved, optional = true, chooser = f)
                ?: return
            val c = a.chooseCard("Swap it with a card in your hand", hand, chooser = f)!!
            a.g.swapCards(f, r, c)
        }

        /** {rune_anvil}: "Retrieve all their discarded cards." (the friend may) */
        private fun retrieveAll(a: ActionScope, f: String) {
            val pile = a.g.hero(f).cards.filter { it.zone == Zone.DISCARD }.sortedBy { it.stamp }.map { it.id }
            if (pile.isEmpty()) return
            if (!a.confirm("Retrieve all your discarded cards?", chooser = f)) return
            for (c in pile) a.g.retrieve(f, c)
        }

        /**
         * {rune_bird}: "Swap one of their items with an item on their card of the same tier and color."
         * [APP] The item card and the other card of that colour and Tier (wherever it is) swap states.
         */
        private fun swapItem(a: ActionScope, f: String) {
            val h = a.g.hero(f)
            val pairs = h.cards.filter { it.zone == Zone.ITEM }.mapNotNull { item ->
                val ic = a.g.card(item.id)
                val other = h.cards.firstOrNull { o ->
                    o.id != item.id && o.zone != Zone.ITEM && a.g.card(o.id).color == ic.color &&
                        a.g.card(o.id).tier == ic.tier
                }
                other?.let { item.id to it.id }
            }
            if (pairs.isEmpty()) return
            val item = a.chooseCard(
                "You may swap one of your items with your card of the same tier and color",
                pairs.map { it.first },
                optional = true,
                chooser = f,
            ) ?: return
            a.g.swapCards(f, item, pairs.first { it.first == item }.second)
        }
    }
}
