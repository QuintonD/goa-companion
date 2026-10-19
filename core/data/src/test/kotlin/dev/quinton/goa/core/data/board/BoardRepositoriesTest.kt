package dev.quinton.goa.core.data.board

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.catalog.RepoAssets
import dev.quinton.goa.core.datastore.board.BoardStore
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssetBoardMapRepositoryTest {
    @Test
    fun `loads every bundled map in index order, once`() = runTest {
        val ids = BoardMapLoader.readIndex(RepoAssets.assets)
        assertThat(ids).isNotEmpty()
        val repo = AssetBoardMapRepository(RepoAssets.assets, StandardTestDispatcher(testScheduler))
        repo.maps.test {
            val maps = awaitItem()
            assertThat(maps.map { it.id }).containsExactlyElementsIn(ids).inOrder()
            maps.forEach { map ->
                assertThat(map.lanes).isNotEmpty()
                assertThat(map.spaces).isNotEmpty()
                assertThat(map.spaces.map { it.hex }.toSet()).hasSize(map.spaces.size)
                val zones = map.zones.map { it.id }.toSet()
                assertThat(zones).containsAtLeastElementsIn(map.spaces.map { it.zone }.toSet())
                map.lanes.forEach { lane ->
                    assertThat(zones).containsAtLeastElementsIn(lane.zones)
                    assertThat(lane.zones).contains(lane.startingBattleZone)
                }
            }
            awaitComplete()
        }
        assertThat(repo.get()).isSameInstanceAs(repo.get())
    }

    @Test
    fun `no index means no maps, reported`() = runTest {
        val errors = mutableListOf<Throwable>()
        val repo = AssetBoardMapRepository(
            assets = { path -> throw FileNotFoundException(path) },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onError = { errors += it },
        )
        assertThat(repo.get()).isEmpty()
        assertThat(errors).hasSize(1)
    }

    @Test
    fun `an unreadable map is left out, the others load`() = runTest {
        val first = BoardMapLoader.readIndex(RepoAssets.assets).first()
        val errors = mutableListOf<Throwable>()
        val repo = AssetBoardMapRepository(
            assets = { path ->
                when (path) {
                    BoardMapLoader.INDEX_PATH -> """{"maps":["$first","missing"]}""".byteInputStream()
                    else -> RepoAssets.assets.open(path)
                }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onError = { errors += it },
        )
        assertThat(repo.get().map { it.id }).containsExactly(first)
        assertThat(errors).hasSize(1)
    }
}

class DataStoreBoardRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var store: BoardStore

    private fun repository(scope: TestScope): DataStoreBoardRepository {
        store = BoardStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                folder.root.resolve("boards.preferences_pb")
            },
        )
        return DataStoreBoardRepository(store, StandardTestDispatcher(scope.testScheduler))
    }

    private fun board(id: String, updatedAt: Long, name: String = id) = SavedBoard(
        id = id,
        name = name,
        lineup = BoardLineup(red = listOf("arien"), blue = listOf("brogan")),
        state = BoardState(
            mapId = "forgotten-island",
            pieces = listOf(
                HeroPiece("p1", Hex(0, 0), "arien", Team.RED),
                MinionPiece("p2", Hex(1, -1), Team.BLUE, MinionType.HEAVY, lane = "lane"),
                TokenPiece("p3", Hex(2, -1), "Mine", Team.RED),
                // Waiting in the tray.
                MinionPiece("p4", null, Team.RED, MinionType.MELEE),
            ),
            battleZones = mapOf("lane" to "mid"),
        ),
        createdAt = 1L,
        updatedAt = updatedAt,
    )

    @Test
    fun `boards round-trip, newest first`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        repo.upsert(board("a", 10))
        repo.upsert(board("b", 30))
        repo.upsert(board("c", 20))
        assertThat(repo.boards.first().map { it.id }).containsExactly("b", "c", "a").inOrder()
        assertThat(repo.observe("a").first()).isEqualTo(board("a", 10))
        repo.upsert(board("a", 40, name = "Renamed"))
        assertThat(repo.boards.first().first().name).isEqualTo("Renamed")
        repo.delete("a")
        assertThat(repo.observe("a").first()).isNull()
        assertThat(repo.boards.first().map { it.id }).containsExactly("b", "c").inOrder()
    }

    @Test
    fun `a board with a hero, a minion, a token and a tray piece round-trips through plain JSON`() {
        val board = board("a", 10)
        // The default discriminator "type" names the piece; a minion's type is stored as "minion".
        val json = Json.encodeToString(SavedBoard.serializer(), board)
        assertThat(json).contains(""""type":"minion"""")
        assertThat(json).contains(""""minion":"heavy"""")
        assertThat(Json.decodeFromString(SavedBoard.serializer(), json)).isEqualTo(board)
        assertThat(BoardJson.decode(BoardJson.encode(board))).isEqualTo(board)
    }

    @Test
    fun `a board saved before minions had a Lane still reads, its minions unbound`() {
        val old = BoardJson.encode(board("a", 10)).replace(""","lane":"lane"""", "")
        assertThat(old).doesNotContain(""""lane":"lane"""")
        val read = BoardJson.decode(old)!!
        assertThat(read.state.pieces.filterIsInstance<MinionPiece>().map { it.lane }).containsExactly(null, null)
        assertThat(BoardJson.encode(board("a", 10))).contains(""""lane":"lane"""")
    }

    @Test
    fun `a corrupt document loses only its own board`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        repo.upsert(board("a", 10))
        store.write("broken", """{"id":"broken","name":""")
        store.write("alien", """{"id":"alien","state":{"pieces":[{"type":"dragon"}]}}""")
        assertThat(repo.boards.first().map { it.id }).containsExactly("a")
        assertThat(repo.observe("broken").first()).isNull()
    }

    @Test
    fun `newer documents with unknown fields still read`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        val json = BoardJson.encode(board("a", 10)).replaceFirst("{", """{"future":{"x":1},""")
        store.write("a", json)
        assertThat(repo.observe("a").first()).isEqualTo(board("a", 10))
    }

    @Test
    fun `import replace keeps exactly the imported boards`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        repo.upsert(board("a", 10))
        repo.upsert(board("b", 10))
        repo.import(listOf(board("b", 5, name = "Older"), board("c", 1)), replace = true)
        val boards = repo.boards.first()
        assertThat(boards.map { it.id }).containsExactly("b", "c")
        assertThat(boards.first { it.id == "b" }.name).isEqualTo("Older")
    }

    @Test
    fun `import merge keeps the later edit of each board`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        repo.upsert(board("a", 10, name = "Local a"))
        repo.upsert(board("b", 30, name = "Local b"))
        repo.import(
            listOf(board("a", 20, name = "Imported a"), board("b", 20, name = "Imported b"), board("c", 1)),
            replace = false,
        )
        val byId = repo.boards.first().associateBy { it.id }
        assertThat(byId.keys).containsExactly("a", "b", "c")
        assertThat(byId.getValue("a").name).isEqualTo("Imported a")
        assertThat(byId.getValue("b").name).isEqualTo("Local b")
    }
}
