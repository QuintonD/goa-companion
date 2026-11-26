package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.ai.Fallback
import dev.quinton.goa.core.game.ai.Picker
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.withoutStackTraces
import dev.quinton.goa.core.model.match.Team
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * The computer player (ADR-085): one determinized view, the general playbook, then the hero's
 * notes. An answer depends on the seed, the decision id and the seat. Any failure answers with
 * [Fallback], which never reads the session.
 *
 * [playbooks] defaults to [HeroPlaybooks]. A test passes another function to sit a hero without
 * its notes (the ablation).
 */
class HeuristicAi(
    strength: Int,
    private val seed: Long,
    /** Measurement knobs ([HeuristicProfile.tune]); empty in play. */
    tune: Map<String, Double> = emptyMap(),
    /** Measurement only (the hybrid search's cheaper playout policies): a change to the strength's profile. */
    profileEdit: (HeuristicProfile) -> HeuristicProfile = { it },
    playbooks: (String) -> HeroHeuristics = HeroPlaybooks::forHero,
) : AiPlayer {
    /** Measurement only: `h10+heroes=0` seats every hero with the general book alone. */
    private val playbooks: (String) -> HeroHeuristics =
        if ((tune["heroes"] ?: 1.0) <= 0.0) { _ -> DefaultHeroHeuristics } else playbooks

    val profile: HeuristicProfile = HeuristicProfile.of(strength).copy(tune = tune).let { p ->
        // Measurement only: a layer switched by name (h10+laneTiming=0).
        p.copy(
            laneTiming = p.on("laneTiming", p.laneTiming),
            twoTurnPlans = p.on("twoTurn", p.twoTurnPlans),
            knowHands = p.on("knowHands", p.knowHands),
            omniscient = p.on("omni", p.omniscient),
            focusFire = p.on("focus", p.focusFire),
            initiativeReading = p.on("initiative", p.initiativeReading),
            cardCounting = p.on("counting", p.cardCounting),
            temperature = p.knob("temp", p.temperature),
        ).let(profileEdit)
    }

    /** Diagnostics only: receives a line per looked-at decision with each option's value. */
    internal var trace: ((String) -> Unit)? = null

    /**
     * Diagnostics only (the regret probe): the scores behind an answer, per stage ("override", "static",
     * "look", "cards") with the options scored and their values. Never changes an answer.
     */
    internal var scored: ((stage: String, options: List<Choice>, values: DoubleArray) -> Unit)? = null

    override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice =
        withoutStackTraces { chooseNow(session, decision, cancelled) }

    private fun chooseNow(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
        if (decision.options.isEmpty()) return Fallback.choose(decision)
        if (decision.options.size == 1) return decision.options.single()
        val es = session as? EngineSession ?: return Fallback.choose(decision)
        return try {
            if (cancelled()) return Fallback.choose(decision)
            answer(es, decision, cancelled)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Fallback.choose(decision)
        }
    }

    private fun answer(es: EngineSession, decision: Decision, cancelled: () -> Boolean): Choice {
        val rng = Random(mix(seed, decision.id * 1_000_003L + decision.seat.hashCode()))
        val det = determinize(es, decision, rng.nextLong())
        val seen = if (profile.cardCounting) seenCards(es, decision.team) else emptyMap()
        return answerOn(det, decision, rng, seen, cancelled)
    }

    /**
     * The book's answer on [det], a copy already determinized for [decision]'s team (the hybrid search's
     * playouts, `ai/hybrid`): no second determinization. [seen] is the public card memory of that team.
     */
    internal fun answerOn(
        det: EngineSession,
        decision: Decision,
        rng: Random,
        seen: Map<String, Set<String>>,
        cancelled: () -> Boolean = { false },
    ): Choice {
        val pending = det.pending
        if (pending == null || pending.seat != decision.seat || pending.kind != decision.kind) {
            return Fallback.choose(decision)
        }
        val blind = decision.kind == DecisionKind.CHOOSE_SPACE && rng.nextDouble() < profile.blindRate
        val ctx = HContext(
            session = det,
            decision = pending,
            team = pending.team,
            profile = profile,
            playbooks = playbooks,
            blindModifiers = blind,
            seen = seen,
        )
        val book = if (profile.heroPlaybooks) playbooks(pending.seat) else DefaultHeroHeuristics
        book.override(ctx, pending)?.takeIf { it in decision.options }?.let {
            scored?.invoke("override", listOf(it), DoubleArray(1))
            return it
        }
        val static = DoubleArray(pending.options.size) { i ->
            General.score(ctx, pending, pending.options[i]) + adjust(book, ctx, pending, pending.options[i])
        }
        scored?.invoke("static", pending.options, static)
        val looked = look(ctx, pending, static, rng, cancelled)
        val choice = looked ?: pick(pending, pending.options.indices.toList(), static, rng, fromLook = false)
        if (choice in decision.options) return choice
        // The sampled view offered something the real question does not (a space that depends on a
        // hidden card): the best static option the real question has, else the fallback.
        val legal = pending.options.indices.filter { pending.options[it] in decision.options }
        return legal.maxByOrNull { static[it] }?.let { pending.options[it] } ?: Fallback.choose(decision)
    }

    private fun adjust(book: HeroHeuristics, ctx: HContext, d: Decision, choice: Choice): Double =
        if (profile.heroPlaybooks) book.adjust(ctx, d, choice) else 0.0

    /** One-ply values for the options that need the engine. Null when static scores are enough. */
    private fun look(
        ctx: HContext,
        d: Decision,
        static: DoubleArray,
        rng: Random,
        cancelled: () -> Boolean,
    ): Choice? {
        if (d.kind == DecisionKind.PLAY_CARD) return cardLook(ctx, d, rng, cancelled)
        val idxs = lookAt(ctx, d, static)
        if (idxs.isEmpty()) return null
        val got = ArrayList<Int>()
        val values = ArrayList<Double>()
        val book = if (profile.heroPlaybooks) playbooks(d.seat) else DefaultHeroHeuristics
        val rest = CardSim.restOn(ctx)
        for (i in idxs) {
            if (cancelled()) break
            got += i
            // The look is the position. [General.commitment] is what that position does not contain.
            val o = d.options[i]
            val ownTurn = ctx.profile.on("turnLook", true) && ctx.state.acting == d.seat
            val turn = when {
                rest -> CardSim.rest(ctx, d, o)
                ownTurn -> CardSim.after(ctx, d, o)
                else -> null
            }
            values += (turn ?: Lookahead.value(ctx, d, o)) +
                General.commitment(ctx, d, d.options[i]) +
                ctx.inLook { adjust(book, ctx, d, d.options[i]) }
        }
        scored?.invoke("look", got.map { d.options[it] }, values.toDoubleArray())
        trace?.invoke(
            "${d.seat} ${d.kind} " + got.indices.joinToString(" ") { "${d.options[got[it]]}=${round1(values[it])}" },
        )
        if (got.isEmpty() || values.none { it > Lookahead.WORSE / 2 }) return null
        return pick(d, got, values.toDoubleArray(), rng, fromLook = true)
    }

    /**
     * The card play from strength 5 ([HeuristicProfile.Look.ACTIONS]): each card's own action through
     * the engine ([CardSim]), plus what that action cannot see. Null (static scores) when a card
     * cannot be simulated, so the values never mix two scales.
     */
    private fun cardLook(ctx: HContext, d: Decision, rng: Random, cancelled: () -> Boolean): Choice? {
        if (ctx.profile.lookahead < HeuristicProfile.Look.ACTIONS || !ctx.profile.on("sim", true)) return null
        if (d.options.any { it !is Choice.Card }) return null
        val book = if (profile.heroPlaybooks) playbooks(d.seat) else DefaultHeroHeuristics
        val values = DoubleArray(d.options.size)
        if (ctx.profile.on("predict", profile.predictCards)) CardSim.cardModel(ctx)
        val samples = ctx.profile.knob("reveal", profile.revealSamples.toDouble()).toInt()
        val sampled = if (samples > 0) {
            CardSim.sampled(ctx, d.options.map { (it as Choice.Card).cardId }, samples)
        } else {
            null
        }
        for ((i, o) in d.options.withIndex()) {
            if (cancelled()) return null
            // Knob `revealMix` (measurement): a share of the one-action look kept beside the sampled turn.
            val mix = if (sampled != null) ctx.profile.knob("revealMix", 1.0) else 0.0
            val one = if (mix < 1.0) CardSim.value(ctx, (o as Choice.Card).cardId) ?: return null else 0.0
            val sim = if (sampled != null) mix * sampled[i] + (1.0 - mix) * one else one
            values[i] = sim + General.commitment(ctx, d, o) + ctx.inLook { adjust(book, ctx, d, o) }
        }
        scored?.invoke("cards", d.options, values)
        trace?.invoke(
            "${d.seat} cards " + d.options.indices.joinToString(" ") { i ->
                "${(d.options[i] as Choice.Card).cardId}=${round1(values[i])}"
            },
        )
        mixed(ctx, d, values, rng)?.let { return it }
        return pick(d, d.options.indices.toList(), values, rng, fromLook = true)
    }

    /**
     * G17 (from strength 7): when the two best cards are within [MIX_GAP], the seed picks between them,
     * so the round's shape is not a script a person can learn. Never when the best card wins the game.
     * Two cards within the gap are, by the book's own numbers, the same play; a lethal threat or a push
     * that one card answers puts it far ahead, outside the gap.
     */
    private fun mixed(ctx: HContext, d: Decision, values: DoubleArray, rng: Random): Choice? {
        if (!ctx.profile.initiativeReading || !ctx.profile.on("mix", true) || values.size < 2) return null
        val order = values.indices.sortedByDescending { values[it] }
        val a = order[0]
        val b = order[1]
        if (values[a] - values[b] > MIX_GAP || values[a] >= WON / 2) return null
        return d.options[if (rng.nextBoolean()) a else b]
    }

    private fun lookAt(ctx: HContext, d: Decision, static: DoubleArray): List<Int> {
        if (ctx.profile.lookahead == HeuristicProfile.Look.OFF) return emptyList()
        val opaque = Lookahead.opaque(ctx.state, d)
        val cap = cap(ctx, d, opaque)
        if (cap <= 0) return emptyList()
        if (d.options.size <= cap) return d.options.indices.toList()
        return static.indices.sortedByDescending { static[it] }.take(cap)
    }

    private fun cap(ctx: HContext, d: Decision, opaque: Boolean): Int {
        val look = ctx.profile.lookahead
        if (opaque) return OPAQUE_CAP
        if (look == HeuristicProfile.Look.OPAQUE) return 0
        val nullDefense = d.kind == DecisionKind.DEFEND && d.context.defensePreview.values.any { it == null }
        return when (d.kind) {
            DecisionKind.CHOOSE_ACTION -> d.options.size

            DecisionKind.CHOOSE_UNIT -> UNIT_CAP

            // G78, G81, G84: the hand that is left, or the priced death, is a position the look can score.
            DecisionKind.DEFEND -> if (nullDefense || ctx.profile.on("defLook", true)) d.options.size else 0

            // A move is where a static score walks into a death. Strength 7+ checks its best few.
            // F1: with the turn played out every square is scored by what happens to it.
            DecisionKind.CHOOSE_SPACE -> when {
                look != HeuristicProfile.Look.MOVES -> 0
                CardSim.restOn(ctx) -> ctx.profile.knob("restSpaces", REST_SPACES.toDouble()).toInt()
                else -> SPACE_CAP
            }

            else -> 0
        }
    }

    private fun pick(d: Decision, idxs: List<Int>, values: DoubleArray, rng: Random, fromLook: Boolean): Choice {
        val choices = idxs.map { d.options[it] }
        // A look returns a position value, often far above [CRITICAL]. Only a won game is critical there.
        val lane = !fromLook && values.isNotEmpty() && values.max() >= CRITICAL
        val won = fromLook && values.isNotEmpty() && values.max() >= 500.0
        val critical = d.kind == DecisionKind.DEFEND || d.kind == DecisionKind.REMOVE_MINION || lane || won
        val temp = if (critical) 0.0 else profile.temperature
        val slip = if (critical) 0.0 else profile.slipRate
        return Picker.pick(choices, values, temp, profile.maxGap, slip, rng)
    }

    private fun determinize(es: EngineSession, d: Decision, sampleSeed: Long): EngineSession {
        val humans = es.setup.seats.filter {
            it.team == d.team && it.controller == Controller.Human && it.heroId != d.seat
        }.map { it.heroId }.toSet()
        val options = Determinizer.Options(
            seen = if (profile.cardCounting) seenCards(es, d.team) else emptyMap(),
            hidePlayedOf = humans,
            knowHands = profile.knowHands,
            omniscient = profile.omniscient,
        )
        return es.determinizeWith(d.team, sampleSeed, options, quiet = true)
    }

    /** Cards each enemy has shown faceup since its last level-up (the public log). */
    private fun seenCards(es: EngineSession, team: Team): Map<String, Set<String>> {
        val enemies = es.setup.seats.filter { it.team != team }.map { it.heroId }.toSet()
        val seen = LinkedHashMap<String, MutableSet<String>>()
        for (e in es.events) {
            when (e) {
                is GameEvent.CardsRevealed -> e.played.forEach { (hero, card) ->
                    if (hero in enemies && card != null) seen.getOrPut(hero) { LinkedHashSet() }.add(card)
                }

                is GameEvent.Defended -> if (e.heroId in enemies && e.cardId != null) {
                    seen.getOrPut(e.heroId) { LinkedHashSet() }.add(e.cardId)
                }

                is GameEvent.ActionChosen -> if (e.heroId in enemies) {
                    seen.getOrPut(e.heroId) { LinkedHashSet() }.add(e.cardId)
                }

                is GameEvent.LevelledUp -> seen.remove(e.heroId)

                else -> Unit
            }
        }
        return seen
    }

    private fun round1(v: Double): Double = kotlin.math.round(v * 10.0) / 10.0

    companion object {
        private const val CRITICAL = 12.0
        private const val MIX_GAP = 2.0
        private const val WON = 1000.0
        private const val OPAQUE_CAP = 8
        private const val UNIT_CAP = 8
        private const val SPACE_CAP = 6
        private const val REST_SPACES = 64

        /** The same mix as the old search, so a seed names one stream of answers. */
        fun mix(a: Long, b: Long): Long {
            var x = a * -0x61c8864680b583ebL + b
            x = (x xor (x ushr 33)) * -0xae502812aa7333L
            x = (x xor (x ushr 33)) * -0x3b314601e57a13adL
            return x xor (x ushr 33)
        }
    }
}
