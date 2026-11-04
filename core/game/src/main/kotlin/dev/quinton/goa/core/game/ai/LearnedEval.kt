package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.f32
import dev.quinton.goa.core.game.engine.CardInst
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.max

/** What scores a position for one team, in coin-equivalents (positive: good for [Team]). */
internal fun interface PositionEvaluator {
    fun evaluate(state: GameState, team: Team): Double
}

/** Where a searching strength uses the learned evaluation (AI.md "Learned evaluation"). */
internal enum class EvalSource {
    /** The hand-weighted [Evaluator] everywhere. */
    HAND,

    /** The net at the end of every playout; the one-ply looks inside playouts keep the hand evaluation. */
    LEAVES,

    /** The net at playout ends and in the one-ply looks inside playouts. */
    EVERYWHERE,
}

/**
 * A small multilayer perceptron, inputs → [h1] → [h2] → 1, ReLU, plus a linear skip from the inputs
 * to the output (wide and deep), read from [LearnedWeights]; on top (ADR-049), what a team's own
 * heroes and cards add: a per-hero linear correction on that hero's [Features.HERO] numbers and a
 * value per card id and place. A hero or card the tables do not know adds nothing. The first layer
 * is stored input-major (`w1[i * h1 + j]`) so zero inputs cost nothing.
 */
internal class LearnedNet(
    val inputs: Int,
    val h1: Int,
    val h2: Int,
    private val w1: FloatArray,
    private val b1: FloatArray,
    private val w2: FloatArray,
    private val b2: FloatArray,
    private val w3: FloatArray,
    /** The linear skip from the inputs to the output (wide and deep). */
    private val wl: FloatArray,
    private val b3: Float,
    /** Coin-equivalents per unit of logit: the hand evaluation's scale, fitted when the net was trained. */
    val coinScale: Double,
    heroes: List<String> = emptyList(),
    /** Per hero, [HERO_COLS] numbers: the weights on its [Features.HERO] vector, then a constant. */
    private val wh: FloatArray = FloatArray(0),
    cards: List<String> = emptyList(),
    /** Per card id, [CARD_PLACES] numbers: its value in each place ([cardPlace]). */
    private val wc: FloatArray = FloatArray(0),
) {
    private val heroRow: Map<String, Int> = heroes.withIndex().associate { (i, h) -> h to i * HERO_COLS }
    private val cardRow: Map<String, Int> = cards.withIndex().associate { (i, c) -> c to i * CARD_PLACES }

    init {
        require(
            w1.size == inputs * h1 && b1.size == h1 && w2.size == h1 * h2 && b2.size == h2 && w3.size == h2 &&
                wl.size == inputs && wh.size == heroes.size * HERO_COLS && wc.size == cards.size * CARD_PLACES,
        ) {
            "Learned net: weight sizes do not match $inputs → $h1 → $h2 → 1, ${heroes.size} heroes, ${cards.size} cards"
        }
    }

    /**
     * The output for the inputs `x[off until off + inputs]`, in float arithmetic: every operation is
     * done in Double and rounded to 32 bits ([f32]), which is exactly the JVM's float arithmetic and
     * makes the browser (where Kotlin's `Float` is a double) score every position with the same bits.
     */
    fun forward(x: FloatArray, off: Int): Float {
        val a1 = DoubleArray(h1) { b1[it].toDouble() }
        var out = f32(b3.toDouble())
        for (i in 0 until inputs) {
            val xi = x[off + i].toDouble()
            if (xi == 0.0) continue
            out = f32(out + f32(xi * wl[i]))
            val row = i * h1
            for (j in 0 until h1) a1[j] = f32(a1[j] + f32(xi * w1[row + j]))
        }
        for (k in 0 until h2) {
            var s = b2[k].toDouble()
            val row = k * h1
            for (j in 0 until h1) s = f32(s + f32(w2[row + j] * max(0.0, a1[j])))
            out = f32(out + f32(w3[k] * max(0.0, s)))
        }
        return out.toFloat()
    }

    /** What [team]'s heroes and cards add to its output; [perHero] as [Features.extract] writes it. */
    fun corrections(state: GameState, team: Team, perHero: FloatArray): Double {
        var out = 0.0
        val size = Features.HERO.size
        for ((i, h) in state.heroes.withIndex()) {
            if (h.team != team) continue
            heroRow[h.id]?.let { row ->
                out = f32(out + wh[row + size])
                for (k in 0 until size) {
                    val v = perHero[i * size + k].toDouble()
                    if (v != 0.0) out = f32(out + f32(v * wh[row + k]))
                }
            }
            for (c in h.cards) {
                val place = cardPlace(c)
                if (place >= 0) cardRow[c.id]?.let { out = f32(out + wc[it + place]) }
            }
        }
        return out
    }

    /** Training's view of the tables: a hero's row (table order) on its numbers at `v[off]`; a card and place cell. */
    fun tableHero(row: Int, v: FloatArray, off: Int): Double {
        if (wh.isEmpty()) return 0.0
        val size = Features.HERO.size
        var out = wh[row * HERO_COLS + size].toDouble()
        for (k in 0 until size) out += wh[row * HERO_COLS + k] * v[off + k]
        return out
    }

    fun tableCard(cell: Int): Double = if (wc.isEmpty()) 0.0 else wc[cell].toDouble()

    companion object {
        /** A hero's [Features.HERO] numbers and a constant. */
        val HERO_COLS: Int = Features.HERO.size + 1

        /** In hand, played and unresolved, resolved face up, an item, in a hero's extra place (a spellbook). */
        const val CARD_PLACES: Int = 5

        /** The column of a card's place in the card table, −1 where its value is not looked at. */
        fun cardPlace(c: CardInst): Int = when (c.zone) {
            Zone.HAND -> 0
            Zone.PLAYED -> 1
            Zone.RESOLVED -> if (c.faceDown) -1 else 2
            Zone.ITEM -> 3
            Zone.EXTRA -> 4
            else -> -1
        }

        /** The checked-in net (generated by `LearnedTrainTest`, AI.md "How to retrain"): v0.10's, `b1s3`. */
        val CURRENT: LearnedNet by lazy { LearnedWeights.net() }

        /**
         * The nets strength 10 averages (ADR-060): three seeds of one training run, [CURRENT] first. The
         * mean of their scores is what the search overrates less than any one of them.
         */
        val ENSEMBLE: List<LearnedNet> by lazy { listOf(CURRENT, LearnedWeights2.net(), LearnedWeights3.net()) }
    }
}

