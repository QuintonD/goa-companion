package dev.quinton.goa.feature.players.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.designsystem.icon.PlayerCrests
import dev.quinton.goa.core.designsystem.theme.PlayerColors
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val color: Long = PlayerColors.all.first().argb,
    val avatarId: Int = 0,
    val nameError: String? = null,
    val saving: Boolean = false,
    /** True once saved; the screen navigates back. */
    val saved: Boolean = false,
    /** The edited player missing (deleted by an import) — show an error. */
    val notFound: Boolean = false,
) {
    /** Preview of the player being edited, for [dev.quinton.goa.core.designsystem.component.PlayerAvatar]. */
    val preview: Player get() = Player(id = "preview", name = name.ifBlank { "?" }, color = color, avatarId = avatarId, createdAt = 0)
}

const val MAX_NAME_LENGTH = 24

@HiltViewModel(assistedFactory = PlayerEditViewModel.Factory::class)
class PlayerEditViewModel @AssistedInject constructor(
    @Assisted private val playerId: String?,
    private val players: PlayerRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(playerId: String?): PlayerEditViewModel
    }

    private val state = MutableStateFlow(PlayerEditUiState())
    val uiState: StateFlow<PlayerEditUiState> = state.asStateFlow()
    private var original: Player? = null

    init {
        viewModelScope.launch {
            val all = players.observePlayers(includeArchived = true).first()
            val existing = playerId?.let { id -> all.firstOrNull { it.id == id } }
            original = existing
            state.value = when {
                existing != null -> PlayerEditUiState(
                    loading = false,
                    isNew = false,
                    name = existing.name,
                    color = existing.color,
                    avatarId = existing.avatarId,
                )
                playerId != null -> PlayerEditUiState(loading = false, isNew = false, notFound = true)
                else -> {
                    val used = all.map { it.color }.toSet()
                    PlayerEditUiState(
                        loading = false,
                        color = (PlayerColors.all.firstOrNull { it.argb !in used } ?: PlayerColors.default(all.size)).argb,
                        avatarId = all.size % PlayerCrests.count,
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) = state.update { it.copy(name = name.take(MAX_NAME_LENGTH), nameError = null) }

    fun onColorChange(color: Long) = state.update { it.copy(color = color) }

    fun onAvatarChange(avatarId: Int) = state.update { it.copy(avatarId = avatarId.coerceIn(0, PlayerCrests.count - 1)) }

    fun save() {
        val current = state.value
        if (current.saving || current.loading || current.notFound) return
        val name = current.name.trim()
        if (name.isEmpty()) {
            state.update { it.copy(nameError = "Enter a name") }
            return
        }
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val all = players.observePlayers(includeArchived = true).first()
            val clash = all.any { it.id != playerId && it.name.trim().equals(name, ignoreCase = true) }
            if (clash) {
                state.update { it.copy(saving = false, nameError = "“$name” is already in your group") }
                return@launch
            }
            val player = original?.copy(name = name, color = current.color, avatarId = current.avatarId) ?: Player(
                id = UUID.randomUUID().toString(),
                name = name,
                color = current.color,
                avatarId = current.avatarId,
                createdAt = System.currentTimeMillis(),
            )
            players.upsert(player)
            state.update { it.copy(saving = false, saved = true) }
        }
    }
}
