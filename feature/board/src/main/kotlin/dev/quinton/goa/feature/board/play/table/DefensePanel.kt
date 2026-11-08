package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.CardTitle
import dev.quinton.goa.core.designsystem.component.CardTitleSize
import dev.quinton.goa.core.designsystem.component.CoinIcon
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.LifeCounterIcon
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.PlateRowDivider
import dev.quinton.goa.core.designsystem.component.SelectablePlate
import dev.quinton.goa.core.designsystem.component.SelectionMark
import dev.quinton.goa.core.designsystem.component.bottomEdgeFade
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.game.DefeatPreview
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ---- Defense (p.14) ---------------------------------------------------------------------

/**
 * Attacked (UI hill climb, round 5: a list, not five squeezed columns). The attacker's card with the Attack total;
 * when no card saves the hero, that said once ("No card saves Tigerclaw"); then the hand's cards as a list, one row
 * each: a thumbnail, the card's name, its Defense total against the Attack as the engine counts it and the outcome in
 * a word ("Defense 2 vs Attack 6 · Defeated"; a Block's verdict; a card whose text decides, that text verbatim), its
 * text a tap away (expand). Only a saving card is marked; failing ones read in neutral text, never as errors. Not
 * defending is a full-width row of its own, with what it comes to as the engine plays it out ([DefeatPreview]: the
 * Life counters, the coins). Read card and the commit close it; when the list is longer than the panel's room
 * ([LocalQuestionRoom]) it scrolls inside, so the commit stays in view. Nothing is chosen for the player, except that
 * when no card saves the hero, not defending is chosen from the start ("Accept defeat · keep cards").
 */
@Composable
internal fun DefensePanel(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    focus: CardFocusState,
    busy: Boolean,
    defeat: DefeatPreview? = null,
) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    val context = decision.context
    val attack = context.attackTotal
    val attacker = context.attackerId?.let(table::pieceName)
    val heading = listOfNotNull(
        attacker?.let { "$it attacks" },
        context.rangedAttack?.let { if (it) "ranged" else "melee" },
    ).joinToString(" · ").ifEmpty { "Attacked" }
    val offered = table.cardOptions.mapNotNull { choice -> table.card(choice.cardId)?.let { OfferedCard(it, choice) } }
    val rows = offered.map { item ->
        val id = item.card.id
        defenseLine(context.defensePreview[id], context.blockPreview[id], attack)
    }
    val focusCards = offered.mapIndexed { i, item -> FocusCard(item.card, verdict = rows[i].verdict) }
    val summary = listOfNotNull(heading, attack?.let { "Attack $it" }).joinToString(" · ")
    val chosen = chosenCard(table)
    val declines = table.buttonOptions
    // No card saves the hero: not defending (and keeping the cards) is what the button offers until a card is chosen.
    val hopeless = offered.isNotEmpty() && rows.all { it.verdict.holds == false } && declines.isNotEmpty()
    val declined = table.selected?.takeIf { it !is Choice.Card && it in declines }
    val answer: Choice? = chosen ?: declined ?: declines.firstOrNull()?.takeIf { hopeless }
    val picked = chosen?.let { c -> offered.indexOfFirst { it.choice == c }.takeIf { it >= 0 } }
    val hero = table.decider?.let { table.hero(it.heroId).name }
    val items = table.decider?.itemStats?.count { it == Stat.DEFENSE } ?: 0
    // Said once, in the attack's box: the rows then need no mark of their own.
    val noneSaves = if (hopeless) "No card saves ${hero ?: "you"}" else null
    val open = { i: Int, asText: Boolean ->
        openOffered(focus, table, offered, i, onAnswer, context = summary, cards = focusCards, asText = asText)
    }
    PinnedFoot(
        head = {
            AttackHeader(heading, attack, context.minionModifier, table.sourceCard, decision.prompt, focus, noneSaves)
        },
        count = offered.size,
        list = {
            offered.forEachIndexed { i, item ->
                val isChosen = item.choice == chosen
                val line = rows[i]
                if (i > 0) PlateRowDivider()
                DefenseOption(
                    item.card,
                    line,
                    attack,
                    focus,
                    chosen = isChosen,
                    // What the chosen card comes to, counted: "Defense 2 = card 1 + minion 1 vs Attack 6".
                    counted = if (isChosen) {
                        defenseCounted(item.card, line, attack, context.minionModifier, items, context.rangedAttack)
                    } else {
                        null
                    },
                    onChoose = item.choice?.takeIf { !isChosen }?.let { c -> { onSelect(c) } },
                    onOpen = { open(i, false) },
                )
            }
        },
        foot = {
            declines.forEach { choice ->
                DeclineOption(table, choice, selected = choice == answer, defeat) { onSelect(choice) }
            }
            CommitRow(
                table,
                answer,
                "Choose a card, or Don't defend",
                busy,
                onAnswer,
                // The defeat is certain (the engine played it out): the button says so, not only what is kept.
                label = if (answer != null && answer !is Choice.Card && defeat != null) {
                    "Accept defeat · keep cards"
                } else {
                    defendLabel(table, answer)
                },
            ) {
                if (offered.isNotEmpty()) open(picked ?: 0, true)
            }
        },
    )
}

