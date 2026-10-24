package dev.quinton.goa.core.game.heroes

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
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.inRadiusOf
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.DefenseScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.isAttackCard
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.removePiece
import dev.quinton.goa.core.game.engine.retrieve
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.swapCards
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex

/**
 * Emmitt the Traveler. Card text quoted verbatim above each registration (`catalog/heroes/emmitt.json`);
 * `EmmittTest` follows it clause by clause. Rulings: `rulings/emmitt.md`.
 */
object EmmittHero : HeroModule {
    override val heroId: String = "emmitt"
    override val complete: Boolean = true

    const val GLITCH: String = "token_glitch"

    /** [dev.quinton.goa.core.game.engine.TurnRecord.notes] prefix: the unit was displaced this turn. */
    private const val DISPLACED = "emmitt.displaced:"

    override fun register(r: Registry) {
        // [APP] supply 3: the most Glitch tokens any card places (rulings/emmitt.md).
        r.token(TokenKind(GLITCH, "Glitch", supply = 3))
        // Tracks "remained in the same space since the last turn" (moves, pushes, placements, swaps).
        r.heroRule(heroId, Displacements)
        // Reverse Time: "Target a unit adjacent to you. After the attack: Next turn: Heroes with lower
        // initiative act before heroes with higher initiative; this effect ignores immunity. (Resolve
        // ties as normal.)"
        r.card("emmitt-gold", ReverseTime(heroOnly = false))
        // Tear in Time (Handicap): "Target a hero adjacent to you. After the attack: Next Turn: Heroes
        // with lower initiative act before heroes with higher initiative. This effect ignores immunity."
        r.card("emmitt-handicap", ReverseTime(heroOnly = true))
        // Unstable Timeline: "Place 2 Glitch tokens in radius, with at least two spaces between each
        // token; if used as a defense, place 3 tokens instead. An enemy hero in play chooses one of the
        // Glitch tokens; you swap with that token. End of turn: Remove all Glitch tokens."
        r.card("emmitt-silver", UnstableTimeline)
        // Temporal Punch / Slam / Judgment: "Target a unit adjacent to you; when defending, the enemy
        // hero must use the Initiative value of their card and items instead of the Defense value of
        // their card and items. (Minion defense modifiers are applied as normal.)"
        r.card("emmitt-red-1", script { temporal(this) })
        r.card("emmitt-red-2a", script { temporal(this) })
        r.card("emmitt-red-3a", script { temporal(this) })
        // Flashback / Déjà Vu: "Target a unit adjacent to you. After the attack: You may place 3 (2)
        // Glitch tokens in radius, with at least two spaces between each token; if you do, up to 1 enemy
        // hero in radius swaps with a Glitch token of their choice. End of turn: Remove all Glitch tokens."
        r.card("emmitt-red-2b", Flashback(tokens = 3))
        r.card("emmitt-red-3b", Flashback(tokens = 2))
        // Time Walk / Fast Forward: "Move an enemy hero in range, who remained in the same space since
        // the last turn, 2 spaces in a straight line."
        r.card(
            "emmitt-green-1",
            script {
                if (!timeWalk(this)) fizzle("no enemy hero that remained in place can be moved")
            },
        )
        r.card(
            "emmitt-green-2a",
            script {
                if (!timeWalk(this)) fizzle("no enemy hero that remained in place can be moved")
            },
        )
        // Time Snare / Time Trap: "An enemy hero in range who has already resolved a card this turn
        // discards a card, if able." Time Bomb: "… discards a card, or is defeated."
        r.card("emmitt-blue-1", script { timeSnare(this, orDefeated = false) })
        r.card("emmitt-blue-2a", script { timeSnare(this, orDefeated = false) })
        r.card("emmitt-blue-3a", script { timeSnare(this, orDefeated = true) })
        // Time Loop: "Swap with an enemy hero in range who has already resolved a card this turn."
        r.card("emmitt-blue-2b", script { if (!timeLoop(this)) fizzle("no enemy hero in range has resolved a card") })
        // Time Capsule: "You, and friendly heroes in radius, may retrieve all cards discarded this turn."
        r.card("emmitt-green-2b", script { if (!timeCapsule(this)) fizzle("no card was discarded this turn") })
        // Back to the Future: "Choose one — • Place a unit in range into the space where that unit was at
        // the start of this turn. • Move an enemy hero in range, who remained in the same space since the
        // last turn, 2 spaces in a straight line."
        r.card("emmitt-green-3a", BackToTheFuture)
        // Future Proof: "Choose one — • You, and friendly heroes in radius, may retrieve all cards
        // discarded this turn. • This turn: Friendly heroes in radius are immune to enemy actions."
        r.card("emmitt-green-3b", FutureProof)
        // Time Warp: "Choose one — • Swap with an enemy hero in range who has already resolved a card
        // this turn. • An enemy hero in range swaps their unresolved card with one of their resolved
        // cards of their choice."
        r.card("emmitt-blue-3b", TimeWarp)
        // Alternative Timelines (Ultimate): "You may play two cards each turn; if you do, after the cards
        // are revealed, retrieve one of your unresolved cards."
        r.card("emmitt-purple-4", AlternativeTimelines)
    }

