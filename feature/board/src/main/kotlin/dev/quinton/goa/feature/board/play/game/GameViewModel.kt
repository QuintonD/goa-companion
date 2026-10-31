package dev.quinton.goa.feature.board.play.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.data.game.AiPlayers
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.CoachAnalysis
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.CoachSuggestions
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.review.ReviewKeeper
import dev.quinton.goa.feature.board.play.review.ReviewUi
import dev.quinton.goa.feature.board.play.table.ResolutionFlow
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.choiceFor
import dev.quinton.goa.feature.board.play.table.logLines
import dev.quinton.goa.feature.board.play.table.resolutionFlow
import dev.quinton.goa.feature.board.play.table.turnLog
import javax.inject.Inject
import javax.inject.Provider
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays the game in progress (PRODUCT §9). The person answers their heroes' questions; computer
 * seats are answered by [AiPlayers] off the main thread, one action at a time, each shown for the
 * chosen pace with its log line ("thinking…" once it takes a while). Take back undoes the person's
 * last answer. With Coach on, the coach starts looking at each of the person's questions as soon as
 * it is asked ([Coach.prepare], in the background), so an answer is checked against a finished look
 * at once; a nudge waits for Choose anyway or Rethink and never answers for the person. Every answer
 * is saved.
 *
 * The session is not thread-safe: every call on it runs on [engine], one at a time (a single
 * thread of Dispatchers.Default in the app). The coach's look ahead runs on [analysis] on a fork of
 * the session that nothing else touches, so the table stays responsive while it looks.
 */
