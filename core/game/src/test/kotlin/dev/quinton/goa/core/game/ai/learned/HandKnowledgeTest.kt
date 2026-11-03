package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineSession
import java.io.File
import org.junit.Test

/**
 * Opt-in (`AI_HANDS=<games.jsonl>,…:<games>`): what knowing the enemies' hands (ADR-062) removes in
 * practice. Replays stored games and, at every card play, counts for each enemy hero of the standard
 * shape the upgrade histories consistent with what the deciding team sees with strength 9–10's memory,
 * against those left once the hand-plus-played pool is known, and how many different hand pools they deal.
 */
class HandKnowledgeTest {
    @Test
    fun `upgrade histories removed by knowing the enemies' hands`() {
        val spec = System.getenv("AI_HANDS") ?: return
        val files = spec.substringBeforeLast(":").split(",").map { File(it) }
        val limit = spec.substringAfterLast(":").toInt()
        val data = TestData.engine.data
        val games = Learned.readGames(files).filter { it.size == (System.getenv("AI_SIZE")?.toIntOrNull() ?: 3) }
            .take(limit)
        // Per level of the enemy hero: looks, histories (memory), histories (hands), pools (memory), ambiguous.
        val byLevel = sortedMapOf<Int, LongArray>()
        var other = 0L
        for (r in games) {
            val s = TestData.engine.newGame(r.setup) as EngineSession
            for (c in r.answers) {
                val d = s.pending ?: break
                if (d.kind == DecisionKind.PLAY_CARD) {
                    val seen = Searcher.seenCards(s, d.team)
                    for (h in s.state.heroes.filter { it.team != d.team }) {
                        val mem = Determinizer.historyCount(data, s.state, h.id, seen[h.id].orEmpty(), false)
                        val known = Determinizer.historyCount(data, s.state, h.id, seen[h.id].orEmpty(), true)
                        if (mem == null || known == null) {
                            other++
                            continue
                        }
                        val row = byLevel.getOrPut(h.level) { LongArray(6) }
                        row[0]++
                        row[1] += mem.first.toLong()
                        row[2] += known.first.toLong()
                        row[3] += mem.second.toLong()
                        row[4] += if (mem.second > 1) 1 else 0
                        row[5] += known.second.toLong()
                    }
                }
                if (s.answer(d.id, c) !is AnswerResult.Accepted) break
            }
        }
        println("HANDS ${games.size} games; enemy heroes at card plays not of the standard shape: $other")
        val total = LongArray(6)
        for ((level, row) in byLevel) {
            for (i in row.indices) total[i] += row[i]
            println("HANDS " + line("level $level", row))
        }
        println("HANDS " + line("all", total))
    }

    private fun line(label: String, r: LongArray): String {
        val n = r[0].toDouble().coerceAtLeast(1.0)
        return "%s: %d looks, histories %.2f -> %.2f, hand pools %.2f -> %.2f, pool unknown in %.1f%%".format(
            label, r[0], r[1] / n, r[2] / n, r[3] / n, r[5] / n, 100.0 * r[4] / n,
        )
    }
}
