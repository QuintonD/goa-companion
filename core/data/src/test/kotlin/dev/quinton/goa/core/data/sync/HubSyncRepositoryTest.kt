package dev.quinton.goa.core.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.HubProblem
import dev.quinton.goa.core.data.HubSyncResult
import dev.quinton.goa.core.data.match.RoomMatchRepository
import dev.quinton.goa.core.data.player.RoomPlayerRepository
import dev.quinton.goa.core.data.rating.FakeEloConfigRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.datastore.sync.GroupSyncStore
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.HubToken
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
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
 * The hub client against a fake hub on a real socket (ADR-030 §4): the fake opens the phone's
 * sealed file with the group key, merges it into its own copy with [SyncMerge] and seals the
 * result back, as the real hub does. Errors become words on the Group screen, stored in
 * `group_sync`.
 */
@RunWith(RobolectricTestRunner::class)
class HubSyncRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val group = GroupCredentials.create("Friday nights")
    private var now = 10_000L
    private val clock = EditClock { now }

    private lateinit var db: GoaDatabase
    private lateinit var groups: DataStoreGroupRepository
    private lateinit var players: RoomPlayerRepository
    private lateinit var matches: RoomMatchRepository
    private lateinit var elo: FakeEloConfigRepository
    private lateinit var hubUrl: String

    /** The hub: holds the group's document, merges every upload in, answers with the result. */
    private inner class FakeHub(var doc: SyncDocument = SyncDocument.EMPTY, val key: GroupCredentials = group) :
        Dispatcher() {
        val uploads = mutableListOf<SyncDocument>()
        var contentType: String? = null

        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.method != "POST" || request.url.encodedPath != OkHttpHubSyncRepository.SYNC_PATH) {
                return MockResponse.Builder().code(404).build()
            }
            val token = request.headers["Authorization"]?.removePrefix("Bearer ")
            if (token == null || !HubToken.matches(key, token)) {
                return MockResponse.Builder().code(401).body("Wrong group key").build()
            }
            contentType = request.headers["Content-Type"]
            val incoming = SyncCodec.decodeFile(requireNotNull(request.body).toByteArray(), key)
            uploads += incoming
            doc = SyncMerge.merge(doc, incoming)
            return MockResponse.Builder()
                .code(200)
                .body(Buffer().write(SyncCodec.encodeFile(doc, key, createdAt = 1)))
                .build()
        }
    }

    private fun answer(code: Int, text: String = "") = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(code).body(text).build()
    }

    @Before
    fun setUp() {
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        hubUrl = "http://127.0.0.1:${server.port}"
        db = GoaDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        val store = GroupSyncStore(
            PreferenceDataStoreFactory.create(produceFile = { File(tmp.root, "group_sync.preferences_pb") }),
        )
        groups = DataStoreGroupRepository(store)
        players = RoomPlayerRepository(db, clock)
        matches = RoomMatchRepository(db, clock)
        elo = FakeEloConfigRepository(clock = { now })
        repository = OkHttpHubSyncRepository(
            groups = groups,
            store = store,
            sync = RoomSyncRepository(db, elo, clock),
            database = db,
            eloConfig = elo,
            clock = clock,
            client = HubHttpClient.create(connectSeconds = 2, ioSeconds = 1, callSeconds = 5),
        )
    }

    private lateinit var repository: OkHttpHubSyncRepository

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

    /** Joined with a hub; Room and the socket run on real threads. */
    private suspend fun joined(hub: String = hubUrl) = groups.join(group, hubUrl = hub)

    private suspend fun syncNow() = withContext(Dispatchers.IO) { repository.sync() }

    @Test
    fun aRoundTripSendsThisPhonesDataAndBringsTheGroupsBack() = runTest {
        val hubSide = listOf(player("ann"), player("bob"), player("cid"), player("dee"))
        val hub = FakeHub(SyncDocument(players = hubSide, matches = listOf(match("h1", listOf("ann"), listOf("bob")))))
        server.dispatcher = hub
        joined()
        listOf("ann", "bob", "cid", "dee").forEach { players.upsert(player(it)) }
        matches.upsert(match("p1", listOf("cid"), listOf("dee")))

        val result = syncNow()

        assertThat(result).isInstanceOf(HubSyncResult.Synced::class.java)
        assertThat((result as HubSyncResult.Synced).changes.matchesAdded).isEqualTo(1)
        // This phone now has the hub's match, and the hub has this phone's.
        assertThat(matches.observeMatches().first().map { it.id }).containsExactly("h1", "p1")
        assertThat(hub.doc.matches.map { it.id }).containsExactly("h1", "p1")
        assertThat(hub.uploads.single().matches.map { it.id }).containsExactly("p1")
        assertThat(hub.contentType).isEqualTo(SyncCodec.FILE_MIME_TYPE)
        val request = server.takeRequest()
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer ${HubToken.derive(group)}")
        val state = groups.state.first()
        assertThat(state.hubSyncedAt).isEqualTo(now)
        assertThat(state.hubError).isNull()
    }

    @Test
    fun thePhoneNamesItselfSoTheHubTellsPhonesApart() = runTest {
        server.dispatcher = FakeHub()
        joined()
        players.upsert(Player("zoe", "Zoë Ann", 1, 0, createdAt = 1))
        syncNow()
        val anonymous = server.takeRequest()
        val id = groups.state.first().deviceId
        assertThat(anonymous.headers[OkHttpHubSyncRepository.DEVICE_ID_HEADER]).isEqualTo(id)
        // No player chosen and no name: no name header (the hub says "A phone").
        assertThat(anonymous.headers[OkHttpHubSyncRepository.DEVICE_NAME_HEADER]).isNull()

        groups.setMe("zoe")
        syncNow()
        val named = server.takeRequest()
        // Header values are ASCII: the name travels percent-encoded as UTF-8.
        assertThat(named.headers[OkHttpHubSyncRepository.DEVICE_NAME_HEADER]).isEqualTo("Zo%C3%AB%20Ann")
        assertThat(OkHttpHubSyncRepository.deviceNameHeader("A+B & C")).isEqualTo("A%2BB%20%26%20C")
    }

    @Test
    fun aSecondRoundTripChangesNothingAndTheGroupsRatingConstantsArrive() = runTest {
        val hub = FakeHub()
        server.dispatcher = hub
        joined()
        players.upsert(player("ann"))
        syncNow()
        hub.doc = SyncMerge.merge(
            hub.doc,
            SyncDocument(eloConfig = StampedEloConfig(EloConfig(kEstablished = 25.0), 9)),
        )

        val again = syncNow() as HubSyncResult.Synced

        assertThat(again.changes.ratingConstantsChanged).isTrue()
        assertThat(elo.config.first().kEstablished).isEqualTo(25.0)
        val third = syncNow() as HubSyncResult.Synced
        assertThat(third.changes.isEmpty).isTrue()
    }

    @Test
    fun aHubWithAnotherKeyForTheGroupIsRefusedAndSaysSo() = runTest {
        server.dispatcher = FakeHub(key = GroupCredentials(group.groupId, ByteArray(32) { 9 }, group.name))
        joined()

        val result = syncNow() as HubSyncResult.Failed

        assertThat(result.error.problem).isEqualTo(HubProblem.WRONG_KEY)
        assertThat(result.error.message).contains("group key isn't accepted by the hub")
        assertThat(groups.state.first().hubError).isEqualTo(result.error)
    }

    @Test
    fun aVersionMismatchShowsTheHubsOwnWords() = runTest {
        server.dispatcher = answer(409, "The hub runs a newer version. Update the app.\n")
        joined()

        val error = (syncNow() as HubSyncResult.Failed).error

        assertThat(error.problem).isEqualTo(HubProblem.VERSION)
        assertThat(error.message).isEqualTo("The hub runs a newer version. Update the app.")
        assertThat(error.problem.transient).isFalse()
    }

    @Test
    fun aVersionMismatchWithoutWordsStillExplains() = runTest {
        server.dispatcher = answer(409)
        joined()

        val error = (syncNow() as HubSyncResult.Failed).error

        assertThat(error.message).contains("different versions")
    }

    @Test
    fun invalidDataRefusedByTheHubSaysWhy() = runTest {
        server.dispatcher = answer(422, "Match 2026-10-17: Ann is on both teams.")
        joined()

        val error = (syncNow() as HubSyncResult.Failed).error

        assertThat(error.problem).isEqualTo(HubProblem.INVALID_DATA)
        assertThat(
            error.message,
        ).isEqualTo("The hub refused this phone's data.\nMatch 2026-10-17: Ann is on both teams.")
    }

    @Test
    fun tooBigAndServerErrorsAndRedirectsAndWrongAddresses() = runTest {
        joined()
        val cases = mapOf(
            413 to HubProblem.TOO_BIG,
            500 to HubProblem.SERVER,
            503 to HubProblem.SERVER,
            302 to HubProblem.WRONG_ADDRESS,
            404 to HubProblem.WRONG_ADDRESS,
        )
        cases.forEach { (code, problem) ->
            server.dispatcher = answer(code)
            val error = (syncNow() as HubSyncResult.Failed).error
            assertThat(error.problem).isEqualTo(problem)
        }
        assertThat(HubProblem.SERVER.transient).isTrue()
        assertThat(HubProblem.TOO_BIG.transient).isFalse()
    }

    @Test
    fun aSlowHubTimesOut() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse.Builder().code(200).headersDelay(3, TimeUnit.SECONDS).build()
        }
        joined()

        val error = (syncNow() as HubSyncResult.Failed).error

        assertThat(error.problem).isEqualTo(HubProblem.TIMEOUT)
        assertThat(error.problem.transient).isTrue()
        assertThat(error.message).isEqualTo("The hub took too long to answer.")
    }

    @Test
    fun anUnreachableHubIsRetriedLaterAndKeepsTheLastGoodSync() = runTest {
        server.dispatcher = FakeHub()
        joined()
        syncNow()
        val syncedAt = groups.state.first().hubSyncedAt
        server.close()
        now += 60_000

        val error = (syncNow() as HubSyncResult.Failed).error

        assertThat(error.problem).isEqualTo(HubProblem.UNREACHABLE)
        assertThat(error.message).startsWith("Can't reach the hub")
        val state = groups.state.first()
        assertThat(state.hubSyncedAt).isEqualTo(syncedAt)
        assertThat(state.hubError).isEqualTo(error)
        assertThat(error.at).isEqualTo(now)
    }

    @Test
    fun aSuccessClearsTheLastError() = runTest {
        joined()
        server.dispatcher = answer(500)
        syncNow()
        assertThat(groups.state.first().hubError).isNotNull()
        server.dispatcher = FakeHub()

        syncNow()

        assertThat(groups.state.first().hubError).isNull()
    }

    @Test
    fun dataThisPhoneWouldRefuseIsNeverSent() = runTest {
        server.dispatcher = FakeHub()
        joined()
        listOf("ann", "bob").forEach { players.upsert(player(it)) }
        matches.upsert(match("bad", listOf("ann"), listOf("bob"), heroes = listOf("arien", "arien")))

        val error = (syncNow() as HubSyncResult.Failed).error

        assertThat(error.problem).isEqualTo(HubProblem.INVALID_DATA)
        assertThat(error.message).startsWith("This phone's data needs a fix first.")
        assertThat(error.message).contains("hero arien is listed twice")
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun withoutAGroupOrAHubNothingIsSent() = runTest {
        server.dispatcher = FakeHub()
        assertThat(syncNow()).isEqualTo(HubSyncResult.NotConfigured)
        groups.join(group)
        assertThat(syncNow()).isEqualTo(HubSyncResult.NotConfigured)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun anotherHubOrGroupStartsWithACleanStatus() = runTest {
        joined()
        server.dispatcher = answer(500)
        syncNow()
        assertThat(groups.state.first().hubError).isNotNull()

        groups.setHubUrl("https://other.example")
        assertThat(groups.state.first().hubError).isNull()

        groups.setHubUrl(hubUrl)
        server.dispatcher = FakeHub()
        syncNow()
        assertThat(groups.state.first().hubSyncedAt).isNotNull()
        groups.join(GroupCredentials.create("Other group"))
        assertThat(groups.state.first().hubSyncedAt).isNull()
    }

    @Test
    fun changesToSyncedDataAreSignalled() = runTest {
        withContext(Dispatchers.Default) {
            repository.localChanges.test {
                players.upsert(player("ann"))
                awaitItem()
                players.upsert(player("bob"))
                awaitItem()
                matches.upsert(match("m1", listOf("ann"), listOf("bob")))
                awaitItem()
                elo.update { it.copy(kEstablished = 30.0) }
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
