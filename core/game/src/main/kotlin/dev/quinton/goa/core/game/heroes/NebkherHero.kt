package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.InteractQuery
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.radiusOf
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.ActorDefeated
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.actionOptions
import dev.quinton.goa.core.game.engine.affectedBy
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.canTarget
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.performAction
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.primaryKind
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.swapCards
import dev.quinton.goa.core.game.engine.tokensOnBoard
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * NebKher the Harbinger. Card text quoted verbatim above each registration
 * (`catalog/heroes/nebkher.json`); `NebkherTest` follows it clause by clause with the FAQ. Readings
 * the text leaves open are in `core/game/rulings/nebkher.md`.
 */
object NebkherHero : HeroModule {
    override val heroId: String = "nebkher"
    override val complete: Boolean = true

    const val ILLUSION = "token_illusion"
    private const val ULTIMATE = "nebkher-purple-4"
    private const val LAUGHED = "nebkher.laughed"

    /** The colours a card in hand can have (Imbue Doubt's "Name a color"). */
    val COLOURS: List<CardColor> =
        listOf(CardColor.GOLD, CardColor.SILVER, CardColor.RED, CardColor.GREEN, CardColor.BLUE)

    override fun register(r: Registry) {
        // [APP] No supply count is known for Illusion tokens: not limited (rulings/nebkher.md).
        r.token(TokenKind(ILLUSION, "Illusion", supply = null))
        // Mind Grip / Mind Probe: "if you would place any tokens this way, place Illusion tokens instead;
        // skip giving markers" / "do not use markers". NebKher places no other tokens and gives no markers
        // with his own cards, so this holds whenever he is the one placing or giving.
        r.heroRule(heroId, CopyRule)
        // Mind Grip: "Choose one — • Perform an action on the card in the previous turn slot of an enemy
        // hero in range; if you would place any tokens this way, place {token_illusion} Illusion tokens
        // instead; skip giving markers. • Defeat a minion adjacent to you."
        r.card("nebkher-gold", MindGrip(handicap = false))
        // Mind Probe (Handicap): "Perform an action on the card in the previous turn slot of an enemy hero
        // in range; if you would place any tokens this way, place {token_illusion} Illusion tokens
        // instead; do not use markers."
        r.card("nebkher-handicap", MindGrip(handicap = true))
        // Diabolical Laughter: "Laugh diabolically; if you do, choose up to three times — • Swap with an
        // {token_illusion} Illusion token in radius. • Place an Illusion token in an adjacent space. • Swap
        // two resolved cards of an enemy hero in radius, without canceling active effects."
        r.card("nebkher-silver", Laughter)
        // Phantasmal Sentry: "Choose one — • Target a hero in range adjacent to an {token_illusion}
        // Illusion token in range. • Target a unit adjacent to you."
        r.card("nebkher-red-1", Sentry)
        // Phantasmal Warrior / Champion: "Choose one — • Before the attack: Move an {token_illusion}
        // Illusion token in range up to 1 (2) space(s) to a space adjacent to an enemy hero in range.
        // Target that hero. • Target a unit adjacent to you."
        r.card("nebkher-red-2a", Phantasm(1))
        r.card("nebkher-red-3a", Phantasm(2))
        // Twist Fate: "Target a unit in range. After the attack: You may swap an enemy unit in range with an
        // {token_illusion} Illusion token adjacent to you." Devious Scheme: "… Illusion token in range."
        r.card("nebkher-red-2b", Twist(tokenInRange = false))
        r.card("nebkher-red-3b", Twist(tokenInRange = true))
        // Fleeting Image: "Place an {token_illusion} Illusion token in radius. You may swap with an
        // Illusion token in play." Multiple Projections / Master of Illusions: "Place up to 2 (3) Illusion
        // tokens in radius. You may swap with an Illusion token in play."
        r.card("nebkher-green-1", Projection(count = 1, exact = true))
        r.card("nebkher-green-2a", Projection(count = 2, exact = false))
        r.card("nebkher-green-3a", Projection(count = 3, exact = false))
        // Illusionary Force / Army: "Place up to 2 (3) {token_illusion} Illusion tokens in radius. This
        // round: While you are performing actions, all Illusion tokens count as both tokens and friendly
        // melee minions."
        r.card("nebkher-green-2b", Force(2))
        r.card("nebkher-green-3b", Force(3))
        // Imbue Doubt / Time to Reconsider: "Name a color. Next turn, after playing cards: An enemy hero in
        // radius discards a card of that color, if able." An Illusion of Choice: "… Up to two enemy heroes
        // in radius each discard a card of that color, if able."
        r.card("nebkher-blue-1", Doubt(upToTwo = false))
        r.card("nebkher-blue-2a", Doubt(upToTwo = false))
        r.card("nebkher-blue-3a", Doubt(upToTwo = true))
        // Crack in Reality: "Split the board into two sides with a straight line of spaces drawn through
        // your space. This turn: Units on either side of the line cannot interact with objects and spaces
        // on the other side of the line, as if they did not exist. (This includes movement, placement,
        // radius-based effects, etc.)" Shift Reality: "… cannot interact with you, nor with objects and
        // spaces on the other side of the line, as if they did not exist."
        r.card("nebkher-blue-2b", Crack(withYou = false))
        r.card("nebkher-blue-3b", Crack(withYou = true))
        // What the Hell Are You? (Ultimate): "Each time after you laugh diabolically as part of performing
        // an action, all enemy heroes in radius discard a card, or are defeated." (Diabolical Laughter.)
        r.card(ULTIMATE, dev.quinton.goa.core.game.effects.script())
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun ActionScope.illusions(): List<String> =
        g.tokensOnBoard().filter { g.state.piece(it)?.kind == ILLUSION }

    private fun ActionScope.illusionsWithin(n: Int): List<String> =
        illusions().filter { id -> g.posOf(id)?.let { g.distance(pos, it) in 1..n } == true }

    /** Empty spaces 1..[n] from NebKher ("in radius"). */
    private fun ActionScope.emptyWithin(n: Int): List<Hex> = g.within(pos, n).filter { g.isEmpty(it) }

    /** "Choose one —" with only the possible bullets offered (RULINGS 38); null when none is possible. */
    private fun ActionScope.chooseBullet(labels: List<String>, possible: List<Boolean>): Int? {
        val ok = labels.indices.filter { possible[it] }
        if (ok.isEmpty()) return null
        if (ok.size == 1) return ok.single()
        val c = g.ask(
            heroId,
            DecisionKind.CHOOSE_OPTION,
            "Choose one",
            ok.map {
                Choice.Option(it, labels[it])
            },
            cardId,
        )
        return (c as Choice.Option).index
    }

    /** "You may swap with an Illusion token in play." */
    private fun ActionScope.maySwapWithIllusion() {
        val ts = illusions().filter { canTarget(it) && g.canSwap(heroId, it, heroId) }
        val t = chooseToken("You may swap with an Illusion token in play", ts, optional = true) ?: return
        target(t)
        g.swap(heroId, t, heroId)
    }

    // ---- Mind Grip / Mind Probe ----------------------------------------------------------------

    /** The hero rule behind "place Illusion tokens instead; skip giving markers". */
    private object CopyRule : Behavior {
        override fun tokenKind(g: Game, src: Src, kind: String, byHeroId: String?): String? =
            if (byHeroId != null && byHeroId == src.owner && kind != ILLUSION) ILLUSION else null

        override fun forbidsMarker(g: Game, src: Src, heroId: String, kind: String, fromHeroId: String?): Boolean =
            fromHeroId != null && fromHeroId == src.owner
    }

    /** An enemy hero's faceup card on its previous Turn slot, with the actions NebKher could perform on it. */
    private class Copyable(val heroId: String, val cardId: String, val actions: List<ActionType>)

    private class MindGrip(private val handicap: Boolean) : CardScript {
        private fun copyable(a: ActionScope): List<Copyable> {
            val slot = a.g.state.turn - 1
            if (slot < 1) return emptyList()
            return a.unitsWithin(a.range).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
                .mapNotNull { a.g.heroBodyOf(it) }.distinct().mapNotNull { enemy ->
                    val c =
                        a.g.hero(enemy).cards.firstOrNull {
                            it.zone == Zone.RESOLVED && it.slot == slot && !it.faceDown
                        }
                            ?: return@mapNotNull null
                    // [APP] Hold is not an action worth taking from another hero's card.
                    val actions = a.g.actionOptions(a.heroId, c.id).filter { it != ActionType.HOLD }
                    if (actions.isEmpty()) null else Copyable(enemy, c.id, actions)
                }
        }

        private fun minions(a: ActionScope): List<String> =
            a.unitsWithin(1).filter { a.g.isMinion(it) && a.isEnemy(it) && a.canTarget(it) }

        override fun primary(a: ActionScope) {
            val cards = copyable(a)
            val pick = if (handicap) {
                if (cards.isEmpty()) a.fizzle("no enemy hero in range with a card to use") else 0
            } else {
                a.chooseBullet(
                    listOf(
                        "Perform an action on the card in the previous turn slot of an enemy hero in range",
                        "Defeat a minion adjacent to you",
                    ),
                    listOf(cards.isNotEmpty(), minions(a).isNotEmpty()),
                ) ?: a.fizzle("nothing to do")
            }
            if (pick == 1) {
                // "Defeat a minion adjacent to you." (an enemy one: p.13 "You can never defeat a friendly unit")
                val m = a.target(a.chooseUnit("Defeat a minion adjacent to you", minions(a))!!)
                a.g.defeatMinion(m, a.heroId)
                return
            }
            perform(a, cards)
        }

        /** "Perform an action on the card in the previous turn slot of an enemy hero in range." */
        private fun perform(a: ActionScope, cards: List<Copyable>) {
            val enemy = a.chooseUnit(
                "Choose an enemy hero in range: you perform an action on the card in its previous turn slot",
                cards.map { it.heroId },
            )!!
            a.target(enemy)
            val c = cards.first { it.heroId == enemy }
            val choice = a.g.ask(
                a.heroId,
                DecisionKind.CHOOSE_ACTION,
                "Choose an action on ${a.g.cardName(c.cardId)}",
                c.actions.map { Choice.Action(it) },
                c.cardId,
                auto = false,
            ) as Choice.Action
            val kind = when (choice.action) {
                ActionType.PRIMARY -> primaryKind(a.g.card(c.cardId))
                ActionType.MOVEMENT -> ActionKind.MOVEMENT
                ActionType.FAST_TRAVEL -> ActionKind.FAST_TRAVEL
                ActionType.ATTACK -> ActionKind.ATTACK
                ActionType.CLEAR -> ActionKind.CLEAR
                ActionType.HOLD -> ActionKind.HOLD
            }
            a.g.note("${a.g.heroName(a.heroId)} uses ${a.g.heroName(enemy)}'s ${a.g.cardName(c.cardId)}.")
            val done = a.g.performAction(a.heroId, c.cardId, kind, primary = choice.action == ActionType.PRIMARY)
            // NebKher was defeated while performing it: his Mind Grip action ends too.
            if (done == null && a.hero.at == null) throw ActorDefeated(a.heroId)
        }
    }

    // ---- Diabolical Laughter and the Ultimate --------------------------------------------------

    private object Laughter : CardScript {
        private val labels = listOf(
            "Swap with an Illusion token in radius",
            "Place an Illusion token in an adjacent space",
            "Swap two resolved cards of an enemy hero in radius",
        )

        override fun primary(a: ActionScope) {
            // "Laugh diabolically" (FAQ: you don't need to really laugh). It is always done.
            a.g.note("${a.g.heroName(a.heroId)} laughs diabolically.")
            a.g.state.turnRecord.notes += LAUGHED
            a.g.touch()
            whatTheHell(a)
            // "if you do, choose up to three times —" (the same bullet may be chosen again).
            val limit = a.choiceLimit(3, upTo = true)
            for (i in 1..limit) {
                val possible =
                    listOf(swapTargets(a).isNotEmpty(), a.emptyWithin(1).isNotEmpty(), cardSwapHeroes(a).isNotEmpty())
                val ok = labels.indices.filter { possible[it] }
                if (ok.isEmpty()) return
                val c = a.g.ask(
                    a.heroId,
                    DecisionKind.CHOOSE_OPTION,
                    "Diabolical Laughter: choose ($i of up to $limit)",
                    ok.map { Choice.Option(it, labels[it]) as Choice } + Choice.Decline("Stop"),
                    a.cardId,
                )
                when ((c as? Choice.Option)?.index) {
                    0 -> {
                        val t = a.target(a.chooseToken(labels[0], swapTargets(a))!!)
                        a.g.swap(a.heroId, t, a.heroId)
                    }

                    1 -> {
                        val h = a.chooseSpace(labels[1], a.emptyWithin(1))!!
                        a.g.placeToken(ILLUSION, a.heroId, h)
                    }

                    2 -> swapCards(a)

                    else -> return
                }
            }
        }

        /** "Each time after you laugh diabolically …, all enemy heroes in radius discard a card, or are defeated." */
        private fun whatTheHell(a: ActionScope) {
            val h = a.hero
            if (h.at == null || h.cards.none { it.id == ULTIMATE && it.zone == Zone.ULTIMATE }) return
            val radius = a.g.cardValue(a.heroId, ULTIMATE, Stat.RADIUS) ?: 0
            val victims = a.unitsWithin(radius).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
                .mapNotNull { a.g.heroBodyOf(it) }.distinct()
            for (v in victims) {
                if (a.g.hero(v).at == null) continue
                a.target(v)
                a.g.forceDiscard(v, a.heroId, orDefeated = true, sourceCardId = ULTIMATE)
            }
        }

        private fun swapTargets(a: ActionScope): List<String> =
            a.illusionsWithin(a.radius).filter { a.canTarget(it) && a.g.canSwap(a.heroId, it, a.heroId) }

        private fun cardSwapHeroes(a: ActionScope): List<String> =
            a.unitsWithin(a.radius).filter { a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) }
                .mapNotNull { a.g.heroBodyOf(it) }.distinct()
                .filter { e -> a.g.hero(e).cards.count { it.zone == Zone.RESOLVED } >= 2 }

        /** "Swap two resolved cards of an enemy hero in radius, without canceling active effects." */
        private fun swapCards(a: ActionScope) {
            val e = a.target(a.chooseUnit("Swap two resolved cards of an enemy hero in radius", cardSwapHeroes(a))!!)
            val resolved = a.g.hero(e).cards.filter { it.zone == Zone.RESOLVED }.sortedBy { it.slot }

            // Chosen by Turn slot: a facedown card stays hidden.
            fun label(slot: Int) = resolved.first { it.slot == slot }.let { c ->
                "Turn slot $slot: ${if (c.faceDown) "a facedown card" else a.g.cardName(c.id)}"
            }
            val first = (
                a.g.ask(
                    a.heroId,
                    DecisionKind.CHOOSE_OPTION,
                    "First card to swap",
                    resolved.map {
                        Choice.Option(it.slot, label(it.slot))
                    },
                    a.cardId,
                ) as Choice.Option
                ).index
            val rest = resolved.filter { it.slot != first }
            val second = (
                a.g.ask(
                    a.heroId,
                    DecisionKind.CHOOSE_OPTION,
                    "Swap it with",
                    rest.map {
                        Choice.Option(it.slot, label(it.slot))
                    },
                    a.cardId,
                ) as Choice.Option
                ).index
            val c1 = resolved.first { it.slot == first }.id
            val c2 = resolved.first { it.slot == second }.id
            // The log names the slots, never a facedown card (p.10; events are public).
            val text = "${a.g.heroName(a.heroId)} swaps ${a.g.heroName(e)}'s resolved cards: " +
                "${label(first)} and ${label(second)}."
            a.g.swapCards(e, c1, c2, keepEffects = true, text = text)
        }
    }

