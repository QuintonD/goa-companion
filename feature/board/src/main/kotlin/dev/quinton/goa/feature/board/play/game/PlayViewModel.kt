package dev.quinton.goa.feature.board.play.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.game.AiPlayers
import dev.quinton.goa.core.data.game.GameEntry
import dev.quinton.goa.core.data.game.PlayOptions
import dev.quinton.goa.core.data.game.PlaySpeed
import dev.quinton.goa.core.data.game.SavedGameRepository
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GameFactoryProvider
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.navigation.PlayRoute
import dev.quinton.goa.feature.board.play.table.TableCatalog
import dev.quinton.goa.feature.board.play.table.TableModel
import dev.quinton.goa.feature.board.play.table.TableState
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A game against the computer (PRODUCT §9). The session is confined to [engine] (one thread: it is
 * not thread-safe); the computer's players and the Coach run on [compute] with a fork of the game,
 * never on the main thread.
 *
 * The loop: while the pending decision belongs to a computer seat, its player chooses
 * (cancellable), the answer is applied and saved, and the action stays on screen for a moment (the
 * Speed setting; card play is private and quick: "waiting for the others", p.7). A human decision
 * waits for [choose]. The reveal and each end of round wait for [continueGame]. Before a human
 * answer is applied the Coach (when on) may nudge, within a short timeout and never choosing for
 * the player: [chooseAnyway] or [rethink]. Every answer autosaves the game; [takeBack] undoes the
 * player's last answer (and the computer's after it) and is counted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = PlayViewModel.Factory::class)
