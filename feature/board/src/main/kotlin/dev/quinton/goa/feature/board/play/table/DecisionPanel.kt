package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import dev.quinton.goa.core.designsystem.component.ActionIcon
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.LookAction
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.SteelIconBronze
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.TitlePlate
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
import dev.quinton.goa.feature.board.ui.boxText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.isSpell
import dev.quinton.goa.feature.board.ui.isLandscape
import dev.quinton.goa.feature.board.ui.smallBoxText

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
    val beside = layout == PanelLayout.CARD_BESIDE && source != null
    // The table sizes its panel to where the question ends; what follows it (what happened, the hands) scrolls.
    val questionEnd = LocalPanelQuestionEnd.current
    DisposableEffect(questionEnd) { onDispose { questionEnd?.invoke(null) } }
    Column(
        modifier
            .fillMaxWidth()
            .onPlaced { questionEnd?.invoke(it.positionInParent().y.toInt() + it.size.height) },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PromptHeader(
            table,
            decision,
            focus,
            who = who,
            // Asked by a card: the prompt goes in the card's text box, beside it. Defend says it in its attack header.
            prompt = decision.kind != DecisionKind.DEFEND && !beside,
            askedBy = layout == PanelLayout.CARDS && decision.kind != DecisionKind.DEFEND,
        )
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

            // A phone sideways, short: every action in view beside a card still read at a glance.
            layout == PanelLayout.CARD_BESIDE && source != null && !hasBoardOptions(table) && shortSideways() -> {
                CompactQuestion(source, decision, focus) {
                    Choices(table, table.buttonOptions, onAnswer, busy, brief = true) { CastPreview(table, focus) }
                }
                NoteBox(PlateColor.of(source.color), table, decision, focus)
                SpellReasonsIfNoPrimary(table)
                Footnote(table, decision)
            }

            layout == PanelLayout.CARD_BESIDE && source != null -> {
                val board = hasBoardOptions(table)
                CardBeside(table, source, focus, large = !board) {
                    // "Choose an action for <the card beside>" said nothing the card and its chips don't: the
                    // header names who decides, the chips are the question (TalkBack still hears the prompt).
                    if (board || decision.kind != DecisionKind.CHOOSE_ACTION) {
                        QuestionBox(table, source, decision, board)
                    } else {
                        SpokenPrompt(decision.prompt)
                    }
                    if (board) BoardConfirm(table, onAnswer, busy)
                    Choices(table, table.buttonOptions, onAnswer, busy) { CastPreview(table, focus) }
                    NoteBox(PlateColor.of(source.color), table, decision, focus)
                    SpellReasonsIfNoPrimary(table)
                    Footnote(table, decision)
                }
                if (board) BoardChips(table, onSelect)
            }

            else -> {
                if (hasBoardOptions(table)) {
                    BoardHint(table)
                    BoardConfirm(table, onAnswer, busy)
                    BoardChips(table, onSelect)
                }
                Choices(table, table.buttonOptions, onAnswer, busy)
                NoteBox(PlateColor.SILVER, table, decision, focus)
                Footnote(table, decision)
            }
        }
    }
}

private fun hasBoardOptions(table: TableUi) = table.spaceOptions.isNotEmpty() || table.pieceOptions.isNotEmpty()

/** What a question rests on that no card prints, in the app's words, and the rulebook page it is on. */
private fun footnote(table: TableUi, decision: Decision): Pair<String, String>? = when {
    // With the card action offered, its chip carries p.7; without it, why the card's text doesn't apply.
    decision.kind == DecisionKind.CHOOSE_ACTION && table.buttonOptions.any { it is Choice.Action } &&
        Choice.Action(ActionType.PRIMARY) !in table.buttonOptions ->
        "Only the card action applies the card's text." to "p.7"

    // A bare "who acts first?" over two portraits told nothing about why it was asked.
    decision.kind == DecisionKind.ACT_FIRST ->
        "Heroes of one team on the same initiative choose which of them acts first; between " +
            "teams the Tie Breaker coin decides, then it flips." to "p.7"

    else -> null
}

/** The question's one rule row ([RuleRow]): its page there, and nowhere on its buttons. */
@Composable
private fun Footnote(table: TableUi, decision: Decision) {
    footnote(table, decision)?.let { (text, page) -> RuleRow(text, page, live = false) }
}

