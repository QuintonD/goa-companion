package dev.quinton.goa.core.datastore.player

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlayerMatchUpStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun file() = folder.root.resolve("${PlayerMatchUpStore.FILE_NAME}.preferences_pb")

    private fun store(scope: TestScope) =
        PlayerMatchUpStore(PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { file() })

    @Test
    fun `different people are remembered, once each, until forgotten`() = runTest(UnconfinedTestDispatcher()) {
        val store = store(this)
        assertThat(store.differentPairs.first()).isEmpty()
        store.addDifferent("a\nb")
        store.addDifferent("a\nc")
        store.addDifferent("a\nb")
        assertThat(store.differentPairs.first()).containsExactly("a\nb", "a\nc")
        store.clearDifferent()
        assertThat(store.differentPairs.first()).isEmpty()
    }

    @Test
    fun `answers survive a restart`() {
        runTest(UnconfinedTestDispatcher()) { store(this).addDifferent("a\nb") }
        runTest(UnconfinedTestDispatcher()) {
            assertThat(store(this).differentPairs.first()).containsExactly("a\nb")
        }
    }
}
