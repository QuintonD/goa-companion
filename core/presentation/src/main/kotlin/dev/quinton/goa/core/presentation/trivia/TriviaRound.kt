package dev.quinton.goa.core.presentation.trivia

import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaEngine
import dev.quinton.goa.core.domain.trivia.TriviaFact
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.core.domain.trivia.TriviaMastery
import dev.quinton.goa.core.domain.trivia.TriviaQuestion
import dev.quinton.goa.core.domain.trivia.TriviaResult
import dev.quinton.goa.core.domain.trivia.TriviaSession
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import kotlin.random.Random

/** The question on screen, with what the answer reveals. */
sealed interface QuizQuestionUi {
    /** "[stat]?" for [card]; [value] is the printed number ([plus] = printed `+`). */
    data class Number(
        val card: TriviaCardUi,
        val stat: TriviaStat,
        val value: Int,
        val plus: Boolean,
        val choices: List<Int>,
    ) : QuizQuestionUi

    /** "Which has the higher initiative?" */
    data class Duel(
        val first: TriviaCardUi,
        val second: TriviaCardUi,
        val firstValue: Int,
        val secondValue: Int,
        val right: DuelPick,
    ) : QuizQuestionUi
}

/** A wrong answer in the round summary: "[title]: [yours] → [right]". */
data class MissUi(
    val card: TriviaCardUi,
    /** "Initiative", or "Higher initiative" for a duel. */
    val title: String,
    val yours: String,
    val right: String,
    /** The other card of a missed duel. */
    val other: TriviaCardUi? = null,
)

/** How an answer button looks once the question is answered. Never colour alone: icons too. */
enum class Mark { OPEN, RIGHT, WRONG, OTHER }

/** A number choice once [answer] is given: the right value, the wrong pick, or another (open before). */
fun markFor(question: QuizQuestionUi.Number, answer: TriviaAnswer.Number?, value: Int): Mark = when {
    answer == null -> Mark.OPEN
    value == question.value -> Mark.RIGHT
    value == answer.value -> Mark.WRONG
    else -> Mark.OTHER
}

/** A duel choice once [answer] is given. */
fun markFor(question: QuizQuestionUi.Duel, answer: TriviaAnswer.Duel?, pick: DuelPick): Mark = when {
    answer == null -> Mark.OPEN
    pick == question.right -> Mark.RIGHT
    pick == answer.pick -> Mark.WRONG
    else -> Mark.OTHER
}

sealed interface TriviaQuizUiState {
    data object Loading : TriviaQuizUiState

    /** Nothing to ask: the setup's cards print none of the chosen numbers. */
    data object Empty : TriviaQuizUiState

    data class Playing(
        /** 1-based number of the question on screen. */
        val number: Int,
        /** Questions in the round, or null when it runs until stopped. */
        val length: Int?,
        val correctCount: Int,
        val streak: Int,
        val question: QuizQuestionUi,
        /** The answer given, or null while the question is open. */
        val answer: TriviaAnswer?,
        val correct: Boolean?,
        /** "Right! …" / "Not quite. Mad Dash is initiative 7.", once answered. */
        val feedback: String?,
        /** Next ends the round (shows "See results"). */
        val lastQuestion: Boolean,
        val hapticsEnabled: Boolean,
    ) : TriviaQuizUiState {
        val answered: Boolean get() = answer != null

        /** At least one answer given this round: leaving asks first (the round's results would go). */
        val started: Boolean get() = answered || number > 1

        /** "Question 3 of 10", or "Question 3" for an endless round. */
        val title: String get() = if (length != null) "Question $number of $length" else "Question $number"

        /** "4 right · 3 in a row". */
        val scoreLine: String
            get() = buildString {
                append("$correctCount right")
                if (streak >= 2) append(" · $streak in a row")
            }

        /** Questions answered so far (for the progress bar). */
        val done: Int get() = number - if (answered) 0 else 1

        /** "See results" on the last question, else "Next". */
        val nextLabel: String get() = if (lastQuestion) "See results" else "Next"

        /** How a number choice looks: open, the right one, the wrong pick, or another. */
        fun mark(value: Int): Mark {
            val q = question as? QuizQuestionUi.Number ?: return Mark.OPEN
            return markFor(q, answer as? TriviaAnswer.Number, value)
        }

        /** How a duel choice looks. */
        fun mark(pick: DuelPick): Mark {
            val q = question as? QuizQuestionUi.Duel ?: return Mark.OPEN
            return markFor(q, answer as? TriviaAnswer.Duel, pick)
        }
    }

    data class Summary(
        val correct: Int,
        val asked: Int,
        val bestStreak: Int,
        /** Numbers that reached "learned" this round. */
        val learned: Int,
        val misses: List<MissUi>,
    ) : TriviaQuizUiState {
        val canPractise: Boolean get() = misses.isNotEmpty()

        /** "7 of 10 right", or "No questions answered". */
        val title: String get() = if (asked == 0) "No questions answered" else "$correct of $asked right"

        val subtitle: String
            get() = when {
                asked == 0 -> "End a round after answering to see how it went."
                misses.isEmpty() -> "Every answer right."
                else -> "Missed numbers come back sooner next time."
            }

        /** "number learned" / "numbers learned" under the "+N". */
        val learnedLabel: String get() = if (learned == 1) "number learned" else "numbers learned"
    }
}

