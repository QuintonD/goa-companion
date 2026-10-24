package dev.quinton.goa.core.data.sync

import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.ServerSyncState
import dev.quinton.goa.core.data.SyncServerDefaults
import dev.quinton.goa.core.data.SyncServerError
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.data.SyncedPhone
import dev.quinton.goa.core.datastore.sync.GroupSyncPrefs
import dev.quinton.goa.core.datastore.sync.GroupSyncStore
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SecureBytes
import dev.quinton.goa.core.domain.sync.SyncServerAddress
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** [GroupRepository] in its own DataStore ([GroupSyncStore]), apart from the app settings. */
@Singleton
internal class DataStoreGroupRepository @Inject constructor(
    private val store: GroupSyncStore,
    private val defaults: SyncServerDefaults,
) : GroupRepository {
    internal var random: SecureBytes = SecureBytes.System

    /** The build's default server, checked like a typed one (an unusable default is no default). */
    private val defaultUrl: String? =
        defaults.defaultUrl?.let { SyncServerAddress.normalize(it, defaults.allowLocalHttp) }

    override val state: Flow<GroupState> = store.prefs
        .onStart { ensureDeviceId() }
        .filter { it.deviceId != null }
        .map(::toState)
        .distinctUntilChanged()

    override suspend fun start(name: String): GroupCredentials {
        val group = GroupCredentials.create(name, random)
        join(group)
        return group
    }

    override suspend fun join(group: GroupCredentials, serverUrl: String?) = store.edit { prefs ->
        val same = prefs.groupId == group.groupId
        val invited = serverUrl?.let(::override)
        val server = if (serverUrl == null && same) prefs.serverUrl else invited
        // The last server sync's outcome belongs to that group and server.
        val status = if (same && server == prefs.serverUrl) prefs else prefs.withoutServerStatus()
        status.copy(
            groupId = group.groupId,
            groupKey = Base64.getEncoder().encodeToString(group.key),
            groupName = group.name,
            // Another group's phones and players mean nothing here.
            phonesJson = if (same) prefs.phonesJson else null,
            lastSyncAt = if (same) prefs.lastSyncAt else null,
            mePlayerId = if (same || prefs.groupId == null) prefs.mePlayerId else null,
            serverUrl = server,
            serverEnabled = if (same) prefs.serverEnabled else null,
        )
    }

    override suspend fun leave() = store.edit {
        it.withoutServerStatus().copy(
            groupId = null,
            groupKey = null,
            groupName = null,
            phonesJson = null,
            lastSyncAt = null,
            serverUrl = null,
            serverEnabled = null,
        )
    }

    override suspend fun setServerUrl(url: String?) = store.edit { prefs ->
        val server = url?.let(::override)
        (if (server == prefs.serverUrl) prefs else prefs.withoutServerStatus()).copy(serverUrl = server)
    }

    override suspend fun setServerSyncEnabled(enabled: Boolean) =
        store.edit { it.copy(serverEnabled = enabled.takeUnless { on -> on }) }

    override suspend fun setMe(playerId: String?) = store.edit { it.copy(mePlayerId = playerId) }

    override suspend fun setDeviceName(name: String?) =
        store.edit { it.copy(deviceName = name?.trim()?.takeIf(String::isNotEmpty)) }

    override suspend fun recordSync(phones: List<SyncDevice>, at: Long) = store.edit { prefs ->
        val known = decodePhones(prefs.phonesJson)
        val updated = phones.map { SyncedPhone(it.deviceId, it.name, at) }
        val merged = (updated + known.filterNot { k -> updated.any { it.deviceId == k.deviceId } })
            .filterNot { it.deviceId == prefs.deviceId }
            .sortedByDescending { it.lastSyncedAt }
            .take(MAX_PHONES)
        prefs.copy(phonesJson = json.encodeToString(phoneList, merged), lastSyncAt = at)
    }

    /**
     * Stores how a server sync of [groupId] with [server] went, unless the group or server
     * changed while it ran (that outcome isn't theirs).
     */
    internal suspend fun recordServerSync(
        groupId: String,
        server: String,
        outcome: SyncServerResult,
        documentHash: String? = null,
    ) = store.edit { prefs ->
        if (prefs.groupId != groupId || serverOf(prefs) != server) return@edit prefs
        when (outcome) {
            is SyncServerResult.Synced -> prefs.copy(
                serverSyncedAt = outcome.at,
                serverRev = outcome.rev,
                serverDocHash = documentHash.takeIf { outcome.rev != null },
                serverError = null,
                serverErrorKind = null,
                serverErrorAt = null,
            )

            is SyncServerResult.Failed -> prefs.copy(
                serverError = outcome.error.message,
                serverErrorKind = outcome.error.problem.name,
                serverErrorAt = outcome.error.at,
            )

            SyncServerResult.NotConfigured -> prefs
        }
    }

    /**
     * The rev and document hash of the last sync of [groupId] with [server] (both sides held that
     * document then); null when there is none.
     */
    internal suspend fun serverCheckpoint(groupId: String, server: String): Pair<Long, String>? {
        val prefs = store.prefs.first()
        if (prefs.groupId != groupId || serverOf(prefs) != server) return null
        val rev = prefs.serverRev ?: return null
        val hash = prefs.serverDocHash ?: return null
        return rev to hash
    }

    /** What to store for [url]: its origin, or null when it is the default (or not an address). */
    private fun override(url: String): String? =
        SyncServerAddress.normalize(url, defaults.allowLocalHttp)?.takeIf { it != defaultUrl }

    private fun serverOf(prefs: GroupSyncPrefs): String? = prefs.serverUrl ?: defaultUrl

    private suspend fun ensureDeviceId() {
        store.edit { if (it.deviceId == null) it.copy(deviceId = UUID.randomUUID().toString()) else it }
    }

    private fun toState(prefs: GroupSyncPrefs): GroupState {
        val key = prefs.groupKey?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        val id = prefs.groupId
        val group = if (id != null && key != null) GroupCredentials(id, key, prefs.groupName ?: "Our group") else null
        return GroupState(
            group = group,
            deviceId = requireNotNull(prefs.deviceId),
            deviceName = prefs.deviceName,
            mePlayerId = prefs.mePlayerId,
            phones = decodePhones(prefs.phonesJson),
            lastSyncAt = prefs.lastSyncAt,
            server = ServerSyncState(
                url = serverOf(prefs),
                custom = prefs.serverUrl != null,
                defaultUrl = defaultUrl,
                enabled = prefs.serverEnabled ?: true,
                syncedAt = prefs.serverSyncedAt,
                rev = prefs.serverRev,
                error = prefs.serverError?.let { message ->
                    SyncServerError(
                        problem = SyncServerProblem.entries.firstOrNull { it.name == prefs.serverErrorKind }
                            ?: SyncServerProblem.SERVER,
                        message = message,
                        at = prefs.serverErrorAt ?: 0,
                    )
                },
            ),
        )
    }

    private fun decodePhones(text: String?): List<SyncedPhone> =
        text?.let { runCatching { json.decodeFromString(phoneList, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val MAX_PHONES = 20
        val json = Json { ignoreUnknownKeys = true }
        val phoneList = ListSerializer(SyncedPhone.serializer())
    }
}