    private fun script(primary: ActionScope.() -> Unit): CardScript = dev.quinton.goa.core.game.effects.script(
        primary = primary,
    )

    // ---- shared clauses --------------------------------------------------------------------------

    private fun ActionScope.inRangeOfMe(id: String, n: Int = range): Boolean {
        val me = hero.at ?: return false
        val p = g.posOf(id) ?: return false
        return g.distance(me, p) in 1..n
    }

    private fun ActionScope.enemyHeroesInRange(n: Int = range): List<String> =
        g.unitsOnBoard().filter { g.isHero(it) && isEnemy(it) && inRangeOfMe(it, n) && canTarget(it) }

    /** "who has already resolved a card this turn" (p.7: the card is placed on its Turn slot). */
    private fun Game.resolvedThisTurn(id: String): Boolean = heroBodyOf(id) in state.turnRecord.resolved

    /**
     * "who remained in the same space since the last turn": on the space it held when this turn began
     * and not moved, pushed, placed or swapped since ([APP] rulings/emmitt.md).
     */
    fun remained(g: Game, id: String): Boolean {
        val start = g.state.turnRecord.startPositions[id] ?: return false
        return g.posOf(id) == start && "$DISPLACED$id" !in g.state.turnRecord.notes
    }

    private object Displacements : Behavior {
        override fun on(g: Game, src: Src, t: Trigger) {
            val ids = when (t) {
                is Trigger.Moved -> listOf(t.id)
                is Trigger.Placed -> listOf(t.id)
                is Trigger.Swapped -> listOf(t.a, t.b)
                else -> return
            }
            for (id in ids) g.state.turnRecord.notes += "$DISPLACED$id"
        }
    }

    private fun temporal(a: ActionScope) {
        val t = a.adjacentAttackTarget() ?: return
        a.attack(t, defenseFromInitiative = true)
    }

    /** Time Walk's text; false when no enemy hero qualifies (nothing was done). */
    private fun timeWalk(a: ActionScope): Boolean {
        val heroes = a.enemyHeroesInRange().filter { remained(a.g, it) && a.canMoveOther(it, 2, straightLine = true) }
        if (heroes.isEmpty()) return false
        val h = a.chooseUnit("Move an enemy hero who remained in the same space 2 spaces in a straight line", heroes)!!
        a.moveOther(h, 2, straightLine = true)
        return true
    }

    private fun timeSnare(a: ActionScope, orDefeated: Boolean) {
        val heroes = a.enemyHeroesInRange().filter { a.g.resolvedThisTurn(it) }
        if (heroes.isEmpty()) a.fizzle("no enemy hero in range has resolved a card this turn")
        val v = a.chooseUnit("An enemy hero who has already resolved a card this turn discards a card", heroes)!!
        if (orDefeated) a.discardOrDefeat(v) else a.discardIfAble(v)
    }

    private fun timeLoopTargets(a: ActionScope): List<String> =
        a.enemyHeroesInRange().filter { a.g.resolvedThisTurn(it) && a.g.canSwap(a.heroId, it, a.heroId) }

    private fun timeLoop(a: ActionScope): Boolean {
        val heroes = timeLoopTargets(a)
        if (heroes.isEmpty()) return false
        val h = a.target(a.chooseUnit("Swap with an enemy hero who has already resolved a card this turn", heroes)!!)
        a.g.swap(a.heroId, h, a.heroId)
        return true
    }

    /** Cards [heroId] discarded this turn that are still in its discard pile. */
    private fun Game.discardedThisTurn(heroId: String): List<String> {
        val pile = hero(heroId).cards.filter { it.zone == Zone.DISCARD }.map { it.id }.toSet()
        return state.turnRecord.discarded[heroId].orEmpty().filter { it in pile }.distinct()
    }

