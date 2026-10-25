package dev.quinton.goa.feature.board.play.review

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.play.game.PlaySheet
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.ui.cards.CardFocusState

/** The top bar's way into the review: one tap from the game and the puzzle screens. */
@Composable
fun ReviewButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.Dashboard, contentDescription = "Game state: cards played, turns, the board")
    }
}

/**
 * The review in its sheet. A card opens the card focus viewer over it (view only, swiping through the
 * row's face-up cards); back or an upward swipe returns to the review. [onSheet] switches the
 * screen's sheet.
 */
@Composable
fun ReviewSheet(
    table: TableUi,
    review: ReviewUi?,
    open: PlaySheet.Review,
    onSheet: (PlaySheet?) -> Unit,
    onAction: (ReviewAction) -> Unit,
    focus: CardFocusState,
    puzzle: Boolean = false,
) {
    BoardSheet(onDismiss = { onSheet(null) }) {
        GameReviewContent(
            table = table,
            review = review,
            tab = open.tab,
            onTab = { onSheet(PlaySheet.Review(it)) },
            onOpenCard = { cards, index -> focus.show(cards, index, title = "Game state") },
            onAction = onAction,
            onDone = { onSheet(null) },
            puzzle = puzzle,
        )
    }
}

/** Tells the ViewModel while the review is showing (a card read over it included): it builds the history only then. */
@Composable
fun ReviewOpenEffect(sheet: PlaySheet?, onAction: (ReviewAction) -> Unit) {
    val reviewing = sheet is PlaySheet.Review
    LaunchedEffect(reviewing) { onAction(ReviewAction.Open(reviewing)) }
}
