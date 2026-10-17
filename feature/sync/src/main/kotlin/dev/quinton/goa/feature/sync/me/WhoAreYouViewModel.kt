package dev.quinton.goa.feature.sync.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.designsystem.icon.PlayerCrests
import dev.quinton.goa.core.designsystem.theme.PlayerColors
import dev.quinton.goa.core.model.player.Player
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
    val newName: String = "",
    val nameError: String? = null,
    /** Chosen: the screen closes. */
    val done: Boolean = false,
)

private data class Form(val newName: String = "", val nameError: String? = null, val done: Boolean = false)

/**
 * "Which player are you?" (ADR-025): links this phone to a player, picked from the synced list,
 * or created here, which is how a friend adds themself to the group. Stored on this phone only.
 */
@HiltViewModel
class WhoAreYouViewModel @Inject constructor(
    private val players: PlayerRepository,
    private val groups: GroupRepository,
    private val clock: EditClock,
) : ViewModel() {
    private val form = MutableStateFlow(Form())

    val uiState: StateFlow<WhoAreYouUiState> = combine(
        players.observePlayers(),
        groups.state,
        form,
    ) { people, group, f ->
        WhoAreYouUiState(
            loading = false,
            players = people.sortedBy { it.name.lowercase() },
            meId = group.mePlayerId,
            newName = f.newName,
            nameError = f.nameError,
            done = f.done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoAreYouUiState())

    fun choose(playerId: String) {
        viewModelScope.launch {
            groups.setMe(playerId)
            form.update { it.copy(done = true) }
        }
    }

    fun onNameChange(name: String) = form.update { it.copy(newName = name.take(MAX_NAME), nameError = null) }

    /** Adds this phone's owner as a new player (unless the name is taken) and picks them. */
    fun createMe() {
        val name = form.value.newName.trim()
        if (name.isEmpty()) {
            form.update { it.copy(nameError = "Enter your name") }
            return
        }
        viewModelScope.launch {
            val existing = players.findByName(name)
            if (existing != null) {
                form.update {
                    it.copy(nameError = "“${existing.name}” is already in the group. If that's you, pick it above.")
                }
                return@launch
            }
            val all = players.observePlayers(includeArchived = true).first()
            val used = all.map { it.color }.toSet()
            val player = Player(
                id = UUID.randomUUID().toString(),
                name = name,
                color = (PlayerColors.all.firstOrNull { it.argb !in used } ?: PlayerColors.default(all.size)).argb,
                avatarId = all.size % PlayerCrests.count,
                createdAt = clock.now(),
            )
            players.upsert(player)
            groups.setMe(player.id)
            form.update { it.copy(done = true) }
        }
    }

    private companion object {
        const val MAX_NAME = 24
    }
}
