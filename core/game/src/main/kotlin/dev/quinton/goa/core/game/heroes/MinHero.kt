package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.AffectQuery
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.chooseDistance
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.pushAway
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.DefenseScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.actionAllowed
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.canTarget
import dev.quinton.goa.core.game.engine.cardColorOf
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isImmune
import dev.quinton.goa.core.game.engine.moveCard
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.primaryPossible
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.removePiece
import dev.quinton.goa.core.game.engine.repeatForbidden
import dev.quinton.goa.core.game.engine.resolvedCards
import dev.quinton.goa.core.game.engine.secondaryAttackTargets
import dev.quinton.goa.core.game.engine.sortedHexes
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Min the Dragonmonk. Card text quoted verbatim above each registration (`catalog/heroes/min.json`);
 * `MinTest` follows it clause by clause with the FAQ. Readings the text leaves open are in
 * `core/game/rulings/min.md`.
 */
object MinHero : HeroModule {
    override val heroId: String = "min"
    override val complete: Boolean = true

    const val SMOKE: String = "token_smoke_bomb"
    const val BLAST: String = "token_blast"
    const val DUD: String = "token_dud"
    const val GRENADE: String = "token_grenade"
    private const val GRENADE_ID = "grenade"
    private const val MINE_CARD = "card"

    override fun register(r: Registry) {
        // [APP] Supplies (rulings/min.md 9): "the Smoke bomb token", one Grenade, 2 blasts and 2 duds
        // (Minefield places 2 blasts, Cluster Mine 2 duds). Both mine kinds are labelled "Mine": facedown,
        // their kind is hidden from the other team, events included.
        r.token(TokenKind(SMOKE, "Smoke bomb", supply = 1))
        r.token(TokenKind(GRENADE, "Grenade", supply = 1))
        r.token(TokenKind(BLAST, "Mine", supply = 2, passable = true))
        r.token(TokenKind(DUD, "Mine", supply = 2, passable = true))
        // "enemy heroes cannot target you or another unit if the Smoke bomb token is on a straight line
        // between that enemy hero and their target." (a property of the token, p.19)
        r.behavior(SMOKE, SmokeBomb)
        // "units may move through them; remove mine tokens after an enemy hero moves through them; for
        // every blast removed, that hero discards a card, if able."
        r.behavior(BLAST, Mine(blast = true))
        r.behavior(DUD, Mine(blast = false))

        // Fast as Lightning: "Target a unit in range. After the attack: Apply the "After the attack" text
        // of your resolved or discarded red card. (If it has radius, use that card's value.)"
        r.card("min-gold", Lightning(heroOnly = false))
        // Chop Down (Handicap): "Target a hero adjacent to you. After the attack: Apply the "After the
        // attack" text of your resolved or discarded red card."
        r.card("min-handicap", Lightning(heroOnly = true))
        // Smoke Bomb: "Place the Smoke bomb token in radius; enemy heroes cannot target you or another unit
        // if the Smoke bomb token is on a straight line between that enemy hero and their target."
        r.card("min-silver", SmokeBombCard)
        // Crane Stance: "Target a unit adjacent to you. After the attack: Push an enemy unit adjacent to you
        // up to 3 spaces." Tiger Stance: "… After the attack: You may move 1 space to a space adjacent to
        // the target. Push an enemy unit adjacent to you up to 3 spaces." Viper Stance: "… After the
        // attack: You may swap with a Smoke bomb in radius." Dragon Stance: "… You may move 1 or 2 spaces
        // to a space adjacent to the target. Push …" Cobra Stance: "… You may swap with a Smoke bomb in
        // radius; if you do, you may place the Smoke bomb into a space in radius."
        for (red in RED_CARDS) r.card(red, Stance)
        // Poof! / Vanish: "Swap with a Smoke bomb token in range; if you do, block the attack." Ruse: "Swap
        // with a Smoke bomb in range; if you do, block the attack. You may place the Smoke bomb into a
        // space in range."
        r.card("min-green-1", Poof(place = false))
        r.card("min-green-2a", Poof(place = false))
        r.card("min-green-3a", Poof(place = true))
        // Trip Mine: "Place 2 Mine tokens, 1 blast and 1 dud, facedown in radius; …" Cluster Mine: "Place 3
        // Mine tokens, 1 blast and 2 duds, …" Minefield: "Place 3 Mine tokens, 2 blasts and 1 dud, …"
        r.card("min-blue-1", Mines(blasts = 1, duds = 1))
        r.card("min-blue-2a", Mines(blasts = 1, duds = 2))
        r.card("min-blue-3a", Mines(blasts = 2, duds = 1))
        // Death Grenade: "Place a Grenade token into a space in radius. End of turn: Up to 1 enemy hero
        // adjacent to that token discards a card, or is defeated. Remove the Grenade token." Holy Death
        // Grenade: "… Up to 2 enemy heroes adjacent to that token discard a card, or are defeated. …"
        r.card("min-blue-2b", Grenade(victims = 1))
        r.card("min-blue-3b", Grenade(victims = 2))
        // Inner Strength: "This round: Double your item bonuses."
        r.card("min-green-2b", InnerStrength)
        // Perfect Self: "Choose one, or both — • This round: Double your item bonuses. • Take a Tier II card
        // from your deck and add it to your dashboard as a permanent item."
        r.card("min-green-3b", PerfectSelf)
        // Flurry of Blows (Ultimate): "Each time after you perform an attack action, you may repeat it once
        // on a different target."
        r.card("min-purple-4", FlurryOfBlows)
    }

