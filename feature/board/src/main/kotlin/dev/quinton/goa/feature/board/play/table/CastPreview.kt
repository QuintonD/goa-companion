package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.PrimaryPreview
import dev.quinton.goa.core.game.UnavailableCard
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/*
 * What the card's primary action will do, said before the player commits (`DecisionContext
 * .primaryPreview`, ADR-040 extended after the hands-on pass): in the hands-on game "Skill, primary
 * action" cast Gydion's only castable spell and took its only target with no question, and cast
 * Suggestion to no effect. Under the Primary chip: the spell it casts as its card (tap to read), its
 * only target, a warning when it will do nothing; the card's other spells each with why not. Only
 * on the person's own question: the panel is drawn for their seats only (`TableUi.mine`).
 */

/** The preview of the pending action question, when it is one and has something to say. */
internal fun TableUi.primaryPreview(): PrimaryPreview? =
    decision?.takeIf { it.kind == DecisionKind.CHOOSE_ACTION }?.context?.primaryPreview

/** True when the question offers the Primary action. */
private fun TableUi.offersPrimary(): Boolean =
    decision?.options?.any { it is Choice.Action && it.action == ActionType.PRIMARY } == true

/** "Casts Expeditious Retreat" / "Casts one of these: you choose which next"; null for a card's own text. */
internal fun castLine(table: TableUi, preview: PrimaryPreview): String? = when (preview.cardIds.size) {
    0 -> null
    1 -> "Casts ${table.card(preview.cardIds.single())?.name ?: "a spell"}"
    else -> "Casts one of these: you choose which next"
}

/** "Its only target: Tigerclaw" when the action's first choice has only one option. */
internal fun targetLine(table: TableUi, preview: PrimaryPreview): String? =
    preview.onlyTargetId?.let { "Its only target: ${table.pieceName(it)}" }

/** The warning that the action will do nothing, with the rule it rests on. */
internal fun noEffectLine(preview: PrimaryPreview): String? = preview.noEffect?.let { text ->
    preview.noEffectSource?.let { "${text.removeSuffix(".")} ($it)." } ?: text
}

/**
 * The card's other spells, one line each with the engine's reason ("Fireball: Not in the spellbook:
 * removed faceup (Prepare Spells)."). When the Primary isn't offered and every spell has the same
 * reason (an empty spellbook, all cast), the action's own line already says it: none are listed.
 */
internal fun spellReasonLines(table: TableUi, preview: PrimaryPreview, primaryOffered: Boolean): List<SpellReason> {
    val all = preview.unavailableCards
    if (!primaryOffered && all.size > 1 && all.all { it.text == all.first().text }) return emptyList()
    return all.mapNotNull { u -> table.card(u.cardId)?.let { SpellReason(it, reasonText(it, u)) } }
}

internal data class SpellReason(val card: Card, val text: String)

private fun reasonText(card: Card, u: UnavailableCard): String =
    "${card.name}: " + (u.source?.let { "${u.text} ($it)." } ?: "${u.text}.")

/**
 * Under the Primary chip: the spell(s) it casts as cards, its only target, the warning when it will
 * do nothing, then the card's other spells with why not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CastPreview(table: TableUi, focus: CardFocusState) {
    val preview = table.primaryPreview() ?: return
    val spells = preview.cardIds.mapNotNull(table::card)
    val cast = castLine(table, preview)
    val target = targetLine(table, preview)
    Column(
        Modifier.fillMaxWidth().padding(start = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (spells.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                spells.forEachIndexed { i, spell -> SpellThumb(table, spell, spells, i, focus) }
                if (cast != null) {
                    Column(Modifier.align(Alignment.CenterVertically)) {
                        Text(cast, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        target?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        } else if (target != null) {
            Text(target, style = MaterialTheme.typography.bodySmall)
        }
        noEffectLine(preview)?.let { NoEffect(it) }
        SpellReasons(table, preview, primaryOffered = true)
    }
}

/** The card's spells that can't be cast, when the Primary isn't offered (after the action's own line). */
@Composable
internal fun SpellReasonsIfNoPrimary(table: TableUi) {
    val preview = table.primaryPreview() ?: return
    if (table.offersPrimary()) return
    SpellReasons(table, preview, primaryOffered = false)
}

@Composable
private fun SpellReasons(table: TableUi, preview: PrimaryPreview, primaryOffered: Boolean) {
    spellReasonLines(table, preview, primaryOffered).forEach { r ->
        Text(
            r.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.lg),
        )
    }
}

/** The spell the Primary casts, as its official card: tap to read it large among the others. */
@Composable
private fun SpellThumb(table: TableUi, spell: Card, spells: List<Card>, index: Int, focus: CardFocusState) {
    val title = table.sourceCard?.let { "Cast by ${it.name}" }
    val read = { focus.show(spells, index, title = title) }
    Surface(
        onClick = read,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, cardColor(spell.color).copy(alpha = 0.8f)),
        modifier = Modifier
            .cardFocusOrigin(focus, spell.id)
            .clearAndSetSemantics {
                contentDescription = "${spokenCard(spell)}, the spell the primary action casts"
                role = Role.Button
                onClick(label = "Read the card") {
                    read()
                    true
                }
            },
    ) { TableCard(spell, width = SPELL_THUMB) }
}

/** "Will do nothing": said plainly, in the error colours, never by colour alone (the icon and words). */
@Composable
private fun NoEffect(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Row(Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 2.dp).size(scaledIconSize(16.dp, 1.3f)),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** A spell's thumbnail, as wide as a portrait card this wide (a landscape card keeps the height). */
private val SPELL_THUMB = 40.dp
