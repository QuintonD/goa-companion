package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.SrcKind
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random

/** Control flow of a run (ENGINE.md "Re-execution"). Stack traces are off: these are not errors. */
open class ControlSignal(message: String? = null) : RuntimeException(message, null, false, false)

/** A decision has no answer yet: the run stops here and the decision is pending. */
class Suspend(val decision: Decision) : ControlSignal()

/** A win condition was met (p.8, p.13); the game ended mid-step. */
class GameEnded : ControlSignal()

/**
 * A mandatory step of the card text cannot be done (p.10 "Mandatory and Optional Steps": "stop
 * performing the action at that step, and skip any remaining steps").
 */
class Fizzle(reason: String) : ControlSignal(reason)

/** Probe runs (is a Primary action possible at all?) stop at the first question. */
internal class ProbeHit : ControlSignal()

internal enum class RunMode { NORMAL, PROBE }

/**
 * One run of the rules over [state] (ENGINE.md). Card scripts and the flow call its primitives;
 * decisions come from [answers] in order, and the first one without an answer suspends the run.
 * Extension functions in this package add the rules by topic (Board, Cards, Combat, Units, Flow).
 */
class Game internal constructor(
    val data: EngineData,
    val state: GameState,
    private val answers: List<Choice>,
    internal val mode: RunMode = RunMode.NORMAL,
    /** In a determinized copy, answers that are no longer options are replaced by a sampled one. */
    private val lenient: Random? = null,
) {
    val geo: BoardGeometry = data.geometry(state.mapId)

    /** Events of this run, oldest first. */
    val events: MutableList<GameEvent> = ArrayList()

    /** Decisions asked (and answered) in this run. */
    var asked: Int = 0
        private set

    /** The heroes performing actions now, innermost last (an attack's defender is pushed on top). */
    internal val actors: ArrayList<String> = ArrayList()

    /** The hero performing the innermost action, if any. */
    val actor: String? get() = actors.lastOrNull()

    // ---- decisions ---------------------------------------------------------------------------

    /**
     * Ask [seat]'s controller. With [auto] and a single option, the answer is that option and no
     * decision is asked (nothing to decide). [options] must be non-empty and stable for the same
     * state (sorted canonically) so re-runs ask the same question.
     */
    @Suppress("LongParameterList")
    fun ask(
        seat: String,
        kind: DecisionKind,
        prompt: String,
        options: List<Choice>,
        sourceCardId: String? = null,
        context: DecisionContext = DecisionContext(),
        auto: Boolean = true,
    ): Choice {
        check(options.isNotEmpty()) { "Decision without options: $prompt" }
        if (auto && options.size == 1) return options.single()
        if (mode == RunMode.PROBE) throw ProbeHit()
        val index = asked
        if (index < answers.size) {
            asked++
            val given = answers[index]
            if (lenient != null && given !in options) return options[lenient.nextInt(options.size)]
            return given
        }
        val team = teamOf(seat) ?: Team.RED
        throw Suspend(Decision(state.decisions + index, seat, team, kind, prompt, sourceCardId, options, context))
    }

    fun emit(event: GameEvent) {
        if (mode == RunMode.PROBE) return
        events += event
    }

    fun note(text: String) = emit(GameEvent.Note(text))

    /** Stop the current action: a mandatory step can't be done (p.10). */
    fun fizzle(reason: String): Nothing = throw Fizzle(reason)

    // ---- lookups -----------------------------------------------------------------------------

    fun card(cardId: String): Card = data.card(cardId)

    fun hero(id: String): HeroState = state.hero(id) ?: error("No hero $id")

    fun heroOrNull(id: String): HeroState? = state.hero(id)

    fun heroName(id: String): String = data.heroName(id)

    /** Readable name of any unit, token or hero for event text. */
    fun name(id: String): String {
        state.hero(id)?.let { return heroName(it.id) }
        state.minion(id)?.let { return "${teamName(it.team)} ${minionName(it.type)} minion" }
        state.piece(id)?.let { piece ->
            piece.heroBody?.let { return heroName(it) }
            return piece.label
        }
        return id
    }

    fun cardName(cardId: String): String = data.cardOrNull(cardId)?.name ?: cardId

    /** The hero whose body this unit is (a hero, or a piece that is "the same hero", e.g. Razzle's doubles). */
    fun heroBodyOf(id: String): String? = state.hero(id)?.id ?: state.piece(id)?.heroBody

    fun isHero(id: String): Boolean = heroBodyOf(id) != null

    fun isMinion(id: String): Boolean = state.minion(id) != null

    fun isToken(id: String): Boolean = state.piece(id)?.token == true

    /** A unit: a hero, a minion, or a piece that is one (p.19 "Unit": "heroes and minions"). */
    fun isUnit(id: String): Boolean = isHero(id) || isMinion(id) || state.piece(id)?.unit == true

    fun teamOf(id: String): Team? =
        state.hero(id)?.team ?: state.minion(id)?.team ?: state.piece(id)?.let { p ->
            p.team ?: p.heroBody?.let { state.hero(it)?.team }
        }

    fun posOf(id: String): Hex? = state.hero(id)?.at ?: state.minion(id)?.at ?: state.piece(id)?.at

    /** Spaces between two spaces, obstacles included (p.17 "Farther", p.18 "Nearest"). */
    fun distance(a: Hex, b: Hex): Int = dev.quinton.goa.core.domain.board.HexGrid.distance(a, b)

    /** p.17 "Friendly": "another unit on your team. It never includes you." */
    fun isFriendly(a: String, b: String): Boolean {
        if (a == b) return false
        val bodyA = heroBodyOf(a)
        if (bodyA != null && bodyA == heroBodyOf(b)) return false
        val ta = teamOf(a) ?: return false
        return ta == teamOf(b)
    }

    fun isEnemy(a: String, b: String): Boolean {
        val ta = teamOf(a) ?: return false
        val tb = teamOf(b) ?: return false
        return ta != tb
    }

    fun teamHeroes(team: Team): List<HeroState> = state.heroes.filter { it.team == team }

    /** The first hero of [team] in seat order: the seat that answers team decisions. */
    fun teamSeat(team: Team): String = state.heroes.first { it.team == team }.id

    // ---- occupancy ---------------------------------------------------------------------------

    private var occupancyCache: HashMap<Hex, String>? = null
    private var sourcesCache: List<Pair<Behavior, Src>>? = null

    /** Call after any change of a position, piece, effect, marker or Ultimate (primitives do). */
    fun touch() {
        occupancyCache = null
        sourcesCache = null
        state.stamp++
    }

    /** What occupies each space: hero ids, minion ids, piece ids (p.18: one obstacle per space). */
    fun occupancy(): Map<Hex, String> {
        occupancyCache?.let { return it }
        val map = HashMap<Hex, String>()
        for (h in state.heroes) h.at?.let { check(map.put(it, h.id) == null) { "Two obstacles in $it" } }
        for (m in state.minions) m.at?.let { check(map.put(it, m.id) == null) { "Two obstacles in $it" } }
        for (p in state.pieces) p.at?.let { check(map.put(it, p.id) == null) { "Two obstacles in $it" } }
        occupancyCache = map
        return map
    }

    fun occupant(hex: Hex): String? = occupancy()[hex]

    // ---- behaviours in force -----------------------------------------------------------------

    /** Every behaviour in force with its source, in a stable order (coin team first, then seat). */
    fun sources(): List<Pair<Behavior, Src>> {
        sourcesCache?.let { return it }
        val reg = data.registry
        val out = ArrayList<Pair<Behavior, Src>>()
        for (h in orderedHeroes()) {
            reg.heroRule(h.id)?.let { out += it to Src(SrcKind.HERO, h.id, h.id) }
            if (h.at != null || hasBodyOnBoard(h.id)) {
                for (c in h.cards) {
                    if (c.zone == Zone.ULTIMATE) {
                        reg.behavior(c.id)?.let { out += it to Src(SrcKind.ULTIMATE, c.id, h.id, cardId = c.id) }
                    }
                }
            }
            for (e in state.effects) {
                if (e.owner == h.id && inForce(e)) {
                    reg.behavior(e.key ?: e.cardId)?.let {
                        out += it to Src(SrcKind.EFFECT, e.key ?: e.cardId, e.owner, cardId = e.cardId, effect = e)
                    }
                }
            }
            for (m in h.markers) {
                reg.behavior(m.kind)?.let { out += it to Src(SrcKind.MARKER, m.kind, m.from, marker = m, holder = h.id) }
            }
        }
        for (p in state.pieces) {
            if (p.at == null) continue
            reg.behavior(p.kind)?.let { out += it to Src(SrcKind.PIECE, p.kind, p.owner, piece = p) }
        }
        sourcesCache = out
        return out
    }

    private fun hasBodyOnBoard(heroId: String): Boolean = state.pieces.any { it.heroBody == heroId && it.at != null }

    /** Heroes ordered for simultaneous effects (p.17): the team on the coin first, then seat order. */
    fun orderedHeroes(): List<HeroState> =
        state.heroes.filter { it.team == state.tieBreaker } + state.heroes.filter { it.team != state.tieBreaker }

    /** p.17–19: is this Active effect in force right now? */
    fun inForce(e: ActiveEffect): Boolean {
        if (e.spent) return false
        return when (e.timing) {
            Timing.THIS_TURN -> e.round == state.round && e.turn == state.turn
            Timing.THIS_ROUND -> e.round == state.round
            Timing.NEXT_TURN -> e.round == state.round && state.turn == e.turn + 1
            Timing.UNTIL_CANCELLED -> true
            Timing.NEXT_TURN_AFTER_PLAYING, Timing.END_OF_TURN, Timing.END_OF_ROUND -> false
        }
    }

    // ---- small helpers -----------------------------------------------------------------------

    internal fun stamp(): Long = ++state.stamp

    companion object {
        fun teamName(team: Team): String = if (team == Team.RED) "Red" else "Blue"

        fun minionName(type: MinionType): String = when (type) {
            MinionType.MELEE -> "melee"
            MinionType.RANGED -> "ranged"
            MinionType.HEAVY -> "heavy"
        }
    }
}