    private fun capsuleHeroes(a: ActionScope): List<String> {
        val friends = a.g.unitsOnBoard().filter { id ->
            a.g.isHero(id) && a.isFriendly(id) && a.inRangeOfMe(id, a.radius) && a.canTarget(id)
        }.mapNotNull { a.g.heroBodyOf(it) }
        return (listOf(a.heroId) + friends).distinct().filter { a.g.discardedThisTurn(it).isNotEmpty() }
    }

    /** "You, and friendly heroes in radius, may retrieve all cards discarded this turn." */
    private fun timeCapsule(a: ActionScope): Boolean {
        val heroes = capsuleHeroes(a)
        if (heroes.isEmpty()) return false
        for (h in heroes) {
            val cards = a.g.discardedThisTurn(h)
            if (cards.isEmpty()) continue
            // Each hero decides for itself: "may" is theirs ([APP] rulings/emmitt.md).
            if (!a.confirm("Retrieve all ${cards.size} card(s) you discarded this turn?", chooser = h)) continue
            for (c in cards) a.g.retrieve(h, c)
        }
        return true
    }

    // ---- Glitch tokens -----------------------------------------------------------------------------

    private const val GLITCH_SPACING = 3

    /** "with at least two spaces between each token": every two tokens at distance 3 or more ([APP]). */
    private fun spaced(g: Game, placed: List<Hex>, h: Hex) = placed.all { g.distance(it, h) >= GLITCH_SPACING }

    private fun completable(g: Game, candidates: List<Hex>, placed: List<Hex>, need: Int): Boolean {
        if (need == 0) return true
        return candidates.any { c ->
            c !in placed && spaced(g, placed, c) &&
                completable(g, candidates, placed + c, need - 1)
        }
    }

    private fun glitchSpaces(g: Game, heroId: String, radius: Int): List<Hex> {
        val me = g.posOf(heroId) ?: return emptyList()
        return g.within(me, radius).filter { g.isEmpty(it) }
    }

    fun canPlaceGlitches(g: Game, heroId: String, radius: Int, n: Int): Boolean =
        completable(g, glitchSpaces(g, heroId, radius), emptyList(), n)

    /**
     * Place [n] Glitch tokens in radius, pairwise 3+ apart (all or none: a set that can't be completed is
     * never started). p.18: with too few in the supply, tokens of the same type are taken from the board
     * first. Returns the tokens placed.
     */
    private fun placeGlitches(g: Game, heroId: String, radius: Int, n: Int, source: String?): List<String> {
        val onBoard = g.state.pieces.filter { it.kind == GLITCH && it.at != null }
        val recycle = onBoard.size + n - GLITCH_SUPPLY
        repeat(maxOf(0, minOf(recycle, onBoard.size))) {
            val left = g.state.pieces.filter { it.kind == GLITCH && it.at != null }.map { it.id }
            val c = g.ask(
                heroId,
                DecisionKind.CHOOSE_TOKEN,
                "Not enough Glitch tokens: take one from the board",
                left.map { Choice.Token(it) },
                source,
            ) as Choice.Token
            g.removePiece(c.tokenId)
        }
        val candidates = glitchSpaces(g, heroId, radius)
        if (!completable(g, candidates, emptyList(), n)) g.fizzle("no room for $n Glitch tokens")
        val placed = ArrayList<Hex>()
        val ids = ArrayList<String>()
        for (i in 1..n) {
            val opts = candidates.filter { c ->
                c !in placed && spaced(g, placed, c) && completable(g, candidates, placed + c, n - i)
            }
            val c = g.ask(
                heroId,
                DecisionKind.CHOOSE_SPACE,
                "Place Glitch token $i of $n in radius (at least two spaces between tokens)",
                opts.sortedWith(dev.quinton.goa.core.game.engine.HEX_ORDER).map { Choice.Space(it) },
                source,
            ) as Choice.Space
            placed += c.hex
            g.placeToken(GLITCH, heroId, c.hex)?.let { ids += it }
        }
        return ids
    }

    private const val GLITCH_SUPPLY = 3

    private fun glitchTokens(g: Game): List<String> = g.state.pieces.filter {
        it.kind == GLITCH && it.at != null
    }.map { it.id }

    /** "End of turn: Remove all Glitch tokens." */
    private object RemoveGlitches : Behavior {
        override fun applyTimed(g: Game, src: Src) {
            for (id in glitchTokens(g)) g.removePiece(id)
        }
    }

