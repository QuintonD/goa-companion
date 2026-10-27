package dev.quinton.goa.feature.board.play.table

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/**
 * The resolution flow (ADR-046): one line per hero's card resolution since the person last chose,
 * each opening to its steps — the action, moves, each attack, the defending card as the card (tap to
 * read it), discards with the open card, pushes, coins, defeats, effects. An entry where an enemy
 * defended against (or was defeated by) one of the person's heroes starts open; lines a player must
 * not miss are bold with a bar in the accent colour. TalkBack reads each entry as a sentence and says
 * whether it is expanded.
 */
@Composable
fun ResolutionFlowView(flow: ResolutionFlow, table: TableUi, focus: CardFocusState?, modifier: Modifier = Modifier) {
    if (flow.isEmpty) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (flow.earlier > 0) {
            Text(
                "${flow.earlier} earlier ${if (flow.earlier == 1) "card" else "cards"} in the game log",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        flow.entries.forEach { entry ->
            key(entry.key) { FlowEntryView(entry, table, focus, earlier = !flow.fresh) }
        }
    }
}

@Composable
private fun FlowEntryView(entry: FlowEntry, table: TableUi, focus: CardFocusState?, earlier: Boolean) {
    var open by rememberSaveable(entry.key) { mutableStateOf(entry.open) }
    val scheme = MaterialTheme.colorScheme
    val accent = if (entry.important) scheme.primary else scheme.outlineVariant
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = scheme.surfaceVariant.copy(alpha = if (earlier) EARLIER_ALPHA else 1f),
        border = BorderStroke(if (entry.important || entry.open) 1.5.dp else 1.dp, accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            val toggle = { open = !open }
            Surface(
                onClick = toggle,
                color = Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = spokenEntry(entry, table, earlier)
                    stateDescription = if (open) "Expanded" else "Collapsed"
                    role = Role.Button
                    onClick(label = if (open) "Collapse" else "Expand") {
                        toggle()
                        true
                    }
                },
            ) { EntryHeader(entry, table, open, earlier) }
            AnimatedVisibility(open) { EntrySteps(entry, table, focus) }
        }
    }
}

@Composable
private fun EntryHeader(entry: FlowEntry, table: TableUi, open: Boolean, earlier: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        EntryBadge(entry, table)
        entry.cardId?.let(table::card)?.let { card ->
            Spacer(Modifier.width(Spacing.xs))
            TableCard(card, width = HEADER_CARD)
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            val who = buildList {
                add(entry.title.uppercase())
                if (entry.mine) add("YOU")
                if (earlier) add("EARLIER")
            }.joinToString(" · ")
            Text(who, style = GoaTextStyles.overline, color = entry.team?.let { teamColor(it) } ?: scheme.primary)
            Text(
                entry.line,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.important || entry.open) FontWeight.SemiBold else null,
                color = scheme.onSurface,
            )
        }
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
        )
    }
}

