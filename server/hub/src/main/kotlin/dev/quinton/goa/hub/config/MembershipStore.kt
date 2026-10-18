package dev.quinton.goa.hub.config

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SyncCrypto
import dev.quinton.goa.hub.store.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlinx.serialization.Serializable

/**
 * The one group this hub belongs to (ADR-030 §2), in `group.json` (owner read/write only). The hub
 * holds the group key like any member phone.
 */
class MembershipStore(private val file: Path) {
    @Serializable
    private data class Stored(val groupId: String, val key: String, val name: String, val joinedAt: Long)

    @Volatile
    private var cached: Pair<Long, GroupCredentials?>? = null

    /** The group, or null before `goa-hub join` / the first-run page. */
    fun group(): GroupCredentials? {
        val modified = if (Files.exists(file)) Files.getLastModifiedTime(file).toMillis() else -1L
        cached?.takeIf { it.first == modified }?.let { return it.second }
        val group = AtomicFiles.readOrNull(file)?.let { bytes ->
            val stored = ConfigJson.decodeFromString(Stored.serializer(), bytes.decodeToString())
            GroupCredentials(stored.groupId, Base64.getUrlDecoder().decode(stored.key), stored.name)
        }
        cached = modified to group
        return group
    }

    /** Result of [join]. */
    sealed interface JoinResult {
        data class Joined(val group: GroupCredentials, val rejoined: Boolean) : JoinResult

        /** The hub already belongs to [current]; one group per hub. */
        data class OtherGroup(val current: GroupCredentials) : JoinResult
    }

    /**
     * Joins [group]. Joining the same group again only refreshes its name; another group is refused
     * (its data would mix with this one's, and the old group's phones would lose their hub).
     */
    @Synchronized
    fun join(group: GroupCredentials, now: Long): JoinResult {
        val current = group()
        if (current != null && current.groupId != group.groupId) return JoinResult.OtherGroup(current)
        if (current != null && !SyncCrypto.same(current.key, group.key)) return JoinResult.OtherGroup(current)
        val stored = Stored(
            groupId = group.groupId,
            key = Base64.getUrlEncoder().withoutPadding().encodeToString(group.key),
            name = group.name,
            joinedAt = now,
        )
        AtomicFiles.write(file, ConfigJson.encodeToString(Stored.serializer(), stored).toByteArray(), private = true)
        cached = null
        return JoinResult.Joined(group, rejoined = current != null)
    }
}
