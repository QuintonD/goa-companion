package dev.quinton.goa.core.domain.trivia

import kotlin.math.abs
import kotlin.random.Random
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a card-number question is answered. */
@Serializable
enum class AnswerStyle {
    /** Four nearby numbers to pick from. */
    @SerialName("choices")
    CHOICES,

    /** Every number the stat takes on any card (1–13 for Initiative): recall, not recognition. */
    @SerialName("recall")
    RECALL,
}

/** A quiz the player set up. Hero scope is resolved by the caller into the fact pool. */
@Serializable
data class TriviaConfig(
    /** Heroes to quiz; empty = every hero in the player's owned products. */
    val heroIds: List<String> = emptyList(),
    val stats: Set<TriviaStat> = TriviaStat.entries.toSet(),
    val groups: Set<CardGroup> = CardGroup.entries.toSet(),
    val answerStyle: AnswerStyle = AnswerStyle.CHOICES,
    /** Questions per round; [ENDLESS] = until the player stops. */
    val length: Int = DEFAULT_LENGTH,
    /** Mix in "which card has the higher initiative?" questions (needs Initiative in [stats]). */
    val duels: Boolean = true,
) {
    /**
     * The setup of a "Practise the missed ones" round on [missedFacts] numbers: no duels, and two
     * questions per number, capped by this round's length; never endless.
     */
    fun forPractice(missedFacts: Int): TriviaConfig {
        val twice = PRACTICE_QUESTIONS_PER_FACT * missedFacts.coerceAtLeast(1)
        return copy(duels = false, length = if (length == ENDLESS) twice else minOf(twice, length))
    }

    companion object {
        const val ENDLESS = 0
        const val DEFAULT_LENGTH = 10
        val LENGTHS = listOf(10, 20, ENDLESS)
        const val PRACTICE_QUESTIONS_PER_FACT = 2
    }
}

/** Mastery of one fact (Leitner box), stored per fact key. [value]/[plus] = what was learned. */
@Serializable
data class FactProgress(
    val value: Int,
    val plus: Boolean = false,
    val box: Int = 0,
    val seen: Int = 0,
    val correct: Int = 0,
    val lastSeenAt: Long = 0,
)

data class MasterySummary(val total: Int, val mastered: Int, val learning: Int) {
    val unseen: Int get() = total - mastered - learning
}

/**
 * Leitner boxes: a right answer moves a fact up one box (max [MAX_BOX]), a wrong one back to 0.
 * A fact is mastered from [MASTERED_BOX] (three right in a row). Progress recorded for a different
 * printed value (the catalog was corrected) no longer counts: the fact is new again. A right
 * answer recorded with `promote = false` (asked again too soon, [TriviaEngine.MIN_SPACING]) counts
 * as seen and correct but leaves the box where it is.
 */
object TriviaMastery {
    const val MASTERED_BOX = 3
    const val MAX_BOX = 5

    fun current(fact: TriviaFact, progress: FactProgress?): FactProgress? =
        progress?.takeIf { it.value == fact.value && it.plus == fact.plus }

    fun box(fact: TriviaFact, progress: FactProgress?): Int = current(fact, progress)?.box ?: 0

    fun isMastered(fact: TriviaFact, progress: FactProgress?): Boolean = box(fact, progress) >= MASTERED_BOX

    fun record(
        fact: TriviaFact,
        previous: FactProgress?,
        correct: Boolean,
        now: Long,
        promote: Boolean = true,
    ): FactProgress {
        val base = current(fact, previous) ?: FactProgress(value = fact.value, plus = fact.plus)
        return base.copy(
            box = when {
                !correct -> 0
                promote -> (base.box + 1).coerceAtMost(MAX_BOX)
                else -> base.box
            },
            seen = base.seen + 1,
            correct = base.correct + if (correct) 1 else 0,
            lastSeenAt = now,
        )
    }

