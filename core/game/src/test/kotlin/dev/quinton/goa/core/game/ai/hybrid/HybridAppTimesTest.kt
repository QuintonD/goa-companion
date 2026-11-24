package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in timing probe for the app's think time (wave 4 track A; not a tournament: strength 10 against the book
 * at 9 on a few dev line-ups, 100+, nowhere near the held-out 4000+ or the sealed 5000+). `AI_APP_TIMES=2:2,3:1`
 * plays two 2v2 games and one 3v3, strength 10 on one thread as the web worker runs it, one player per team
 * kept for the game as the apps keep it. For every searched answer it prints its time, and at card plays also
 * the answer a player with the budget `AI_APP_BUDGETS` (default 2000,667: the app's 2 s, and 2 s on a phone
 * about 3× slower than this machine) gives, so the share of answers a budget changes is measured offline.
 */
class HybridAppTimesTest {
    @Test
    fun `decision times on one thread`() {
        val spec = System.getenv("AI_APP_TIMES") ?: return
        // At most this many searched answers of strength 10 a game (a short probe), the first (warm-up) unprinted.
        val maxTen = System.getenv("AI_APP_MAX")?.toIntOrNull() ?: Int.MAX_VALUE
        val budgets = (System.getenv("AI_APP_BUDGETS") ?: "2000,667").split(",").map { it.trim().toLong() }
        val knobs = AiPlayers.SEARCH_10 + ("leaf" to AiPlayers.SEARCH_10_LEAF.ordinal.toDouble())
        var game = 0
        for (part in spec.split(",")) {
            val (size, n) = part.split(":").map { it.trim().toInt() }
            for (k in 0 until n) {
                val lineup = FIRST_LINEUP + game
                val (red, blue) = AiHarness.mixedLineups(lineup + 1, seed = SEED, size = size)[lineup]
                val tenTeam = if (game % 2 == 0) Team.BLUE else Team.RED
                val s = TestData.engine.newGame(
                    AiHarness.setup(red, blue, AiLevel.EXPERT, AiLevel.EXPERT, Team.entries[game % 2]),
                ) as EngineSession
                val ten = HybridAi(AiPlayers.APP_SEED + 10, knobs)
                val timed = budgets.map { HybridAi(AiPlayers.APP_SEED + 10, knobs + ("ms" to it.toDouble())) }
                val book = AiPlayers.deterministic(9, SEED + game)
                var answers = 0
                var peakMemo = 0
                var searched = 0
                while (answers < MAX_ANSWERS && searched < maxTen) {
                    val d = s.pending ?: break
                    val c = if (d.team == tenTeam && d.options.size > 1) {
                        val t0 = System.nanoTime()
                        val a = ten.choose(s, d)
                        val ms = (System.nanoTime() - t0) / 1_000_000
                        peakMemo = maxOf(peakMemo, ten.memoSize)
                        searched++
                        val agree = if (d.kind == DecisionKind.PLAY_CARD) {
                            timed.joinToString(" ") { p -> if (p.choose(s, d) == a) "=" else "x" }
                        } else {
                            "-"
                        }
                        if (game > 0 || searched > 1) println(
                            "APP_TIME size=$size game=$game round=${s.state.round} turn=${s.state.turn} " +
                                "kind=${d.kind} options=${d.options.size} ms=$ms memo=${ten.memoSize} agree=$agree",
                        )
                        a
                    } else {
                        book.choose(s, d)
                    }
                    check(s.answer(d.id, c) is AnswerResult.Accepted)
                    answers++
                }
                println(
                    "APP_GAME size=$size game=$game rounds=${s.state.round} winner=${s.state.winner} " +
                        "peakMemo=$peakMemo stats=${ten.stats} budgets=$budgets cut=${timed.map { it.stats[4] }}",
                )
                game++
            }
        }
    }

    private companion object {
        const val SEED = 20_261_124L
        const val FIRST_LINEUP = 100
        const val MAX_ANSWERS = 3_000
    }
}
