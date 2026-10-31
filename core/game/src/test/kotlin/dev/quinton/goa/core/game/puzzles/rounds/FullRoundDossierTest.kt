package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.domain.board.zoneName
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.puzzles.GoalState
import dev.quinton.goa.core.game.puzzles.Node
import dev.quinton.goa.core.game.puzzles.PreparedPuzzle
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.PuzzleSolver
import dev.quinton.goa.core.game.puzzles.Side
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import dev.quinton.goa.core.model.board.Hex
import java.io.File
import org.junit.Test

/**
 * Opt-in: `ROUND_DOSSIER=<dir>` writes, for each full round, every fact its text may use, all from the
 * engine: the position by name, the solution with what happened, what the plan does against each enemy
 * answer it could have given on that line, and draft plan lines for the known plays. A sentence in a
 * puzzle's text traces to this or to the rulebook, or it goes (ENCODING.md "Full rounds").
 */
class FullRoundDossierTest {
    private val data = TestData.engine.data

    @Test
    fun `write the dossiers`() {
        val dir = File(System.getenv("ROUND_DOSSIER") ?: return).also { it.mkdirs() }
        val only = System.getenv("ROUND_ONLY")?.split(",")?.toSet()
        for (p in Puzzles.fullRounds) {
            if (only != null && p.info.id !in only) continue
            File(dir, "${p.info.id}.txt").writeText(dossier(p))
        }
    }

    private fun name(st: GameState, id: String): String =
        st.hero(id)?.let { data.heroName(it.id) }
            ?: st.minion(id)?.let { "${it.team.name.lowercase()} ${it.type.name.lowercase()} minion" }
            ?: st.piece(id)?.label ?: id

    private fun zone(st: GameState, hex: Hex): String {
        val geo = data.geometry(st.mapId)
        return data.maps[st.mapId]?.zoneName(geo.zoneOf(hex)) ?: "a space"
    }

    /** "Lane Zone 2, next to Swift and the red melee minion". */
    private fun place(st: GameState, hex: Hex, except: String): String {
        val near = (
            st.heroes.filter {
                it.id != except && it.at != null && HexGrid.distance(it.at!!, hex) == 1
            }.map { data.heroName(it.id) } +
                st.minions.filter { it.at != null && HexGrid.distance(it.at!!, hex) == 1 }.map { name(st, it.id) }
            )
        return zone(st, hex) + if (near.isEmpty()) "" else ", next to " + near.joinToString(" and ")
    }