    // ---- attacks -------------------------------------------------------------------------------

    private object Sentry : CardScript {
        /** "a hero in range adjacent to an Illusion token in range" */
        private fun nearIllusion(a: ActionScope, id: String): Boolean {
            if (!a.g.isHero(id) || !a.inRange(id)) return false
            val p = a.g.posOf(id) ?: return false
            return a.illusionsWithin(a.range).any { t -> a.g.posOf(t)?.let { HexGrid.distance(it, p) == 1 } == true }
        }

        override fun primary(a: ActionScope) {
            val enemies = a.g.unitsOnBoard().filter { a.isEnemy(it) && a.canTarget(it, ActionKind.ATTACK) }
            val pick = a.chooseBullet(
                listOf(
                    "Target a hero in range adjacent to an Illusion token in range",
                    "Target a unit adjacent to you",
                ),
                listOf(enemies.any { nearIllusion(a, it) }, enemies.any { a.isAdjacent(it) }),
            ) ?: a.fizzle("no target")
            val rule = if (pick == 0) { id: String -> nearIllusion(a, id) } else { id: String -> a.isAdjacent(id) }
            val t =
                a.attackTarget(
                    if (pick ==
                        0
                    ) {
                        "Target a hero next to an Illusion token"
                    } else {
                        "Target a unit adjacent to you"
                    },
                ) { rule(it) }
                    ?: return
            a.attack(t, valid = { rule(it) })
        }
    }

