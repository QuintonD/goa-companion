package dev.quinton.goa.feature.board.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.AccountAvatar
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.FootArt
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaProgressBar
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaSegment
import dev.quinton.goa.core.designsystem.component.GoaSegmentedRow
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PODIUM_PLACES
import dev.quinton.goa.core.designsystem.component.Podium
import dev.quinton.goa.core.designsystem.component.PodiumFacts
import dev.quinton.goa.core.designsystem.component.PodiumPlace
import dev.quinton.goa.core.designsystem.component.ScreenFoot
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardPresenter
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardRowUi
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardTab
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardUi

/**
 * The public leaderboards (ADR-056), named for what they rank: "Online ratings", Players and Heroes, a line on
 * whose games count, the columns labelled (Rank, Player or Hero, Rating), your row marked, how old the board is.
 */
@Composable
fun OnlineLeaderboardScreen(
    state: LeaderboardUi,
    onTab: (LeaderboardTab) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Each hero's portrait by id, for the Heroes tab's podium. */
    portraits: Map<String, String?> = emptyMap(),
) {
    GoaScreen(
        title = "Online ratings",
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = onRefresh, enabled = !state.loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        },
    ) { padding ->
        // A short board ends in the rulebook's battlefield, not in empty navy.
        Box(Modifier.fillMaxSize().padding(padding)) {
            ScreenFoot(FootArt.HALL)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                GoaSegmentedRow(Modifier.fillMaxWidth()) {
                    state.tabs.forEachIndexed { i, tab ->
                        GoaSegment(
                            selected = state.tab == tab,
                            onClick = { onTab(tab) },
                        ) { Text(tab.label) }
                    }
                }
                Text(
                    "Public Elo ratings from ranked online games. A " +
                        (if (state.tab == LeaderboardTab.HEROES) "hero" else "player") +
                        " appears after ${LeaderboardPresenter.GAMES_TO_APPEAR} rated games.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.status?.let { Status(it, state.stale) }
                when {
                    state.rows.isNotEmpty() -> {
                        if (state.loading) GoaProgressBar(null, Modifier.fillMaxWidth())
                        Rows(state, portraits)
                    }

                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

                    else -> EmptyState(
                        icon = Icons.Outlined.EmojiEvents,
                        title = "Nothing here yet",
                        message = state.empty.orEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Status(text: String, stale: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        if (stale) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = GoaTheme.colors.unverified,
                modifier = Modifier.size(scaledIconSize(16.dp)),
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (stale) GoaTheme.colors.unverified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One plate for the board, dividers inside (DESIGN.md), under a header that says what each number is. */
@Composable
private fun Rows(state: LeaderboardUi, portraits: Map<String, String?>) {
    val heroes = state.tab == LeaderboardTab.HEROES
    // The top three on a podium while three columns have room; the rest as rows under it, in the same plate.
    val podium = state.rows.size >= PODIUM_PLACES && LocalDensity.current.fontScale < PODIUM_FONT_SCALE
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        if (podium) {
            Podium(
                state.rows.take(PODIUM_PLACES).map { row ->
                    PodiumPlace(place = row.rank, label = row.rank.toString(), description = spoken(row)) {
                        Standing(row, heroes, portraits[row.id])
                    }
                },
                Modifier.padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.md),
            )
        }
        val rows = if (podium) state.rows.drop(PODIUM_PLACES) else state.rows
        if (rows.isNotEmpty()) {
            if (podium) HorizontalDivider(color = GoaTheme.colors.engravedBorder)
            Header(if (heroes) "Hero" else "Player")
        }
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = GoaTheme.colors.engravedBorder)
            BoardRow(row, heroes, portraits[row.id])
        }
    }
}

/**
 * A podium place: the hero's portrait, or for a player the crest their account id picks (the board carries no
 * hero per account, and a bare initial read as unfinished); the name, the rating, the record.
 */
@Composable
private fun Standing(row: LeaderboardRowUi, hero: Boolean, portrait: String?) {
    val size = if (row.rank == 1) 60.dp else 50.dp
    if (hero) {
        HeroPortrait(row.id, portrait, null, Modifier.size(size), CircleShape)
    } else {
        AccountAvatar(row.id, row.name, size = size)
    }
    Spacer(Modifier.height(Spacing.xs))
    Text(
        row.name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (row.isYou) Text("You", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Text(
        row.rating.toString(),
        style = GoaTextStyles.scoreMedium,
        color = if (row.rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
    PodiumFacts(listOf("${row.wins} wins", "${row.games} games"))
}

private fun spoken(row: LeaderboardRowUi): String = buildString {
    append("Rank ${row.rank}: ${row.name}")
    if (row.isYou) append(" (you)")
    append(", rating ${row.rating}, ${row.wins} wins in ${row.games} games")
}

/** A row's crest or portrait. */
private val ROW_MARK = 36.dp

/** From this font scale the podium's three columns give way to rows. */
private const val PODIUM_FONT_SCALE = 1.3f

/** "# · PLAYER · RATING": the columns, for the eye (each row's TalkBack line says them). */
@Composable
private fun Header(what: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .padding(top = Spacing.sm, bottom = Spacing.xs)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = GoaTextStyles.overline
        val color = MaterialTheme.colorScheme.primary
        Text("#", style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 28.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(what.uppercase(), style = style, color = color, modifier = Modifier.weight(1f))
        Text("RATING", style = style, color = color)
    }
    HorizontalDivider(color = GoaTheme.colors.engravedBorder)
}

/** A row under the podium: rank, the crest (a player's, as on the podium) or the hero's portrait, name, rating. */
@Composable
private fun BoardRow(row: LeaderboardRowUi, hero: Boolean, portrait: String?) {
    val spoken = spoken(row)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (row.isYou) Modifier.background(GoaTheme.colors.bronzeGlow) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .heightIn(min = 40.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.rank.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        if (hero) {
            HeroPortrait(row.id, portrait, null, Modifier.size(ROW_MARK), CircleShape)
        } else {
            AccountAvatar(row.id, row.name, size = ROW_MARK)
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isYou) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text("You", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "${row.wins} wins · ${row.games} games",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(row.rating.toString(), style = GoaTextStyles.score)
    }
}
