package dev.quinton.goa.feature.players.match

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.players.HeroOption
import dev.quinton.goa.core.presentation.players.MAX_NAME_LENGTH
import dev.quinton.goa.core.presentation.players.PilotOption
import dev.quinton.goa.core.presentation.players.formatRating
import dev.quinton.goa.core.presentation.players.label
import dev.quinton.goa.feature.players.common.Pill

/** Filters heroes by name or title, case-insensitive. */
fun List<HeroOption>.search(query: String): List<HeroOption> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.title.contains(q, ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroPickerSheet(
    heroes: List<HeroOption>,
    selected: String?,
    taken: Set<String>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = Spacing.screen)) {
            Text(
                "Choose hero",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics {
                    heading()
                },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search heroes") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
            )
        }
        val results = heroes.search(query)
        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl), modifier = Modifier.navigationBarsPadding()) {
            if (query.isBlank()) {
                item(key = "unknown") {
                    PickerRow(
                        selected = selected == null,
                        enabled = true,
                        onClick = { onPick(null) },
                        leading = {
                            Icon(
                                Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).padding(Spacing.sm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        title = "Unknown hero",
                        subtitle = "Counts for the pilot's rating only",
                    )
                }
            }
            if (heroes.isEmpty()) {
                item(key = "no-catalog") {
                    Text(
                        "The hero catalog isn't available, so only “Unknown hero” can be chosen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.screen),
                    )
                }
            } else if (results.isEmpty()) {
                item(key = "no-results") {
                    Text(
                        "No hero matches “$query”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.screen),
                    )
                }
            }
            items(results, key = { it.id }) { hero ->
                val inUse = hero.id in taken
                PickerRow(
                    selected = hero.id == selected,
                    enabled = !inUse,
                    onClick = { onPick(hero.id) },
                    leading = {
                        HeroPortrait(hero.id, hero.portrait, contentDescription = null, modifier = Modifier.size(40.dp))
                    },
                    title = hero.name,
                    subtitle = hero.title,
                    trailing = {
                        if (inUse) Pill("In this match") else ComplexityStars(hero.complexity)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilotPickerSheet(
    team: Team,
    pilots: List<PilotOption>,
    selected: String?,
    sameTeam: Set<String>,
    otherTeam: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Adds a new player by name and seats them; never a dead end when the group is empty. */
    onAddPlayer: ((String) -> Unit)? = null,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = Spacing.screen)) {
            Text(
                "Pilot for ${team.label()}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics {
                    heading()
                },
            )
            Text(
                "Pick the same player for two seats to record a double pilot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl), modifier = Modifier.navigationBarsPadding()) {
            if (onAddPlayer != null) {
                item(key = "add") {
                    val add = {
                        if (newName.isNotBlank()) {
                            onAddPlayer(newName)
                            newName = ""
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.take(MAX_NAME_LENGTH) },
                            placeholder = {
                                Text(if (pilots.isEmpty()) "Name of the first player" else "Someone missing? Add them")
                            },
                            leadingIcon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        TextButton(onClick = add, enabled = newName.isNotBlank()) { Text("Add") }
                    }
                }
            } else if (pilots.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No players yet. Add your group on the Players tab first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.screen),
                    )
                }
            }
            items(pilots, key = { it.player.id }) { option ->
                val id = option.player.id
                val onOther = id in otherTeam
                PickerRow(
                    selected = id == selected,
                    enabled = !onOther,
                    onClick = { onPick(id) },
                    leading = { PlayerAvatar(option.player, size = 40.dp) },
                    title = option.player.name,
                    subtitle = if (option.rating.games > 0) "Elo ${formatRating(option.rating.value)}" else "Unrated",
                    trailing = {
                        when {
                            onOther -> Pill("On ${team.opponent.label()}")
                            id in sameTeam -> Pill("Double pilot", color = MaterialTheme.colorScheme.secondary)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PickerRow(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (!enabled) contentDescription = "$title, unavailable"
            }
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
        if (selected) {
            Spacer(Modifier.width(Spacing.sm))
            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.secondary)
        }
    }
}