/** "Leave this round?" — the confirmation when leaving after an answer. */
object LeaveRound {
    const val TITLE = "Leave this round?"
    const val MESSAGE = "Your answers are saved. The round's results won't be."
    const val SEE_RESULTS = "See results"
    const val LEAVE = "Leave"
}

/** A "Practise the missed ones" round: its setup and exactly the numbers missed. */
data class TriviaPractice(val config: TriviaConfig, val factKeys: List<String>)

/**
 * One quiz round's screen logic over [TriviaEngine]: the fact pool for a setup, each answer's
 * progress updates, the question and summary as shown, and what "Practise the missed ones" asks.
 * The phone's ViewModel and the hub hold one each; it is not thread-safe (callers serialise).
 */
class TriviaRound private constructor(
    /** The setup of this round (a practice round's own, reduced setup). */
    val config: TriviaConfig,
    /** The numbers a practice round asks, or empty for a round over [config]'s heroes. */
    val factKeys: List<String>,
    private val cards: Map<String, TriviaCardUi>,
    private val hapticsEnabled: Boolean,
    private val random: Random,
    first: TriviaSession,
) {
    var session: TriviaSession = first
        private set

    /** Progress when the round began: what "learned this round" is measured against. */
    private var startProgress: Map<String, FactProgress> = first.progress

    /** What the screen shows now. */
    var state: TriviaQuizUiState = render(first)
        private set

    /**
     * Answers the question on screen. Returns the progress to store (possibly empty), or null when
     * the answer was ignored (already answered, or the round is over).
     */
    fun answer(answer: TriviaAnswer, now: Long): Map<String, FactProgress>? {
        val outcome = TriviaEngine.answer(session, answer, now)
        if (outcome.session === session) return null
        publish(outcome.session)
        return outcome.updates
    }

    /** The next question (or the summary after the last). */
    fun next() {
        publish(TriviaEngine.next(session, random))
    }

    /** Ends the round now and shows the summary. */
    fun stop() {
        if (session.finished) return
        publish(TriviaEngine.stop(session))
    }

    /** A new round with the same setup and pool, from the progress as it is now. */
    fun playAgain() {
        begin(TriviaEngine.start(session.pool, session.ranges, session.progress, config, random))
    }

    /**
     * The round of only the numbers missed in this one: no duels, each number about twice
     * ([TriviaConfig.forPractice]); null when nothing was missed.
     */
    fun practice(): TriviaPractice? {
        val missed = session.missedFacts
        if (missed.isEmpty()) return null
        return TriviaPractice(config.forPractice(missed.size), missed.map { it.key })
    }

    private fun begin(next: TriviaSession) {
        startProgress = next.progress
        publish(next)
    }

    private fun publish(next: TriviaSession) {
        session = next
        state = render(next)
    }

    private fun render(session: TriviaSession): TriviaQuizUiState = when {
        session.pool.isEmpty() -> TriviaQuizUiState.Empty
        session.finished -> summary(session)
        else -> playing(session)
    }

    private fun playing(session: TriviaSession): TriviaQuizUiState.Playing {
        val question = requireNotNull(session.current)
        val result = session.results.lastOrNull()?.takeIf { session.answered }
        return TriviaQuizUiState.Playing(
            number = session.results.size + if (session.answered) 0 else 1,
            length = session.length,
            correctCount = session.correctCount,
            streak = session.streak,
            question = questionUi(question),
            answer = session.answer,
            correct = result?.correct,
            feedback = result?.let { feedback(it) },
            lastQuestion = session.length?.let { session.results.size >= it } ?: false,
            hapticsEnabled = hapticsEnabled,
        )
    }

    private fun summary(session: TriviaSession): TriviaQuizUiState.Summary {
        val learned = session.results.mapNotNull { (it.question as? TriviaQuestion.CardNumber)?.fact }
            .distinctBy { it.key }
            .count { fact ->
                TriviaMastery.isMastered(fact, session.progress[fact.key]) &&
                    !TriviaMastery.isMastered(fact, startProgress[fact.key])
            }
        return TriviaQuizUiState.Summary(
            correct = session.correctCount,
            asked = session.results.size,
            bestStreak = bestStreak(session.results),
            learned = learned,
            misses = session.results.filterNot { it.correct }.map(::miss),
        )
    }

    private fun card(fact: TriviaFact): TriviaCardUi = cards[fact.cardId] ?: TriviaCardUi(
        heroId = fact.heroId,
        heroName = "Unknown hero",
        portrait = null,
        cardId = fact.cardId,
        name = fact.cardId,
        color = CardColor.OTHER,
        label = "",
        primary = null,
        image = null,
        heroVerified = false,
    )

    private fun questionUi(question: TriviaQuestion): QuizQuestionUi = when (question) {
        is TriviaQuestion.CardNumber -> QuizQuestionUi.Number(
            card = card(question.fact),
            stat = question.fact.stat,
            value = question.fact.value,
            plus = question.fact.plus,
            choices = question.choices,
        )

        is TriviaQuestion.HigherInitiative -> QuizQuestionUi.Duel(
            first = card(question.first),
            second = card(question.second),
            firstValue = question.first.value,
            secondValue = question.second.value,
            right = TriviaEngine.duelAnswer(question),
        )
    }

    private fun feedback(result: TriviaResult): String {
        val fact = when (val q = result.question) {
            is TriviaQuestion.CardNumber -> statement(q.fact)
            is TriviaQuestion.HigherInitiative -> duelStatement(q)
        }
        return if (result.correct) "Right! $fact" else "Not quite. $fact"
    }

    /** "Mad Dash is initiative 7." / "Mad Dash has Attack 4+." */
    private fun statement(fact: TriviaFact): String {
        val name = card(fact).name
        val value = TriviaText.value(fact.value, fact.plus)
        return if (fact.stat == TriviaStat.INITIATIVE) {
            "$name is initiative $value."
        } else {
            "$name has ${TriviaText.stat(fact.stat)} $value."
        }
    }

    private fun duelStatement(q: TriviaQuestion.HigherInitiative): String {
        val a = card(q.first).name
        val b = card(q.second).name
        return when (TriviaEngine.duelAnswer(q)) {
            DuelPick.FIRST -> "$a (${q.first.value}) is higher than $b (${q.second.value})."
            DuelPick.SECOND -> "$b (${q.second.value}) is higher than $a (${q.first.value})."
            DuelPick.TIE -> "Both are initiative ${q.first.value}."
        }
    }

    private fun miss(result: TriviaResult): MissUi = when (val q = result.question) {
        is TriviaQuestion.CardNumber -> MissUi(
            card = card(q.fact),
            title = TriviaText.stat(q.fact.stat),
            yours = (result.answer as? TriviaAnswer.Number)?.value?.let { TriviaText.value(it, q.fact.plus) } ?: "–",
            right = TriviaText.value(q.fact.value, q.fact.plus),
        )

        is TriviaQuestion.HigherInitiative -> {
            val first = card(q.first)
            val second = card(q.second)
            fun pick(p: DuelPick?) = when (p) {
                DuelPick.FIRST -> first.name
                DuelPick.SECOND -> second.name
                DuelPick.TIE -> "Same initiative"
                null -> "–"
            }
            MissUi(
                card = first,
                other = second,
                title = "Higher initiative",
                yours = pick((result.answer as? TriviaAnswer.Duel)?.pick),
                right = "${pick(TriviaEngine.duelAnswer(q))} (${q.first.value} vs ${q.second.value})",
            )
        }
    }

    private fun bestStreak(results: List<TriviaResult>): Int {
        var best = 0
        var run = 0
        results.forEach { r ->
            run = if (r.correct) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }

    companion object {
        /**
         * Starts a round over [config] (or, with [factKeys], exactly those numbers wherever they are:
         * "Practise the missed ones"). [ownedProducts] scopes "all heroes" (empty = every hero);
         * [progress] is the stored mastery.
         */
        @Suppress("LongParameterList")
        fun start(
            catalog: Catalog,
            config: TriviaConfig,
            factKeys: List<String>,
            ownedProducts: Set<String>,
            progress: Map<String, FactProgress>,
            hapticsEnabled: Boolean = true,
            random: Random = Random.Default,
        ): TriviaRound {
            val cards = catalog.heroes.flatMap { hero -> hero.cards.map { TriviaCardUi.of(hero, it) } }
                .associateBy { it.cardId }
            val pool = pool(catalog, config, factKeys, ownedProducts)
            val ranges = TriviaFacts.valueRange(catalog)
            val session = TriviaEngine.start(pool, ranges, progress, config, random)
            return TriviaRound(config, factKeys, cards, hapticsEnabled, random, session)
        }

        /** The facts a round asks: the practice keys, else the setup's heroes (or the owned ones). */
        fun pool(
            catalog: Catalog,
            config: TriviaConfig,
            factKeys: List<String>,
            ownedProducts: Set<String>,
        ): List<TriviaFact> {
            if (factKeys.isNotEmpty()) {
                // "Practise the missed ones": exactly those numbers, wherever they are.
                val keys = factKeys.toSet()
                return TriviaFacts.of(catalog, catalog.heroes.map { it.id }).filter { it.key in keys }
            }
            val heroIds = config.heroIds.filter { catalog.hero(it) != null }.ifEmpty {
                catalog.heroes.filter { ownedProducts.isEmpty() || it.product in ownedProducts }.map { it.id }
            }
            return TriviaFacts.of(catalog, heroIds, config.stats, config.groups)
        }
    }
}
