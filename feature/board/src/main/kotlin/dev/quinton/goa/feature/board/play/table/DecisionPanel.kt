package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.TextAutoSize
import dev.quinton.goa.core.game.UnavailableAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.Block
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.SteelIconBronze
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.game.DefeatPreview
import dev.quinton.goa.feature.board.ui.MinionTypeBadge
import dev.quinton.goa.feature.board.ui.PlateButton
import dev.quinton.goa.feature.board.ui.PlateTone
import dev.quinton.goa.feature.board.ui.PlateToneSurface
import dev.quinton.goa.feature.board.ui.PlateWell
import dev.quinton.goa.feature.board.ui.TeamBadge
import dev.quinton.goa.feature.board.ui.badgeOverhang
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.isSpell
import dev.quinton.goa.feature.board.ui.isLandscape

/**
 * The question being asked and its answers, by kind (PRODUCT §9: "every question says what is
 * asked and which card asks it; every option is shown on the board or as a button"). The card is
 * the content (ADR-041): the official card the question is about is shown large enough to read,
 * and the answers are tied to it rather than restating what it prints.
 * - actions are compact chips beside the card, with the game's icon and value; only what the card
 *   doesn't say is added (the rule of Fast Travel and Hold; that only the primary applies the text);
 *   under the Primary chip, what it will do before it is chosen: Gydion's spell as its card, its only
 *   target, a warning when it will do nothing (CastPreview.kt);
 * - "Choose one" options, yes / decline and numbers are buttons beside the card;
 * - spaces, units and tokens are tapped on the board (each also a TalkBack node), then confirmed,
 *   with the asking card beside the hint;
 * - cards (to play, discard, defend with, upgrade, keep): a tap chooses one, a tap on the chosen one reads it
 *   in the card focus viewer, and a bronze button naming the choice answers (DecisionCards.kt);
 * - which card to upgrade shows, beside each, the two cards it would become (UpgradeCards.kt);
 * - Defend shows the attacker's card with the Attack, then the hand's cards with each verdict.
 * [onSelect] marks a tentative choice, [onAnswer] answers, [focus] is the screen's card viewer.
 */
@Composable
fun DecisionPanel(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    focus: CardFocusState,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    who: Boolean = true,
    /** A DEFEND: what not defending comes to, as the engine plays it out (the consequence row above the commit). */
    defeat: DefeatPreview? = null,
) {
    val decision = table.decision ?: return
    val source = table.sourceCard
    val layout = panelLayout(decision.kind, source != null)
    val board = hasBoardOptions(table)
    // The table sizes its panel to where the question ends; what follows it (what happened, the hands) scrolls.
    val questionEnd = LocalPanelQuestionEnd.current
    DisposableEffect(questionEnd) { onDispose { questionEnd?.invoke(null) } }
    Column(
        modifier
            .fillMaxWidth()
            .onPlaced { questionEnd?.invoke(it.positionInParent().y.toInt() + it.size.height) },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // The question is said once, in the head (the panel's strip, or here when the strip doesn't carry it).
        if (who) DecisionWho(table)
        if (layout == PanelLayout.CARDS && decision.kind != DecisionKind.DEFEND && source != null) {
            AskedBy(source, decision, focus)
        }
        when {
            decision.kind == DecisionKind.PLAY_CARD -> HandPicker(table, onSelect, onAnswer, focus, busy)

            decision.kind == DecisionKind.DEFEND -> DefensePanel(
                table,
                onSelect,
                onAnswer,
                focus,
                busy,
                defeat?.takeIf { it.decisionId == decision.id },
            )

            decision.kind == DecisionKind.UPGRADE_CARD -> UpgradePicker(table, onSelect, onAnswer, focus, busy)

            layout == PanelLayout.CARDS -> CardPicker(table, onSelect, onAnswer, focus, busy)

            // Who acts first: the tied heroes as buttons, one tap answers (the map's pieces still select, then Confirm).
            decision.kind == DecisionKind.ACT_FIRST -> {
                if (table.selected != null) BoardAnswer(table, null, focus, onAnswer, busy)
                ActFirstButtons(table, onAnswer, busy)
                Choices(table, table.buttonOptions, onAnswer, busy)
            }

            // Answered on the map: the asking card small, List and Confirm on one row, the panel's chips under it.
            board -> {
                BoardAnswer(table, source, focus, onAnswer, busy)
                BoardChips(table, onSelect)
                Choices(table, table.buttonOptions, onAnswer, busy)
            }

            // A phone sideways, short: every action in view beside a card still read at a glance.
            layout == PanelLayout.CARD_BESIDE && source != null && shortSideways() -> {
                SpokenPrompt(decision.prompt)
                CompactQuestion(source, focus) {
                    Choices(table, table.buttonOptions, onAnswer, busy, focus = focus, brief = true) {
                        CastPreview(table, focus)
                    }
                }
            }

            layout == PanelLayout.CARD_BESIDE && source != null -> {
                // The head says the question; the card and its answers are the rest (TalkBack hears the prompt whole).
                SpokenPrompt(decision.prompt)
                CardBeside(table, source, focus, numbers = decision.kind == DecisionKind.CHOOSE_ACTION) {
                    Choices(table, table.buttonOptions, onAnswer, busy, focus = focus) { CastPreview(table, focus) }
                }
            }

            else -> {
                SpokenPrompt(decision.prompt)
                Choices(table, table.buttonOptions, onAnswer, busy, focus = focus)
            }
        }
    }
}

