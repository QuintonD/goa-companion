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
    private val playbooks: (String) -> HeroHeuristics = HeroPlaybooks::forHero,
) : AiPlayer {
    val profile: HeuristicProfile = HeuristicProfile.of(strength)

    override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
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
        val pending = det.pending
        if (pending == null || pending.seat != decision.seat || pending.kind != decision.kind) {
            return Fallback.choose(decision)
        }
        val blind = decision.kind == DecisionKind.CHOOSE_SPACE && rng.nextDouble() < profile.blindRate
        val seen = if (profile.cardCounting) seenCards(es, decision.team) else emptyMap()
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
        book.override(ctx, pending)?.takeIf { it in decision.options }?.let { return it }
        val static = DoubleArray(pending.options.size) { i ->
            General.score(ctx, pending, pending.options[i]) + adjust(book, ctx, pending, pending.options[i])
        }
        val looked = look(ctx, pending, static, rng, cancelled)
        val choice = looked ?: pick(pending, pending.options.indices.toList(), static, rng)
        return decision.options.firstOrNull { it == choice } ?: choice
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
        val idxs = lookAt(ctx, d, static)
        if (idxs.isEmpty()) return null
        val got = ArrayList<Int>()
        val values = ArrayList<Double>()
        val book = if (profile.heroPlaybooks) playbooks(d.seat) else DefaultHeroHeuristics
        for (i in idxs) {
            if (cancelled()) break
            got += i
            values += Lookahead.value(ctx, d, d.options[i]) + adjust(book, ctx, d, d.options[i])
        }
        if (got.isEmpty() || values.none { it > Lookahead.WORSE / 2 }) return null
        return pick(d, got, values.toDoubleArray(), rng)
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

            DecisionKind.DEFEND -> if (nullDefense) d.options.size else 0

            // A move is where a static score walks into a death. Strength 7+ checks its best few.
            DecisionKind.CHOOSE_SPACE -> if (look == HeuristicProfile.Look.MOVES) SPACE_CAP else 0

            else -> 0
        }
    }

    private fun pick(d: Decision, idxs: List<Int>, values: DoubleArray, rng: Random): Choice {
        val choices = idxs.map { d.options[it] }
        val critical = d.kind == DecisionKind.DEFEND || d.kind == DecisionKind.REMOVE_MINION ||
            (values.isNotEmpty() && values.max() >= CRITICAL)
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

    companion object {
        private const val CRITICAL = 12.0
        private const val OPAQUE_CAP = 8
        private const val UNIT_CAP = 8
        private const val SPACE_CAP = 4

        /** The same mix as the old search, so a seed names one stream of answers. */
        fun mix(a: Long, b: Long): Long {
            var x = a * -0x61c8864680b583ebL + b
            x = (x xor (x ushr 33)) * -0xae502812aa7333L
            x = (x xor (x ushr 33)) * -0x3b314601e57a13adL
            return x xor (x ushr 33)
        }
    }
}
