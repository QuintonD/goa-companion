package dev.quinton.goa.feature.board.play.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText

/**
 * Game over: who won and how, in the rulebook's words; how each hero did; take backs and nudges;
 * the coach's turning points (when Coach is on), each with its round and turn; Rematch (same
 * setup, a new coin flip), New game, Done.
 */
@Composable
internal fun GameOverContent(
    play: PlayUi,
    over: GameOverUi,
    onAction: (GameAction) -> Unit,
    onNewGame: () -> Unit,
    onDone: () -> Unit,
    onHero: (String) -> Unit,
) {
    val winColor = teamColor(over.winner)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, winColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                    Text(
                        if (over.youWon) "You won" else "The computer won",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
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
                    Text(
                        PlayText.winReason(over.reason, over.winner),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        listOf(
                            BoardText.count(over.rounds, "round"),
                            BoardText.count(over.takeBacks, "take back"),
                            if (over.coach) BoardText.count(over.nudges, "coach nudge") else "Coach off",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Overline("How each hero did")
                Team.entries.forEach { team ->
                    over.heroes.filter { it.team == team }.forEach { result -> HeroResultRow(play, result, onHero) }
                }
            }

            if (over.coach) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Overline("Coach · turning points")
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

                        else -> points.forEach { note ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(Spacing.md).semantics(mergeDescendants = true) {}) {
                                    Icon(
                                        Icons.Outlined.Lightbulb,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Column {
                                        val at = listOfNotNull(
                                            note.round?.let { "Round $it" },
                                            note.turn?.let { "Turn $it" },
                                        ).joinToString(" · ")
                                        if (at.isNotEmpty()) {
                                            Text(
                                                at.uppercase(),
                                                style = GoaTextStyles.overline,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                        Text(
                                            note.text,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                        if (note.detail.isNotBlank()) {
                                            Text(
                                                note.detail,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = {
                    onAction(GameAction.Rematch)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Rematch", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Same heroes and seats; the app flips the Tie Breaker coin again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onNewGame, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("New game")
                }
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun Overline(text: String) {
    Text(
        text.uppercase(),
        style = GoaTextStyles.overline,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun HeroResultRow(play: PlayUi, result: HeroResult, onHero: (String) -> Unit) {
    val info = play.table.hero(result.heroId)
    val color = teamColor(result.team)
    val who = if (result.controller == Controller.Human) "You" else PlayText.controller(result.controller)
    val facts = listOf(
        "Level ${result.level}",
        BoardText.count(result.heroDefeats, "hero defeated", "heroes defeated"),
        BoardText.count(result.minionDefeats, "minion defeated", "minions defeated"),
        "defeated ${BoardText.count(result.defeated, "time")}",
        BoardText.count(result.coins, "coin") + " gained",
    )
    Surface(
        onClick = { onHero(result.heroId) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "${info.name}, ${BoardText.team(result.team)}, $who. " + facts.joinToString(", ")
            },
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            HeroPortrait(
                result.heroId,
                info.portrait,
                null,
                Modifier.size(44.dp).border(2.dp, color, CircleShape),
                shape = CircleShape,
                framed = false,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamMark(result.team, markSize = 9.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(info.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        who,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    facts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
