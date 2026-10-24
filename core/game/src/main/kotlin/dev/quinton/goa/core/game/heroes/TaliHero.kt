package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.AffectQuery
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.DefeatOutcome
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Replacement
import dev.quinton.goa.core.game.effects.ReplacementResult
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.actionAllowed
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.canTarget
import dev.quinton.goa.core.game.engine.cardColorOf
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.discard
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.hand
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.performPrimary
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.primaryKind
import dev.quinton.goa.core.game.engine.primaryPossible
import dev.quinton.goa.core.game.engine.removePiece
import dev.quinton.goa.core.game.engine.repeatForbidden
import dev.quinton.goa.core.game.engine.retrieve
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Tali the Spiritcaller. Card text quoted verbatim above each registration
 * (`catalog/heroes/tali.json`); `TaliTest` follows it clause by clause with the FAQ. Readings the
 * text leaves open are in `core/game/rulings/tali.md`.
 */
object TaliHero : HeroModule {
    override val heroId: String = "tali"
    override val complete: Boolean = true

    const val ICE: String = "token_ice"
    const val TOTEM: String = "token_totem"
    private const val VARIANT = "variant"
    private const val VENERATED = "venerated"
    private const val ULTIMATE = "tali-purple-4"

    /** The colours a hero may name (p.9: the Action card colours; the Ultimate is never in hand or discard). */
    private val COLOURS = listOf(CardColor.GOLD, CardColor.SILVER, CardColor.RED, CardColor.GREEN, CardColor.BLUE)

    override fun register(r: Registry) {
        // [APP] Supplies (rulings/tali.md 7): the most any card places (Pack Ice: 3; one Totem).
        r.token(TokenKind(ICE, "Ice", supply = 3))
        r.token(TokenKind(TOTEM, "Totem", supply = 1))
        // "Enemy heroes have -1 Initiative for every Ice token they are adjacent to." (a token property, p.19)
        r.behavior(ICE, IceToken)
        // "If a friendly (melee) minion adjacent to a Totem would be defeated, remove the Totem instead."
        // Venerated: "Totem tokens are immune to enemy actions."
        r.behavior(TOTEM, TotemToken)

        // Ice Blast: "Target a unit adjacent to you. If the target discards a card to defend, After the
        // attack: Another enemy hero in radius discards a card of the same color, if able."
        r.card("tali-gold", IceBlast(heroOnly = false))
        // Ice Shard (Handicap): "Target a hero adjacent to you. If the target discards a card to defend,
        // After the attack: Another enemy hero in radius discards a card of the same color, if able."
        r.card("tali-handicap", IceBlast(heroOnly = true))
        // Commune with Spirits: "A friendly hero in play names a color; you may discard a card of that
        // color. Perform the primary action on a card of that color in your discard."
        r.card("tali-silver", Commune)
        // Winter Dagger / Winter Spear / Winter Scepter: "Target a unit adjacent to you. +3 Attack if this
        // card is not in the discard."
        r.card("tali-red-1", Winter)
        r.card("tali-red-2a", Winter)
        r.card("tali-red-3a", Winter)
        // Spirit Wolf: "Choose one — • If this card is not in the discard, target a unit in range. • Target
        // a hero adjacent to you." Spirit Bear: "Choose one, or both, on different targets — • … • …"
        r.card("tali-red-2b", Spirit(both = false))
        r.card("tali-red-3b", Spirit(both = true))
        // Glacial Barrier: "Place an Ice token in radius. Enemy heroes have -1 Initiative for every Ice
        // token they are adjacent to." Wall of Frost / Pack Ice: "Place up to 2 (3) Ice tokens in radius. …"
        r.card("tali-green-1", Ice(count = 1, upTo = false))
        r.card("tali-green-2a", Ice(count = 2, upTo = true))
        r.card("tali-green-3a", Ice(count = 3, upTo = true))
        // Ancestral Totem: "Place a Totem token in range. If a friendly melee minion adjacent to a Totem
        // would be defeated, remove the Totem instead." Venerated Totem: "… If a friendly minion adjacent to
        // a Totem would be defeated, remove the Totem instead. Totem tokens are immune to enemy actions."
        r.card("tali-green-2b", Totem(venerated = false))
        r.card("tali-green-3b", Totem(venerated = true))
        // Cold Snap / Snowstorm: "Choose a direction. Move each enemy unit in radius 1 space in that
        // direction, if able." Blizzard: "… If this card is in the discard, End of turn: May repeat once."
        r.card("tali-blue-1", Snow(blizzard = false))
        r.card("tali-blue-2a", Snow(blizzard = false))
        r.card("tali-blue-3a", Snow(blizzard = true))
        // Guardian Spirit: "Up to 1 friendly hero in radius may retrieve a discarded card. If "Guardian
        // Spirit" is in the discard, retrieve it." Warrior Spirit: "… If "Warrior Spirit" is in the
        // discard, you may retrieve a discarded card."
        r.card("tali-blue-2b", Guardian(warrior = false))
        r.card("tali-blue-3b", Guardian(warrior = true))
        // Reign of Winter (Ultimate): "Each time after you defeat a minion with your basic attack, an enemy
        // hero in radius discards a card of the color of your choice, if able."
        r.card(ULTIMATE, ReignOfWinter)
    }

