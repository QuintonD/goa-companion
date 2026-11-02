package dev.quinton.goa.feature.board.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.SettingsActionButton
import dev.quinton.goa.core.designsystem.theme.Spacing

/** The Play tab's first screen (ADR-055): Board, which leads on to games and puzzles, and Card trivia. */
@Composable
fun PlayHomeScreen(
    onBoard: () -> Unit,
    onTrivia: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GoaScreen(title = "Play", modifier = modifier, actions = { SettingsActionButton(onSettings) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            EntryCard(
                icon = Icons.Outlined.Hexagon,
                title = "Board",
                text = "The real map for any line-up, games against the computer, and puzzles.",
                enabled = true,
                onClick = onBoard,
            )
            EntryCard(
                icon = Icons.Outlined.Quiz,
                title = "Card trivia",
                text = "A quiz on the numbers printed on every hero's cards, with your group's leaderboard.",
                enabled = true,
                onClick = onTrivia,
            )
        }
    }
}
