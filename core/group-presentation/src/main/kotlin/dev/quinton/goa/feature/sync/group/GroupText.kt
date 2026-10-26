package dev.quinton.goa.feature.sync.group

import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.SyncServerOutcome
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.SyncServerAddress
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.sync.SyncWords
import dev.quinton.goa.feature.sync.server.SyncServerText
import dev.quinton.goa.feature.sync.server.SyncServerUi

data class PhoneRow(val name: String, val lastSynced: String)

data class GroupUiState(
    val loading: Boolean = true,
    val groupName: String? = null,
    /** The invite the QR code shows (`goa-sync:1?…`, with `&s=` for a group's own sync server). */
    val invite: String? = null,
    val me: Player? = null,
    val deviceName: String = "",
    val phones: List<PhoneRow> = emptyList(),
    val lastSync: String? = null,
    val players: Int = 0,
    val matches: Int = 0,
    val busy: Boolean = false,
    /** Why an invite couldn't be used (shown under the paste field). */
    val joinError: String? = null,
    /** A one-off message for the snackbar. */
    val message: String? = null,
    /** The group's sync server and how its automatic sync went (ADR-036). */
    val server: SyncServerUi = SyncServerUi(),
) {
    val inGroup: Boolean get() = groupName != null
}

/** The Group sync screen's state and words, shared by the phone and the web app (ADR-043). */
object GroupText {
    const val GIVE_A_NAME = "Give the group a name"
    const val USE_DEFAULT_SERVER = "This group uses the app's sync server again."

    /**
     * The screen's state from the group, the players ([people], archived included) and the match
     * log. [deviceName] is what this device is called when no player is chosen (the phone's model,
     * the browser's name).
     */
    @Suppress("LongParameterList")
    fun state(
        group: GroupState,
        people: List<Player>,
        matches: Int,
        serverRunning: Boolean,
        now: Long,
        deviceName: String,
        busy: Boolean = false,
        joinError: String? = null,
        message: String? = null,
        addressError: String? = null,
    ): GroupUiState {
        val me = people.firstOrNull { it.id == group.mePlayerId }
        return GroupUiState(
            loading = false,
            groupName = group.group?.name,
            // The server travels in the invite only when this device syncs through it and it isn't
            // the app's built-in one.
            invite = group.group?.let { GroupInvite.format(it, group.server.activeUrl.takeIf { group.server.custom }) },
            me = me,
            deviceName = me?.name ?: group.deviceName ?: deviceName,
            phones = group.phones.map { PhoneRow(it.name, SyncWords.ago(it.lastSyncedAt, now)) },
            // Any sync counts, face to face or through the server (hands-on: "last synced 6 days ago"
            // under a server card saying "Synced just now").
            lastSync = listOfNotNull(group.lastSyncAt, group.server.syncedAt).maxOrNull()
                ?.let { SyncWords.ago(it, now) },
            players = people.count { !it.archived },
            matches = matches,
            busy = busy,
            joinError = joinError,
            message = message,
            server = SyncServerText.ui(group, serverRunning, now, addressError),
        )
    }

    fun started(name: String) = "Group “$name” started. Invite your friends with the QR code."

    fun alreadyIn(name: String) = "This phone is already in “$name”."

    fun left(name: String?) = "Left “$name”. Everything you played stays on this phone."

    /** After joining with a sync server: what the first sync through it brought. */
    fun joinedThroughServer(name: String, outcome: SyncServerOutcome): String {
        val received = outcome.received
        return when {
            !outcome.synced -> "Joined “$name”. The sync server can't help right now, so sync face to face."

            received == null ->
                "Joined “$name”. The sync server has nothing from the group yet, so sync face to face once."

            else -> "Joined “$name”. From the sync server: $received."
        }
    }

    fun serverSaved(url: String) = "Server saved. Syncing with ${SyncServerAddress.display(url)}…"
}