    private fun colourName(c: CardColor): String = c.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun ActionScope.inDiscard(): Boolean = hero.card(cardId)?.zone == Zone.DISCARD

    // ---- tokens --------------------------------------------------------------------------------

    private object IceToken : Behavior {
        override fun statDelta(g: Game, src: Src, q: StatQuery): Int {
            if (q.stat != Stat.INITIATIVE) return 0
            val owner = src.owner ?: return 0
            val at = src.piece?.at ?: return 0
            if (!g.isHero(q.unitId) || !g.isEnemy(owner, q.unitId)) return 0
            val p = g.posOf(q.unitId) ?: return 0
            return if (g.distance(p, at) == 1) -1 else 0
        }
    }

    private object TotemToken : Behavior {
        override fun replace(g: Game, src: Src, r: Replacement): ReplacementResult {
            if (r !is Replacement.MinionDefeat) return ReplacementResult()
            val owner = src.owner ?: return ReplacementResult()
            val piece = src.piece ?: return ReplacementResult()
            val at = piece.at ?: return ReplacementResult()
            val m = g.state.minion(r.minionId) ?: return ReplacementResult()
            val mAt = m.at ?: return ReplacementResult()
            if (m.team != g.teamOf(owner) || g.distance(at, mAt) != 1) return ReplacementResult()
            if (piece.data[VARIANT] != VENERATED && m.type != MinionType.MELEE) return ReplacementResult()
            // FAQ: "no minion is defeated and no coins are gained."
            g.note("${g.name(r.minionId)} is not defeated: the Totem is removed instead.")
            g.removePiece(piece.id)
            return ReplacementResult(DefeatOutcome.PREVENTED, handled = true)
        }

        override fun immune(g: Game, src: Src, q: AffectQuery): Boolean {
            val piece = src.piece ?: return false
            if (q.targetId != piece.id || piece.data[VARIANT] != VENERATED) return false
            val owner = src.owner ?: return false
            val actor = q.actorId ?: return false
            return g.isEnemy(owner, actor)
        }
    }