/**
 * A question in three parts, the middle one scrolling: [head] and [foot] at their own height, [list] (the [count]
 * cards, as rows of one plate) given what is left of the panel's room ([LocalQuestionRoom]) and scrolling inside it,
 * never less than [LIST_MIN] (with large text the panel itself then scrolls). When cards are below, a cue says how
 * many ("+1 more card"), not a fade alone. Without a room (a preview), all three at their own height.
 */
@Composable
private fun PinnedFoot(
    head: @Composable ColumnScope.() -> Unit,
    count: Int,
    list: @Composable ColumnScope.() -> Unit,
    foot: @Composable ColumnScope.() -> Unit,
) {
    val room = LocalQuestionRoom.current
    var top by remember { mutableStateOf<Float?>(null) }
    val scroll = rememberScrollState()
    Layout(
        contents = listOf(
            { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = head) },
            {
                GoaPlate(contentPadding = PaddingValues(0.dp), brackets = false) {
                    Column(
                        Modifier.weight(1f, fill = false).bottomEdgeFade(scroll).verticalScroll(scroll),
                        content = list,
                    )
                    // Only when a card is below, not for a few pixels of the last row's edge: a line of the plate's
                    // own under the rows, so it covers none of them.
                    val hidden = with(LocalDensity.current) { (scroll.maxValue - scroll.value).toDp() }
                    if (hidden > OPTION_ROW / 2) MoreBelow(scroll, count)
                }
            },
            { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = foot) },
        ),
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { top = it.positionInRoot().y },
    ) { (heads, lists, feet), constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val h = heads.first().measure(loose)
        val f = feet.first().measure(loose)
        val gap = LIST_GAP.roundToPx()
        val at = top
        val limit = if (room != null && at != null) {
            room.height.roundToPx() - (at - room.contentTop.value).roundToInt()
        } else {
            null
        }
        val most = limit?.let { (it - h.height - f.height - 2 * gap).coerceAtLeast(LIST_MIN.roundToPx()) }
        // A scrolling column needs a bound: without a room, one no list reaches.
        val l = lists.first().measure(loose.copy(maxHeight = most ?: UNBOUNDED_LIST))
        val height = h.height + gap + l.height + gap + f.height
        layout(constraints.maxWidth, height) {
            h.place(0, 0)
            l.place(0, h.height + gap)
            f.place(0, h.height + gap + l.height + gap)
        }
    }
}

/**
 * Under a list that goes on, a line of its plate: how many cards are below ("+1 more card"), a tap scrolls to them.
 * TalkBack reaches every row by scrolling already, so it is for the eye.
 */
@Composable
private fun MoreBelow(scroll: ScrollState, count: Int, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val row = with(LocalDensity.current) { OPTION_ROW.toPx() }
    val below = (((scroll.maxValue - scroll.value) / row).toInt() + 1).coerceIn(1, maxOf(1, count - 1))
    Surface(
        onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
    ) {
        Column {
            PlateRowDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "+$below more ${if (below == 1) "card" else "cards"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
            }
        }
    }
}

