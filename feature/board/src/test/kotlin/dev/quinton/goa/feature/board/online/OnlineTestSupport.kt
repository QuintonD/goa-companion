package dev.quinton.goa.feature.board.online

import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.DecisionClock
import dev.quinton.goa.core.online.Me
import dev.quinton.goa.core.online.MyGames
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.RoomCreated
import dev.quinton.goa.core.online.RoomSeat
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.fakeFactory
import dev.quinton.goa.feature.board.play.fakeSetup
import dev.quinton.goa.feature.board.play.online.GameConnection
import dev.quinton.goa.feature.board.play.online.GameConnector
import dev.quinton.goa.feature.board.play.online.OnlineGateway
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineIdentityStore
import dev.quinton.goa.feature.board.play.online.OnlineResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal const val CODE = "ABCD1234"
internal const val GAME = "game-1"

/** The app's seams, by hand. */
internal class FakeIdentities(var identity: OnlineIdentity? = OnlineIdentity("acc", "secret-token", "Ann")) :
    OnlineIdentityStore {
    override suspend fun load(): OnlineIdentity? = identity

    override suspend fun save(identity: OnlineIdentity) {
        this.identity = identity
    }

    override suspend fun clear() {
        identity = null
    }
}

internal class FakeGateway : OnlineGateway {
    var account: OnlineResult<AccountCreated> = OnlineResult.Ok(AccountCreated("acc", "new-token", "RECOVER-1"))
    var linked: OnlineResult<AccountCreated> = OnlineResult.Ok(AccountCreated("acc", "linked-token", "RECOVER-2"))
    var who: OnlineResult<Me> = OnlineResult.Ok(Me("acc", "Linked"))
    var renamed: OnlineResult<Me> = OnlineResult.Ok(Me("acc", "New name"))
    var deleted: OnlineResult<Unit> = OnlineResult.Ok(Unit)
    var created: OnlineResult<RoomCreated> = OnlineResult.Ok(RoomCreated(CODE))
    var joined: OnlineResult<RoomView> = OnlineResult.Ok(room())
    var left: OnlineResult<RoomView> = OnlineResult.Ok(room())
    var games: OnlineResult<MyGames> = OnlineResult.Ok(MyGames(emptyList()))
    val rooms = mutableListOf<CreateRoom>()
    val joins = mutableListOf<String>()
    val leaves = mutableListOf<String>()

    override suspend fun createAccount(name: String) = account

    override suspend fun link(recoveryCode: String) = linked

    override suspend fun me() = who

    override suspend fun rename(name: String) = renamed

    override suspend fun deleteMe() = deleted

    override suspend fun createRoom(room: CreateRoom): OnlineResult<RoomCreated> {
        rooms += room
        return created
    }

    override suspend fun join(code: String): OnlineResult<RoomView> {
        joins += code
        return joined
    }

    override suspend fun leave(code: String): OnlineResult<RoomView> {
        leaves += code
        return left
    }

    override suspend fun myGames() = games
}

internal class FakeConnection : GameConnection {
    private val frames = Channel<ServerMessage>(Channel.UNLIMITED)
    val sent = mutableListOf<ClientMessage>()
    var closed = false
        private set

    override val incoming: Flow<ServerMessage> = frames.receiveAsFlow()

    override suspend fun send(message: ClientMessage) {
        check(!closed) { "sent on a closed socket" }
        sent += message
    }

    override fun close() {
        closed = true
        frames.close()
    }

    fun feed(frame: ServerMessage) {
        frames.trySend(frame)
    }

    /** The socket dropping. */
    fun drop() = frames.close()

    fun fail(cause: Throwable) = frames.close(cause)

    val said: List<ClientMessage> get() = sent.filter { it !is ClientMessage.Ping }
}

internal class FakeConnector : GameConnector {
    val hellos = mutableListOf<ClientMessage.Hello>()
    val connections = mutableListOf<FakeConnection>()
    var onOpen: (FakeConnection) -> Unit = {}
    val last: FakeConnection get() = connections.last()