    /**
     * Place a token of [kind] into an empty space of [area] (p.18 "Place"): from the supply, or, when it
     * is used up, first remove one of Tali's tokens of that kind from the board (never one placed by this
     * action). [optional] for "up to". Returns false when nothing was placed.
     */
    private fun ActionScope.placeOwnToken(
        kind: String,
        area: () -> List<Hex>,
        prompt: String,
        optional: Boolean,
        placedNow: MutableList<String>,
    ): Boolean {
        val supply = g.data.registry.tokenKind(kind)?.supply ?: Int.MAX_VALUE
        val onBoard = g.state.pieces.filter { it.kind == kind && it.at != null && it.owner == heroId }.map { it.id }
        val recycle = onBoard.size >= supply
        if (recycle) {
            val recyclable = onBoard.filter { it !in placedNow }
            if (recyclable.isEmpty()) return false
            val freed = recyclable.mapNotNull { g.posOf(it) }.toSet()
            if (area().none { g.isEmpty(it) || it in freed }) return false
            if (optional &&
                !confirm(
                    "Place a ${g.data.registry.tokenKind(
                        kind,
                    )?.label} token (one already on the board is removed first)?",
                )
            ) {
                return false
            }
            g.removePiece(chooseToken("Remove one of your tokens from the board first", recyclable)!!)
        }
        val options = area().filter { g.isEmpty(it) }
        if (options.isEmpty()) return false
        val h = chooseSpace(prompt, options, optional = optional && !recycle) ?: return false
        val id = g.placeToken(kind, heroId, h) ?: return false
        placedNow += id
        return true
    }

    // ---- Ice Blast / Ice Shard ----------------------------------------------------------------

    private class IceBlast(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "If the target discards a card to defend, After the attack: Another enemy hero in radius
            // discards a card of the same color, if able."
            val defended = r.defenseCard ?: return
            val colour = a.g.card(defended).color
            val targetHero = a.g.heroBodyOf(t)
            val victims = a.unitsWithin(a.radius).filter { id ->
                a.g.isHero(id) && a.isEnemy(id) && a.g.heroBodyOf(id) != targetHero && a.canTarget(id)
            }
            if (victims.isEmpty()) return
            val v = a.target(
                a.chooseUnit("Another enemy hero in radius discards a ${colourName(colour)} card", victims)!!,
            )
            a.g.forceDiscard(
                v,
                a.heroId,
                orDefeated = false,
                sourceCardId = a.cardId,
                what = "a ${colourName(colour)} card",
            ) {
                it.color == colour
            }
        }
    }

    // ---- Commune with Spirits -----------------------------------------------------------------

    private object Commune : CardScript {
        override fun primary(a: ActionScope) {
            // "A friendly hero in play names a color" — FAQ: with the whole team defeated or immune, no
            // friendly hero can be chosen and the card does nothing.
            val friends = a.g.unitsOnBoard().filter { a.g.isHero(it) && a.isFriendly(it) && a.canTarget(it) }
            if (friends.isEmpty()) a.fizzle("no friendly hero in play")
            val f = a.target(a.chooseUnit("A friendly hero in play names a color", friends)!!)
            val namer = a.g.heroBodyOf(f)!!
            val colour = COLOURS[
                a.chooseOption(
                    "Name a color for ${a.g.heroName(a.heroId)}",
                    COLOURS.map(::colourName),
                    chooser = namer,
                )!!,
            ]
            a.g.note("${a.g.heroName(namer)} names ${colourName(colour).lowercase()}.")
            // "you may discard a card of that color."
            val inHand = a.g.hand(a.heroId).filter { a.g.cardColorOf(a.heroId, it) == colour }
            if (inHand.isNotEmpty()) {
                a.chooseCard("You may discard a ${colourName(colour)} card", inHand, optional = true)?.let {
                    a.g.discard(a.heroId, it)
                }
            }
            // "Perform the primary action on a card of that color in your discard." (mandatory)
            val cards = a.g.discardPile(a.heroId).filter {
                a.g.cardColorOf(a.heroId, it) == colour && performable(a, it)
            }
            if (cards.isEmpty()) a.fizzle("no ${colourName(colour).lowercase()} card in the discard to perform")
            val c = a.chooseCard("Perform the primary action of a ${colourName(colour)} card in your discard", cards)!!
            a.g.performPrimary(a.heroId, c)
        }

        private fun performable(a: ActionScope, cardId: String): Boolean {
            val kind = primaryKind(a.g.card(cardId))
            if (kind == ActionKind.DEFENSE || kind == ActionKind.NONE) return false
            if (a.g.data.registry.script(cardId)?.hasPrimary != true) return false
            return a.g.actionAllowed(a.heroId, cardId, kind, true) && a.g.primaryPossible(a.heroId, cardId)
        }
    }

    // ---- red ----------------------------------------------------------------------------------

    private object Winter : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            // "+3 Attack if this card is not in the discard." (card text: not an item, p.16)
            a.attack(t, bonus = { if (a.inDiscard()) 0 else 3 })
        }
    }

    private class Spirit(private val both: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // Bullet A: "If this card is not in the discard, target a unit in range." Bullet B: "Target a
            // hero adjacent to you." The card is ranged, so both attacks are (RULINGS 19).
            val bulletA = { id: String -> !a.inDiscard() && a.inRange(id) }
            val bulletB = { id: String -> a.g.isHero(id) && a.isAdjacent(id) }
            val enemies = a.g.unitsOnBoard().filter { a.isEnemy(it) && a.canTarget(it) }
            val forA = enemies.filter(bulletA)
            val forB = enemies.filter(bulletB)
            val bothPossible = both && forA.any { x -> forB.any { y -> y != x } }
            val labels = listOf("Target a unit in range", "Target a hero adjacent to you")
            val choices = buildList {
                if (forA.isNotEmpty()) add(listOf(0))
                if (forB.isNotEmpty()) add(listOf(1))
                if (bothPossible) {
                    add(listOf(0, 1))
                    add(listOf(1, 0))
                }
            }
            if (choices.isEmpty()) a.fizzle("no target")
            val texts = choices.map { order ->
                if (order.size ==
                    1
                ) {
                    labels[order.single()]
                } else {
                    "Both: ${labels[order[0]]}, then ${labels[order[1]].lowercase()}"
                }
            }
            val order = choices[
                if (choices.size ==
                    1
                ) {
                    0
                } else {
                    a.chooseOption(if (both) "Choose one, or both" else "Choose one", texts)!!
                },
            ]
            // "on different targets": the second attack may not target the first one's target.
            var first: String? = null
            for (bullet in order) {
                val other = first
                val valid: (String) -> Boolean = { id ->
                    id != other && (
                        if (bullet ==
                            0
                        ) {
                            bulletA(id)
                        } else {
                            bulletB(id)
                        }
                        )
                }
                val t = a.attackTarget(labels[bullet], valid = valid) ?: return
                a.attack(t, valid = valid)
                if (first == null) first = t
                if (a.hero.at == null) return
            }
        }
    }

    // ---- green --------------------------------------------------------------------------------

    private class Ice(private val count: Int, private val upTo: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val spaces = { a.g.within(a.pos, a.radius) }
            val placed = ArrayList<String>()
            for (i in 1..count) {
                val prompt = if (count ==
                    1
                ) {
                    "Place an Ice token in radius"
                } else {
                    "Place up to $count Ice tokens in radius ($i)"
                }
                val ok = a.placeOwnToken(ICE, spaces, prompt, optional = upTo, placedNow = placed)
                if (!ok) {
                    // "Place an Ice token in radius" is mandatory (p.10); "up to" is not.
                    if (!upTo) a.fizzle("no space for an Ice token")
                    return
                }
            }
        }
    }

    private class Totem(private val venerated: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val spaces = { a.g.within(a.pos, a.range) }
            val placed = ArrayList<String>()
            if (!a.placeOwnToken(TOTEM, spaces, "Place a Totem token in range", optional = false, placedNow = placed)) {
                a.fizzle("no space for the Totem")
            }
            // The token keeps the rules of the card that placed it (p.19 "it retains all its properties").
            val piece = a.g.state.piece(placed.single())!!
            piece.data[VARIANT] = if (venerated) VENERATED else "ancestral"
            a.g.touch()
        }
    }

    // ---- blue ---------------------------------------------------------------------------------

    private val DIRECTION_NAMES = listOf("east", "north-east", "north-west", "west", "south-west", "south-east")

    private class Snow(private val blizzard: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Choose a direction. Move each enemy unit in radius 1 space in that direction, if able."
            val dir = a.chooseOption("Choose a direction", DIRECTION_NAMES)!!
            val units = a.unitsWithin(a.radius).filter { a.isEnemy(it) && a.canTarget(it) }
            // [APP] All move "at once": a unit blocked only by another moving unit moves too (rulings/tali.md 4).
            val left = units.toMutableList()
            var progress = true
            while (progress && left.isNotEmpty()) {
                progress = false
                for (id in left.toList()) {
                    val spec = MoveSpec(id, 1, 1, straightLine = true, directions = setOf(dir))
                    if (!a.g.canDisplace(id, Displacement.MOVE, a.heroId) || !a.g.canMove(spec)) continue
                    a.target(id)
                    a.g.move(
                        spec,
                        a.heroId,
                        "Move ${a.g.name(id)} 1 space ${DIRECTION_NAMES[dir]}",
                        optional = false,
                        sourceCardId = a.cardId,
                    )
                    left -= id
                    progress = true
                }
            }
            // Blizzard: "If this card is in the discard, End of turn: May repeat once."
            if (blizzard && !a.isRepeat && a.inDiscard()) a.activeEffect(Timing.END_OF_TURN)
        }

        override val behavior: Behavior? = if (!blizzard) {
            null
        } else {
            object : Behavior {
                override fun applyTimed(g: Game, src: Src) {
                    val owner = src.owner ?: return
                    val card = src.cardId ?: return
                    if (g.posOf(owner) == null || g.repeatForbidden(owner)) return
                    val c = g.ask(
                        owner,
                        DecisionKind.CONFIRM,
                        "End of turn: repeat Blizzard once?",
                        listOf(Choice.Yes, Choice.Decline("Don't repeat")),
                        card,
                        auto = false,
                    )
                    if (c is Choice.Yes) g.performAction(owner, card, ActionKind.SKILL, primary = true, isRepeat = true)
                }
            }
        }
    }

    private class Guardian(private val warrior: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Up to 1 friendly hero in radius may retrieve a discarded card."
            val friends = a.unitsWithin(a.radius).filter { id ->
                a.g.isHero(id) && a.isFriendly(id) && a.canTarget(id) &&
                    a.g.discardPile(a.g.heroBodyOf(id)!!).isNotEmpty()
            }
            if (friends.isNotEmpty()) {
                val f = a.chooseUnit(
                    "Up to 1 friendly hero in radius may retrieve a discarded card",
                    friends,
                    optional = true,
                )
                if (f != null) a.mayRetrieve(who = a.g.heroBodyOf(a.target(f))!!)
            }
            if (!a.inDiscard()) return
            if (warrior) {
                // "If "Warrior Spirit" is in the discard, you may retrieve a discarded card."
                a.mayRetrieve()
            } else {
                // "If "Guardian Spirit" is in the discard, retrieve it."
                a.g.retrieve(a.heroId, a.cardId)
            }
        }
    }

    // ---- Reign of Winter ----------------------------------------------------------------------

    private object ReignOfWinter : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                if (t !is Trigger.MinionDefeated) return
                val owner = src.owner ?: return
                if (t.byHeroId != owner) return
                // "with your basic attack": an Attack action on a basic card (p.9), being performed now.
                val action = g.currentAction() ?: return
                if (action.heroId != owner || action.kind != ActionKind.ATTACK || !action.basic) return
                val me = g.posOf(owner) ?: return
                val radius = g.cardValue(owner, ULTIMATE, Stat.RADIUS) ?: 0
                val victims = g.unitsOnBoard().filter { id ->
                    g.isHero(id) && g.isEnemy(owner, id) && g.posOf(id)!!.let { g.distance(me, it) in 1..radius } &&
                        g.canTarget(id, owner, ActionKind.ATTACK, action.cardId)
                }
                if (victims.isEmpty()) return
                val v = (
                    g.ask(
                        owner,
                        DecisionKind.CHOOSE_UNIT,
                        "Reign of Winter: an enemy hero in radius discards a card of the color of your choice",
                        victims.map { Choice.Unit(it) },
                        ULTIMATE,
                    ) as Choice.Unit
                    ).unitId
                val pick = g.ask(
                    owner,
                    DecisionKind.CHOOSE_OPTION,
                    "Choose the color ${g.name(v)} discards",
                    COLOURS.mapIndexed { i, c -> Choice.Option(i, colourName(c)) },
                    ULTIMATE,
                ) as Choice.Option
                val colour = COLOURS[pick.index]
                action.target(v)
                g.forceDiscard(
                    v,
                    owner,
                    orDefeated = false,
                    sourceCardId = ULTIMATE,
                    what = "a ${colourName(colour)} card",
                ) {
                    it.color == colour
                }
            }
        }
    }
}
