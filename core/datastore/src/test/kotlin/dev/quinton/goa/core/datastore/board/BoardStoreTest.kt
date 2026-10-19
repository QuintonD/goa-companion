package dev.quinton.goa.core.datastore.board

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoardStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(scope: TestScope) = BoardStore(
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            folder.root.resolve("boards.preferences_pb")
        },
    )

    @Test
    fun `each board is its own document`() = runTest(UnconfinedTestDispatcher()) {
        val store = store(this)
        assertThat(store.documents.first()).isEmpty()
        store.write("a", "{a}")
        store.write("b", "{b}")
        store.write("a", "{a2}")
        assertThat(store.documents.first()).containsExactly("a", "{a2}", "b", "{b}")
        store.delete("a")
        assertThat(store.documents.first()).containsExactly("b", "{b}")
    }

    @Test
    fun `a boards file that can't be read is replaced, not a crash`() = runTest(UnconfinedTestDispatcher()) {
        val file = folder.root.resolve("corrupt.preferences_pb")
        file.writeBytes(byteArrayOf(0x7f, 0x13, 0x00, 0x42, 0x01, 0x02, 0x03))
        val store = BoardStore(BoardStoreModule.boardsDataStore(backgroundScope) { file })
        assertThat(store.documents.first()).isEmpty()
        store.write("a", "{a}")
        assertThat(store.documents.first()).containsExactly("a", "{a}")
    }

    @Test
    fun `update keeps exactly what the transform returns`() = runTest(UnconfinedTestDispatcher()) {
        val store = store(this)
        store.write("a", "{a}")
        store.write("b", "{b}")
        store.update { current ->
            assertThat(current).containsExactly("a", "{a}", "b", "{b}")
            mapOf("b" to "{b2}", "c" to "{c}")
        }
        assertThat(store.documents.first()).containsExactly("b", "{b2}", "c", "{c}")
    }
}
