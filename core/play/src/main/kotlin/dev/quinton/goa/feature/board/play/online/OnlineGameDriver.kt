package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.Resume
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.TurnRef
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.NoGame
import dev.quinton.goa.feature.board.play.game.OnlineUi
import dev.quinton.goa.feature.board.play.game.PlayAnnouncement
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.RevealedCard
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.game.gameOverUi
import dev.quinton.goa.feature.board.play.game.latestReveal
import dev.quinton.goa.feature.board.play.game.recentLines
import dev.quinton.goa.feature.board.play.game.revealOf
import dev.quinton.goa.feature.board.play.game.turnStart
import dev.quinton.goa.feature.board.play.review.ReviewKeeper
import dev.quinton.goa.feature.board.play.review.pushesWon
import dev.quinton.goa.feature.board.play.review.reviewUi
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.choiceFor
import dev.quinton.goa.feature.board.play.table.logLines
import dev.quinton.goa.feature.board.play.table.resolutionFlow
import dev.quinton.goa.feature.board.play.table.turnLog
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException

/**
 * Plays one online game on a screen (ADR-056, docs/ONLINE.md): turns the server's frames into the same
 * [GameUiState] the local game shows, with the same shared functions. The server holds the game; this
 * holds the person's frames, the log and what the screen is doing.
 *
 * [scope] is the screen's and must run on one thread (the main one): leaving the screen cancels it and
 * closes the socket. [client] names the build for the server's logs (`android/0.13.0`).
 */
