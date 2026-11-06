package dev.quinton.goa.feature.board.play.table

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Upgrade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.CoinIcon
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.TeamBadge
import dev.quinton.goa.feature.board.ui.badgeOverhang

/**
 * The event log (a sheet): every event of the game in the engine's words, a plate per turn, the latest turn first
 * ([logTurns]) and its events in the order they happened, so cause reads before effect. Each line has the hero it is
 * about (portrait in the team ring with the mark; a minion's team mark) and an icon for what happened (attack,
 * defense, move, push, defeat, Life counter, level, coins, cards); lines not to miss are semibold on a bronze bar.
 * The bookkeeping (the actions chosen, the reveal, cards played facedown) folds under the turn's "Details".
 * [at]: the event count at a decision of the review; the line before it is where the game stood (marked, its turn
 * open and scrolled to). [table] names the heroes and teams; without it the lines have their icons only.
 */
@Composable
fun EventLog(lines: List<LogLine>, modifier: Modifier = Modifier, at: Int? = null, table: TableUi? = null) {
    val marked = at?.minus(1)?.takeIf { it in lines.indices }
    val turns = remember(lines) { logTurns(lines) }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = marked?.let { m ->
            turns.indexOfFirst { t -> (t.headings + t.events).any { it.index == m } }
        }?.coerceAtLeast(0) ?: 0,
    )
    LazyColumn(
        modifier.fillMaxWidth(),
        state = state,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            bottom = Spacing.lg,
        ),
    ) {
        itemsIndexed(turns, key = { _, turn -> (turn.headings + turn.events).first().index }) { _, turn ->
            TurnPlate(turn, marked, table)
        }
    }
}

@Composable
private fun TurnPlate(turn: LogTurn, marked: Int?, table: TableUi?) {
    val shown = turn.events.filter { !it.value.detail }
    val details = turn.events.filter { it.value.detail }
    val key = (turn.headings + turn.events).first().index
    var open by rememberSaveable(key) { mutableStateOf(details.any { it.index == marked }) }
    PlateSurface(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val title = turnTitle(turn)
            if (title != null) {
                Text(
                    title.uppercase(),
                    style = GoaTextStyles.overline,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            shown.forEach { (index, line) -> LogRow(line, index == marked, table) }
            if (details.isNotEmpty()) {
                DetailsToggle(details.size, open) { open = !open }
                if (open) details.forEach { (index, line) -> LogRow(line, index == marked, table, quiet = true) }
            }
        }
    }
}

/**
 * A turn's title as the top bar says it ("Round 1 · Turn 4"); a round's own heading ("Round 1 begins.") only when no
 * turn heading says the round already.
 */
private fun turnTitle(turn: LogTurn): String? {
    val texts = turn.headings.map { it.value.text.trim() }
    val turns = texts.mapNotNull { text ->
        TURN_HEADING.matchEntire(text)?.let { m -> "Round ${m.groupValues[2]} · Turn ${m.groupValues[1]}" }
    }
    return turns.ifEmpty { texts.map { it.removeSuffix(".") } }.joinToString(" · ").ifEmpty { null }
}

private val TURN_HEADING = Regex("Turn (\\d+) of round (\\d+)\\.?")

@Composable
private fun DetailsToggle(count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (open) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "Details · $count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** One event: who it is about, an icon for what happened, the engine's sentence. */
@Composable
private fun LogRow(line: LogLine, marked: Boolean, table: TableUi?, quiet: Boolean = false) {
    val hero = line.actor?.let { id -> table?.heroView(id) }
    val minionTeam = if (hero ==
        null
    ) {
        line.actor?.let { id -> table?.view?.minions?.firstOrNull { it.id == id }?.team }
    } else {
        null
    }
    val strong = line.strong && !quiet
    val background = when {
        marked -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(6.dp))
            .then(if (strong) Modifier.border(1.5.dp, StrongEdge, RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = if (marked || strong) 6.dp else 0.dp, vertical = if (marked || strong) 4.dp else 0.dp)
            .clearAndSetSemantics { contentDescription = line.text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(scaledIconSize(if (quiet) 20.dp else 26.dp, 1.3f)), contentAlignment = Alignment.Center) {
            when {
                hero != null && table != null -> LogPortrait(table, hero.heroId, hero.team, quiet)
                minionTeam != null -> TeamMark(minionTeam, markSize = scaledIconSize(13.dp, 1.3f))
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        KindIcon(line.kind, quiet)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            line.text,
            style = if (quiet) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else null,
            color = when {
                marked -> MaterialTheme.colorScheme.onSecondaryContainer
                quiet -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LogPortrait(table: TableUi, heroId: String, team: Team, quiet: Boolean) {
    val size = scaledIconSize(if (quiet) 18.dp else 24.dp, 1.3f)
    Box {
        HeroPortrait(
            heroId,
            table.hero(heroId).portrait,
            null,
            Modifier.size(size).border(1.5.dp, teamColor(team), CircleShape),
            shape = CircleShape,
            framed = false,
        )
        if (!quiet) TeamBadge(team, Modifier.align(Alignment.BottomEnd).badgeOverhang(LOG_BADGE), size = LOG_BADGE)
    }
}

/** What happened as an icon (the sentence says it too): the game's own where it has one. */
@Composable
private fun KindIcon(kind: LogKind, quiet: Boolean) {
    val size = scaledIconSize(if (quiet) 14.dp else 18.dp, 1.3f)
    if (kind == LogKind.COINS) {
        CoinIcon(size = size)
        return
    }
    val icon: ImageVector = when (kind) {
        LogKind.ATTACK -> GameIcons.attack.vector
        LogKind.DEFEND -> GameIcons.defense.vector
        LogKind.MOVE -> GameIcons.movement.vector
        LogKind.PUSH -> Icons.Outlined.KeyboardDoubleArrowRight
        LogKind.DEFEAT -> Icons.Outlined.HeartBroken
        LogKind.REMOVE -> Icons.Outlined.Close
        LogKind.LIFE -> Icons.Outlined.FavoriteBorder
        LogKind.LEVEL -> Icons.Outlined.Upgrade
        LogKind.CARDS -> Icons.Outlined.Style
        LogKind.ACTION -> Icons.Outlined.TouchApp
        LogKind.ROUND -> Icons.Outlined.Flag
        LogKind.COINS, LogKind.OTHER -> Icons.Outlined.Info
    }
    val tint = when (kind) {
        LogKind.DEFEAT, LogKind.LIFE -> MaterialTheme.colorScheme.error
        LogKind.ATTACK, LogKind.PUSH, LogKind.LEVEL -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(size))
}

private val LOG_BADGE = 11.dp
private val StrongEdge = Color(0xFFC9A35B)
