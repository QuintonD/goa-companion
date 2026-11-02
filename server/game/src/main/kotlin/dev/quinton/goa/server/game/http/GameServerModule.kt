package dev.quinton.goa.server.game.http

import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.ApiError
import dev.quinton.goa.core.online.CreateAccount
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.HealthInfo
import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.LinkAccount
import dev.quinton.goa.core.online.Me
import dev.quinton.goa.core.online.MyGames
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.Rename
import dev.quinton.goa.core.online.RoomCreated
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.server.game.GameServerConfig
import dev.quinton.goa.server.game.lobby.Accounts
import dev.quinton.goa.server.game.lobby.Lobby
import dev.quinton.goa.server.game.lobby.LobbyError
import dev.quinton.goa.server.game.lobby.LobbyRefused
import dev.quinton.goa.server.game.room.GameRooms
import dev.quinton.goa.server.game.room.LeaderboardSource
import dev.quinton.goa.server.game.store.AccountRow
import dev.quinton.goa.server.game.store.SqliteGameStore
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.utils.io.readBuffer
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import org.slf4j.LoggerFactory

/** The path Funnel publishes this server under; it may arrive with or without it (docs/ONLINE.md). */
const val PUBLIC_MOUNT = "/game"

private val log = LoggerFactory.getLogger("goa-game")
private const val MAX_BODY_BYTES = 4 * 1024
private const val STORE_RETRY_SECONDS = "30"
private const val PING_SECONDS = 20L
private const val PONG_TIMEOUT_SECONDS = 45L
private const val SWEEP_MILLIS = 60_000L
private const val RETENTION_FIRST_MILLIS = 10 * 60_000L
private const val RETENTION_EVERY_MILLIS = 24 * 60 * 60_000L
private const val PLAY_FRAME_LIMIT = MAX_FRAME_BYTES.toLong()
internal const val OTHER_PATH = "(other)"
private val ROOM_ACTION = Regex("/v1/rooms/[^/]+/(join|leave)")

private val REQUEST_STARTED = AttributeKey<Long>("goa.request.started")

/**
 * One log line per request: method, path template, status, duration. Never bodies, tokens, codes or client
 * addresses. Written when the response is sent, because StatusPages answers a refusal only after the rest
 * of the pipeline has unwound. Healthy health checks (Docker polls every 30 s) log at debug.
 */
private val RequestLog = createApplicationPlugin("RequestLog") {
    onCall { call -> call.attributes.put(REQUEST_STARTED, System.nanoTime()) }
    on(ResponseSent) { call ->
        val status = call.response.status()?.value ?: HttpStatusCode.InternalServerError.value
        val millis = (System.nanoTime() - call.attributes[REQUEST_STARTED]) / 1_000_000
        val path = template(call.request.path())
        val line = "${call.request.httpMethod.value} $path $status ${millis}ms"
        if (path == OnlineRoutes.HEALTH && status == HttpStatusCode.OK.value) log.debug(line) else log.info(line)
    }
}

/** What the routes and the sockets share. */
internal class GameServices(
    val config: GameServerConfig,
    val store: SqliteGameStore,
    val accounts: Accounts,
    val lobby: Lobby,
    val rooms: GameRooms,
    val leaderboard: LeaderboardSource,
    val hub: SocketHub,
    val catalogVersion: Int,
    val socketFrames: RateLimiter,
    val accountLimiter: RateLimiter,
    val clock: () -> Long,
) {
    /** Sockets open now, counted from before their Hello, as each takes memory from that moment on. */
    val sockets = AtomicInteger()

    fun draining(): Boolean = Files.exists(config.drainFile)
}

/**
 * The server's outside. The streams join in `Main.kt`: [rooms] is the room core, which takes the ratings'
 * `MatchMapper`, and [leaderboard] the ratings. [store] belongs to the caller, who closes it.
 */
