package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Replacement
import dev.quinton.goa.core.game.effects.ReplacementResult
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.chooseDistance
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.discardOrDefeat
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.pushAway
import dev.quinton.goa.core.game.effects.tokensWithin
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.takeTokensBack
import dev.quinton.goa.core.game.engine.tokenSupplyLeft
import dev.quinton.goa.core.game.engine.tokensInPlay
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.model.board.Hex

/**
 * Mrak the Rockshaper. Card text quoted verbatim above each registration (`catalog/heroes/mrak.json`);
 * `MrakTest` follows it clause by clause. Readings: `core/game/rulings/mrak.md`.
 */
object MrakHero : HeroModule {
    override val heroId: String = "mrak"
    override val complete: Boolean = true

    /** The Rock token kind (`{token_rock}` in the card text). */
    const val ROCK: String = "token_rock"

    private const val ULTIMATE = "mrak-purple-4"

    /** Longer than any straight line of a map: "any number of spaces" (Rolling Stone). */
    private const val ANY_NUMBER = 40

    override fun register(r: Registry) {
        // Removed at the end of round (p.19). Three Rock tokens in the box (rulings/mrak.md 1); p.18: when
        // the supply lacks rocks, Mrak first takes them from the board.
        r.token(TokenKind(ROCK, "Rock", supply = 3))
        // Fissure: "Target a unit adjacent to you. After the attack: Place a Rock token in each of the
        // first three empty spaces in the straight line from you in the direction of the attack."
        r.card("mrak-gold", Fissure(heroOnly = false))
        // Lesser Fissure (Handicap): "Target a hero adjacent to you. After the attack: …" (the same)
        r.card("mrak-handicap", Fissure(heroOnly = true))
        // Stone Grip: "Place exactly 3 Rock tokens into empty spaces adjacent to an enemy hero in range,
        // and as far away from you as possible."
        r.card("mrak-silver", StoneGrip)
        // Seismic Slam / Seismic Assault: "An enemy hero in radius adjacent to terrain, or to a Rock
        // token, discards a card, or is defeated." Epicenter: "… May repeat once on a different target."
        // (Seismic Slam: "The secondary Attack action is non-ranged": the engine's secondary attack.)
        r.card("mrak-red-1", Seismic(repeat = false))
        r.card("mrak-red-2a", Seismic(repeat = false))
        r.card("mrak-red-3a", Seismic(repeat = true))
        // Stone Carapace: "This round: If you would discard a card from your hand, you may discard this
        // card instead; you may discard this card to perform its defense action, as if it was in your
        // hand." Rock Solid: "You may retrieve a discarded card. This round: …" (the same)
        r.card("mrak-red-2b", Carapace(retrieve = false))
        r.card("mrak-red-3b", Carapace(retrieve = true))
        // Treacherous Ground / Rockslide: "You may move a unit in range 1 space to a space adjacent to
        // terrain, or a Rock token." Avalanche: "… May repeat once."
        r.card("mrak-green-1", Treacherous(repeat = false))
        r.card("mrak-green-2a", Treacherous(repeat = false))
        r.card("mrak-green-3a", Treacherous(repeat = true))
        // Rolling Stone: "Move any number of spaces in a straight line, ignoring obstacles, without
        // moving through more than one empty space. (The starting space and the destination space do
        // not count.)" Strolling Stone: "… more than two empty spaces."
        r.card("mrak-green-2b", Rolling(emptyAllowed = 1))
        r.card("mrak-green-3b", Rolling(emptyAllowed = 2))
        // Boulder Rush: "Push a token, or an enemy unit, adjacent to you 1 or 2 spaces, ignoring
        // obstacles; you may move up to 2 spaces in the direction of the push, ignoring obstacles."
        // Boulder Blitz: "1, 2 or 3 … up to 3". Boulderdozer: "1, 2, 3 or 4 … up to 4".
        r.card("mrak-blue-1", Boulder(2))
        r.card("mrak-blue-2a", Boulder(3))
        r.card("mrak-blue-3a", Boulder(4))
        // Stomping Step: "Move a unit in radius which is adjacent to terrain, or to a Rock token, 1 space.
        // Place a Rock token in the space it occupied." Ground Shaker: "… May repeat once on a different
        // target."
        r.card("mrak-blue-2b", Stomping(repeat = false))
        r.card("mrak-blue-3b", Stomping(repeat = true))
        // Rock and a Hard Place (Ultimate): "Once per turn, after you place one or more Rock tokens into
        // one or more spaces adjacent to one or more enemy heroes, each of those heroes discards a card,
        // if able." Applied by [placeRocks] after each placement of Mrak's cards.
        r.card("mrak-purple-4", RockAndAHardPlace)
    }

