package dev.quinton.goa.core.game.ai.heuristic

import kotlin.concurrent.Volatile

/**
 * The named terms of [HEval] (HEVAL.md). Each is a hand-written feature in its own natural unit,
 * Red minus Blue; the evaluation is `Σ wᵢ · fᵢ` in coin-equivalents. The non-linear parts (the lane's
 * win probability, the Life ladder, the threat map) live inside the features.
 *
 * [hand] is the weight the book was written and measured with: with it every term adds exactly what
 * the evaluation added before the terms were split. A term new in wave 3 has hand weight 0.
 */
enum class HTerm(val label: String, val hand: Double) {
    /** `60 × (2W − 1)` per lane: W is P(Red wins the lane) from the push recursion. */
    LANE("lane", 1.4),

    /** The extra `40 × (2W − 1)` of a lane at match point (100 in place of 60). */
    LANE_MATCH("laneMatch", 1.4),

    /** Minions on the lane, Red minus Blue, 1.5 each (3 at match point). */
    MARGIN("margin", 1.0),

    /** The Life ladder (`HContext.lifeLadder`). */
    LIFE("life", 1.0),

    /** 1 for a team one defeat from losing on Life (Blue's minus Red's). */
    LIFE_DANGER("lifeDanger", 0.0),

    /** Set-up pieces on the board (the best four per owner). */
    PIECES("pieces", 1.0),

    /** Coins (×1.3 in the opening rounds). */
    COINS("coins", 1.0),

    /** Levels: the cumulative cost plus 3 a level, a level due, the Ultimate. */
    LEVELS("levels", 1.0),

    /** Hero counters (Gydion's spellbook, wishes). */
    COUNTERS("counters", 1.0),

    /** The hand: 2.5 per needed card, the spare by turn, −3 per missing card. */
    HAND("hand", 1.0),

    /** 0.12 × the card ratings of a hand this team may read. */
    HAND_RATING("handRating", 1.0),

    /** Heroes off the board. */
    OFF_BOARD("offBoard", -8.0),

    /** Lane presence: +1 in the Battle Zone, −0.55 a space outside. */
    PRESENCE("presence", 1.0),

    /** Attack cards in hand that reach an enemy minion (1.6 each, capped at 4.8). */
    OPPORTUNITY("opportunity", 2.0),

    /** A minion in reach of a card still to play, priced by what its kill does to the lane (share 1). */
    LANE_THREAT("laneThreat", HEval.LANE_THREAT),

    /** The pull toward the zone the hero should stand in (+2.5 there, −0.7 a space). */
    ZONE_PULL("zonePull", 1.0),

    /** Expected loss from the threat map (positive; the weight is negative). */
    EXPOSURE("exposure", -1.0),

    /** The playbooks' `evalTerms`. */
    PLAYBOOK("playbook", 1.0),

    /**
     * Expected attack plays that reach an enemy minion: pool weights for a hidden hand, at most the
     * plays left this round (REVIEW-2-design §2.2.4).
     */
    OPP_POOL("oppPool", 0.0),

    /** Minus the actions a hero needs to reach its zone, Fast Travel and respawn included (#13). */
    ACCESS("access", 0.0),

    /**
     * The lane from one projected minion battle (#9, #14): both sides' expected minion kills this round
     * from reachable attack cards, the exact a ≥ 2b push and carry-over at the round's end, and the next
     * push's chance from the new zone's spawns. `(60 or 100) × (2W − 1)`.
     */
    LANE_PROJ("laneProj", 0.0),

    /** The best Defense (printed + items) among the hand's cards that can defend; a readable hand only. */
    DEFENSE("defense", 0.0),

    /** Cards in a readable hand that can defend or block. */
    DEFENDERS("defenders", 0.0),

    /** Friendly melee minions adjacent (+1) and enemy ranged minions within 2 (−1.35), as `HContext.formation`. */
    FORMATION("formation", 0.0),

    /** Minus how far the nearest enemy hero is from the hero's ideal distance (`HeroStyle.idealDistance`). */
    IDEAL_GAP("idealGap", 0.0),

    /** The minion defense modifier (p.14) where the hero stands. */
    MINION_MOD("minionMod", 0.0),

    /** The highest Initiative (printed + items) in a readable hand. */
    INITIATIVE("initiative", 0.0),