fun Application.gameServerModule(
    config: GameServerConfig,
    store: SqliteGameStore,
    engine: GameEngine,
    rooms: GameRooms,
    leaderboard: LeaderboardSource,
    clock: () -> Long = System::currentTimeMillis,
) {
    val hub = SocketHub()
    val services = GameServices(
        config = config,
        store = store,
        accounts = Accounts(store, clock),
        lobby = Lobby(
            store = store,
            engine = engine,
            rooms = rooms,
            maxRooms = config.maxRooms,
            draining = { Files.exists(config.drainFile) },
            clock = clock,
            onChange = hub::roomChanged,
            maxOpenRooms = config.maxOpenRooms,
        ),
        rooms = rooms,
        leaderboard = leaderboard,
        hub = hub,
        catalogVersion = engine.data.catalog.dataVersion,
        socketFrames = RateLimiter(config.socketFramesPerMinute, Int.MAX_VALUE),
        accountLimiter = RateLimiter(config.accountsPerClientPerHour, config.accountsGlobalPerHour, perMinutes = 60),
        clock = clock,
    )
    val limiter = RateLimiter(config.perClientPerMinute, config.globalPerMinute)
    val upgrades = RateLimiter(config.upgradesPerClientPerMinute, config.upgradesGlobalPerMinute)

    install(RequestLog)

    intercept(ApplicationCallPipeline.Monitoring) {
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header("X-Robots-Tag", "noindex")
        // The board may be kept and asked about with its ETag; nothing else may be kept at all.
        val board = template(call.request.path()) == OnlineRoutes.LEADERBOARD
        call.response.header(HttpHeaders.CacheControl, if (board) "no-cache" else "no-store")
        allowOrigin(call, config.origins)
    }

    intercept(ApplicationCallPipeline.Plugins) {
        // CORS preflights touch no data and carry no token: answered before the rate limiter, so a
        // browser's game isn't refused for asking permission first.
        if (call.request.httpMethod == HttpMethod.Options) {
            call.preflight(config.origins)
            finish()
            return@intercept
        }
        val path = template(call.request.path())
        // Health is exempt: Docker and a deploy's drain read it, and neither may be locked out by a flood.
        val wait = when (path) {
            OnlineRoutes.HEALTH -> null
            OnlineRoutes.PLAY -> upgrades.acquire(clientKey(call))
            else -> limiter.acquire(clientKey(call))
        }
        if (wait != null) {
            call.response.header(HttpHeaders.RetryAfter, wait.toString())
            call.error(HttpStatusCode.TooManyRequests, "rate_limited")
            finish()
            return@intercept
        }
        // Browsers always say where a socket comes from; native clients say nothing.
        val origin = call.request.headers[HttpHeaders.Origin]
        if (path == OnlineRoutes.PLAY && origin != null && origin !in config.origins) {
            call.error(HttpStatusCode.Forbidden, "origin_refused")
            finish()
        }
    }

    install(WebSockets) {
        pingPeriod = PING_SECONDS.seconds
        timeout = PONG_TIMEOUT_SECONDS.seconds
        maxFrameSize = PLAY_FRAME_LIMIT
        masking = false
    }

    install(StatusPages) {
        exception<LobbyRefused> { call, refused -> call.refuse(refused.error) }
        exception<BadBody> { call, _ -> call.error(HttpStatusCode.BadRequest, "bad_request") }
        exception<TooLarge> { call, _ ->
            // The rest of an oversized body is not read; close rather than drain it.
            call.response.header(HttpHeaders.Connection, "close")
            call.error(HttpStatusCode.PayloadTooLarge, "too_large")
        }
        exception<SQLException> { call, cause ->
            log.warn("store unavailable: {}", cause.javaClass.simpleName)
            call.response.header(HttpHeaders.RetryAfter, STORE_RETRY_SECONDS)
            call.error(HttpStatusCode.ServiceUnavailable, "store_unavailable")
        }
        exception<Throwable> { call, cause ->
            log.error("unexpected failure: {}", cause.javaClass.name)
            call.error(HttpStatusCode.InternalServerError, "internal")
        }
        status(HttpStatusCode.NotFound) { call, _ -> call.error(HttpStatusCode.NotFound, "not_found") }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.error(HttpStatusCode.MethodNotAllowed, "method_not_allowed")
        }
    }

    launch { sweepOldEveryDay(services) }

    routing {
        api(services)
        route(PUBLIC_MOUNT) { api(services) }
    }

    launch {
        while (isActive) {
            delay(SWEEP_MILLIS.milliseconds)
            @Suppress("TooGenericExceptionCaught") // A bad row must not end the sweeps for good.
            try {
                services.lobby.sweep()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                log.warn("room sweep failed: {}", failure.javaClass.simpleName)
            }
        }
    }
}

/** First a little after starting, so that a deploy doesn't begin with it; then daily. */
private suspend fun sweepOldEveryDay(services: GameServices) {
    delay(RETENTION_FIRST_MILLIS.milliseconds)
    while (true) {
        @Suppress("TooGenericExceptionCaught") // A failed sweep can wait for tomorrow's.
        try {
            val swept = services.store.sweepOld(services.clock())
            log.info("Old data swept: {}", swept)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            log.warn("sweep of old data failed: {}", failure.javaClass.simpleName)
        }
        delay(RETENTION_EVERY_MILLIS.milliseconds)
    }
}