    fun summary(facts: Collection<TriviaFact>, progress: Map<String, FactProgress>): MasterySummary {
        var mastered = 0
        var learning = 0
        facts.forEach { fact ->
            val p = current(fact, progress[fact.key])
            when {
                p == null -> Unit
                p.box >= MASTERED_BOX -> mastered++
                else -> learning++
            }
        }
        return MasterySummary(facts.size, mastered, learning)
    }
}

sealed interface TriviaQuestion {
    /** "What is the [fact.stat] of this card?" — answered with one of [choices] (ascending). */
    data class CardNumber(val fact: TriviaFact, val choices: List<Int>) : TriviaQuestion

    /** "Which card has the higher initiative?" — [first] or [second], or a tie. */
    data class HigherInitiative(val first: TriviaFact, val second: TriviaFact) : TriviaQuestion

    /** Facts shown in the answer. */
    val facts: List<TriviaFact>
        get() = when (this) {
            is CardNumber -> listOf(fact)
            is HigherInitiative -> listOf(first, second)
        }
}

enum class DuelPick { FIRST, SECOND, TIE }

sealed interface TriviaAnswer {
    data class Number(val value: Int) : TriviaAnswer

    data class Duel(val pick: DuelPick) : TriviaAnswer
}

data class TriviaResult(val question: TriviaQuestion, val answer: TriviaAnswer, val correct: Boolean)

/** A missed fact asked again once [dueAfter] questions have been answered this round. */
data class TriviaRetry(val fact: TriviaFact, val dueAfter: Int)

/**
 * One round of trivia: the pool it asks from, the question on screen ([current]; null once the
 * round is over), and the answer given to it ([answer], null until answered).
 */
data class TriviaSession(
    val config: TriviaConfig,
    val pool: List<TriviaFact>,
    val ranges: Map<TriviaStat, List<Int>>,
    val progress: Map<String, FactProgress>,
    val current: TriviaQuestion?,
    val answer: TriviaAnswer? = null,
    val results: List<TriviaResult> = emptyList(),
    val retries: List<TriviaRetry> = emptyList(),
) {
    val finished: Boolean get() = current == null
    val answered: Boolean get() = answer != null
    val correctCount: Int get() = results.count { it.correct }

    /** The round's fixed length, or null when it runs until stopped. */
    val length: Int? get() = config.length.takeIf { it != TriviaConfig.ENDLESS }

    /** Right answers in a row, counting back from the latest. */
    val streak: Int get() = results.asReversed().takeWhile { it.correct }.size

    /** Facts behind each wrong answer (both cards of a missed duel), first miss first, no repeats. */
    val missedFacts: List<TriviaFact>
        get() = results.filterNot { it.correct }.flatMap { it.question.facts }.distinctBy { it.key }
}

/** The answer to [TriviaEngine.answer]: the new session plus the progress to store. */
data class TriviaOutcome(val session: TriviaSession, val correct: Boolean, val updates: Map<String, FactProgress>)

/**
 * Pure quiz engine: picks questions weighted towards what the player doesn't know yet, re-asks a
 * missed number a few questions later, and scores answers. Randomness is injected for tests.
 */
object TriviaEngine {
    /** Share of questions that are initiative duels, when enabled and possible. */
    const val DUEL_SHARE = 0.2

    /** A missed fact comes back after this many other questions. */
    const val RETRY_GAP = 3

    /** Cards not asked again within this many questions (when the pool allows). */
    const val RECENT_CARDS = 3

    /**
     * A right answer moves a fact up a box only on its first card-number question of the round, or
     * once at least this many other questions were answered since it was last asked that way: an
     * answer still in short-term memory doesn't show it is learned.
     */
    const val MIN_SPACING = 4

    /** Duel partners are looked for within this initiative distance first (ties included). */
    const val DUEL_CLOSE = 2

    const val CHOICE_COUNT = 4

