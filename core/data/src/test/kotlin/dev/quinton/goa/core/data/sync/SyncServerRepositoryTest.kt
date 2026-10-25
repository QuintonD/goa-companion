package dev.quinton.goa.core.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.SyncServerDefaults
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.data.match.RoomMatchRepository
import dev.quinton.goa.core.data.player.RoomPlayerRepository
import dev.quinton.goa.core.data.rating.FakeEloConfigRepository
import dev.quinton.goa.core.data.trivia.RoomTriviaRoundRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.datastore.sync.GroupSyncStore
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.domain.sync.SyncServerToken
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sync server client against a fake relay on a real socket (docs/SYNC-SERVER.md): the fake
 * stores one opaque blob per group with a rev, checks the token's hash and `If-Match` /
 * `If-None-Match` as the real server does, and never opens the blob. Each branch of the phone
 * algorithm, and each status the server can answer, ends as data on this phone and words in
 * `group_sync`.
 */
@RunWith(RobolectricTestRunner::class)
class SyncServerRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val group = GroupCredentials.create("Friday nights")
    private var now = 10_000L
    private val clock = EditClock { now }

    private lateinit var db: GoaDatabase
    private lateinit var groups: DataStoreGroupRepository
    private lateinit var players: RoomPlayerRepository
    private lateinit var matches: RoomMatchRepository
    private lateinit var rounds: RoomTriviaRoundRepository
    private lateinit var elo: FakeEloConfigRepository
    private lateinit var repository: OkHttpSyncServerRepository
    private lateinit var origin: String

    private val path get() = "/v1/groups/${group.groupId}/document"

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** The relay: one blob, its rev and the token hash set by the first upload; never opens the blob. */
    private inner class FakeRelay : Dispatcher() {
        var blob: ByteArray? = null
        var rev = 0L
        var tokenHash: String? = null
        val requests = mutableListOf<RecordedRequest>()

        /** Runs before a PUT is handled; a non-null answer replaces the relay's own. */
        var beforePut: (RecordedRequest) -> MockResponse? = { null }

        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            if (request.url.encodedPath != path) return answer(404)
            val token = request.headers["Authorization"]?.removePrefix("Bearer ")
            val tokenOk = token != null && sha256(token) == tokenHash
            return when (request.method) {
                "GET" -> when {
                    blob == null -> answer(404)

                    !tokenOk -> answer(401)

                    request.headers["If-None-Match"] == "\"$rev\"" ->
                        MockResponse.Builder().code(304).setHeader("ETag", "\"$rev\"").build()

                    else -> MockResponse.Builder().code(200).setHeader("ETag", "\"$rev\"")
                        .setHeader("Content-Type", SyncCodec.FILE_MIME_TYPE).body(Buffer().write(blob!!)).build()
                }

                "PUT" -> beforePut(request) ?: put(request, token, tokenOk)

                else -> answer(405)
            }
        }

        private fun put(request: RecordedRequest, token: String?, tokenOk: Boolean): MockResponse {
            val body = request.body?.toByteArray() ?: ByteArray(0)
            val ifNoneMatch = request.headers["If-None-Match"]
            val ifMatch = request.headers["If-Match"]
            return when {
                tokenHash != null && !tokenOk -> answer(401)

                token == null -> answer(401)

                body.isEmpty() -> answer(400)

                ifNoneMatch == null && ifMatch == null -> answer(428)

                ifNoneMatch == "*" && blob != null -> answer(412)

                ifMatch != null && ifMatch != "\"$rev\"" -> answer(412)

                else -> {
                    blob = body
                    rev++
                    if (tokenHash == null) tokenHash = sha256(token)
                    MockResponse.Builder().code(if (rev == 1L) 201 else 200).setHeader("ETag", "\"$rev\"").build()
                }
            }
        }

        /** Another phone of [key]'s group uploaded [doc]. */
        fun seed(doc: SyncDocument, key: GroupCredentials = group) {
            blob = SyncCodec.encodeFile(doc, key, createdAt = 1)
            rev++
            tokenHash = sha256(SyncServerToken.derive(key))
        }

        fun stored(): SyncDocument = SyncCodec.decodeFile(requireNotNull(blob), group)

        val puts get() = requests.filter { it.method == "PUT" }
    }

    private fun answer(code: Int) = MockResponse.Builder().code(code).build()

    private fun always(code: Int) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = answer(code)
    }

    @Before
    fun setUp() {
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        origin = "http://127.0.0.1:${server.port}"
        db = GoaDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        val store = GroupSyncStore(
            PreferenceDataStoreFactory.create(produceFile = { File(tmp.root, "group_sync.preferences_pb") }),
        )
        // The relay is the build's default server here, as oldboi is in the app.
        groups = DataStoreGroupRepository(store, SyncServerDefaults(origin, allowLocalHttp = true))
        players = RoomPlayerRepository(db, clock)
        matches = RoomMatchRepository(db, clock)
        rounds = RoomTriviaRoundRepository(db, clock)
        elo = FakeEloConfigRepository(clock = { now })
        repository = OkHttpSyncServerRepository(
            groups = groups,
            sync = RoomSyncRepository(db, elo, clock),
            database = db,
            eloConfig = elo,
            clock = clock,
            client = SyncServerHttpClient.create(connectSeconds = 2, ioSeconds = 1, callSeconds = 5),
        )
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun player(id: String) = Player(id, id.replaceFirstChar(Char::uppercase), 1, 0, createdAt = 1)

    private fun match(id: String, red: List<String>, blue: List<String>, heroes: List<String?> = emptyList()) = Match(
        id = id,
        createdAt = 5,
        playedAt = 5,
        status = MatchStatus.COMPLETED,
        mode = GameMode.QUICK,
        winner = Team.RED,
        participants = (red.map { it to Team.RED } + blue.map { it to Team.BLUE }).mapIndexed { i, (p, team) ->
            Participant(p, heroes.getOrNull(i), team)
        },
        updatedAt = 5,
    )

    private fun round(id: String, playerId: String) = TriviaRound(
        id = id,
        playerId = playerId,
        kind = TriviaRoundKind.CHALLENGE,
        playedAt = 100,
        length = 20,
        questions = 20,
        correct = 15,
        bestStreak = 6,
        answerMillis = 100_000,
        heroCount = 32,
        catalogVersion = 7,
        updatedAt = 100,
    )

    private val four = listOf("ann", "bob", "cid", "dee").map(::player)

    /** Room, DataStore and the socket run on real threads. */
    private suspend fun syncNow() = withContext(Dispatchers.IO) { repository.sync() }

    private suspend fun failed() = (syncNow() as SyncServerResult.Failed).error

    private suspend fun localMatches() = matches.observeMatches().first().map { it.id }

    @Test
    fun anEmptyServerGetsThisPhonesDataWithIfNoneMatch() = runTest {
        val relay = FakeRelay()
        server.dispatcher = relay
        groups.join(group)
        four.forEach { players.upsert(it) }
        matches.upsert(match("p1", listOf("ann", "bob"), listOf("cid", "dee")))

        val result = syncNow() as SyncServerResult.Synced

        assertThat(result.uploaded).isTrue()
        assertThat(result.rev).isEqualTo(1)
        assertThat(result.changes.isEmpty).isTrue()
        val (get, put) = relay.requests
        assertThat(get.method).isEqualTo("GET")
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.headers["If-None-Match"]).isEqualTo("*")
        assertThat(put.headers["If-Match"]).isNull()
        assertThat(put.headers["Content-Type"]).startsWith(SyncCodec.FILE_MIME_TYPE)
        relay.requests.forEach {
            assertThat(it.headers["Authorization"]).isEqualTo("Bearer ${SyncServerToken.derive(group)}")
        }
        // The relay holds a sealed copy of exactly this phone's data, and only its token's hash.
        assertThat(relay.stored().matches.map { it.id }).containsExactly("p1")
        assertThat(relay.stored().players).hasSize(4)
        assertThat(String(relay.blob!!)).doesNotContain("Ann")
        assertThat(String(relay.blob!!)).doesNotContain(group.name) // the name stays on the phones
        assertThat(relay.tokenHash).isEqualTo(sha256(SyncServerToken.derive(group)))
        val state = groups.state.first().server
        assertThat(state.syncedAt).isEqualTo(now)
        assertThat(state.rev).isEqualTo(1)
        assertThat(state.error).isNull()
    }

    @Test
    fun aNewerServerCopyIsMergedInAndNothingIsUploadedWhenItAlreadyHasEverything() = runTest {
        val relay = FakeRelay()
        relay.seed(
            SyncDocument(
                players = four,
                matches = listOf(match("h1", listOf("ann"), listOf("bob"))),
                triviaRounds = listOf(round("r1", "ann")),
                eloConfig = StampedEloConfig(EloConfig(kEstablished = 25.0), 9),
            ),
        )
        server.dispatcher = relay
        groups.join(group)

        val result = syncNow() as SyncServerResult.Synced

        assertThat(result.uploaded).isFalse()
        assertThat(result.rev).isEqualTo(1)
        assertThat(result.changes.matchesAdded).isEqualTo(1)
        assertThat(result.changes.playersAdded).isEqualTo(4)
        assertThat(result.changes.triviaRoundsAdded).isEqualTo(1)
        assertThat(result.changes.ratingConstantsChanged).isTrue()
        assertThat(localMatches()).containsExactly("h1")
        assertThat(rounds.observeRounds().first().map { it.id }).containsExactly("r1")
        assertThat(elo.config.first().kEstablished).isEqualTo(25.0)
        assertThat(relay.puts).isEmpty()
        assertThat(groups.state.first().server.rev).isEqualTo(1)
    }

    @Test
    fun bothSidesNewDataMergesBothWaysWithIfMatch() = runTest {
        val relay = FakeRelay()
        relay.seed(SyncDocument(players = four, matches = listOf(match("h1", listOf("ann"), listOf("bob")))))
        server.dispatcher = relay
        groups.join(group)
        four.forEach { players.upsert(it) }
        matches.upsert(match("p1", listOf("cid"), listOf("dee")))

        val result = syncNow() as SyncServerResult.Synced

        assertThat(result.uploaded).isTrue()
        assertThat(result.rev).isEqualTo(2)
        assertThat(relay.puts.single().headers["If-Match"]).isEqualTo("\"1\"")
        assertThat(relay.puts.single().headers["If-None-Match"]).isNull()
        assertThat(localMatches()).containsExactly("h1", "p1")
        assertThat(relay.stored().matches.map { it.id }).containsExactly("h1", "p1")
    }

    @Test
    fun aSecondSyncWithNothingNewOnlyReadsAFreshSaltNeverCounts() = runTest {
        val relay = FakeRelay()
        server.dispatcher = relay
        groups.join(group)
        four.forEach { players.upsert(it) }
        syncNow()
        now += 60_000

        val again = syncNow() as SyncServerResult.Synced

        assertThat(again.uploaded).isFalse()
        assertThat(again.changes.isEmpty).isTrue()
        assertThat(again.rev).isEqualTo(1)
        assertThat(relay.puts).hasSize(1)
        assertThat(relay.requests.map { it.method }).containsExactly("GET", "PUT", "GET").inOrder()
        // Nothing changed on either side since rev 1: a conditional GET, answered 304 with no body.
        assertThat(relay.requests.first().headers["If-None-Match"]).isNull()
        assertThat(relay.requests.last().headers["If-None-Match"]).isEqualTo("\"1\"")
        assertThat(groups.state.first().server.syncedAt).isEqualTo(now)
    }

    @Test
    fun aChangeOnEitherSideSinceTheLastSyncIsNeverSkipped() = runTest {
        val relay = FakeRelay()
        server.dispatcher = relay
        groups.join(group)
        four.forEach { players.upsert(it) }
        syncNow()

        // This phone changed: a plain GET (304 would hide nothing, but the change must go up).
        now += 1_000
        matches.upsert(match("p1", listOf("ann"), listOf("bob")))
        val mine = syncNow() as SyncServerResult.Synced
        assertThat(mine.uploaded).isTrue()
        assertThat(relay.requests[2].headers["If-None-Match"]).isNull()
        assertThat(relay.stored().matches.map { it.id }).containsExactly("p1")

        // Another phone changed the server's copy: the conditional GET gets the new document.
        val x1 = SyncDocument(matches = listOf(match("x1", listOf("cid"), listOf("dee"))))
        relay.seed(SyncMerge.merge(relay.stored(), x1))
        val theirs = syncNow() as SyncServerResult.Synced
        assertThat(relay.requests[4].headers["If-None-Match"]).isEqualTo("\"2\"")
        assertThat(theirs.changes.matchesAdded).isEqualTo(1)
        assertThat(theirs.uploaded).isFalse()
        assertThat(localMatches()).containsExactly("p1", "x1")
    }

    @Test
    fun aDeletionOnThisPhoneReachesTheServer() = runTest {
        val relay = FakeRelay()
        relay.seed(SyncDocument(players = four, matches = listOf(match("h1", listOf("ann"), listOf("bob")))))
        server.dispatcher = relay
        groups.join(group)
        syncNow()
        now += 1_000
        matches.delete("h1")

        val result = syncNow() as SyncServerResult.Synced

        assertThat(result.uploaded).isTrue()
        assertThat(relay.stored().matches).isEmpty()
        assertThat(relay.stored().deletions.map { it.id }).containsExactly("h1")
    }

    @Test
    fun anotherPhoneUploadingFirstMeans412AndTheNextTryMergesItsData() = runTest {
        val relay = FakeRelay()
        relay.seed(SyncDocument(players = four))
        server.dispatcher = relay
        groups.join(group)
        matches.upsert(match("p1", listOf("ann"), listOf("bob")))
        var raced = false
        relay.beforePut = {
            if (!raced) {
                raced = true
                // Another phone's upload lands between this phone's GET and PUT.
                relay.seed(SyncDocument(players = four, matches = listOf(match("x1", listOf("cid"), listOf("dee")))))
                answer(412)
            } else {
                null
            }
        }

        val result = syncNow() as SyncServerResult.Synced

        assertThat(result.uploaded).isTrue()
        assertThat(result.rev).isEqualTo(3)
        assertThat(relay.requests.map { it.method }).containsExactly("GET", "PUT", "GET", "PUT").inOrder()
        assertThat(relay.puts.last().headers["If-Match"]).isEqualTo("\"2\"")
        assertThat(localMatches()).containsExactly("p1", "x1")
        assertThat(relay.stored().matches.map { it.id }).containsExactly("p1", "x1")
        assertThat(result.changes.matchesAdded).isEqualTo(1)
    }

    @Test
    fun threeConflictsInARowStopAndWaitForAPerson() = runTest {
        val relay = FakeRelay()
        server.dispatcher = relay
        groups.join(group)
        players.upsert(player("ann"))
        relay.beforePut = { answer(412) }

        val error = failed()

        assertThat(error.problem).isEqualTo(SyncServerProblem.BUSY)
        assertThat(error.problem.transient).isFalse()
        assertThat(error.message).contains("Nothing was lost")
        assertThat(relay.requests.map { it.method }).containsExactly("GET", "PUT", "GET", "PUT", "GET", "PUT")
            .inOrder()
        assertThat(OkHttpSyncServerRepository.MAX_ATTEMPTS).isEqualTo(3)
        assertThat(groups.state.first().server.error).isEqualTo(error)
    }

    @Test
    fun aServerHoldingTheGroupUnderAnotherKeyIs401() = runTest {
        val relay = FakeRelay()
        relay.seed(SyncDocument.EMPTY, key = GroupCredentials(group.groupId, ByteArray(32) { 9 }, group.name))
        server.dispatcher = relay
        groups.join(group)

        val error = failed()

        assertThat(error.problem).isEqualTo(SyncServerProblem.WRONG_KEY)
        assertThat(error.problem.transient).isFalse()
        assertThat(error.message).contains("another key")
        assertThat(relay.puts).isEmpty()
    }

    @Test
    fun aDocumentFromAnotherAppVersionSaysWhichSideToUpdate() = runTest {
        val relay = FakeRelay()
        server.dispatcher = relay
        groups.join(group)

        relay.seed(SyncDocument(version = SyncDocument.VERSION + 1))
        val newer = failed()
        assertThat(newer.problem).isEqualTo(SyncServerProblem.VERSION)
        assertThat(newer.message).contains("newer version of the app. Update the app on this phone")

        relay.seed(SyncDocument(version = SyncDocument.VERSION - 1))
        val older = failed()
        assertThat(older.problem).isEqualTo(SyncServerProblem.VERSION)
        assertThat(older.message).contains("older version of the app")
        assertThat(relay.puts).isEmpty()
    }

    @Test
    fun aDocumentThatCantBeOpenedIsReportedNotOverwritten() = runTest {
        val relay = FakeRelay()
        server.dispatcher = relay
        groups.join(group)
        relay.seed(SyncDocument.EMPTY)
        relay.blob = "not a sync file".toByteArray()

        val error = failed()

        assertThat(error.problem).isEqualTo(SyncServerProblem.BAD_DOCUMENT)
        assertThat(relay.puts).isEmpty()
    }

    @Test
    fun tooBigIs413AndWaitsForAPerson() = runTest {
        val relay = FakeRelay()
        relay.beforePut = { answer(413) }
        server.dispatcher = relay
        groups.join(group)
        players.upsert(player("ann"))

        val error = failed()

        assertThat(error.problem).isEqualTo(SyncServerProblem.TOO_BIG)
        assertThat(error.problem.transient).isFalse()
        assertThat(error.message).contains("over 8 MB")
    }

    @Test
    fun serverErrorsRateLimitsTimeoutsAndNoConnectionAreTransient() = runTest {
        groups.join(group)
        mapOf(
            500 to SyncServerProblem.SERVER,
            503 to SyncServerProblem.SERVER,
            429 to SyncServerProblem.RATE_LIMITED,
        ).forEach { (code, problem) ->
            server.dispatcher = always(code)
            val error = failed()
            assertThat(error.problem).isEqualTo(problem)
            assertThat(error.problem.transient).isTrue()
        }

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse.Builder().code(200).headersDelay(3, TimeUnit.SECONDS).build()
        }
        val slow = failed()
        assertThat(slow.problem).isEqualTo(SyncServerProblem.TIMEOUT)
        assertThat(slow.problem.transient).isTrue()

        server.close()
        val offline = failed()
        assertThat(offline.problem).isEqualTo(SyncServerProblem.UNREACHABLE)
        assertThat(offline.problem.transient).isTrue()
        assertThat(offline.message).startsWith("Can't reach the sync server")
    }

    private fun refusing(code: Int, retryAfter: String, error: String) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(code)
            .setHeader("Retry-After", retryAfter)
            .setHeader("Content-Type", "application/json")
            .body("""{"error":"$error"}""")
            .build()
    }

    @Test
    fun retryAfterIsKeptForTheRetryAndAFullServerSaysSo() = runTest {
        groups.join(group)
        server.dispatcher = refusing(429, "120", "rate_limited")
        val limited = failed()
        assertThat(limited.problem).isEqualTo(SyncServerProblem.RATE_LIMITED)
        assertThat(limited.retryAfterSeconds).isEqualTo(120)

        server.dispatcher = refusing(503, "30", "store_unavailable")
        val unavailable = failed()
        assertThat(unavailable.problem).isEqualTo(SyncServerProblem.SERVER)
        assertThat(unavailable.retryAfterSeconds).isEqualTo(30)

        // The group cap: only a first upload can meet it.
        val relay = FakeRelay()
        relay.beforePut = {
            MockResponse.Builder().code(503).setHeader("Retry-After", "3600").body("""{"error":"relay_full"}""").build()
        }
        server.dispatcher = relay
        players.upsert(player("ann"))
        val full = failed()
        assertThat(full.problem).isEqualTo(SyncServerProblem.SERVER_FULL)
        assertThat(full.message).startsWith("The sync server is full")
        assertThat(full.retryAfterSeconds).isEqualTo(3600)
        assertThat(full.problem.transient).isTrue()
        assertThat(full.problem.needsAttention).isTrue()
        assertThat(SyncServerProblem.SERVER.needsAttention).isFalse()
        assertThat(groups.state.first().server.error?.problem).isEqualTo(SyncServerProblem.SERVER_FULL)
    }

    @Test
    fun redirectsAndSomethingThatIsntASyncServerAreTheWrongAddress() = runTest {
        groups.join(group)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse.Builder().code(302).setHeader("Location", "https://elsewhere.example/").build()
        }
        assertThat(failed().problem).isEqualTo(SyncServerProblem.WRONG_ADDRESS)
        // Any web server: "not found" for the document, and "not found" for the upload too.
        players.upsert(player("ann"))
        server.dispatcher = always(404)
        val error = failed()
        assertThat(error.problem).isEqualTo(SyncServerProblem.WRONG_ADDRESS)
        assertThat(error.problem.transient).isFalse()
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun aMissingEtagIsNotTrustedAsARev() = runTest {
        groups.join(group)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(200)
                .body(Buffer().write(SyncCodec.encodeFile(SyncDocument.EMPTY, group, createdAt = 1))).build()
        }
        assertThat(failed().problem).isEqualTo(SyncServerProblem.SERVER)
    }

    @Test
    fun dataEveryPhoneWouldRefuseIsNeverUploaded() = runTest {
        val relay = FakeRelay()
        server.dispatcher = relay
        groups.join(group)
        listOf("ann", "bob").forEach { players.upsert(player(it)) }
        matches.upsert(match("bad", listOf("ann"), listOf("bob"), heroes = listOf("arien", "arien")))

        val error = failed()

        assertThat(error.problem).isEqualTo(SyncServerProblem.INVALID_DATA)
        assertThat(error.message).contains("hero arien is listed twice")
        assertThat(relay.puts).isEmpty()
    }

    @Test
    fun withoutAGroupOrWithTheServerOffNothingIsSent() = runTest {
        server.dispatcher = FakeRelay()
        assertThat(syncNow()).isEqualTo(SyncServerResult.NotConfigured)
        groups.join(group)
        groups.setServerSyncEnabled(false)
        assertThat(syncNow()).isEqualTo(SyncServerResult.NotConfigured)
        assertThat(server.requestCount).isEqualTo(0)
        groups.setServerSyncEnabled(true)
        assertThat(syncNow()).isInstanceOf(SyncServerResult.Synced::class.java)
    }

    @Test
    fun aSuccessClearsTheLastErrorAndKeepsItsTimeOnFailure() = runTest {
        groups.join(group)
        server.dispatcher = FakeRelay()
        syncNow()
        val syncedAt = groups.state.first().server.syncedAt
        now += 60_000
        server.dispatcher = always(503)
        val error = failed()
        val state = groups.state.first().server
        assertThat(state.syncedAt).isEqualTo(syncedAt)
        assertThat(state.error).isEqualTo(error)
        assertThat(error.at).isEqualTo(now)

        server.dispatcher = FakeRelay()
        syncNow()
        assertThat(groups.state.first().server.error).isNull()
    }

    @Test
    fun changesToSyncedDataAreSignalled() = runTest {
        withContext(Dispatchers.Default) {
            repository.localChanges.test {
                // Room registers the invalidation observer after collection starts, asynchronously
                // (no initial emission to wait for): a write before that is never signalled, which is
                // what made this test fail when run with the rest of the class.
                delay(OBSERVER_SETTLE_MS)
                players.upsert(player("ann"))
                awaitItem()
                players.upsert(player("bob"))
                awaitItem()
                matches.upsert(match("m1", listOf("ann"), listOf("bob")))
                awaitItem()
                rounds.add(round("r1", "ann"))
                awaitItem()
                elo.update { it.copy(kEstablished = 30.0) }
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun theMergeIsTheSameAsASyncFileImportsAndTheServerConverges() = runTest {
        val relay = FakeRelay()
        val remote = SyncDocument(players = four, matches = listOf(match("h1", listOf("ann"), listOf("bob"))))
        relay.seed(remote)
        server.dispatcher = relay
        groups.join(group)
        players.upsert(player("eve"))
        val local = RoomSyncRepository(db, elo, clock).export()

        syncNow()

        // What the server and this phone now hold is SyncMerge.merge(local, remote), nothing else.
        val expected = SyncMerge.merge(local, remote)
        assertThat(relay.stored()).isEqualTo(expected)
        assertThat(RoomSyncRepository(db, elo, clock).export()).isEqualTo(expected)
    }
}

/** Time for Room to register a flow's invalidation observer (real time: Dispatchers.Default). */
private const val OBSERVER_SETTLE_MS = 500L
