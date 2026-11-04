package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.PortableMath
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.engine.heavyImmune
import dev.quinton.goa.core.model.catalog.CardColor
import kotlin.math.max
import kotlin.math.min

/**
 * Tests only, measured and not kept (AI.md "v0.14: hand knowledge and card inference"; the variant
 * `cards=learned` plugs it in through [LevelProfile.playModel]).
 *
 * A learned guess of which card of its hand a hero plays (ADR-062 part 2; Skat's policy-based inference,
 * Rebstock et al. 2019): a conditional logit over the hand, z(card) = shared · φ + hero · φ + card bias +
 * card turn slope · turn, where φ are [F] numbers of the card in this state (its printed numbers, today's
 * static score, what it reaches, the hero's danger, the turn). The hero and card parts are data keyed by
 * id; a hero or card the tables do not know adds nothing, so the model stays generic. Reads only the
 * state it is given (a determinized copy in a search).
 */
internal class CardPlayModel(
    val shared: FloatArray,
    val heroes: List<String>,
    /** [heroes].size × [F]. */
    val heroWeights: FloatArray,
    val cards: List<String>,
    /** [cards].size × [CARD_PARAMS]: a bias and a slope on the centred turn. */
    val cardWeights: FloatArray,
) {
    private val heroIndex = heroes.withIndex().associate { it.value to it.index }
    private val cardIndex = cards.withIndex().associate { it.value to it.index }

    init {
        require(shared.size == F && heroWeights.size == heroes.size * F && cardWeights.size == cards.size * CARD_PARAMS)
    }

    /** The logits of [pool]'s cards for [heroId]. */
    fun logits(static: StaticPolicy, ctx: Ctx, heroId: String, pool: List<String>): DoubleArray {
        val phi = features(static, ctx, heroId, pool)
        val turn = turnCentred(ctx)
        val hi = heroIndex[heroId]
        return DoubleArray(pool.size) { i -> logit(phi[i], hi, cardIndex[pool[i]], turn) }
    }

    fun logit(phi: FloatArray, hero: Int?, card: Int?, turn: Double): Double {
        var z = 0.0
        for (j in 0 until F) z += (shared[j] + (if (hero != null) heroWeights[hero * F + j] else 0f)) * phi[j]
        if (card != null) z += cardWeights[card * CARD_PARAMS] + cardWeights[card * CARD_PARAMS + 1] * turn
        return z
    }

    /** P(card) over [pool], mixed with [uniform] of a uniform guess. */
    fun weights(static: StaticPolicy, ctx: Ctx, heroId: String, pool: List<String>, uniform: Double): List<Double> {
        val z = logits(static, ctx, heroId, pool)
        val best = z.maxOrNull() ?: 0.0
        val e = z.map { PortableMath.exp(it - best) }
        val sum = e.sum()
        return e.map { (1 - uniform) * it / sum + uniform / pool.size }
    }

    companion object {
        /** Numbers per card ([features]). */
        const val F: Int = 40
        const val CARD_PARAMS: Int = 2

        /** The checked-in model ([CardPlayWeights]). */
        val CURRENT: CardPlayModel by lazy {
            CardPlayModel(
                CardPlayWeights.shared(),
                CardPlayWeights.HEROES,
                CardPlayWeights.heroes(),
                CardPlayWeights.CARDS,
                CardPlayWeights.cards(),
            )
        }

        fun turnCentred(ctx: Ctx): Double = (ctx.state.turn - 2.5) / 1.5

        /** [F] numbers for each card of [pool] in [heroId]'s hand pool. */
        @Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber")
        fun features(static: StaticPolicy, ctx: Ctx, heroId: String, pool: List<String>): Array<FloatArray> {
            val ev = ctx.ev
            val hi = ctx.info(heroId)
            val h = hi.h
            val at = h.at
            val profiles = pool.map { ev.profile(it) }
            val scores = pool.map { static.playScore(ctx, heroId, Choice.Card(it)).coerceIn(-30.0, 30.0) }
            val bestScore = scores.maxOrNull() ?: 0.0
            val inits = profiles.map { it.initiative }
            val meanInit = if (inits.isEmpty()) 0.0 else inits.average()
            val maxInit = inits.maxOrNull() ?: 0
            val minInit = inits.minOrNull() ?: 0
            val bestDef = profiles.filter { it.canDefend }.maxOfOrNull { it.defense ?: 0 } ?: -1
            // The pool holds this turn's card too, at the hero's own question and when a guess deals it again.
            val spare = pool.size - (5 - ctx.state.turn)
            val exposure = if (at == null) 0.0 else min(ctx.exposure(hi, at), 30.0) / 10
            val far = if (at == null) 0.0 else (-ctx.presence(h.team, at)).coerceIn(0.0, 5.0) / 5
            val turn = turnCentred(ctx)
            // Enemy units and the hero's distance to each: heroes, minions, the last minion of a lane.
            val heroesAt = ArrayList<Int>()
            val minionsAt = ArrayList<Int>()
            val lastAt = ArrayList<Int>()
            if (at != null) {
                for (e in ctx.state.heroes) {
                    val p = e.at ?: continue
                    if (e.team != h.team) heroesAt += HexGrid.distance(at, p)
                }
                for (m in ctx.state.minions) {
                    val p = m.at ?: continue
                    if (m.team == h.team || ctx.g.heavyImmune(m.id)) continue
                    val d = HexGrid.distance(at, p)
                    minionsAt += d
                    if (ctx.state.minions.count { it.team == m.team && it.lane == m.lane && it.at != null } ==
                        1
                    ) {
                        lastAt += d
                    }
                }
            }
            return Array(pool.size) { i ->
                val p = profiles[i]
                val x = FloatArray(F)
                fun b(v: Boolean) = if (v) 1f else 0f
                val hits = p.attackCard || p.forcesDiscard
                val skillReach = max(p.range, p.radius) + 1
                x[0] = (scores[i] / 5).toFloat()
                x[1] = ((scores[i] - bestScore) / 5).toFloat()
                x[2] = p.initiative / 10f
                x[3] = ((p.initiative - meanInit) / 5).toFloat()
                x[4] = b(p.initiative == maxInit)
                x[5] = b(p.initiative == minInit)
                x[6] = b(p.attackCard)
                x[7] = p.attack / 5f
                x[8] = b(p.skillCard)
                x[9] = b(p.canDefend)
                x[10] = (p.defense ?: 0) / 5f
                x[11] = b(p.canDefend && (p.defense ?: 0) >= bestDef)
                x[12] = b(p.hasMovement)
                x[13] = p.movement / 4f
                x[14] = p.reach / 4f
                x[15] = p.radius / 3f
                x[16] = b(p.forcesDiscard)
                x[17] = b(p.block)
                x[18] = b(p.color == CardColor.GOLD)
                x[19] = b(p.color == CardColor.SILVER)
                x[20] = b(p.color == CardColor.RED)
                x[21] = b(p.color == CardColor.GREEN)
                x[22] = b(p.color == CardColor.BLUE)
                x[23] = if (p.basic) 0f else (p.tier ?: 0) / 3f
                x[24] = (p.rating / 10).toFloat()
                x[25] = b(hits && heroesAt.any { it <= p.reach })
                x[26] = b(hits && minionsAt.any { it <= p.reach })
                x[27] = b(hits && lastAt.any { it <= p.reach })
                x[28] = b(hits && heroesAt.none { it <= p.reach } && heroesAt.any { it <= p.reach + 2 })
                x[29] = (exposure * x[9]).toFloat()
                x[30] = (exposure * x[12]).toFloat()
                x[31] = b(spare <= 1) * x[11]
                x[32] = (turn * x[18]).toFloat()
                x[33] = (turn * x[19]).toFloat()
                x[34] = (turn * x[20]).toFloat()
                x[35] = (turn * x[21]).toFloat()
                x[36] = (turn * x[22]).toFloat()
                x[37] = (far * p.movement / 4).toFloat()
                x[38] = b(p.skillCard && (heroesAt.any { it <= skillReach } || minionsAt.any { it <= skillReach }))
                x[39] = b(spare <= 0) * x[6]
                x
            }
        }
    }
}