    private class Phantasm(private val spaces: Int) : CardScript {
        /** Enemy heroes in range that can be attacked. */
        private fun heroes(a: ActionScope) = a.g.unitsOnBoard().filter {
            a.g.isHero(it) && a.isEnemy(it) && a.inRange(it) && a.canTarget(it, ActionKind.ATTACK)
        }

        private fun ends(a: ActionScope, token: String): Map<Hex, List<Hex>> {
            val hs = heroes(a).mapNotNull { a.g.posOf(it) }
            return a.g.reach(MoveSpec(token, 0, spaces, endFilter = { h -> hs.any { HexGrid.distance(it, h) == 1 } }))
        }

        private fun tokens(a: ActionScope) = a.illusionsWithin(a.range).filter {
            a.canTarget(it) &&
                ends(a, it).isNotEmpty()
        }

        override fun primary(a: ActionScope) {
            val pick = a.chooseBullet(
                listOf(
                    "Move an Illusion token in range up to $spaces space${if (spaces == 1) "" else "s"} " +
                        "next to an enemy hero in range, target that hero",
                    "Target a unit adjacent to you",
                ),
                listOf(
                    tokens(a).isNotEmpty(),
                    a.g.unitsOnBoard().any { a.isEnemy(it) && a.isAdjacent(it) && a.canTarget(it, ActionKind.ATTACK) },
                ),
            ) ?: a.fizzle("no target")
            if (pick == 1) {
                val t = a.attackTarget("Target a unit adjacent to you") { a.isAdjacent(it) } ?: return
                a.attack(t, valid = { a.isAdjacent(it) })
                return
            }
            // "Before the attack: Move an Illusion token in range up to N spaces to a space adjacent to an
            // enemy hero in range." (0 spaces when it already is.)
            val token = a.target(a.chooseToken("Move an Illusion token in range", tokens(a))!!)
            val start = a.g.posOf(token)!!
            val end = a.chooseSpace(
                "Move the Illusion token to a space next to an enemy hero in range",
                ends(a, token).keys,
            )!!
            if (end != start) {
                a.g.move(
                    MoveSpec(token, 1, spaces, endFilter = {
                        it == end
                    }),
                    a.heroId,
                    "Move the Illusion token",
                    false,
                    a.cardId,
                )
            }
            // "Target that hero."
            val at = a.g.posOf(token)!!
            val rule = { id: String ->
                a.g.isHero(id) && a.inRange(id) && a.g.posOf(id)?.let { HexGrid.distance(it, at) == 1 } == true
            }
            val t = a.attackTarget("Target that hero") { rule(it) } ?: return
            a.attack(t, valid = { rule(it) })
        }
    }

