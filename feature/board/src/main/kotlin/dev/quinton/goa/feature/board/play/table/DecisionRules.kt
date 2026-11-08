package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind

/*
 * Rules on demand (2026-11-08, show, don't tell): the panel no longer prints a rule paragraph, a "Do nothing" under
 * Hold or a page pill on every chip. What a question rests on that no card prints is one tap away instead: an info
 * mark at the end of the panel's head opens the question's rules, each with its rulebook page. TalkBack still hears
 * each action's rule on its chip.
 */

/** One rule a question rests on: the app's words and where the rulebook says it ("p.7"; an FAQ or designer ruling). */
internal data class DecisionRule(val text: String, val page: String?)

/** The rules of the person's question now, in the order its options show; empty when nothing needs saying. */
internal fun decisionRules(table: TableUi): List<DecisionRule> {
    val decision = table.decision ?: return emptyList()
    if (!table.asking) return emptyList()
    return when (decision.kind) {
        DecisionKind.CHOOSE_ACTION -> buildList {
            val actions = table.buttonOptions.filterIsInstance<Choice.Action>().map { it.action }
            add(
                if (ActionType.PRIMARY in actions) {
                    DecisionRule("Card action: only it applies the card's text.", "p.7")
                } else {
                    DecisionRule("Only the card action applies the card's text.", "p.7")
                },
            )
            actions.forEach { action ->
                actionRule(action)?.let { rule ->
                    val page = RULE_PAGE.find(rule)?.value?.trim('(', ')')
                    add(
                        DecisionRule(
                            "${actionLabel(action, table.sourceCard)}: ${rule.replace(RULE_PAGE, "").trim()}",
                            page,
                        ),
                    )
                }
            }
            // The actions the card prints that can't be taken now, with the engine's reason (ADR-041).
            unavailableActions(decision).forEach { add(DecisionRule("${it.text}.", it.source)) }
            // Gydion's card: the spells it names that can't be cast now, with the engine's reason (ADR-040).
            spellRuleLines(table).forEach { add(DecisionRule(it.text, null)) }
        }

        DecisionKind.ACT_FIRST -> listOf(
            DecisionRule(
                "Heroes of one team on the same initiative choose which of them acts first; between teams the Tie " +
                    "Breaker coin decides, then it flips.",
                "p.7",
            ),
        )

        DecisionKind.PLAY_CARD -> table.decider?.takeIf { it.at == null }?.let { hero ->
            listOf(
                DecisionRule(
                    "${table.hero(hero.heroId).name} is off the board. When this card resolves you may respawn in " +
                        "your Throne Zone, then act with it.",
                    "p.7",
                ),
            )
        }.orEmpty()

        DecisionKind.DEFEND -> listOf(
            DecisionRule(
                "If you cannot defend successfully, or choose not to defend, you are defeated.",
                "p.14",
            ),
        )

        DecisionKind.UPGRADE_CARD -> listOf(
            DecisionRule(
                "The card on the left is the choice: it goes back to your deck, and you keep one of the two beside " +
                    "it; the other is tucked under your hero as an item.",
                "p.15",
            ),
        )

        DecisionKind.KEEP_UPGRADE -> listOf(
            DecisionRule("One goes to your hand; the other is tucked under your hero as an item.", "p.15"),
        )

        else -> emptyList()
    }
}

private val RULE_PAGE = Regex("\\(p\\.\\d+\\)$")

/**
 * The info mark at the end of the panel's head: a tap opens the question's [rules] (each with its page) in a small
 * menu at the mark, a tap outside closes it. A 48 dp target; nothing when there are no rules.
 */
@Composable
internal fun RulesButton(rules: List<DecisionRule>, modifier: Modifier = Modifier) {
    if (rules.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = "Rules for this question" },
        ) {
            Icon(
                Icons.Outlined.Info,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(scaledIconSize(20.dp, 1.3f)),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RuleList(rules, Modifier.widthIn(max = RULES_WIDTH).padding(horizontal = Spacing.md, vertical = Spacing.xs))
        }
    }
}

/** The rules one under another, each with its page as a pill at its end. */
@Composable
internal fun RuleList(rules: List<DecisionRule>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        rules.forEach { rule ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription =
                        listOfNotNull(rule.text, rule.page?.let { "Rulebook ${it.replace("p.", "page ")}" })
                            .joinToString(". ")
                },
            ) {
                Text(
                    rule.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                rule.page?.let {
                    Spacer(Modifier.width(Spacing.sm))
                    PagePill(it)
                }
            }
        }
    }
}

private val RULES_WIDTH = 320.dp
