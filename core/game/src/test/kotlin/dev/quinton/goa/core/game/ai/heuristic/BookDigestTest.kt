package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Opt-in answer digest of the book on fixed games (strategy round, Track T): the knob-off proof that a
 * layer switched off gives exactly the baseline's answers. `AI_BOOK_DIGEST=h9;h9+team=0;h7+team=0` plays, per
 * spec, the same mirrored self-play games (the spec on both sides) on `AI_H_GAMES` line-ups from `AI_H_FIRST`
 * (default 2 from 2000) in 2v2 and 3v3 (`AI_DIGEST_FORMATS`, default `2v2,3v3`), and prints one digest of every
 * answer (decision id, seat, kind, choice) per game and per spec, with the mean card-play time. Two specs whose
 * digests match answered every question of those games alike. `AI_THREADS` games at once.
 */
class BookDigestTest {
    @Test
    fun `book answer digest`() {
        val specs = System.getenv("AI_BOOK_DIGEST")?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
        val n = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 2
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 2000
        val formats = (System.getenv("AI_DIGEST_FORMATS") ?: "2v2,3v3").split(",").map { AiHarness.Format.parse(it) }
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        for (spec in specs) {
            val parts = spec.split("+")
            val strength = parts[0].drop(1).toInt()
            val tune = parts.drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }
            println("DIGEST spec $spec strength $strength tune $tune")
            val jobs = ArrayList<Callable<String>>()
            for (format in formats) {
                val all = AiHarness.mixedLineups(first + n, size = format.size)
                for (i in first until first + n) {
                    val (red, blue) = all[i]
                    for (aRed in listOf(true, false)) {
                        jobs += Callable { game(format, red, blue, i, aRed, strength, tune) }
                    }
                }
            }
            val pool = Executors.newFixedThreadPool(threads)
            var total = 0L
            try {
                for (f in pool.invokeAll(jobs)) {
                    val line = f.get()
                    println("DIGEST $spec $line")
                    total = total * 31 + line.substringAfter("digest ").substringBefore(" ").toULong(16).toLong()
                }
            } finally {
                pool.shutdown()
            }
            println("DIGEST TOTAL $spec ${java.lang.Long.toHexString(total)}")
        }
    }

    private fun game(
        format: AiHarness.Format,
        red: List<String>,
        blue: List<String>,
        i: Int,
        aRed: Boolean,
        strength: Int,
        tune: Map<String, Double>,
    ): String {
        val coin = if (i % 2 == 0) Team.RED else Team.BLUE
        val seed = (50_000L + i) * 2
        var digest = 1469598103934665603L
        val lock = Any()
        fun player(sd: Long) = object : AiPlayer {
            val ai = HeuristicAi(strength, sd, tune = tune)
            override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
                val c = ai.choose(session, decision, cancelled)
                synchronized(lock) {
                    val key = "${decision.id}|${decision.seat}|${decision.kind}|$c"
                    digest = (digest xor key.hashCode().toLong()) * 1099511628211L
                }
                return c
            }
        }
        val pa = player(if (aRed) seed else seed + 1)
        val pb = player(if (aRed) seed + 1 else seed)
        val r = AiHarness.play(
            AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin, format.length, format.mapId),
            mapOf(Team.RED to if (aRed) pa else pb, Team.BLUE to if (aRed) pb else pa),
            timed = true,
        )
        val plays = r.timings[DecisionKind.PLAY_CARD].orEmpty()
        val ms = if (plays.isEmpty()) 0.0 else plays.average() / 1e6
        return "${format.label} $i${if (aRed) "R" else "B"} digest ${java.lang.Long.toHexString(digest)} " +
            "winner ${r.winner} rounds ${r.rounds} decisions ${r.decisions} play ${"%.1f".format(ms)} ms " +
            "(${plays.size}) ${red + blue}"
    }
}
