package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in: `AI_LOG=RED_LEVEL:BLUE_LEVEL[:seed[:lineup]]` prints one game's log, to read how a level
 * plays. The line-up is an index into the first-game line-ups, `m<i>` for the i-th mixed line-up, or
 * hero ids joined by `+` (Red first, then Blue). A side is a level or a variant as in [ExperimentTest]
 * (`s10`, `s10+hunt=0/2/1.5`); `AI_FORMAT=3v3` for bigger teams. `AI_LOG_DECISIONS` also prints every choice.
 */
class GameLogTest {
    @Test
    fun `print one game`() {
        val spec = System.getenv("AI_LOG")?.split(":") ?: return
        val seed = spec.getOrNull(2)?.toLong() ?: 1L
        val l = spec.getOrNull(3) ?: "0"
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val (r, b) = when {
            "+" in l -> l.split("+").let { it.take(it.size / 2) to it.drop(it.size / 2) }
            l.startsWith("m") -> AiHarness.mixedLineups(l.drop(1).toInt() + 1, size = format.size).last()
            else -> AiHarness.LINEUPS[l.toInt()]
        }
        // A side is a level (HARD) or a strength or variant as in [ExperimentTest] (s10+hunt=0/2/1.5).
        fun side(x: String): Pair<AiLevel, (Long) -> dev.quinton.goa.core.game.AiPlayer> =
            AiLevel.entries.firstOrNull { it.name == x }?.let { lv -> lv to { sd: Long -> AiPlayers.deterministic(lv, sd) } }
                ?: ExperimentTest.side(x).let { sd -> AiLevel.HARD to sd.factory }
        val (red, redPlayer) = side(spec[0])
        val (blue, bluePlayer) = side(spec[1])
        val s = TestData.engine.newGame(AiHarness.setup(r, b, red, blue, Team.RED, format.length, format.mapId))
        val players = mapOf(
            Team.RED to redPlayer(seed),
            Team.BLUE to bluePlayer(seed + 1),
        )
        val decisions = System.getenv("AI_LOG_DECISIONS") != null
        // AI_LOG_VALUES: Hard-level playout values of every option of card plays and actions.
        val values = System.getenv("AI_LOG_VALUES") != null
        // AI_LOG_LEAVES=<hero>: also the playout events of the first sample for that hero's actions.
        val leavesOf = System.getenv("AI_LOG_LEAVES")
        val searcher = Searcher(
            Evaluator(TestData.engine.data),
            AiPlayers.profile(AiLevel.HARD),
            keepLeaves = leavesOf != null,
        )
        println("LOG Red ${r.joinToString()} (${spec[0]}) vs Blue ${b.joinToString()} (${spec[1]})")
        var printed = 0
        var n = 0
        while (n < 8000) {
            val d = s.pending ?: break
            val c = players.getValue(d.team).choose(s, d)
            if (decisions && d.options.size > 1) println("LOG   ? ${d.seat} ${d.kind} ${d.prompt} -> $c")
            if (values && d.options.size > 1 && d.kind in VALUED) {
                val a = searcher.analyze(s as EngineSession, d, 7L + d.id, { false }, Long.MAX_VALUE)
                a?.options?.sortedByDescending { it.mean }?.forEach {
                    println("LOG       ${"%7.2f".format(it.mean)} ±${"%.1f".format(it.sd)} ${it.choice}")
                }
                if (d.seat == leavesOf && d.kind == DecisionKind.CHOOSE_ACTION) {
                    a?.leaves?.filter { it.sample == 0 }?.forEach { l ->
                        println("LOG       leaf ${l.choice}:")
                        l.end.events.drop(l.startEvents).forEach { println("LOG         ${it.text}") }
                    }
                }
            }
            s.answer(d.id, c)
            for (e in s.events.drop(printed)) println("LOG ${e.text}")
            printed = s.events.size
            n++
        }
        val v = s.view(null)
        println("LOG winner ${v.winner} ${v.winReason} round ${v.round}")
    }

    companion object {
        private val VALUED = setOf(DecisionKind.PLAY_CARD, DecisionKind.CHOOSE_ACTION, DecisionKind.DEFEND)
    }
}
