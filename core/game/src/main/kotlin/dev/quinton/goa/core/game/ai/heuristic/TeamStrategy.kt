package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.domain.concurrentMap
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max
import kotlin.math.min

/**
 * Track V, the win condition (GENERAL.md §4a, G140–G148): what a team is good at, what it is playing for, and each
 * hero's job inside that plan. Re-derived from the state at every decision (no memory) and from public information
 * only: the heroes' printed kits (their whole catalog deck, which is public), levels, items, Life, lanes and waves.
 * The same function gives a team its own plan and the plan it reads for the enemy.
 *
 * The plan acts in four places, each its own knob under the master `plan`:
 * - `plan.style` (from 7): [style], the one hook in [HContext.style]: aggression, lane weight and keep-defender by job
 *   and by the Life race, pulled toward one team target so heroes of a team do not split (G142, G143).
 * - `plan.race` (from 7): [deathScale], the one hook in [HContext.killWorth]: a death is worth more to the side whose
 *   road it is (hunt) or who cannot afford it, less to the side that can afford it in the last push (G144).
 * - `plan.upgrade` (from 5): [itemScale], [keepBias] and [upgradeBias] in [CardRules]: the job and what the team
 *   lacks choose the upgrade and the item; the hero file's `upgradeOrder` stays the order (G145–G147).
 * - `plan.econ` (from 5): [evalTerm], the one line in [HEval]: the Tier III step at level 5, a level that is due this
 *   round, and the Pity coin (G148).
 */
internal object TeamStrategy {
    /** What a team plays for. Economy is in every plan: SCALE is "nothing else is the shorter road yet". */
    enum class Plan { KILL, PUSH, SCALE }

    /** A hero's job inside the plan. */
    enum class Job { FINISHER, STRIPPER, ANCHOR, PUSHER, FARMER }

    /**
     * One hero's printed kit, each 0..~1.2: [kill] attack, initiative and forced discards; [clear] attack cards,
     * range and radius (wave clear); [durability] defense and blocks; [mobility] movement; [setup] strips and skills.
     */
    data class Kit(val kill: Double, val clear: Double, val durability: Double, val mobility: Double, val setup: Double)

    /** A team's view: its plan, the score of each plan, every hero's job, and the Life race. */
    class View(
        val team: Team,
        val plan: Plan,
        val scores: Map<Plan, Double>,
        val jobs: Map<String, Job>,
        /** Our deaths to lose and theirs ([HContext.deathsToLose]). */
        val ours: Int,
        val theirs: Int,
        /** Any lane is on its last wave. */
        val lastWave: Boolean,
        /** Sum of the team's and the enemy's kits. */
        val kit: Kit,
        val enemyKit: Kit,
    ) {
        /** We cannot afford a death and they can (G144: play safe). */
        val danger: Boolean get() = ours <= 1 && theirs >= 2

        /** We can afford a death and they cannot (G144: the last push is easier for us). */
        val afford: Boolean get() = ours >= 2 && theirs <= 1

        /** Their Life is the shorter road (G143: hunt). */
        val hunt: Boolean get() = theirs <= 2 && theirs < ours
    }

    // ---- switches -------------------------------------------------------------------------------------------------

    fun on(ctx: HContext, knob: String, from: Int): Boolean =
        ctx.profile.on(MASTER, true) && ctx.profile.on(knob, ctx.profile.strength >= from)

    private fun styleOn(ctx: HContext) =
        ctx.profile.heroPlaybooks && on(ctx, "plan.style", STYLE_FROM) && sizeOn(ctx)

    private fun raceOn(ctx: HContext) = on(ctx, "plan.race", RACE_FROM) && sizeOn(ctx)

    /**
     * The jobs and the Life race act on teams of at most `plan.size` heroes (default 2). Measured (RESULTS.md,
     * Track V): in 3v3 they cost games (h9 22-42 against the plan-off book: the plan side killed less and died more), in 2v2
     * they did not (72-56); upgrades and economy act at every size.
     */
    private fun sizeOn(ctx: HContext): Boolean =
        ctx.state.heroes.count { it.team == ctx.team } <= ctx.profile.knob("plan.size", SIZE.toDouble())

