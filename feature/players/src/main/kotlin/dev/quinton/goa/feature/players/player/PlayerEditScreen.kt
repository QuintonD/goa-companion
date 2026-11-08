package dev.quinton.goa.feature.players.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.BannerTextButton
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.selectionFrame
import dev.quinton.goa.core.designsystem.icon.PlayerCrests
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.SectionHeader

data class PlayerEditActions(
    val onBack: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onAvatarChange: (Int) -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 * A player's name and crest. Crests are team-neutral (bronze on steel): red and blue are the teams'
 * colours, so a player has no colour to pick (`Player.color` stays in the data for older phones).
 */
@Composable
fun PlayerEditScreen(state: PlayerEditUiState, actions: PlayerEditActions, modifier: Modifier = Modifier) {
    LaunchedEffect(state.saved) { if (state.saved) actions.onBack() }
    GoaScreen(
        title = if (state.isNew) "New player" else "Edit player",
        modifier = modifier,
        onBack = actions.onBack,
        actions = {
            if (!state.loading && !state.notFound) {
                BannerTextButton("Save", onClick = actions.onSave, enabled = !state.saving)
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))

            state.notFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.PersonOff, "Player not found", "This player no longer exists.")
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.md))
                PlayerAvatar(state.preview, size = 96.dp)
                Spacer(Modifier.height(Spacing.lg))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = actions.onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = {
                        Text(state.nameError ?: "${state.name.length} / $MAX_NAME_LENGTH")
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { actions.onSave() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionHeader("Crest")
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    repeat(PlayerCrests.count) { id ->
                        val selected = id == state.avatarId
                        // The one selection look: a bronze edge over a bronze tint around the shield.
                        Box(
                            Modifier
                                .size(56.dp)
                                .selectionFrame(selected, RoundedCornerShape(12.dp))
                                .selectable(selected = selected, role = Role.RadioButton) { actions.onAvatarChange(id) }
                                .semantics {
                                    contentDescription = PlayerCrests.names.getOrElse(id) { "Crest ${id + 1}" }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            PlayerAvatar(state.preview.copy(avatarId = id), size = 44.dp)
                        }
                    }
                }
                Text(
                    "The crest marks the player on the leaderboard and in match records. Team colours stay " +
                        "with the teams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = Spacing.xl),
                )
            }
        }
    }
}