/**
 * One hand card to defend with, as a row of the list, on one line: the official card small, its name, what it comes
 * to against this attack and the outcome in a word ("Def 2 vs 6 · Defeated"; only a saving card has a mark), and, one
 * tap away, the card's own text. A card whose text decides shows that text verbatim under it. A tap chooses it, again
 * reads it large; TalkBack hears it all in words, chosen with how its total is counted ([counted]).
 */
@Composable
private fun DefenseOption(
    card: Card,
    line: DefenseLine,
    attack: Int?,
    focus: CardFocusState,
    chosen: Boolean,
    counted: String?,
    onChoose: (() -> Unit)?,
    onOpen: () -> Unit,
) {
    var expanded by rememberSaveable(card.id) { mutableStateOf(false) }
    val outcome = optionOutcome(line.verdict.holds, line.verdict.label)
    val text = CardText.plain(card.text).trim()
    // The card's own condition decides (p.14): its words, verbatim, where the others have their totals.
    val decides = line.verdict.holds == null && text.isNotEmpty()
    val spoken = listOfNotNull(card.name, counted ?: defenseVs(line, attack), outcome, text.takeIf { decides })
        .joinToString(", ")
    val tap = onChoose ?: onOpen
    SelectablePlate(
        selected = chosen,
        onClick = tap,
        mark = SelectionMark.None,
        // As tall as its 48 dp target: four cards and not defending fit the panel.
        contentPadding = PaddingValues(start = Spacing.md),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = spoken
            if (onChoose != null) {
                customActions = listOf(
                    CustomAccessibilityAction("Look at the card") {
                        onOpen()
                        true
                    },
                )
            }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = Color.Transparent,
            modifier = Modifier.cardFocusOrigin(focus, card.id),
        ) { TableCard(card, width = OPTION_CARD) }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f).padding(vertical = 2.dp).clearAndSetSemantics {}) {
            if (LocalDensity.current.fontScale > STACK_SCALE) {
                // Large text: the name over its outcome, each with the row's width, never squeezed beside it.
                CardTitle(card.name, size = CardTitleSize.Caption, color = MaterialTheme.colorScheme.onSurface)
                OutcomeLine(shortVs(line, attack), outcome, line.verdict.holds)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The name beside a thumbnail, at its caption size: two lines still fit the 48 dp row.
                    CardTitle(
                        card.name,
                        Modifier.weight(1f),
                        size = CardTitleSize.Caption,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    OutcomeLine(shortVs(line, attack), outcome, line.verdict.holds)
                }
            }
            if (decides) {
                Text(
                    "“$text”",
                    style = GoaTextStyles.printed.copy(fontSize = 14.sp, lineHeight = 17.sp),
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                counted?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded && !decides) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (!decides && text.isNotEmpty()) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = "${if (expanded) "Hide" else "Show"} the text of ${card.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(Spacing.md))
        }
    }
}

/** A row's numbers, short: "Def 2 vs 6" for a total, "Block fails" for a Block that doesn't stop it, else null. */
private fun shortVs(line: DefenseLine, attack: Int?): String? = when {
    line.total != null -> "Def ${line.total}" + attack?.let { " vs $it" }.orEmpty()
    line.verdict.holds == false -> "Block fails"
    else -> null
}

