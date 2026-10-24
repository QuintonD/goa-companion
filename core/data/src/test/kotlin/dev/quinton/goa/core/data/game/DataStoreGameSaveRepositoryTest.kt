package dev.quinton.goa.core.data.game

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.datastore.game.GameStore
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.model.match.Team
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreGameSaveRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var store: GameStore

    private fun repository(scope: TestScope): DataStoreGameSaveRepository {
        store = GameStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                folder.root.resolve("games.preferences_pb")
            },
        )
        return DataStoreGameSaveRepository(store, StandardTestDispatcher(scope.testScheduler))
    }

    private val game = GameInProgress(
        game = SavedGame(
            engineVersion = 1,
            setup = GameSetup(
                mapId = "forgotten-island",
                seats = listOf(
                    SeatSetup("arien", Team.RED, Controller.Human),
                    SeatSetup("wasp", Team.BLUE, Controller.Ai(AiLevel.HARD)),
                ),
                length = GameLength.LONG,
                tieBreaker = Team.BLUE,
            ),
            payload = """{"answers":[1,2,3]}""",
            savedAt = 42,
        ),
        takeBacks = 2,
        nudges = 3,
        rethinks = 1,
        coach = false,
        pace = AiPace.SLOW,
        startedAt = 7,
    )

    @Test
    fun `no game reads as none, a saved one round-trips, clear removes it`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        assertThat(repo.current.first()).isEqualTo(StoredGame.None)
        repo.save(game)
        assertThat(repo.current.first()).isEqualTo(StoredGame.Saved(game))
        repo.clear()
        assertThat(repo.current.first()).isEqualTo(StoredGame.None)
    }

    @Test
    fun `a corrupt document reads as unreadable, never a crash`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        store.write("{not json")
        assertThat(repo.current.first()).isEqualTo(StoredGame.Unreadable)
        store.write("""{"takeBacks": 1}""")
        assertThat(repo.current.first()).isEqualTo(StoredGame.Unreadable)
    }

    @Test
    fun `a newer build's extra fields still read`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        val json = GameJson.encode(game).replaceFirst("{", """{"futureField":true,""")
        store.write(json)
        assertThat(repo.current.first()).isEqualTo(StoredGame.Saved(game))
    }
}
