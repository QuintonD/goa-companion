package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.monotonicNanos
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachPlan
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession

/**
 * The round plan at a card play (ADR-051): candidate orders of the hero's cards for the turns left are
 * played to the end of the round on the same guesses of the hidden cards, the rest of the table by the
 * playout policy. An order is told only when the same one is best in a clear majority of the samples;
 * otherwise only the card to keep, when that is stable; otherwise nothing. Own cards only: no hidden
 * card of anyone else is named. Stops at [deadline] with no plan rather than half of one.
 */
internal class RoundPlanner(
    private val f: CoachFacts,
    private val searcher: Searcher,
    private val seed: Long,
    private val runner: SampleRunner,
    private val deadline: Long,
    private val cancelled: () -> Boolean,
) {
    private val d: Decision = f.d

    @Suppress("ReturnCount")
    fun plan(firstCards: List<String>, close: Boolean): CoachPlan? {
        if (d.kind != DecisionKind.PLAY_CARD || d.options.any { it !is Choice.Card } || d.options.size < 2) return null
        val plays = PLAYS_PER_ROUND - f.view.turn + 1
        if (plays < 2) return null
        val copies = (0 until SAMPLES).mapNotNull { k ->
            searcher.determinize(f.es, d, Searcher.mix(seed, PLAN_SEED + k)).takeIf { it.pending?.kind == d.kind }
        }
        if (copies.size < MIN_SAMPLES) return null
        val hand = d.options.filterIsInstance<Choice.Card>().map { it.cardId }
        val orders = candidates(copies, hand, firstCards, plays) ?: return null
        val values = Array(orders.size) { DoubleArray(copies.size) { Double.NaN } }
        val tasks = ArrayList<() -> Unit>()
        for (i in orders.indices) {
            for (k in copies.indices) tasks += { values[i][k] = playOrder(copies[k], orders[i]).second }
        }
        for (batch in tasks.chunked(runner.width)) {
            if (cancelled() || monotonicNanos() > deadline) return null
            runner.runAll(batch)
        }
        // A sample where orders tie speaks for none of them.
        val best = copies.indices.mapNotNull { k ->
            val top = orders.indices.maxOf { values[it][k] }
            orders.indices.filter { values[it][k] >= top - TIE }.singleOrNull()
        }
        val need = (copies.size * MAJORITY + 0.999).toInt()
        val winner = best.groupingBy { it }.eachCount().entries
            .sortedWith(compareBy({ -it.value }, { it.key })).firstOrNull() ?: return null
        val detail = "Best in ${winner.value} of ${copies.size} guesses of the hidden cards played to the end of " +
            "the round. " +
            "A card used to defend is discarded (p.10) and changes the plan; all cards come back at the end of the round (p.8)."
        // Against a clear first card an order that starts elsewhere would contradict the sheet's first line.
        val firstAgrees = close || orders[winner.key].first() == firstCards.first()
        if (winner.value >= need && orders.size >= 2 && firstAgrees) {
            val order = orders[winner.key]
            val keep = hand.filter { it !in order }
            return CoachPlan(order, keep.singleOrNull(), planText(order, keep), detail)
        }
        val kept = best.mapNotNull { i -> hand.filter { it !in orders[i] }.singleOrNull() }
        val keep = kept.groupingBy {
            it
        }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key })).firstOrNull()
        // "Keep this one" under a sheet that clearly plays it first would contradict the first line.
        if (keep != null && keep.value >= need && (close || keep.key != firstCards.first())) {
            return CoachPlan(
                emptyList(),
                keep.key,
                "Keep ${f.cardName(keep.key)} unplayed this round; the order of the others isn't settled.",
                "It stays unplayed in ${keep.value} of ${copies.size} guesses played to the end of the round. " +
                    "All cards come back at the end of the round (p.8).",
            )
        }
        return null
    }

    private fun planText(order: List<String>, keep: List<String>): String {
        val names = order.map { f.cardName(it) ?: "a card" }
        val sequence = "Play ${names.first()} now" + names.drop(1).joinToString("") { ", then $it" }
        val kept = keep.singleOrNull()?.let { "; keep ${f.cardName(it)}" }.orEmpty()
        return "$sequence$kept."
    }

    /**
     * The orders worth playing: what the playouts themselves play after each first card (their round plan
     * on each guess), the most frequent first. Null when nothing can be asked in time.
     */
    private fun candidates(
        copies: List<EngineSession>,
        hand: List<String>,
        firstCards: List<String>,
        plays: Int,
    ): List<List<String>>? {
        val firsts = firstCards.filter { it in hand }.take(MAX_FIRST)
        if (firsts.isEmpty()) return null
        val counted = LinkedHashMap<List<String>, Int>()
        for (first in firsts) {
            for (copy in copies) {
                if (cancelled() || monotonicNanos() > deadline) return null
                val (order, _) = playOrder(copy, listOf(first))
                val full = order.take(plays)
                if (full.size == plays) counted[full] = (counted[full] ?: 0) + 1
            }
        }
        val natural = counted.entries.sortedWith(compareBy({ -it.value }, { it.key.joinToString() })).map { it.key }
        // The rival of each: the same first card with the rest the other way round, or with the kept card in.
        val rivals = natural.flatMap { order ->
            val rest = order.drop(1)
            val kept = hand.filter { it !in order }
            listOfNotNull(
                (listOf(order.first()) + rest.reversed()).takeIf { rest.size >= 2 },
                kept.firstOrNull()?.takeIf { rest.isNotEmpty() }?.let { order.dropLast(1) + it },
            )
        }
        return (natural.take(MAX_ORDERS / 2) + rivals + natural).distinct().take(MAX_ORDERS).takeIf { it.isNotEmpty() }
    }

    /** One guess played to the end of the round with [plan] first: the cards the hero played, and the value. */
    private fun playOrder(copy: EngineSession, plan: List<String>): Pair<List<String>, Double> {
        val s = copy.forkSession()
        val round = s.state.round
        val played = ArrayList<String>()
        var left = plan
        var n = 0
        while (n++ < MAX_STEPS) {
            val q = s.pending ?: break
            val st = s.state
            if (st.round != round) break
            val own = q.kind == DecisionKind.PLAY_CARD && q.seat == f.seat
            val forced = if (own) left.firstOrNull { Choice.Card(it) in q.options } else null
            val c: Choice = when {
                q.options.size == 1 -> q.options[0]
                forced != null -> Choice.Card(forced)
                q.kind == DecisionKind.PLAY_CARD -> searcher.static.best(st, q)
                searcher.opaque(st, q) -> searcher.greedy(s, q)
                else -> searcher.static.best(st, q)
            }
            if (own && c is Choice.Card) {
                played += c.cardId
                left = left.filter { it != c.cardId }
            }
            s.answer(q.id, c)
        }
        return played to searcher.leaf.evaluate(s.state, f.team)
    }

    private companion object {
        const val SAMPLES = 6
        const val MIN_SAMPLES = 4
        const val MAJORITY = 2.0 / 3.0
        const val MAX_FIRST = 3
        const val MAX_ORDERS = 6
        const val TIE = 0.01
        const val MAX_STEPS = 1500
        const val PLAYS_PER_ROUND = 4
        const val PLAN_SEED = 7_001L
    }
}
