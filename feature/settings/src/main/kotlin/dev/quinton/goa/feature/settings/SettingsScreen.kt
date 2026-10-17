package dev.quinton.goa.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.settings.ThemeMode

/** Where each settings link goes; the route maps these to Navigation 3 keys. */
enum class SettingsLink { TIMER_PRESETS, DRAFT_METHODS, RATINGS, BACKUP, ABOUT }

@Composable
fun SettingsRoute(onBack: () -> Unit, onOpen: (SettingsLink) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onAction = viewModel::onAction, onBack = onBack, onOpen = onOpen)
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
    onOpen: (SettingsLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    GoaScreen(title = "Settings", onBack = onBack, modifier = modifier) { padding ->
        when (state) {
            SettingsUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            is SettingsUiState.Ready -> SettingsList(state, onAction, onOpen, padding)
        }
    }
}

@Composable
private fun SettingsList(
    state: SettingsUiState.Ready,
    onAction: (SettingsAction) -> Unit,
    onOpen: (SettingsLink) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + Spacing.xl,
        ),
    ) {
        item { SectionHeader("Appearance") }
        item {
            GoaCard(Modifier.fillMaxWidth()) {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Dark is easiest on the eyes at the table.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(Spacing.md))
                ThemeSelector(state.theme) { onAction(SettingsAction.SetTheme(it)) }
            }
        }

        item { SectionHeader("Collection") }
        item {
            GoaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                SwitchRow(
                    icon = Icons.Outlined.Inventory2,
                    title = "I own every product",
                    subtitle = if (state.ownsEverything) {
                        "All heroes are available in drafts and filters"
                    } else {
                        "${state.ownedCount} of ${state.products.size} products owned"
                    },
                    checked = state.ownsEverything,
                    onCheckedChange = { onAction(SettingsAction.SetOwnsEverything(it)) },
                )
                if (state.products.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = GoaTheme.colors.engravedBorder)
                    state.products.forEach { p ->
                        ProductRow(p, enabled = !state.ownsEverything) { onAction(SettingsAction.ToggleProduct(p.id)) }
                    }
                }
            }
        }

        item { SectionHeader("At the table") }
        item {
            GoaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                SwitchRow(
                    icon = Icons.Outlined.Vibration,
                    title = "Haptics",
                    subtitle = "Vibrate on timer cues and draft turns",
                    checked = state.hapticsEnabled,
                    onCheckedChange = { onAction(SettingsAction.SetHaptics(it)) },
                )
                SwitchRow(
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    title = "Sounds",
                    subtitle = "Play timer cues and phase alerts",
                    checked = state.soundsEnabled,
                    onCheckedChange = { onAction(SettingsAction.SetSounds(it)) },
                )
            }
        }

        item { SectionHeader("Game setup") }
        item {
            GoaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                LinkRow(Icons.Outlined.Timer, "Timer presets", "Phase lengths, cues and auto-advance") {
                    onOpen(SettingsLink.TIMER_PRESETS)
                }
                LinkRow(Icons.Outlined.SwapHoriz, "Draft methods", "House draft, official methods, your own") {
                    onOpen(SettingsLink.DRAFT_METHODS)
                }
                LinkRow(Icons.Outlined.Leaderboard, "Ratings", "Elo constants and double-pilot weighting") {
                    onOpen(SettingsLink.RATINGS)
                }
            }
        }

        item { SectionHeader("Data") }
        item {
            GoaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                LinkRow(Icons.Outlined.Backup, "Backup and restore", "Export or import players, matches and drafts") {
                    onOpen(SettingsLink.BACKUP)
                }
                LinkRow(Icons.Outlined.Info, "About", "Version, credits and licences") { onOpen(SettingsLink.ABOUT) }
            }
        }
    }
}

private data class ThemeOption(val mode: ThemeMode, val label: String, val icon: ImageVector)

private val ThemeOptions = listOf(
    ThemeOption(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
    ThemeOption(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
    ThemeOption(ThemeMode.SYSTEM, "System", Icons.Outlined.Contrast),
)

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
        // Large text: three segments can't fit their labels, so use a radio list.
        Column(Modifier.selectableGroup()) {
            ThemeOptions.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = option.mode == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option.mode) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option.mode == selected, onClick = null)
                    Spacer(Modifier.width(Spacing.md))
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        return
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThemeOptions.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.mode == selected,
                onClick = { onSelect(option.mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeOptions.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = option.mode == selected) {
                        Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    activeContentColor = MaterialTheme.colorScheme.onSurface,
                    activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = GoaTheme.colors.engravedBorder,
                ),
            ) { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    Spacer(Modifier.width(Spacing.lg))
}

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        RowText(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.md))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
private fun ProductRow(product: ProductOption, enabled: Boolean, onToggle: () -> Unit) {
    val detail = listOfNotNull(
        product.released?.toString(),
        if (product.heroCount > 0) "${product.heroCount} heroes" else null,
    ).joinToString(" · ").ifEmpty { null }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = product.owned, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = Spacing.lg + 24.dp + Spacing.lg, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(product.name, detail, Modifier.weight(1f))
        Checkbox(checked = product.owned, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(Spacing.sm))
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            RowIcon(icon)
            RowText(title, subtitle, Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal val PreviewSettings = SettingsUiState.Ready(
    theme = ThemeMode.DARK,
    ownsEverything = false,
    products = listOf(
        ProductOption("core", "Core", 2022, 7, owned = true),
        ProductOption("devoted", "Devoted", 2022, 5, owned = true),
        ProductOption("arcane", "Arcane", 2025, 5, owned = false),
    ),
    hapticsEnabled = true,
    soundsEnabled = false,
)

@Preview
@Composable
private fun SettingsScreenPreview() {
    GoaTheme { SettingsScreen(PreviewSettings, onAction = {}, onBack = {}, onOpen = {}) }
}

@Preview
@Composable
private fun SettingsScreenLightPreview() {
    GoaTheme(ThemeMode.LIGHT) { SettingsScreen(PreviewSettings, onAction = {}, onBack = {}, onOpen = {}) }
}
