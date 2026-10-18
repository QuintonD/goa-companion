package dev.quinton.goa.feature.trivia.quiz

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
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
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.feature.trivia.TriviaCardUi
import dev.quinton.goa.feature.trivia.TriviaText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The question on screen, with what the answer reveals. */
@Immutable
sealed interface QuizQuestionUi {
    /** "[stat]?" for [card]; [value] is the printed number ([plus] = printed `+`). */
    @Immutable
    data class Number(
        val card: TriviaCardUi,
        val stat: TriviaStat,
        val value: Int,
        val plus: Boolean,
        val choices: List<Int>,
    ) : QuizQuestionUi

    /** "Which has the higher initiative?" */
    @Immutable
    data class Duel(
        val first: TriviaCardUi,
        val second: TriviaCardUi,
        val firstValue: Int,
        val secondValue: Int,
        val right: DuelPick,
    ) : QuizQuestionUi
}

/** A wrong answer in the round summary: "[title]: [yours] → [right]". */
@Immutable
data class MissUi(
    val card: TriviaCardUi,
    /** "Initiative", or "Higher initiative" for a duel. */
    val title: String,
    val yours: String,
    val right: String,
    /** The other card of a missed duel. */
    val other: TriviaCardUi? = null,
)

sealed interface TriviaQuizUiState {
    data object Loading : TriviaQuizUiState

    /** Nothing to ask: the setup's cards print none of the chosen numbers. */
    data object Empty : TriviaQuizUiState

    @Immutable
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
        /** "Right!" / "Mad Dash is initiative 7", once answered. */
        val feedback: String?,
        /** Next ends the round (shows "See results"). */
        val lastQuestion: Boolean,
        val hapticsEnabled: Boolean,
    ) : TriviaQuizUiState {
        val answered: Boolean get() = answer != null
    }

    @Immutable
    data class Summary(
        val correct: Int,
        val asked: Int,
        val bestStreak: Int,
        /** Numbers that reached "learned" this round. */
        val learned: Int,
        val misses: List<MissUi>,
    ) : TriviaQuizUiState {
        val canPractise: Boolean get() = misses.isNotEmpty()
    }
}

@HiltViewModel(assistedFactory = TriviaQuizViewModel.Factory::class)
class TriviaQuizViewModel @AssistedInject constructor(
    @Assisted private val route: TriviaQuizRoute,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val progressRepository: TriviaProgressRepository,
    private val clock: EditClock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: TriviaQuizRoute): TriviaQuizViewModel
    }

    private val config: TriviaConfig = TriviaText.decode(route.config)

    private val state = MutableStateFlow<TriviaQuizUiState>(TriviaQuizUiState.Loading)
    val uiState: StateFlow<TriviaQuizUiState> = state.asStateFlow()

    private var session: TriviaSession? = null
    private var cards: Map<String, TriviaCardUi> = emptyMap()
    private var settings = AppSettings()

    /** Progress when the round began: what "learned this round" is measured against. */
    private var startProgress: Map<String, FactProgress> = emptyMap()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val catalog = catalogRepository.catalog.catch { emit(Catalog.EMPTY) }.first()
        settings = runCatching { settingsRepository.settings.first() }.getOrDefault(AppSettings())
        val progress = runCatching { progressRepository.progress.first() }.getOrDefault(emptyMap())
        cards = catalog.heroes.flatMap { hero -> hero.cards.map { TriviaCardUi.of(hero, it) } }.associateBy { it.cardId }
        val pool = pool(catalog)
        val ranges = TriviaFacts.valueRange(catalog)
        begin(TriviaEngine.start(pool, ranges, progress, config))
    }

    private fun pool(catalog: Catalog): List<TriviaFact> {
        if (route.factKeys.isNotEmpty()) {
            // "Practise the missed ones": exactly those numbers, wherever they are.
            val keys = route.factKeys.toSet()
            return TriviaFacts.of(catalog, catalog.heroes.map { it.id }).filter { it.key in keys }
        }
        val owned = settings.ownedProducts
        val heroIds = config.heroIds.filter { catalog.hero(it) != null }.ifEmpty {
            catalog.heroes.filter { owned.isEmpty() || it.product in owned }.map { it.id }
        }
        return TriviaFacts.of(catalog, heroIds, config.stats, config.groups)
    }

    private fun begin(next: TriviaSession) {
        startProgress = next.progress
        publish(next)
    }

    fun answer(answer: TriviaAnswer) {
        val current = session ?: return
        val outcome = TriviaEngine.answer(current, answer, clock.now())
        if (outcome.session === current) return
        // Stored right away: leaving mid-round keeps what was learned.
        if (outcome.updates.isNotEmpty()) viewModelScope.launch { progressRepository.record(outcome.updates) }
        publish(outcome.session)
    }

    fun next() {
        val current = session ?: return
        publish(TriviaEngine.next(current))
    }

    /** Ends the round now and shows the summary. */
    fun stop() {
        val current = session ?: return
        if (current.finished) return
        publish(TriviaEngine.stop(current))
    }

    /** A new round with the same setup. */
    fun playAgain() {
        val current = session ?: return
        begin(TriviaEngine.start(current.pool, current.ranges, current.progress, config))
    }

    /** The round of only the numbers missed in this one, without duels; null when nothing was missed. */
    fun practiseRoute(): TriviaQuizRoute? {
        val missed = session?.missedFacts.orEmpty()
        if (missed.isEmpty()) return null
        return TriviaQuizRoute(TriviaText.encode(config.copy(duels = false)), missed.map { it.key })
    }

    /** True when [other] would replay this very round (same setup and facts). */
    fun isSameRound(other: TriviaQuizRoute): Boolean = other == route

    private fun publish(next: TriviaSession) {
        session = next
        state.value = when {
            next.pool.isEmpty() -> TriviaQuizUiState.Empty
            next.finished -> summary(next)
            else -> playing(next)
        }
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
            hapticsEnabled = settings.hapticsEnabled,
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
        return if (result.correct) "Right! $fact" else fact
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
}