    fun upgradeOn(ctx: HContext) = on(ctx, "plan.upgrade", UPGRADE_FROM)

    private fun econOn(ctx: HContext) = on(ctx, "plan.econ", ECON_FROM)

    // ---- the model ------------------------------------------------------------------------------------------------

    /** [team]'s view of the game, once per context. */
    fun view(ctx: HContext, team: Team): View = ctx.teamViews.getOrPut(team) { derive(ctx, team) }

    /** [heroId]'s job in its team's plan, or null when the hero is unknown. */
    fun job(ctx: HContext, heroId: String): Job? {
        val h = ctx.state.hero(heroId) ?: return null
        return view(ctx, h.team).jobs[heroId]
    }

    /** The printed kit of [heroId]: every coloured, Gold and Silver card of its catalog deck (public). */
    fun kit(ctx: HContext, heroId: String): Kit {
        val h = ctx.state.hero(heroId) ?: return ZERO
        val raw = rawStyle(ctx, heroId)
        kits[heroId]?.let { (style, kit) -> if (style == raw) return kit }
        // A playbook other than the registry's (an ablation) is computed and not kept.
        return kitOf(ctx, h, raw).also { if (raw == registryStyle(ctx, heroId)) kits[heroId] = raw to it }
    }

    private fun rawStyle(ctx: HContext, heroId: String): HeroStyle =
        if (ctx.profile.heroPlaybooks) ctx.playbooks(heroId).style else HeroStyle.DEFAULT

    private fun registryStyle(ctx: HContext, heroId: String): HeroStyle =
        if (ctx.profile.heroPlaybooks) HeroPlaybooks.forHero(heroId).style else HeroStyle.DEFAULT

    private fun kitOf(ctx: HContext, h: HeroState, style: HeroStyle): Kit {
        val cards = h.cards.map { ctx.card(it.id) }.filter { it.color in KIT_COLOURS }.distinctBy { it.id }
        if (cards.isEmpty()) return ZERO
        val n = cards.size.toDouble()
        val attacks = cards.filter { it.attackCard }
        val atk = attacks.map { it.attack.toDouble() }.average0()
        val init = attacks.map { it.initiative.toDouble() }.average0()
        val strip = cards.count { it.forcesDiscard || it.discardOrDefeat } / n
        val area = cards.count { it.radius > 0 } / n
        val ranged = if (attacks.isEmpty()) 0.0 else attacks.count { it.ranged } / attacks.size.toDouble()
        val reach = attacks.map { it.reach.toDouble() }.average0()
        val def = cards.filter { it.canDefend && it.defense != null }.map { it.defense!!.toDouble() }.average0()
        val blocks = cards.count { it.block } / n
        val mob = cards.filter { it.hasMovement }.map { it.movement.toDouble() }.average0()
        val skills = cards.count { it.skillCard } / n
        val role = style.role
        val kill = 0.40 * norm(atk, 2.0, 7.0) + 0.15 * norm(init, 5.0, 11.0) + 0.20 * min(1.0, strip * 2.0) +
            0.25 * style.aggression + roleBonus(role, HeroRole.ASSASSIN, 0.15) + roleBonus(role, HeroRole.FIGHTER, 0.05)
        val clear = 0.35 * (attacks.size / n) + 0.25 * min(1.0, ranged + area) + 0.15 * norm(reach, 1.0, 4.0) +
            0.25 * min(1.0, style.laneWeight) + roleBonus(role, HeroRole.PUSHER, 0.15) +
            roleBonus(role, HeroRole.RANGED, 0.05)
        val durability = 0.65 * norm(def, 2.0, 6.0) + 0.15 * min(1.0, blocks * 3.0) + 0.2 * (1.0 - style.aggression) +
            roleBonus(role, HeroRole.TANK, 0.2)
        val setup = 0.6 * min(1.0, strip * 2.0) + 0.25 * min(1.0, skills * 2.0) +
            roleBonus(role, HeroRole.SETUP, 0.2) + roleBonus(role, HeroRole.SUPPORT, 0.15)
        return Kit(kill, clear, durability, norm(mob, 1.0, 4.0), setup)
    }

