package dev.quinton.goa.web.play.online

import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineIdentityStore
import dev.quinton.goa.web.storage.KeyValueStore
import dev.quinton.goa.web.storage.getJson
import dev.quinton.goa.web.storage.putJson
import kotlinx.serialization.Serializable

/**
 * This browser's online account, under `online/identity` in IndexedDB ([store] is scoped to `online/`). Never
 * synced and in no export: sync files and the group's document are built from `group/` alone. On an iPhone not
 * added to the Home Screen the browser may clear it: the recovery code is how the person gets back (ADR-056).
 */
class WebOnlineIdentityStore(private val store: KeyValueStore) : OnlineIdentityStore {
    override suspend fun load(): OnlineIdentity? =
        store.getJson<StoredIdentity>(KEY)?.let { OnlineIdentity(it.accountId, it.token, it.name) }

    override suspend fun save(identity: OnlineIdentity) =
        store.putJson(KEY, StoredIdentity(identity.accountId, identity.token, identity.name))

    override suspend fun clear() = store.delete(KEY)

    @Serializable
    private data class StoredIdentity(val accountId: String, val token: String, val name: String)

    private companion object {
        const val KEY = "identity"
    }
}
