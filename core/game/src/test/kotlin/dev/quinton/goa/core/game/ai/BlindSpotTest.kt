package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.learned.Learned
import dev.quinton.goa.core.game.ai.learned.Teams
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Opt-in: `AI_BLIND=<variant>:<games>` (variant as in [Learned.side], 3v3 on the tune line-ups) plays
 * games and, at every question with a real choice that is not a card play, runs the player's own search
 * again and counts how often it sees no difference between the options: the choices whose effect
 * comes after the playouts end, where the answer is a coin toss (AI.md "Known weaknesses").
 */
class BlindSpotTest {
    private class Row(val key: String, val blind: Boolean, val spread: Double)

    @Test
    fun `questions the search cannot tell apart`() {
        val spec = System.getenv("AI_BLIND")?.split(":") ?: return
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 4
        val games = spec[1].toInt()
        val side = Learned.side(spec[0])
        val profile = Learned.profile(spec[0])
        val pool = Executors.newFixedThreadPool(threads)
        val rows = try {
            pool.invokeAll(
                Teams.specs(games, 3, Teams.TUNE).map { g ->
                    Callable {
                        val (red, blue) = g.lineup
                        val s = TestData.engine.newGame(
                            AiHarness.setup(red, blue, AiLevel.HARD, AiLevel.HARD, g.coin),
                        ) as EngineSession
                        val players = mapOf(Team.RED to side.factory(g.seed), Team.BLUE to side.factory(g.seed + 1))
                        val searcher = Searcher(Evaluator(s.engineData, profile.knobs), profile)
                        val out = ArrayList<Row>()
                        var n = 0
                        while (n++ < 8000) {
                            val d = s.pending ?: break
                            if (d.options.size > 1) look(searcher, s, d)?.let { out += it }
                            s.answer(d.id, players.getValue(d.team).choose(s, d))
                        }
                        out
                    }
                },
            ).flatMap { it.get() }
        } finally {
            pool.shutdown()
        }
        for ((key, list) in rows.groupBy { it.key }.entries.sortedByDescending { e -> e.value.count { it.blind } }) {
            println(
                "BLIND %-58s %5d asked, %5d no difference (%3.0f%%), mean spread %.1f".format(
                    key, list.size, list.count { it.blind }, 100.0 * list.count { it.blind } / list.size,
                    list.map { it.spread }.average(),
                ),
            )
        }
    }

    private fun look(searcher: Searcher, s: EngineSession, d: Decision): Row? {
        val a = searcher.analyze(s, d, 11L + d.id, { false }, Long.MAX_VALUE) ?: return null
        val means = a.options.map { it.mean }.filter { it.isFinite() }
        if (means.size < 2) return null
        val spread = means.max() - means.min()
        val prompt = d.prompt.replace(Regex("[0-9]+"), "#").take(44)
        return Row("${d.seat} ${d.kind} $prompt", spread < 0.01, spread)
    }
}