    fun start(
        pool: List<TriviaFact>,
        ranges: Map<TriviaStat, List<Int>>,
        progress: Map<String, FactProgress>,
        config: TriviaConfig,
        random: Random = Random.Default,
    ): TriviaSession {
        val empty = TriviaSession(config, pool.distinctBy { it.key }, ranges, progress, current = null)
        return if (empty.pool.isEmpty()) empty else empty.ask(pick(empty, random))
    }

    /** Scores [answer] to the current question. Ignored (no updates) if already answered or finished. */
    fun answer(session: TriviaSession, answer: TriviaAnswer, now: Long): TriviaOutcome {
        val question = session.current
        if (question == null || session.answered) return TriviaOutcome(session, correct = false, updates = emptyMap())
        val correct = isCorrect(question, answer)
        // Only a card-number question tests a number exactly; a duel can be right by a rough sense of order.
        val updates = when (question) {
            is TriviaQuestion.CardNumber -> mapOf(
                question.fact.key to TriviaMastery.record(
                    question.fact,
                    session.progress[question.fact.key],
                    correct,
                    now,
                    promote = spacedOut(session, question.fact),
                ),
            )

            is TriviaQuestion.HigherInitiative -> emptyMap()
        }
        val asked = session.results.size + 1
        val retries = if (correct) {
            session.retries
        } else {
            session.retries + question.facts.filter { f -> session.retries.none { it.fact.key == f.key } }
                .map { TriviaRetry(it, dueAfter = asked + RETRY_GAP) }
        }
        val next = session.copy(
            answer = answer,
            results = session.results + TriviaResult(question, answer, correct),
            progress = session.progress + updates,
            retries = retries,
        )
        return TriviaOutcome(next, correct, updates)
    }

    /** Moves on after an answer: the next question, or the end of the round. */
    fun next(session: TriviaSession, random: Random = Random.Default): TriviaSession {
        if (session.finished || !session.answered) return session
        val done = session.length?.let { session.results.size >= it } ?: false
        return if (done) {
            session.copy(current = null, answer = null)
        } else {
            session.ask(pick(session, random))
        }
    }

    /**
     * True when [fact] hasn't been asked as a card-number question yet this round, or at least
     * [MIN_SPACING] other questions were answered since it last was.
     */
    fun spacedOut(session: TriviaSession, fact: TriviaFact): Boolean {
        val last = session.results.indexOfLast { (it.question as? TriviaQuestion.CardNumber)?.fact?.key == fact.key }
        return last < 0 || session.results.size - 1 - last >= MIN_SPACING
    }

    /**
     * Shows [question]. Only a card-number question uses up a pending retry of its fact: a duel
     * touching the same card tests a rough sense of order, not the number.
     */
    private fun TriviaSession.ask(question: TriviaQuestion): TriviaSession {
        val key = (question as? TriviaQuestion.CardNumber)?.fact?.key
        return copy(current = question, answer = null, retries = retries.filterNot { it.fact.key == key })
    }

    /** Ends the round now (an endless round, or stopping early). */
    fun stop(session: TriviaSession): TriviaSession = session.copy(current = null, answer = null)

    fun isCorrect(question: TriviaQuestion, answer: TriviaAnswer): Boolean = when (question) {
        is TriviaQuestion.CardNumber -> (answer as? TriviaAnswer.Number)?.value == question.fact.value
        is TriviaQuestion.HigherInitiative -> (answer as? TriviaAnswer.Duel)?.pick == duelAnswer(question)
    }

    fun duelAnswer(question: TriviaQuestion.HigherInitiative): DuelPick = when {
        question.first.value > question.second.value -> DuelPick.FIRST
        question.first.value < question.second.value -> DuelPick.SECOND
        else -> DuelPick.TIE
    }

