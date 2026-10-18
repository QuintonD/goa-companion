package dev.quinton.goa.core.data.sync

import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.HubError
import dev.quinton.goa.core.data.HubProblem
import dev.quinton.goa.core.data.SyncedPhone
import dev.quinton.goa.core.datastore.sync.GroupSyncPrefs
import dev.quinton.goa.core.datastore.sync.GroupSyncStore
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.HubAddress
import dev.quinton.goa.core.domain.sync.SecureBytes
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** [GroupRepository] in its own DataStore ([GroupSyncStore]), apart from the app settings. */
@Singleton
internal class DataStoreGroupRepository @Inject constructor(
    private val store: GroupSyncStore,
) : GroupRepository {
    internal var random: SecureBytes = SecureBytes.System

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

    override suspend fun join(
        group: GroupCredentials,
        hubUrl: String?,
        invitedPlayerId: String?,
    ) = store.edit { prefs ->
        val same = prefs.groupId == group.groupId
        val hub = hubUrl?.let(HubAddress::normalize) ?: prefs.hubUrl.takeIf { same }
        // The last hub sync's outcome belongs to that group and hub.
        val status = if (same && hub == prefs.hubUrl) prefs else prefs.withoutHubStatus()
        status.copy(
            hubUrl = hub,
            invitedPlayerId = invitedPlayerId ?: prefs.invitedPlayerId.takeIf { same },
            groupId = group.groupId,
            groupKey = Base64.getEncoder().encodeToString(group.key),
            groupName = group.name,
            // Another group's phones and players mean nothing here.
            phonesJson = if (same) prefs.phonesJson else null,
            lastSyncAt = if (same) prefs.lastSyncAt else null,
            mePlayerId = if (same || prefs.groupId == null) prefs.mePlayerId else null,
        )
    }

    override suspend fun leave() = store.edit {
        it.withoutHubStatus().copy(
            groupId = null,
            groupKey = null,
            groupName = null,
            phonesJson = null,
            lastSyncAt = null,
            hubUrl = null,
            invitedPlayerId = null,
        )
    }

    override suspend fun setHubUrl(url: String?) = store.edit { prefs ->
        val hub = url?.let(HubAddress::normalize)
        (if (hub == prefs.hubUrl) prefs else prefs.withoutHubStatus()).copy(hubUrl = hub)
    }

    override suspend fun setInvitedPlayer(playerId: String?) = store.edit { it.copy(invitedPlayerId = playerId) }

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
            hubUrl = prefs.hubUrl,
            invitedPlayerId = prefs.invitedPlayerId,
            hubSyncedAt = prefs.hubSyncedAt,
            hubError = prefs.hubError?.let { message ->
                HubError(
                    problem = HubProblem.entries.firstOrNull { it.name == prefs.hubErrorKind } ?: HubProblem.SERVER,
                    message = message,
                    at = prefs.hubErrorAt ?: 0,
                )
            },
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
