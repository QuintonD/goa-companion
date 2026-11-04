package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * The auxiliary training targets (ADR-060): what happens after a recorded position besides who wins,
 * each from Red's side, so a head can predict it as (Red's output − Blue's) like the win:
 *
 * 0. **kills**: Blue heroes defeated minus Red heroes defeated from the position to the end of its round, / 2;
 * 1. **push**: who wins the next lane push (1 Red, 0 Blue, ½ none before the game ends);
 * 2. **life**: Red's Life counters minus Blue's when the game ends, / 10.
 *
 * Replays a game exactly as [Learned.positions] does (the same positions, in the same order), so the
 * targets line up with the cached features by (family, game, index). NaN where unknown (a game that
 * no longer replays to its end).
 */
internal object AuxTargets {
    const val COUNT: Int = 3
    val NAMES: List<String> = listOf("kills", "push", "life")

    /** Kills and life are regressions (squared error), push a probability (cross-entropy). */
    val BINARY: BooleanArray = booleanArrayOf(false, true, false)

    fun key(family: Long, game: Int, index: Int): Long = (family * 1_000_003L + game) * 4096L + index

    /** The targets of [r]'s positions, in [Learned.positions]' order. */
    fun of(r: Learned.GameRecord, resolvedRate: Double): List<FloatArray> {
        r.winner ?: return emptyList()
        val s = TestData.engine.newGame(r.setup) as EngineSession
        val team = r.setup.seats.associate { it.heroId to it.team }
        val rng = kotlin.random.Random(r.game * 7919L + r.family)
        var lastTurn = -1
        var lastResolved = 0
        // Per position: its round and how many events had happened.
        val at = ArrayList<IntArray>()
        // The event count when each round began.
        val roundStart = HashMap<Int, Int>()
        var complete = true
        for (c in r.answers) {
            val d = s.pending ?: break
            val st = s.state
            roundStart.getOrPut(st.round) { s.events.size }
            val turn = Searcher.turnIndex(st)
            val resolved = st.heroes.sumOf { h -> h.cards.count { it.zone == Zone.RESOLVED } }
            val boundary = turn != lastTurn
            val after = !boundary && resolved > lastResolved && rng.nextDouble() < resolvedRate
            if ((boundary || after) && st.winner == null) at += intArrayOf(st.round, s.events.size)
            lastTurn = turn
            lastResolved = resolved
            if (s.answer(d.id, c) !is AnswerResult.Accepted) {
                complete = false
                break
            }
        }
        val events = s.events
        val end = s.state
        val finished = complete && end.winner != null
        val life = if (finished) {
            ((end.lifeCounters[Team.RED] ?: 0) - (end.lifeCounters[Team.BLUE] ?: 0)) / 10f
        } else {
            Float.NaN
        }
        return at.map { (round, from) ->
            val until = roundStart[round + 1] ?: if (finished) events.size else -1
            floatArrayOf(kills(events, from, until, team), push(events, from, if (finished) end else null), life)
        }
    }

    /** Blue heroes defeated minus Red's in `events[from until until]`, / 2; NaN if [until] is unknown (−1). */
    private fun kills(events: List<GameEvent>, from: Int, until: Int, team: Map<String, Team>): Float {
        if (until < 0) return Float.NaN
        var k = 0
        for (i in from until until) {
            val e = events[i] as? GameEvent.Defeated ?: continue
            when (team[e.unitId]) {
                Team.BLUE -> k++
                Team.RED -> k--
                null -> {}
            }
        }
        return k / 2f
    }

    /** Who wins the next push after `events[from]`: the game's last push counts; [end] is null if it did not finish. */
    private fun push(events: List<GameEvent>, from: Int, end: GameState?): Float {
        for (i in from until events.size) {
            val e = events[i] as? GameEvent.LanePushed ?: continue
            return if (e.winner == Team.RED) 1f else 0f
        }
        return when {
            end == null -> Float.NaN
            end.winReason == WinReason.LAST_PUSH -> if (end.winner == Team.RED) 1f else 0f
            else -> 0.5f
        }
    }

    /** The targets of every position of [file] by [key], computed once and cached in [dir]. */
    fun load(file: File, rate: Double, threads: Int, dir: File): Map<Long, FloatArray> {
        val cache = File(dir, "${file.nameWithoutExtension}.aux$COUNT-$rate.bin")
        if (cache.exists() && cache.lastModified() >= file.lastModified()) return read(cache)
        val games = Learned.readGames(listOf(file))
        val pool = Executors.newFixedThreadPool(threads)
        val t0 = System.nanoTime()
        val out = HashMap<Long, FloatArray>()
        try {
            val all = pool.invokeAll(games.map { r -> Callable { r to of(r, rate) } }).map { it.get() }
            for ((r, ts) in all) for ((i, t) in ts.withIndex()) out[key(r.family, r.game, i)] = t
        } finally {
            pool.shutdown()
        }
        println(
            "TRAIN aux targets of ${games.size} games of $file: ${out.size} positions in " +
                "${"%.0f".format((System.nanoTime() - t0) / 1e9)} s",
        )
        dir.mkdirs()
        DataOutputStream(cache.outputStream().buffered(1 shl 20)).use { o ->
            o.writeInt(out.size)
            for ((k, v) in out) {
                o.writeLong(k)
                for (x in v) o.writeFloat(x)
            }
        }
        return out
    }

    private fun read(f: File): Map<Long, FloatArray> = DataInputStream(f.inputStream().buffered(1 shl 20)).use { i ->
        val n = i.readInt()
        val out = HashMap<Long, FloatArray>(n * 2)
        repeat(n) { out[i.readLong()] = FloatArray(COUNT) { i.readFloat() } }
        out
    }
}
