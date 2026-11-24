package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.SampleRunner
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team

/**
 * Builds the card play's matrix ([CardEquilibrium.Matrix], Wave 4 Track G, knob `eq`).
 *
 * Strength 10 knows the enemy hands (ADR-062) but not the enemy's facedown card, so the only uncertainty
 * before the reveal is which card each enemy puts down: a simultaneous choice. Each enemy hero whose card
 * is still unknown gets the opponent model's probabilities over its hand (the book's softmax, as the search
 * deals it now, computed with every hand card back in hand, so it cannot see our card). The joint plays the
 * model finds likeliest become the columns ([CardEquilibrium.jointColumns], at most the column cap):
 * - an enemy that already played: its card is dealt by the determinizer as the column says (one-hot
 *   weights on the same seed, so every column is the same deal but for those cards);
 * - an enemy still to play: the playout plays the column's card for it ([valueOf]'s `forced`).
 * Our cards are the rows (the search's own candidates, the book's first). Teammates still to play are
 * answered by the book inside the playout, as in the search: one AI plays the whole team, and the
 * teammate's real answer comes after ours and sees it, so modelling it as a reply conditioned on our card
 * is the team's sequential choice, not a simultaneous one. Every cell is one deterministic book playout
 * to the search's horizon, valued as our win chance ([CardEquilibrium.winChance]).
 *
 * Reads only determinized copies (the same `determinizeWith` view as the search); deterministic.
 */
internal class CardEquilibriumSearch(private val policy: BookPolicy, private val runner: SampleRunner) {
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun matrix(
        es: EngineSession,
        d: Decision,
        config: HybridConfig,
        seed: Long,
        seenOf: (Team) -> Map<String, Set<String>>,
        humans: Set<String>,
        cancelled: () -> Boolean,
        candidates: (det: EngineSession, dp: Decision) -> List<Choice>,
        valueOf: (fork: EngineSession, salt: Long, forced: Map<String, Choice>) -> Double,
        chance: (Double) -> Double,
    ): CardEquilibrium.Matrix? {
        if (d.kind != DecisionKind.PLAY_CARD || d.options.any { it !is Choice.Card }) return null
        val team = d.team
        val data = es.engineData
        val setup = es.setup
        val seenByUs = seenOf(team)
        // The enemies whose played card the determinizer deals: hero → (pool, model weights).
        val dealt = LinkedHashMap<String, Pair<List<String>, List<Double>>>()
        val model = { st: GameState, hero: String, pool: List<String> ->
            policy.playWeights(data, setup, st, hero, pool, config.model, config.tau, seenOf) ?: List(pool.size) { 1.0 }
        }
        fun deal(force: Map<String, String>?): EngineSession {
            val weight = { st: GameState, hero: String, pool: List<String> ->
                val card = force?.get(hero)
                if (card != null && card in pool) {
                    pool.map { if (it == card) 1.0 else 0.0 }
                } else {
                    val w = model(st, hero, pool)
                    val h = st.hero(hero)
                    if (force == null && h != null && h.team != team) dealt[hero] = pool to w
                    w
                }
            }
            val options = Determinizer.Options(
                seen = seenByUs,
                playWeight = weight,
                hidePlayedOf = humans,
                knowHands = policy.profile.knowHands,
            )
            return es.determinizeWith(team, seed, options, quiet = true)
        }
        val base = deal(null)
        val dp = base.pending?.takeIf { it.seat == d.seat && it.kind == d.kind } ?: return null
        if (cancelled()) return null
        // One played card each: a hero that put down more than one keeps the model's deal in every column.
        val perHero = ArrayList<Pair<String, List<Pair<String, Double>>>>()
        val down = HashSet<String>()
        for ((hero, pw) in dealt) {
            val played = base.state.hero(hero)?.cards?.count { it.zone == Zone.PLAYED } ?: 0
            if (played != 1 || pw.first.size < 2) continue
            perHero += hero to pw.first.zip(pw.second.normalised())
            down += hero
        }
        val toPlay = base.state.heroes.filter {
            it.team != team && !base.state.revealed && it.unresolved.isEmpty() && it.hand.size > 1
        }
        for (h in toPlay) {
            val pool = h.hand.map { it.id }.sorted()
            val w = policy.playWeights(
                data,
                setup,
                base.state,
                h.id,
                pool,
                maxOf(1, config.replyModel),
                config.replyTau,
                seenOf,
            ) ?: List(pool.size) { 1.0 }
            perHero += h.id to pool.zip(w.normalised())
        }
        val cap = if (config.columnCap > 0) config.columnCap else CardEquilibrium.DEFAULT_COLUMNS
        val joint = CardEquilibrium.jointColumns(perHero, cap)
        val mass = joint.sumOf { it.second }
        val rows = candidates(base, dp).filter { it in dp.options }
        if (rows.size < 2) return null
        val cols = joint.map { (cards, _) ->
            val forceDown = cards.filterKeys { it in down }
            val det = if (forceDown.isEmpty()) base else deal(forceDown)
            val dpc = det.pending?.takeIf { it.seat == d.seat && it.kind == d.kind }
            Triple(det, dpc, cards.filterKeys { it !in down }.mapValues { Choice.Card(it.value) as Choice })
        }
        val payoff = Array(rows.size) { DoubleArray(cols.size) { Double.NaN } }
        val tasks = ArrayList<() -> Unit>()
        // One salt for every cell: the book answers alike wherever the positions agree (common random numbers).
        val salt = HeuristicAi.mix(seed, COLUMN_SALT)
        for ((j, col) in cols.withIndex()) {
            val (det, dpc, forced) = col
            if (dpc == null) continue
            for ((i, c) in rows.withIndex()) {
                if (c !in dpc.options) continue
                tasks += {
                    if (!cancelled()) {
                        val f = det.forkForAnswer()
                        if (BookPolicy.play(f, dpc, c)) payoff[i][j] = chance(valueOf(f, salt, forced))
                    }
                }
            }
        }
        runner.runAll(tasks)
        if (cancelled()) return null
        // A cell that could not be played (a refused answer) leaves its row out; a column without a deal too.
        val keepCols = cols.indices.filter { j -> payoff.any { !it[j].isNaN() } }
        val keepRows = rows.indices.filter { i -> keepCols.all { !payoff[i][it].isNaN() } }
        if (keepRows.size < 2 || keepCols.isEmpty()) return null
        val weight = keepCols.map { joint[it].second }.toDoubleArray().normalised()
        return CardEquilibrium.Matrix(
            rows = keepRows.map { rows[it] },
            columns = keepCols.map { joint[it].first },
            columnWeight = weight,
            payoff = Array(keepRows.size) { r -> DoubleArray(keepCols.size) { c -> payoff[keepRows[r]][keepCols[c]] } },
            mass = mass,
            enemiesDown = down.size,
            enemiesToPlay = toPlay.size,
        )
    }

    private fun List<Double>.normalised(): List<Double> {
        val s = sum()
        return if (s > 0) map { it / s } else map { 1.0 / size }
    }

    private fun DoubleArray.normalised(): DoubleArray {
        val s = sum()
        return if (s > 0) DoubleArray(size) { this[it] / s } else DoubleArray(size) { 1.0 / size }
    }

    private companion object {
        const val COLUMN_SALT = 0x6E9_C01L
    }
}
