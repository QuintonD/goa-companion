package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.Analysis
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.ai.WinChance
import dev.quinton.goa.core.game.ai.EvalSource
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.sqrt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * Opt-in regret probe (REVIEW-2-losses.md). `AI_H_REGRET=s10,s8` plays mirrored games of the heuristic
 * player at strength 10 against the old search at each listed strength, on `AI_H_GAMES` line-ups from
 * `AI_H_FIRST` (default 1000: development line-ups only). At every question of the h10 side with more
 * than one option it also asks an ORACLE ([oracleProfile]): the old search at strength 10 with `AI_H_REGRET_X` times
 * its card-play samples (default 3) and no wall clock, from the same seat's determinized view (the search player's own
 * determinizer, so no hidden information), with h10's answer forced into its candidates. The h10 side's
 * play is unchanged: the probe only observes.
 *
 * Each question becomes one JSON line in `AI_H_REGRET_OUT` (default build/regret.jsonl): the kind, hero,
 * round and turn, h10's answer and its own scores, the oracle's best, the gap (paired over the oracle's
 * samples, with its standard error), a two-fold cross-fitted gap (best picked on half the samples,
 * measured on the other half: no winner's curse), and a state summary. A game ends with a "game" line.
 * Every question is also answered by a second opinion, the same search scored by the hand evaluation
 * instead of the net ("q" fields: model noise); `AI_H_REGRET_RETEST` (default 0.2) is the share of card plays
 * the oracle answers again with another seed ("r" fields: sampling noise). `AI_H_REGRET_BOTH=1` also probes
 * the search side's own questions (a calibration: the oracle's regret of a player that wins). `AI_H_REGRET_ALT=h8`
 * values what another strength would have answered. `AI_FORMAT` and `AI_THREADS` (default 2) as elsewhere.
 *
 * `AI_H_REGRET_ONLY=<lineup>:<R|B>:<sN>:<decision id>[,…]` replays one game (h10 Red or Blue) to that question
 * and prints the position, h10's trace and the oracle's full analysis (examples in the review).
 */
class HeuristicRegretTest {
    private val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
    private val mult = System.getenv("AI_H_REGRET_X")?.toIntOrNull() ?: 3

    /**
     * The oracle: strength 10's search (the net, enemy hands known as h10 knows them) with [mult] times its
     * card-play samples, no wall clock, enemies still to play this turn drawing their card from the opponent
     * model in each sample (`rm`: without it they play their static best, one deterministic reply), and the
     * kill extension (a playout runs on through a next-turn kill either team can make). Other questions are
     * answered after the reveal with every hand known: their samples agree (measured), so few are kept.
     */
    private val oracleProfile = AiPlayers.profile(10).let {
        it.copy(playSamples = it.playSamples * mult, otherSamples = OTHER_SAMPLES, rm = 1.0, killExt = 1)
    }

    /** The second opinion on every question: the same search scored by the hand evaluation (model noise). */
    private val secondProfile = oracleProfile.copy(
        learned = EvalSource.HAND,
        knobs = AiPlayers.TUNED,
        playSamples = AiPlayers.profile(10).playSamples,
        otherSamples = 2,
    )
    private val retest = System.getenv("AI_H_REGRET_RETEST")?.toDoubleOrNull() ?: 0.2
    private val both = System.getenv("AI_H_REGRET_BOTH") == "1"

    /** `AI_H_REGRET_ALT=h8,h9`: other strengths asked the same question; the oracle values their answer too. */
    private val alts = System.getenv("AI_H_REGRET_ALT")?.split(",")?.map { it.trim() }
        ?.filter { it.isNotEmpty() }.orEmpty()

