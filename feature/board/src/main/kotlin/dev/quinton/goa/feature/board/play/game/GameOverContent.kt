package dev.quinton.goa.feature.board.play.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.ActionIcon
import dev.quinton.goa.core.designsystem.component.CoinIcon
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.LookAction
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.SectionHeaderPaddingTight
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.PagePill
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.TeamBadge
import dev.quinton.goa.feature.board.ui.badgeOverhang
import dev.quinton.goa.feature.board.ui.cards.CardFocusState

/**
 * Game over: who won and how, in the rulebook's words; how each hero did as a grid (Level, Hero KOs, Minion KOs,
 * Deaths, Coins) with an icon over each column; take backs and nudges; the coach's turning points
 * (when Coach is on), each with its round and turn; the report; New game. Rematch (same setup, a new coin flip) and
 * Done are pinned under it, in reach without scrolling. Online (ADR-056): whose team won and whether it counted, with
 * no report, rematch or new game: those need a whole game, which a phone doesn't hold.
 */
@Composable
internal fun GameOverContent(
    play: PlayUi,
    over: GameOverUi,
    onAction: (GameAction) -> Unit,
    onNewGame: () -> Unit,
    onDone: () -> Unit,
    onHero: (String) -> Unit,
    focus: CardFocusState? = null,
    onLogAt: (Int) -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                ResultPlate(play, over)

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("How each hero did", padding = SectionHeaderPaddingTight)
                    // One plate for the section, dividers between its rows: not a frame round every row.
                    PlateSurface(brackets = true, modifier = Modifier.fillMaxWidth()) {
                        HeroResults(play, over, onHero)
                    }
                }

                // The review is the person's next step after the result: in view over the pinned bar, before the coach.
                if (play.online == null) GameReportSection(play, onAction, focus, onLogAt)

                if (over.coach) TurningPoints(over)

                if (play.online == null) {
                    GoaOutlinedButton(onClick = onNewGame, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("New game")
                    }
                }
            }
        }
        GameOverBar(online = play.online != null, onRematch = { onAction(GameAction.Rematch) }, onDone = onDone)
    }
}

