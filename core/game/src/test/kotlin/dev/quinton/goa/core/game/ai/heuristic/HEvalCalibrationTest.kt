package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.NetEvaluator
import dev.quinton.goa.core.game.ai.SampleRunner
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.ai.hybrid.BookPolicy
import dev.quinton.goa.core.game.ai.hybrid.HybridAi
import dev.quinton.goa.core.game.ai.hybrid.HybridPlayers
import dev.quinton.goa.core.game.ai.hybrid.PolicyMode
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Opt-in recorders for the calibration of [HEval] (HEVAL.md "Calibration pipeline"); the fits are
 * `tools/ai/heval_fit.py`, `heval_rank.py` and `heval_resid.py`.
 *
 * **Record.** `AI_HEVAL_DATA=h10:h10,x10+pol=1+scope=3:s10` plays mirrored games on mixed line-ups
 * (`AI_H_GAMES` line-ups from `AI_H_FIRST`, like `HeuristicTournamentTest`; `AI_THREADS`, `AI_FORMAT`)
 * and appends to `AI_HEVAL_OUT` one row per team at the first decision of every turn (where a turn-horizon
 * search scores its leaves): the [HTerm] features from that team's side, computed on a copy determinized
 * from that team's view exactly as the hybrid's leaf is (strength 10's view, ADR-062), the old learned
 * net's logit on the same copy, and whether that team won.
 */
class HEvalCalibrationTest {
    @Test
    fun `record positions`() {
        val spec = System.getenv("AI_HEVAL_DATA") ?: return
        val out = File(System.getenv("AI_HEVAL_OUT") ?: error("AI_HEVAL_OUT"))
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 16
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 2000
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        if (!out.exists()) out.writeText(header() + "\n")
        val all = AiHarness.mixedLineups(first + lineups, size = format.size)
        for (pair in spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val (aName, bName) = pair.split(":")
            val jobs = ArrayList<Callable<List<String>>>()
            for (i in first until first + lineups) {
                val (red, blue) = all[i]
                val coin = if (i % 2 == 0) Team.RED else Team.BLUE
                for (aIsRed in listOf(true, false)) {
                    jobs += Callable {
                        val seed = (50_000L + i) * 2
                        val setup = setupOf(red, blue, coin, format)
                        val players = mapOf(
                            Team.RED to player(if (aIsRed) aName else bName, seed),
                            Team.BLUE to player(if (aIsRed) bName else aName, seed + 1),
                        )
                        val game = "$pair|$i|${if (aIsRed) "a" else "b"}"
                        val labels = mapOf(
                            Team.RED to if (aIsRed) aName else bName,
                            Team.BLUE to if (aIsRed) bName else aName,
                        )
                        play(game, setup, players, labels)
                    }
                }
            }
            val pool = Executors.newFixedThreadPool(threads)
            try {
                var n = 0
                for (f in pool.invokeAll(jobs)) {
                    val rows = try {
                        f.get()
                    } catch (e: java.util.concurrent.ExecutionException) {
                        println("HEVAL_DATA game failed: ${e.cause}")
                        emptyList()
                    }
                    synchronized(this) { out.appendText(rows.joinToString("") { "$it\n" }) }
                    n += rows.size
                }
                println("HEVAL_DATA $pair: ${jobs.size} games, $n rows -> $out")
            } finally {
                pool.shutdown()
            }
        }
    }

    /**
     * `AI_HEVAL_LEAVES=x10+pol=1+scope=3+leaf=2:s10` plays mirrored games and appends to `AI_HEVAL_OUT` one
     * row per leaf the hybrid side scores: the decision, the sample and the candidate, the learned net's
     * logit and the [HTerm] features from the deciding team's side (HEVAL.md "Leaf ranking").
     */
    @Test
    fun `record leaves`() {
        val spec = System.getenv("AI_HEVAL_LEAVES") ?: return
        val out = File(System.getenv("AI_HEVAL_OUT") ?: error("AI_HEVAL_OUT"))
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 4
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 2500
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        if (!out.exists()) {
            val head = listOf("game", "decision", "kind", "sample", "cand", "round", "turn", "net") +
                HTerm.entries.map { it.label } + CONTEXT + listOf("rollHeval", "rollNet")
            out.writeText(head.joinToString(",") + "\n")
        }
        val all = AiHarness.mixedLineups(first + lineups, size = format.size)
        val (aName, bName) = spec.split(":")
        val profile = HeuristicProfile.of(10)
        val roll = System.getenv("AI_HEVAL_ROLL") == "1"
        val jobs = ArrayList<Callable<Int>>()
        for (i in first until first + lineups) {
            val (red, blue) = all[i]
            val coin = if (i % 2 == 0) Team.RED else Team.BLUE
            for (aIsRed in listOf(true, false)) {
                jobs += Callable {
                    val seed = (60_000L + i) * 2
                    val game = "$spec|$i|${if (aIsRed) "a" else "b"}"
                    val net = NetEvaluator(TestData.engine.data, LearnedNet.ENSEMBLE)
                    val rows = StringBuilder()
                    val policy = BookPolicy(seed, emptyMap())
                    val hybrid = HybridAi(seed, tuneOf(aName))
                    hybrid.leafObserver = { d, sample, cand, f, team ->
                        val ctx = HContext(f, f.pending, team, profile, finishing = true)
                        val red = when (f.state.winner) {
                            null -> net.redLogit(f.state)
                            Team.RED -> 20.0
                            else -> -20.0
                        }
                        val feats = HEval.features(ctx, team)
                        val win = f.state.winner?.let { if (it == team) 1.0 else -1.0 }
                        rows.append(game).append(',').append(d.id).append(',').append(d.kind.name).append(',')
                            .append(sample).append(',').append(cand).append(',').append(f.state.round).append(',')
                            .append(f.state.turn).append(',').append("%.4f".format(if (team == Team.RED) red else -red))
                        for (t in HTerm.entries) {
                            val v = feats?.get(t.ordinal) ?: if (t == HTerm.LIFE) 1000.0 * win!! else 0.0
                            rows.append(',').append("%.4f".format(v))
                        }
                        for (c in context(ctx, team)) rows.append(',').append(c)
                        val (rh, rn) = if (roll) {
                            rollout(f, team, policy, net, profile, d.id * 31L + sample)
                        } else {
                            Double.NaN to Double.NaN
                        }
                        rows.append(',').append("%.4f".format(rh)).append(',').append("%.4f".format(rn))
                        rows.append('\n')
                    }
                    val other = player(bName, seed + 1)
                    val seats = mapOf(
                        Team.RED to if (aIsRed) hybrid else other,
                        Team.BLUE to if (aIsRed) other else hybrid,
                    )
                    AiHarness.play(setupOf(red, blue, coin, format), seats)
                    synchronized(this) { out.appendText(rows.toString()) }
                    rows.count { it == '\n' }
                }
            }
        }
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val n = pool.invokeAll(jobs).sumOf { f ->
                try {
                    f.get()
                } catch (e: java.util.concurrent.ExecutionException) {
                    println("HEVAL_LEAVES game failed: ${e.cause}")
                    0
                }
            }
            println("HEVAL_LEAVES $spec: ${jobs.size} games, $n leaves -> $out")
        } finally {
            pool.shutdown()
        }
    }

    /**
     * A search-backed value of a leaf: the static book plays every seat to the next round's first card play
     * (the round-end battle resolved by the engine), then HEval (hand weights) and the net score it from
     * [team]'s side. A finished game is ±1000 / ±20.
     */
    @Suppress("LongParameterList")
    private fun rollout(
        leaf: EngineSession,
        team: Team,
        policy: BookPolicy,
        net: NetEvaluator,
        profile: HeuristicProfile,
        salt: Long,
    ): Pair<Double, Double> {
        val f = leaf.forkForAnswer()
        val start = f.state.round
        var n = 0
        while (n < 900) {
            val d = f.pending ?: break
            if (f.state.round != start && d.kind == dev.quinton.goa.core.game.DecisionKind.PLAY_CARD) break
            val c = policy.answer(f, d, PolicyMode.STATIC, emptyMap(), salt)
            if (!BookPolicy.play(f, d, c)) break
            n++
        }
        f.state.winner?.let { w -> return if (w == team) HEval.WIN to 20.0 else -HEval.WIN to -20.0 }
        val ctx = HContext(f, f.pending, team, profile, finishing = true)
        val red = net.redLogit(f.state)
        return HEval.value(ctx, team, null, HEvalWeights.HAND) to (if (team == Team.RED) red else -red)
    }

    /** Readable context of a leaf for the residual analysis (HEVAL.md "Residuals"), from [team]'s side. */
    private fun context(ctx: HContext, team: Team): List<String> {
        val st = ctx.state
        val own = st.heroes.filter { it.team == team }
        val foe = st.heroes.filter { it.team != team }
        val lane = st.battleZones.keys.firstOrNull()
        val zones = lane?.let { ctx.game.geo.lanes[it]?.zones }
        val i = zones?.indexOf(st.battleZones[lane]) ?: -1
        val toWin = if (zones == null || i < 0) -1 else if (team == Team.RED) zones.lastIndex - i else i
        val toLose = if (zones == null || i < 0) -1 else if (team == Team.RED) i else zones.lastIndex - i
        return listOf(
            own.sumOf { it.hand.size }, foe.sumOf { it.hand.size },
            lane?.let { ctx.minionCount(team, it) } ?: 0,
            lane?.let { ctx.minionCount(HContext.other(team), it) } ?: 0,
            lane?.let { st.wavesLeft[it] } ?: 0, toWin, toLose,
            st.lifeCounters[team] ?: 0, st.lifeCounters[HContext.other(team)] ?: 0,
            own.sumOf { it.level }, foe.sumOf { it.level },
            own.count { it.at == null }, foe.count { it.at == null },
        ).map { it.toString() } +
            listOf(st.step.name, own.joinToString("+") { it.id }, foe.joinToString("+") { it.id })
    }

    private fun tuneOf(spec: String): Map<String, Double> =
        spec.split("+").drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }

    private fun header(): String =
        (listOf("game", "players", "round", "turn", "team", "won", "net") + HTerm.entries.map { it.label })
            .joinToString(",")

    private fun setupOf(red: List<String>, blue: List<String>, coin: Team, format: AiHarness.Format) =
        AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin, format.length, format.mapId)

    private fun play(
        game: String,
        setup: dev.quinton.goa.core.game.GameSetup,
        players: Map<Team, AiPlayer>,
        labels: Map<Team, String>,
    ): List<String> {
        val profile = HeuristicProfile.of(10)
        val net = NetEvaluator(TestData.engine.data, LearnedNet.ENSEMBLE)
        val pending = ArrayList<Pair<Team, String>>()
        var lastTurn = -1
        var k = 0L
        val r = AiHarness.play(setup, players) { s: EngineSession, d ->
            val st = s.state
            val turn = HybridAi.turnIndex(st)
            if (turn != lastTurn && st.winner == null) {
                lastTurn = turn
                for (team in Team.entries) {
                    val seen = Searcher.seenCards(s, team)
                    val det = s.determinizeWith(
                        team,
                        HeuristicAi.mix(game.hashCode().toLong(), k++),
                        Determinizer.Options(seen = seen, knowHands = profile.knowHands),
                        quiet = true,
                    )
                    val ctx = HContext(det, det.pending ?: d, team, profile, seen = seen, finishing = true)
                    val f = HEval.features(ctx, team) ?: continue
                    val red = net.redLogit(det.state)
                    val logit = if (team == Team.RED) red else -red
                    val row = listOf(game, labels.getValue(team), st.round, st.turn, team.name, "?") +
                        "%.4f".format(logit) + f.map { "%.4f".format(it) }
                    pending += team to row.joinToString(",")
                }
            }
        }
        val winner = r.winner ?: return emptyList()
        return pending.map { (team, row) -> row.replace(",?,", if (team == winner) ",1," else ",0,") }
    }

    private fun player(spec: String, seed: Long): AiPlayer {
        val parts = spec.split("+")
        val n = parts[0].drop(1).toInt()
        val tune = parts.drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }
        return when (spec[0]) {
            'h' -> HeuristicAi(n, seed, tune = tune)
            's' -> AiPlayers.searchDeterministic(n, seed)
            'x' -> HybridPlayers.hybrid(seed, tune, SampleRunner.SEQUENTIAL)
            else -> error("AI_HEVAL_DATA wants hN, sN or x10, not $spec")
        }
    }

    private companion object {
        val CONTEXT = listOf(
            "ownHand", "foeHand", "ownMinions", "foeMinions", "waves", "toWin", "toLose", "ownLife", "foeLife",
            "ownLevels", "foeLevels", "ownOff", "foeOff", "step", "ownHeroes", "foeHeroes",
        )
    }
}