    override suspend fun open(hello: ClientMessage.Hello): GameConnection {
        hellos += hello
        return FakeConnection().also {
            connections += it
            onOpen(it)
        }
    }
}

/** A room with [seats] seats a side; [filled] are taken, in order, by Ann (the person), Bob, Cid and Dee. */
internal fun room(
    size: Int = 1,
    filled: Int = 1,
    hero: (Int) -> String? = { null },
    ready: (Int) -> Boolean = { false },
    status: RoomStatus = RoomStatus.OPEN,
    gameId: String? = null,
    ranked: Boolean = false,
): RoomView {
    val slots = Team.entries.flatMap { team -> (0 until size).map { team to it } }
    return RoomView(
        code = CODE,
        hostAccountId = "acc",
        mapId = "forgotten-island",
        length = GameLength.QUICK,
        teamSize = size,
        ranked = ranked,
        seats = slots.mapIndexed { i, (team, index) ->
            RoomSeat(
                team = team,
                index = index,
                accountId = if (i < filled) ACCOUNTS[i] else null,
                name = if (i < filled) NAMES[i] else null,
                heroId = if (i < filled) hero(i) else null,
                ready = i < filled && ready(i),
            )
        },
        status = status,
        expiresAt = 0,
        gameId = gameId,
    )
}

private val ACCOUNTS = listOf("acc", "acc-bob", "acc-cid", "acc-dee", "acc-eve", "acc-fay")
private val NAMES = listOf("Ann", "Bob", "Cid", "Dee", "Eve", "Fay")

/**
 * A scripted game and the frames a server would make of it (docs/ONLINE.md "Hidden information"): the seat's own
 * view, its own question with a ticket for an id, a stub for anyone else's.
 */
internal class GameFrames(private val you: String = "arien") {
    private val seats = setOf("arien", "brogan", "wasp", "tigerclaw")
    val session: GameSession = fakeFactory().newGame(fakeSetup(humans = seats))
    private var seq = 0L
    val players = session.setup.seats.mapIndexed { i, seat ->
        SeatPlayer(seat.heroId, "acc-$i", PERSON_NAMES.getOrElse(i) { seat.heroId }, seat.team)
    }

    fun start(gameId: String = GAME, ranked: Boolean = true) =
        ServerMessage.GameStart(gameId, session.setup, players, setOf(you), ranked)

    fun question(seat: String = you): Decision? = session.pending?.let { pending ->
        if (pending.seat == seat) pending.copy(id = pending.id + TICKET_BASE) else stub(pending)
    }

    fun table(
        have: Int = 0,
        decision: Decision? = question(),
        clock: DecisionClock? = null,
        presence: Map<String, Presence> = seats.associateWith { Presence.HERE },
        locked: List<String> = emptyList(),
        gameId: String = GAME,
    ) = ServerMessage.Table(
        gameId = gameId,
        seq = ++seq,
        view = session.view(you),
        decision = decision,
        eventsFrom = have,
        events = session.events.drop(have),
        locked = locked,
        clock = clock,
        presence = presence,
    )

    /** Answers on, as the others would, until [seat]'s or someone else's turn is the one [wanted]. */
    fun answerUntil(wanted: (Decision) -> Boolean) {
        var guard = 0
        while (guard++ < MAX_ANSWERS) {
            val pending = checkNotNull(session.pending) { "the game is over" }
            if (wanted(pending)) return
            session.answer(pending.id, pending.options.first())
        }
        error("never got there")
    }

    private fun stub(decision: Decision) = decision.copy(
        id = 0,
        prompt = "",
        sourceCardId = null,
        options = emptyList(),
        context = DecisionContext(),
    )

    private companion object {
        const val TICKET_BASE = 1_000L
        const val MAX_ANSWERS = 200
        val PERSON_NAMES = listOf("Ann", "Bob", "Cid", "Dee")
    }
}