private fun Route.api(services: GameServices) {
    health(services)
    accounts(services)
    rooms(services)
    leaderboard(services)
    webSocket(OnlineRoutes.PLAY) { playWithinTheCap(services) }
}

private fun Route.health(services: GameServices) {
    get(OnlineRoutes.HEALTH) {
        val ok = services.store.isAvailable()
        val health = HealthInfo(
            ok = ok,
            protocol = OnlineProtocol.VERSION,
            rulesBuild = services.config.rulesBuild,
            status = if (services.draining()) "draining" else "open",
            // A deploy waits for this to reach 0: running games only. An open room can't start during a
            // drain and survives the restart, so waiting for it would only hold the deploy up.
            rooms = if (ok) services.rooms.runningCount else 0,
            maxRooms = services.config.maxRooms,
            openRooms = if (ok) services.lobby.openCount() else 0,
        )
        val status = if (ok) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respondJson(HealthInfo.serializer(), health, status)
    }
}

private fun Route.accounts(services: GameServices) {
    post(OnlineRoutes.ACCOUNTS) {
        val wait = services.accountLimiter.acquire(clientKey(call))
        if (wait != null) {
            call.response.header(HttpHeaders.RetryAfter, wait.toString())
            return@post call.error(HttpStatusCode.TooManyRequests, "rate_limited")
        }
        val created = services.accounts.create(call.body(CreateAccount.serializer()).name)
        call.respondJson(AccountCreated.serializer(), created, HttpStatusCode.Created)
    }
    post(OnlineRoutes.ACCOUNTS_LINK) {
        val linked = services.accounts.link(call.body(LinkAccount.serializer()).recoveryCode)
        call.respondJson(AccountCreated.serializer(), linked)
    }
    get(OnlineRoutes.ME) {
        val account = call.account(services) ?: return@get
        call.respondJson(Me.serializer(), Me(account.id, account.name))
    }
    patch(OnlineRoutes.ME) {
        val account = call.account(services) ?: return@patch
        val renamed = services.accounts.rename(account, call.body(Rename.serializer()).name)
        services.lobby.openRoomsOf(account.id).forEach { services.lobby.announce(it) }
        call.respondJson(Me.serializer(), Me(renamed.id, renamed.name))
    }
    delete(OnlineRoutes.ME) {
        val account = call.account(services) ?: return@delete
        services.lobby.openRoomsOf(account.id).forEach { services.lobby.leave(account.id, it) }
        services.rooms.gamesOf(account.id).forEach { services.rooms.leave(it, account.id) }
        services.accounts.delete(account)
        services.hub.disconnect(account.id)
        call.respondBytes(ByteArray(0), status = HttpStatusCode.NoContent)
    }
    get(OnlineRoutes.MY_GAMES) {
        val account = call.account(services) ?: return@get
        call.respondJson(MyGames.serializer(), MyGames(services.lobby.myGames(account.id)))
    }
}

private fun Route.rooms(services: GameServices) {
    post(OnlineRoutes.ROOMS) {
        val account = call.account(services) ?: return@post
        val room = services.lobby.create(account.id, call.body(CreateRoom.serializer()), clientKey(call))
        call.respondJson(RoomCreated.serializer(), RoomCreated(room.code), HttpStatusCode.Created)
    }
    post(OnlineRoutes.join("{code}")) {
        val account = call.account(services) ?: return@post
        call.respondRoom(services.lobby.join(account.id, call.parameters["code"].orEmpty()))
    }
    post(OnlineRoutes.leave("{code}")) {
        val account = call.account(services) ?: return@post
        call.respondRoom(services.lobby.leave(account.id, call.parameters["code"].orEmpty()))
    }
}

private fun Route.leaderboard(services: GameServices) {
    get(OnlineRoutes.LEADERBOARD) {
        val board = services.leaderboard.snapshot()
        val etag = "\"${boardTag(board)}\""
        call.response.header(HttpHeaders.ETag, etag)
        val asked = call.request.headers[HttpHeaders.IfNoneMatch].orEmpty()
            .split(',').map { it.trim().removePrefix("W/") }
        if (etag in asked || "*" in asked) {
            call.respondBytes(ByteArray(0), status = HttpStatusCode.NotModified)
        } else {
            call.respondJson(LeaderboardSnapshot.serializer(), board)
        }
    }
}