private fun hasBoardOptions(table: TableUi) = table.spaceOptions.isNotEmpty() || table.pieceOptions.isNotEmpty()

/**
 * A question answered on the map, in one row: the asking card small (tap to read it), "List" (the options as rows,
 * lettered on the map) and, once one is tapped, Confirm saying what it does ("Move here · 3"). The lit spaces say
 * where to tap; nothing here says it again.
 */
@Composable
private fun BoardAnswer(
    table: TableUi,
    source: Card?,
    focus: CardFocusState,
    onAnswer: (Choice) -> Unit,
    busy: Boolean,
) {
    if (source == null && !listOffered(table) && boardChoice(table) == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        source?.let { AskingThumb(table, it, focus) }
        MapListToggle(table)
        BoardConfirm(table, onAnswer, busy, Modifier.weight(1f))
    }
}

/** The asking card as a thumbnail at the start of a row: tap to read it large. */
@Composable
private fun AskingThumb(table: TableUi, card: Card, focus: CardFocusState) {
    val reach = table.decision?.let { sourceReach(it.prompt, card) }
    val read = { focus.show(listOf(card), title = "Asked by") }
    Surface(
        onClick = read,
        shape = RoundedCornerShape(3.dp),
        color = Color.Transparent,
        shadowElevation = 2.dp,
        modifier = Modifier
            .cardFocusOrigin(focus, card.id)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull("Asked by ${spokenCard(card)}", reach).joinToString(", ")
                role = Role.Button
                onClick(label = "Read the card") {
                    read()
                    true
                }
            },
    ) { TableCard(card, width = if (card.isSpell) SPELL_THUMB_ROW else CARD_THUMB_ROW) }
}

/** The tied heroes, each a button with its portrait: a tap answers who acts first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActFirstButtons(table: TableUi, onAnswer: (Choice) -> Unit, busy: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        table.pieceOptions.forEach { (id, choice) ->
            val view = table.heroView(id)
            val hero = view?.let { table.hero(it.heroId) }
            PlateToneSurface(
                onClick = { onAnswer(choice) },
                enabled = !busy,
                tone = PlateTone.STEEL,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = "${table.label(choice)} acts first"
                },
            ) {
                Row(
                    Modifier.padding(start = Spacing.sm, end = Spacing.md, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hero != null && view != null) {
                        HeroPortrait(
                            hero.id,
                            hero.portrait,
                            null,
                            Modifier
                                .size(scaledIconSize(32.dp, 1.2f))
                                .border(2.dp, teamColor(view.team), CircleShape),
                            shape = CircleShape,
                            framed = false,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text(
                        table.label(choice),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PlateTone.STEEL.content,
                    )
                }
            }
        }
    }
}

/** A question the panel shows by its answers alone, for TalkBack (a polite live region, as the shown prompt). */
@Composable
private fun SpokenPrompt(prompt: String) {
    Box(
        Modifier.size(1.dp).semantics {
            contentDescription = prompt
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/**
 * For a short side panel: the asking card at a size its printed numbers read at (tap to read it large), beside it
 * the [answers], compact (the head says the question).
 */
@Composable
private fun CompactQuestion(
    card: Card,
    focus: CardFocusState,
    answers: @Composable ColumnScope.() -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            onClick = { focus.show(listOf(card), title = "Asked by") },
            shape = RoundedCornerShape(4.dp),
            color = Color.Transparent,
            modifier = Modifier
                .cardFocusOrigin(focus, card.id)
                .clearAndSetSemantics {
                    contentDescription = spokenCard(card)
                    role = Role.Button
                    onClick(label = "Read the card") {
                        focus.show(listOf(card), title = "Asked by")
                        true
                    }
                },
        ) { TableCard(card, width = CARD_IN_SHORT_PANEL) }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            answers()
        }
    }
}