    private fun derive(ctx: HContext, team: Team): View {
        val state = ctx.state
        val enemy = HContext.other(team)
        val mine = state.heroes.filter { it.team == team }
        val theirs = state.heroes.filter { it.team == enemy }
        val kit = sum(mine.map { kit(ctx, it.id) })
        val enemyKit = sum(theirs.map { kit(ctx, it.id) })
        val size = max(1, mine.size).toDouble()
        val dOurs = ctx.deathsToLose(team)
        val dTheirs = ctx.deathsToLose(enemy)
        var toWin = Int.MAX_VALUE
        var lastWave = false
        var margin = 0
        for (lane in state.battleZones.keys) {
            toWin = min(toWin, stepsToEnemyThrone(ctx, lane, team))
            if ((state.wavesLeft[lane] ?: 9) <= 1) lastWave = true
            margin = max(margin, ctx.minionCount(team, lane) - ctx.minionCount(enemy, lane))
        }
        val levelGap = mine.map { it.level.toDouble() }.average0() - theirs.map { it.level.toDouble() }.average0()
        val early = state.round <= 2 && state.heroes.all { it.level <= 3 }
        val wavesLeft = state.wavesLeft.values.maxOrNull() ?: 1

        // G140: composition, per hero (team sums are per hero so 2v2 and 3v3 read alike).
        var kill = 0.6 * (kit.kill - enemyKit.durability) / size
        var push = 0.6 * (kit.clear - enemyKit.clear) / size
        var scale = 0.0
        // G141: the race shifts the plan. Their Life is a road when one or two deaths end the game.
        if (dTheirs <= 1) kill += 1.0 else if (dTheirs == 2) kill += 0.35
        if (dTheirs < dOurs) kill += 0.25
        if (toWin <= 1) push += 0.8
        if (lastWave) push += 0.5
        if (margin >= 2) push += 0.3
        if (early) scale += 0.6
        if (levelGap < 0) scale += 0.3 * min(2.0, -levelGap)
        if (wavesLeft >= 3) scale += 0.2
        val scores = linkedMapOf(Plan.KILL to kill, Plan.PUSH to push, Plan.SCALE to scale)
        val plan = scores.entries.maxBy { it.value }.key
        return View(team, plan, scores, jobs(ctx, mine, plan), dOurs, dTheirs, lastWave, kit, enemyKit)
    }

    /**
     * G142: each hero's job. KILL: the best killer finishes, the best set-up strips, a third farms. PUSH: the most
     * durable anchors, the rest push. SCALE: the most durable anchors, the rest farm. Ties go to the hero id.
     */
    private fun jobs(ctx: HContext, mine: List<HeroState>, plan: Plan): Map<String, Job> {
        val heroes = mine.sortedBy { it.id }
        val kits = heroes.associate { it.id to kit(ctx, it.id) }
        val out = LinkedHashMap<String, Job>()
        fun best(by: (Kit) -> Double): String? =
            heroes.filter { it.id !in out }.maxByOrNull { by(kits.getValue(it.id)) }?.id
        when (plan) {
            Plan.KILL -> {
                best { it.kill }?.let { out[it] = Job.FINISHER }
                best { it.setup + 0.5 * it.kill }?.let { out[it] = Job.STRIPPER }
                for (h in heroes) out.getOrPut(h.id) { Job.FARMER }
            }

            Plan.PUSH -> {
                best { it.durability }?.let { out[it] = Job.ANCHOR }
                for (h in heroes) out.getOrPut(h.id) { Job.PUSHER }
            }

            Plan.SCALE -> {
                best { it.durability }?.let { out[it] = Job.ANCHOR }
                for (h in heroes) out.getOrPut(h.id) { Job.FARMER }
            }
        }
        return out
    }

    // ---- the plan acts ----------------------------------------------------------------------------------------------

