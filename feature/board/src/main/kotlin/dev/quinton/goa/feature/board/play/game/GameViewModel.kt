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
import dev.quinton.goa.core.game.CoachNote
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
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.choiceFor
import dev.quinton.goa.feature.board.play.table.logLines
import javax.inject.Inject
import javax.inject.Provider
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
 * last answer. With Coach on, an answer is first shown to the coach (short timeout); a nudge waits
 * for Choose anyway or Rethink and never answers for the person. Every answer is saved.
 *
 * The session is not thread-safe: every call on it runs on [engine], one at a time (a single
 * thread of Dispatchers.Default in the app).
 */
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
    )

    private val _state = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = _state.asStateFlow()

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

    /** The computer's turn (or the coach after the game): cancelled by take back, rematch, leaving. */
    private var driver: Job? = null

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
        val boardMap = maps.firstOrNull { it.id == inProgress.game.setup.mapId } ?: return unavailable(NoGame.MAP_MISSING)
        map = boardMap
        heroes = catalog.boardHeroes()
        cards = catalog.heroes.flatMap { it.allCards }.associateBy { it.id }
        start(restored, inProgress, fresh = false)
    }

    private fun unavailable(why: NoGame) {
        _state.value = GameUiState.Unavailable(why)
    }

    private suspend fun start(game: GameSession, inProgress: GameInProgress, fresh: Boolean) {
        session = game
        progress = inProgress
        humans = game.setup.seats.filter { it.controller == Controller.Human }.map { it.heroId }.toSet()
        bottomTeam = game.setup.seats.firstOrNull { it.controller == Controller.Human }?.team ?: Team.RED
        selected = null
        nudge = null
        points = null
        humanAnswered = !fresh && withContext(engine) { game.events.isNotEmpty() }
        shownEvents = withContext(engine) { game.events.size }
        reveal = withContext(engine) { latestReveal(game.events) }
        publish()
        drive()
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
            is GameAction.SetCoach -> update { it.copy(coach = action.on) }
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
            val note = if (progress?.coach == true) askCoach(game, decision, choice) else null
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

    private suspend fun askCoach(game: GameSession, decision: Decision, choice: Choice) =
        withTimeoutOrNull(NUDGE_TIMEOUT_MS) {
            withContext(engine) {
                val job = currentCoroutineContext().job
                runCatching { coach.nudge(game, decision, choice) { !job.isActive } }.getOrNull()
            }
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
        val result = withContext(engine) { game.answer(decision.id, choice) }
        busy = false
        selected = null
        if (result is AnswerResult.Accepted) {
            humanAnswered = true
            absorb(game)
            save()
        }
        publish()
        drive()
    }

    private fun takeBack() {
        val game = session ?: return
        if (busy) return
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
            shownEvents = withContext(engine) { game.events.size }
            reveal = withContext(engine) { latestReveal(game.events) }
            update(publish = false) { it.copy(takeBacks = it.takeBacks + 1) }
            announce("Took back your last choice.")
            save()
            publish()
            drive()
        }
    }

    private fun rematch() {
        val game = session ?: return
        val old = progress ?: return
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
                    publish()
                    return@launch
                }
                computerAnswers(game, decision)
            }
        }
    }

    private suspend fun computerAnswers(game: GameSession, decision: Decision) {
        val level = game.setup.seats.firstOrNull { it.heroId == decision.seat }?.controller as? Controller.Ai
        val player = ai.forLevel(level?.level ?: AiLevel.NORMAL)
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
        val hidden = decision.kind == DecisionKind.PLAY_CARD
        delay(
            when {
                revealed -> pace.millis * REVEAL_FACTOR
                hidden -> pace.millis / HIDDEN_FACTOR
                else -> pace.millis
            },
        )
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
                    reveal = revealOf(event)
                    revealed = true
                }
                is GameEvent.TurnStarted, is GameEvent.RoundStarted -> reveal = emptyList()
                else -> Unit
            }
        }
        announce(fresh.joinToString(" ") { it.text })
        return revealed
    }

    private fun latestReveal(events: List<GameEvent>): List<RevealedCard> {
        val last = events.indexOfLast { it is GameEvent.CardsRevealed }
        if (last < 0) return emptyList()
        if (events.drop(last + 1).any { it is GameEvent.TurnStarted || it is GameEvent.RoundStarted }) return emptyList()
        return revealOf(events[last] as GameEvent.CardsRevealed)
    }

    private fun revealOf(event: GameEvent.CardsRevealed): List<RevealedCard> = event.played
        .map { (hero, card) -> RevealedCard(hero, card, card?.let(cards::get)?.initiative) }
        .sortedByDescending { it.initiative ?: -1 }

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
        val snapshot = withContext(engine) { Snapshot(game.pending, mergedView(game), game.events.toList()) }
        val decision = snapshot.pending
        val view = snapshot.view
        val status = when {
            decision == null || view.phase == GamePhase.GAME_OVER -> TurnStatus.Over
            decision.seat in humans -> TurnStatus.YourTurn
            decision.kind == DecisionKind.PLAY_CARD && humans.all { h -> view.heroes.any { it.heroId == h && it.played != null } } ->
                TurnStatus.Waiting(view.heroes.filter { it.heroId !in humans && it.played == null }.map { it.heroId })
            else -> TurnStatus.Computer(decision.seat, thinking)
        }
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
        )
        val over = if (status == TurnStatus.Over) gameOver(view, snapshot.events, inProgress, game) else null
        _state.update {
            GameUiState.Playing(
                PlayUi(
                    table = table,
                    status = status,
                    recent = snapshot.events.takeLast(RECENT).map { it.text },
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
                ),
            )
        }
    }

    private class Snapshot(val pending: Decision?, val view: GameView, val events: List<GameEvent>)

    /**
     * What the person may see: the view of their first hero, with each other hero they control
     * as that hero sees itself (its hand and its played card). They play all of them.
     */
    private fun mergedView(game: GameSession): GameView {
        val first = humans.firstOrNull() ?: return game.view(null)
        val base = game.view(first)
        if (humans.size == 1) return base
        val own = humans.drop(1).associateWith { seat -> game.view(seat).heroes.firstOrNull { it.heroId == seat } }
        return base.copy(heroes = base.heroes.map { hero -> own[hero.heroId] ?: hero })
    }

    private fun gameOver(view: GameView, events: List<GameEvent>, inProgress: GameInProgress, game: GameSession): GameOverUi? {
        val over = events.filterIsInstance<GameEvent.GameOver>().lastOrNull()
        val winner = view.winner ?: over?.winner ?: return null
        val reason = view.winReason ?: over?.reason ?: return null
        val heroIds = game.setup.seats.map { it.heroId }.toSet()
        val defeats = events.filterIsInstance<GameEvent.Defeated>()
        val results = game.setup.seats.map { seat ->
            val hero = view.heroes.firstOrNull { it.heroId == seat.heroId }
            HeroResult(
                heroId = seat.heroId,
                team = seat.team,
                controller = seat.controller,
                level = hero?.level ?: 1,
                heroDefeats = defeats.count { it.byHeroId == seat.heroId && it.unitId in heroIds },
                minionDefeats = defeats.count { it.byHeroId == seat.heroId && it.unitId !in heroIds },
                defeated = defeats.count { it.unitId == seat.heroId },
                coins = events.filterIsInstance<GameEvent.CoinsGained>().filter { it.heroId == seat.heroId }.sumOf { it.amount },
            )
        }
        val yourTeam = game.setup.seats.firstOrNull { it.controller == Controller.Human }?.team
        return GameOverUi(
            winner = winner,
            reason = reason,
            youWon = winner == yourTeam,
            rounds = view.round,
            heroes = results,
            takeBacks = inProgress.takeBacks,
            nudges = inProgress.nudges,
            rethinks = inProgress.rethinks,
            turningPoints = points,
            coach = inProgress.coach,
        )
    }

    override fun onCleared() {
        driver?.cancel()
    }

    internal companion object {
        /** The coach gets this long to look at a choice; then the answer goes through unnudged. */
        const val NUDGE_TIMEOUT_MS = 1_500L

        const val TURNING_POINTS_TIMEOUT_MS = 20_000L

        /** "Thinking…" after this long. */
        const val THINKING_MS = 700L

        /** The reveal stays this many paces; a hidden card play (p.7) a fraction of one. */
        const val REVEAL_FACTOR = 2L
        const val HIDDEN_FACTOR = 4L

        const val RECENT = 4
    }
}
