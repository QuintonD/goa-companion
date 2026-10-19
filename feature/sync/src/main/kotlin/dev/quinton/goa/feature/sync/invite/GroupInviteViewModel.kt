package dev.quinton.goa.feature.sync.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.presentation.group.GroupInvites
import dev.quinton.goa.core.presentation.group.InviteFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GroupInviteUiState(
    val loading: Boolean = true,
    val groupName: String? = null,
    /** Who an invite can be made for: active players by name, this phone's own player left out. */
    val players: List<Player> = emptyList(),
    val choosing: Boolean = false,
    /** The player a personal invite is for; null = anyone. */
    val player: Player? = null,
    /** What the QR code shows; null while choosing or when this phone is in no group. */
    val invite: String? = null,
    /** "Share as text": what to do with it, then the invite. */
    val shareText: String? = null,
) {
    val inGroup: Boolean get() = groupName != null
}

/**
 * "Invite a friend" (ADR-025, ADR-031): the invite QR code, for anyone or for one player. A
 * personal invite carries the player's id (`p=`); after the first sync the friend's phone asks
 * "You're <name>?" and becomes that player instead of adding a new one.
 */
@HiltViewModel(assistedFactory = GroupInviteViewModel.Factory::class)
class GroupInviteViewModel @AssistedInject constructor(
    @Assisted playerId: String?,
    groups: GroupRepository,
    players: PlayerRepository,
    merges: PlayerMergeRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(playerId: String?): GroupInviteViewModel
    }

    private val choice = MutableStateFlow(playerId?.let(InviteFor::One) ?: InviteFor.Choosing)

    val uiState: StateFlow<GroupInviteUiState> = combine(
        groups.state,
        players.observePlayers(includeArchived = true),
        merges.observeMerges(),
        choice,
    ) { group, people, merged, chosen ->
        val credentials = group.group ?: return@combine GroupInviteUiState(
            loading = false,
            players = GroupInvites.choices(people, merged, group.mePlayerId),
            choosing = true,
        )
        val view = GroupInvites.view(credentials, group.hubUrl, people, merged, group.mePlayerId, chosen)
        GroupInviteUiState(
            loading = false,
            groupName = credentials.name,
            players = view.players,
            choosing = view.choosing,
            player = view.player,
            invite = view.invite,
            shareText = view.shareText,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupInviteUiState())

    fun choose(target: InviteFor) {
        choice.value = target
    }
}
