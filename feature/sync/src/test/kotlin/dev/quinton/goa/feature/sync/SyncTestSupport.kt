package dev.quinton.goa.feature.sync

import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.InMemorySyncData
import dev.quinton.goa.core.domain.sync.InMemorySyncNetwork
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.protocol.SyncApplyResult
import dev.quinton.goa.core.domain.sync.protocol.SyncTransport
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.sync.di.SessionTransport
import dev.quinton.goa.feature.sync.di.SyncTransportFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** A phone's sync data in memory, as the real repository merges it. */
class FakeSyncRepository(initial: SyncDocument = SyncDocument.EMPTY) : SyncRepository {
    val data = InMemorySyncData(initial)

    override suspend fun export(): SyncDocument = data.export()

    override suspend fun apply(incoming: SyncDocument): SyncApplyResult = data.apply(incoming)

    override suspend fun exportFile(group: GroupCredentials): ByteArray = SyncCodec.encodeFile(
        export(),
        group,
        createdAt = 1,
    )

    override suspend fun importFile(bytes: ByteArray, group: GroupCredentials?): SyncApplyResult =
        apply(SyncCodec.decodeFile(bytes, group))
}

/** Every phone's sessions go through one in-memory room. */
class RoomTransports(private val network: InMemorySyncNetwork, private val phone: String) : SyncTransportFactory {
    var opened = 0
        private set
    var shutdowns = 0
        private set

    override fun create(): SessionTransport {
        opened++
        val transport = network.transport(phone)
        return object : SessionTransport, SyncTransport by transport {
            override fun shutdown() {
                shutdowns++
            }
        }
    }
}

val TestGroup = GroupCredentials("6f1c2d6e-8a3b-4c5d-9e0f-112233445566", ByteArray(32) { 3 }, "Friday nights")

fun groupState(group: GroupCredentials? = TestGroup, me: String? = null, device: String = "phone") =
    GroupState(group, device, null, me, emptyList(), null)

fun person(id: String, name: String = id.replaceFirstChar(Char::uppercase), createdAt: Long = 1) =
    Player(id, name, 0xFF3FC1B0, avatarId = 0, createdAt = createdAt)

fun game(id: String, red: List<String>, blue: List<String>, updatedAt: Long = 1) = Match(
    id = id,
    createdAt = 1,
    playedAt = 1,
    status = MatchStatus.COMPLETED,
    mode = GameMode.QUICK,
    winner = Team.RED,
    participants = red.map { Participant(it, null, Team.RED) } + blue.map { Participant(it, null, Team.BLUE) },
    updatedAt = updatedAt,
)