/** The weights in source: 32-bit floats, little-endian, Base64, so a value keeps its bits at 5.3 characters. */
internal object LearnedCodec {
    @OptIn(ExperimentalEncodingApi::class)
    fun floats(text: String, n: Int): FloatArray {
        val b = Base64.decode(text)
        require(b.size == n * 4) { "Learned weights: ${b.size} bytes for $n floats" }
        return FloatArray(n) { i ->
            val o = i * 4
            Float.fromBits(
                (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
                    ((b[o + 2].toInt() and 0xff) shl 16) or (b[o + 3].toInt() shl 24),
            )
        }
    }
}

/**
 * The learned evaluation (ADR-047 part B, ADR-049): Red's chance to win is σ(N(Red) − N(Blue)), where
 * N is the net on a team's features plus what its heroes and cards add, so swapping the teams negates
 * the score exactly; returned in coin-equivalents (logit × [LearnedNet.coinScale], the hand
 * evaluation's scale) so the search's noise and risk settings keep their meaning. A finished game
 * scores like [Evaluator]. Reads only the state it is given (a determinized copy in a search).
 *
 * With several [nets] (ADR-060) the features are extracted once and the score is the mean of the nets'
 * scores, each in its own coin scale; one net scores exactly as before.
 */
internal class NetEvaluator(data: EngineData, private val nets: List<LearnedNet>) : PositionEvaluator {
    constructor(data: EngineData, net: LearnedNet = LearnedNet.CURRENT) : this(data, listOf(net))

    private val features = Features(data)

    init {
        require(nets.isNotEmpty()) { "Learned evaluation without a net" }
        for (net in nets) {
            require(net.inputs == Features.SIZE) {
                "Learned net expects ${net.inputs} inputs, the features are ${Features.SIZE}"
            }
        }
    }

    /** The logit of P(Red wins) by the first net. */
    fun redLogit(state: GameState): Double {
        val perHero = FloatArray(state.heroes.size * Features.HERO.size)
        return logit(nets[0], state, extract(state, perHero), perHero)
    }

    private fun extract(state: GameState, perHero: FloatArray): FloatArray {
        val x = FloatArray(2 * Features.SIZE)
        features.extract(state, x, perHero)
        return x
    }

    private fun logit(net: LearnedNet, state: GameState, x: FloatArray, perHero: FloatArray): Double {
        val red = net.forward(x, 0).toDouble() + net.corrections(state, Team.RED, perHero)
        val blue = net.forward(x, Features.SIZE).toDouble() + net.corrections(state, Team.BLUE, perHero)
        return f32(f32(red) - f32(blue))
    }

    /** Red's score in coin-equivalents: the mean of the nets' scores. */
    private fun redScore(state: GameState): Double {
        val perHero = FloatArray(state.heroes.size * Features.HERO.size)
        val x = extract(state, perHero)
        if (nets.size == 1) return logit(nets[0], state, x, perHero) * nets[0].coinScale
        var sum = 0.0
        for (net in nets) sum += logit(net, state, x, perHero) * net.coinScale
        return sum / nets.size
    }

    override fun evaluate(state: GameState, team: Team): Double {
        val red =
            state.winner?.let { if (it == Team.RED) Evaluator.WIN - state.round else -Evaluator.WIN + state.round }
                ?: redScore(state)
        return if (team == Team.RED) red else -red
    }
}