/** "Defense 2 vs Attack 6 · Defeated" in neutral text, the outcome word in weight; a saving card's with its tick. */
@Composable
private fun OutcomeLine(vs: String?, outcome: String, holds: Boolean?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = if (holds == true) verdictColor(true) else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (holds == true) {
            Icon(
                Icons.Outlined.CheckCircle,
                null,
                tint = strong,
                modifier = Modifier.padding(end = 4.dp).size(scaledIconSize(16.dp, 1.3f)),
            )
        }
        Text(
            buildAnnotatedString {
                vs?.let {
                    withStyle(SpanStyle(color = muted)) { append(it) }
                    withStyle(SpanStyle(color = muted)) { append(" · ") }
                }
                withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append(outcome) }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Not defending, a full-width row of its own under the cards: "Don't defend · keep your cards", and under it what it
 * comes to as the engine plays it out ([DefeatPreview]): defeated, the Life counters the team spends with the Life
 * counter's own mark ("Blue −1 (1 left)"; the last one: "Red wins"), the coins each hero collects ("Arien +2"), p.13.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeclineOption(
    table: TableUi,
    choice: Choice,
    selected: Boolean,
    defeat: DefeatPreview?,
    onClick: () -> Unit,
) {
    val label = "${table.label(choice)} · keep your cards"
    val spoken = listOf(label) + (defeat?.let { defeatSpoken(table, it) } ?: listOf("Defeated"))
    SelectablePlate(
        selected = selected,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = spoken.joinToString(". ") + ". Rulebook page ${if (defeat != null) 13 else 14}"
        },
    ) {
        Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // The page on the title's line: the consequence has the row's whole width, on one line.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.xs))
                PagePill(if (defeat != null) "p.13" else "p.14")
            }
            val icon = scaledIconSize(20.dp, 1.3f)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Defeated",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (defeat != null) {
                    val team = defeat.team
                    val winner = defeat.winner
                    if (team != null) {
                        val left = if (winner != null) {
                            "${BoardText.team(winner)} wins"
                        } else {
                            defeat.lifeLeft?.let { "($it left)" }
                        }
                        val spent = listOfNotNull("${BoardText.team(team)} −${defeat.lifeSpent}", left)
                            .joinToString(" ")
                        ConsequenceItem(spent) { LifeCounterIcon(team, spent = winner != null, size = icon) }
                    }
                    defeat.coins.forEach { (id, n) ->
                        ConsequenceItem("${table.hero(id).name} +$n") { CoinIcon(size = icon) }
                    }
                }
            }
        }
    }
}

/** What not defending comes to, for TalkBack, in words: the defeat, the Life counters, the coins, the respawn (p.7). */
private fun defeatSpoken(table: TableUi, defeat: DefeatPreview): List<String> {
    val hero = table.hero(defeat.defeated).name
    val by = defeat.byHero?.let { " by ${table.hero(it).name}" }.orEmpty()
    val team = defeat.team
    val winner = defeat.winner
    return buildList {
        add("$hero is defeated$by")
        if (team != null) {
            add(
                if (winner != null) {
                    "${BoardText.team(team)} spends its last Life counter: ${BoardText.team(winner)} wins"
                } else {
                    "${BoardText.team(team)} spends ${BoardText.count(defeat.lifeSpent, "Life counter")}" +
                        defeat.lifeLeft?.let { left -> " ($left left)" }.orEmpty()
                },
            )
        }
        defeat.coins.forEach { (id, n) -> add("${table.hero(id).name} gains ${BoardText.count(n, "coin")}") }
        if (winner == null) add("$hero may respawn before acting next turn")
    }
}