/** Who won, how (the rulebook's words, its page as a small pill), and the game's length. */
@Composable
private fun ResultPlate(play: PlayUi, over: GameOverUi) {
    PlateSurface(
        brackets = true,
        border = BorderStroke(2.dp, teamColor(over.winner)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val winColor = teamColor(over.winner)
        Column(
            Modifier.padding(Spacing.lg).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                Icons.Outlined.EmojiEvents,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            TitlePlate(
                when {
                    play.online != null -> if (over.youWon) "Your team won" else "Your team lost"
                    over.youWon -> "You won"
                    else -> "The computer won"
                },
                Modifier.fillMaxWidth(),
                style = GoaTextStyles.plateTitle.copy(fontSize = 22.sp, lineHeight = 28.sp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamMark(over.winner, markSize = scaledIconSize(12.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    "${BoardText.team(over.winner)} wins · ${PlayText.winTitle(over.reason)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = winColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            WithPage(PlayText.winReason(over.reason, over.winner), centred = true) {
                Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Text(
                if (play.online != null) {
                    BoardText.count(over.rounds, "round")
                } else {
                    listOfNotNull(
                        BoardText.count(over.rounds, "round"),
                        BoardText.count(over.takeBacks, "take back"),
                        if (over.coach) BoardText.count(over.nudges, "coach nudge") else null,
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            play.online?.let { online ->
                ratedWords(online)?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun TurningPoints(over: GameOverUi) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Coach · turning points", padding = SectionHeaderPaddingTight)
        val points = over.turningPoints
        when {
            points == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.sm))
                Text("The coach is going through the game…", style = MaterialTheme.typography.bodyMedium)
            }

            points.isEmpty() -> Text(
                "No single moment decided this one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> PlateSurface(brackets = true, modifier = Modifier.fillMaxWidth()) {
                Column {
                    points.forEachIndexed { i, note ->
                        if (i > 0) HorizontalDivider(color = GoaTheme.colors.engravedBorder)
                        TurningPoint(note)
                    }
                }
            }
        }
    }
}

/**
 * Rematch (bronze, the common next step) and Done, pinned under the content on the slate, as every flow's commit is;
 * online, Done alone.
 */
@Composable
private fun GameOverBar(online: Boolean, onRematch: () -> Unit, onDone: () -> Unit) {
    GoaPlateTheme {
        Row(
            Modifier
                .fillMaxWidth()
                .slatePlate(RectangleShape, brackets = false)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (online) {
                GoaButton(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Done") }
            } else {
                GoaOutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text("Done")
                }
                GoaButton(
                    onClick = onRematch,
                    modifier = Modifier
                        .weight(REMATCH_SHARE)
                        .heightIn(min = 52.dp)
                        .semantics {
                            contentDescription =
                                "Rematch: same heroes and seats; the app flips the Tie Breaker coin again"
                        },
                ) {
                    Icon(Icons.Outlined.Replay, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Rematch")
                }
            }
        }
    }
}

/** Whether the result counts on the leaderboard; null until the server has said. */
private fun ratedWords(online: OnlineUi): String? = when (online.rated) {
    true -> "Rated: this result counts on the leaderboard."
    false -> if (online.ranked) "Not rated: this result doesn't count." else "A friendly room: not rated."
    null -> null
}

/** "(p.13)" and the like out of a sentence: the page goes in a pill after it ([WithPage]), never in the words. */
private fun withoutPages(text: String): String = text.replace(PAGES, "")

/** A sentence that cites the rulebook, its pages as small bronze pills after the words ("… counter." p.13). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WithPage(text: String, centred: Boolean = false, words: @Composable (String) -> Unit) {
    val pages = PAGES.findAll(text).flatMap { PAGE_NUMBER.findAll(it.value).map { m -> m.value } }.toList()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(
            Spacing.xs,
            if (centred) Alignment.CenterHorizontally else Alignment.Start,
        ),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        words(withoutPages(text))
        pages.forEach { PagePill(it) }
    }
}

private val PAGE_NUMBER = Regex("p\\.\\d+")

private val PAGES = Regex(" \\(p\\.\\d+(?:[,;] ?p\\.\\d+)*\\)")

@Composable
private fun TurningPoint(note: CoachNote) {
    Row(Modifier.padding(Spacing.md).semantics(mergeDescendants = true) {}) {
        Icon(Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(Spacing.sm))
        Column {
            val at = listOfNotNull(note.round?.let { "Round $it" }, note.turn?.let { "Turn $it" }).joinToString(" · ")
            if (at.isNotEmpty()) {
                Text(
                    at.uppercase(),
                    style = GoaTextStyles.overline,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                // The heading already says when: no "Round 1, turn 1:" again.
                withoutPages(if (at.isNotEmpty()) withoutWhen(note.text) else note.text),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (note.detail.isNotBlank()) {
                WithPage(note.detail) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

/** The grid's columns, in order: what each counts, its icon and its head. */
private enum class ResultColumn(val head: String, val spoken: String, val width: Dp) {
    LEVEL("Level", "Level", 42.dp),
    HEROES("Hero\nKOs", "heroes defeated", 50.dp),
    MINIONS("Minion\nKOs", "minions defeated", 56.dp),
    FALLEN("Deaths", "times defeated", 50.dp),
    COINS("Coins", "coins gained", 46.dp),
}

private fun ResultColumn.of(r: HeroResult): Int = when (this) {
    ResultColumn.LEVEL -> r.level
    ResultColumn.HEROES -> r.heroDefeats
    ResultColumn.MINIONS -> r.minionDefeats
    ResultColumn.FALLEN -> r.defeated
    ResultColumn.COINS -> r.coins
}

@Composable
private fun ColumnIcon(column: ResultColumn) {
    val size = scaledIconSize(20.dp, 1.3f)
    val tint = MaterialTheme.colorScheme.primary
    when (column) {
        ResultColumn.LEVEL -> Icon(Icons.Outlined.MilitaryTech, null, tint = tint, modifier = Modifier.size(size))
        ResultColumn.HEROES -> ActionIcon(LookAction.ATTACK, size = size)
        ResultColumn.MINIONS -> Icon(Icons.Outlined.Person, null, tint = tint, modifier = Modifier.size(size))
        ResultColumn.FALLEN -> Icon(Icons.Outlined.HeartBroken, null, tint = tint, modifier = Modifier.size(size))
        ResultColumn.COINS -> CoinIcon(size = size)
    }
}

/**
 * How each hero did, as a grid to compare across heroes at a glance: a head row of icons over short words, then a row
 * per hero (portrait in its team ring, team mark, name, who played it) with its figures in the same columns. With large
 * text the figures go under the name, the columns spanning the row. A row opens that hero's sheet.
 */
@Composable
private fun HeroResults(play: PlayUi, over: GameOverUi, onHero: (String) -> Unit) {
    val stacked = LocalDensity.current.fontScale > STACKED_SCALE
    val heroes = Team.entries.flatMap { team -> over.heroes.filter { it.team == team } }
    Column {
        if (!stacked) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm)
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.Bottom,
            ) {
                Spacer(Modifier.weight(1f))
                Heads(Modifier.width(FIGURES), fixed = true)
            }
        }
        heroes.forEachIndexed { i, result ->
            if (i > 0 || !stacked) HorizontalDivider(color = GoaTheme.colors.engravedBorder)
            HeroResultRow(play, result, stacked, onHero)
        }
    }
}

@Composable
private fun Heads(modifier: Modifier, fixed: Boolean = false) {
    // A head is a word over its column: it grows with the text only to 1.3×, never clipped.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, STACKED_SCALE))) {
        HeadRow(modifier, fixed)
    }
}

/** The heads in their columns: [fixed] widths beside the names, else shares of a row of their own. */
@Composable
private fun HeadRow(modifier: Modifier, fixed: Boolean) {
    Row(modifier) {
        ResultColumn.entries.forEach { column ->
            val cell = if (fixed) Modifier.width(column.width) else Modifier.weight(1f)
            Column(cell, horizontalAlignment = Alignment.CenterHorizontally) {
                ColumnIcon(column)
                val style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, letterSpacing = 0.sp)
                Text(
                    column.head,
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    // "Hero KOs" on two lines over its column; a line never breaks mid-word.
                    maxLines = 2,
                    softWrap = false,
                    // Heads never touch: a word wider than its column steps down a little.
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = style.fontSize),
                    modifier = Modifier.padding(horizontal = HEAD_GAP),
                )
            }
        }
    }
}

@Composable
private fun RowScope.Figures(result: HeroResult, fixed: Boolean) {
    Row(if (fixed) Modifier.width(FIGURES) else Modifier.weight(1f)) {
        ResultColumn.entries.forEach { column ->
            Text(
                "${column.of(result)}",
                style = GoaTextStyles.score.copy(fontSize = 20.sp, lineHeight = 24.sp),
                textAlign = TextAlign.Center,
                color = if (column.of(result) == 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    LocalContentColor.current
                },
                modifier = if (fixed) Modifier.width(column.width) else Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroResultRow(play: PlayUi, result: HeroResult, stacked: Boolean, onHero: (String) -> Unit) {
    val info = play.table.hero(result.heroId)
    val who = when {
        // Online every seat is a person: the player's name, not "You" on all of them.
        play.online != null && result.heroId !in play.table.mine ->
            play.online?.players?.get(result.heroId) ?: PlayText.controller(result.controller)

        result.controller == Controller.Human -> "You"

        else -> PlayText.controller(result.controller)
    }
    val spoken = ResultColumn.entries.joinToString(", ") { c ->
        if (c == ResultColumn.LEVEL) "Level ${result.level}" else "${c.of(result)} ${c.spoken}"
    }
    Surface(
        onClick = { onHero(result.heroId) },
        color = Color.Transparent,
        contentColor = LocalContentColor.current,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "${info.name}, ${BoardText.team(result.team)}, $who. $spoken"
            },
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The team's mark on the portrait's foot (as the decision strip has it): the name keeps the width.
                Box {
                    HeroPortrait(
                        result.heroId,
                        info.portrait,
                        null,
                        Modifier.size(PORTRAIT).border(2.dp, teamColor(result.team), CircleShape),
                        shape = CircleShape,
                        framed = false,
                    )
                    TeamBadge(result.team, Modifier.align(Alignment.BottomEnd).badgeOverhang(BADGE), size = BADGE)
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    // A name is never broken mid-word: a long one steps down to fit its column.
                    val nameStyle = MaterialTheme.typography.titleSmall
                    Text(
                        info.name,
                        style = nameStyle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = nameStyle.fontSize),
                    )
                    Text(
                        // "Computer ·" / "Hard 8" when it wraps: the dot stays with the word before it.
                        who.replace(" · ", "\u00A0· "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!stacked) Figures(result, fixed = true)
            }
            if (stacked) {
                Heads(Modifier.fillMaxWidth().padding(top = Spacing.sm))
                Row { Figures(result, fixed = false) }
            }
        }
    }
}

/** The figures' columns, each as wide as its head word with air on both sides (a gap between heads). */
private val FIGURES = ResultColumn.entries.fold(0.dp) { sum, c -> sum + c.width }
private val BADGE = 14.dp
private val PORTRAIT = 36.dp

/** Air either side of a head word: neighbouring heads never touch. */
private val HEAD_GAP = 3.dp
private const val STACKED_SCALE = 1.3f
private const val REMATCH_SHARE = 1.4f
