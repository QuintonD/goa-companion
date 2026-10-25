package dev.quinton.goa.web.group

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.SyncServerDefaults
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.data.sync.PrefsGroupRepository
import dev.quinton.goa.core.data.sync.RelayHttp
import dev.quinton.goa.core.data.sync.RelayNetworkException
import dev.quinton.goa.core.data.sync.RelayNetworkFailure
import dev.quinton.goa.core.data.sync.RelayRequest
import dev.quinton.goa.core.data.sync.RelayResponse
import dev.quinton.goa.core.data.sync.RelaySyncRepository
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SyncFileCodec
import dev.quinton.goa.core.domain.sync.SyncHash
import dev.quinton.goa.core.domain.sync.SyncServerToken
import dev.quinton.goa.core.domain.sync.SyncVectors
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * The browser's sync with the relay end to end, minus the network: the shared algorithm
 * (`RelaySyncRepository`, guarded on the JVM by `SyncServerRepositoryTest`) over the web's
 * IndexedDB-shaped stores and WebCrypto, against a fake relay that behaves like `server/relay`
 * (opaque blob, rev, token hash, `If-Match` / `If-None-Match`).
 */
class WebRelaySyncTest {
    private class Answer(override val code: Int, val headers: Map<String, String> = emptyMap(), val bytes: ByteArray = ByteArray(0)) :
        RelayResponse {
        override fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value

        override suspend fun body(max: Long): ByteArray? = bytes.takeIf { it.size <= max }
    }

    /** The relay: one blob per group, its rev and the hash of the token that registered it. */
    private class FakeRelay : RelayHttp {
        var blob: ByteArray? = null
        var rev = 0L
        var tokenHash: String? = null
        val requests = mutableListOf<RelayRequest>()
        var beforePut: suspend (RelayRequest) -> Answer? = { null }
        var always: Answer? = null
        var offline = false

        override suspend fun <T> exchange(request: RelayRequest, read: suspend (RelayResponse) -> T): T {
            if (offline) throw RelayNetworkException(RelayNetworkFailure.UNREACHABLE)
            requests += request
            return read(always ?: answer(request))
        }

        private suspend fun answer(request: RelayRequest): Answer {
            val token = request.headers["Authorization"]?.removePrefix("Bearer ")
            val tokenOk = token != null && hash(token) == tokenHash
            val etag = mapOf("ETag" to "\"$rev\"")
            return when (request.method) {
                "GET" -> when {
                    blob == null -> Answer(404)
                    !tokenOk -> Answer(401)
                    request.headers["If-None-Match"] == "\"$rev\"" -> Answer(304, etag)
                    else -> Answer(200, etag, blob!!)
                }

                else -> beforePut(request) ?: put(request, token, tokenOk)
            }
        }

        private fun put(request: RelayRequest, token: String?, tokenOk: Boolean): Answer {
            val ifMatch = request.headers["If-Match"]
            return when {
                token == null || (tokenHash != null && !tokenOk) -> Answer(401)
                request.headers["If-None-Match"] == "*" && blob != null -> Answer(412)
                ifMatch != null && ifMatch != "\"$rev\"" -> Answer(412)
                else -> {
                    blob = request.body
                    rev++
                    if (tokenHash == null) tokenHash = hash(token)
                    Answer(if (rev == 1L) 201 else 200, mapOf("ETag" to "\"$rev\""))
                }
            }
        }

        fun hash(token: String) = SyncHash.hex(SyncHash.sha256(token.encodeToByteArray()))

        val puts get() = requests.filter { it.method == "PUT" }
    }

    private class Browser(relay: RelayHttp) {
        var now = 10_000L
        private val clock = EditClock { now }
        val docs = GroupDocumentStore(MemoryKeyValueStore())
        val players = WebPlayerRepository(docs, clock)
        val matches = WebMatchRepository(docs, clock)
        val sync = WebSyncRepository(docs, clock)
        val groups = PrefsGroupRepository(WebGroupPrefsStore(MemoryKeyValueStore()), SyncServerDefaults(RELAY))
        val server = RelaySyncRepository(groups, sync, docs.changes, clock, relay, WebCryptoCipher)