    private val RED_CARDS = listOf("min-red-1", "min-red-2a", "min-red-2b", "min-red-3a", "min-red-3b")

    // ---- Smoke bomb ---------------------------------------------------------------------------

    private object SmokeBomb : Behavior {
        override fun forbidsTarget(g: Game, src: Src, q: dev.quinton.goa.core.game.effects.AffectQuery): Boolean {
            val owner = src.owner ?: return false
            val bomb = src.piece?.at ?: return false
            val actor = q.actorId ?: return false
            if (!g.isHero(actor) || !g.isEnemy(owner, actor) || !g.isUnit(q.targetId)) return false
            val from = g.posOf(actor) ?: return false
            val to = g.posOf(q.targetId) ?: return false
            // "on a straight line between": a space of the line from the hero to the target, both excluded.
            val line = HexGrid.straightLine(from, to) ?: return false
            return bomb in line.dropLast(1)
        }
    }

    private fun Game.smokeBomb(owner: String): String? =
        state.pieces.firstOrNull { it.kind == SMOKE && it.owner == owner && it.at != null }?.id

    /** Place the Smoke bomb into [to]: from the supply, or moved from the board (p.18 "Place", supply 1). */
    private fun Game.placeSmoke(owner: String, to: Hex) {
        val onBoard = smokeBomb(owner)
        if (onBoard != null) place(onBoard, to, owner) else placeToken(SMOKE, owner, to)
    }

    private object SmokeBombCard : CardScript {
        override fun primary(a: ActionScope) {
            val spaces = a.g.within(a.pos, a.radius).filter { a.g.isEmpty(it) }
            if (spaces.isEmpty()) a.fizzle("no empty space in radius")
            a.g.placeSmoke(a.heroId, a.chooseSpace("Place the Smoke bomb token in radius", spaces)!!)
        }
    }

    // ---- red: the stances and their "After the attack" text ----------------------------------

