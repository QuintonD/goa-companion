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
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.domain.trivia.ChallengeStanding
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.LeaderboardPeriod
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaChallenge
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaEngine
import dev.quinton.goa.core.domain.trivia.TriviaFact
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.core.domain.trivia.TriviaLeaderboard
import dev.quinton.goa.core.domain.trivia.TriviaMastery
import dev.quinton.goa.core.domain.trivia.TriviaQuestion
import dev.quinton.goa.core.domain.trivia.TriviaResult
import dev.quinton.goa.core.domain.trivia.TriviaRounds
import dev.quinton.goa.core.domain.trivia.TriviaSession
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.feature.trivia.PlayingAsState
import dev.quinton.goa.feature.trivia.TriviaCardUi
import dev.quinton.goa.feature.trivia.TriviaText
import dev.quinton.goa.feature.trivia.playingAs
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        /** "Right! …" / "Not quite. Mad Dash is initiative 7.", once answered. */
        val feedback: String?,
        /** Next ends the round (shows "See results"). */
        val lastQuestion: Boolean,
        val hapticsEnabled: Boolean,
        /** The fixed Challenge (ADR-032). */
        val challenge: Boolean = false,
    ) : TriviaQuizUiState {
        val answered: Boolean get() = answer != null

        /** At least one answer given this round: leaving asks first (the round's results would go). */
        val started: Boolean get() = answered || number > 1
    }

    @Immutable
    data class Summary(
        val correct: Int,
        val asked: Int,
        val bestStreak: Int,
        /** Numbers that reached "learned" this round. */
        val learned: Int,
        val misses: List<MissUi>,
        /** The round's learning progress was saved (played as this phone's "You", or no "You" set). */
        val progressSaved: Boolean = true,
        /** Who played; null = Guest (not recorded). */
        val playerName: String? = null,
        /** Set for a Challenge; null for a practice round. */
        val challenge: ChallengeResultUi? = null,
    ) : TriviaQuizUiState {
        val canPractise: Boolean get() = misses.isNotEmpty()
    }
}

/** How a Challenge went, for its summary (ADR-032). */
@Immutable
data class ChallengeResultUi(
    /** Every question answered: on the board (when played as a player). */
    val finished: Boolean,
    /** Summed time from each question appearing to its answer. */
    val answerMillis: Long,
    /** This round's id, to tell "New best!" from an older best. */
    val roundId: String,
    /** The player's place once the round is stored; null before that, for Guest or unfinished. */
    val standing: ChallengeStanding? = null,
) {
    val newBest: Boolean get() = standing?.best?.id == roundId
}