    private fun card(id: String): String {
        val c = data.card(id)
        val v = c.values
        val stats = listOfNotNull(
            c.initiative?.let { "init $it" },
            v.attack?.let { "A$it" },
            v.defense?.let { "D$it" },
            v.movement?.let { "M$it" },
            v.range?.let { "R$it" },
            v.radius?.let { "rad$it" },
        ).joinToString(" ")
        return "${c.name} [$id, ${c.primary}, $stats]${if (c.text.isBlank()) "" else " \"${c.text}\""}"
    }

    private fun position(st: GameState): String = buildString {
        appendLine(
            "Round ${st.round}. Coin: ${st.tieBreaker}. Battle Zone ${st.battleZones}. " +
                "Waves ${st.wavesLeft}. Life ${st.lifeCounters}.",
        )
        for (h in st.heroes) {
            appendLine(
                "${data.heroName(h.id)} (${h.team}, level ${h.level}, ${h.coins} coins) in ${h.at?.let {
                    place(st, it, h.id)
                } ?: "off the board"}",
            )
            h.inZone(Zone.HAND).forEach { appendLine("    hand: ${card(it.id)}") }
            h.inZone(Zone.ITEM).forEach { appendLine("    item: ${data.card(it.id).item}") }
            if (h.markers.isNotEmpty()) appendLine("    markers: ${h.markers.map { it.kind }}")
            if (h.counters.isNotEmpty()) appendLine("    counters: ${h.counters}")
        }
        val byZone = st.minions.groupBy { m -> m.at?.let { zone(st, it) } ?: "off" }
        for ((z, ms) in byZone) appendLine("Minions in $z: ${ms.joinToString { name(st, it.id) }}")
        if (st.pieces.isNotEmpty()) {
            appendLine(
                "Pieces: ${st.pieces.joinToString {
                    "${it.label} in ${it.at?.let { a -> zone(st, a) }}"
                }}",
            )
        }
    }

    private fun answer(st: GameState, c: Choice): String = when (c) {
        is Choice.Card -> data.card(c.cardId).name
        is Choice.Action -> c.action.name.lowercase()
        is Choice.Space -> place(st, c.hex, st.acting.orEmpty())
        is Choice.Unit -> name(st, c.unitId)
        is Choice.Token -> st.piece(c.tokenId)?.label ?: c.tokenId
        is Choice.Option -> c.label
        is Choice.Number -> "${c.value}"
        is Choice.Decline -> "decline (${c.label})"
        else -> c.toString()
    }

    private fun lineText(solver: PuzzleSolver, line: List<LineStep>, from: Int): String = buildString {
        var seen = from
        for (s in line) {
            val st = s.at.session.state
            val side = solver.sideOf(s.decision)
            if (s.decision.options.size > 1 || s.decision.kind.name == "PLAY_CARD") {
                val who = if (side == Side.PLAYER) "YOU" else "them"
                appendLine(
                    "  [T${PlanProver.turnOf(
                        s.at,
                    )}] $who ${data.heroName(
                        s.decision.seat,
                    )} ${s.decision.kind} (${s.decision.prompt}): ${answer(
                        st,
                        s.choice,
                    )}  {${solver.choices(s.at).size} of ${s.decision.options.size}}",
                )
            }
            val ev = s.next.session.events
            for (e in ev.drop(seen)) {
                if (e !is GameEvent.Note ||
                    "plays a card" !in e.text
                ) {
                    appendLine("        ${e.text}")
                }
            }
            seen = ev.size
        }
    }

    private fun dossier(p: Puzzle): String = buildString {
        val prep = RoundCertificate.prepared(p)
        val cert = checkNotNull(RoundCertificate.load(p.info.id)) { p.info.id }
        val solver = prep.solver
        val line = cert.line(prep)
        appendLine("=== ${p.info.id}: player ${p.playerSeats}; goal ${p.info.goal}; grade ${p.info.difficulty}")
        val st = prep.rootNode.session.state
        append(position(st))
        appendLine("=== known")
        val known = checkNotNull(p.known)
        for ((h, cards) in known.cards) {
            cards.forEachIndexed { i, c ->
                appendLine(
                    "${data.heroName(
                        h,
                    )} turn ${i + 1}: ${card(
                        c,
                    )}${if (known.actions[h]?.containsKey(i + 1) == true) "  (action known)" else "  (free)"}",
                )
            }
        }
        appendLine("=== draft plans")
        append(plans(p, line))
        appendLine("=== solution line")
        append(lineText(solver, line, prep.rootNode.session.events.size))
        appendLine("=== other enemy answers on the line (what the plan does then)")
        append(alternatives(prep, cert, line))
    }

    /** One line per known play, from what it did on the solution line. */
    private fun plans(p: Puzzle, line: List<LineStep>): String = buildString {
        val known = checkNotNull(p.known)
        for ((hero, turns) in known.actions) {
            for (turn in turns.keys.sorted()) {
                val steps = line.filter {
                    it.decision.seat == hero && it.at.session.state.turn == turn &&
                        it.at.session.state.acting == hero
                }
                val parts = ArrayList<String>()
                for (s in steps) {
                    val st = s.next.session.state
                    for (e in s.next.session.events.drop(s.at.session.events.size)) {
                        when (e) {
                            is GameEvent.Moved -> if (e.unitId ==
                                hero
                            ) {
                                parts += "moves to ${place(st, e.path.last(), hero)}"
                            }

                            is GameEvent.Placed -> if (e.pieceId == hero) parts += "travels to ${place(st, e.to, hero)}"

                            is GameEvent.Attacked -> parts += "attacks ${name(s.at.session.state, e.targetId)}"

                            is GameEvent.ActionChosen -> if (e.action.name == "HOLD") parts += "holds"

                            else -> Unit
                        }
                    }
                }
                val text = parts.joinToString(", then ").replaceFirstChar {
                    it.uppercase()
                }.ifEmpty { "Uses its skill" }
                appendLine("plan $hero $turn: $text.")
            }
        }
    }

    private fun alternatives(prep: PreparedPuzzle, cert: RoundCertificate, line: List<LineStep>): String = buildString {
        val solver = prep.solver
        val plan = cert.plan()
        var shown = 0
        for (s in line) {
            val d = s.decision
            if (solver.sideOf(d) != Side.OPPONENT) continue
            val others = solver.choices(s.at).filter { it != s.option }
            if (others.isEmpty()) continue
            val st = s.at.session.state
            appendLine(
                "[T${PlanProver.turnOf(
                    s.at,
                )}] ${data.heroName(d.seat)} ${d.kind} (${d.prompt}): line plays ${answer(st, s.choice)}",
            )
            for (i in others.take(MAX_OTHERS)) {
                if (shown++ > MAX_SHOWN) return@buildString
                val c: Node = solver.child(s.at, d.options[i])!!
                if (c.progress.state != GoalState.OPEN || c.decision == null) {
                    appendLine("    ${answer(st, d.options[i])}: the goal is met at once")
                    continue
                }
                val rest = PlanProver.mainLine(solver, c, plan)
                val mine = rest.filter {
                    solver.sideOf(it.decision) == Side.PLAYER && it.decision.options.size > 1
                }.take(MAX_MOVES)
                    .joinToString("; ") { answer(it.at.session.state, it.choice) }
                val end = rest.lastOrNull()?.let { PlanProver.turnOf(it.next) } ?: PlanProver.turnOf(c)
                appendLine("    ${answer(st, d.options[i])}: plan goes on $mine; goal met in turn $end")
            }
        }
    }

    private companion object {
        const val MAX_OTHERS = 6
        const val MAX_SHOWN = 40
        const val MAX_MOVES = 4
    }
}
