package dev.quinton.goa.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

/**
 * The app's recurring destinations, each with one glyph and one name wherever a screen links to it (DESIGN.md
 * "Destinations"): a flag is always Match setup, the hourglass the Timer, the fanned cards the Draft, the open book
 * the Rules, the two arrows Group sync (always said in words: alone, the glyph reads as "refresh").
 */
enum class Destination(val label: String) {
    MATCH_SETUP("Match setup"),
    TIMER("Timer"),
    DRAFT("Draft"),
    RULES("Rules"),
    GROUP_SYNC("Group sync"),
    BOARD("Board"),
}

/** The destination's one glyph. */
val Destination.icon: ImageVector
    @Composable get() = when (this) {
        Destination.MATCH_SETUP -> Icons.Outlined.Flag
        Destination.TIMER -> ImageVector.vectorResource(GoaIcons.NavTimer)
        Destination.DRAFT -> ImageVector.vectorResource(GoaIcons.NavDraft)
        Destination.RULES -> Icons.AutoMirrored.Outlined.MenuBook
        Destination.GROUP_SYNC -> Icons.Outlined.Sync
        Destination.BOARD -> Icons.Outlined.Hexagon
    }