    /**
     * Team-level exposure (wave 3 Track B2): each enemy hero's expected loss on its best target among our
     * heroes (`HContext.exposureFrom`: its card this turn and next), summed over the enemy heroes. An enemy
     * plays one card a turn, so its threat is charged once, not to every hero it reaches as [EXPOSURE] does.
     * Positive; the weight is negative.
     */
    TEAM_EXPOSURE("teamExposure", 0.0),

    /**
     * The race for a push that decides the game (a lane at match point, the last wave above all; wave 3
     * Track B2): who empties the Battle Zone first, turn by turn from the heroes' reachable attack plays on
     * minions, with the round-end battle (a ≥ 2b pushes, else 2b − a carries over) and later rounds at the
     * measured kill rate. `100 × (2W − 1)`, W the lane after the race's push.
     */
    LAST_RACE("lastRace", 0.0),
    ;

    companion object {
        val byLabel: Map<String, HTerm> = entries.associateBy { it.label }
    }
}

/**
 * A set of [HTerm] weights plus the scale on the book's minion price (`laneKillDelta`, which the lane
 * term's weight does not reach). Selected through the profile's knobs (HEVAL.md "Knobs"):
 * `hev` picks the set for the book's own evaluations, `hevLeaf` the one a search leaf uses (default:
 * `hev`), and `hw.<label>` / `laneDelta` override single weights for a measurement.
 */
class HEvalWeights(val name: String, val w: DoubleArray, val laneDelta: Double) {
    init {
        require(w.size == HTerm.entries.size) {
            "HEvalWeights $name: ${w.size} weights for ${HTerm.entries.size} terms"
        }
    }

    operator fun get(t: HTerm): Double = w[t.ordinal]

    fun with(t: HTerm, value: Double): HEvalWeights =
        HEvalWeights(name, w.copyOf().also { it[t.ordinal] = value }, laneDelta)

    /** These weights, renamed [newName], with [edits] in place. */
    fun edit(newName: String, vararg edits: Pair<HTerm, Double>, delta: Double = laneDelta): HEvalWeights {
        val c = w.copyOf()
        for ((t, v) in edits) c[t.ordinal] = v
        return HEvalWeights(newName, c, delta)
    }

