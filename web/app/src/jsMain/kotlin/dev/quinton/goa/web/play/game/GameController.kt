package dev.quinton.goa.web.play.game

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.TurnReview
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GamePacing
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.NoGame
import dev.quinton.goa.feature.board.play.game.PendingNudge
import dev.quinton.goa.feature.board.play.game.PlayAnnouncement
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.RevealedCard
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.game.gameOverUi
import dev.quinton.goa.feature.board.play.game.humanTeam
import dev.quinton.goa.feature.board.play.game.humans
import dev.quinton.goa.feature.board.play.game.latestReveal
import dev.quinton.goa.feature.board.play.game.recentLines
import dev.quinton.goa.feature.board.play.game.revealOf
import dev.quinton.goa.feature.board.play.game.turnStart
import dev.quinton.goa.feature.board.play.game.turnStatus
import dev.quinton.goa.feature.board.play.review.ReviewKeeper
import dev.quinton.goa.feature.board.play.review.pushesWon
import dev.quinton.goa.feature.board.play.review.reviewUi
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.choiceFor
import dev.quinton.goa.feature.board.play.table.logLines
import dev.quinton.goa.feature.board.play.table.turnLog
import dev.quinton.goa.web.engine.EngineRequest
import dev.quinton.goa.web.engine.EngineResponse
import dev.quinton.goa.web.engine.GameSnapshot
import dev.quinton.goa.web.engine.Turn
import dev.quinton.goa.web.play.data.EngineClient
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays the game in progress on the web (PRODUCT §9), as the phone's GameViewModel does, with the
 * session in the engine worker ([engine]): the person answers their heroes' questions; computer seats
 * are answered one action at a time (EngineRequest.GameComputer), each shown for the chosen pace,
 * "thinking…" once it takes a while. Take back undoes the person's last answer (a computer search in
 * progress is stopped by resetting the worker, which is then rebuilt from the save). With Coach on,
 * the coach's own worker ([coach]) looks at each of the person's questions as soon as it is asked;
 * an answer waits for its verdict at most [GamePacing.NUDGE_WAIT_MS], then goes through. Every answer
 * is saved ([saves]). What the screen shows is worked out by the same shared code as on the phone
 * (TableUi, turnStatus, recentLines, gameOverUi, reviewUi).
 *
 * [scope] is the screen's: leaving the screen cancels it, and with it any search in the workers.
 */