class PlayViewModel internal constructor(
    private val route: PlayRoute,
    private val provider: GameFactoryProvider,
    private val games: SavedGameRepository,
    private val catalogRepository: CatalogRepository,
    private val mapRepository: BoardMapRepository,
    private val aiPlayers: AiPlayers,
    private val coach: Coach,
    private val clock: EditClock,
    private val engine: CoroutineDispatcher,
    private val compute: CoroutineDispatcher,
    private val random: Random = Random.Default,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    @AssistedInject constructor(
        @Assisted route: PlayRoute,
        provider: GameFactoryProvider,
        games: SavedGameRepository,
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        aiPlayers: AiPlayers,
        coach: Coach,
        clock: EditClock,
    ) : this(
        route,
        provider,
        games,
        catalogRepository,
        mapRepository,
        aiPlayers,
        coach,
        clock,
        engine = Dispatchers.Default.limitedParallelism(1),
        compute = Dispatchers.Default,
    )

    @AssistedFactory
    interface Factory {
        fun create(route: PlayRoute): PlayViewModel
    }

    private val state = MutableStateFlow<PlayUiState>(PlayUiState.Loading)
    val uiState: StateFlow<PlayUiState> = state.asStateFlow()

    private lateinit var factory: GameFactory
    private lateinit var session: GameSession
    private lateinit var stored: StoredGame
    private lateinit var catalog: TableCatalog
    private lateinit var map: BoardMap
    private var humans: Set<String> = emptySet()
    private var ais: Map<String, AiPlayer> = emptyMap()

    private var driveJob: Job? = null
    private var activity: Activity? = null
    private var coachPrompt: CoachPrompt? = null
    private val interludes = ArrayDeque<Pending>()
    private var gate: CompletableDeferred<Unit>? = null
    private var announcements = 0L
    private var announcement: Announcement? = null
    private var canTakeBack = false
    private var busy = false
    private var over: GameOverUi? = null

    init {
        viewModelScope.launch { load() }
    }

    // ---- Loading -----------------------------------------------------------------------------

    private suspend fun load() {
        when (val opened = open(games.observe(route.gameId).first())) {
            is Opened.Failed -> state.value = PlayUiState.Unplayable(route.gameId, opened.message, opened.otherRules)
            is Opened.Ready -> start(opened)
        }
    }

    /**
     * The saved game restored, or why it can't be: gone, unreadable, saved by other rules, its
     * answers no longer fitting the rules (the engine throws on restore), or its map missing.
     */
    private suspend fun open(entry: GameEntry?): Opened {
        val game = entry as? StoredGame
        factory = provider.get()
        val sameRules = game?.game?.engineVersion == factory.engineVersion
        val restored = if (game != null && sameRules) {
            withContext(engine) { runCatching { factory.restore(game.game) }.getOrNull() }
        } else {
            null
        }
        val gameMap = game?.let { g -> mapRepository.maps.first().firstOrNull { it.id == g.game.setup.mapId } }
        return when {
            entry == null -> Opened.Failed("This game is no longer saved.")
            game == null -> Opened.Failed("This saved game can't be read.")
            !sameRules -> Opened.Failed("This game was saved with another version of the rules.", otherRules = true)
            restored == null -> Opened.Failed("This game no longer fits the rules as the app plays them now.", true)
            gameMap == null -> Opened.Failed("This game's map isn't in this build.")
            else -> Opened.Ready(game, restored, gameMap)
        }
    }

    private suspend fun start(opened: Opened.Ready) {
        val game = opened.game
        map = opened.map
        catalog = TableCatalog.of(catalogRepository.catalog.first())
        session = opened.session
        stored = game
        val seats = game.game.setup.seats
        humans = seats.filter { it.controller == Controller.Human }.map { it.heroId }.toSet()
        ais = seats.mapIndexedNotNull { i, seat ->
            (seat.controller as? Controller.Ai)?.let { seat.heroId to aiPlayers.create(it.level, game.createdAt + i) }
        }.toMap()
        canTakeBack = withContext(engine) { session.fork().undoToLastHumanDecision() }
        publish()
        drive()
    }

    private val ready: Boolean get() = ::session.isInitialized

    // ---- The loop ----------------------------------------------------------------------------

    /** Runs the computer's seats until a human must answer, a moment waits, or the game ends. */
    private fun drive() {
        driveJob?.cancel()
        driveJob = viewModelScope.launch {
            while (isActive) {
                if (interludes.isNotEmpty()) waitForContinue()
                val decision = withContext(engine) { session.pending }
                if (decision == null) {
                    if (over == null) finish()
                    return@launch
                }
                if (decision.seat in humans) {
                    activity = activity?.copy(hero = null, prompt = null, thinking = false, waitingForOthers = false)
                    publish()
                    return@launch
                }
                computerAnswers(decision)
            }
        }
    }

    private suspend fun waitForContinue() {
        val wait = CompletableDeferred<Unit>()
        gate = wait
        publish()
        wait.await()
    }

    /** One computer answer: choose (off the main thread), apply, save, show it for a moment. */
    private suspend fun computerAnswers(decision: Decision) {
        val playing = decision.kind == DecisionKind.PLAY_CARD
        activity = Activity(
            hero = (state.value as? PlayUiState.Playing)?.table?.hero(decision.seat),
            prompt = if (playing) null else decision.prompt,
            thinking = false,
            waitingForOthers = playing,
            last = activity?.last.orEmpty(),
        )
        publish()
        val choice = computerChoice(ais[decision.seat], decision)
        val accepted = withContext(engine) {
            // A choice the engine refuses (a computer player's bug) never stalls the game.
            session.answer(decision.id, choice) as? AnswerResult.Accepted
                ?: session.answer(decision.id, decision.options.first()) as? AnswerResult.Accepted
        } ?: return
        save()
        onEvents(accepted.events)
        publish()
        delay(if (playing) PLAY_PAUSE_MS else pace(stored.options.speed))
    }

    /** The computer's choice, on [compute] with a fork of the game; "thinking…" once it takes a while. */
    private suspend fun computerChoice(ai: AiPlayer?, decision: Decision): Choice {
        if (ai == null) return decision.options.first()
        val fork = withContext(engine) { session.fork() }
        val thinking = viewModelScope.launch {
            delay(THINKING_AFTER_MS)
            activity = activity?.copy(thinking = true)
            publish()
        }
        return try {
            withContext(compute) {
                val job = coroutineContext.job
                ai.choose(fork, decision) { !job.isActive }
            }
        } finally {
            thinking.cancel()
        }
    }

    /** The player answers decision [decisionId] (a stale or repeated tap is ignored). */
    fun choose(decisionId: Long, choice: Choice) {
        if (!accepting) return
        busy = true
        viewModelScope.launch {
            val decision = withContext(engine) { session.pending }?.takeIf { it.asks(decisionId, choice) }
            if (decision == null) {
                busy = false
                return@launch
            }
            publish()
            val note = if (stored.options.coach) nudge(decision, choice) else null
            if (note != null) {
                busy = false
                val label = (state.value as? PlayUiState.Playing)?.table?.decision?.option(choice)?.label
                    ?: describe(choice)
                coachPrompt = CoachPrompt(decision.id, choice, label, note)
                say("Coach: ${note.text}")
                publish()
                return@launch
            }
            commit(decision, choice)
        }
    }

    /** The player's answers are taken: the game is on, nothing is being sent, no nudge is waiting. */
    private val accepting: Boolean get() = ready && !busy && over == null && coachPrompt == null

    /** This is decision [id], a human's, and [choice] is one of its options. */
    private fun Decision.asks(id: Long, choice: Choice): Boolean = this.id == id && seat in humans && choice in options

    /** The coach's nudge, if it has one within [COACH_TIMEOUT_MS]; it never holds the game up. */
    private suspend fun nudge(decision: Decision, choice: Choice): CoachNote? {
        val fork = withContext(engine) { session.fork() }
        val cancelled = AtomicBoolean(false)
        val asked = viewModelScope.async(compute) {
            runCatching { coach.nudge(fork, decision, choice) { cancelled.get() } }.getOrNull()
        }
        val note = withTimeoutOrNull(COACH_TIMEOUT_MS) { asked.await() }
        if (note == null) {
            cancelled.set(true)
            asked.cancel()
        }
        return note
    }

    /** "Choose anyway": the nudged choice goes ahead. */
    fun chooseAnyway() {
        val prompt = coachPrompt ?: return
        coachPrompt = null
        busy = true
        viewModelScope.launch {
            val decision = withContext(engine) { session.pending }
            if (decision == null || decision.id != prompt.decisionId) {
                busy = false
                return@launch publish()
            }
            commit(decision, prompt.choice)
        }
    }

    /** "Rethink": back to the question, nothing chosen. */
    fun rethink() {
        coachPrompt = null
        viewModelScope.launch { publish() }
    }

    private suspend fun commit(decision: Decision, choice: Choice) {
        val accepted = withContext(engine) { session.answer(decision.id, choice) as? AnswerResult.Accepted }
        busy = false
        if (accepted == null) return publish()
        canTakeBack = true
        save()
        onEvents(accepted.events)
        publish()
        drive()
    }

    /** "Continue" on the reveal or the end of a round. */
    fun continueGame() {
        if (interludes.isEmpty()) return
        interludes.removeFirst()
        viewModelScope.launch {
            publish()
            if (interludes.isEmpty()) gate?.complete(Unit)
        }
    }

    /** Undo the player's last answer and everything after it (the computer then answers again). */
    fun takeBack() {
        if (!ready || !canTakeBack || busy) return
        busy = true
        viewModelScope.launch {
            driveJob?.cancelAndJoin()
            gate = null
            coachPrompt = null
            interludes.clear()
            val undone = withContext(engine) { session.undoToLastHumanDecision() }
            if (undone) {
                over = null
                stored = stored.copy(takeBacks = stored.takeBacks + 1)
                canTakeBack = withContext(engine) { session.fork().undoToLastHumanDecision() }
                activity = null
                say("Taken back. ${withContext(engine) { session.pending?.prompt }.orEmpty()}")
                save()
            }
            busy = false
            publish()
            drive()
        }
    }

    fun setCoach(on: Boolean) = updateOptions { it.copy(coach = on) }

    fun setSpeed(speed: PlaySpeed) = updateOptions { it.copy(speed = speed) }

    private fun updateOptions(change: (PlayOptions) -> PlayOptions) {
        if (!ready) return
        stored = stored.copy(options = change(stored.options))
        if (!stored.options.coach) coachPrompt = null
        viewModelScope.launch {
            save()
            publish()
        }
    }

    // ---- Game over, rematch ------------------------------------------------------------------

    private suspend fun finish() {
        val (view, events) = withContext(engine) { session.view(humans.firstOrNull()) to session.events }
        val table = buildTable()
        val (heroes, pushes) = summarize(events, table.heroes)
        over = GameOverUi(
            winner = view.winner,
            reason = view.winReason,
            story = events.filterIsInstance<GameEvent.GameOver>().lastOrNull()?.text,
            humanTeam = table.humanTeam,
            rounds = view.round,
            heroes = heroes,
            pushes = pushes,
            takeBacks = stored.takeBacks,
            coachOn = stored.options.coach,
            turningPoints = if (stored.options.coach) null else emptyList(),
        )
        activity = null
        save()
        publish()
        if (stored.options.coach) {
            val seat = humans.firstOrNull()
            val saved = withContext(engine) { session.save() }
            val cancelled = AtomicBoolean(false)
            val points = seat?.let {
                withTimeoutOrNull(TURNING_POINTS_TIMEOUT_MS) {
                    withContext(compute) {
                        runCatching { coach.turningPoints(saved, seat) { cancelled.get() } }.getOrNull()
                    }
                }
            }
            cancelled.set(true)
            over = over?.copy(turningPoints = points.orEmpty())
            publish()
        }
    }

    /** The same line-up, map, players and options with a new coin flip; returns the new game's route. */
    suspend fun rematch(): PlayRoute? {
        if (!ready) return null
        val setup = stored.game.setup.copy(tieBreaker = if (random.nextBoolean()) Team.RED else Team.BLUE)
        val now = clock.now()
        val saved = withContext(engine) { factory.newGame(setup).save() }.copy(savedAt = now)
        val game = StoredGame(newId(), saved, stored.options, createdAt = now, updatedAt = now)
        withContext(NonCancellable) {
            games.save(game)
            games.delete(stored.id)
        }
        return PlayRoute(game.id)
    }

    /** Leaving a finished game (Done, New game) deletes it. */
    suspend fun discardFinished() {
        if (ready && over != null) withContext(NonCancellable) { games.delete(stored.id) }
    }

    /** Deletes an unplayable saved game. */
    suspend fun deleteUnplayable() = withContext(NonCancellable) { games.delete(route.gameId) }

    /** This game's line-up (hero ids in pick order), for "New game" from the result. */
    val lineup: Pair<List<String>, List<String>>
        get() {
            if (!ready) return emptyList<String>() to emptyList()
            val seats = stored.game.setup.seats
            return seats.filter { it.team == Team.RED }.map { it.heroId } to
                seats.filter { it.team == Team.BLUE }.map { it.heroId }
        }

    // ---- State -------------------------------------------------------------------------------

    private fun onEvents(events: List<GameEvent>) {
        if (events.isEmpty()) return
        val texts = events.map { it.text }
        val base = activity ?: Activity(null, null, thinking = false, waitingForOthers = false, last = emptyList())
        activity = base.copy(last = texts.takeLast(LAST_LINES), thinking = false)
        say(texts.joinToString(" "))
        queueInterludes(events)
    }

    private fun queueInterludes(events: List<GameEvent>) {
        // The end-of-round steps (p.8, pp.15–16) run between "End of round N." and the next round.
        val end = events.indexOfFirst { it is GameEvent.Note && it.text.startsWith(END_OF_ROUND) }
        val start = events.indexOfFirst { it is GameEvent.RoundStarted && it.round > 1 }
        val from = when {
            end >= 0 -> end
            start >= 0 -> 0
            else -> -1
        }
        if (from >= 0) {
            val until = start.takeIf { it > from } ?: events.size
            val steps = events.subList(from, until)
            val round = (events[from] as? GameEvent.Note)?.text?.let { DIGITS.find(it)?.value?.toIntOrNull() }
                ?: (events.filterIsInstance<GameEvent.RoundStarted>().firstOrNull()?.round?.minus(1) ?: 1)
            val levelled = steps.filterIsInstance<GameEvent.LevelledUp>().map { it.heroId }.toSet()
            interludes += Pending.RoundEnd(round, steps.map { it.text }, levelled)
        }
        if (events.any { it is GameEvent.CardsRevealed }) interludes += Pending.Reveal
    }

    private fun say(text: String) {
        if (text.isBlank()) return
        announcement = Announcement(++announcements, text)
    }

    /** The table for the human deciding now, else for the first human hero (their hand shows). */
    private suspend fun buildTable(): TableState = withContext(engine) {
        val pending = session.pending
        val viewer = pending?.seat?.takeIf { it in humans }
            ?: stored.game.setup.seats.firstOrNull { it.heroId in humans }?.heroId
        TableModel.build(map, session.view(viewer), pending, catalog, humans)
    }

    private suspend fun publish() {
        if (!ready) return
        val table = buildTable()
        val log = withContext(engine) { session.events.map { it.text } }
        state.value = PlayUiState.Playing(
            table = table,
            options = stored.options,
            activity = activity,
            interlude = interludes.firstOrNull()?.let { resolve(it, table) },
            coach = coachPrompt,
            canTakeBack = canTakeBack,
            takeBacks = stored.takeBacks,
            log = log,
            announcement = announcement,
            over = over,
            busy = busy,
        )
    }

    /** An interlude with the table's heroes filled in. */
    private fun resolve(pending: Pending, table: TableState): Interlude = when (pending) {
        is Pending.RoundEnd -> Interlude.RoundEnd(
            pending.round,
            pending.lines,
            table.heroes.filter { it.id in pending.levelled },
        )

        Pending.Reveal -> Interlude.Reveal(
            table.initiative,
            table.initiative.map {
                "${it.hero.name}: ${it.card?.name ?: "a card"}, initiative ${it.initiative ?: "?"}"
            },
        )
    }

    private suspend fun save() {
        val (saved, view) = withContext(engine) { session.save() to session.view(null) }
        val now = clock.now()
        stored = stored.copy(
            game = saved.copy(savedAt = now),
            updatedAt = now,
            round = view.round,
            turn = view.turn,
            finished = view.phase == GamePhase.GAME_OVER,
            winner = view.winner,
        )
        withContext(NonCancellable) { games.save(stored) }
    }

    private fun describe(choice: Choice): String = when (choice) {
        is Choice.Card -> catalog.cardName(choice.cardId)
        is Choice.Space -> "that space"
        is Choice.Unit -> catalog.hero(choice.unitId).name
        is Choice.Token -> "that token"
        is Choice.Action -> choice.action.name.lowercase().replace('_', ' ')
        is Choice.Option -> choice.label
        is Choice.Number -> choice.value.toString()
        Choice.Yes -> "Yes"
        is Choice.Decline -> choice.label
        Choice.Pass -> "Pass"
    }

    internal companion object {
        fun pace(speed: PlaySpeed): Long = when (speed) {
            PlaySpeed.SLOW -> 1_800L
            PlaySpeed.NORMAL -> 1_000L
            PlaySpeed.FAST -> 350L
        }

        /** A computer playing its card facedown: private and quick (p.7). */
        const val PLAY_PAUSE_MS = 200L
        const val THINKING_AFTER_MS = 600L
        const val COACH_TIMEOUT_MS = 1_500L
        const val TURNING_POINTS_TIMEOUT_MS = 10_000L
        const val LAST_LINES = 3
        const val END_OF_ROUND = "End of round"
        val DIGITS = Regex("\\d+")
    }
}

/** A saved game opened, or why it can't be. */
private sealed interface Opened {
    data class Ready(val game: StoredGame, val session: GameSession, val map: BoardMap) : Opened

    data class Failed(val message: String, val otherRules: Boolean = false) : Opened
}

/** Interludes as queued, before the table they show is known. */
private sealed interface Pending {
    data class RoundEnd(val round: Int, val lines: List<String>, val levelled: Set<String>) : Pending

    data object Reveal : Pending
}
