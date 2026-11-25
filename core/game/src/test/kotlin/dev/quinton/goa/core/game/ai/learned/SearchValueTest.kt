package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.ai.hybrid.HybridAi
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Opt-in (Wave 3 Track E2): the search's own values of recorded positions, as training targets for the leaf.
 *
 * `AI_SV=<games.jsonl>,…:<out.tsv>` replays every game and, at each turn boundary the trainer records
 * ([Learned.positions] with no resolved positions, so the indices match), lets the search (`AI_SV_SPEC`, default
 * `W2+leaf=3`) answer the first card play of each team in that turn and writes its backed-up values: one line per
 * (game, position, team) with the value of the card it would play, the best mean, the value of the card that was
 * played and the number of candidates, from that team's view, in the leaf's units (coin-equivalents). The trainer
 * reads them with `AI_SV` ([LearnedTrainTest]). Nothing here changes a game: the search only answers on copies.
 * `AI_THREADS` games at once; `AI_SV_SECONDS` stops starting games after that long (split runs), and a game
 * already in the output is skipped, so a run picks up where the last one stopped. The key is the input file's
 * name, the game and the position index (two files can hold the same game number).
 */
class SearchValueTest {
    @Test
    fun `search values of recorded positions`() {
        val spec = System.getenv("AI_SV")?.split(":", limit = 2) ?: return
        val files = spec[0].split(",").map(::File)
        val out = File(spec[1])
        val side = System.getenv("AI_SV_SPEC") ?: "W2+leaf=3"
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 3
        val seconds = System.getenv("AI_SV_SECONDS")?.toLong() ?: Long.MAX_VALUE
        val done = if (out.exists()) {
            out.readLines().filter { it.isNotBlank() }.map { it.split("\t").let { f -> f[0] to f[1].toInt() } }.toSet()
        } else {
            emptySet()
        }
        val todo = files.flatMap { f ->
            Learned.readGames(listOf(f)).filter { it.winner != null && (tag(f) to it.game) !in done }.map { f to it }
        }
        println("SV ${todo.size} games to search (${done.size} already in $out), spec $side")
        val t0 = System.nanoTime()
        val pool = Executors.newFixedThreadPool(threads)
        val cs = ExecutorCompletionService<String>(pool)
        var submitted = 0
        var finished = 0
        out.parentFile?.mkdirs()
        try {
            java.io.FileWriter(out, true).buffered().use { w ->
                val it = todo.iterator()
                fun submit() {
                    if (!it.hasNext() || (System.nanoTime() - t0) / 1_000_000_000L >= seconds) return
                    val (f, r) = it.next()
                    cs.submit(Callable { game(tag(f), r, side) })
                    submitted++
                }
                repeat(threads) { submit() }
                while (finished < submitted) {
                    val lines = try {
                        cs.take().get()
                    } catch (e: java.util.concurrent.ExecutionException) {
                        println("SV game failed: ${e.cause}")
                        ""
                    }
                    finished++
                    if (lines.isNotEmpty()) {
                        w.write(lines)
                        w.flush()
                    }
                    if (finished % 10 == 0) {
                        println("SV $finished games in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
                    }
                    submit()
                }
            }
        } finally {
            pool.shutdown()
        }
        println("SV wrote $finished games to $out in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
    }

    private fun tag(f: File) = f.nameWithoutExtension

    /** The lines of one game (empty when nothing was searched). */
    private fun game(tag: String, r: Learned.GameRecord, side: String): String {
        val ai = LeafNets.player(side, r.game * 31L + 7) as HybridAi
        var last: Triple<List<dev.quinton.goa.core.game.Choice>, DoubleArray, dev.quinton.goa.core.game.Choice?>? =
            null
        ai.onSearched = { _, cands, means, best -> last = Triple(cands, means, best) }
        val s = TestData.engine.newGame(r.setup) as EngineSession
        val sb = StringBuilder()
        var lastTurn = -1
        var index = -1
        var boundaryTurn = -1
        val searched = HashSet<Team>()
        for (c in r.answers) {
            val d = s.pending ?: break
            val st = s.state
            val turn = Searcher.turnIndex(st)
            if (turn != lastTurn && st.winner == null) {
                index++
                boundaryTurn = turn
                searched.clear()
            }
            lastTurn = turn
            if (d.kind == DecisionKind.PLAY_CARD && turn == boundaryTurn && !st.revealed && d.team !in searched &&
                d.options.size > 1
            ) {
                searched += d.team
                last = null
                ai.choose(s, d) { false }
                last?.let { (cands, means, best) ->
                    val bestAt = cands.indexOf(best)
                    val recAt = cands.indexOf(c)
                    val max = means.filter { !it.isNaN() }.maxOrNull() ?: Double.NaN
                    fun v(i: Int) = if (i < 0) Double.NaN else means[i]
                    sb.append(tag).append('\t').append(r.game).append('\t').append(index).append('\t')
                        .append(d.team.name).append('\t').append(fmt(v(bestAt))).append('\t').append(fmt(max))
                        .append('\t').append(fmt(v(recAt))).append('\t').append(cands.size).append('\n')
                }
            }
            if (s.answer(d.id, c) !is AnswerResult.Accepted) break
        }
        return sb.toString()
    }

    /**
     * The same values recorded while a game is played (`AI_SV_LIVE` in [LearnedSelfPlayTest]): each search side
     * reports the first card play of its team in each turn it searched; [lines] maps the turns to the trainer's
     * position indices by replaying the record.
     */
    internal class Live(players: List<dev.quinton.goa.core.game.AiPlayer>) {
        private class Row(val turn: Int, val team: Team, val best: Double, val max: Double, val n: Int)

        private val rows = java.util.Collections.synchronizedList(ArrayList<Row>())
        private val searched = HashSet<Pair<Int, Team>>()

        @Volatile
        private var current: Pair<Int, Team>? = null

        init {
            for (p in players) {
                val h = p as? HybridAi ?: continue
                h.onSearched = { _, cands, means, best ->
                    current?.let { (turn, team) ->
                        current = null
                        val i = cands.indexOf(best)
                        val max = means.filter { !it.isNaN() }.maxOrNull() ?: Double.NaN
                        rows += Row(turn, team, if (i < 0) Double.NaN else means[i], max, cands.size)
                    }
                }
            }
        }

        /** Before each decision: marks the one whose search counts (as [SearchValueTest.game] picks it). */
        fun observe(s: EngineSession, d: dev.quinton.goa.core.game.Decision) {
            current = null
            val st = s.state
            if (d.kind != DecisionKind.PLAY_CARD || st.revealed || d.options.size <= 1 || st.winner != null) return
            val key = Searcher.turnIndex(st) to d.team
            if (searched.add(key)) current = key
        }

        fun lines(tag: String, r: Learned.GameRecord): String {
            val index = HashMap<Int, Int>()
            val s = TestData.engine.newGame(r.setup) as EngineSession
            var lastTurn = -1
            var n = -1
            for (c in r.answers) {
                val d = s.pending ?: break
                val st = s.state
                val turn = Searcher.turnIndex(st)
                if (turn != lastTurn && st.winner == null) index[turn] = ++n
                lastTurn = turn
                if (s.answer(d.id, c) !is AnswerResult.Accepted) break
            }
            val sb = StringBuilder()
            for (row in rows) {
                val i = index[row.turn] ?: continue
                sb.append(tag).append('\t').append(r.game).append('\t').append(i).append('\t').append(row.team.name)
                    .append('\t').append(fmt(row.best)).append('\t').append(fmt(row.max)).append('\t')
                    .append(fmt(row.best)).append('\t').append(row.n).append('\n')
            }
            return sb.toString()
        }
    }

    private companion object {
        fun fmt(v: Double) = if (v.isNaN()) "NaN" else String.format(java.util.Locale.ROOT, "%.4f", v)
    }
}