    private class Twist(private val tokenInRange: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Target a unit in range."
            val t = a.attackTarget("Target a unit in range") { a.inRange(it) } ?: return
            val res = a.attack(t, valid = { a.inRange(it) })
            if (!res.connected) return
            // "After the attack: You may swap an enemy unit in range with an Illusion token adjacent to you
            // (in range)."
            val tokens = a.illusionsWithin(if (tokenInRange) a.range else 1).filter { a.canTarget(it) }
            if (tokens.isEmpty()) return
            val units = a.unitsWithin(a.range).filter { u ->
                a.isEnemy(u) && a.canTarget(u) && tokens.any { a.g.canSwap(u, it, a.heroId) }
            }
            val u =
                a.chooseUnit("You may swap an enemy unit in range with an Illusion token", units, optional = true)
                    ?: return
            val tk = a.chooseToken("Swap it with an Illusion token", tokens.filter { a.g.canSwap(u, it, a.heroId) })!!
            a.target(u)
            a.target(tk)
            a.g.swap(u, tk, a.heroId)
        }
    }

    // ---- skills --------------------------------------------------------------------------------

    /** Place [count] ([exact]) or up to [count] Illusion tokens in radius. */
    private fun placeIllusions(a: ActionScope, count: Int, exact: Boolean) {
        for (i in 1..count) {
            val spaces = a.emptyWithin(a.radius)
            if (spaces.isEmpty()) {
                if (exact) a.fizzle("no empty space in radius")
                return
            }
            val prompt = "Place an Illusion token in radius" + if (exact) "" else " ($i of up to $count)"
            val h = a.chooseSpace(prompt, spaces, optional = !exact) ?: return
            a.g.placeToken(ILLUSION, a.heroId, h)
        }
    }

    private class Projection(private val count: Int, private val exact: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            placeIllusions(a, count, exact)
            a.maySwapWithIllusion()
        }
    }

    private class Force(private val count: Int) : CardScript {
        override fun primary(a: ActionScope) {
            placeIllusions(a, count, exact = false)
            a.activeEffect(Timing.THIS_ROUND)
        }

        /** "While you are performing actions, all Illusion tokens count as both tokens and friendly melee minions." */
        override val behavior: Behavior = object : Behavior {
            override fun pieceCountsAsMinion(
                g: Game,
                src: Src,
                piece: PieceState,
                actorId: String?,
            ): Pair<Team, MinionType>? {
                val owner = src.owner ?: return null
                if (piece.kind != ILLUSION || actorId == null || actorId != owner) return null
                return g.teamOf(owner)?.let { it to MinionType.MELEE }
            }
        }
    }

    private const val COLOUR = "colour"

    private class Doubt(private val upToTwo: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Name a color."
            val i = a.chooseOption(
                "Name a color",
                COLOURS.map {
                    it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                },
            )!!
            a.g.note("${a.g.heroName(a.heroId)} names ${COLOURS[i].name.lowercase()}.")
            // "Next turn, after playing cards: …" (FAQ: never into the next round)
            a.activeEffect(Timing.NEXT_TURN_AFTER_PLAYING, data = mapOf(COLOUR to COLOURS[i].name))
        }

        override val behavior: Behavior = object : Behavior {
            override fun applyTimed(g: Game, src: Src) {
                val owner = src.owner ?: return
                val me = g.posOf(owner) ?: return
                val colour = src.effect?.data?.get(COLOUR)?.let { CardColor.valueOf(it) } ?: return
                val r = g.radiusOf(src)
                val left = g.unitsOnBoard().filter { id ->
                    g.isHero(id) && g.isEnemy(owner, id) && g.posOf(id)?.let { g.distance(me, it) in 1..r } == true &&
                        g.canTarget(id, owner, ActionKind.SKILL, src.cardId) && g.affectedBy(src, id)
                }.mapNotNull { g.heroBodyOf(it) }.distinct().toMutableList()
                val what = "a ${colour.name.lowercase()} card"
                for (n in 1..(if (upToTwo) 2 else 1)) {
                    if (left.isEmpty()) return
                    val opts =
                        left.map { Choice.Unit(it) as Choice } +
                            if (upToTwo) listOf(Choice.Decline(if (n == 1) "None" else "Done")) else emptyList()
                    val prompt = if (upToTwo) {
                        "Up to two enemy heroes in radius each discard $what ($n of up to 2)"
                    } else {
                        "An enemy hero in radius discards $what"
                    }
                    val c = g.ask(
                        owner,
                        DecisionKind.CHOOSE_UNIT,
                        prompt,
                        opts,
                        src.cardId,
                        auto = !upToTwo,
                    ) as? Choice.Unit ?: return
                    left -= c.unitId
                    val matches = { card: dev.quinton.goa.core.model.catalog.Card -> card.color == colour }
                    g.forceDiscard(
                        c.unitId,
                        owner,
                        orDefeated = false,
                        sourceCardId = src.cardId,
                        what = what,
                        filter = matches,
                    )
                }
            }
        }
    }

    // ---- Crack in Reality / Shift Reality -------------------------------------------------------

    private const val AXIS = "axis"
    private const val Q = "q"
    private const val R = "r"

    /** Which side of the line through [c] along [axis] (0: r constant, 1: q + r constant, 2: q constant) [h] is on. */
    fun sideOf(c: Hex, axis: Int, h: Hex): Int = Integer.signum(
        when (axis) {
            0 -> h.r - c.r
            1 -> (h.q + h.r) - (c.q + c.r)
            else -> h.q - c.q
        },
    )

    private class Crack(private val withYou: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Split the board into two sides with a straight line of spaces drawn through your space."
            val me = a.pos
            val labels = (0..2).map { axis ->
                val d = HexGrid.DIRECTIONS[axis]
                "The line through ${Hex(me.q - d.q, me.r - d.r)}, your space and ${Hex(me.q + d.q, me.r + d.r)}"
            }
            val axis = a.chooseOption("Draw a straight line through your space", labels)!!
            a.activeEffect(Timing.THIS_TURN, data = mapOf(AXIS to "$axis", Q to "${me.q}", R to "${me.r}"))
        }

        override val behavior: Behavior = object : Behavior {
            override fun forbidsInteraction(g: Game, src: Src, q: InteractQuery): Boolean {
                val data = src.effect?.data ?: return false
                val axis = data[AXIS]?.toInt() ?: return false
                val c = Hex(data[Q]!!.toInt(), data[R]!!.toInt())
                val at = g.posOf(q.actorId) ?: return false
                val actorSide = sideOf(c, axis, at)
                // FAQ: "Units on the line can affect and be affected by units on both sides."
                if (actorSide == 0) return false
                if (!g.affectedBy(src, q.actorId)) return false
                val hexSide = sideOf(c, axis, q.hex)
                if (hexSide != 0 && hexSide != actorSide) return true
                // Shift Reality: "cannot interact with you" (wherever NebKher is now).
                return withYou && q.actorId != src.owner && src.owner?.let { g.posOf(it) } == q.hex
            }
        }
    }
}