        suspend fun join(group: GroupCredentials, url: String? = null) = groups.join(group, url)
    }

    private val group = GroupCredentials.create("Friday nights")

    private fun player(id: String) = Player(id, id.replaceFirstChar(Char::uppercase), 0xFF3FC1B0, 0, createdAt = 1)

    private fun match(id: String, red: String, blue: String, playedAt: Long = 10) = Match(
        id = id,
        createdAt = playedAt,
        playedAt = playedAt,
        status = MatchStatus.COMPLETED,
        mode = GameMode.QUICK,
        winner = Team.RED,
        participants = listOf(Participant(red, null, Team.RED), Participant(blue, null, Team.BLUE)),
    )

    @Test
    fun twoBrowsersMeetThroughTheRelayAndTheThirdSyncAsksOnlyWhetherAnythingChanged() = runTest {
        val relay = FakeRelay()
        val ann = Browser(relay).apply { join(group) }
        ann.players.upsert(player("ann"))
        ann.players.upsert(player("bob"))
        ann.matches.upsert(match("m1", "ann", "bob"))

        val first = ann.server.sync()
        assertIs<SyncServerResult.Synced>(first)
        assertTrue(first.uploaded)
        assertEquals(1L, first.rev)
        assertEquals("*", relay.puts.single().headers["If-None-Match"])
        assertEquals("Bearer ${SyncServerToken.derive(group)}", relay.puts.single().headers["Authorization"])

        val bob = Browser(relay).apply { join(group) }
        val joined = bob.server.sync()
        assertIs<SyncServerResult.Synced>(joined)
        assertEquals(1, joined.changes.matchesAdded)
        assertEquals(2, joined.changes.playersAdded)
        assertEquals(false, joined.uploaded)
        assertEquals(ann.sync.export(), bob.sync.export())
        assertEquals(1L, bob.groups.state.first().server.rev)

        // Nothing new on either side: a GET with If-None-Match, answered 304, and no upload.
        relay.requests.clear()
        val quiet = bob.server.sync()
        assertIs<SyncServerResult.Synced>(quiet)
        assertEquals("\"1\"", relay.requests.single().headers["If-None-Match"])

        bob.now = 20_000
        bob.matches.delete("m1")
        val deleted = bob.server.sync()
        assertIs<SyncServerResult.Synced>(deleted)
        assertTrue(deleted.uploaded)
        assertEquals("\"1\"", relay.puts.last().headers["If-Match"])
        val back = ann.server.sync()
        assertIs<SyncServerResult.Synced>(back)
        assertEquals(1, back.changes.matchesRemoved)
        assertTrue(ann.matches.observeMatches().first().isEmpty())
    }

    @Test
    fun aPhonesUploadOpensInTheBrowser() = runTest {
        val relay = FakeRelay()
        val phoneGroup = SyncVectors.group()
        relay.blob = SyncVectors.FILE.encodeToByteArray()
        relay.rev = 4
        relay.tokenHash = relay.hash(SyncServerToken.derive(phoneGroup))
        val web = Browser(relay).apply { join(phoneGroup) }
        val result = web.server.sync()
        assertIs<SyncServerResult.Synced>(result)
        assertEquals(SyncVectors.fileDocument().players, web.sync.export().players)
        assertEquals(SyncVectors.fileDocument().matches, web.sync.export().matches)
    }