    /** Unstable Timeline's text after placing: an enemy hero in play chooses; Emmitt swaps with that token. */
    private fun timelineSwap(g: Game, heroId: String, source: String) {
        val team = g.hero(heroId).team
        val enemies = g.state.heroes.filter { h ->
            h.team != team && (h.at != null || g.state.pieces.any { it.heroBody == h.id && it.at != null })
        }.map { it.id }
        if (enemies.isEmpty()) g.fizzle("no enemy hero in play")
        // Choosing is not targeting: immunity does not matter (rulings/emmitt.md).
        val chooser = (
            g.ask(
                heroId,
                DecisionKind.CHOOSE_UNIT,
                "Choose the enemy hero who picks a Glitch token",
                enemies.map { Choice.Unit(it) },
                source,
            ) as Choice.Unit
            ).unitId
        val tokens = glitchTokens(g).filter { g.canSwap(heroId, it, heroId) }
        if (tokens.isEmpty()) g.fizzle("Emmitt cannot swap with a Glitch token")
        val t = (
            g.ask(
                chooser,
                DecisionKind.CHOOSE_TOKEN,
                "Choose the Glitch token Emmitt swaps with",
                tokens.map { Choice.Token(it) },
                source,
            ) as Choice.Token
            ).tokenId
        g.swap(heroId, t, heroId)
    }

    // ---- cards -------------------------------------------------------------------------------------

    private class ReverseTime(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            val res = a.attack(t)
            if (!res.connected) return
            a.activeEffect(Timing.NEXT_TURN)
        }

