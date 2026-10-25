package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Opt-in: `AI_BLUNDERS=<strength>:<games>[:<first line-up>]` plays self-play games on mixed
 * line-ups and counts the plays a decent player would call blunders (AI.md "Known weaknesses"),
 * judged on the real game (this is a diagnosis, not a player: it may look at every card):
 * - **declined a defense** it could make (a card whose Defense total holds), or defended with a
 *   card that falls while one that holds was in hand;
 * - **left a defeat on the table:** an action that defeats an enemy hero (played out with the
 *   rules of thumb) was offered and another was chosen that defeats none, pushes nothing, wins nothing;
 * - **walked into a defeat:** the hero moved, was defeated later in the same turn, and another
 *   destination of that move (the rest of the turn played out the same way) was not defeated.
 * `AI_BLUNDERS_SHOW=n` prints the first n of each with the game and the turn.
 */
class BlunderTest {
    private class Found(val kind: String, val text: String)

    @Test
    fun `count blunders in self-play`() {
        val spec = System.getenv("AI_BLUNDERS")?.split(":") ?: return
        val strength = spec[0].toInt()
        val games = spec.getOrNull(1)?.toInt() ?: 24
        val first = spec.getOrNull(2)?.toInt() ?: 300
        val lineups = AiHarness.mixedLineups(first + games).drop(first)
        val pool = Executors.newFixedThreadPool(System.getenv("AI_THREADS")?.toIntOrNull() ?: 4)
        val results = try {
            pool.invokeAll(lineups.mapIndexed { i, (r, b) -> Callable { game(i, r, b, strength) } }).map { it.get() }
        } finally {
            pool.shutdown()
        }
        val all = results.flatMap { it.second }
        val decisions = results.sumOf { it.first }
        println("BLUNDERS strength $strength, $games games, $decisions decisions")
        val show = System.getenv("AI_BLUNDERS_SHOW")?.toIntOrNull() ?: 5
        for ((kind, list) in all.groupBy { it.kind }) {
            println("BLUNDERS $kind: ${list.size} (${"%.2f".format(list.size.toDouble() / games)} per game)")
            list.take(show).forEach { println("BLUNDERS   ${it.text}") }
        }
    }

    private fun game(i: Int, red: List<String>, blue: List<String>, strength: Int): Pair<Int, List<Found>> {
        val level = AiLevel.of(strength)
        val s = TestData.engine.newGame(
            AiHarness.setup(red, blue, level, level, if (i % 2 == 0) Team.RED else Team.BLUE),
        ) as EngineSession
        val players = mapOf(
            Team.RED to AiPlayers.deterministic(strength, 9_000L + 2 * i),
            Team.BLUE to AiPlayers.deterministic(strength, 9_001L + 2 * i),
        )
        val judge = Searcher(Evaluator(s.engineData), AiPlayers.profile(AiLevel.HARD))
        val found = ArrayList<Found>()
        // This turn's moves of each hero: the session before the move, the decision, the choice.
        val moves = ArrayList<Triple<EngineSession, Decision, Choice>>()
        var turn = -1
        var n = 0
        var asked = 0
        val where = {
            "game $i (${red.joinToString("+")} vs ${blue.joinToString("+")}) R${s.state.round}T${s.state.turn}"
        }
        while (n < 8000) {
            val d = s.pending ?: break
            val t = Searcher.turnIndex(s.state)
            if (t != turn) {
                moves.clear()
                turn = t
            }
            val c = players.getValue(d.team).choose(s, d)
            if (d.options.size > 1) asked++
            when (d.kind) {
                DecisionKind.DEFEND -> defense(d, c)?.let { found += Found(it, "${where()} ${d.seat}: $c") }

                DecisionKind.CHOOSE_ACTION -> if (d.options.size > 1) {
                    leftKill(s, d, c, judge)?.let { found += Found("left a defeat on the table", "${where()} $it") }
                }

                DecisionKind.CHOOSE_SPACE -> {
                    val subject = d.context.subjectId ?: d.seat
                    if (subject == d.seat && d.options.size > 1 && d.prompt.startsWith("Move")) {
                        moves += Triple(s.forkSession(), d, c)
                    }
                }

                else -> Unit
            }
            val before = s.events.size
            s.answer(d.id, c)
            for (e in s.events.drop(before)) {
                if (e !is GameEvent.Defeated || s.state.hero(e.unitId) == null) continue
                val move = moves.lastOrNull { it.second.seat == e.unitId } ?: continue
                avoidable(move, e.unitId, judge)?.let {
                    found += Found("walked into a defeat", "${where()} ${e.unitId} moved to ${move.third}, $it")
                }
            }
            n++
        }
        return asked to found
    }

    private fun defense(d: Decision, c: Choice): String? {
        val total = d.context.attackTotal ?: return null
        val preview = d.context.defensePreview
        if (d.options.none { it is Choice.Card && (preview[it.cardId] ?: -1) >= total }) return null
        return when {
            c is Choice.Decline -> "declined a defense"
            c is Choice.Card && (preview[c.cardId] ?: Int.MAX_VALUE) < total -> "defended with a card that falls"
            else -> null
        }
    }

    /** Each action played out to its end with the rules of thumb: does it defeat an enemy hero? */
    private fun leftKill(s: EngineSession, d: Decision, c: Choice, judge: Searcher): String? {
        fun outcome(o: Choice): Pair<Boolean, Boolean> {
            val f = s.forkSession()
            val before = f.events.size
            f.answer(d.id, o)
            judge.playout(f, d.seat, d.team, Horizon.ACTION)
            val ev = f.events.drop(before)
            val kill = ev.any {
                it is GameEvent.Defeated &&
                    f.state.hero(it.unitId)?.team?.let { t -> t != d.team } == true
            }
            val gain = ev.any { it is GameEvent.LanePushed && it.winner == d.team } || f.state.winner == d.team
            return kill to gain
        }
        val chosen = outcome(c)
        if (chosen.first || chosen.second) return null
        val killer = d.options.firstOrNull { it != c && outcome(it).first } ?: return null
        return "chose $c over $killer, which defeats an enemy hero"
    }

    /** Another destination of the move, the rest of the turn played out the same way, not defeated. */
    private fun avoidable(move: Triple<EngineSession, Decision, Choice>, hero: String, judge: Searcher): String? {
        val (before, d, chosen) = move
        fun defeated(o: Choice): Boolean {
            val f = before.forkSession()
            val start = f.events.size
            f.answer(d.id, o)
            judge.playout(f, d.seat, d.team, Horizon.TURN)
            return f.events.drop(start).any { it is GameEvent.Defeated && it.unitId == hero }
        }
        // The judge's own play-out must agree that the chosen space loses the hero, or it proves nothing.
        if (!defeated(chosen)) return null
        val safe = d.options.filter { it != chosen }.firstOrNull { !defeated(it) } ?: return null
        return "$safe was safe"
    }
}