/**
 * The panel's one head (2026-11-08, show, don't tell; was two lines over a title plate and a prompt box): a bar in the
 * team's colour, the deciding hero's portrait in its team ring with the team's mark at its foot (a team's question:
 * the mark alone), then one line in near-white (ink on the light theme's paper), never in team-coloured text: the
 * question ([decisionHead]: "Who acts first?", "Defend against Arien"; someone else's, "Computer · choosing an
 * action"), and at its end the question's rules on demand ([RulesButton]). The hero's name is on the turn track, not
 * repeated here. TalkBack hears whose question and what, as one node and a polite live region: a new owner is heard.
 */
@Composable
fun DecisionWho(table: TableUi, modifier: Modifier = Modifier) {
    val decision = table.decision ?: return
    val hero = table.decider?.let { table.hero(it.heroId) }
    val head = decisionHead(table) ?: return
    val spoken = decisionHeaderSpoken(table) ?: head
    val ink = MaterialTheme.colorScheme.onSurface
    val scale = LocalDensity.current.fontScale
    val cap = minOf(scale, HEADER_MAX_SCALE) / scale
    Row(modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f, fill = false)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(WHO_BAR)
                    .heightIn(min = WHO_PORTRAIT)
                    .padding(vertical = 2.dp)
                    .background(teamColor(decision.team), RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(Spacing.sm))
            if (hero != null) {
                Box {
                    HeroPortrait(
                        hero.id,
                        hero.portrait,
                        null,
                        Modifier
                            .size(scaledIconSize(WHO_PORTRAIT, 1.2f))
                            .border(2.dp, teamColor(decision.team), CircleShape),
                        shape = CircleShape,
                        framed = false,
                    )
                    TeamBadge(decision.team, Modifier.align(Alignment.BottomEnd).badgeOverhang(WHO_BADGE), size = WHO_BADGE)
                }
            } else {
                TeamMark(decision.team, markSize = scaledIconSize(16.dp, 1.3f))
            }
            Spacer(Modifier.width(Spacing.sm + 2.dp))
            // Never cut: the line wraps, the strip grows.
            Text(
                head,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp * cap, lineHeight = 20.sp * cap),
                fontWeight = FontWeight.SemiBold,
                color = ink,
            )
        }
        RulesButton(decisionRules(table))
    }
}

/** True while the shown panel is at its peek (a space or piece being chosen on the map, [GameTable]). */
internal val LocalPanelPeeking = staticCompositionLocalOf { false }

private val WHO_PORTRAIT = 34.dp
private val WHO_BAR = 4.dp

/** The header grows with large text up to 1.4× (22 sp): past that it pushed the question under the fold. */
private const val HEADER_MAX_SCALE = 1.4f

private val WHO_BADGE = 13.dp

/** The card asking a card question, small with its name (tap to read it); TalkBack hears "Asked by …". */
@Composable
private fun AskedBy(card: Card, decision: Decision, focus: CardFocusState) {
    // "in radius", "in range" in the question are the card's own values (p.10): say them.
    val reach = sourceReach(decision.prompt, card)
    PlateToneSurface(
        onClick = { focus.show(listOf(card), title = "Asked by") },
        border = BorderStroke(1.dp, cardColor(card.color).copy(alpha = 0.8f)),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription =
                    listOfNotNull("Asked by ${card.name}", reach).joinToString(", ") + ". Read the card"
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableCard(card, Modifier.cardFocusOrigin(focus, card.id), width = 28.dp)
            Spacer(Modifier.width(Spacing.sm))
            // The card's name and its reach wrap rather than being cut.
            Text(
                listOfNotNull(card.name, reach).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * The asking card beside the buttons that answer it (about a quarter of the panel; smaller with large text). Tap the
 * card to read it in the card focus viewer; under it, its name and [numbers] for an action question (the chips use
 * them), and the card's reach when the question uses it.
 */
@Composable
private fun CardBeside(
    table: TableUi,
    card: Card,
    focus: CardFocusState,
    numbers: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reach = table.decision?.let { sourceReach(it.prompt, card) }
    val big = LocalDensity.current.fontScale > LARGE_TEXT
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A spell (Gydion) is a landscape card, about twice as wide at the same height: it goes above.
        val spell = card.isSpell
        val width = when {
            spell -> SPELL_BESIDE
            // Beside yes / no, numbers or "choose one" the card is a reminder, and with large text the answers need
            // the width: small, a tap reads it large.
            !numbers || big -> if (big) 56.dp else 72.dp
            else -> (maxWidth * BESIDE_SHARE).coerceIn(84.dp, 108.dp)
        }
        val read = { focus.show(listOf(card), title = "Asked by") }
        // The answers come first: the card beside them is a reminder, tap it to read it large.
        val shown: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = read,
                    shape = RoundedCornerShape(width * 0.08f),
                    color = Color.Transparent,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .cardFocusOrigin(focus, card.id)
                        .clearAndSetSemantics {
                            contentDescription = listOfNotNull(spokenCard(card), reach).joinToString(", ")
                            role = Role.Button
                            onClick(label = "Read the card") {
                                read()
                                true
                            }
                        },
                ) {
                    TableCard(card, width = width)
                }
                // Beside the answers there is room: the card's name and numbers in a strip under it, as in a hand.
                if (numbers && !spell && !big) {
                    CardCaption(card, width)
                    CardNumbers(card, width)
                }
                reach?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (spell) {
            // A landscape spell beside the answers leaves them a word per line: it goes above them.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                shown()
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = content)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                shown()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = content)
            }
        }
    }
}