        /** "Heroes with lower initiative act before heroes with higher initiative; this effect ignores immunity." */
        override val behavior: Behavior = object : Behavior {
            override fun reversesInitiative(g: Game, src: Src): Boolean = true
        }
    }

    private object UnstableTimeline : CardScript {
        override fun primary(a: ActionScope) {
            placeGlitches(a.g, a.heroId, a.radius, 2, a.cardId)
            timelineSwap(a.g, a.heroId, a.cardId)
            a.activeEffect(Timing.END_OF_TURN)
        }

        override fun defense(d: DefenseScope) {
            // "if used as a defense, place 3 tokens instead." The defense value still counts if this stops.
            placeGlitches(d.g, d.heroId, d.radius, 3, d.cardId)
            timelineSwap(d.g, d.heroId, d.cardId)
            d.activeEffect(Timing.END_OF_TURN)
        }

        override val behavior: Behavior = RemoveGlitches
    }

    private class Flashback(private val tokens: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val t = a.adjacentAttackTarget() ?: return
            val res = a.attack(t)
            if (!res.connected) return
            if (!canPlaceGlitches(a.g, a.heroId, a.radius, tokens)) return
            if (!a.confirm("You may place $tokens Glitch tokens in radius")) return
            placeGlitches(a.g, a.heroId, a.radius, tokens, a.cardId)
            a.activeEffect(Timing.END_OF_TURN)
            // "if you do, up to 1 enemy hero in radius swaps with a Glitch token of their choice."
            val heroes = a.enemyHeroesInRange(a.radius).filter { h ->
                glitchTokens(a.g).any { a.g.canSwap(h, it, a.heroId) }
            }
            val h =
                a.chooseUnit("Up to 1 enemy hero in radius swaps with a Glitch token", heroes, optional = true)
                    ?: return
            a.target(h)
            val tok = a.chooseToken(
                "Choose the Glitch token you swap with",
                glitchTokens(a.g).filter { a.g.canSwap(h, it, a.heroId) },
                chooser = a.g.heroBodyOf(h) ?: h,
            ) ?: return
            a.g.swap(h, tok, a.heroId)
        }

        override val behavior: Behavior = RemoveGlitches
    }

    private object BackToTheFuture : CardScript {
        override fun primary(a: ActionScope) {
            val placeable = placeTargets(a)
            val walkable = a.enemyHeroesInRange().any {
                remained(a.g, it) && a.canMoveOther(it, 2, straightLine = true)
            }
            val bullets = buildList {
                if (placeable.isNotEmpty()) {
                    add(
                        Choice.Option(0, "Place a unit in range where it was at the start of this turn"),
                    )
                }
                if (walkable) {
                    add(
                        Choice.Option(1, "Move an enemy hero who remained in place 2 spaces in a straight line"),
                    )
                }
            }
            if (bullets.isEmpty()) a.fizzle("neither option can be done")
            val pick = a.g.ask(
                a.heroId,
                DecisionKind.CHOOSE_OPTION,
                "Back to the Future: choose one",
                bullets,
                a.cardId,
            )
            if ((pick as Choice.Option).index == 1) {
                timeWalk(a)
                return
            }
            val u = a.target(
                a.chooseUnit(
                    "Place a unit in range into the space where it was at the start of this turn",
                    placeable,
                )!!,
            )
            a.g.place(u, a.g.state.turnRecord.startPositions.getValue(u)!!, a.heroId)
        }

        private fun placeTargets(a: ActionScope): List<String> = a.g.unitsOnBoard().filter { id ->
            val start = a.g.state.turnRecord.startPositions[id]
            start != null && a.inRangeOfMe(id) && a.g.posOf(id) != start && a.g.isEmpty(start, id) &&
                a.canTarget(id) && a.g.canPlace(id, a.heroId)
        }
    }

    private object FutureProof : CardScript {
        override fun primary(a: ActionScope) {
            val bullets = buildList {
                if (capsuleHeroes(
                        a,
                    ).isNotEmpty()
                ) {
                    add(
                        Choice.Option(0, "You and friendly heroes in radius may retrieve cards discarded this turn"),
                    )
                }
                add(Choice.Option(1, "This turn: friendly heroes in radius are immune to enemy actions"))
            }
            val pick = a.g.ask(a.heroId, DecisionKind.CHOOSE_OPTION, "Future Proof: choose one", bullets, a.cardId)
            if ((pick as Choice.Option).index == 0) timeCapsule(a) else a.activeEffect(Timing.THIS_TURN)
        }

        override val behavior: Behavior = object : Behavior {
            override fun immune(g: Game, src: Src, q: AffectQuery): Boolean {
                val owner = src.owner ?: return false
                val actor = q.actorId ?: return false
                if (!g.isHero(q.targetId) || !g.isFriendly(owner, q.targetId)) return false
                if (!g.isHero(actor) || !g.isEnemy(owner, actor)) return false
                return g.inRadiusOf(src, q.targetId)
            }
        }
    }

    private object TimeWarp : CardScript {
        override fun primary(a: ActionScope) {
            val swappers = cardSwapTargets(a)
            val bullets = buildList {
                if (timeLoopTargets(
                        a,
                    ).isNotEmpty()
                ) {
                    add(
                        Choice.Option(0, "Swap with an enemy hero in range who has already resolved a card"),
                    )
                }
                if (swappers.isNotEmpty()) {
                    add(
                        Choice.Option(1, "An enemy hero in range swaps their unresolved card with a resolved one"),
                    )
                }
            }
            if (bullets.isEmpty()) a.fizzle("neither option can be done")
            val pick = a.g.ask(a.heroId, DecisionKind.CHOOSE_OPTION, "Time Warp: choose one", bullets, a.cardId)
            if ((pick as Choice.Option).index == 0) {
                timeLoop(a)
                return
            }
            val v = a.target(
                a.chooseUnit("An enemy hero in range swaps their unresolved card with a resolved one", swappers)!!,
            )
            val h = a.g.heroBodyOf(v)!!
            val hero = a.g.hero(h)
            val unresolved = hero.cards.first { it.zone == Zone.PLAYED }.id
            val resolved = hero.cards.filter { it.zone == Zone.RESOLVED }.sortedBy { it.slot }.map { it.id }
            val c = a.chooseCard(
                "Choose one of your resolved cards to swap with your unresolved card",
                resolved,
                chooser = h,
            )!!
            a.g.swapCards(h, unresolved, c)
            // The swapped-in card is now this turn's played card ([APP] rulings/emmitt.md).
            if (a.g.isAttackCard(h, c)) a.g.state.turnRecord.playedAttack += h
        }

        private fun cardSwapTargets(a: ActionScope): List<String> = a.enemyHeroesInRange().filter { id ->
            val h = a.g.hero(a.g.heroBodyOf(id)!!)
            h.cards.any { it.zone == Zone.PLAYED } && h.cards.any { it.zone == Zone.RESOLVED }
        }.distinctBy { a.g.heroBodyOf(it) }
    }

    private object AlternativeTimelines : CardScript {
        override val hasPrimary: Boolean get() = false

        override val behavior: Behavior = object : Behavior {
            override fun extraCardsToPlay(g: Game, src: Src, heroId: String): Int = if (heroId == src.owner) 1 else 0

            override fun on(g: Game, src: Src, t: Trigger) {
                if (t != Trigger.CardsRevealed) return
                val owner = src.owner ?: return
                val played = g.hero(owner).cards.filter { it.zone == Zone.PLAYED }.map { it.id }
                if (played.size < 2) return
                // "if you do, after the cards are revealed, retrieve one of your unresolved cards."
                val c = (
                    g.ask(
                        owner,
                        DecisionKind.CHOOSE_CARD,
                        "Alternative Timelines: retrieve one of your unresolved cards",
                        played.map { Choice.Card(it) },
                        src.cardId,
                    ) as Choice.Card
                    ).cardId
                g.retrieve(owner, c)
            }
        }
    }
}
