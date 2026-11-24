package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.ThreadRunner
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in (Wave 4 Track G): the card plays of cheap book-vs-book games (h10 both sides, line-ups
 * `AI_H_FIRST`…, `AI_H_GAMES`, `AI_FORMAT`; never the integrator's 4000–5999) asked of strength 10 as it
 * ships and of strength 10 with `eq=1`, both timed on `AI_THREADS` threads. Each card play prints one
 * `EQ {…}` line: the matrix (our win chance, our cards × the enemy's likeliest joint plays), the model's
 * column weights, the exact equilibrium, the pick, and strength 10's own card; `tools`-free parsing in
 * Python gives RESULTS.md's numbers (saddle share, disagreement, entropy, exploitability). The games go on
 * with h10's answers, so the positions are the book's games, not the searches'.
 */
class CardEquilibriumProbeTest {
    @Test
    fun `card play matrices`() {
        if (System.getenv("AI_EQ_PROBE") == null) return
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 2
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 1000
        require(first + lineups <= 4000 || first >= 6000) { "line-ups 4000–5999 are the integrator's" }
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val maxPlays = System.getenv("AI_EQ_MAX")?.toIntOrNull() ?: 200
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val knobs = AiPlayers.SEARCH_10 + ("leaf" to AiPlayers.SEARCH_10_LEAF.ordinal.toDouble())
        val all = AiHarness.mixedLineups(first + lineups, size = format.size)
        var plays = 0
        ThreadRunner(threads).use { runner ->
            for (g in first until first + lineups) {
                if (plays >= maxPlays) break
                val (red, blue) = all[g]
                val coin = if (g % 2 == 0) Team.RED else Team.BLUE
                val seed = (70_000L + g) * 2
                val cur = HybridAi(seed, knobs, runner)
                val eq = HybridAi(seed, knobs + ("eq" to 1.0), runner)
                val books = mapOf(Team.RED to HeuristicAi(10, seed + 1), Team.BLUE to HeuristicAi(10, seed + 2))
                val setup = AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin, format.length, format.mapId)
                val s = TestData.engine.newGame(setup) as EngineSession
                var n = 0
                while (n < MAX_DECISIONS) {
                    val d = s.pending ?: break
                    val c = books.getValue(d.team).choose(s, d)
                    val probe = d.kind == DecisionKind.PLAY_CARD && d.options.size >= 2 &&
                        d.options.all { it is Choice.Card } && plays < maxPlays
                    if (probe) {
                        var t0 = System.nanoTime()
                        val now = cur.choose(s, d)
                        val msCur = (System.nanoTime() - t0) / 1e6
                        var line: String? = null
                        eq.equilibriumProbe = { _, m, p -> line = describe(m, p) }
                        t0 = System.nanoTime()
                        val eqAnswer = eq.choose(s, d)
                        val msEq = (System.nanoTime() - t0) / 1e6
                        eq.equilibriumProbe = null
                        plays++
                        println(
                            "EQ {\"game\":$g,\"id\":${d.id},\"round\":${s.state.round},\"turn\":${s.state.turn}," +
                                "\"seat\":\"${d.seat}\",\"team\":\"${d.team}\",\"heroes\":${s.state.heroes.size}," +
                                "\"cur\":\"${(now as? Choice.Card)?.cardId}\",\"eqAnswer\":\"${(eqAnswer as? Choice.Card)?.cardId}\"," +
                                "\"msCur\":${"%.0f".format(msCur)},\"msEq\":${"%.0f".format(msEq)}," +
                                "\"m\":${line ?: "null"}}",
                        )
                    }
                    check(s.answer(d.id, c) is AnswerResult.Accepted) { "rejected $c" }
                    n++
                }
                println("EQ game $g ${red + blue}: winner ${s.state.winner}, round ${s.state.round}, plays so far $plays")
            }
        }
    }

    private fun describe(m: CardEquilibrium.Matrix, p: CardEquilibrium.Pick): String {
        fun arr(a: DoubleArray) = a.joinToString(",", "[", "]") { "%.6f".format(it) }
        val rows = m.rows.joinToString(",", "[", "]") { "\"${(it as? Choice.Card)?.cardId}\"" }
        val cols = m.columns.joinToString(",", "[", "]") { c ->
            c.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
        }
        return "{\"rows\":$rows,\"cols\":$cols,\"w\":${arr(m.columnWeight)},\"mass\":${"%.4f".format(m.mass)}," +
            "\"down\":${m.enemiesDown},\"toPlay\":${m.enemiesToPlay}," +
            "\"a\":${m.payoff.joinToString(",", "[", "]") { arr(it) }}," +
            "\"row\":${p.row},\"argmax\":${p.argmax},\"saddle\":${p.saddle},\"value\":${"%.6f".format(p.solution.value)}," +
            "\"p\":${arr(p.solution.row)},\"q\":${arr(p.solution.column)},\"played\":${arr(p.played)}}"
    }

    private companion object {
        const val MAX_DECISIONS = 8000
    }
}