    private object Stance : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val r = a.attack(t)
            if (!r.connected) return
            afterTheAttack(a, a.cardId, t, a.radius)
        }
    }

    /**
     * The "After the attack" text of the red card [red], applied by the action [a] (its own, or Fast as
     * Lightning's: "If it has radius, use that card's value"). [target] is the attack's target.
     */
    private fun afterTheAttack(a: ActionScope, red: String, target: String, radius: Int) {
        when (red) {
            "min-red-1" -> pushAdjacentEnemy(a)

            "min-red-2a" -> {
                moveNextToTarget(a, target, 1)
                pushAdjacentEnemy(a)
            }

            "min-red-3a" -> {
                moveNextToTarget(a, target, 2)
                pushAdjacentEnemy(a)
            }

            "min-red-2b" -> swapWithSmoke(a, radius)

            "min-red-3b" -> if (swapWithSmoke(a, radius)) {
                // "if you do, you may place the Smoke bomb into a space in radius."
                val spaces = a.g.within(a.pos, radius).filter { a.g.isEmpty(it) }
                a.chooseSpace("You may place the Smoke bomb into a space in radius", spaces, optional = true)?.let {
                    a.g.placeSmoke(a.heroId, it)
                }
            }
        }
    }

    /** "Push an enemy unit adjacent to you up to 3 spaces." ("up to": optional, p.10.) */
    private fun pushAdjacentEnemy(a: ActionScope) {
        val enemies = a.unitsWithin(1).filter { a.isEnemy(it) && a.canTarget(it) }
        if (enemies.isEmpty()) return
        val t = a.chooseUnit("Push an enemy unit adjacent to you up to 3 spaces", enemies)!!
        a.pushAway(t, a.chooseDistance((0..3).toList(), "Push ${a.g.name(t)} how many spaces?"))
    }

    /**
     * "You may move 1 (1 or 2) space(s) to a space adjacent to the target." [APP] A target no longer on
     * the board has no adjacent spaces, so there is no move (rulings/min.md 2).
     */
    private fun moveNextToTarget(a: ActionScope, target: String, max: Int) {
        val tPos = a.g.posOf(target) ?: return
        a.moveSelf(1, max, optional = true, endFilter = { a.g.distance(it, tPos) == 1 })
    }

    /** "You may swap with a Smoke bomb in radius." Returns true when Min swapped. */
    private fun swapWithSmoke(a: ActionScope, radius: Int): Boolean {
        val bomb = a.g.smokeBomb(a.heroId) ?: return false
        val at = a.g.posOf(bomb) ?: return false
        if (a.g.distance(a.pos, at) !in 1..radius || !a.g.canSwap(a.heroId, bomb, a.heroId)) return false
        if (!a.confirm("Swap with the Smoke bomb?", decline = "Don't swap")) return false
        a.g.swap(a.heroId, bomb, a.heroId)
        return true
    }

    // ---- gold: Fast as Lightning / Chop Down --------------------------------------------------

    private class Lightning(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.rangedAttackTarget()
            } ?: return
            val r = a.attack(t)
            if (!r.connected) return
            // "Apply the "After the attack" text of your resolved or discarded red card." (faceup only:
            // a facedown card has no colour, p.10)
            val reds = (a.g.resolvedCards(a.heroId) + a.g.discardPile(a.heroId)).filter {
                a.g.cardColorOf(a.heroId, it) == CardColor.RED && it in RED_CARDS
            }
            if (reds.isEmpty()) return
            val red = a.chooseCard("Apply the After the attack text of which red card?", reds)!!
            val radius = a.g.cardValue(a.heroId, red, Stat.RADIUS) ?: 0
            afterTheAttack(a, red, t, radius)
        }
    }

    // ---- green: Poof! / Vanish / Ruse ---------------------------------------------------------

    private class Poof(private val place: Boolean) : CardScript {
        override val hasPrimary: Boolean get() = false

        override fun defense(d: DefenseScope) {
            val me = d.pos ?: return
            val bomb = d.g.smokeBomb(d.heroId)
            val bombAt = bomb?.let { d.g.posOf(it) }
            // "Swap with a Smoke bomb token in range; if you do, block the attack."
            val swapped = bomb != null && bombAt != null && d.g.distance(me, bombAt) in 1..d.range &&
                d.g.canSwap(d.heroId, bomb, d.heroId)
            if (swapped) d.g.swap(d.heroId, bomb!!, d.heroId)
            d.block(swapped)
            // Ruse: "You may place the Smoke bomb into a space in range." (a sentence of its own)
            if (!place) return
            val now = d.pos ?: return
            val spaces = d.g.within(now, d.range).filter { d.g.isEmpty(it) }
            d.chooseSpace("You may place the Smoke bomb into a space in range", spaces, optional = true)?.let {
                d.g.placeSmoke(d.heroId, it)
            }
        }
    }

    // ---- blue: mines and grenades -------------------------------------------------------------

    private class Mines(private val blasts: Int, private val duds: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val n = blasts + duds
            val area = a.g.within(a.pos, a.radius).filter { a.g.isEmpty(it) }
            // All N tokens are one mandatory step: with fewer empty spaces it cannot be done (p.10).
            if (area.size < n) a.fizzle("not enough empty spaces in radius")
            val chosen = ArrayList<Hex>()
            for (i in 1..n) {
                chosen += a.chooseSpace("Place a Mine token in radius ($i of $n)", area.filter { it !in chosen })!!
            }
            // Which are blasts: Min chooses the fewer kind among the chosen spaces.
            val fewer = if (blasts <= duds) BLAST to blasts else DUD to duds
            val marked = ArrayList<Hex>()
            repeat(fewer.second) { i ->
                val what = if (fewer.first == BLAST) "blast" else "dud"
                marked +=
                    a.chooseSpace(
                        "Which space holds a $what? (${i + 1} of ${fewer.second})",
                        chosen.filter {
                            it !in
                                marked
                        },
                    )!!
            }
            // Placed in board order, so the log does not tell the kinds apart.
            for (h in chosen.sortedHexes()) {
                val kind = if ((h in marked) == (fewer.first == BLAST)) BLAST else DUD
                val takeFrom = a.g.state.pieces.firstOrNull {
                    it.kind == kind && it.owner == a.heroId && it.at != null
                }?.id
                val id = a.g.placeToken(kind, a.heroId, h, takeFrom) ?: continue
                val piece = a.g.state.piece(id)!!
                piece.faceDown = true
                piece.data[MINE_CARD] = a.cardId
                a.g.touch()
            }
        }
    }

    private class Mine(private val blast: Boolean) : Behavior {
        override fun on(g: Game, src: Src, t: Trigger) {
            if (t !is Trigger.Moved) return
            val piece = src.piece ?: return
            val at = piece.at ?: return
            val owner = src.owner ?: return
            val hero = g.heroBodyOf(t.id) ?: return
            if (!g.isEnemy(owner, hero) || at !in t.path) return
            // "remove mine tokens after an enemy hero moves through them; for every blast removed, that hero
            // discards a card, if able."
            g.removePiece(piece.id)
            g.note("The Mine at $at was a ${if (blast) "blast" else "dud"}.")
            if (!blast) return
            // [APP] A hero immune to Min's skill actions is not affected by its discard (rulings/min.md 6).
            val q = AffectQuery(hero, owner, ActionKind.SKILL, true, false, piece.data[MINE_CARD], fromEffect = true)
            if (g.isImmune(q)) return
            g.forceDiscard(hero, owner, orDefeated = false, sourceCardId = piece.data[MINE_CARD])
        }
    }

    private class Grenade(private val victims: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val spaces = a.g.within(a.pos, a.radius).filter { a.g.isEmpty(it) }
            if (spaces.isEmpty()) a.fizzle("no empty space in radius")
            val h = a.chooseSpace("Place a Grenade token into a space in radius", spaces)!!
            val old = a.g.state.pieces.firstOrNull { it.kind == GRENADE && it.owner == a.heroId && it.at != null }
            val id = if (old != null) {
                a.g.place(old.id, h, a.heroId)
                old.id
            } else {
                a.g.placeToken(GRENADE, a.heroId, h) ?: a.fizzle("no Grenade token")
            }
            a.activeEffect(Timing.END_OF_TURN, data = mapOf(GRENADE_ID to id))
        }

        override val behavior: Behavior = object : Behavior {
            override fun applyTimed(g: Game, src: Src) {
                val owner = src.owner ?: return
                val card = src.cardId ?: return
                val id = src.effect?.data?.get(GRENADE_ID) ?: return
                val at = g.state.piece(id)?.at ?: return
                // "Up to N enemy hero(es) adjacent to that token discard(s) a card, or is defeated." FAQ: heroes
                // immune this turn are still immune at the end of turn and cannot be chosen.
                val chosen = ArrayList<String>()
                while (chosen.size < victims) {
                    val options = g.unitsAdjacentTo(at).filter { u ->
                        g.isHero(u) && g.isEnemy(owner, u) && u !in chosen &&
                            g.canTarget(u, owner, ActionKind.SKILL, card)
                    }
                    if (options.isEmpty()) break
                    val c = g.ask(
                        owner,
                        DecisionKind.CHOOSE_UNIT,
                        if (victims == 1) {
                            "End of turn: up to 1 enemy hero next to the Grenade discards a card, or is defeated"
                        } else {
                            "End of turn: up to $victims enemy heroes next to the Grenade discard a card, or are defeated"
                        },
                        options.map { Choice.Unit(it) as Choice } +
                            Choice.Decline(if (chosen.isEmpty()) "None" else "Done"),
                        card,
                    )
                    if (c !is Choice.Unit) break
                    chosen += c.unitId
                }
                for (v in chosen) g.forceDiscard(v, owner, orDefeated = true, sourceCardId = card)
                // "Remove the Grenade token."
                if (g.state.piece(id)?.at != null) g.removePiece(id)
            }
        }
    }

    // ---- green alternates: item bonuses ------------------------------------------------------

    /** "This round: Double your item bonuses." */
    private object DoubleItems : Behavior {
        override fun itemMultiplier(g: Game, src: Src, heroId: String): Int = if (heroId == src.owner) 2 else 1
    }

    private object InnerStrength : CardScript {
        override fun primary(a: ActionScope) {
            a.activeEffect(Timing.THIS_ROUND)
        }

        override val behavior: Behavior = DoubleItems
    }

    private object PerfectSelf : CardScript {
        override fun primary(a: ActionScope) {
            val tierTwo = a.hero.cards.filter { it.zone == Zone.DECK && a.g.card(it.id).tier == 2 }.map { it.id }
            val labels = listOf(
                "This round: Double your item bonuses",
                "Take a Tier II card from your deck as a permanent item",
                "Both",
            )
            // "Choose one, or both —": the second bullet needs a Tier II card in the deck.
            val options = if (tierTwo.isEmpty()) listOf(0) else listOf(0, 1, 2)
            val pick = options[
                if (options.size ==
                    1
                ) {
                    0
                } else {
                    a.chooseOption("Choose one, or both", options.map { labels[it] })!!
                },
            ]
            if (pick == 0 || pick == 2) a.activeEffect(Timing.THIS_ROUND)
            if (pick == 1 || pick == 2) {
                val c = a.chooseCard("Take a Tier II card from your deck as a permanent item", tierTwo)!!
                a.g.moveCard(a.heroId, c, Zone.ITEM)
                a.g.note("${a.g.heroName(a.heroId)} adds ${a.g.cardName(c)} to the dashboard as an item.")
            }
        }

        override val behavior: Behavior = DoubleItems
    }

    // ---- Flurry of Blows ----------------------------------------------------------------------

    private object FlurryOfBlows : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun on(g: Game, src: Src, t: Trigger) {
                if (t !is Trigger.AfterAction) return
                val owner = src.owner ?: return
                // "Each time after you perform an attack action": not a repeated instance (p.10).
                if (t.heroId != owner || t.kind != ActionKind.ATTACK || t.repeat) return
                val card = t.cardId ?: return
                if (g.posOf(owner) == null || g.repeatForbidden(owner)) return
                if (!g.actionAllowed(owner, card, ActionKind.ATTACK, t.primary)) return
                // "on a different target": every unit that action targeted is excluded (RULINGS 37).
                val excluded = t.targets.toSet()
                val possible = if (t.primary) {
                    g.primaryPossible(owner, card, excluded)
                } else {
                    g.secondaryAttackTargets(owner, card).any { it !in excluded }
                }
                if (!possible) return
                val c = g.ask(
                    owner,
                    DecisionKind.CONFIRM,
                    "Flurry of Blows: repeat the attack once on a different target?",
                    listOf(Choice.Yes, Choice.Decline("Don't repeat")),
                    "min-purple-4",
                    auto = false,
                )
                if (c !is Choice.Yes) return
                g.performAction(
                    owner,
                    card,
                    ActionKind.ATTACK,
                    primary = t.primary,
                    isRepeat = true,
                    excluded = excluded,
                )
            }
        }
    }
}
