package dev.quinton.goa.feature.sync.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.designsystem.icon.PlayerCrests
import dev.quinton.goa.core.designsystem.theme.PlayerColors
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.presentation.group.AddCheck
import dev.quinton.goa.core.presentation.group.MergeOffer
import dev.quinton.goa.core.presentation.group.PlayerLink
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WhoAreYouUiState(
    val loading: Boolean = true,
    /** Active players, by name. */
    val players: List<Player> = emptyList(),
    val meId: String? = null,
    /** The player a personal invite was made for (ADR-031): "You're <name>?" leads the screen. */
    val invited: Player? = null,
    val newName: String = "",
    val nameError: String? = null,
    val mergeOffer: MergeOffer? = null,
    /** The old player couldn't be merged: the matches both played in (one line each). */
    val mergeRefused: List<String>? = null,
    val addCheck: AddCheck? = null,
    /** Chosen: the screen closes. */
    val done: Boolean = false,
)

private data class Form(
    val newName: String = "",
    val nameError: String? = null,
    val mergeOffer: MergeOffer? = null,
    val mergeRefused: List<String>? = null,
    val addCheck: AddCheck? = null,
    val done: Boolean = false,
)

/**
 * "Which player are you?" (ADR-025): links this phone to a player, picked from the synced list,
 * or created here, which is how a friend adds themself to the group. Stored on this phone only.
 *
 * ADR-031: a personal invite leads with "You're <name>?"; adding a name that looks like players
 * already in the group first asks "Is one of these you?".
 */
@HiltViewModel
class WhoAreYouViewModel @Inject constructor(
    private val players: PlayerRepository,
    private val groups: GroupRepository,
    private val matches: MatchRepository,
    private val merges: PlayerMergeRepository,
    private val clock: EditClock,
) : ViewModel() {
    private val form = MutableStateFlow(Form())

    val uiState: StateFlow<WhoAreYouUiState> = combine(
        players.observePlayers(),
        groups.state,
        merges.observeMerges(),
        form,
    ) { people, group, merged, f ->
        WhoAreYouUiState(
            loading = false,
            players = PlayerLink.choices(people),
            meId = PlayerLink.me(group.mePlayerId, merged),
            // Not on this phone (the first sync failed): no question, just the list.
            invited = PlayerLink.invited(group.invitedPlayerId, group.mePlayerId, merged, people),
            newName = f.newName,
            nameError = f.nameError,
            mergeOffer = f.mergeOffer,
            mergeRefused = f.mergeRefused,
            addCheck = f.addCheck,
            done = f.done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoAreYouUiState())

    fun choose(playerId: String) {
        viewModelScope.launch {
            groups.setMe(playerId)
            groups.setInvitedPlayer(null)
            form.update { it.copy(addCheck = null, done = true) }
        }
    }

    /**
     * "Yes, I'm <name>": this phone becomes the invited player. If it was another player before
     * (used alone before joining), offer to merge that one in.
     */
    fun confirmInvited() {
        viewModelScope.launch {
            val invited = uiState.value.invited ?: return@launch
            val group = groups.state.first()
            val merged = merges.observeMerges().first()
            val people = players.observePlayers().first()
            val offer = PlayerLink.mergeOffer(
                invited,
                group.mePlayerId,
                merged,
                people,
                matches.observeMatches().first(),
            )
            groups.setMe(invited.id)
            groups.setInvitedPlayer(null)
            if (offer == null) {
                form.update { it.copy(done = true) }
            } else {
                form.update { it.copy(mergeOffer = offer) }
            }
        }
    }

    /** "Not me": forget the invite's player and pick from the list. */
    fun notMe() {
        viewModelScope.launch { groups.setInvitedPlayer(null) }
    }

    /** Merges this phone's old player into the one it now is (refused if they played together). */
    fun mergeOld() {
        val offer = form.value.mergeOffer ?: return
        viewModelScope.launch {
            when (val result = merges.merge(offer.old.id, offer.into.id)) {
                is PlayerMergeResult.PlayedTogether -> {
                    val people = players.observePlayers(includeArchived = true).first().associateBy { it.id }
                    val lines = PlayerLink.refusalLines(result.matches, people, ZoneId.systemDefault())
                    form.update { it.copy(mergeOffer = null, mergeRefused = lines) }
                }

                PlayerMergeResult.Merged, PlayerMergeResult.NotPossible ->
                    form.update { it.copy(mergeOffer = null, done = true) }
            }
        }
    }

    /** "Keep both": the old player stays as someone else. */
    fun keepBoth() = form.update { it.copy(mergeOffer = null, done = true) }

    fun refusalShown() = form.update { it.copy(mergeRefused = null, done = true) }

    fun onNameChange(name: String) = form.update { it.copy(newName = name.take(PlayerLink.MAX_NAME), nameError = null) }

    /**
     * "Add me to the group": first lists players with a similar name ("Is one of these you?"),
     * otherwise adds this phone's owner as a new player and picks them.
     */
    fun createMe() {
        val name = form.value.newName.trim()
        if (name.isEmpty()) {
            form.update { it.copy(nameError = PlayerLink.NAME_MISSING) }
            return
        }
        viewModelScope.launch {
            val check = PlayerLink.addCheck(name, players.observePlayers().first())
            if (check != null) form.update { it.copy(addCheck = check) } else add(name)
        }
    }

    /** "No, add <name>": none of the look-alikes is this phone's owner. */
    fun addAnyway() {
        val check = form.value.addCheck ?: return
        if (check.nameTaken) return
        form.update { it.copy(addCheck = null) }
        viewModelScope.launch { add(check.name) }
    }

    fun dismissAddCheck() = form.update { it.copy(addCheck = null) }

    private suspend fun add(name: String) {
        // Archived players aren't offered above, but their names stay taken.
        val existing = players.findByName(name)
        if (existing != null) {
            form.update { it.copy(nameError = PlayerLink.nameTaken(existing)) }
            return
        }
        val player = PlayerLink.newPlayer(
            id = UUID.randomUUID().toString(),
            name = name,
            everyone = players.observePlayers(includeArchived = true).first(),
            palette = PlayerColors.all.map { it.argb },
            crests = PlayerCrests.count,
            now = clock.now(),
        )
        players.upsert(player)
        groups.setMe(player.id)
        groups.setInvitedPlayer(null)
        form.update { it.copy(done = true) }
    }
}