class OnlineGameDriver(
    private val gameId: String,
    private val connector: GameConnector,
    private val identities: OnlineIdentityStore,
    private val catalog: suspend () -> Catalog,
    private val maps: suspend () -> List<BoardMap>,
    private val clock: EditClock,
    private val scope: CoroutineScope,
    private val client: String,
    private val random: Random = Random.Default,
) : OnlineGame {
    private val mutableState = MutableStateFlow<OnlineState>(OnlineState.Connecting())
    override val state: StateFlow<OnlineState> = mutableState.asStateFlow()

    private var started = false
    private var catalogVersion = 0
    private var loadedMaps: List<BoardMap> = emptyList()
    private var heroes: Map<String, BoardHero> = emptyMap()
    private var cards: Map<String, Card> = emptyMap()

    private var start: ServerMessage.GameStart? = null
    private var latest: ServerMessage.Table? = null
    private val frames = LinkedHashMap<String?, ServerMessage.Table>()
    private val events = ArrayList<GameEvent>()
    private var ended: ServerMessage.End? = null
    private var gone = false
    private var refused = false

    private var bottomTeam = Team.RED
    private var selected: Choice? = null
    private var shownTicket: Long? = null

    /** The ticket of the answer sent, until a new ticket arrives: a second answer to it isn't sent. */
    private var answered: Long? = null
    private var rejected: Long? = null
    private var shownEvents = 0
    private var answeredAt = 0
    private var reveal: List<RevealedCard> = emptyList()
    private var announcementId = 0L
    private var announcement: PlayAnnouncement? = null
    private var message: String? = null
    private var messageId = 0L
    private val review = ReviewKeeper()
    private var reviewData: ServerMessage.Reviewed? = null
    private var play: PlayUi? = null

    private var live: GameConnection? = null
    private var reader: Job? = null
    private var waitForGame: Job? = null

    /** A table has arrived on this connection: until then the screen's table may be out of date. */
    private var synced = false
    private var attempts = 0
    private var verdict = Next.RETRY
    private var retryAfterMs: Long? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var heardAt = 0L
    private var pingedAt = 0L

    /** The server's clock minus this device's. */
    private var serverOffset = 0L

    fun start() {
        if (started) return
        started = true
        scope.launch { run() }
        scope.launch { tick() }
    }

    // ---- The connection ----------------------------------------------------------------------------

    private enum class Next { RETRY, STOP }

    private suspend fun run() {
        val loadedCatalog = attempt { catalog() } ?: Catalog.EMPTY
        loadedMaps = attempt { maps() }.orEmpty()
        catalogVersion = loadedCatalog.dataVersion
        heroes = loadedCatalog.boardHeroes()
        cards = loadedCatalog.heroes.flatMap { it.allCards }.associateBy { it.id }
        while (connectOnce() == Next.RETRY && ended == null && !gone) {
            synced = false
            when {
                latest != null -> publish()
                retryAfterMs == null -> notReached()
            }
            wait()
        }
    }

    private fun notReached() {
        mutableState.value = OnlineState.Connecting(OnlineWords.UNREACHABLE)
    }

    /** The backoff, or as long as a refusal said; [resumeNow] cuts it short. */
    private suspend fun wait() {
        val ms = retryAfterMs ?: reconnectDelay(attempts++, random)
        retryAfterMs = null
        wake.tryReceive()
        withTimeoutOrNull(ms) { wake.receive() }
    }

    private suspend fun connectOnce(): Next {
        val identity = identities.load() ?: return refuse(Refusal.BAD_TOKEN)
        val hello = ClientMessage.Hello(
            protocol = OnlineProtocol.VERSION,
            token = identity.token,
            client = client,
            catalogVersion = catalogVersion,
            resume = listOf(Resume(gameId, events.size)),
        )
        val connection = withTimeoutOrNull(OPEN_TIMEOUT_MS) { attempt { connector.open(hello) } } ?: return Next.RETRY
        try {
            heardAt = clock.now()
            live = connection
            verdict = Next.RETRY
            coroutineScope {
                val reading = launch { read(connection) }
                reader = reading
                val heart = launch { heartbeat(connection) }
                reading.join()
                heart.cancel()
            }
        } finally {
            live = null
            reader = null
            waitForGame?.cancel()
            connection.close()
        }
        return verdict
    }

    private suspend fun read(connection: GameConnection) {
        val failure = runCatching { connection.incoming.takeWhile(::take).collect() }.exceptionOrNull()
        when (failure) {
            is CancellationException -> throw failure

            // A frame this build can't read: a newer server, never a crash.
            is SerializationException -> verdict = refuse(Refusal.UPDATE_APP)

            else -> Unit
        }
    }

    /** Pings for the clock's sake, and gives up on a socket that has gone silent (browsers don't tell). */
    private suspend fun heartbeat(connection: GameConnection) {
        while (true) {
            ping(connection)
            delay(PING_EVERY_MS)
            if (clock.now() - heardAt > SILENT_MS) {
                reader?.cancel()
                return
            }
        }
    }

    private suspend fun ping(connection: GameConnection) {
        pingedAt = clock.now()
        if (!sent(connection, ClientMessage.Ping(pingedAt))) reader?.cancel()
    }

    private suspend fun sent(connection: GameConnection, message: ClientMessage): Boolean =
        attempt { connection.send(message) } != null

    /** Whatever goes wrong on the network is a dropped connection; being cancelled stays being cancelled. */
    private suspend fun <T : Any> attempt(block: suspend () -> T): T? {
        val result = runCatching { block() }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return result.getOrNull()
    }

    private fun refuse(reason: Refusal, retryAfterSeconds: Int? = null): Next {
        mutableState.value = OnlineState.Refused(reason, retryAfterSeconds)
        if (retryAfterSeconds != null) {
            retryAfterMs = retryAfterSeconds * MILLIS_PER_SECOND
            return Next.RETRY
        }
        refused = true
        return Next.STOP
    }

    // ---- Frames ------------------------------------------------------------------------------------

    /** Takes in one frame. False ends this connection ([verdict] says what next). */
    private fun take(frame: ServerMessage): Boolean {
        heardAt = clock.now()
        return when (frame) {
            is ServerMessage.Welcome -> {
                serverOffset = frame.serverTime - heardAt
                if (gameId !in frame.activeGames) expectGameSoon()
                true
            }

            is ServerMessage.Refused -> {
                verdict = refuse(frame.reason, frame.retryAfterSeconds)
                false
            }

            is ServerMessage.Pong -> {
                if (frame.nonce == pingedAt) serverOffset = frame.serverTime - (pingedAt + heardAt) / 2
                true
            }

            is ServerMessage.Lobby -> true

            is ServerMessage.GameStart -> {
                if (frame.gameId == gameId) begin(frame)
                true
            }

            is ServerMessage.Table -> frame.gameId != gameId || takeTable(frame)

            is ServerMessage.Ack -> {
                if (frame.gameId == gameId) takeAck(frame)
                true
            }

            is ServerMessage.Reviewed -> {
                if (frame.gameId == gameId) {
                    reviewData = frame
                    publish()
                }
                true
            }

            is ServerMessage.End -> {
                if (frame.gameId == gameId) finish(frame)
                true
            }
        }
    }

    /** Not among the person's running games: it may still send how it ended, else it is gone. */
    private fun expectGameSoon() {
        waitForGame?.cancel()
        waitForGame = scope.launch {
            delay(GAME_GONE_AFTER_MS)
            gone = true
            if (latest == null) {
                verdict = refuse(Refusal.NO_SUCH_ROOM)
            } else {
                publish()
            }
            reader?.cancel()
        }
    }

    private fun begin(frame: ServerMessage.GameStart) {
        waitForGame?.cancel()
        // Sent again on every reconnect: the log and what the screen is doing stay as they are.
        if (start == null) {
            bottomTeam = frame.setup.seats.firstOrNull { it.heroId in frame.you }?.team ?: Team.RED
        }
        start = frame
    }

    private fun takeTable(table: ServerMessage.Table): Boolean {
        val game = start ?: return true
        // Something was missed: ask for it again rather than guess.
        if (table.eventsFrom > events.size) return false
        waitForGame?.cancel()
        val first = latest == null
        // The online log only grows: there is no take back, so what overlaps is already held.
        events += table.events.drop(events.size - table.eventsFrom)
        // A page of a long catch-up: the old log isn't news, and the table it ends with says where things stand.
        if (table.more) return true
        frames[table.view.viewer] = table
        latest = table
        attempts = 0
        if (!synced) resync(table)
        synced = true
        val decision = currentDecision(game.you)
        if (decision?.id != shownTicket) {
            shownTicket = decision?.id
            selected = null
        }
        val grew = absorb(first)
        if (grew && review.open) askReview()
        publish()
        return true
    }

    /** The first table on a connection: an answer lost with the old one must be asked again. */
    private fun resync(table: ServerMessage.Table) {
        val decision = table.decision
        if (decision != null && decision.id == answered && decision.seat !in table.locked) answered = null
        if (review.open) askReview()
    }

    private fun takeAck(ack: ServerMessage.Ack) {
        if (ack.accepted || ack.ticket != answered) return
        // A stale ticket must not hang: the latest table's question goes back up, and if the very same
        // ticket is refused twice this device has missed something, so it asks again.
        val again = rejected == ack.ticket
        rejected = ack.ticket
        answered = null
        say(OnlineWords.ANSWER_NOT_TAKEN)
        if (again) reader?.cancel()
    }

    private fun finish(end: ServerMessage.End) {
        waitForGame?.cancel()
        ended = end
        answered = null
        selected = null
        publish()
    }

    /** Takes in the events since the last look: the reveal, the announcement. True when there were any. */
    private fun absorb(first: Boolean): Boolean {
        if (first) {
            // Coming into a game under way: where the current turn began, not a reading of the whole log.
            answeredAt = turnStart(events)
            shownEvents = events.size
            reveal = latestReveal(events) { cards[it]?.initiative }
            return false
        }
        val fresh = events.drop(shownEvents)
        shownEvents = events.size
        if (fresh.isEmpty()) return false
        fresh.forEach { event ->
            when (event) {
                is GameEvent.CardsRevealed -> reveal = revealOf(event) { cards[it]?.initiative }
                is GameEvent.TurnStarted, is GameEvent.RoundStarted -> reveal = emptyList()
                else -> Unit
            }
        }
        val text = fresh.joinToString(" ") { it.text }
        if (text.isNotBlank()) announcement = PlayAnnouncement(text, ++announcementId)
        return true
    }

    /** A real question of the person's from any of their frames, else what the latest frame says. */
    private fun currentDecision(you: Set<String>): Decision? =
        frames.values.firstNotNullOfOrNull { frame -> frame.decision?.takeIf { canAnswer(it, you, answered) } }
            ?: latest?.decision

    // ---- Actions -----------------------------------------------------------------------------------

    override fun onAction(action: GameAction) {
        when (action) {
            is GameAction.Tap -> tap(action.tap)

            is GameAction.Select -> select(action.choice)

            is GameAction.Answer -> answer(action.choice)

            GameAction.Flip -> {
                bottomTeam = bottomTeam.opponent
                publish()
            }

            is GameAction.MessageShown -> if (action.id == messageId) {
                message = null
                publish()
            }

            is GameAction.Review -> {
                val turns = play?.review?.turns
                if (review.handle(action.action, turns)) askReview()
            }

            // These need a whole game, which a phone never holds online (docs/ONLINE.md "Off online").
            GameAction.ChooseAnyway, GameAction.Rethink, GameAction.TakeBack, is GameAction.SetCoach,
            is GameAction.SetPace, GameAction.Rematch, GameAction.AskSuggestions, GameAction.CloseSuggestions,
            GameAction.StartReport, GameAction.CancelReport,
            -> Unit
        }
    }

    override fun tap(tap: TableTap): Boolean {
        val choice = play?.table?.choiceFor(tap) ?: return false
        select(choice)
        return true
    }

    private fun select(choice: Choice) {
        val table = play?.table ?: return
        if (!table.asking || choice !in table.decision?.options.orEmpty()) return
        selected = choice
        publish()
    }

    private fun answer(choice: Choice) {
        val connection = live ?: return
        val decision = start?.let { currentDecision(it.you) }?.takeIf { it.id != answered } ?: return
        val asked = play?.table?.asking == true && synced && ended == null
        if (!asked || choice !in decision.options) return
        answered = decision.id
        answeredAt = events.size
        selected = null
        publish()
        send(connection, ClientMessage.Answer(gameId, decision.id, choice))
    }

    private fun send(connection: GameConnection, message: ClientMessage) {
        scope.launch { if (!sent(connection, message)) reader?.cancel() }
    }

    private fun askReview() {
        if (!review.open) {
            reviewData = null
            publish()
            return
        }
        val connection = live ?: return publish()
        send(connection, ClientMessage.AskReview(gameId, review.rewindAt?.let { TurnRef(it.first, it.second) }))
    }

    private fun say(text: String) {
        message = text
        messageId++
        publish()
    }

    override fun resumeNow() {
        attempts = 0
        val connection = live
        if (connection == null) {
            wake.trySend(Unit)
        } else if (clock.now() - heardAt > RESUME_SILENT_MS) {
            reader?.cancel()
        } else {
            scope.launch { ping(connection) }
        }
    }

    override fun leave() {
        val connection = live
        gone = true
        scope.launch {
            connection?.let { sent(it, ClientMessage.Leave(gameId)) }
            reader?.cancel()
            wake.trySend(Unit)
        }
    }

    // ---- State -------------------------------------------------------------------------------------

    private fun serverNow(): Long = clock.now() + serverOffset

    /** The clock on screen: one second at a time, without working out the rest of the table again. */
    private suspend fun tick() {
        while (ended == null && !refused) {
            delay(MILLIS_PER_SECOND)
            val shown = play ?: continue
            if (latest?.clock != null) show(shown.copy(online = onlineUi()))
        }
    }

    private fun onlineUi(): OnlineUi? {
        val game = start ?: return null
        val table = latest ?: return null
        val end = ended
        val running = table.clock.takeIf { end == null && !gone }
        return OnlineUi(
            players = game.players.associate { it.seat to it.name },
            presence = table.presence,
            clockSeat = running?.seat,
            clockSeconds = running?.let { secondsLeft(it.deadlineAt, serverNow()) },
            ranked = game.ranked,
            rated = end?.rated,
            closed = end?.voided?.let(OnlineWords::voided) ?: OnlineWords.GAME_GONE.takeIf { gone && end == null },
        )
    }

    /** Rebuilds what the screen shows from the latest frames and the driver's own state. */
    private fun publish() {
        val game = start ?: return
        val table = latest ?: return
        val board = loadedMaps.firstOrNull { it.id == game.setup.mapId }
            ?: return show(GameUiState.Unavailable(NoGame.MAP_MISSING))
        val finished = ended != null || gone
        val decision = currentDecision(game.you).takeIf { !finished }
        val view = viewOf(table, game)
        val heroNames = game.players.associate { it.seat to it.name }
        val status = onlineTurnStatus(decision, view, game.you, answered, table.locked) { seat ->
            heroNames[seat] ?: heroes.hero(seat).name
        }
        val asking = synced && !finished && canAnswer(decision, game.you, answered)
        val heroIds = game.setup.seats.map { it.heroId }.toSet()
        val recent = recentLines(status, events, answeredAt, heroIds)
        show(
            PlayUi(
                table = TableUi(
                    map = board,
                    view = view,
                    heroes = heroes,
                    cards = cards,
                    mine = game.you,
                    decision = decision,
                    asking = asking,
                    selected = selected?.takeIf { asking && it in decision?.options.orEmpty() },
                    bottomTeam = bottomTeam,
                    turnLog = turnLog(events),
                    players = heroNames,
                ),
                status = status,
                recent = recent.lines,
                recentStrong = recent.strong,
                log = logLines(events),
                coach = false,
                pace = AiPace.NORMAL,
                takeBacks = 0,
                canTakeBack = false,
                busy = status == TurnStatus.YourTurn && !asking,
                reveal = reveal,
                over = if (status == TurnStatus.Over && !gone && ended?.voided == null) {
                    gameOverUi(view, events, game.setup, game.setup.seats.firstOrNull { it.heroId in game.you }?.team)
                } else {
                    null
                },
                announcement = announcement,
                message = message,
                messageId = messageId,
                review = reviewOf(heroIds),
                flow = resolutionFlow(
                    events,
                    answeredAt,
                    game.setup.seats.associate { it.heroId to it.team },
                    game.you,
                    { heroes.hero(it).name },
                    { cards[it]?.name },
                ),
                online = onlineUi(),
            ),
        )
    }

    private fun viewOf(table: ServerMessage.Table, game: ServerMessage.GameStart): GameView {
        val merged = mergeOwnSeats(table.view, frames.mapValues { it.value.view }, game.you)
        val end = ended ?: return merged
        return merged.copy(winner = end.winner ?: merged.winner, winReason = end.reason ?: merged.winReason)
    }

    private fun reviewOf(heroIds: Set<String>) = reviewData?.takeIf { review.open }?.let { data ->
        reviewUi(heroIds, data.reviews, data.pushes.ifEmpty { pushesWon(events) }, review.rewindAt) { _, _ ->
            data.rewind
        }
    }

    private fun show(shown: PlayUi) {
        play = shown
        show(GameUiState.Playing(shown))
    }

    private fun show(game: GameUiState) {
        if (refused) return
        mutableState.value = if (synced || ended != null || gone) {
            OnlineState.InGame(game)
        } else {
            OnlineState.Reconnecting(game)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val OPEN_TIMEOUT_MS = 15_000L
        const val PING_EVERY_MS = 20_000L

        /** Longer than two pings and the server's own 20 s: a socket this quiet is dead. */
        const val SILENT_MS = 50_000L

        /** Coming back to the foreground after this long, don't trust the old socket. */
        const val RESUME_SILENT_MS = 10_000L
        const val GAME_GONE_AFTER_MS = 5_000L
    }
}