    /**
     * G142–G144, the [HContext.style] hook: [base] modulated by the hero's job and its team's Life race, and pulled
     * toward the team's target aggression so a 2v2 team does not hunt with one hero and farm with the other.
     */
    fun style(ctx: HContext, heroId: String, base: HeroStyle): HeroStyle {
        if (!styleOn(ctx)) return base
        val h = ctx.state.hero(heroId) ?: return base
        val v = view(ctx, h.team)
        val job = v.jobs[heroId] ?: return base
        var aggression = base.aggression + when (job) {
            Job.FINISHER -> 0.15
            Job.STRIPPER -> 0.08
            Job.ANCHOR -> -0.05
            Job.PUSHER, Job.FARMER -> -0.10
        }
        if (v.danger) aggression -= 0.12
        if (v.afford || v.hunt) aggression += 0.10
        val target = if (v.plan == Plan.KILL) KILL_AGGRESSION else FARM_AGGRESSION
        aggression += ALIGN * (target - aggression)
        var lane = base.laneWeight * when (job) {
            Job.PUSHER, Job.FARMER -> 1.15
            Job.ANCHOR -> 1.1
            Job.FINISHER -> 0.95
            Job.STRIPPER -> 1.0
        }
        if (v.plan == Plan.PUSH) lane *= 1.1
        if (v.lastWave) lane *= 1.15
        val keep = base.keepDefender || v.danger || job == Job.ANCHOR
        return base.copy(
            aggression = aggression.coerceIn(0.1, 0.9),
            laneWeight = lane,
            keepDefender = keep,
        )
    }

    /**
     * G144, the [HContext.killWorth] hook: how much a death of a [victim] hero is worth, from the deciding team's plan.
     * Their death is worth more when their Life is our road; ours more when we cannot afford it, less in the last push
     * when we can and they cannot ("it is a lot easier to win the last push if you can afford to die").
     */
    fun deathScale(ctx: HContext, victim: Team): Double {
        if (!raceOn(ctx)) return 1.0
        val v = view(ctx, ctx.team)
        return if (victim == ctx.team) {
            when {
                v.danger -> 1.0 + RACE
                v.afford && v.lastWave -> 1.0 - RACE
                v.afford -> 1.0 - RACE / 2
                else -> 1.0
            }
        } else {
            if (v.hunt) 1.0 + RACE else 1.0
        }
    }

    /**
     * G145–G146: the item's worth to [seat] by its job and what the team lacks. The finisher wants attack and
     * initiative, the anchor defense, a pusher or farmer movement, range and radius. An item a teammate already
     * carries twice is worth less (do not stack what the team covers); a team that cannot hold the enemy's attacks
     * values defense more.
     */
    fun itemScale(ctx: HContext, seat: String, stat: Stat): Double {
        if (!upgradeOn(ctx)) return 1.0
        val h = ctx.state.hero(seat) ?: return 1.0
        val v = view(ctx, h.team)
        val job = v.jobs[seat] ?: return 1.0
        var s = when (job) {
            Job.FINISHER -> when (stat) {
                Stat.ATTACK -> 1.35
                Stat.INITIATIVE -> 1.3
                Stat.DEFENSE -> 0.85
                else -> 1.0
            }

            Job.STRIPPER -> when (stat) {
                Stat.INITIATIVE -> 1.3
                Stat.ATTACK -> 1.1
                else -> 1.0
            }

            Job.ANCHOR -> when (stat) {
                Stat.DEFENSE -> 1.4
                Stat.ATTACK -> 0.9
                else -> 1.0
            }

            Job.PUSHER, Job.FARMER -> when (stat) {
                Stat.MOVEMENT, Stat.RANGE, Stat.RADIUS -> 1.35
                Stat.ATTACK -> 1.05
                else -> 1.0
            }
        }
        if (stat == Stat.DEFENSE && v.enemyKit.kill > v.kit.durability) s *= 1.15
        val mates = ctx.state.heroes.filter { it.team == h.team && it.id != seat }
        val covered = mates.sumOf { ctx.itemBonus(it.id, stat) }
        if (covered >= 2 && !(job == Job.FINISHER && stat == Stat.ATTACK)) s *= max(0.7, 1.0 - 0.1 * (covered - 1))
        return s
    }

