package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.zoneOf
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.match.Team

/**
 * What the coach's sentences read (ADR-051): the seat's own view of the position, the playouts of
 * one analysis, and the names. Only [view] and the public parts of [state] (positions, minions, levels,
 * markers, tokens) speak about the real game; a playout's hidden cards are guesses and are named
 * only through [openCard] or in [LineBuilder]'s "could".
 */
internal class CoachFacts(val es: EngineSession, val d: Decision, val a: Analysis, val ev: Evaluator) {
    val data: EngineData = es.engineData
    val seat: String = d.seat
    val team: Team = d.team
    val name: String = data.heroName(seat)
    val heroesPerSide: Int = es.setup.seats.count { it.team == team }

    /** What this seat may see. */
    val view: GameView = es.view(seat)

    /** The real position: read only its public parts (see the class comment). */
    val state: GameState = es.state
    val g: Game = Game(data, state, emptyList(), RunMode.PROBE)
    val me: HeroView = view.heroes.first { it.heroId == seat }
    val myAt: Hex? = me.at
    val myHand: List<Card> = me.hand.orEmpty().mapNotNull(data::cardOrNull)

    private val leavesByChoice: Map<Choice, List<Leaf>> = a.leaves.groupBy { it.choice }

    fun leaves(c: Choice): List<Leaf> = leavesByChoice[c].orEmpty()

    fun events(l: Leaf): List<GameEvent> = l.end.events.drop(l.startEvents)

    fun share(c: Choice, test: (Leaf) -> Boolean): Double {
        val ls = leaves(c)
        return if (ls.isEmpty()) 0.0 else ls.count(test).toDouble() / ls.size
    }

    fun teamOf(unit: String): Team? = es.setup.seats.firstOrNull { it.heroId == unit }?.team

    fun isHero(unit: String): Boolean = teamOf(unit) != null

    fun enemy(unit: String): Boolean = teamOf(unit).let { it != null && it != team }

    fun ally(unit: String): Boolean = unit != seat && teamOf(unit) == team

    fun cardName(id: String?): String? = id?.let { data.cardOrNull(it)?.name }

    /** Who, in a sentence: "you" for the seat answering. */
    fun who(hero: String): String = if (hero == seat) "you" else data.heroName(hero)

    /** Is [cardId] of [heroId] open to this seat now: its own, or faceup on the table? */
    fun openCard(heroId: String, cardId: String): Boolean {
        val h = view.heroes.firstOrNull { it.heroId == heroId } ?: return false
        return h.hand?.contains(cardId) == true || h.items.contains(cardId) || h.played?.cardId == cardId ||
            h.alsoPlayed.any { it.cardId == cardId } || h.resolved.any { it.cardId == cardId } ||
            h.discard.any { it.cardId == cardId }
    }

    /** A unit's or piece's name as the log has it; [fallback] for one that is not on the board now. */
    fun unitName(id: String, at: GameState = state, fallback: String = "a unit"): String {
        if (at.hero(id) != null) return who(id)
        if (at.minion(id) == null && at.piece(id) == null) return fallback
        val named = Game(data, at, emptyList(), RunMode.PROBE).name(id)
        return if (named == id) fallback else "the $named"
    }

    fun distanceToNearestEnemy(hex: Hex): Pair<String, Int>? = view.heroes
        .filter { it.team != team && it.at != null }
        .map { it.heroId to HexGrid.distance(it.at!!, hex) }
        .minWithOrNull(compareBy({ it.second }, { it.first }))

    /** The words of an option: its card, its action on the asking card, a unit, where a space is. */
    fun label(c: Choice): String = when (c) {
        is Choice.Card -> cardName(c.cardId) ?: "A card"

        is Choice.Action -> actionLabel(c.action)

        is Choice.Unit -> unitName(c.unitId, fallback = "A unit").replaceFirstChar { it.uppercase() }

        is Choice.Token -> unitName(c.tokenId, fallback = "A token").replaceFirstChar { it.uppercase() }

        is Choice.Space -> spaceLabel(c.hex)

        is Choice.Option -> c.label

        is Choice.Number -> c.value.toString()

        Choice.Yes -> "Yes"

        is Choice.Decline -> c.label

        Choice.Pass -> "Pass"
    }

    private fun actionLabel(action: ActionType): String {
        val card = cardName(d.sourceCardId)
        return when (action) {
            ActionType.PRIMARY -> card?.let { "$it: primary action" } ?: "Primary action"
            ActionType.MOVEMENT -> card?.let { "$it: movement" } ?: "Movement"
            ActionType.FAST_TRAVEL -> "Fast Travel"
            ActionType.ATTACK -> card?.let { "$it: attack" } ?: "Attack"
            ActionType.CLEAR -> "Clear"
            ActionType.HOLD -> "Hold"
        }
    }

    /** "Lane Zone 2 · next to Brogan", "the Red Throne Zone · 4 spaces from Arien". */
    fun spaceLabel(hex: Hex): String {
        val zone = g.spaceName(hex).replaceFirstChar { it.uppercase() }
        val near = distanceToNearestEnemy(hex) ?: return zone
        val who = data.heroName(near.first)
        return when (near.second) {
            0 -> zone
            1 -> "$zone · next to $who"
            else -> "$zone · ${near.second} spaces from $who"
        }
    }

    /** Minions per team in a lane's Battle Zone (p.8: what the Minion Battle counts). */
    fun battleCount(lane: String): Pair<Int, Int>? {
        val bz = state.battleZones[lane] ?: return null
        fun count(t: Team) = state.minions.count { it.team == t && it.lane == lane && it.at?.let(g::zoneOf) == bz }
        return count(team) to count(team.opponent)
    }
}
