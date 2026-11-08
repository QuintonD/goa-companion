package dev.quinton.goa.feature.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.icon.Destination
import dev.quinton.goa.core.designsystem.icon.icon
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing

/** Where each row of More goes; the entry maps these to Navigation 3 keys. */
enum class MoreLink { DRAFT, RULES, GROUP_SYNC, BACKUP, SETTINGS, ABOUT }

/**
 * The bottom bar's fifth item: every feature that has no tab of its own, each a labelled row with its icon and
 * what it is for, so nothing hides behind an unlabelled top-bar glyph.
 */
@Composable
fun MoreScreen(onOpen: (MoreLink) -> Unit, modifier: Modifier = Modifier) {
    GoaScreen(title = "More", modifier = modifier) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xl,
            ),
        ) {
            item { SectionHeader("At the table") }
            item {
                MoreCard {
                    MoreRow(
                        rememberVectorPainter(Destination.DRAFT.icon),
                        Destination.DRAFT.label,
                        "Pick and ban heroes for tonight's teams, then set up the match",
                    ) { onOpen(MoreLink.DRAFT) }
                }
            }
            item { SectionHeader("Reference") }
            item {
                MoreCard {
                    MoreRow(
                        rememberVectorPainter(Destination.RULES.icon),
                        Destination.RULES.label,
                        "The level and defeat ladder and the rulebook's quick reference, with page numbers",
                    ) { onOpen(MoreLink.RULES) }
                }
            }
            item { SectionHeader("Your group") }
            item {
                MoreCard {
                    MoreRow(
                        rememberVectorPainter(Destination.GROUP_SYNC.icon),
                        Destination.GROUP_SYNC.label,
                        "Share matches and players with your friends' phones",
                    ) { onOpen(MoreLink.GROUP_SYNC) }
                    MoreDivider()
                    MoreRow(
                        rememberVectorPainter(Icons.Outlined.Backup),
                        "Backup and restore",
                        "Export or import all your data and settings",
                    ) { onOpen(MoreLink.BACKUP) }
                }
            }
            item { SectionHeader("App") }
            item {
                MoreCard {
                    MoreRow(
                        rememberVectorPainter(Icons.Outlined.Settings),
                        "Settings",
                        "Theme, board style, your collection, sounds, presets and ratings",
                    ) { onOpen(MoreLink.SETTINGS) }
                    MoreDivider()
                    MoreRow(
                        rememberVectorPainter(Icons.Outlined.Info),
                        "About",
                        "The app, its sources and licences",
                    ) { onOpen(MoreLink.ABOUT) }
                }
            }
        }
    }
}

@Composable
private fun MoreCard(content: @Composable () -> Unit) {
    GoaCard(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) { content() }
}

@Composable
private fun MoreDivider() {
    HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = GoaTheme.colors.engravedBorder)
}

/** One destination: the whole row is the button, read as its title and what it is for. */
@Composable
private fun MoreRow(icon: Painter, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(26.dp, 1.4f)),
            )
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