/** Monotonic milliseconds for answer times (not the wall clock: it can jump). */
fun interface QuizTicker {
    fun millis(): Long

    companion object {
        val System = QuizTicker { java.lang.System.nanoTime() / NANOS_PER_MILLI }
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * A quiz round (ADR-028) or the Challenge (ADR-032). The round is played as the home's "Playing as"
 * pick; when its summary appears it is recorded once as a [dev.quinton.goa.core.model.trivia.TriviaRound]
 * (not for Guest, not for an unfinished Challenge, never on Leave). Answer time runs from a question
 * appearing to its answer and pauses while the screen isn't visible ([onVisible]).
 */
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel(assistedFactory = TriviaQuizViewModel.Factory::class)
class TriviaQuizViewModel internal constructor(
    private val route: TriviaQuizRoute,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val progressRepository: TriviaProgressRepository,
    private val playerRepository: PlayerRepository,
    private val groupRepository: GroupRepository,
    private val roundRepository: TriviaRoundRepository,
    private val clock: EditClock,
    /** Seeded in tests so a round is reproducible. */
    private val random: Random,
    private val ticker: QuizTicker = QuizTicker.System,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    @AssistedInject constructor(
        @Assisted route: TriviaQuizRoute,
        catalogRepository: CatalogRepository,
        settingsRepository: SettingsRepository,
        progressRepository: TriviaProgressRepository,
        playerRepository: PlayerRepository,
        groupRepository: GroupRepository,
        roundRepository: TriviaRoundRepository,
        clock: EditClock,
    ) : this(
        route,
        catalogRepository,
        settingsRepository,
        progressRepository,
        playerRepository,
        groupRepository,
        roundRepository,
        clock,
        Random.Default,
    )

    @AssistedFactory
    interface Factory {
        fun create(route: TriviaQuizRoute): TriviaQuizViewModel
    }

    private val config: TriviaConfig = if (route.challenge) TriviaChallenge.CONFIG else TriviaText.decode(route.config)

    private val state = MutableStateFlow<TriviaQuizUiState>(TriviaQuizUiState.Loading)
    val uiState: StateFlow<TriviaQuizUiState> = state.asStateFlow()

    private var session: TriviaSession? = null
    private var cards: Map<String, TriviaCardUi> = emptyMap()
    private var settings = AppSettings()
    private var catalogVersion = 0

    /** Who this round is played as, read once when the quiz opens. */
    private var playing = PlayingAsState.GUEST_ONLY

    /** Progress when the round began: what "learned this round" is measured against. */
    private var startProgress: Map<String, FactProgress> = emptyMap()

    // The round being played: its id and start, and the summed answer time.
    private var roundId = ""
    private var startedAt = 0L
    private var answerMillis = 0L

    /** Time spent on the open question while visible so far, and when the visible stretch began. */
    private var questionMillis = 0L
    private var shownAt: Long? = null
    private var visible = true

    /** Rounds already recorded: each at most once, whatever calls [publish] again. */
    private val recorded = HashSet<String>()
    private var standingJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val catalog = catalogRepository.catalog.catch { emit(Catalog.EMPTY) }.first()
        settings = runCatching { settingsRepository.settings.first() }.getOrDefault(AppSettings())
        val progress = runCatching { progressRepository.progress.first() }.getOrDefault(emptyMap())
        playing = runCatching { playingAs(playerRepository, groupRepository, progressRepository).first() }
            .getOrDefault(PlayingAsState.GUEST_ONLY)
        catalogVersion = catalog.dataVersion
        cards =
            catalog.heroes.flatMap { hero -> hero.cards.map { TriviaCardUi.of(hero, it) } }.associateBy { it.cardId }
        begin(
            if (route.challenge) {
                TriviaChallenge.start(catalog, progress, random)
            } else {
                TriviaEngine.start(pool(catalog), TriviaFacts.valueRange(catalog), progress, config, random)
            },
        )
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
        roundId = newId()
        startedAt = clock.now()
        answerMillis = 0
        standingJob?.cancel()
        publish(next)
        startTiming(next)
    }

    fun answer(answer: TriviaAnswer) {
        val current = session ?: return
        val outcome = TriviaEngine.answer(current, answer, clock.now())
        if (outcome.session === current) return
        answerMillis += stopTiming()
        // Stored right away: leaving mid-round keeps what was learned. NonCancellable, so clearing the
        // view model straight after an answer (Back, End → Done) can't drop the write. Only for this
        // phone's "You" (or anyone while no "You" is set): passing the phone round keeps the owner's
        // mastery theirs (ADR-032).
        if (outcome.updates.isNotEmpty() && playing.movesProgress) {
            viewModelScope.launch { withContext(NonCancellable) { progressRepository.record(outcome.updates) } }
        }
        publish(outcome.session)
    }

    fun next() {
        val current = session ?: return
        val next = TriviaEngine.next(current, random)
        publish(next)
        startTiming(next)
    }

    /** Ends the round now and shows the summary. */
    fun stop() {
        val current = session ?: return
        if (current.finished) return
        stopTiming()
        publish(TriviaEngine.stop(current))
    }

    /** A new round with the same setup (a Challenge: a new draw). */
    fun playAgain() {
        val current = session ?: return
        begin(TriviaEngine.start(current.pool, current.ranges, current.progress, config, random, current.draw))
    }

    /**
     * The quiz screen is in view ([visible] true) or not (the app went to the background, the screen
     * was left): answer time only runs while a question is in view and unanswered.
     */
    fun onVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        val open = session?.let { !it.finished && !it.answered } == true
        if (!visible) {
            shownAt?.let { questionMillis += ticker.millis() - it }
            shownAt = null
        } else if (open) {
            shownAt = ticker.millis()
        }
    }

    /** A new question is on screen: its time starts now (if the screen is in view). */
    private fun startTiming(session: TriviaSession) {
        questionMillis = 0
        shownAt = if (visible && !session.finished && !session.answered) ticker.millis() else null
    }

    /** The open question was answered (or the round ended): its time, and the clock stops. */
    private fun stopTiming(): Long {
        val total = questionMillis + (shownAt?.let { ticker.millis() - it } ?: 0)
        questionMillis = 0
        shownAt = null
        return total.coerceAtLeast(0)
    }

    /**
     * Records the round once, when its summary first appears: played as a player, with at least one
     * answer, and for a Challenge only with every question answered. A finished Challenge then
     * follows the player's place on the board.
     */
    private fun record(finished: TriviaSession) {
        val id = roundId
        if (!recorded.add(id)) return
        val player = playing.player ?: return
        val round = TriviaRounds.of(
            finished,
            id = id,
            playerId = player.id,
            challenge = route.challenge,
            playedAt = startedAt,
            answerMillis = answerMillis,
            catalogVersion = catalogVersion,
        ) ?: return
        standingJob = viewModelScope.launch {
            withContext(NonCancellable) { roundRepository.add(round) }
            if (route.challenge) follow(round)
        }
    }

    private suspend fun follow(round: TriviaRound) {
        combine(roundRepository.observeRounds(), playerRepository.observePlayers(includeArchived = true)) { rounds, all ->
            if (rounds.none { it.id == round.id }) {
                null
            } else {
                val board = TriviaLeaderboard.build(rounds, all, null, clock.now(), LeaderboardPeriod.ALL_TIME)
                TriviaLeaderboard.standing(board, round.playerId)
            }
        }.filterNotNull().distinctUntilChanged().collect { standing ->
            state.update { current ->
                val summary = current as? TriviaQuizUiState.Summary
                val result = summary?.challenge?.takeIf { it.roundId == round.id }
                if (result == null) current else summary.copy(challenge = result.copy(standing = standing))
            }
        }
    }

    /**
     * The round of only the numbers missed in this one: no duels, each number about twice
     * ([TriviaConfig.forPractice]); null when nothing was missed.
     */
    fun practiseRoute(): TriviaQuizRoute? {
        val missed = session?.missedFacts.orEmpty()
        if (missed.isEmpty()) return null
        return TriviaQuizRoute(TriviaText.encode(config.forPractice(missed.size)), missed.map { it.key })
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
        if (next.pool.isNotEmpty() && next.finished) record(next)
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
            challenge = route.challenge,
        )
    }

    private fun summary(session: TriviaSession): TriviaQuizUiState.Summary {
        val learned = if (!playing.movesProgress) {
            0
        } else {
            session.results.mapNotNull { (it.question as? TriviaQuestion.CardNumber)?.fact }
                .distinctBy { it.key }
                .count { fact ->
                    TriviaMastery.isMastered(fact, session.progress[fact.key]) &&
                        !TriviaMastery.isMastered(fact, startProgress[fact.key])
                }
        }
        return TriviaQuizUiState.Summary(
            correct = session.correctCount,
            asked = session.results.size,
            bestStreak = session.bestStreak,
            learned = learned,
            misses = session.results.filterNot { it.correct }.map(::miss),
            progressSaved = playing.movesProgress,
            playerName = playing.player?.name,
            challenge = if (route.challenge) {
                ChallengeResultUi(
                    finished = TriviaChallenge.isFinished(session),
                    answerMillis = answerMillis,
                    roundId = roundId,
                )
            } else {
                null
            },
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
}