class GameController(
    private val engine: EngineClient,
    private val coach: EngineClient,
    private val saves: GameSaveRepository,
    private val catalog: suspend () -> Catalog,
    private val maps: suspend () -> List<BoardMap>,
    private val clock: EditClock,
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit = {},
    private val random: Random = Random.Default,
) {
    private val state = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = state.asStateFlow()

    private var snapshot: GameSnapshot? = null
    private val events = ArrayList<GameEvent>()
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
    private var points: List<CoachNote>? = null
    private var announcementId = 0L
    private var announcement: PlayAnnouncement? = null
    private var shownEvents = 0
    private var answeredAt = 0

    /** The worker generation our game was last sent to (a new worker knows nothing of it). */
    private var generation = -1

    private var driver: Job? = null
    private var look: Job? = null
    private var lookedAt: Long? = null
    private val review = ReviewKeeper()
    private var reviewData: EngineResponse.Review? = null

    /** The game's setup (null until loaded): the screen's "New game" pre-fills its line-up. */
    val setup: GameSetup? get() = snapshot?.setup

    fun start() {
        scope.launch { load() }
    }

    private suspend fun load() {
        val inProgress = when (val stored = saves.current.first()) {
            StoredGame.None -> return unavailable(NoGame.NONE)
            StoredGame.Unreadable -> return unavailable(NoGame.UNREADABLE)
            is StoredGame.Saved -> stored.game
        }
        val loadedCatalog = runCatching { catalog() }.getOrDefault(Catalog.EMPTY)
        val loadedMaps = runCatching { maps() }.getOrDefault(emptyList())
        val restored = restore(inProgress.game) ?: return unavailable(NoGame.INCOMPATIBLE)
        map = loadedMaps.firstOrNull { it.id == inProgress.game.setup.mapId } ?: return unavailable(NoGame.MAP_MISSING)
        heroes = loadedCatalog.boardHeroes()
        cards = loadedCatalog.heroes.flatMap { it.allCards }.associateBy { it.id }
        begin(restored, inProgress, fresh = false)
    }

    private fun unavailable(why: NoGame) {
        state.value = GameUiState.Unavailable(why)
    }

    /** Rebuilds [saved] in the worker; null when the engine can't (an older rules version). */
    private suspend fun restore(saved: SavedGame): GameSnapshot? {
        val response = engine.call(EngineRequest.GameRestore(saved))
        generation = engine.generation
        return (response as? EngineResponse.Game)?.game
    }

    /**
     * Sends [request] for the game; when it reached a fresh worker (the last one was stopped or
     * crashed), the game is rebuilt there from the save first and the request sent again.
     */
    private suspend fun call(request: EngineRequest): EngineResponse {
        val response = engine.call(request)
        if (engine.generation == generation) return response
        val saved = progress?.game ?: return response
        val rebuilt = restore(saved) ?: return response
        adopt(EngineResponse.Game(rebuilt))
        return engine.call(request)
    }

    private fun begin(game: GameSnapshot, inProgress: GameInProgress, fresh: Boolean) {
        events.clear()
        snapshot = null
        adopt(EngineResponse.Game(game))
        progress = inProgress
        humans = game.setup.humans
        bottomTeam = game.setup.humanTeam ?: Team.RED
        selected = null
        nudge = null
        points = null
        review.reset()
        humanAnswered = !fresh && game.canUndo == true
        shownEvents = events.size
        answeredAt = turnStart(events)
        reveal = latestReveal(events) { cards[it]?.initiative }
        publish()
        drive()
    }

    /** Takes in a snapshot: the log grows from where it says. */
    private fun adopt(response: EngineResponse.Game) {
        val game = response.game
        while (events.size > game.eventsFrom) events.removeAt(events.lastIndex)
        events += game.events
        snapshot = game
    }

    // ---- Actions ---------------------------------------------------------------------------------

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
                publish()
            }

            GameAction.Rematch -> rematch()

            is GameAction.MessageShown -> Unit

            is GameAction.Review -> {
                val turns = (state.value as? GameUiState.Playing)?.play?.review?.turns
                if (review.handle(action.action, turns)) scope.launch { loadReview() }
            }
        }
    }

    /** A tap on the board: the option it stands for is selected. False when it was no option. */
    fun tap(tap: TableTap): Boolean {
        val play = (state.value as? GameUiState.Playing)?.play ?: return false
        val choice = play.table.choiceFor(tap) ?: return false
        select(choice)
        return true
    }

    private fun select(choice: Choice) {
        val play = (state.value as? GameUiState.Playing)?.play ?: return
        if (!play.table.asking || choice !in play.table.decision?.options.orEmpty()) return
        selected = choice
        publish()
    }

    private fun answer(choice: Choice) {
        if (busy || nudge != null) return
        val decision = snapshot?.pending ?: return
        if (decision.seat !in humans || choice !in decision.options) return
        busy = true
        publish()
        scope.launch {
            val note = if (progress?.coach == true) askCoach(decision, choice) else null
            if (note != null) {
                val label = (state.value as? GameUiState.Playing)?.play?.table?.label(choice).orEmpty()
                nudge = PendingNudge(note, choice, label)
                update(save = true) { it.copy(nudges = it.nudges + 1) }
                busy = false
                announce("Coach: ${note.text}")
                publish()
                return@launch
            }
            commit(decision, choice)
        }
    }

    /**
     * The coach's verdict on [choice] within [GamePacing.NUDGE_WAIT_MS]: it has usually finished
     * looking while the person thought; past the wait the answer goes through unnudged and the
     * coach's worker (still searching) is stopped.
     */
    private suspend fun askCoach(decision: Decision, choice: Choice): CoachNote? {
        val saved = progress?.game ?: return null
        val response = withTimeoutOrNull(GamePacing.NUDGE_WAIT_MS) {
            runCatching { coach.call(EngineRequest.CoachNudge(saved, decision.id, choice)) }.getOrNull()
        }
        if (response == null) {
            coach.reset()
            lookedAt = null
        }
        return (response as? EngineResponse.Nudge)?.note
    }

    /** The coach starts looking at the person's [decision] in its own worker (Coach on). */
    private fun lookAhead(decision: Decision) {
        if (progress?.coach != true || decision.seat !in humans || lookedAt == decision.id) return
        val saved = progress?.game ?: return
        lookedAt = decision.id
        look = scope.launch {
            runCatching { coach.call(EngineRequest.CoachPrepare(saved, decision.id)) }
        }
    }

    private fun dropLookahead() {
        look?.cancel()
        look = null
        lookedAt = null
    }

    private fun setCoach(on: Boolean) {
        update(save = true) { it.copy(coach = on) }
        if (!on) return dropLookahead()
        snapshot?.pending?.let(::lookAhead)
    }

    private fun chooseAnyway() {
        val pending = nudge ?: return
        val decision = snapshot?.pending ?: return
        nudge = null
        busy = true
        scope.launch {
            if (pending.choice !in decision.options) {
                busy = false
                publish()
            } else {
                commit(decision, pending.choice)
            }
        }
    }

    private fun rethink() {
        if (nudge == null) return
        nudge = null
        selected = null
        update(save = true) { it.copy(rethinks = it.rethinks + 1) }
    }

    private suspend fun commit(decision: Decision, choice: Choice) {
        dropLookahead()
        val response = call(EngineRequest.GameAnswer(decision.id, choice)) as? EngineResponse.Game
        busy = false
        selected = null
        if (response != null && response.game.accepted) {
            humanAnswered = true
            adopt(response)
            absorb()
            answeredAt = shownEvents
            save()
        } else if (response != null) {
            adopt(response)
        }
        publish()
        drive()
    }

    private fun takeBack() {
        if (busy) return
        dropLookahead()
        scope.launch {
            driver?.let {
                it.cancel()
                it.join()
            }
            // The computer may be searching: stop it; the next request rebuilds the game from the save.
            if (engine.busy) engine.reset()
            val response = call(EngineRequest.GameTakeBack) as? EngineResponse.Game
            if (response == null || response.game.undone != true) {
                response?.let(::adopt)
                onMessage("Nothing to take back yet.")
                drive()
                return@launch
            }
            adopt(response)
            selected = null
            nudge = null
            thinking = false
            review.reset()
            humanAnswered = response.game.canUndo == true
            shownEvents = events.size
            answeredAt = turnStart(events)
            reveal = latestReveal(events) { cards[it]?.initiative }
            update(save = false) { it.copy(takeBacks = it.takeBacks + 1) }
            onMessage("Took back your last choice.")
            save()
            publish()
            drive()
        }
    }

    private fun rematch() {
        val game = snapshot ?: return
        val old = progress ?: return
        dropLookahead()
        scope.launch {
            driver?.let {
                it.cancel()
                it.join()
            }
            if (engine.busy) engine.reset()
            val coin = if (random.nextBoolean()) Team.RED else Team.BLUE
            val setup = game.setup.copy(tieBreaker = coin)
            val fresh = (engine.call(EngineRequest.GameStart(setup)) as? EngineResponse.Game)?.game
            generation = engine.generation
            if (fresh == null) {
                onMessage("A rematch couldn't be set up.")
                return@launch
            }
            val inProgress = GameInProgress(
                game = fresh.saved.copy(savedAt = clock.now()),
                coach = old.coach,
                pace = old.pace,
                startedAt = clock.now(),
            )
            withContext(NonCancellable) { saves.save(inProgress) }
            announce("Rematch. The Tie Breaker coin shows ${if (coin == Team.RED) "Red" else "Blue"}.")
            begin(fresh, inProgress, fresh = true)
        }
    }

    /** Leaving the game-over summary (Done, New game): the finished game is no longer kept. */
    fun discardFinished() {
        if ((state.value as? GameUiState.Playing)?.play?.over == null) return
        scope.launch { withContext(NonCancellable) { saves.clear() } }
    }

    /** Deletes an unreadable or incompatible save (or ends the game in progress). */
    fun deleteSaved(onDone: () -> Unit = {}) {
        driver?.cancel()
        dropLookahead()
        if (engine.busy) engine.reset()
        scope.launch {
            withContext(NonCancellable) { saves.clear() }
            snapshot = null
            unavailable(NoGame.NONE)
            onDone()
        }
    }

    private fun update(save: Boolean = true, transform: (GameInProgress) -> GameInProgress) {
        val current = progress ?: return
        val next = transform(current)
        progress = next
        if (save) {
            scope.launch {
                withContext(NonCancellable) { saves.save(next) }
                publish()
            }
        }
    }

    // ---- The computer's turn ---------------------------------------------------------------------

    /** Answers computer seats until a question is the person's or the game ends, paced to follow. */
    private fun drive() {
        driver?.cancel()
        driver = scope.launch {
            while (isActive) {
                val decision = snapshot?.pending
                if (decision == null) {
                    finish()
                    return@launch
                }
                if (decision.seat in humans) {
                    lookAhead(decision)
                    publish()
                    return@launch
                }
                computerAnswers(decision)
            }
        }
    }

    private suspend fun computerAnswers(decision: Decision) {
        thinking = false
        publish()
        val slow = scope.launch {
            delay(GamePacing.THINKING_MS)
            thinking = true
            publish()
        }
        val response = try {
            call(EngineRequest.GameComputer(decision.id)) as? EngineResponse.Game
        } finally {
            slow.cancel()
            thinking = false
        }
        if (response == null) {
            // The engine failed: say so, stop driving (Take back or leaving and coming back retries).
            onMessage("The computer couldn't choose. Leave the game and open it again.")
            throw CancellationException("engine failed")
        }
        adopt(response)
        val revealed = absorb()
        save()
        publish()
        val pace = progress?.pace ?: AiPace.NORMAL
        delay(GamePacing.after(pace, revealed || response.game.revealed, decision.kind))
    }

    private suspend fun finish() {
        publish()
        val inProgress = progress ?: return
        if (!inProgress.coach || points != null) return
        val seat = humans.firstOrNull() ?: return
        val notes = withTimeoutOrNull(GamePacing.TURNING_POINTS_TIMEOUT_MS) {
            runCatching { coach.call(EngineRequest.CoachTurningPoints(inProgress.game, seat)) }.getOrNull()
        }
        if (notes == null) coach.reset()
        points = (notes as? EngineResponse.TurningPoints)?.notes.orEmpty()
        publish()
    }

    // ---- State ------------------------------------------------------------------------------------

    /** Takes in the events since the last look: the reveal, the announcement. True on a reveal. */
    private fun absorb(): Boolean {
        val fresh = events.drop(shownEvents)
        shownEvents = events.size
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

    private suspend fun save() {
        val game = snapshot ?: return
        val current = progress ?: return
        val next = current.copy(game = game.saved.copy(savedAt = clock.now()))
        progress = next
        withContext(NonCancellable) { saves.save(next) }
    }

    private suspend fun loadReview() {
        if (!review.open) {
            reviewData = null
            publish()
            return
        }
        val rewind = review.rewindAt?.let { Turn(it.first, it.second) }
        reviewData = call(EngineRequest.GameReview(humans, rewind)) as? EngineResponse.Review
        publish()
    }

    /** Rebuilds what the screen shows from the latest snapshot and the controller's own state. */
    private fun publish() {
        val game = snapshot ?: return
        val board = map ?: return
        val inProgress = progress ?: return
        val decision = game.pending
        val view: GameView = game.view
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
            turnLog = turnLog(events),
        )
        val over = if (status == TurnStatus.Over) gameOverUi(view, events, game.setup, inProgress, points) else null
        val recent = recentLines(status, events, answeredAt, game.setup.seats.map { it.heroId }.toSet())
        val data = reviewData
        val reviewed = if (review.open && data != null) {
            reviewUi(
                game.setup.seats.map {
                    it.heroId
                }.toSet(),
                data.reviews,
                data.pushes,
                review.rewindAt,
            ) { _, _ -> data.rewind }
        } else {
            null
        }
        state.value = GameUiState.Playing(
            PlayUi(
                table = table,
                status = status,
                recent = recent.lines,
                recentStrong = recent.strong,
                log = logLines(events),
                coach = inProgress.coach,
                pace = inProgress.pace,
                takeBacks = inProgress.takeBacks,
                canTakeBack = humanAnswered && status != TurnStatus.Over,
                busy = busy,
                nudge = nudge,
                reveal = reveal,
                over = over,
                announcement = announcement,
                review = reviewed,
            ),
        )
    }

    /** The review's history (for tests and the screen): the engine's turns, or null while closed. */
    internal val reviewTurns: List<TurnReview>? get() = reviewData?.reviews

    /** Lane pushes won so far, from the log (the review's Now tab while its history loads). */
    internal val pushes: Map<Team, Int> get() = pushesWon(events)
}
