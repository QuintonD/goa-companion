package dev.quinton.goa.core.datastore.trivia

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TriviaProgressStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(scope: TestScope) = TriviaProgressStore(
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            folder.root.resolve("trivia_progress.preferences_pb")
        },
    )

    @Test
    fun `progress is rewritten from what is stored and removed by null`() = runTest(UnconfinedTestDispatcher()) {
        val store = store(this)
        assertThat(store.progressJson.first()).isNull()
        store.updateProgress { old ->
            assertThat(old).isNull()
            "{}"
        }
        store.updateProgress { old -> "$old!" }
        assertThat(store.progressJson.first()).isEqualTo("{}!")
        store.updateProgress { null }
        assertThat(store.progressJson.first()).isNull()
    }

    @Test
    fun `config is kept apart from progress`() = runTest(UnconfinedTestDispatcher()) {
        val store = store(this)
        store.writeConfig("""{"length":20}""")
        store.updateProgress { "{}" }
        store.updateProgress { null }
        assertThat(store.configJson.first()).isEqualTo("""{"length":20}""")
        store.writeConfig(null)
        assertThat(store.configJson.first()).isNull()
    }
}
