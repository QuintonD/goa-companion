package dev.quinton.goa.feature.trivia.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.trivia.TriviaText

/** Pick the heroes to quiz: all of them, or any you choose. Changes apply as you tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeroPickerSheet(
    options: List<HeroOption>,
    selected: List<String>,
    ownedOnly: Boolean,
    ownedCount: Int,
    onToggle: (String) -> Unit,
    onAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        HeroPickerContent(options, selected, ownedOnly, ownedCount, onToggle, onAll, onDone = onDismiss)
    }
}

/**
 * [options] are the owned heroes plus any chosen hero outside them, so "All heroes" counts
 * [ownedCount] (the owned heroes), not the options.
 */
@Composable
internal fun HeroPickerContent(
    options: List<HeroOption>,
    selected: List<String>,
    ownedOnly: Boolean,
    ownedCount: Int,
    onToggle: (String) -> Unit,
    onAll: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val shown = options.filter { option ->
        query.isBlank() ||
            option.name.contains(query.trim(), ignoreCase = true) ||
            option.title?.contains(query.trim(), ignoreCase = true) == true
    }
    Column(modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Heroes to quiz",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (selected.isEmpty()) {
                        "All heroes"
                    } else {
                        TriviaText.count(selected.size, "hero", "heroes") +
                            " chosen"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDone) { Text("Done") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            placeholder = { Text("Search heroes", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        LazyColumn(Modifier.weight(1f)) {
            if (query.isBlank()) {
                item(key = "all") {
                    PickerRow(
                        checked = selected.isEmpty(),
                        onCheckedChange = { if (it) onAll() },
                        title = "All heroes",
                        subtitle = if (ownedOnly) {
                            "The ${TriviaText.count(ownedCount, "hero", "heroes")} you own (Settings)"
                        } else {
                            "Every hero, $ownedCount in all"
                        },
                        leading = {
                            Icon(
                                Icons.Outlined.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(scaledIconSize(28.dp)),
                            )
                        },
                    )
                }
            }
            items(shown, key = { it.heroId }) { option ->
                PickerRow(
                    checked = option.heroId in selected,
                    onCheckedChange = { onToggle(option.heroId) },
                    title = option.name,
                    subtitle = option.title,
                    verified = option.verified,
                    leading = {
                        HeroPortrait(
                            heroId = option.heroId,
                            portrait = option.portrait,
                            contentDescription = null,
                            modifier = Modifier.size(scaledIconSize(40.dp, maxScale = 1.5f)),
                            shape = CircleShape,
                        )
                    },
                )
            }
            if (shown.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No hero matches “${query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.screen),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    verified: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!verified) {
                    Spacer(Modifier.width(Spacing.sm))
                    UnverifiedBadge()
                }
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

private const val SHEET_HEIGHT = 0.9f
