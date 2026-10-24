package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Opt-in: `AI_USAGE=<LEVEL>:<line-ups>` plays mixed line-ups with that level on both sides and prints,
 * per hero and per card, how often each action was chosen (Primary, Movement, Fast Travel, Hold…)
 * and how often the card defended: a hero whose cards are never used for their Primary action is
 * not being played (AI.md "Known weaknesses").
 */
class UsageTest {
    @Test
    fun `how every hero's cards are used`() {
        val spec = System.getenv("AI_USAGE")?.split(":") ?: return
        val level = AiLevel.valueOf(spec[0])
        val n = spec.getOrNull(1)?.toInt() ?: 32
        val lineups = AiHarness.mixedLineups(n)
        val jobs = lineups.mapIndexed { i, (red, blue) ->
            Callable {
                AiHarness.play(
                    AiHarness.setup(red, blue, level, level, if (i % 2 == 0) Team.RED else Team.BLUE),
                    mapOf(
                        Team.RED to AiPlayers.deterministic(level, 900L + i),
                        Team.BLUE to AiPlayers.deterministic(level, 950L + i),
                    ),
                ).session.events
            }
        }
        val pool = Executors.newFixedThreadPool(System.getenv("AI_THREADS")?.toIntOrNull() ?: 6)
        val all = try {
            pool.invokeAll(jobs).map { it.get() }
        } finally {
            pool.shutdown()
        }
        val actions = sortedMapOf<String, MutableMap<ActionType, Int>>()
        val defended = HashMap<String, Int>()
        for (events in all) {
            for (e in events) {
                when (e) {
                    is GameEvent.ActionChosen -> {
                        val m = actions.getOrPut(e.cardId) { sortedMapOf() }
                        m[e.action] = (m[e.action] ?: 0) + 1
                    }

                    is GameEvent.Defended -> e.cardId?.let { defended[it] = (defended[it] ?: 0) + 1 }

                    else -> Unit
                }
            }
        }
        val data = TestData.engine.data
        for (hero in TestData.engine.playableHeroes.sorted()) {
            val cards = actions.keys.filter { data.card(it).let { c -> c.id.startsWith("$hero-") } }
            val tot = cards.sumOf { actions.getValue(it).values.sum() }
            val prim = cards.sumOf { actions.getValue(it)[ActionType.PRIMARY] ?: 0 }
            val hold = cards.sumOf { actions.getValue(it)[ActionType.HOLD] ?: 0 }
            println(
                "USAGE $hero: $tot actions, primary ${pct(prim, tot)}, hold ${pct(hold, tot)} | " +
                    cards.joinToString("; ") { c ->
                        val m = actions.getValue(c)
                        "${data.card(c).name} " + m.entries.joinToString(" ") { "${it.key.name.take(2)}${it.value}" } +
                            (defended[c]?.let { " DEF$it" } ?: "")
                    },
            )
        }
    }

    private fun pct(a: Int, b: Int) = if (b == 0) "-" else "${100 * a / b}%"
}
