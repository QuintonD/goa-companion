package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.monotonicNanos
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.match.Team
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * How fast this device runs strength 10 (AI.md "Budgets"): the phone factor every budget rests on has
 * never been measured, so the app's settings run this and the owner sends back the line. The same
 * card plays on every device: fixed 3v3 games, played on by a fast static player, so no position
 * depends on how far a search got. A slower device answers a prefix of the same shuffled list.
 */
object AiBenchmark {
    /**
     * Bumped when the positions or the measure change: numbers compare only within a version. 2 (ADR-060):
     * strength 10 scores its one-ply looks with three nets, so an engine answer costs about twice v1's.
     */
    const val VERSION: Int = 2
    const val MILLIS: Long = 30_000L
    private const val WARM_UP_SHARE = 6
    private const val SEED = 20_261_031L
    private const val GAMES = 8
    private const val POSITIONS = 500
    private const val MAX_DECISIONS = 2_000
    private const val STRENGTH = 10
    private const val MOVER_STRENGTH = 4
    private const val SIZE = 3
    private const val MAP = "forgotten-island"

    /** The device budget a card play has to fit in at this strength (the app's wall-clock cap). */
    private val capMs: Long get() = AiPlayers.profile(STRENGTH).budgetMs

    class Result(
        val threads: Int,
        val answers: Long,
        val seconds: Double,
        val answersPerSecond: Long,
        /** The same over the first and second half of the card plays: a phone that throttles shows here. */
        val firstHalfPerSecond: Long,
        val secondHalfPerSecond: Long,
        val cardPlays: Int,
        val medianMs: Long,
        val p95Ms: Long,
        val maxMs: Long,
        /** Card plays that needed longer than the app's cap (there they stop early). */
        val overCap: Int,
        val capMs: Long,
    ) {
        /** One line to send back; [device] is what the platform calls itself. */
        fun line(device: String): String = "GoA speed v$VERSION | $device | 3v3 strength 10, $threads threads | " +
            "$answersPerSecond answers/s (halves $firstHalfPerSecond, $secondHalfPerSecond) | " +
            "card play median $medianMs ms, p95 $p95Ms ms, max $maxMs ms, " +
            "$overCap of $cardPlays over $capMs ms | ${(seconds * 10).roundToLong() / 10.0} s"
    }

    /** The fixed card plays, in the order they are measured (each a session at a card play with a choice). */
    internal fun positions(engine: GameEngine): List<EngineSession> {
        val heroes = engine.playableHeroes.sorted()
        val rng = Random(SEED)
        val out = ArrayList<EngineSession>()
        for (g in 0 until GAMES) {
            val group = heroes.shuffled(rng).take(2 * SIZE)
            val setup = GameSetup(
                MAP,
                group.take(SIZE).map { SeatSetup(it, Team.RED, Controller.Ai(AiLevel.NORMAL)) } +
                    group.drop(SIZE).map { SeatSetup(it, Team.BLUE, Controller.Ai(AiLevel.NORMAL)) },
                tieBreaker = if (g % 2 == 0) Team.RED else Team.BLUE,
            )
            val s = engine.newGame(setup) as EngineSession
            val mover = AiPlayers.deterministic(MOVER_STRENGTH, SEED + g)
            var n = 0
            while (n++ < MAX_DECISIONS) {
                val d = s.pending ?: break
                if (d.kind == DecisionKind.PLAY_CARD && d.options.size > 1) out += s.forkSession()
                s.answer(d.id, mover.choose(s, d))
            }
        }
        return out.shuffled(Random(SEED + 1)).take(POSITIONS)
    }

    /**
     * Runs for about [millis] and returns the numbers; null if [cancelled] or no card play finished.
     * The first sixth warms the code up on other positions (a phone's and a JVM's first seconds are
     * not what a game feels like) and is not counted. [progress] gets 0..1.
     */
    fun run(
        engine: GameEngine,
        runner: SampleRunner,
        millis: Long = MILLIS,
        progress: (Double) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): Result? {
        val positions = positions(engine)
        val player = SearchAi(AiLevel.of(STRENGTH), AiPlayers.profile(STRENGTH), SEED, timeCap = false, runner)
        val start = monotonicNanos()
        val warmEnd = start + millis * NANOS_PER_MS / WARM_UP_SHARE
        val end = start + millis * NANOS_PER_MS
        val tell = { progress(((monotonicNanos() - start).toDouble() / (end - start)).coerceIn(0.0, 1.0)) }
        // From the far end of the list: the warm-up never shares a position with the measure.
        var w = positions.size
        while (monotonicNanos() < warmEnd && w > 0) {
            val s = positions[--w]
            player.choose(s, s.pending!!) { cancelled() || monotonicNanos() > warmEnd }
            if (cancelled()) return null
            tell()
        }
        val times = ArrayList<Long>()
        val works = ArrayList<Long>()
        val measured = monotonicNanos()
        for (s in positions.subList(0, w)) {
            val t0 = monotonicNanos()
            val w0 = player.work
            player.choose(s, s.pending!!) { cancelled() || monotonicNanos() > end }
            if (cancelled()) return null
            // An answer the end of the time cut short says nothing: only whole card plays count.
            val t1 = monotonicNanos()
            if (t1 > end) break
            times += t1 - t0
            works += player.work - w0
            tell()
        }
        if (times.isEmpty()) return null
        val half = times.size / 2
        val sorted = times.sorted()
        return Result(
            threads = runner.width,
            answers = works.sum(),
            seconds = (monotonicNanos() - measured) / NANOS_PER_S,
            answersPerSecond = rate(works, times),
            firstHalfPerSecond = rate(works.take(half), times.take(half)),
            secondHalfPerSecond = rate(works.drop(half), times.drop(half)),
            cardPlays = times.size,
            medianMs = sorted[sorted.size / 2] / NANOS_PER_MS,
            p95Ms = sorted[(sorted.size * P95).toInt().coerceAtMost(sorted.size - 1)] / NANOS_PER_MS,
            maxMs = sorted.last() / NANOS_PER_MS,
            overCap = times.count { it > capMs * NANOS_PER_MS },
            capMs = capMs,
        )
    }

    private fun rate(works: List<Long>, times: List<Long>): Long =
        if (times.isEmpty()) 0 else (works.sum() * NANOS_PER_S / times.sum()).roundToLong()

    private const val NANOS_PER_MS = 1_000_000L
    private const val NANOS_PER_S = 1e9
    private const val P95 = 0.95
}