/** The hero's portrait in its team's ring, or a mark for the turn's start and the end of the round. */
@Composable
private fun EntryBadge(entry: FlowEntry, table: TableUi) {
    val hero = entry.heroId
    if (hero != null) {
        HeroPortrait(
            hero,
            table.hero(hero).portrait,
            null,
            Modifier.size(BADGE).border(2.dp, entry.team?.let { teamColor(it) } ?: GoaTheme.colors.engravedBorder, CircleShape),
            shape = CircleShape,
            framed = false,
        )
    } else {
        Box(Modifier.size(BADGE), contentAlignment = Alignment.Center) {
            Icon(
                if (entry.kind == FlowKind.END_OF_ROUND) Icons.Outlined.Flag else Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EntrySteps(entry: FlowEntry, table: TableUi, focus: CardFocusState?) {
    Column(
        Modifier.padding(start = Spacing.md, end = Spacing.sm, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        entry.steps.forEachIndexed { i, step ->
            // The action chosen shows the card it was chosen on (tap to read it).
            val cards = if (step.kind == StepKind.ACTION) listOfNotNull(entry.cardId) else step.cards
            StepRow(entry, i, step, cards.mapNotNull(table::card), table, focus)
        }
    }
}

@Composable
private fun StepRow(
    entry: FlowEntry,
    index: Int,
    step: FlowStep,
    cards: List<Card>,
    table: TableUi,
    focus: CardFocusState?,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(3.dp).heightIn(min = 18.dp).padding(vertical = 2.dp),
        ) {
            Surface(color = if (step.important) scheme.primary else scheme.outlineVariant, modifier = Modifier.size(3.dp, 16.dp)) {}
        }
        Spacer(Modifier.width(Spacing.sm))
        cards.forEachIndexed { c, card ->
            StepCard(entry, index, c, card, step, cards, table, focus)
            Spacer(Modifier.width(Spacing.xs))
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                step.text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (step.important) FontWeight.SemiBold else null,
                color = if (step.important) scheme.onSurface else scheme.onSurfaceVariant,
            )
            step.success?.let { held ->
                // Words and shape, not colour alone (as the Defend question's verdicts).
                val color = if (held) scheme.secondary else scheme.error
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (held) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (held) "Defended" else "Defeated",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
            }
        }
    }
}

/** A card in a step: the defending card, the open card a discard names, the card the action was on. */
@Composable
private fun StepCard(
    entry: FlowEntry,
    index: Int,
    at: Int,
    card: Card,
    step: FlowStep,
    cards: List<Card>,
    table: TableUi,
    focus: CardFocusState?,
) {
    val owner = step.heroId ?: entry.heroId
    val whose = owner?.let { "${table.hero(it).name}'s " }.orEmpty()
    val what = if (step.kind == StepKind.DEFENSE) "defense card" else "card"
    val key = "flow/${entry.key}/$index/${card.id}"
    val show = {
        focus?.open(
            cards.mapIndexed { i, c ->
                FocusCard(c, key = "flow/${entry.key}/$index/${c.id}", caption = if (i == at) caption(step) else null)
            },
            index = at,
            title = owner?.let { table.hero(it).name },
        )
        Unit
    }
    Surface(
        onClick = show,
        enabled = focus != null,
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        modifier = Modifier
            .then(if (focus != null) Modifier.cardFocusOrigin(focus, key) else Modifier)
            .clearAndSetSemantics {
                contentDescription = "$whose$what: ${spokenCard(card)}"
                role = Role.Button
                if (focus != null) {
                    onClick(label = "Read the card") {
                        show()
                        true
                    }
                }
            },
    ) { TableCard(card, width = if (step.kind == StepKind.DEFENSE) DEFENSE_CARD else STEP_CARD) }
}

/** The viewer's caption for a step's card: "Defense 5 against Attack 4 · Defended". */
private fun caption(step: FlowStep): String? = when (step.kind) {
    StepKind.DEFENSE -> listOfNotNull(
        step.attack?.let { "Against Attack $it" },
        step.text.substringAfter(": ", "").removeSuffix(".").ifEmpty { null },
    ).joinToString(" · ").ifEmpty { null }
    else -> step.text.removeSuffix(".")
}

/** One sentence for TalkBack: "Brogan, the computer's hero, played Onslaught: Wasp defended with Lift Up, 5 against 4." */
private fun spokenEntry(entry: FlowEntry, table: TableUi, earlier: Boolean): String {
    val prefix = if (earlier) "Earlier: " else ""
    val hero = entry.heroId ?: return prefix + entry.spoken
    val whose = if (entry.mine) "your hero" else table.heroView(hero)?.let { table.controllerLabel(it).lowercase() }
    val who = listOfNotNull(entry.title, whose).joinToString(", ")
    val card = entry.cardId?.let(table::card)?.name
    val outcome = entry.line.substringAfter(" → ", entry.line).replace(" vs ", " against ")
    val did = if (card != null) "played $card: $outcome" else outcome
    return "$prefix$who, $did."
}

private val BADGE = 32.dp
private val HEADER_CARD: Dp = 30.dp
private val STEP_CARD: Dp = 40.dp
private val DEFENSE_CARD: Dp = 48.dp
private const val EARLIER_ALPHA = 0.6f