    @Test
    fun anUploadThatLostTheRaceFetchesAgainMergesAndWins() = runTest {
        val relay = FakeRelay()
        val ann = Browser(relay).apply { join(group) }
        val bob = Browser(relay).apply { join(group) }
        ann.players.upsert(player("ann"))
        ann.server.sync()
        bob.players.upsert(player("bob"))
        // Just before Bob's upload, Cid's phone uploads its own data.
        var raced = false
        relay.beforePut = { _ ->
            if (!raced) {
                raced = true
                val cid = Browser(FakeRelay())
                cid.players.upsert(player("cid"))
                val merged = SyncFileCodec.decodeFile(relay.blob!!, group, WebCryptoCipher)
                relay.blob = SyncFileCodec.encodeFile(
                    dev.quinton.goa.core.domain.sync.SyncMerge.merge(merged, cid.sync.export()),
                    group,
                    1,
                    WebCryptoCipher,
                    withGroupName = false,
                )
                relay.rev++
            }
            null
        }
        val result = bob.server.sync()
        assertIs<SyncServerResult.Synced>(result)
        assertEquals(3L, result.rev)
        assertEquals(listOf("ann", "bob", "cid"), bob.sync.export().players.map { it.id })
        assertEquals(
            listOf("ann", "bob", "cid"),
            SyncFileCodec.decodeFile(relay.blob!!, group, WebCryptoCipher).players.map { it.id },
        )
    }

    @Test
    fun refusalsReadAsTheGroupScreenSaysThem() = runTest {
        val relay = FakeRelay()
        val web = Browser(relay).apply { join(group) }
        web.players.upsert(player("ann"))

        relay.always = Answer(401)
        val wrongKey = web.server.sync()
        assertIs<SyncServerResult.Failed>(wrongKey)
        assertEquals(SyncServerProblem.WRONG_KEY, wrongKey.error.problem)
        assertTrue(wrongKey.error.message.startsWith("The sync server holds this group under another key."))
        assertEquals(wrongKey.error.message, web.groups.state.first().server.error?.message)

        relay.always = Answer(429, mapOf("Retry-After" to "45"))
        val busy = web.server.sync()
        assertIs<SyncServerResult.Failed>(busy)
        assertEquals(SyncServerProblem.RATE_LIMITED, busy.error.problem)
        assertEquals(45L, busy.error.retryAfterSeconds)

        relay.always = Answer(503, mapOf("Retry-After" to "3600"), """{"error":"relay_full"}""".encodeToByteArray())
        val full = web.server.sync()
        assertIs<SyncServerResult.Failed>(full)
        assertEquals(SyncServerProblem.SERVER_FULL, full.error.problem)

        relay.always = Answer(302)
        val moved = web.server.sync()
        assertIs<SyncServerResult.Failed>(moved)
        assertEquals(SyncServerProblem.WRONG_ADDRESS, moved.error.problem)

        relay.always = null
        relay.offline = true
        val offline = web.server.sync()
        assertIs<SyncServerResult.Failed>(offline)
        assertEquals(SyncServerProblem.UNREACHABLE, offline.error.problem)
        assertEquals("Can't reach the sync server. Check the internet connection.", offline.error.message)

        relay.offline = false
        assertIs<SyncServerResult.Synced>(web.server.sync())
        assertNull(web.groups.state.first().server.error)
    }

    @Test
    fun dataEveryPhoneWouldRefuseIsNeverUploaded() = runTest {
        val relay = FakeRelay()
        val web = Browser(relay).apply { join(group) }
        web.players.upsert(player("ann"))
        web.matches.upsert(match("bad", "ann", "ghost")) // a player who isn't in the data
        val result = web.server.sync()
        assertIs<SyncServerResult.Failed>(result)
        assertEquals(SyncServerProblem.INVALID_DATA, result.error.problem)
        assertTrue(relay.puts.isEmpty())
    }

    @Test
    fun notInAGroupOrServerOffMeansNothingIsSent() = runTest {
        val relay = FakeRelay()
        val web = Browser(relay)
        assertEquals(SyncServerResult.NotConfigured, web.server.sync())
        web.join(group)
        web.groups.setServerSyncEnabled(false)
        assertEquals(SyncServerResult.NotConfigured, web.server.sync())
        assertTrue(relay.requests.isEmpty())
    }

    private companion object {
        const val RELAY = "https://relay.example.org:8443"
    }
}
