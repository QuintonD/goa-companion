package dev.quinton.goa.core.data.puzzle

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.datastore.puzzle.PuzzleProgressStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStorePuzzleProgressRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var store: PuzzleProgressStore

    private fun repository(scope: TestScope): DataStorePuzzleProgressRepository {
        store = PuzzleProgressStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                folder.root.resolve("puzzle_progress.preferences_pb")
            },
        )
        return DataStorePuzzleProgressRepository(store)
    }

    @Test
    fun `progress starts empty, updates per puzzle, and reset forgets everything`() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        val repo = repository(this)
        assertThat(repo.progress.first()).isEmpty()
        repo.update("p01") { it.copy(attempts = it.attempts + 1, nudgesUsed = 2) }
        repo.update("p01") { it.copy(attempts = it.attempts + 1, solved = true, solvedAt = 9) }
        repo.update("p17") { it.copy(solutionShown = true) }
        assertThat(repo.progress.first()).containsExactly(
            "p01",
            PuzzleProgress(solved = true, solvedAt = 9, attempts = 2, nudgesUsed = 2),
            "p17",
            PuzzleProgress(solutionShown = true),
        )
        repo.reset()
        assertThat(repo.progress.first()).isEmpty()
    }

    @Test
    fun `a corrupt document reads as no progress and the next write replaces it`() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        val repo = repository(this)
        store.update { "{not json" }
        assertThat(repo.progress.first()).isEmpty()
        repo.update("p14") { it.copy(attempts = 1) }
        assertThat(repo.progress.first()).containsExactly("p14", PuzzleProgress(attempts = 1))
    }

    @Test
    fun `a newer build's extra fields still read`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        store.update { """{"p01":{"solved":true,"attempts":3,"stars":2}}""" }
        assertThat(repo.progress.first()).containsExactly("p01", PuzzleProgress(solved = true, attempts = 3))
    }
}