/**
 * The actions the asking card prints that the question can't offer, in the card's text box, in the app's type (a
 * reason may be situational). Its tab says "Note": these are not the rulebook's words.
 */
@Composable
private fun NoteBox(color: PlateColor, table: TableUi, decision: Decision, focus: CardFocusState) {
    if (unavailableActions(decision).isEmpty()) return
    CardTextBox(color, Modifier.fillMaxWidth(), cap = "Note") {
        Unavailable(table, decision, focus)
    }
}

/**
 * The question asked by a card, on the card's own text box (mock-up 03): the action on its tab, the
 * rulebook's action symbol, the prompt (a live region) and how to answer on the board.
 */
@Composable
private fun QuestionBox(
    table: TableUi,
    card: Card,
    decision: Decision,
    board: Boolean,
) {
    val action = table.track?.slots
        ?.firstOrNull { it.heroId == decision.seat && it.cards.any { view -> view.cardId == card.id } }
        ?.action
    // A question that isn't the card's action (respawning before it resolves, p.7) is labelled by what it asks: never
    // an ATTACK tab and crossed blades over "Respawn in your Throne Zone?".
    val own = ownQuestion(decision.kind)
    CardTextBox(
        if (own != null) PlateColor.SILVER else PlateColor.of(card.color),
        Modifier.fillMaxWidth(),
        cap = own ?: action?.let { actionWord(it, card) } ?: PlayText.primary(card.primary),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            lookAction(action, card)?.takeIf { own == null }?.let {
                ActionIcon(it, size = scaledIconSize(28.dp, 1.3f))
                Spacer(Modifier.width(Spacing.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    decision.prompt,
                    style = boxText(),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                // How to answer, with the "List" chip beside it (sol #2): the map's options as rows, one tap away.
                if (board) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(boardHint(table), style = boxText(), modifier = Modifier.weight(1f))
                        MapListToggle(table, Modifier.padding(start = Spacing.xs))
                    }
                }
            }
        }
    }
}

/** The tab of a question asked while a card resolves but not by its action, by the question's own kind; else null. */
private fun ownQuestion(kind: DecisionKind): String? = when (kind) {
    DecisionKind.RESPAWN, DecisionKind.ACT_FIRST, DecisionKind.MINION_RETURN, DecisionKind.REMOVE_MINION ->
        kindTitle(kind)

    else -> null
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
 * the question and under that the [answers], compact.
 */
@Composable
private fun CompactQuestion(
    card: Card,
    decision: Decision,
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
            // Choosing the action for the card beside: the chips are the question.
            if (decision.kind == DecisionKind.CHOOSE_ACTION) {
                SpokenPrompt(decision.prompt)
            } else {
                Text(
                    decision.prompt,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            answers()
        }
    }
}

/** The rulebook's symbol for the action being resolved, or the card's primary while none is chosen yet. */
private fun lookAction(action: ActionType?, card: Card): LookAction? = when (action) {
    ActionType.MOVEMENT, ActionType.FAST_TRAVEL -> LookAction.MOVEMENT

    ActionType.ATTACK, ActionType.CLEAR -> LookAction.ATTACK

    ActionType.HOLD -> null

    ActionType.PRIMARY, null -> when (card.primary) {
        PrimaryAction.ATTACK -> LookAction.ATTACK
        PrimaryAction.SKILL -> LookAction.SKILL
        PrimaryAction.DEFENSE -> LookAction.DEFENSE
        PrimaryAction.MOVEMENT -> LookAction.MOVEMENT
        else -> null
    }
}

/**
 * The actions the asking card prints that aren't offered, one line each with the engine's reason
 * (`DecisionContext.unavailable`, ADR-041), so a lone Hold never looks like a broken app. A reason
 * that is another card's effect opens that card when tapped.
 */
@Composable
private fun Unavailable(table: TableUi, decision: Decision, focus: CardFocusState) {
    unavailableActions(decision).forEach { u ->
        val by = u.byCardId?.let { table.cards[it] }
        val tap = if (by == null) {
            Modifier
        } else {
            Modifier
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = "Read ${by.name}") { focus.show(listOf(by), title = "Forbidden by") }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .then(tap)
                .padding(vertical = 2.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info,
                null,
                tint = LocalContentColor.current,
                modifier = Modifier.padding(top = 2.dp).size(scaledIconSize(16.dp, 1.3f)),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(unavailableLine(u), style = smallBoxText())
        }
    }
}

/**
 * Who decides and what, a two-line directive a phone passed round the table is read by (UI hill climb, round 4): a
 * bar in the team's colour, the deciding hero's portrait in its team ring with the team's mark at its foot (a team's
 * question: the mark alone), then in near-white (ink on the light theme's paper), never in team-coloured text: the
 * owner, "You choose · Wasp" or "Computer · Wasp acting" ([decisionOwner]), and the instruction, "Choose a space" or
 * "Defend against Arien" ([decisionDirective]). While the panel peeks ([LocalPanelPeeking]), the instruction is the
 * engine's whole prompt, as nothing under it says it. One TalkBack node, a polite live region: a new owner is heard.
 */
@Composable
fun DecisionWho(table: TableUi, modifier: Modifier = Modifier) {
    val decision = table.decision ?: return
    val hero = table.decider?.let { table.hero(it.heroId) }
    val owner = decisionOwner(table) ?: return
    val peeking = LocalPanelPeeking.current && table.asking
    val directive = if (peeking) decision.prompt else decisionDirective(table)
    val spoken = listOfNotNull(owner, directive).joinToString(". ")
    val ink = MaterialTheme.colorScheme.onSurface
    val scale = LocalDensity.current.fontScale
    val cap = minOf(scale, HEADER_MAX_SCALE) / scale
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(WHO_BAR)
                .fillMaxHeight()
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
        // Never cut: both lines wrap, the strip grows.
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                owner,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp * cap, lineHeight = 20.sp * cap),
                fontWeight = FontWeight.Bold,
                color = ink,
            )
            directive?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp * cap, lineHeight = 21.sp * cap),
                    fontWeight = FontWeight.Medium,
                    color = ink.copy(alpha = DIRECTIVE_ALPHA),
                )
            }
        }
    }
}