// ---- Buttons ----------------------------------------------------------------------------

/**
 * Answers that go at once: actions as compact chips (the game's icon, the value from the card; the card action first
 * and filled, what it will do under it), the actions the card prints that can't be taken now as dimmed chips (a tap
 * says why), "Choose one" options, yes / decline, numbers, pass.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Choices(
    table: TableUi,
    choices: List<Choice>,
    onAnswer: (Choice) -> Unit,
    busy: Boolean,
    /** The card viewer, for a dimmed action whose reason is another card's effect. */
    focus: CardFocusState? = null,
    /** A short side panel: the actions two to a row, so all of them are in view. */
    brief: Boolean = false,
    afterPrimary: @Composable () -> Unit = {},
) {
    val unavailable = table.decision?.let(::unavailableActions).orEmpty()
    if (choices.isEmpty() && unavailable.isEmpty()) return
    val numbers = choices.filterIsInstance<Choice.Number>()
    if (numbers.isNotEmpty() && numbers.size == choices.size) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            numbers.forEach { n ->
                PlateButton(
                    onClick = { onAnswer(n) },
                    enabled = !busy,
                    tone = PlateTone.STEEL,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("${n.value}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        return
    }
    val actions = choices.filterIsInstance<Choice.Action>()
    if (actions.isNotEmpty() || unavailable.isNotEmpty()) {
        val primary = actions.firstOrNull { it.action == ActionType.PRIMARY }
        val others = actions - listOfNotNull(primary).toSet()
        val labels = actions.map { actionLabel(it.action, table.sourceCard) }
        // "Card action" only where the card's value alone would read as another chip (a Movement card's primary).
        val named = labels.size != labels.distinct().size
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // A short side panel: every action two to a row, the card action first, so all are in view.
            if (!brief) {
                primary?.let {
                    ActionChip(table, it.action, busy, named = named, modifier = Modifier.fillMaxWidth()) { onAnswer(it) }
                    afterPrimary()
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                maxItemsInEachRow = if (brief) 2 else Int.MAX_VALUE,
            ) {
                val each = if (brief) Modifier.weight(1f) else Modifier
                if (brief) {
                    primary?.let {
                        ActionChip(table, it.action, busy, named = named, modifier = each, oneLine = true) {
                            onAnswer(it)
                        }
                    }
                }
                others.forEach { choice ->
                    ActionChip(table, choice.action, busy, modifier = each, oneLine = brief) { onAnswer(choice) }
                }
                unavailable.forEach { u -> UnavailableChip(table, u, focus, each) }
            }
            if (brief && primary != null) afterPrimary()
        }
    }
    choices.filter { it !is Choice.Action }.forEach { choice ->
        when (choice) {
            is Choice.Decline, is Choice.Pass -> PlateButton(
                onClick = { onAnswer(choice) },
                enabled = !busy,
                tone = PlateTone.STEEL,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(table.label(choice), textAlign = TextAlign.Center) }

            else -> PlateToneSurface(
                onClick = { onAnswer(choice) },
                enabled = !busy,
                tone = PlateTone.GOLD,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Box48 {
                    (choice as? Choice.Option)?.let { table.decision?.context?.optionMarkers?.get(it.index) }?.let {
                        OfficialIcon(markerArt(it), Modifier.size(OPTION_MARKER).clearAndSetSemantics {})
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text(
                        table.label(choice),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun Box48(content: @Composable () -> Unit) {
    Row(
        Modifier.padding(horizontal = Spacing.md, vertical = 6.dp).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * One action as a chip: the game's icon and the value the card prints ("Movement 4"); the card action filled bronze
 * ("Card action · Movement 4" only when its value alone would read as another chip). The rule a card doesn't print
 * (Fast Travel's condition, Hold, Clear) is in the head's rules ([decisionRules]); TalkBack hears it here whole.
 */
@Composable
private fun ActionChip(
    table: TableUi,
    action: ActionType,
    busy: Boolean,
    modifier: Modifier = Modifier,
    named: Boolean = false,
    /** Two to a row: the label on one line, a step smaller rather than broken mid-word. */
    oneLine: Boolean = false,
    onClick: () -> Unit,
) {
    val card = table.sourceCard
    val primary = action == ActionType.PRIMARY
    val label = actionLabel(action, card)
    val shown = if (primary && named) "Card action · $label" else label
    val rule = actionRule(action)?.replace(PAGE, "")?.trim()
    val tone = if (primary) PlateTone.GOLD else PlateTone.STEEL
    val content = tone.content
    PlateToneSurface(
        onClick = onClick,
        enabled = !busy,
        tone = tone,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(
                    if (primary) "Card action · $label" else label,
                    "the card's primary action".takeIf { primary },
                    rule,
                ).joinToString(", ")
                role = Role.Button
                if (busy) disabled()
                onClick(label = "Choose") {
                    onClick()
                    true
                }
            },
    ) {
        Row(
            Modifier.padding(start = Spacing.sm, end = Spacing.md, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                actionIcon(action, card).vector,
                null,
                tint = when {
                    primary -> content
                    GoaTheme.isDark -> SteelIconBronze // Bright bronze on the dark steel.
                    else -> MaterialTheme.colorScheme.primary // Dark bronze on the light steel.
                },
                modifier = Modifier.size(scaledIconSize(20.dp, 1.3f)),
            )
            Spacer(Modifier.width(6.dp))
            if (oneLine) {
                Text(
                    shown,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp * LocalDensity.current.fontScale),
                )
            } else {
                Text(shown, style = MaterialTheme.typography.titleSmall, color = content, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * An action the card prints that can't be taken now, dimmed with a block mark (so a lone Hold never looks like a
 * broken app): a tap shows the engine's reason (`DecisionContext.unavailable`, ADR-041) and, when the reason is another
 * card's effect, that card a tap further.
 */
@Composable
private fun UnavailableChip(table: TableUi, u: UnavailableAction, focus: CardFocusState?, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val card = table.sourceCard
    val label = actionLabel(u.action, card)
    val by = u.byCardId?.let { table.cards[it] }
    Box(modifier) {
        PlateToneSurface(
            onClick = { open = true },
            tone = PlateTone.STEEL,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .alpha(UNAVAILABLE_ALPHA)
                .clearAndSetSemantics {
                    contentDescription = "$label, not available: ${unavailableLine(u)}"
                    role = Role.Button
                    onClick(label = "Why") {
                        open = true
                        true
                    }
                },
        ) {
            Row(
                Modifier.padding(start = Spacing.sm, end = Spacing.md, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Block, null, Modifier.size(scaledIconSize(18.dp, 1.3f)))
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = TextDecoration.LineThrough,
                    color = PlateTone.STEEL.content,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(
                Modifier.widthIn(max = 320.dp).padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                RuleList(listOf(DecisionRule("${u.text}.", u.source)))
                if (by != null && focus != null) {
                    TextButton(onClick = {
                        open = false
                        focus.show(listOf(by), title = "Forbidden by")
                    }) { Text("Read ${by.name}") }
                }
            }
        }
    }
}

private const val UNAVAILABLE_ALPHA = 0.6f

// ---- Cards ------------------------------------------------------------------------------

private val OPTION_MARKER = 36.dp
private const val BESIDE_SHARE = 0.28f
private val SPELL_BESIDE = 96.dp
private const val LARGE_TEXT = 1.3f
private val CARD_IN_SHORT_PANEL = 64.dp
private val CARD_THUMB_ROW = 40.dp
private val SPELL_THUMB_ROW = 72.dp
private val PAGE = Regex("\\(p\\.\\d+\\)$")

/** A phone held sideways with little height: the asking card beside compact actions, every one in view. */
@Composable
private fun shortSideways(): Boolean = isLandscape() && LocalConfiguration.current.screenHeightDp < SHORT_LANDSCAPE_DP

/** Sideways and shorter than this, the asking card beside the actions is small: every action stays in view. */
private const val SHORT_LANDSCAPE_DP = 480