/** What the board says, whenever it was made: a rename or a deleted name changes it, the log's hash doesn't. */
private fun boardTag(board: LeaderboardSnapshot): String {
    val text = OnlineProtocol.json.encodeToString(LeaderboardSnapshot.serializer(), board.copy(generatedAt = 0))
    return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

private suspend fun ApplicationCall.respondRoom(room: RoomView) = respondJson(RoomView.serializer(), room)

private suspend fun <T> ApplicationCall.respondJson(
    serializer: KSerializer<T>,
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respondText(OnlineProtocol.json.encodeToString(serializer, value), ContentType.Application.Json, status)

internal suspend fun ApplicationCall.error(status: HttpStatusCode, code: String) =
    respondJson(ApiError.serializer(), ApiError(code), status)

private suspend fun ApplicationCall.refuse(error: LobbyError) {
    error.retryAfterSeconds?.let { response.header(HttpHeaders.RetryAfter, it.toString()) }
    error(HttpStatusCode.fromValue(error.status), error.code)
}

/** The account behind the bearer token; otherwise answers 401 and returns null. */
private suspend fun ApplicationCall.account(services: GameServices): AccountRow? {
    val header = request.headers[HttpHeaders.Authorization]?.trim()
    val token = header?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring("Bearer ".length)?.trim()
    val account = token?.let { services.accounts.authenticate(it) }
    if (account == null) {
        response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"goa-game\"")
        error(HttpStatusCode.Unauthorized, "unauthorized")
    }
    return account
}

private class BadBody : Exception()

private class TooLarge : Exception()

/** The request's JSON, limited on the bytes actually received, whatever Content-Length claimed. */
private suspend fun <T> ApplicationCall.body(serializer: DeserializationStrategy<T>): T {
    val declared = request.contentLength() ?: 0
    val bytes = if (declared >
        MAX_BODY_BYTES
    ) {
        null
    } else {
        receiveChannel().readBuffer(MAX_BODY_BYTES + 1L).readByteArray()
    }
    if (bytes == null || bytes.size > MAX_BODY_BYTES) throw TooLarge()
    return try {
        OnlineProtocol.json.decodeFromString(serializer, bytes.decodeToString())
    } catch (_: IllegalArgumentException) {
        // Every kotlinx.serialization failure is one of these.
        throw BadBody()
    }
}

/** The last `X-Forwarded-For` hop (added by the proxy in front of us), else the peer address. */
internal fun clientKey(call: ApplicationCall): String {
    val forwarded = call.request.headers.getAll(HttpHeaders.XForwardedFor)
        ?.flatMap { it.split(',') }
        ?.map { it.trim() }
        ?.lastOrNull { it.isNotEmpty() }
    return clientGroup(forwarded ?: call.request.local.remoteAddress)
}

/**
 * IPv6 clients by /64: one home or phone gets a whole /64, so per address it could never run out. A mapped
 * IPv4 address counts as that IPv4 address. Anything else stays as it is.
 */
internal fun clientGroup(address: String): String {
    val literal = address.removeSurrounding("[", "]").substringBefore('%')
    if (':' !in literal || !IPV6_LITERAL.matches(literal)) return address
    // A literal only: getByName would go to DNS for a name, and this comes from a header.
    val parsed = runCatching { InetAddress.getByName(literal) }.getOrNull() ?: return address
    if (parsed !is Inet6Address) return parsed.hostAddress
    return parsed.address.take(IPV6_PREFIX_BYTES).joinToString("") { "%02x".format(it) } + "/64"
}

private val IPV6_LITERAL = Regex("[0-9A-Fa-f:.]+")
private const val IPV6_PREFIX_BYTES = 8

/** The path for logs and the CORS check: never a room code, whichever way the path arrived. */
internal fun template(rawPath: String): String {
    val path = rawPath.removePrefix(PUBLIC_MOUNT)
    return when {
        path in KNOWN_PATHS -> path
        ROOM_ACTION.matches(path) -> "${OnlineRoutes.ROOMS}/{code}/${path.substringAfterLast('/')}"
        else -> OTHER_PATH
    }
}

private val KNOWN_PATHS = setOf(
    OnlineRoutes.HEALTH,
    OnlineRoutes.ACCOUNTS,
    OnlineRoutes.ACCOUNTS_LINK,
    OnlineRoutes.ME,
    OnlineRoutes.MY_GAMES,
    OnlineRoutes.ROOMS,
    OnlineRoutes.PLAY,
    OnlineRoutes.LEADERBOARD,
)
