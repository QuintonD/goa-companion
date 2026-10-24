package dev.quinton.goa.core.data.game

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.board.BoardMapLoader
import dev.quinton.goa.core.data.catalog.CatalogLoader
import dev.quinton.goa.core.data.catalog.RepoAssets
import dev.quinton.goa.core.datastore.game.SavedGameStore
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.model.match.Team
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedGameRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(scope: TestScope) = SavedGameStore(
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            folder.root.resolve("saved_games.preferences_pb")
        },
    )

    private val setup = GameSetup(
        mapId = "forgotten-island",
        seats = listOf(
            SeatSetup("wasp", Team.RED, Controller.Human),
            SeatSetup("arien", Team.BLUE, Controller.Ai(AiLevel.NORMAL)),
        ),
        tieBreaker = Team.BLUE,
    )

    private fun game(id: String, at: Long) = StoredGame(
        id = id,
        game = SavedGame(engineVersion = 1, setup = setup, payload = "{\"answers\":[]}", savedAt = at),
        options = PlayOptions(coach = false, speed = PlaySpeed.FAST),
        takeBacks = 2,
        createdAt = at,
        updatedAt = at,
        round = 3,
        turn = 2,
    )

    @Test
    fun `games round-trip, newest first, and delete`() = runTest(UnconfinedTestDispatcher()) {
        val store = store(this)
        val repo = DataStoreSavedGameRepository(store, UnconfinedTestDispatcher(testScheduler))
        repo.save(game("a", 10))
        repo.save(game("b", 20))
        assertThat(repo.games.first().map { it.id }).containsExactly("b", "a").inOrder()
        assertThat(repo.observe("a").first()).isEqualTo(game("a", 10))
        repo.delete("b")
        assertThat(repo.games.first().map { it.id }).containsExactly("a")
        assertThat(repo.observe("b").first()).isNull()
    }

    @Test
    fun `a document that can't be read is listed as unreadable, never a crash`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = store(this)
            val repo = DataStoreSavedGameRepository(store, UnconfinedTestDispatcher(testScheduler))
            repo.save(game("a", 10))
            store.write("broken", "{not json")
            assertThat(repo.games.first()).containsExactly(game("a", 10), UnreadableGame("broken")).inOrder()
            assertThat(repo.observe("broken").first()).isEqualTo(UnreadableGame("broken"))
        }

    @Test
    fun `the engine is built once from the bundled catalog and maps`() = runTest {
        val catalog = CatalogLoader.load(RepoAssets.assets)
        val maps = BoardMapLoader.load(RepoAssets.assets)
        val provider = EngineGameFactoryProvider(
            object : CatalogRepository {
                override val catalog = flowOf(catalog)
            },
            object : BoardMapRepository {
                override val maps = flowOf(maps)
            },
            StandardTestDispatcher(testScheduler),
        )
        val engine = provider.get()
        assertThat(provider.get()).isSameInstanceAs(engine)
        assertThat(engine.playableHeroes).containsAtLeast("wasp", "xargatha", "arien", "brogan")
        assertThat(engine.playableMaps).contains("forgotten-island")
        val session = engine.newGame(setup)
        assertThat(session.pending).isNotNull()
        assertThat(engine.restore(session.save()).pending).isEqualTo(session.pending)
    }
}
