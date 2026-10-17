package dev.quinton.goa.core.datastore.draft

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DraftStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(scope: TestScope, name: String = "draft") = DraftStore(
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            folder.root.resolve("$name.preferences_pb")
        },
    )

    @Test
    fun `active draft is saved and cleared`() = runTest(UnconfinedTestDispatcher()) {
        val first = store(this)
        assertThat(first.activeDraft.first()).isNull()
        first.setActiveDraft("""{"id":"d1"}""")
        assertThat(first.activeDraft.first()).isEqualTo("""{"id":"d1"}""")
        first.setActiveDraft(null)
        assertThat(first.activeDraft.first()).isNull()
    }

    @Test
    fun `custom methods persist independently of the active draft`() = runTest(UnconfinedTestDispatcher()) {
        val store = store(this, "methods")
        store.setCustomMethods("[]")
        store.setActiveDraft("x")
        store.setActiveDraft(null)
        assertThat(store.customMethods.first()).isEqualTo("[]")
    }
}