    /**
     * G147: the card to keep (the other becomes the item) by job: the finisher keeps attack and initiative, the anchor
     * defense, a stripper the card that forces a discard, a pusher or farmer range, radius and movement.
     * Coin-equivalents on the scale of [HCard.rating] (about 3–7 a card).
     */
    fun keepBias(ctx: HContext, seat: String, keep: HCard, others: List<HCard>): Double {
        if (!upgradeOn(ctx)) return 0.0
        val job = job(ctx, seat) ?: return 0.0
        val other = others.firstOrNull() ?: return 0.0
        return when (job) {
            Job.FINISHER -> 0.3 * (keep.attack - other.attack) + 0.1 * (keep.initiative - other.initiative)
            Job.STRIPPER -> 0.6 * (strips(keep) - strips(other)) + 0.08 * (keep.initiative - other.initiative)
            Job.ANCHOR -> 0.3 * ((keep.defense ?: 0) - (other.defense ?: 0)) + 0.6 * (blocks(keep) - blocks(other))
            Job.PUSHER, Job.FARMER -> 0.35 * (wide(keep) - wide(other)) + 0.12 * (keep.movement - other.movement)
        }
    }

    /**
     * G145: which colour to upgrade. The hero file's `upgradeOrder` stays the order ([CardRules]); the job adds a
     * little to the colour that does its work: Red (attack) for the finisher, Blue (defense) for the anchor, the colour
     * whose next Tier forces a discard for a stripper, the colour whose next Tier reaches further for a pusher.
     */
    fun upgradeBias(ctx: HContext, seat: String, card: HCard, next: List<HCard>): Double {
        if (!upgradeOn(ctx) || next.isEmpty()) return 0.0
        val job = job(ctx, seat) ?: return 0.0
        return when (job) {
            Job.FINISHER -> if (card.color == CardColor.RED) 0.5 else 0.0
            Job.ANCHOR -> if (card.color == CardColor.BLUE) 0.4 else 0.0
            Job.STRIPPER -> if (next.any { strips(it) > 0 }) 0.5 else 0.0
            Job.PUSHER, Job.FARMER -> 0.25 * next.maxOf { wide(it) }
        }
    }

    /**
     * G105 (from 7, with `plan.upgrade`): an initiative item breaks ties. Each pair of one of our attack cards and an
     * enemy attack card in its public pool at the same effective initiative (a cross-team tie goes to the coin, RB p.7)
     * is worth [TIE_BREAK] times the pool weight, capped at [TIE_CAP].
     */
    fun initiativeEdge(ctx: HContext, seat: String): Double {
        if (!upgradeOn(ctx) || !ctx.profile.initiativeReading) return 0.0
        val h = ctx.state.hero(seat) ?: return 0.0
        val ours = h.cards.filter { it.zone in ROUND_ZONES }.map { ctx.card(it.id) }.filter { it.attackCard }
        if (ours.isEmpty()) return 0.0
        val mine = ctx.itemBonus(seat, Stat.INITIATIVE)
        var ties = 0.0
        for (e in ctx.state.heroes) {
            if (e.team == h.team) continue
            val theirs = ctx.itemBonus(e.id, Stat.INITIATIVE)
            for (p in ctx.possibleCards(e.id)) {
                if (!p.card.attackCard) continue
                val init = p.card.initiative + theirs
                ties += p.weight * ours.count { it.initiative + mine == init }
            }
        }
        return min(TIE_CAP, TIE_BREAK * ties)
    }

    /**
     * G148, the one line in [HEval]: economy terms the level term misses, Red's view. A level that is due this round is
     * certain (levelling is mandatory, coins are never lost, RB p.15), the level that reaches 5 adds the first Tier III
     * (the designer's "true bump in power", BGG 3472633), and a hero that will not level collects the Pity coin
     * (RB p.8).
     */
    fun evalTerm(ctx: HContext): Double {
        if (!econOn(ctx)) return 0.0
        val state = ctx.state
        if (state.step.ordinal > Step.LEVEL_UP.ordinal) return 0.0
        var v = 0.0
        for (h in state.heroes) {
            val sign = if (h.team == Team.RED) 1.0 else -1.0
            v += sign * economy(h)
        }
        return v
    }