    private fun pick(session: TriviaSession, random: Random): TriviaQuestion {
        val asked = session.results.size
        val last = session.lastCards()
        val elsewhere = session.pool.any { it.cardId !in last }
        // A due retry on the card just asked waits a question when the pool has another card.
        val due = session.retries.firstOrNull { it.dueAfter <= asked && (it.fact.cardId !in last || !elsewhere) }
        if (due != null) return numberQuestion(session, due.fact, random)
        // A duel needs two cards besides the ones just asked, so it never repeats the last card.
        val initiative = session.pool.filter { it.stat == TriviaStat.INITIATIVE && it.cardId !in last }
        val canDuel = session.config.duels && initiative.map { it.cardId }.distinct().size >= 2
        return if (canDuel && random.nextDouble() < DUEL_SHARE) {
            duel(session, initiative, random)
        } else {
            numberQuestion(session, weighted(session, avoidRecent(session, session.pool), random), random)
        }
    }

    private fun numberQuestion(session: TriviaSession, fact: TriviaFact, random: Random): TriviaQuestion.CardNumber {
        val range = (session.ranges[fact.stat].orEmpty() + fact.value).distinct().sorted()
        val choices = when (session.config.answerStyle) {
            AnswerStyle.RECALL -> range

            AnswerStyle.CHOICES -> {
                // A run of neighbouring values with the answer at a random place in it. (The nearest
                // values alone would keep an inner answer off both ends: a tell.)
                val count = CHOICE_COUNT.coerceAtMost(range.size)
                val at = range.indexOf(fact.value)
                val from = random.nextInt((at - count + 1).coerceAtLeast(0), at.coerceAtMost(range.size - count) + 1)
                range.subList(from, from + count).toList()
            }
        }
        return TriviaQuestion.CardNumber(fact, choices)
    }

    private fun duel(session: TriviaSession, initiative: List<TriviaFact>, random: Random): TriviaQuestion {
        val first = weighted(session, avoidRecent(session, initiative), random)
        val others = avoidRecent(session, initiative.filter { it.cardId != first.cardId })
        val close = others.filter { abs(it.value - first.value) <= DUEL_CLOSE }
        val second = (close.ifEmpty { others }).random(random)
        return if (random.nextBoolean()) {
            TriviaQuestion.HigherInitiative(first, second)
        } else {
            TriviaQuestion.HigherInitiative(second, first)
        }
    }

    /** Cards of the question just answered. */
    private fun TriviaSession.lastCards(): Set<String> =
        results.lastOrNull()?.question?.facts?.map { it.cardId }?.toSet().orEmpty()

    /**
     * [facts] not on the cards of the last [RECENT_CARDS] questions. When that leaves none the
     * window shrinks, so even a small pool never repeats the card just asked if it has another.
     */
    private fun avoidRecent(session: TriviaSession, facts: List<TriviaFact>): List<TriviaFact> {
        for (window in RECENT_CARDS downTo 1) {
            val recent = session.results.takeLast(window).flatMap { r -> r.question.facts.map { it.cardId } }.toSet()
            val fresh = facts.filter { it.cardId !in recent }
            if (fresh.isNotEmpty()) return fresh
        }
        return facts
    }

    /** Weighted towards facts the player missed, hasn't seen, or is still learning. */
    private fun weighted(session: TriviaSession, facts: List<TriviaFact>, random: Random): TriviaFact {
        val weights = facts.map { weight(it, session.progress[it.key]) }
        var roll = random.nextDouble() * weights.sum()
        facts.forEachIndexed { i, fact ->
            roll -= weights[i]
            if (roll < 0) return fact
        }
        return facts.last()
    }

    internal fun weight(fact: TriviaFact, progress: FactProgress?): Double {
        val p = TriviaMastery.current(fact, progress) ?: return WEIGHT_UNSEEN
        return when (p.box) {
            0 -> WEIGHT_MISSED
            1 -> 4.0
            2 -> 2.0
            3 -> 1.0
            4 -> 0.5
            else -> 0.25
        }
    }

    private const val WEIGHT_UNSEEN = 3.0
    private const val WEIGHT_MISSED = 6.0
}
