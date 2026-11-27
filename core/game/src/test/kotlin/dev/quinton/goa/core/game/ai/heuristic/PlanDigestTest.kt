package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import java.security.MessageDigest
import org.junit.Test

/**
 * Opt-in (Track V). `AI_PLAN_DIGEST="h9+plan=0;h9"` plays the same self-play games (every seat the spec) for each spec,
 * on line-ups `AI_H_FIRST` (default 2600) onward, `AI_PLAN_GAMES` of them (default 4) in 2v2 and half as many in 3v3,
 * and prints a SHA-256 of every answer (decision id, seat, kind, answer). Two specs, or this branch and a baseline
 * checkout, with equal digests answered every question alike. It also prints the level curve (mean level per team
 * at each round's start) and every level-up choice (the colour upgraded; the card kept and the item it leaves).
 */
class PlanDigestTest {
    @Test
    fun `answer digest, level curve and upgrades`() {
        val specs = System.getenv("AI_PLAN_DIGEST")?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 2600
        val n = System.getenv("AI_PLAN_GAMES")?.toIntOrNull() ?: 4
        for (spec in specs) {
            val md = MessageDigest.getInstance("SHA-256")
            val t0 = System.nanoTime()
            val levels = HashMap<Int, MutableList<Double>>()
            val picks = HashMap<String, Int>()
            var games = 0
            var decisions = 0
            val endings = HashMap<String, Int>()
            for (format in listOf(AiHarness.Format.TWO, AiHarness.Format.THREE)) {
                val count = if (format.size == 2) n else (n + 1) / 2
                val all = AiHarness.mixedLineups(first + count, size = format.size)
                for (i in first until first + count) {
                    val (red, blue) = all[i]
                    val coin = if (i % 2 == 0) Team.RED else Team.BLUE
                    val setup = AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin)
                    val players = mapOf(
                        Team.RED to recorder(player(spec, 50_000L + i), md, picks),
                        Team.BLUE to recorder(player(spec, 50_001L + i), md, picks),
                    )
                    var round = 0
                    val r = AiHarness.play(setup, players, observe = { s, _ ->
                        if (s.state.round != round) {
                            round = s.state.round
                            for (t in Team.entries) {
                                val hs = s.state.heroes.filter { it.team == t }
                                if (hs.isEmpty()) continue
                                levels.getOrPut(round) { ArrayList() } += hs.map { it.level }.average()
                            }
                        }
                    })
                    games++
                    decisions += r.decisions
                    val how = r.session.state.winReason?.toString() ?: "none"
                    endings[how] = (endings[how] ?: 0) + 1
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            val secs = (System.nanoTime() - t0) / 1e9
            println("PLAN_DIGEST $spec games=$games decisions=$decisions sha256=$hex in ${"%.0f".format(secs)} s")
            val curve = levels.keys.sorted().joinToString(" ") { r ->
                "r$r=${"%.2f".format(levels.getValue(r).average())}"
            }
            println("PLAN_LEVELS $spec $curve")
            println("PLAN_ENDINGS $spec $endings")
            val chosen = picks.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}:${it.value}" }
            println("PLAN_PICKS $spec $chosen")
        }
    }

    private fun player(spec: String, seed: Long): AiPlayer {
        val parts = spec.split("+")
        val strength = parts[0].drop(1).toInt()
        val tune = parts.drop(1).associate { it.substringBefore("=") to it.substringAfter("=").toDouble() }
        return HeuristicAi(strength, seed, tune = tune)
    }

    private fun recorder(inner: AiPlayer, md: MessageDigest, picks: MutableMap<String, Int>) = object : AiPlayer {
        override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
            val c = inner.choose(session, decision, cancelled)
            synchronized(md) { md.update("${decision.id} ${decision.seat} ${decision.kind} $c\n".toByteArray()) }
            if (decision.options.size > 1 && c is Choice.Card) {
                val data = (session as EngineSession).engineData
                when (decision.kind) {
                    DecisionKind.UPGRADE_CARD -> bump(picks, "up.${data.card(c.cardId).color.name.lowercase()}")

                    DecisionKind.KEEP_UPGRADE -> {
                        val item = decision.options.filterIsInstance<Choice.Card>().first { it != c }
                        bump(picks, "item.${data.card(item.cardId).item?.name?.lowercase()}")
                    }

                    else -> Unit
                }
            }
            return c
        }
    }

    private fun bump(m: MutableMap<String, Int>, k: String) {
        m[k] = (m[k] ?: 0) + 1
    }

    @Suppress("unused")
    private val engine = TestData.engine
}