    // ---- shared helpers ------------------------------------------------------------------------

    private fun Game.isRock(id: String?): Boolean = id != null && state.piece(id)?.kind == ROCK

    /** "adjacent to terrain, or to a Rock token" (p.19 "Terrain": water and rocks, Stone Gaze statues). */
    private fun Game.nextToTerrainOrRock(h: Hex): Boolean =
        HexGrid.neighbors(h).any { isTerrain(it) || isRock(occupant(it)) }

    private fun Game.unitNextToTerrainOrRock(id: String): Boolean = posOf(id)?.let { nextToTerrainOrRock(it) } == true

    /**
     * Place Rock tokens into [hexes] as one placement, then Rock and a Hard Place: "Once per turn,
     * after you place one or more Rock tokens into one or more spaces adjacent to one or more enemy
     * heroes, each of those heroes discards a card, if able." Immune heroes neither count nor discard
     * (goa2-faq common: checking a unit's status counts as targeting).
     */
    private fun ActionScope.placeRocks(hexes: List<Hex>) {
        // p.18 "Place": with too few in the supply, rocks are first taken from the board (Fissure and Stone
        // Grip do it before choosing their spaces, so this only acts for Stomping Step / Ground Shaker).
        g.takeTokensBack(ROCK, heroId, hexes.size, heroId, cardId)
        val placed = hexes.filter { g.isEmpty(it) && g.placeToken(ROCK, heroId, it) != null }
        if (placed.isEmpty()) return
        if (hero.card(ULTIMATE)?.zone != Zone.ULTIMATE || hero.at == null) return
        val once = "$ULTIMATE:$heroId"
        if (once in g.state.turnRecord.notes) return
        val victims = g.unitsOnBoard().filter { id ->
            g.isHero(id) && isEnemy(id) && canTarget(id) &&
                g.posOf(id)?.let { p -> placed.any { g.distance(p, it) == 1 } } == true
        }
        if (victims.isEmpty()) return
        g.state.turnRecord.notes += once
        g.note(
            "Rock and a Hard Place: ${victims.joinToString(", ") {
                g.heroName(g.heroBodyOf(it)!!)
            }} discard${if (victims.size == 1) "s" else ""} a card, if able.",
        )
        // Razzle FAQ: during an action each of her figures is "another hero", so each one next to the rocks
        // makes her discard.
        for (v in victims) discardIfAble(v)
    }

    // ---- cards ---------------------------------------------------------------------------------

    private class Fissure(private val heroOnly: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val t = if (heroOnly) {
                a.adjacentAttackTarget("Target a hero adjacent to you") { a.g.isHero(it) }
            } else {
                a.adjacentAttackTarget()
            } ?: return
            // "the direction of the attack": from Mrak to the target's space when attacking.
            val dir = HexGrid.straightLineDirection(a.pos, a.g.posOf(t)!!)!!
            val r = a.attack(t)
            if (!r.connected) return
            // "After the attack: Place a Rock token in each of the first three empty spaces in the
            // straight line from you": occupied spaces are skipped, the line ends at the board's edge.
            val step = HexGrid.DIRECTIONS[dir]
            fun firstThree(): List<Hex> {
                val spaces = ArrayList<Hex>()
                var k = 1
                while (spaces.size < 3) {
                    val h = Hex(a.pos.q + step.q * k, a.pos.r + step.r * k)
                    if (!a.g.geo.isOnBoard(h)) break
                    if (a.g.isEmpty(h)) spaces += h
                    k++
                }
                return spaces
            }
            // p.18 "Place": "first remove up to that number of tokens of the same type from the board" —
            // before the spaces are found, so a rock taken from the line frees its space.
            var spaces = firstThree()
            while (true) {
                val left = a.g.tokenSupplyLeft(ROCK, a.heroId) ?: break
                if (spaces.size <= left ||
                    a.g.takeTokensBack(ROCK, a.heroId, left + 1, a.heroId, a.cardId) <= left
                ) {
                    break
                }
                spaces = firstThree()
            }
            a.placeRocks(spaces)
        }
    }

    private object StoneGrip : CardScript {
        override fun primary(a: ActionScope) {
            fun emptyAround(id: String, freed: Set<Hex> = emptySet()) =
                HexGrid.neighbors(a.g.posOf(id)!!).filter { a.g.isEmpty(it) || it in freed }
            fun heroes(freed: Set<Hex> = emptySet()) = a.unitsWithin(a.range).filter {
                a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) && emptyAround(it, freed).size >= 3
            }
            // p.18 "Place": with too few rocks in the supply, rocks are first taken from the board. When
            // all of them must go (always, with a supply of 3), their spaces count as empty for the check.
            val inPlay = a.g.tokensInPlay(ROCK, a.heroId)
            val left = a.g.tokenSupplyLeft(ROCK, a.heroId)
            val freed = if (left != null &&
                3 - left >= inPlay.size
            ) {
                inPlay.mapNotNull { a.g.posOf(it) }.toSet()
            } else {
                emptySet()
            }
            // "exactly 3": a hero with fewer than 3 empty spaces around it can't be chosen ([APP]).
            if (heroes(freed).isEmpty()) a.fizzle("no enemy hero in range with 3 empty spaces around it")
            a.g.takeTokensBack(ROCK, a.heroId, 3, a.heroId, a.cardId)
            val heroes = heroes()
            if (heroes.isEmpty()) a.fizzle("no enemy hero in range with 3 empty spaces around it")
            val t = a.target(a.chooseUnit("Place 3 Rock tokens next to an enemy hero in range", heroes)!!)
            // "as far away from you as possible": the farthest spaces first; Mrak picks among ties.
            val candidates = emptyAround(t)
            val chosen = ArrayList<Hex>()
            while (chosen.size < 3) {
                val left = candidates.filter { it !in chosen }
                val far = left.maxOf { a.g.distance(a.pos, it) }
                val tier = left.filter { a.g.distance(a.pos, it) == far }
                val need = 3 - chosen.size
                if (tier.size <= need) {
                    chosen += tier
                } else {
                    chosen += a.chooseSpace("Place a Rock token as far away from you as possible", tier)!!
                }
            }
            a.placeRocks(chosen)
        }
    }

    private class Seismic(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val victims = a.unitsWithin(a.radius).filter {
                a.g.isHero(it) && a.isEnemy(it) && a.canTarget(it) && a.g.unitNextToTerrainOrRock(it)
            }
            if (victims.isEmpty()) a.fizzle("no enemy hero in radius adjacent to terrain or a Rock token")
            val v = a.target(
                a.chooseUnit(
                    "An enemy hero adjacent to terrain or a Rock token discards a card, or is defeated",
                    victims,
                )!!,
            )
            a.discardOrDefeat(v)
            // Epicenter: "May repeat once on a different target."
            if (repeat) a.repeat(differentTarget = true, prompt = "Repeat on a different target?")
        }
    }

    /**
     * Stone Carapace / Rock Solid. The effect lives on the played card (sideways on its Turn slot);
     * discarding it changes its state, so the effect ends (p.17).
     */
    private class Carapace(private val retrieve: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // Rock Solid: "You may retrieve a discarded card." Text comes before the movement (p.11).
            if (retrieve) a.mayRetrieve()
            a.activeEffect(Timing.THIS_ROUND)
        }

        override val behavior: Behavior = object : Behavior {
            /** "If you would discard a card from your hand, you may discard this card instead". */
            override fun replace(g: Game, src: Src, r: Replacement): ReplacementResult {
                if (r !is Replacement.Discard || r.heroId != src.owner) return ReplacementResult()
                val me = src.cardId ?: return ReplacementResult()
                val zone = g.hero(r.heroId).card(me)?.zone
                if (zone != Zone.PLAYED && zone != Zone.RESOLVED) return ReplacementResult()
                val c = g.ask(
                    r.heroId,
                    DecisionKind.CONFIRM,
                    "Discard ${g.cardName(me)} instead of ${g.cardName(r.cardId)}?",
                    listOf(Choice.Yes, Choice.Decline("Discard ${g.cardName(r.cardId)}")),
                    me,
                    auto = false,
                )
                if (c != Choice.Yes) return ReplacementResult()
                return ReplacementResult(handled = true, cardId = me)
            }

            /** "you may discard this card to perform its defense action, as if it was in your hand." */
            override fun extraDefenseCards(g: Game, src: Src, heroId: String): List<String> {
                if (heroId != src.owner) return emptyList()
                val me = src.cardId ?: return emptyList()
                val zone = g.hero(heroId).card(me)?.zone
                return if (zone == Zone.PLAYED || zone == Zone.RESOLVED) listOf(me) else emptyList()
            }
        }
    }

    private class Treacherous(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            val end = { h: Hex -> a.g.nextToTerrainOrRock(h) }
            val units = a.unitsWithin(a.range).filter {
                a.canTarget(it) && a.g.canDisplace(it, Displacement.MOVE, a.heroId) &&
                    a.canMoveOther(it, 1, endFilter = end)
            }
            // "You may move a unit in range 1 space to a space adjacent to terrain, or a Rock token."
            if (units.isEmpty()) return
            val u =
                a.chooseUnit(
                    "You may move a unit in range 1 space next to terrain or a Rock token",
                    units,
                    optional = true,
                )
                    ?: return
            a.moveOther(u, 1, endFilter = end)
            // Avalanche: "May repeat once." (declining the move above leaves nothing to repeat)
            if (repeat) a.repeat()
        }
    }

    private class Rolling(private val emptyAllowed: Int) : CardScript {
        override fun primary(a: ActionScope) {
            val start = a.pos
            // "without moving through more than N empty spaces": start and destination do not count.
            val ok = { h: Hex ->
                (HexGrid.straightLine(start, h)?.dropLast(1)?.count { a.g.isEmpty(it) } ?: Int.MAX_VALUE) <=
                    emptyAllowed
            }
            // "Move any number of spaces in a straight line, ignoring obstacles" ([APP]: at least 1).
            a.moveSelf(1, ANY_NUMBER, straightLine = true, ignoreObstacles = true, endFilter = ok)
        }
    }

    private class Boulder(private val spaces: Int) : CardScript {
        override fun primary(a: ActionScope) {
            // "Push a token, or an enemy unit, adjacent to you 1..N spaces, ignoring obstacles" (mandatory).
            val objects = (a.unitsWithin(1).filter { a.isEnemy(it) } + a.tokensWithin(1)).filter {
                a.canTarget(it) && a.g.canDisplace(it, Displacement.PUSH, a.heroId)
            }
            if (objects.isEmpty()) a.fizzle("no token or enemy unit adjacent can be pushed")
            val t = a.target(a.chooseObject("Push a token, or an enemy unit, adjacent to you", objects)!!)
            val dir = HexGrid.straightLineDirection(a.pos, a.g.posOf(t)!!)!!
            val n = a.chooseDistance((1..spaces).toList(), "Push ${a.g.name(t)} how many spaces?")
            a.pushAway(t, n, ignoreObstacles = true)
            // "you may move up to N spaces in the direction of the push, ignoring obstacles."
            a.g.move(
                MoveSpec(a.heroId, 0, spaces, straightLine = true, ignoreObstacles = true, directions = setOf(dir)),
                a.heroId,
                "You may move up to $spaces spaces in the direction of the push",
                optional = true,
                sourceCardId = a.cardId,
            )
        }
    }

    private class Stomping(private val repeat: Boolean) : CardScript {
        override fun primary(a: ActionScope) {
            // "Move a unit in radius which is adjacent to terrain, or to a Rock token, 1 space." (mandatory)
            val units = a.unitsWithin(a.radius).filter {
                a.canTarget(it) && a.g.unitNextToTerrainOrRock(it) &&
                    a.g.canDisplace(it, Displacement.MOVE, a.heroId) && a.canMoveOther(it, 1)
            }
            if (units.isEmpty()) a.fizzle("no unit in radius next to terrain or a Rock token can move")
            val u = a.target(a.chooseUnit("Move a unit in radius next to terrain or a Rock token 1 space", units)!!)
            val from = a.g.posOf(u)!!
            a.moveOther(u, 1)
            // "Place a Rock token in the space it occupied."
            a.placeRocks(listOf(from))
            // Ground Shaker: "May repeat once on a different target."
            if (repeat) a.repeat(differentTarget = true, prompt = "Repeat on a different target?")
        }
    }

    /** Applied by [placeRocks]; nothing is in force by itself. */
    private object RockAndAHardPlace : CardScript {
        override val hasPrimary: Boolean get() = false
    }
}
