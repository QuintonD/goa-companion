package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionMark
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiCoach
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.CoachReviewer
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.game.ai.confidence
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random
import org.junit.Test

/**
 * Opt-in checks of ADR-051 (AI.md "The coach"), on held-out line-ups.
 *
 * `AI_COACH_HELP=4:4,4:6` a weak player that takes the top suggestion at every decision against plain
 * strengths, mirrored (`AI_N`, `AI_FIRST`, `AI_SIZE`, `AI_FAMILY`, `AI_THREADS`).
 *
 * `AI_COACH_DEEP=<decisions>` the top suggestion at the phone's samples against a far deeper analysis of
 * 64 samples, by confidence.
 *
 * `AI_COACH_MARKS=<games>` every decision of a stand-in at strengths 4, 6, 8 and 10 through the review: how
 * the loss falls by strength, and the noise floor of one decision analysed with two seeds, which the marks' edges
 * are fitted on.
 */
class CoachHelpTest {
    private fun family() = when (System.getenv("AI_FAMILY") ?: "test") {
        "train" -> Teams.TRAIN
        "tune" -> Teams.TUNE
        else -> Teams.TEST
    }

    private val unhurried = 3_600_000L

    /** Takes the coach's top suggestion wherever there is a choice; [strength] answers when it has none. */
    private inner class Follower(private val strength: Int, seed: Long) : AiPlayer {
        private val coach = AiCoach(TestData.engine, seed, unhurried)
        private val own = AiPlayers.deterministic(strength, seed)

        override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
            if (decision.options.size < 2) return decision.options.single()
            return coach.topChoice(session as EngineSession, decision)?.takeIf { it in decision.options }
                ?: own.choose(session, decision, cancelled)
        }
    }

    @Test
    fun `a weak player taking the top suggestion`() {
        val pairs = System.getenv("AI_COACH_HELP")?.split(",") ?: return
        val size = System.getenv("AI_SIZE")?.toIntOrNull() ?: 3
        val n = System.getenv("AI_N")?.toIntOrNull() ?: 32
        val first = System.getenv("AI_FIRST")?.toIntOrNull() ?: 0
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 3
        for (pair in pairs) {
            val (weak, against) = pair.split(":").map { it.toInt() }
            val a = Tournament.Side("s$weak+coach") { seed -> Follower(weak, seed) }
            val b = Tournament.strength(against)
            val score = Teams.mirrored(a, b, Teams.specs(n, size, family(), first), threads)
            println("COACHHELP ${size}v$size $score")
        }
    }

    @Suppress("NestedBlockDepth", "LongMethod", "CyclomaticComplexMethod")
    @Test
    fun `the top suggestion against a far deeper analysis`() {
        val want = System.getenv("AI_COACH_DEEP")?.toIntOrNull() ?: return
        val size = System.getenv("AI_SIZE")?.toIntOrNull() ?: 3
        val rows = HashMap<String, IntArray>()
        val coach = AiCoach(TestData.engine, 0x0C0AC4L, unhurried)
        var looked = 0
        var game = 0
        val specs = Teams.specs(want / 40 + 8, size, family())
        val rng = Random(11)
        while (looked < want && game < specs.size) {
            val spec = specs[game]
            val (red, blue) = spec.lineup
            val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, spec.coin))
                as EngineSession
            val players = mapOf(
                Team.RED to AiPlayers.deterministic(6, spec.seed),
                Team.BLUE to AiPlayers.deterministic(8, spec.seed + 1),
            )
            var n = 0
            while (n < 8000 && looked < want) {
                val d = s.pending ?: break
                if (d.options.size > 1 && rng.nextInt(SAMPLE_ONE_IN) == 0) {
                    val deep = coach.rankedAt(s, d, DEEP)
                    val deepTop = deep.firstOrNull()?.option?.choice
                    val deepSecond = deep.getOrNull(1)
                    for ((label, samples) in listOf(
                        "look " +
                            "ahead" to 0,
                        "12/8" to if (d.kind.name == "PLAY_CARD") 12 else 8,
                    )) {
                        val r = coach.rankedAt(s, d, samples)
                        if (r.size < 2 || deepTop == null) continue
                        val conf = confidence(r[0], r[1]).name
                        val agree = r[0].option.choice == deepTop
                        // The difference at 64 samples between the deep best and this top pick.
                        val lost = deep.first { it.option.choice == r[0].option.choice }.let {
                            deep.first().chance -
                                it.chance
                        }
                        for (key in listOf("$label all", "$label $conf")) {
                            val row = rows.getOrPut(key) { IntArray(3) }
                            row[0]++
                            if (agree) row[1]++
                            if (lost < NEAR) row[2]++
                        }
                    }
                    if (deepSecond != null) looked++
                }
                s.answer(d.id, players.getValue(d.team).choose(s, d))
                n++
            }
            game++
        }
        for ((k, r) in rows.toSortedMap()) {
            println(
                "COACHDEEP $k: n ${r[0]}, same top as 64 samples ${pc(r[1], r[0])}, within 3 points of its best " +
                    "${pc(r[2], r[0])}",
            )
        }
    }

    /** The time from the tap to the sheet, with the phone's budget at 4x the desktop (AI.md "The coach"). */
    @Test
    fun `the time of a suggestion`() {
        val games = System.getenv("AI_COACH_TIMING")?.toIntOrNull() ?: return
        val size = System.getenv("AI_SIZE")?.toIntOrNull() ?: 3
        val onDesktop = AiCoach.BUDGET_MS / PHONE_FACTOR
        val tap = ArrayList<Double>()
        val look = ArrayList<Double>()
        var withPlan = 0
        var plays = 0
        var samples = 0
        for (spec in Teams.specs(games, size, family())) {
            val (red, blue) = spec.lineup
            val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, spec.coin))
                as EngineSession
            val coach = AiCoach(TestData.engine, 0x0C0AC4L, onDesktop)
            val p = AiPlayers.deterministic(8, spec.seed)
            var n = 0
            while (n++ < 8000) {
                val d = s.pending ?: break
                if (d.team == Team.RED && d.options.size > 1) {
                    val t0 = System.nanoTime()
                    val prepared = coach.prepare(s.fork(), d)
                    val t1 = System.nanoTime()
                    val said = coach.suggest(prepared, s.fork(), d)
                    val t2 = System.nanoTime()
                    look += (t1 - t0) / 1e6
                    tap += (t2 - t1) / 1e6
                    samples += said?.samples ?: 0
                    if (d.kind.name == "PLAY_CARD") {
                        plays++
                        if (said?.plan != null) withPlan++
                    }
                }
                s.answer(d.id, p.choose(s, d))
            }
        }
        fun line(v: List<Double>): String {
            val q = v.sorted()
            return "mean ${"%.0f".format(v.average())} ms, p95 ${"%.0f".format(q[(q.size * 0.95).toInt()])} ms, " +
                "max ${"%.0f".format(q.last())} ms"
        }
        println(
            "COACHTIME ${tap.size} decisions, budget $onDesktop ms: the look ahead ${line(look)}; the tap ${line(tap)}; " +
                "mean samples ${"%.1f".format(samples.toDouble() / tap.size)}, a plan at $withPlan of $plays card plays",
        )
    }

    private fun pc(a: Int, b: Int) = "%.1f%%".format(100.0 * a / b.coerceAtLeast(1))

    @Test
    fun `the review's marks on a survey`() {
        val games = System.getenv("AI_COACH_MARKS")?.toIntOrNull() ?: return
        val size = System.getenv("AI_SIZE")?.toIntOrNull() ?: 3
        for (strength in listOf(4, 6, 8, 10)) {
            val all = ArrayList<Double>()
            val counts = java.util.EnumMap<DecisionMark, Int>(DecisionMark::class.java)
            var ms = 0.0
            var maxMs = 0.0
            val specs = Teams.specs(games, size, family())
            for (spec in specs) {
                val (red, blue) = spec.lineup
                val r = AiHarness.play(
                    AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, spec.coin),
                    mapOf(
                        Team.RED to AiPlayers.deterministic(strength, spec.seed),
                        Team.BLUE to AiPlayers.deterministic(8, spec.seed + 1),
                    ),
                    maxDecisions = 20_000,
                )
                val coach = AiCoach(TestData.engine, 0x0C0AC4L, unhurried)
                val t0 = System.nanoTime()
                val report = coach.review(r.session.save(), setOf(red[0]))!!
                val took = (System.nanoTime() - t0) / 1e6
                ms += took
                maxMs = maxOf(maxMs, took)
                report.decisions.forEach {
                    all += it.loss
                    counts[it.mark] = (counts[it.mark] ?: 0) + 1
                }
            }
            val sorted = all.sorted()
            fun q(p: Double) = if (sorted.isEmpty()) 0.0 else sorted[(p * (sorted.size - 1)).toInt()]
            println(
                "COACHMARKS strength $strength: ${all.size} decisions of one seat in $games games, mean loss " +
                    "${"%.3f".format(all.average())}, " +
                    "p50 ${"%.3f".format(q(0.5))} p75 ${"%.3f".format(q(0.75))} p90 ${"%.3f".format(q(0.9))} p95 " +
                    "${"%.3f".format(q(0.95))} " +
                    "p99 ${"%.3f".format(q(0.99))}; $counts; review of one seat mean ${"%.0f".format(ms / games)} " +
                    "ms, max ${"%.0f".format(maxMs)} ms; " +
                    "edges ${CoachReviewer.GOOD_BELOW}/${CoachReviewer.INACCURACY_BELOW}/" +
                    "${CoachReviewer.MISTAKE_BELOW}",
            )
        }
        // The noise floor: the loss of the first-choice option under another seed of the same analysis.
        val floor = ArrayList<Double>()
        val a = AiCoach(TestData.engine, 1L, unhurried)
        val b = AiCoach(TestData.engine, 2L, unhurried)
        for (spec in Teams.specs(games, size, family())) {
            val (red, blue) = spec.lineup
            val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, spec.coin))
                as EngineSession
            val p = AiPlayers.deterministic(8, spec.seed)
            var n = 0
            while (n < 3000) {
                val d = s.pending ?: break
                if (d.options.size > 1 && n % 5 == 0) {
                    val ra = a.rankedAt(s, d, 0)
                    val rb = b.rankedAt(s, d, 0)
                    val top = ra.firstOrNull()?.option?.choice
                    val other = rb.firstOrNull { it.option.choice == top }
                    if (other != null) floor += rb.first().chance - other.chance
                }
                s.answer(d.id, p.choose(s, d))
                n++
            }
        }
        val sorted = floor.sorted()
        fun q(p: Double) = if (sorted.isEmpty()) 0.0 else sorted[(p * (sorted.size - 1)).toInt()]
        println(
            "COACHMARKS noise floor (one seed's best under another seed), ${floor.size} decisions: mean " +
                "${"%.3f".format(floor.average())}, " +
                "p50 ${"%.3f".format(q(0.5))} p75 ${"%.3f".format(q(0.75))} p90 ${"%.3f".format(q(0.9))} p95 " +
                "${"%.3f".format(q(0.95))}",
        )
    }

    private companion object {
        const val DEEP = 64
        const val SAMPLE_ONE_IN = 12
        const val NEAR = 0.03
        const val PHONE_FACTOR = 4
    }
}
