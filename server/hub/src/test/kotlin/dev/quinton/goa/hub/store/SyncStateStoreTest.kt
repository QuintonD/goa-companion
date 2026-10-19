package dev.quinton.goa.hub.store

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The hub's list of phones that synced with it: the 90-day rule and "Forget". */
class SyncStateStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy { SyncStateStore(tmp.root.toPath().resolve("sync-state.json")) }
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_760_000_000_000L

    @Test
    fun `a phone not seen for 90 days isn't shown, and leaves the file at the next sync`() {
        // The anonymous row from before phones sent an id and a name.
        store.record(null, null, now - 91 * day)
        store.record("p1", "Quinton", now - 90 * day)
        assertThat(store.read(now).clients.map { it.name }).containsExactly("Quinton")
        assertThat(store.readAll().clients).hasSize(2)

        store.record("p2", "Sam", now)
        assertThat(store.readAll().clients.map { it.name }).containsExactly("Sam", "Quinton").inOrder()
        assertThat(store.read(now + day).clients.map { it.name }).containsExactly("Sam")
    }

    @Test
    fun `Forget removes one phone, and it comes back when it syncs again`() {
        store.record(null, null, now - day)
        store.record("p1", "Quinton", now)
        assertThat(store.forget("unnamed")).isTrue()
        assertThat(store.forget("unnamed")).isFalse()
        assertThat(store.read(now).clients.map { it.id }).containsExactly("p1")

        store.record(null, null, now + 1)
        assertThat(store.read(now + 1).clients.map { it.name }).containsExactly("A phone", "Quinton").inOrder()
        assertThat(store.read(now + 1).clients.first().syncs).isEqualTo(1)
    }
}
