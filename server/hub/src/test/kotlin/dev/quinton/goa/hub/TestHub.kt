package dev.quinton.goa.hub

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.hub.config.HubPaths
import dev.quinton.goa.hub.push.PushTransport
import dev.quinton.goa.hub.web.DeviceCookie
import dev.quinton.goa.hub.web.hubModule
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

/** A wall clock tests move by hand; elapsed time follows it. */
class FakeClock(start: Long = 1_760_000_000_000L, override val zone: ZoneId = ZoneId.of("UTC")) : HubClock {
    private val now = AtomicLong(start)

    override fun nowMillis(): Long = now.get()

    override fun elapsedMillis(): Long = now.get()

    fun advance(ms: Long) {
        now.addAndGet(ms)
    }

    fun set(ms: Long) = now.set(ms)
}

/** A group with a fixed key, so tests can make invites and tokens. */
val TestGroup = GroupCredentials("2b1e7a3c-4d5e-4f60-8a9b-0c1d2e3f4a5b", ByteArray(32) { it.toByte() }, "Table Night")
val OtherGroup =
    GroupCredentials("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d", ByteArray(32) { (it + 7).toByte() }, "Elsewhere")

const val HUB_URL = "https://goa.example.org"
private const val HTTP_CREATED = 201

fun testHub(
    dir: Path,
    clock: FakeClock = FakeClock(),
    joined: Boolean = true,
    transport: PushTransport = PushTransport { _, _, _ -> HTTP_CREATED },
): Hub =
    Hub(HubPaths(dir), clock, pushTransport = transport).also { hub ->
        if (joined) {
            hub.membership.join(TestGroup, clock.nowMillis())
            hub.config.update { it.copy(publicUrl = HUB_URL) }
        }
    }

/** Runs a Ktor test application over [hub]. */
fun hubTest(hub: Hub, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application { hubModule(hub) }
    block()
}

/** A client that doesn't follow redirects, so tests see 302s and Set-Cookie. */
fun ApplicationTestBuilder.plainClient(): HttpClient = createClient { followRedirects = false }

/** Signs a browser in through `POST /join`; returns its cookie token. */
suspend fun HttpClient.signIn(invite: String = GroupInvite.format(TestGroup, HUB_URL)): String {
    val response = submitForm("/join", parameters { append("invite", invite) }) {
        header(HttpHeaders.Origin, HUB_URL)
    }
    val cookie = response.headers.getAll(HttpHeaders.SetCookie).orEmpty().first {
        it.startsWith(DeviceCookie.NAME + "=")
    }
    return cookie.substringAfter("=").substringBefore(";")
}

fun cookie(token: String) = "${DeviceCookie.NAME}=$token"

fun player(id: String, name: String = id, createdAt: Long = 0, updatedAt: Long = createdAt) =
    Player(id = id, name = name, color = 0xFF3FC1B0, avatarId = 3, createdAt = createdAt, updatedAt = updatedAt)

fun match(
    id: String,
    red: List<String>,
    blue: List<String>,
    updatedAt: Long = 0,
    playedAt: Long = 0,
    notes: String = "",
): Match {
    val seats = red.map { it to Team.RED } + blue.map { it to Team.BLUE }
    return Match(
        id = id,
        createdAt = playedAt,
        playedAt = playedAt,
        status = MatchStatus.COMPLETED,
        mode = GameMode.QUICK,
        winner = Team.RED,
        notes = notes,
        participants = seats.mapIndexed { i, (p, t) -> Participant(p, "h$i", t) },
        updatedAt = updatedAt,
    )
}
