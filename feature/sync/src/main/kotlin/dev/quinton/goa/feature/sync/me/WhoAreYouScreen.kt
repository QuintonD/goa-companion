package dev.quinton.goa.feature.sync.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.theme.Spacing

class WhoAreYouActions(
    val onBack: () -> Unit = {},
    val onChoose: (String) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onConfirmInvited: () -> Unit = {},
    val onNotMe: () -> Unit = {},
    val onMergeOld: () -> Unit = {},
    val onKeepBoth: () -> Unit = {},
    val onRefusalShown: () -> Unit = {},
    val onAddAnyway: () -> Unit = {},
    val onDismissAddCheck: () -> Unit = {},
)

@Composable
fun WhoAreYouScreen(state: WhoAreYouUiState, actions: WhoAreYouActions) {
    GoaScreen(title = "Which player are you?", onBack = actions.onBack) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@GoaScreen
        }
        LazyColumn(
            Modifier.fillMaxSize().selectableGroup(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding() + Spacing.sm,
                bottom = padding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            state.invited?.let { invited -> item(key = "invited") { InvitedCard(invited, actions) } }
            item {
                Text(
                    "This phone opens your player page first and marks you as “You”. Only this phone knows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.players.isNotEmpty()) item { SectionHeader("Pick yourself") }
            items(state.players, key = { it.id }) { player ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = player.id == state.meId,
                            role = Role.RadioButton,
                            onClick = { actions.onChoose(player.id) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = player.id == state.meId, onClick = null)
                    Spacer(Modifier.width(Spacing.md))
                    PlayerAvatar(player, size = 32.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Text(player.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item { SectionHeader(if (state.players.isEmpty()) "Add yourself" else "Not in the list?") }
            item {
                OutlinedTextField(
                    value = state.newName,
                    onValueChange = actions.onNameChange,
                    label = { Text("Your name") },
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { actions.onCreate() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = actions.onCreate,
                    enabled = state.newName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = Spacing.sm),
                ) { Text("Add me to the group") }
            }
        }
    }
    LinkDialogs(state, actions)
}