/**
 * True while the shown panel is at its peek (a space or piece being chosen on the map, [GameTable]): the header then
 * carries the engine's whole instruction ([DecisionWho]).
 */
internal val LocalPanelPeeking = staticCompositionLocalOf { false }

private val WHO_PORTRAIT = 34.dp
private val WHO_BAR = 4.dp
private const val DIRECTIVE_ALPHA = 0.94f

/** The header grows with large text up to 1.4× (22 sp): past that it pushed the question under the fold. */
private const val HEADER_MAX_SCALE = 1.4f

/** The longest instruction that goes on a title plate (as `promptParts` puts a few words there). */
private const val PROMPT_PLATE = 30
private val WHO_BADGE = 13.dp

/**
 * [DecisionWho] unless the panel's strip says it ([who]), the prompt (a live region; a question asked by a card
 * has it in the card's text box, Defend in its attack header): a few words on the title plate, anything longer in
 * the text box under it ([promptParts]); for card questions "Asked by <card>" with a thumbnail (tap to read it).
 */
@Composable
private fun PromptHeader(
    table: TableUi,
    decision: Decision,
    focus: CardFocusState,
    who: Boolean,
    prompt: Boolean,
    askedBy: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (who) DecisionWho(table)
        if (prompt) {
            val parts = promptParts(decision.prompt).let { parts ->
                // "Minion Battle: remove a minion" under "Gydion decides · Minion Battle": the title is said once,
                // on the strip; the plate holds what to do.
                val text = parts.text
                if (parts.title.equals(kindTitle(decision.kind), ignoreCase = true) && text != null &&
                    text.length <= PROMPT_PLATE
                ) {
                    PromptParts(text, null)
                } else {
                    parts
                }
            }
            // The parchment plate is the question's title ("DISCARD A CARD"); the header's second line then says only
            // whom it answers, never the same words again in another style ([decisionDirective]).
            Column(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                parts.title?.let { TitlePlate(it, Modifier.fillMaxWidth(), maxLines = Int.MAX_VALUE) }
                parts.text?.let { text ->
                    val color = table.sourceCard?.let { PlateColor.of(it.color) } ?: PlateColor.SILVER
                    CardTextBox(color, Modifier.fillMaxWidth()) { Text(text, style = boxText()) }
                }
            }
        }
        val card = table.sourceCard
        if (askedBy && card != null) AskedBy(card, decision, focus)
    }
}

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
            Text(
                "Asked by ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
 * The asking card beside what answers it: large (about a third of the panel, up to 150 dp; smaller
 * with large text) when the answers are buttons, smaller beside a board question's hint. Tap the
 * card to read it in the card focus viewer; under it, the card's reach when the question uses it.
 */
@Composable
private fun CardBeside(
    table: TableUi,
    card: Card,
    focus: CardFocusState,
    large: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reach = table.decision?.let { sourceReach(it.prompt, card) }
    val big = LocalDensity.current.fontScale > LARGE_TEXT
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A spell (Gydion) is a landscape card, about twice as wide at the same height: it goes above.
        val spell = card.isSpell
        val width = when {
            !large -> if (big) 56.dp else 72.dp
            spell -> SPELL_BESIDE
            big -> (maxWidth * BESIDE_SHARE).coerceIn(80.dp, 100.dp)
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
                if (large && !spell) {
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
        if (large && (big || spell)) {
            // Large text beside a card leaves the answers a word per line: the card goes above them.
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
 * Answers that go at once: actions as compact chips (the game's icon, the value from the card),
 * "Choose one" options, yes / decline, numbers, pass.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Choices(
    table: TableUi,
    choices: List<Choice>,
    onAnswer: (Choice) -> Unit,
    busy: Boolean,
    /** A short side panel: each action's rule only by its page (TalkBack still hears it whole). */
    brief: Boolean = false,
    afterPrimary: @Composable () -> Unit = {},
) {
    if (choices.isEmpty()) return
    val numbers = choices.filterIsInstance<Choice.Number>()
    if (numbers.size == choices.size) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            numbers.forEach { n ->
                PlateButton(
                    onClick = { onAnswer(n) },
                    enabled = !busy,
                    tone = PlateTone.STEEL,
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    Text("${n.value}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        return
    }
    if (brief && choices.all { it is Choice.Action }) {
        // A short side panel: two actions to a row, so all four are in view with the round and the hand's tab.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            choices.filterIsInstance<Choice.Action>().chunked(2).forEach { pair ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    // Fast Travel beside Hold takes the width Hold doesn't need: its condition reads on two lines.
                    val roomy = pair.size == 2 && pair.any { it.action == ActionType.HOLD }
                    pair.forEach { choice ->
                        val share = if (roomy && pairedRule(choice.action) != null) ROOMY_SHARE else 1f
                        ActionChip(
                            table,
                            choice.action,
                            busy,
                            modifier = Modifier.weight(share).fillMaxHeight(),
                            brief = true,
                            paired = true,
                        ) { onAnswer(choice) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                if (pair.any { it.action == ActionType.PRIMARY }) afterPrimary()
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        choices.forEach { choice ->
            when {
                choice is Choice.Action -> {
                    ActionChip(table, choice.action, busy, brief = brief) { onAnswer(choice) }
                    if (choice.action == ActionType.PRIMARY) afterPrimary()
                }

                choice is Choice.Decline || choice is Choice.Pass -> PlateButton(
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
}

@Composable
private fun Box48(content: @Composable () -> Unit) {
    Row(
        Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * One action as a chip: the game's icon and the value the card prints ("Movement 4"), the primary
 * filled and marked; a rule the card doesn't print in a few words with its page (Fast Travel's
 * condition, Hold, Clear). TalkBack reads it whole.
 */
@Composable
private fun ActionChip(
    table: TableUi,
    action: ActionType,
    busy: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    /** A short side panel: a long rule shows only its page on the chip (TalkBack still hears it whole). */
    brief: Boolean = false,
    /** Two to a row (a short side panel): the glyph and page over the words, which keep the chip's width. */
    paired: Boolean = false,
    onClick: () -> Unit,
) {
    val card = table.sourceCard
    val primary = action == ActionType.PRIMARY
    val label = if (primary) "Card action · ${actionLabel(action, card)}" else actionLabel(action, card)
    // The rule a card doesn't print, in a few words; its page a small pill at the chip's end, never in the words.
    val full = actionRule(action)
    val rule = full?.replace(PAGE, "")?.trim()
    val page = if (primary) "p.7" else full?.let { PAGE.find(it)?.value?.trim('(', ')') }
    val tone = if (primary) PlateTone.GOLD else PlateTone.STEEL
    val content = tone.content
    PlateToneSurface(
        onClick = onClick,
        enabled = !busy,
        tone = tone,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription =
                    listOfNotNull(label, "the card's primary action".takeIf { primary }, rule).joinToString(", ")
                role = Role.Button
                if (busy) disabled()
                onClick(label = "Choose") {
                    onClick()
                    true
                }
            },
    ) {
        if (paired) {
            PairedChipContent(action, card, primary, page, content, pairedRule(action))
            return@PlateToneSurface
        }
        Row(
            Modifier.padding(horizontal = if (brief) Spacing.sm else Spacing.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                actionIcon(action, card).vector,
                null,
                tint = when {
                    primary -> content

                    GoaTheme.isDark -> SteelIconBronze

                    // Bright bronze on the dark steel.
                    else -> MaterialTheme.colorScheme.primary // Dark bronze on the light steel.
                },
                modifier = Modifier.size(scaledIconSize(20.dp, 1.3f)),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                )
                // Brief (a short side panel): the label alone, every action in view (TalkBack hears the rule).
                if (!brief) rule?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = content) }
            }
            page?.let {
                Spacer(Modifier.width(Spacing.sm))
                PagePill(it, color = content)
            }
        }
    }
}

/**
 * An action chip two to a row: the game's glyph and the rule's page on a line of their own, then the words at the
 * chip's whole width ("Card action" over the card's value for the primary), so no word is broken.
 */
@Composable
private fun PairedChipContent(
    action: ActionType,
    card: Card?,
    primary: Boolean,
    page: String?,
    content: Color,
    rule: String?,
) {
    if (rule != null) {
        // A condition the card doesn't print (Fast Travel's), kept in view sideways too (sol-b #15): the glyph, the
        // words and the page on one line, the condition under them.
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    actionIcon(action, card).vector,
                    null,
                    tint = if (GoaTheme.isDark) SteelIconBronze else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(scaledIconSize(20.dp, 1.3f)),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    actionLabel(action, card),
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                    // On one line: a little smaller rather than broken, never cut.
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp),
                    modifier = Modifier.weight(1f),
                )
                page?.let {
                    Spacer(Modifier.width(Spacing.xs))
                    PagePill(it, color = content)
                }
            }
            Text(rule, style = MaterialTheme.typography.bodySmall, color = content)
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                actionIcon(action, card).vector,
                null,
                tint = when {
                    primary -> content
                    GoaTheme.isDark -> SteelIconBronze
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(scaledIconSize(20.dp, 1.3f)),
            )
            Spacer(Modifier.weight(1f))
            page?.let { PagePill(it, color = content) }
        }
        if (primary) Text("Card action", style = MaterialTheme.typography.labelMedium, color = content)
        Text(
            actionLabel(action, card),
            style = MaterialTheme.typography.titleSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The condition an action chip two to a row keeps in view, in fewer words than [actionRule] (whose whole sentence
 * TalkBack still hears): Fast Travel's, p.11. Null for the others (Hold's "Do nothing" is its name).
 */
private fun pairedRule(action: ActionType): String? = when (action) {
    ActionType.FAST_TRAVEL -> "Your/adjacent Zone · no enemies"
    else -> null
}

/** Fast Travel's share of its row beside Hold. */
private const val ROOMY_SHARE = 2f

// ---- Cards ------------------------------------------------------------------------------

private val OPTION_MARKER = 36.dp
private const val BESIDE_SHARE = 0.28f
private val SPELL_BESIDE = 96.dp
private const val LARGE_TEXT = 1.3f
private val CARD_IN_SHORT_PANEL = 64.dp
private val PAGE = Regex("\\(p\\.\\d+\\)$")

/** A phone held sideways with little height: the asking card beside compact actions, every one in view. */
@Composable
private fun shortSideways(): Boolean = isLandscape() && LocalConfiguration.current.screenHeightDp < SHORT_LANDSCAPE_DP

/** Sideways and shorter than this, the asking card beside the actions is small: every action stays in view. */
private const val SHORT_LANDSCAPE_DP = 480
