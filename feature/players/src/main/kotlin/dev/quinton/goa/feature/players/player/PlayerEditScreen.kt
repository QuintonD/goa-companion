package dev.quinton.goa.feature.players.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.players.common.AVATAR_COUNT
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.Motion
import dev.quinton.goa.feature.players.common.PlayerPalette
import dev.quinton.goa.feature.players.common.SectionHeader

data class PlayerEditActions(
    val onBack: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onColorChange: (Long) -> Unit = {},
    val onAvatarChange: (Int) -> Unit = {},
    val onSave: () -> Unit = {},
)

@Composable
fun PlayerEditScreen(state: PlayerEditUiState, actions: PlayerEditActions, modifier: Modifier = Modifier) {
    LaunchedEffect(state.saved) { if (state.saved) actions.onBack() }
    GoaScreen(
        title = if (state.isNew) "New player" else "Edit player",
        modifier = modifier,
        onBack = actions.onBack,
        actions = {
            if (!state.loading && !state.notFound) {
                TextButton(onClick = actions.onSave, enabled = !state.saving) { Text("Save") }
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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { actions.onSave() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionHeader("Colour")
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    PlayerPalette.swatches.forEach { swatch ->
                        ColorSwatch(
                            color = Color(swatch.argb.toInt()),
                            name = swatch.name,
                            selected = swatch.argb == state.color,
                            onClick = { actions.onColorChange(swatch.argb) },
                        )
                    }
                }
                SectionHeader("Crest")
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    repeat(AVATAR_COUNT) { id ->
                        val selected = id == state.avatarId
                        val ring by animateDpAsState(if (selected) 3.dp else 0.dp, Motion.spec(), label = "crest-ring")
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(ring, MaterialTheme.colorScheme.primary, CircleShape)
                                .selectable(selected = selected, role = Role.RadioButton) { actions.onAvatarChange(id) }
                                .semantics { contentDescription = "Crest ${id + 1}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            PlayerAvatar(state.preview.copy(avatarId = id), size = 44.dp)
                        }
                    }
                }
                Text(
                    "Colour and crest identify the player on the leaderboard and in match records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = Spacing.xl),
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, Motion.spec(), label = "swatch-ring")
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(ring, MaterialTheme.colorScheme.onSurface, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }
            .padding(6.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF0B1622))
    }
}