    /** The correction for one hero on top of HEval's level term (which gives a due level +1.5). */
    internal fun economy(h: HeroState): Double {
        var v = if (h.level >= TIER_III_LEVEL) TIER_III else 0.0
        var level = h.level
        var coins = h.coins
        var due = 0.0
        while (level < MAX_LEVEL && coins >= level) {
            coins -= level
            level++
            due += LEVEL + if (level == TIER_III_LEVEL) TIER_III else 0.0
        }
        v += if (due > 0.0) DUE * due - HEVAL_DUE else if (h.levelledThisRound) 0.0 else PITY
        return v
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private fun strips(c: HCard): Int = if (c.forcesDiscard || c.discardOrDefeat) 1 else 0

    private fun blocks(c: HCard): Int = if (c.block) 1 else 0

    /** Reach of a card for wave clear: range when ranged, radius, a pre-move. */
    private fun wide(c: HCard): Double = (if (c.ranged) c.range.toDouble() else 0.0) + c.radius + 0.5 * c.premove

    private fun roleBonus(role: HeroRole, want: HeroRole, bonus: Double) = if (role == want) bonus else 0.0

    private fun norm(x: Double, lo: Double, hi: Double): Double = ((x - lo) / (hi - lo)).coerceIn(0.0, 1.0)

    private fun List<Double>.average0(): Double = if (isEmpty()) 0.0 else sum() / size


    private fun sum(kits: List<Kit>): Kit = Kit(
        kits.sumOf { it.kill },
        kits.sumOf { it.clear },
        kits.sumOf { it.durability },
        kits.sumOf { it.mobility },
        kits.sumOf { it.setup },
    )

    private val ZERO = Kit(0.0, 0.0, 0.0, 0.0, 0.0)
    private val KIT_COLOURS = setOf(CardColor.RED, CardColor.GREEN, CardColor.BLUE, CardColor.GOLD, CardColor.SILVER)

    /**
     * Kits by hero, with the style they were made from: the catalog deck is fixed, so hero and style fully determine
     * the value. Two entries a hero at most (playbooks on, and [HeroStyle.DEFAULT] below strength 5).
     */
    private val kits = concurrentMap<String, Pair<HeroStyle, Kit>>()

    const val MASTER: String = "plan"
    const val STYLE_FROM: Int = 7
    const val RACE_FROM: Int = 7
    const val UPGRADE_FROM: Int = 5
    const val ECON_FROM: Int = 5
    const val SIZE: Int = 2

    private const val KILL_AGGRESSION = 0.65
    private const val FARM_AGGRESSION = 0.45

    /** Share of the way each hero's aggression moves toward the team's target (G142). */
    private const val ALIGN = 0.3

    /** G144: the most a plan moves the worth of a death. */
    private const val RACE = 0.15

    private const val LEVEL = 3.0
    private const val TIER_III = 2.0
    private const val TIER_III_LEVEL = 5
    private const val MAX_LEVEL = 8

    /** A due level is certain but its power arrives next round; HEval already gives it [HEVAL_DUE]. */
    private const val DUE = 0.85
    private const val HEVAL_DUE = 1.5
    private const val PITY = 1.0

    private const val TIE_BREAK = 0.4
    private const val TIE_CAP = 1.2
    private val ROUND_ZONES = setOf(Zone.HAND, Zone.PLAYED, Zone.RESOLVED, Zone.DISCARD)
}

/** Diagnostics (tests): the plan facts of [heroId]'s team at this decision, as short labels. */
internal fun planFacts(ctx: HContext, heroId: String): List<String> {
    val h = ctx.state.hero(heroId) ?: return emptyList()
    val v = TeamStrategy.view(ctx, h.team)
    val out = mutableListOf("plan.${v.plan}", "job.${v.jobs[heroId]}")
    if (v.hunt) out += "hunt"
    if (v.afford) out += "afford"
    if (v.danger) out += "danger"
    if (v.lastWave) out += "lastWave"
    out += "d${v.ours}-${v.theirs}"
    return out
}