@Composable
private fun ConsequenceItem(text: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The Defend button in one line: "Defend · Blink Strike", "Don't defend · keep your cards". */
private fun defendLabel(table: TableUi, answer: Choice?): String? = when (answer) {
    null -> null
    is Choice.Card -> table.card(answer.cardId)?.let { "Defend · ${it.name}" }
    else -> "${table.label(answer)} · keep your cards"
}

/**
 * Each option's end in a word, from the engine's verdict (DecisionContext.defensePreview/blockPreview): p.14 "If
 * you cannot defend successfully, or choose not to defend, you are defeated."
 */
internal fun optionOutcome(holds: Boolean?, label: String): String = when (holds) {
    true -> if (label == "Blocks") "Blocks it" else "Defends"
    false -> "Defeated"
    null -> "Its text decides"
}

/**
 * An option's numbers in words: "Defense 2 vs Attack 6" for a total, a Block's verdict in the engine's words ("Block:
 * not against this attack"), else null (its text decides).
 */
internal fun defenseVs(line: DefenseLine, attack: Int?): String? = when {
    line.total != null -> "Defense ${line.total}" + attack?.let { " vs Attack $it" }.orEmpty()
    line.verdict.holds != null -> line.detail
    else -> null
}

/**
 * How the chosen card's total is made, from the engine's numbers: "Defense 3 = card 2 + minion 1 vs Attack 6";
 * for a card whose text decides, the attack it is weighed against as the engine knows it now ("This is a melee
 * attack, Attack 6."). Null when the row says it all already.
 */
private fun defenseCounted(
    card: Card,
    line: DefenseLine,
    attack: Int?,
    minion: Int?,
    items: Int,
    ranged: Boolean?,
): String? {
    if (line.verdict.holds == null) {
        val kind = ranged?.let { if (it) "a ranged attack" else "a melee attack" }
        val facts = listOfNotNull(kind, attack?.let { "Attack $it" })
        return facts.takeIf { it.isNotEmpty() }?.let { "This is ${it.joinToString(", ")}." }
    }
    val sum = defenseSum(line.total, card.values.defense, minion, items) ?: return null
    return sum + attack?.let { " vs Attack $it" }.orEmpty()
}

/**
 * The attack: the attacker's card (tap to read it), who attacks and how, the Attack total, and the
 * minion defense modifier counted, on the text box in the attacking card's colour, as every question asked by a card.
 * It carries the question for TalkBack (a polite live region). Its text stops growing at 1.3×: with more it pushed
 * the cards to defend with off the screen.
 */
@Composable
private fun AttackHeader(
    heading: String,
    attack: Int?,
    minionModifier: Int?,
    card: Card?,
    prompt: String,
    focus: CardFocusState,
    noneSaves: String?,
) {
    val density = LocalDensity.current
    val capped = Density(density.density, minOf(density.fontScale, ATTACK_MAX_SCALE))
    CompositionLocalProvider(LocalDensity provides capped) {
        AttackBox(heading, attack, minionModifier, card, prompt, focus, noneSaves)
    }
}

@Composable
private fun AttackBox(
    heading: String,
    attack: Int?,
    minionModifier: Int?,
    card: Card?,
    prompt: String,
    focus: CardFocusState,
    noneSaves: String?,
) {
    val color = card?.let { PlateColor.of(it.color) } ?: PlateColor.RED
    // Who attacks and how on the box's own tab, as the cards print ATTACK there: a line less over the options.
    CardTextBox(color, Modifier.fillMaxWidth(), cap = heading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (card != null) {
                Surface(
                    onClick = { focus.show(listOf(card), title = "Attacking card") },
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .cardFocusOrigin(focus, card.id)
                        .clearAndSetSemantics {
                            contentDescription = "Attacking card: ${spokenCard(card)}"
                            role = Role.Button
                            onClick(label = "Read the card") {
                                focus.show(listOf(card), title = "Attacking card")
                                true
                            }
                        },
                ) { TableCard(card, width = ATTACK_CARD) }
                Spacer(Modifier.width(Spacing.md))
            }
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = listOfNotNull(prompt, noneSaves).joinToString(". ")
                },
            ) {
                // The Attack, beside it the outcome when no card saves (said once, here) and the minion modifier: the
                // cards to defend with keep their place in view.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        attack?.let { "Attack $it" } ?: "Attack decided by the card",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = Spacing.sm),
                    )
                    Column(Modifier.weight(1f)) {
                        noneSaves?.let {
                            Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        minionModifier?.takeIf { it != 0 }?.let { m ->
                            Text(
                                "Minion defense ${if (m > 0) "+$m" else "$m"} each",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A card in the list of options: small, its colour and art enough to know it (Read card or a tap shows it large). */
private val OPTION_CARD = 26.dp

/** Past this font scale a row puts the name over its outcome. */
private const val STACK_SCALE = 1.3f

/** Between the list and what is over and under it. */
private val LIST_GAP = 6.dp

/** A row of the list: its 48 dp target (the cue counts the cards below by it). */
private val OPTION_ROW = 48.dp

/** The list keeps at least this much (about two rows), even when large text leaves the panel no room for it. */
private val LIST_MIN = 112.dp
private val ATTACK_CARD = 26.dp

/** The list's bound, px, where the panel gives it no room (a preview): taller than any hand. */
private const val UNBOUNDED_LIST = 20_000
private const val ATTACK_MAX_SCALE = 1.3f