    companion object {
        /** The weights the book was measured with before wave 3 (the default behaviour). */
        val HAND: HEvalWeights = HEvalWeights("hand", DoubleArray(HTerm.entries.size) { HTerm.entries[it].hand }, 1.0)

        /**
         * Fitted to the outcomes of 1,200 h10 games (h10 self-play and h10 against s8, line-ups 2000–2299),
         * 4-fold cross-validated log-loss 0.587 against the hand weights' 0.593 (HEVAL.md "Fit 1"). The
         * minion opportunity is the pool-weighted [HTerm.OPP_POOL]; off the board carries no flat price.
         */
        val FIT: HEvalWeights = HAND.edit(
            "fit",
            HTerm.LANE to 1.94, HTerm.LANE_MATCH to 1.01, HTerm.MARGIN to -1.83, HTerm.LIFE to 1.16,
            HTerm.COINS to 1.67, HTerm.LEVELS to 1.0, HTerm.HAND to 1.97, HTerm.HAND_RATING to 2.02,
            HTerm.LANE_THREAT to 0.298, HTerm.EXPOSURE to -0.44, HTerm.OPP_POOL to 5.05, HTerm.OPPORTUNITY to 0.0,
            HTerm.OFF_BOARD to 0.0,
        )

        /** The terms fitted by least squares to the old learned net's logit (R² 0.93; HEVAL.md "Distilled"). */
        val DISTIL: HEvalWeights = HAND.edit(
            "distil",
            HTerm.LANE to 1.576, HTerm.LANE_MATCH to 0.304, HTerm.MARGIN to 0.877, HTerm.LIFE to 1.384,
            HTerm.PIECES to 0.868, HTerm.COINS to 1.537, HTerm.LEVELS to 1.918, HTerm.COUNTERS to 0.585,
            HTerm.HAND to 2.208, HTerm.HAND_RATING to 5.137, HTerm.OFF_BOARD to -4.297, HTerm.PRESENCE to 1.144,
            HTerm.OPPORTUNITY to 1.850, HTerm.LANE_THREAT to 0.116, HTerm.ZONE_PULL to 0.255, HTerm.EXPOSURE to -0.392,
            HTerm.PLAYBOOK to 0.250, HTerm.OPP_POOL to 0.366, HTerm.ACCESS to 0.432, HTerm.LANE_PROJ to 0.269,
        )

        /** Every term fitted to outcomes, presence, zone pull, access and off the board included. */
        val FIT_ALL: HEvalWeights = HAND.edit(
            "fitAll",
            HTerm.LANE to 1.969, HTerm.LANE_MATCH to 1.001, HTerm.MARGIN to -1.788, HTerm.LIFE to 1.222,
            HTerm.COINS to 1.847, HTerm.LEVELS to 1.017, HTerm.HAND to 2.010, HTerm.HAND_RATING to 1.928,
            HTerm.OFF_BOARD to 6.785, HTerm.PRESENCE to -0.992, HTerm.LANE_THREAT to 0.309, HTerm.ZONE_PULL to -0.812,
            HTerm.EXPOSURE to -0.428, HTerm.OPP_POOL to 5.857, HTerm.ACCESS to 2.583, HTerm.OPPORTUNITY to 0.0,
        )

        /**
         * The hand weights plus the last-push race ([HTerm.LAST_RACE] 0.5; HEVAL.md "Wave 3 — Track B2"). As the
         * book and the leaf of x10 with the HEval leaf it beat the same search without it by SPRT (57.5%, 240
         * games). As h10's own evaluation it measured even (51.2%, 256 games) and lost one puzzle's defends, so the
         * book keeps [HAND]; the search uses this set by default ([searchDefaults]).
         */
        val RACE: HEvalWeights = HAND.edit("race", HTerm.LAST_RACE to 0.5)

        /** Weight sets by number (`hev=1`). 0 is [HAND]. */
        val SETS: List<HEvalWeights> = listOf(HAND, FIT, DISTIL, FIT_ALL, RACE)

        /** The set the HEval-leaf search (x10) gives its book and its leaf: [RACE]. */
        const val SEARCH_SET: Int = 4

        /**
         * Book knobs a search seats its book with by default: `hev` = [SEARCH_SET] when its leaf is HEval
         * ([heLeaf]), so the book's playouts and the leaf both score with it. A spec's own `hev` overrides it.
         */
        fun searchDefaults(heLeaf: Boolean): Map<String, Double> =
            if (heLeaf) mapOf("hev" to SEARCH_SET.toDouble()) else emptyMap()

        /** The set the book uses when no knob says otherwise. */
        const val DEFAULT_SET: Int = 0

        /** The set a search leaf uses when no knob says otherwise. */
        const val DEFAULT_LEAF_SET: Int = 0

        @Volatile
        private var cache: Triple<HeuristicProfile, Boolean, HEvalWeights>? = null

        /** The weights [profile] evaluates with; [leaf] for a search's leaf. */
        fun of(profile: HeuristicProfile, leaf: Boolean = false): HEvalWeights {
            cache?.let { if (it.first === profile && it.second == leaf) return it.third }
            val r = resolve(profile, leaf)
            cache = Triple(profile, leaf, r)
            return r
        }

        private fun resolve(profile: HeuristicProfile, leaf: Boolean): HEvalWeights {
            val book = profile.knob("hev", DEFAULT_SET.toDouble()).toInt()
            val leafDefault = if ("hev" in profile.tune) book else DEFAULT_LEAF_SET
            val idx = if (leaf) profile.knob("hevLeaf", leafDefault.toDouble()).toInt() else book
            val base = SETS[idx.coerceIn(0, SETS.lastIndex)]
            if (profile.tune.isEmpty()) return base
            val w = base.w.copyOf()
            // The knobs measured before wave 3 keep their meaning: `lane` is the lane's scale, `opp`
            // multiplies the opportunity term's 2.0, `oppLane` is the lane-threat share.
            profile.tune["lane"]?.let {
                w[HTerm.LANE.ordinal] = it
                w[HTerm.LANE_MATCH.ordinal] = it
            }
            profile.tune["opp"]?.let { w[HTerm.OPPORTUNITY.ordinal] = base[HTerm.OPPORTUNITY] * it }
            profile.tune["oppLane"]?.let { w[HTerm.LANE_THREAT.ordinal] = it }
            for ((k, v) in profile.tune) {
                if (!k.startsWith("hw.")) continue
                val t = HTerm.byLabel[k.removePrefix("hw.")] ?: error("No HEval term ${k.removePrefix("hw.")}")
                w[t.ordinal] = v
            }
            return HEvalWeights(base.name, w, profile.knob("laneDelta", base.laneDelta))
        }
    }
}