@Suppress("LargeClass")
@HiltViewModel
class GameViewModel internal constructor(
    private val catalogRepository: CatalogRepository,
    private val mapRepository: BoardMapRepository,
    private val saves: GameSaveRepository,
    private val factory: suspend () -> GameFactory,
    private val ai: AiPlayers,
    private val coach: Coach,
    private val clock: EditClock,
    private val engine: CoroutineDispatcher,
    private val random: Random,
    private val analysis: CoroutineDispatcher = engine,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Inject
    constructor(
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        saves: GameSaveRepository,
        factory: Provider<GameFactory>,
        ai: AiPlayers,
        coach: Coach,
        clock: EditClock,
    ) : this(
        catalogRepository,
        mapRepository,
        saves,
        { withContext(Dispatchers.Default) { factory.get() } },
        ai,
        coach,
        clock,
        Dispatchers.Default.limitedParallelism(1),
        Random.Default,
        Dispatchers.Default,
    )

    private val state = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = state.asStateFlow()

    /** The game being played; only touched on [engine]. */
    private var session: GameSession? = null
    private var progress: GameInProgress? = null
    private var map: BoardMap? = null
    private var heroes: Map<String, BoardHero> = emptyMap()
    private var cards: Map<String, Card> = emptyMap()
    private var humans: Set<String> = emptySet()
    private var bottomTeam: Team = Team.RED
    private var selected: Choice? = null
    private var nudge: PendingNudge? = null
    private var busy = false
    private var thinking = false
    private var humanAnswered = false
    private var reveal: List<RevealedCard> = emptyList()

    /** The coach's turning points once the game is over (null until it has looked). */
    private var points: List<CoachNote>? = null
    private var announcementId = 0L
    private var announcement: PlayAnnouncement? = null
    private var message: String? = null
    private var messageId = 0L
    private var shownEvents = 0

    /** The event count at the person's last answer: the panel tells what happened since. */
    private var answeredAt = 0

    /** The event count just before the person's last answer: the resolution flow starts there (ADR-046). */
    private var flowFrom = 0

    /** The computer's turn (or the coach after the game): cancelled by take back, rematch, leaving. */
    private var driver: Job? = null

    /**
     * The coach's look at the person's pending question, started when it is asked: dropped (and its
     * search cancelled) once the question is answered, on take back, rematch, Coach off and leaving.
     */
    private var lookahead: Lookahead? = null

    private class Lookahead(val decision: Decision, val analysis: Deferred<CoachAnalysis?>)

    /** The suggestions of the pending question, kept while the sheet is shut (ADR-051). */
    private var suggestions: Pair<Long, CoachSuggestions?>? = null
    private var suggesting: Job? = null
    private var suggestOpen = false

    /** The post-game review (ADR-051): its job, and what it has so far. */
    private var reporting: Job? = null
    private var report: ReportUi? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val stored = saves.current.first()
        val inProgress = when (stored) {
            StoredGame.None -> return unavailable(NoGame.NONE)
            StoredGame.Unreadable -> return unavailable(NoGame.UNREADABLE)
            is StoredGame.Saved -> stored.game
        }
        val catalog = runCatching { catalogRepository.catalog.first() }.getOrDefault(Catalog.EMPTY)
        val maps = runCatching { mapRepository.maps.first() }.getOrDefault(emptyList())
        val restored = withContext(engine) {
            try {
                factory().restore(inProgress.game)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // An engine change made this save replay differently, or the engine refuses it.
                null
            }
        } ?: return unavailable(NoGame.INCOMPATIBLE)
        val boardMap =
            maps.firstOrNull { it.id == inProgress.game.setup.mapId } ?: return unavailable(NoGame.MAP_MISSING)
        map = boardMap
        heroes = catalog.boardHeroes()
        cards = catalog.heroes.flatMap { it.allCards }.associateBy { it.id }
        start(restored, inProgress, fresh = false)
    }

    private fun unavailable(why: NoGame) {
        state.value = GameUiState.Unavailable(why)
    }

    private suspend fun start(game: GameSession, inProgress: GameInProgress, fresh: Boolean) {
        session = game
        progress = inProgress
        humans = game.setup.humans
        bottomTeam = game.setup.humanTeam ?: Team.RED
        selected = null
        nudge = null
        points = null
        review.reset()
        humanAnswered = !fresh && canUndo(game)
        shownEvents = withContext(engine) { game.events.size }
        answeredAt = withContext(engine) { turnStart(game.events) }
        flowFrom = answeredAt
        reveal = withContext(engine) { latestReveal(game.events) { cards[it]?.initiative } }
        publish()
        drive()
    }

    private val review = ReviewKeeper()

    /** Whether the person has an answer to take back: tried on a copy, so the game is untouched. */
    private suspend fun canUndo(game: GameSession): Boolean = withContext(engine) {
        runCatching { game.fork().undoToLastHumanDecision() }.getOrDefault(false)
    }

    // ---- Actions -------------------------------------------------------------------------

    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.Tap -> tap(action.tap)

            is GameAction.Select -> select(action.choice)

            is GameAction.Answer -> answer(action.choice)

            GameAction.ChooseAnyway -> chooseAnyway()

            GameAction.Rethink -> rethink()

            GameAction.TakeBack -> takeBack()

            is GameAction.SetCoach -> setCoach(action.on)

            is GameAction.SetPace -> update { it.copy(pace = action.pace) }

            GameAction.Flip -> {
                bottomTeam = bottomTeam.opponent
                viewModelScope.launch { publish() }
            }

            GameAction.Rematch -> rematch()

            is GameAction.MessageShown -> if (action.id == messageId) {
                message = null
                viewModelScope.launch { publish() }
            }

            is GameAction.Review -> {
                val turns = (uiState.value as? GameUiState.Playing)?.play?.review?.turns
                if (review.handle(action.action, turns)) viewModelScope.launch { publish() }
            }

            GameAction.AskSuggestions -> askSuggestions()

            GameAction.CloseSuggestions -> {
                suggestOpen = false
                viewModelScope.launch { publish() }
            }

            GameAction.StartReport -> startReport()

            GameAction.CancelReport -> cancelReport()
        }
    }

    /** A tap on the board: the option it stands for is selected. Returns false when it was no option. */
    fun tap(tap: TableTap): Boolean {
        val play = (uiState.value as? GameUiState.Playing)?.play ?: return false
        val choice = play.table.choiceFor(tap) ?: return false
        select(choice)
        return true
    }

    private fun select(choice: Choice) {
        val play = (uiState.value as? GameUiState.Playing)?.play ?: return
        if (!play.table.asking || choice !in play.table.decision?.options.orEmpty()) return
        selected = choice
        viewModelScope.launch { publish() }
    }

    private fun answer(choice: Choice) {
        val game = session ?: return
        if (busy || nudge != null) return
        busy = true
        viewModelScope.launch {
            publish()
            val decision = withContext(engine) { game.pending }
            if (decision == null || decision.seat !in humans || choice !in decision.options) {
                busy = false
                publish()
                return@launch
            }
            // The coach doesn't warn against what it just suggested.
            val suggestedFirst = suggestions?.takeIf { it.first == decision.id }?.second?.options?.firstOrNull()?.choice
            val note = if (progress?.coach == true && choice != suggestedFirst) {
                askCoach(game, decision, choice)
            } else {
                null
            }
            if (note != null) {
                nudge = PendingNudge(note, choice, currentTable()?.label(choice).orEmpty())
                update(publish = false) { it.copy(nudges = it.nudges + 1) }
                busy = false
                announce("Coach: ${note.text}")
                publish()
                return@launch
            }
            commit(game, decision, choice)
        }
    }

    /**
     * The coach's verdict on [choice]: from the look ahead when it has finished (at once), else after
     * waiting for it at most [NUDGE_WAIT_MS]; with no look ahead (a coach that doesn't prepare) the
     * coach looks at the answer itself, within the same wait. Past it the answer goes through unnudged.
     */
    private suspend fun askCoach(game: GameSession, decision: Decision, choice: Choice): CoachNote? {
        val ahead = lookahead?.takeIf { it.decision == decision }?.analysis
        return withTimeoutOrNull(NUDGE_WAIT_MS) {
            val prepared = ahead?.let { awaitLook(it) }
            withContext(engine) {
                val job = currentCoroutineContext().job
                runCatching {
                    if (prepared != null) {
                        coach.nudge(prepared, game, decision, choice) { !job.isActive }
                    } else {
                        coach.nudge(game, decision, choice) { !job.isActive }
                    }
                }.getOrNull()
            }
        }
    }

    /** A look ahead's result; null when it was cancelled (the caller's own cancellation still throws). */
    private suspend fun awaitLook(look: Deferred<CoachAnalysis?>): CoachAnalysis? {
        look.join()
        return if (look.isCancelled) null else look.await()
    }

    /**
     * Starts the coach looking at the person's [decision] in the background (Coach on), on a fork made
     * on [engine]: the real session is never touched off [engine], and looking never changes it.
     */
    private fun lookAhead(game: GameSession, decision: Decision) {
        if (progress?.coach != true || decision.seat !in humans) return
        if (lookahead?.decision == decision) return
        val previous = lookahead?.analysis
        previous?.cancel()
        val look = viewModelScope.async {
            // One look at a time: the coach's search is not made for two callers at once.
            previous?.join()
            val copy = withContext(engine) { game.fork() }
            withContext(analysis) {
                val job = currentCoroutineContext().job
                runCatching { coach.prepare(copy, decision) { !job.isActive } }.getOrNull()
            }
        }
        lookahead = Lookahead(decision, look)
    }

    private fun dropLookahead() {
        lookahead?.analysis?.cancel()
        lookahead = null
    }

    /**
     * Suggest (ADR-051), Coach on or off: at once if already worked out for this question, else on [analysis]
     * from the look ahead when there is one. The session is never touched off [engine].
     */
    private fun askSuggestions() {
        val game = session ?: return
        if (nudge != null) return
        viewModelScope.launch {
            val decision = withContext(engine) { game.pending } ?: return@launch
            if (decision.seat !in humans || decision.options.size < 2) return@launch
            suggestOpen = true
            if (suggestions?.first == decision.id) return@launch publish()
            suggesting?.cancel()
            val ahead = lookahead?.takeIf { it.decision == decision }?.analysis
            suggesting = launch {
                publish()
                val prepared = ahead?.let { awaitLook(it) }
                val copy = withContext(engine) { game.fork() }
                val said = withContext(analysis) {
                    val job = currentCoroutineContext().job
                    runCatching { coach.suggest(prepared, copy, decision) { !job.isActive } }.getOrNull()
                }
                suggestions = decision.id to said
                publish()
            }
        }
    }

    private fun dropSuggestions() {
        suggesting?.cancel()
        suggesting = null
        suggestions = null
        suggestOpen = false
    }

    /** Every decision of the person's looked at again, on [analysis]; play is over and nothing waits on it. */
    private fun startReport() {
        val game = session ?: return
        if (reporting?.isActive == true || report?.report != null) return
        report = ReportUi(running = true)
        reporting = viewModelScope.launch {
            publish()
            val saved = withContext(engine) { game.save() }
            val done = withContext(analysis) {
                val job = currentCoroutineContext().job
                runCatching {
                    coach.review(saved, humans, { n, total -> reportProgress(n, total) }) { !job.isActive }
                }.getOrNull()
            }
            report = if (done != null) ReportUi(report?.done ?: 0, report?.total ?: 0, report = done) else null
            publish()
        }
    }

    private fun reportProgress(done: Int, total: Int) {
        report = ReportUi(done, total, running = true)
        if (done % REPORT_PUBLISH_EVERY == 0) viewModelScope.launch { publish() }
    }

    private fun cancelReport() {
        reporting?.cancel()
        reporting = null
        report = null
        viewModelScope.launch { publish() }
    }

    private fun setCoach(on: Boolean) {
        update { it.copy(coach = on) }
        if (!on) return dropLookahead()
        val game = session ?: return
        viewModelScope.launch { withContext(engine) { game.pending }?.let { lookAhead(game, it) } }
    }

    private fun chooseAnyway() {
        val pending = nudge ?: return
        val game = session ?: return
        nudge = null
        busy = true
        viewModelScope.launch {
            val decision = withContext(engine) { game.pending }
            if (decision == null || pending.choice !in decision.options) {
                busy = false
                publish()
            } else {
                commit(game, decision, pending.choice)
            }
        }
    }

    private fun rethink() {
        if (nudge == null) return
        nudge = null
        selected = null
        update { it.copy(rethinks = it.rethinks + 1) }
    }

    private suspend fun commit(game: GameSession, decision: Decision, choice: Choice) {
        dropLookahead()
        dropSuggestions()
        val (before, result) = withContext(engine) { game.events.size to game.answer(decision.id, choice) }
        busy = false
        selected = null
        if (result is AnswerResult.Accepted) {
            humanAnswered = true
            absorb(game)
            answeredAt = shownEvents
            flowFrom = before
            save()
        }
        publish()
        drive()
    }

    private fun takeBack() {
        val game = session ?: return
        if (busy) return
        dropLookahead()
        dropSuggestions()
        viewModelScope.launch {
            driver?.let {
                it.cancel()
                it.join()
            }
            val undone = withContext(engine) { game.undoToLastHumanDecision() }
            if (!undone) {
                say("Nothing to take back yet.")
                drive()
                return@launch
            }
            selected = null
            nudge = null
            thinking = false
            review.reset()
            humanAnswered = canUndo(game)
            shownEvents = withContext(engine) { game.events.size }
            answeredAt = withContext(engine) { turnStart(game.events) }
            flowFrom = answeredAt
            reveal = withContext(engine) { latestReveal(game.events) { cards[it]?.initiative } }
            update(publish = false) { it.copy(takeBacks = it.takeBacks + 1) }
            say("Took back your last choice.")
            save()
            publish()
            drive()
        }
    }

    private fun rematch() {
        val game = session ?: return
        val old = progress ?: return
        dropLookahead()
        dropSuggestions()
        reporting?.cancel()
        report = null
        viewModelScope.launch {
            driver?.let {
                it.cancel()
                it.join()
            }
            val coin = if (random.nextBoolean()) Team.RED else Team.BLUE
            val setup = game.setup.copy(tieBreaker = coin)
            val fresh = withContext(engine) { runCatching { factory().newGame(setup) }.getOrNull() }
            if (fresh == null) {
                say("A rematch couldn't be set up.")
                return@launch
            }
            val inProgress = GameInProgress(
                game = withContext(engine) { fresh.save() },
                coach = old.coach,
                pace = old.pace,
                startedAt = clock.now(),
            )
            withContext(NonCancellable) { saves.save(inProgress) }
            announce("Rematch. The Tie Breaker coin shows ${if (coin == Team.RED) "Red" else "Blue"}.")
            start(fresh, inProgress, fresh = true)
        }
    }

    /** Leaving the game-over summary (Done, New game): the finished game is no longer kept. */
    fun discardFinished() {
        if ((uiState.value as? GameUiState.Playing)?.play?.over == null) return
        viewModelScope.launch { withContext(NonCancellable) { saves.clear() } }
    }

    /** Deletes an unreadable or incompatible save (or ends the game in progress). */
    fun deleteSaved() {
        driver?.cancel()
        dropLookahead()
        viewModelScope.launch {
            withContext(NonCancellable) { saves.clear() }
            session = null
            unavailable(NoGame.NONE)
        }
    }

    private fun update(publish: Boolean = true, transform: (GameInProgress) -> GameInProgress) {
        val current = progress ?: return
        val next = transform(current)
        progress = next
        viewModelScope.launch {
            withContext(NonCancellable) { saves.save(next) }
            if (publish) publish()
        }
    }

    // ---- The computer's turn -------------------------------------------------------------

    /** Answers computer seats until a question is the person's or the game ends, paced to follow. */
    private fun drive() {
        val game = session ?: return
        driver?.cancel()
        driver = viewModelScope.launch {
            while (isActive) {
                val decision = withContext(engine) { game.pending }
                if (decision == null) {
                    finish(game)
                    return@launch
                }
                if (decision.seat in humans) {
                    lookAhead(game, decision)
                    publish()
                    return@launch
                }
                computerAnswers(game, decision)
            }
        }
    }

    private suspend fun computerAnswers(game: GameSession, decision: Decision) {
        val computer = game.setup.seats.firstOrNull { it.heroId == decision.seat }?.controller as? Controller.Ai
        val player = ai.forStrength(computer?.strength ?: AiLevel.NORMAL.strength)
        thinking = false
        publish()
        // "Thinking…" once the computer takes a while.
        val slow = viewModelScope.launch {
            delay(THINKING_MS)
            thinking = true
            publish()
        }
        val choice = try {
            withContext(engine) {
                val job = currentCoroutineContext().job
                player.choose(game, decision) { !job.isActive }
            }
        } finally {
            slow.cancel()
            thinking = false
        }
        val result = withContext(engine) { game.answer(decision.id, choice) }
        if (result !is AnswerResult.Accepted) {
            // A computer answer that isn't one of the options: fall back to the first listed one.
            withContext(engine) { game.answer(decision.id, decision.options.first()) }
        }
        val revealed = absorb(game)
        save()
        publish()
        val pace = progress?.pace ?: AiPace.NORMAL
        delay(GamePacing.after(pace, revealed, decision.kind))
    }

    private suspend fun finish(game: GameSession) {
        publish()
        val progress = progress ?: return
        if (!progress.coach || points != null) return
        val seat = humans.firstOrNull() ?: return
        val notes = withTimeoutOrNull(TURNING_POINTS_TIMEOUT_MS) {
            withContext(engine) {
                val job = currentCoroutineContext().job
                runCatching { coach.turningPoints(game.save(), seat) { !job.isActive } }.getOrNull()
            }
        }
        points = notes.orEmpty()
        publish()
    }

    // ---- State ---------------------------------------------------------------------------

    /** Takes in the events since the last look: the reveal, the announcement. True on a reveal. */
    private suspend fun absorb(game: GameSession): Boolean {
        val (fresh, total) = withContext(engine) { game.events.drop(shownEvents) to game.events.size }
        shownEvents = total
        if (fresh.isEmpty()) return false
        var revealed = false
        fresh.forEach { event ->
            when (event) {
                is GameEvent.CardsRevealed -> {
                    reveal = revealOf(event) { cards[it]?.initiative }
                    revealed = true
                }

                is GameEvent.TurnStarted, is GameEvent.RoundStarted -> reveal = emptyList()

                else -> Unit
            }
        }
        announce(fresh.joinToString(" ") { it.text })
        return revealed
    }

    private fun announce(text: String) {
        if (text.isBlank()) return
        announcement = PlayAnnouncement(text, ++announcementId)
    }

    private fun say(text: String) {
        message = text
        messageId++
        viewModelScope.launch { publish() }
    }

    private suspend fun save() {
        val game = session ?: return
        val current = progress ?: return
        val saved = withContext(engine) { game.save() }
        val next = current.copy(game = saved.copy(savedAt = clock.now()))
        progress = next
        withContext(NonCancellable) { saves.save(next) }
    }

    private fun currentTable(): TableUi? = (uiState.value as? GameUiState.Playing)?.play?.table

    /** Rebuilds what the screen shows from the session (on [engine]) and the app's own state. */
    private suspend fun publish() {
        val game = session ?: return
        val board = map ?: return
        val inProgress = progress ?: return
        val teams = game.setup.seats.associate { it.heroId to it.team }
        val snapshot = withContext(engine) {
            val events = game.events.toList()
            val flow = resolutionFlow(events, flowFrom, teams, humans, { heroes.hero(it).name }, { cards[it]?.name })
            Snapshot(game.pending, game.mergedView(humans), events, review.build(game, humans), flow)
        }
        val decision = snapshot.pending
        val view = snapshot.view
        val status = turnStatus(decision, view, humans, thinking)
        val table = TableUi(
            map = board,
            view = view,
            heroes = heroes,
            cards = cards,
            mine = humans,
            decision = decision,
            asking = decision != null && decision.seat in humans && nudge == null,
            selected = selected?.takeIf { decision != null && it in decision.options },
            bottomTeam = bottomTeam,
            turnLog = turnLog(snapshot.events),
        )
        val over = if (status == TurnStatus.Over) {
            gameOverUi(view, snapshot.events, game.setup, inProgress, points)
        } else {
            null
        }
        val recent = recentLines(status, snapshot.events, answeredAt, game.setup.seats.map { it.heroId }.toSet())
        state.update {
            GameUiState.Playing(
                PlayUi(
                    table = table,
                    status = status,
                    recent = recent.lines,
                    recentStrong = recent.strong,
                    log = logLines(snapshot.events),
                    coach = inProgress.coach,
                    pace = inProgress.pace,
                    takeBacks = inProgress.takeBacks,
                    canTakeBack = humanAnswered && status != TurnStatus.Over,
                    busy = busy,
                    nudge = nudge,
                    reveal = reveal,
                    over = over,
                    announcement = announcement,
                    message = message,
                    messageId = messageId,
                    review = snapshot.review,
                    flow = snapshot.flow,
                    suggest = suggestUi(decision),
                    canSuggest = decision != null && decision.seat in humans && decision.options.size >= 2,
                    report = report,
                ),
            )
        }
    }

    /** What the sheet shows while it is open: the question's suggestions, or that the coach is thinking. */
    private fun suggestUi(decision: Decision?): SuggestUi? {
        if (!suggestOpen || decision == null) return null
        val known = suggestions?.takeIf { it.first == decision.id }
        return SuggestUi(decision.seat, looking = known == null, suggestions = known?.second)
    }

    private class Snapshot(
        val pending: Decision?,
        val view: GameView,
        val events: List<GameEvent>,
        val review: ReviewUi? = null,
        val flow: ResolutionFlow = ResolutionFlow(),
    )

    override fun onCleared() {
        driver?.cancel()
        dropLookahead()
        dropSuggestions()
        reporting?.cancel()
    }

    internal companion object {
        const val NUDGE_WAIT_MS = GamePacing.NUDGE_WAIT_MS
        const val TURNING_POINTS_TIMEOUT_MS = GamePacing.TURNING_POINTS_TIMEOUT_MS
        const val THINKING_MS = GamePacing.THINKING_MS

        /** The review tells its progress this often (each publish rebuilds the screen's state). */
        const val REPORT_PUBLISH_EVERY = 4
    }
}