    @Test
    fun `oracle regret of h10`() {
        System.getenv("AI_H_REGRET_ONLY")?.let { return replay(it) }
        val spec = System.getenv("AI_H_REGRET") ?: return
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 4
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 1000
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val out = File(System.getenv("AI_H_REGRET_OUT") ?: "build/regret.jsonl")
        out.parentFile?.mkdirs()
        val all = AiHarness.mixedLineups(first + lineups, size = format.size)
        val jobs = ArrayList<Callable<Unit>>()
        for (opp in spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            for (i in first until first + lineups) for (hRed in listOf(true, false)) {
                jobs += Callable {
                    val t0 = System.nanoTime()
                    val lines = game(all[i], i, hRed, opp, stopAt = null)
                    val secs = (System.nanoTime() - t0) / 1e9
                    synchronized(this) { out.appendText(lines.joinToString("\n", postfix = "\n")) }
                    val side = if (hRed) "R" else "B"
                    println("REGRET game $i $side vs $opp: ${lines.size - 1} questions in ${secs.toInt()} s")
                }
            }
        }
        val pool = Executors.newFixedThreadPool(threads)
        try {
            pool.invokeAll(jobs).forEach { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    /** One game; the JSON lines (questions, then the game line). [stopAt]: replay to that question and print it. */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun game(
        lineup: Pair<List<String>, List<String>>,
        index: Int,
        hRed: Boolean,
        opp: String,
        stopAt: Long?,
    ): List<String> {
        val (red, blue) = lineup
        val coin = if (index % 2 == 0) Team.RED else Team.BLUE
        val seed = 50_000L + index
        val hTeam = if (hRed) Team.RED else Team.BLUE
        val h = HeuristicAi(10, if (hRed) seed * 2 else seed * 2 + 1)
        // The same seed: an alternative strength answers the same question in the same position, nothing more.
        val others = alts.map { it to HeuristicAi(it.drop(1).toInt(), if (hRed) seed * 2 else seed * 2 + 1) }
        val s = AiPlayers.searchDeterministic(opp.drop(1).toInt(), if (hRed) seed * 2 + 1 else seed * 2)
        val setup = AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin, format.length, format.mapId)
        val es = TestData.engine.newGame(setup) as EngineSession
        val oracle = Searcher(Evaluator(es.engineData, oracleProfile.knobs), oracleProfile)
        // The second opinion (model noise): the same search scored by the hand evaluation instead of the net.
        val second = Searcher(Evaluator(es.engineData, secondProfile.knobs), secondProfile)
        val out = ArrayList<String>()
        val gameId = "${format.label}|$index|${if (hRed) "R" else "B"}|$opp"
        var stage: Pair<String, Pair<List<Choice>, DoubleArray>>? = null
        h.scored = { st, opts, v -> stage = st to (opts to v) }
        val traces = ArrayList<String>()
        if (stopAt != null) h.trace = { traces += it }
        var n = 0
        while (n < 8000) {
            val d = es.pending ?: break
            val mine = d.team == hTeam
            stage = null
            traces.clear()
            val player: AiPlayer = if (mine) h else s
            val c = player.choose(es, d)
            if (d.options.size > 1 && (mine || both) && (stopAt == null || d.id == stopAt)) {
                val altChoices = if (mine) others.map { (name, p) -> name to p.choose(es, d) } else emptyList()
                val rec = probe(es, d, c, oracle, second, if (mine) stage else null, gameId, mine, altChoices)
                if (stopAt != null) {
                    es.events.takeLast(RECENT).forEach { println("REPLAY event ${it.text}") }
                    println("REPLAY ${status(es.state)}")
                    traces.forEach { println("REPLAY trace $it") }
                    println("REPLAY $rec")
                    return emptyList()
                }
                out += rec.toString()
            }
            val r = es.answer(d.id, c)
            check(r is AnswerResult.Accepted) { "Rejected $c for $d: $r" }
            n++
        }
        out += gameLine(es, gameId, hTeam).toString()
        return out
    }

    @Suppress("LongMethod", "LongParameterList")
    private fun probe(
        es: EngineSession,
        d: Decision,
        chosen: Choice,
        oracle: Searcher,
        second: Searcher,
        stage: Pair<String, Pair<List<Choice>, DoubleArray>>?,
        gameId: String,
        mine: Boolean,
        altChoices: List<Pair<String, Choice>> = emptyList(),
    ): JsonObject {
        val t0 = System.nanoTime()
        val oseed = HeuristicAi.mix(ORACLE_SALT, d.id * 1_000_003L + d.seat.hashCode())
        val a = oracle.analyze(es, d, oseed, { false }, Long.MAX_VALUE, mustInclude = chosen)
        val ms = (System.nanoTime() - t0) / 1_000_000
        // The same oracle again with another seed on a share of the card plays (the only questions it samples).
        val twice = d.kind == DecisionKind.PLAY_CARD && unit(gameId, d.id) < retest
        val again = if (twice) {
            oracle.analyze(es, d, oseed xor RETEST_SALT, { false }, Long.MAX_VALUE, mustInclude = chosen)
        } else {
            null
        }
        val other = second.analyze(es, d, oseed, { false }, Long.MAX_VALUE, mustInclude = chosen)
        val st = es.state
        val hero = st.hero(d.seat)
        return buildJsonObject {
            put("g", gameId)
            put("side", if (mine) "h" else "s")
            put("id", d.id)
            put("kind", d.kind.name)
            put("seat", d.seat)
            put("team", d.team.name)
            put("prompt", d.prompt.take(PROMPT))
            d.sourceCardId?.let { put("src", it) }
            d.context.subjectId?.let { put("subject", it) }
            put("subjectOwn", d.context.subjectId?.let { id -> st.hero(id)?.team == d.team } ?: false)
            put("round", st.round)
            put("turn", st.turn)
            put("step", st.step.name)
            put("acting", st.acting ?: "")
            put("lifeOwn", st.lifeCounters[d.team] ?: 0)
            put("lifeEnemy", st.lifeCounters[d.team.opponent] ?: 0)
            put("hand", hero?.hand?.size ?: -1)
            put("level", hero?.level ?: 0)
            put("onBoard", hero?.at != null)
            put("nOpts", d.options.size)
            put("choice", label(es, chosen))
            put("ms", ms)
            put("lanes", lanes(st, d.team))
            put("heroes", heroes(st, d.team))
            if (d.kind == DecisionKind.DEFEND) {
                put("atk", d.context.attackTotal ?: -1)
                put("def", buildJsonObject { d.context.defensePreview.forEach { (k, v) -> put(k, v ?: -99) } })
            }
            stage?.let { (name, sv) ->
                put("hStage", name)
                val (opts, v) = sv
                put(
                    "hScores",
                    buildJsonArray {
                        opts.indices.sortedByDescending { v[it] }.take(TOP).forEach { i ->
                            add(pair(label(es, opts[i]), v[i]))
                        }
                    },
                )
                val i = opts.indexOf(chosen)
                if (i >= 0) {
                    put("hChosenScore", round2(v[i]))
                    put("hArgmax", v[i] >= v.max() - 1e-9)
                }
            }
            if (a != null) putAll(this, "o", gaps(es, a, chosen))
            if (again != null) putAll(this, "r", gaps(es, again, chosen))
            if (other != null) putAll(this, "q", gaps(es, other, chosen))
            for ((name, c2) in altChoices) {
                put("alt_$name", label(es, c2))
                if (c2 == chosen) continue
                a?.let { an -> versus(an, c2, chosen, es)?.let { put("altWp_$name", round4(it)) } }
                other?.let { an -> versus(an, c2, chosen, es)?.let { put("altQWp_$name", round4(it)) } }
            }
        }
    }

    /**
     * The oracle's verdict on [chosen]: best, gap, paired standard error, cross-fitted gap (the best picked on
     * the even samples, its gap measured on the odd ones, and back: no winner's curse), top means. The same in
     * win chance ("wp" fields: each sample's value through [WinChance], so a won or lost playout is 1 or 0 and
     * a gap in a lopsided position counts little).
     */
    private fun gaps(es: EngineSession, a: Analysis, chosen: Choice): JsonObject {
        val opts = a.options
        val mine = opts.firstOrNull { it.choice == chosen } ?: return buildJsonObject { put("missing", true) }
        val size = es.setup.seats.count { it.team == Team.RED }
        val wp = { v: DoubleArray -> DoubleArray(v.size) { k -> if (v[k].isNaN()) v[k] else WinChance.of(v[k], size) } }
        val coins = verdict(opts.map { it.values }, opts.indexOf(mine))
        val chance = verdict(opts.map { wp(it.values) }, opts.indexOf(mine))
        val means = opts.map { mean(it.values, null) }
        return buildJsonObject {
            put("best", label(es, opts[coins.best].choice))
            put("gap", round2(coins.gap))
            put("se", round2(coins.se))
            put("cf", round2(coins.cf))
            put("wpBest", label(es, opts[chance.best].choice))
            put("wpGap", round4(chance.gap))
            put("wpSe", round4(chance.se))
            put("wpCf", round4(chance.cf))
            put("wp", round4(mean(wp(mine.values), null)))
            put("n", coins.n)
            put("cands", opts.size)
            put("v", round2(mean(mine.values, null)))
            put("spread", round2(means.max() - means.filter { it.isFinite() }.min()))
            put(
                "top",
                buildJsonArray {
                    opts.indices.sortedByDescending { means[it] }.take(TOP).forEach { i ->
                        add(pair(label(es, opts[i].choice), means[i]))
                    }
                },
            )
        }
    }

    /** The oracle's paired win-chance difference of [alt] over [chosen] (null when [alt] was not a candidate). */
    private fun versus(a: Analysis, alt: Choice, chosen: Choice, es: EngineSession): Double? {
        val x = a.options.firstOrNull { it.choice == alt } ?: return null
        val y = a.options.firstOrNull { it.choice == chosen } ?: return null
        val size = es.setup.seats.count { it.team == Team.RED }
        val wp = { v: DoubleArray -> DoubleArray(v.size) { k -> if (v[k].isNaN()) v[k] else WinChance.of(v[k], size) } }
        return paired(wp(x.values), wp(y.values), null).average().takeIf { !it.isNaN() }
    }

    private class Verdict(val best: Int, val gap: Double, val se: Double, val cf: Double, val n: Int)

    private fun verdict(values: List<DoubleArray>, mine: Int): Verdict {
        val means = values.map { mean(it, null) }
        val bi = means.indices.maxBy { means[it] }
        val diffs = paired(values[bi], values[mine], null)
        val folds = listOf(0, 1).map { f ->
            val sel = values.maxBy { mean(it, 1 - f) }
            paired(sel, values[mine], f).average().takeIf { !it.isNaN() } ?: 0.0
        }
        return Verdict(bi, diffs.average().takeIf { !it.isNaN() } ?: 0.0, se(diffs), folds.average(), diffs.size)
    }

    private fun pair(label: String, value: Double): JsonArray = buildJsonArray {
        add(JsonPrimitive(label))
        add(JsonPrimitive(round2(value)))
    }

    private fun putAll(b: kotlinx.serialization.json.JsonObjectBuilder, prefix: String, o: JsonObject) {
        for ((k, v) in o) b.put(prefix + k.replaceFirstChar { it.uppercase() }, v)
    }

    /** Values of the samples in fold [fold] (k % 2), or all of them. */
    private fun mean(v: DoubleArray, fold: Int?): Double {
        val xs = v.indices.filter { (fold == null || it % 2 == fold) && !v[it].isNaN() }.map { v[it] }
        return if (xs.isEmpty()) Double.NEGATIVE_INFINITY else xs.average()
    }

    private fun paired(a: DoubleArray, b: DoubleArray, fold: Int?): List<Double> =
        a.indices.filter { (fold == null || it % 2 == fold) && !a[it].isNaN() && !b[it].isNaN() }.map { a[it] - b[it] }

    private fun se(x: List<Double>): Double {
        if (x.size < 2) return 0.0
        val m = x.average()
        return sqrt(x.sumOf { (it - m) * (it - m) } / (x.size - 1) / x.size)
    }

    private fun gameLine(es: EngineSession, gameId: String, hTeam: Team): JsonObject {
        val st = es.state
        val teamOf = es.setup.seats.associate { it.heroId to it.team }
        val defeats = mutableMapOf(Team.RED to 0, Team.BLUE to 0)
        val empty = mutableMapOf(Team.RED to 0, Team.BLUE to 0)
        var lastEmpty: String? = null
        for (e in es.events) {
            if (e is GameEvent.Note && e.text.endsWith(" has no card to defend with.")) lastEmpty = e.text
            if (e is GameEvent.Defeated) {
                val t = teamOf[e.unitId] ?: continue
                defeats[t] = defeats.getValue(t) + 1
                if (lastEmpty != null) empty[t] = empty.getValue(t) + 1
                lastEmpty = null
            }
        }
        return buildJsonObject {
            put("game", gameId)
            put("hTeam", hTeam.name)
            put("winner", st.winner?.name ?: "none")
            put("reason", st.winReason?.name ?: "none")
            put("hWon", st.winner == hTeam)
            put("rounds", st.round)
            put("hDefeats", defeats.getValue(hTeam))
            put("sDefeats", defeats.getValue(hTeam.opponent))
            put("hEmpty", empty.getValue(hTeam))
            put("sEmpty", empty.getValue(hTeam.opponent))
            put("heroes", JsonArray(es.setup.seats.map { JsonPrimitive("${it.heroId}:${it.team.name[0]}") }))
        }
    }

    private fun lanes(st: GameState, team: Team): JsonArray = JsonArray(
        st.battleZones.entries.map { (lane, bz) ->
            val own = st.minions.count { it.team == team && it.lane == lane && it.at != null }
            val enemy = st.minions.count { it.team != team && it.lane == lane && it.at != null }
            JsonPrimitive("$lane:$bz:$own-$enemy:w${st.wavesLeft[lane]}")
        },
    )

    private fun heroes(st: GameState, team: Team): JsonArray = JsonArray(
        st.heroes.map { h ->
            val mark = if (h.team == team) "+" else "-"
            JsonPrimitive("$mark${h.id} L${h.level} h${h.hand.size} ${h.at?.let { "${it.q},${it.r}" } ?: "off"}")
        },
    )

    private fun label(es: EngineSession, c: Choice): String = when (c) {
        is Choice.Card -> {
            val card = runCatching { es.engineData.card(c.cardId) }.getOrNull()
            if (card == null) {
                "card:${c.cardId}"
            } else {
                "card:${c.cardId}:${card.primary?.name ?: "-"}:i${card.initiative ?: 0}"
            }
        }

        is Choice.Space -> "space:${c.hex.q},${c.hex.r}"
        is Choice.Unit -> "unit:${c.unitId}"
        is Choice.Token -> "token:${c.tokenId}"
        is Choice.Action -> "action:${c.action.name}"
        is Choice.Option -> "option:${c.index}:${c.label.take(PROMPT)}"
        is Choice.Number -> "number:${c.value}"
        is Choice.Yes -> "yes"
        is Choice.Decline -> "decline:${c.label}"
        is Choice.Pass -> "pass"
    }

    private fun status(st: GameState): String {
        val heroes = st.heroes.joinToString(" | ") { h ->
            val hand = h.cards.filter { it.zone == Zone.HAND }.joinToString(",") { it.id }
            "${h.id}(${h.team.name[0]}) L${h.level} at ${h.at?.let { "${it.q},${it.r}" } ?: "off"} hand[$hand]"
        }
        val minions = st.minions.filter { it.at != null }.joinToString(" ") { m ->
            "${m.team.name[0]}${m.type.name[0]}${m.at!!.q},${m.at!!.r}"
        }
        return "R${st.round}T${st.turn} ${st.step} life R${st.lifeCounters[Team.RED]} B${st.lifeCounters[Team.BLUE]} " +
            "zones ${st.battleZones} waves ${st.wavesLeft} :: $heroes :: $minions"
    }

    private fun replay(specs: String) {
        for (spec in specs.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val parts = spec.split(":")
            val (idx, side, opp) = parts
            val id = parts[3]
            val i = idx.toInt()
            val all = AiHarness.mixedLineups(i + 1, size = format.size)
            println("REPLAY ==== $spec ${all[i]}")
            game(all[i], i, side == "R", opp, stopAt = id.toLong())
        }
    }

    /** A stable number in [0, 1) for a question (which ones the oracle answers twice). */
    private fun unit(gameId: String, id: Long): Double =
        ((HeuristicAi.mix(gameId.hashCode().toLong(), id) ushr 11).toDouble() / (1L shl 53).toDouble())

    private fun round4(v: Double): Double = if (v.isFinite()) kotlin.math.round(v * 10_000.0) / 10_000.0 else -9999.0

    private fun round2(v: Double): Double = if (v.isFinite()) kotlin.math.round(v * 100.0) / 100.0 else -9999.0

    private companion object {
        const val ORACLE_SALT = 0x0EAC1EL
        const val RETEST_SALT = 0x5EC0DL
        const val TOP = 6
        const val OTHER_SAMPLES = 4
        const val RECENT = 30
        const val PROMPT = 60
    }
}
