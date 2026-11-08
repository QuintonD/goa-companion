package dev.quinton.goa.feature.board.play.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.BannerTextButton
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaSegment
import dev.quinton.goa.core.designsystem.component.GoaSegmentedRow
import dev.quinton.goa.core.designsystem.component.GoaSlider
import dev.quinton.goa.core.designsystem.component.GoaSwitch
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.SectionHeaderPaddingTight
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.bottomEdgeFade
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.engine.Counters
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SearchField
import dev.quinton.goa.feature.board.list.LineupHeroes
import dev.quinton.goa.feature.board.map.PreloadMapPainting
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.puzzles.PuzzleText
import dev.quinton.goa.feature.board.play.table.SpellbookChip
import dev.quinton.goa.feature.board.play.table.SpellbookSheet
import dev.quinton.goa.feature.board.play.table.TieBreakerMark
import dev.quinton.goa.feature.board.play.table.catalogSpellbook
import dev.quinton.goa.feature.board.setup.HeroGrid
import dev.quinton.goa.feature.board.setup.HistoryButton
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.SourcesContent
import dev.quinton.goa.feature.board.setup.TeamSwitch
import dev.quinton.goa.feature.board.setup.Teams
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.cards.CardFocusHost
import dev.quinton.goa.feature.board.ui.cards.rememberCardFocusState
import kotlin.math.roundToInt

/**
 * New game against the computer, in two steps. **Line-up:** both teams in pick order, search with
 * the Red | Blue switch, the hero grid (heroes not playable yet are veiled, "Later"), and "Use a
 * draft or match". **Options:** who you play, the computer's strength 1–10 (per seat under Advanced),
 * Quick or Long, the map, Coach; then the app flips the Tie Breaker coin and the game starts.
 */
@Composable
fun NewGameScreen(
    state: NewGameUiState,
    onAction: (NewGameAction) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    starting: Boolean = false,
    initialSources: Boolean = false,
    initialSpellbook: String? = null,
) {
    val ready = state as? NewGameUiState.Ready
    var sources by rememberSaveable { mutableStateOf(initialSources) }
    var spellbook by rememberSaveable { mutableStateOf(initialSpellbook) }
    val focus = rememberCardFocusState()
    val options = ready?.form?.step == NewGameStep.OPTIONS
    GoaScreen(
        title = if (options) "Game options" else "New game",
        onBack = { if (options) onAction(NewGameAction.Step(NewGameStep.LINEUP)) else onBack() },
        modifier = modifier,
        actions = {
            if (ready != null && !options) {
                if (ready.red.isNotEmpty() || ready.blue.isNotEmpty()) {
                    BannerTextButton("Clear", onClick = { onAction(NewGameAction.Lineup(NewBoardAction.Clear)) })
                }
                // Past line-ups in words on the bar ("History"), there only when there is one to use.
                if (ready.sources.isNotEmpty()) HistoryButton(onClick = { sources = true })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                NewGameUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                NewGameUiState.ComingSoon -> ComingSoon(onBack, Modifier.align(Alignment.Center))

                is NewGameUiState.Ready -> if (options) {
                    OptionsStep(state, onAction, starting, onSpellbook = { spellbook = it })
                } else {
                    LineupStep(state, onAction, onSpellbook = { spellbook = it })
                }
            }
        }
    }
    if (ready != null && sources) {
        BoardSheet(onDismiss = { sources = false }) {
            SourcesContent(
                ready.sources,
                onUse = {
                    onAction(NewGameAction.Lineup(NewBoardAction.Use(it)))
                    sources = false
                },
                onDone = { sources = false },
            )
        }
    }
    // Gydion's 22 spell cards, from the line-up's menu or "You play" (ADR-040).
    val book = spellbook?.let { id -> ready?.picker?.heroById?.get(id) }?.let(::catalogSpellbook)
    if (book != null) SpellbookSheet(book, focus, onDismiss = { spellbook = null })
    CardFocusHost(focus)
    val coin = ready?.form?.coin
    if (ready != null && coin != null) {
        CoinDialog(coin, starting, onStart = onStart, onDismiss = { onAction(NewGameAction.CancelCoin) })
    }
}

/** No hero can be seated yet: the rules engine is being built and tested card by card. */
@Composable
private fun ComingSoon(onBack: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Hexagon,
        title = "Playing against the computer is coming",
        message = "The app is learning every rule and every card. Heroes join as soon as each of their cards " +
            "is built and tested, so the computer never plays a card wrong. Until then, set up positions on the board.",
        modifier = modifier,
        action = { TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") } },
    )
}

// ---- Step 1: line-up ---------------------------------------------------------------------

@Composable
private fun LineupStep(
    state: NewGameUiState.Ready,
    onAction: (NewGameAction) -> Unit,
    onSpellbook: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val picker = state.picker
    val shown = picker.heroes.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
            it.title.contains(query.trim(), ignoreCase = true)
    }
    val large = LocalDensity.current.fontScale > LARGE_FONT
    val lineup: (NewBoardAction) -> Unit = { onAction(NewGameAction.Lineup(it)) }
    val search = @Composable { mod: Modifier ->
        // Plate-dark in both themes, as on New board.
        GoaPlateTheme {
            if (large) {
                Column(mod) {
                    SearchField(query, {
                        query = it
                    }, onSearch = { focus.clearFocus() }, horizontalPadding = 0.dp, placeholder = "Search")
                    TeamSwitch(state.form.lineup.filling, onPick = { lineup(NewBoardAction.Fill(it)) })
                }
            } else {
                Row(mod, verticalAlignment = Alignment.CenterVertically) {
                    SearchField(
                        query,
                        { query = it },
                        onSearch = { focus.clearFocus() },
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 0.dp,
                        placeholder = "Search",
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    TeamSwitch(state.form.lineup.filling, onPick = { lineup(NewBoardAction.Fill(it)) })
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.42f).fillMaxHeight()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Teams(picker, lineup, onSpellbook)
                        PlayableNote(state)
                    }
                    LineupBar(state, onAction)
                }
                Column(Modifier.weight(0.58f).fillMaxHeight()) {
                    search(Modifier.padding(start = Spacing.sm, end = Spacing.screen))
                    HeroGrid(shown, picker, lineup, large, start = Spacing.sm, end = Spacing.screen)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Teams(picker, lineup, onSpellbook)
                    PlayableNote(state)
                }
                search(Modifier.padding(horizontal = Spacing.screen))
                HeroGrid(shown, picker, lineup, large, start = Spacing.screen, end = Spacing.screen)
                LineupBar(state, onAction)
            }
        }
    }
}

/** How many heroes can be played so far: the rest are veiled in the grid. */
@Composable
private fun PlayableNote(state: NewGameUiState.Ready) {
    val later = state.picker.heroById.size - state.playable
    if (later <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Info,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "${BoardText.count(state.playable, "hero", "heroes")} can play so far; " +
                "$later more join as their cards are tested.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The line-up's size in the rulebook's terms (p.20), and Next. */
@Composable
private fun LineupBar(state: NewGameUiState.Ready, onAction: (NewGameAction) -> Unit) {
    val red = state.red.size
    val blue = state.blue.size
    val note = lineupNote(red, blue)
    Column(Modifier.fillMaxWidth()) {
        EngravedRule()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("$red v $blue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            GoaButton(
                onClick = { onAction(NewGameAction.Step(NewGameStep.OPTIONS)) },
                enabled = state.canContinue,
                modifier = Modifier.heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = Spacing.xl),
            ) { Text("Next", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

// ---- Step 2: options ---------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsStep(
    state: NewGameUiState.Ready,
    onAction: (NewGameAction) -> Unit,
    starting: Boolean,
    onSpellbook: (String) -> Unit,
) {
    PreloadMapPainting(state.map?.painting)
    val page = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        // The page fades into the pinned coin bar while more is below, and ends clear of it.
        Column(
            Modifier
                .weight(1f)
                .bottomEdgeFade(page)
                .verticalScroll(page)
                .padding(horizontal = Spacing.screen)
                .padding(top = Spacing.md, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PlateSurface(
                onClick = { onAction(NewGameAction.Step(NewGameStep.LINEUP)) },
                brackets = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Line-up. Change" },
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlowRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        LineupHeroes(Team.RED, state.red.map(state::hero))
                        Text(
                            "vs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LineupHeroes(Team.BLUE, state.blue.map(state::hero))
                    }
                    Text(
                        "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Section("You play") { YouPlay(state, onAction, onSpellbook) }

            // The value ("8 · Hard") on the head's line: the page fits a phone without the Coach under the bar.
            Section("Computer strength", trailing = { StrengthValue(state.form.strength) }) {
                StrengthSlider(state.form.strength, "Computer strength", showValue = false) {
                    onAction(NewGameAction.SetStrength(it))
                }
                Text(
                    PlayText.strengthNote(state.form.strength),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.aiSeats.size > 1) Advanced(state, onAction)
            }

            Section("Length", note = lengthNote(state)) {
                // Each length with the counters it starts with, as the engine sets them up (p.5): the choice reads
                // without the rulebook (sol #17).
                val lanes = state.map?.lanes ?: 1
                val heroes = state.red.size + state.blue.size
                GoaSegmentedRow(Modifier.fillMaxWidth()) {
                    GameLength.entries.forEach { length ->
                        val counts = startingCounters(lanes, length, heroes)
                        GoaSegment(
                            selected = state.form.length == length,
                            onClick = { onAction(NewGameAction.SetLength(length)) },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(PlayText.length(length), maxLines = 1)
                                Text(
                                    counts,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            Section("Map") { MapChoice(state, onAction) }

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = state.form.coach, role = Role.Switch) {
                        onAction(NewGameAction.SetCoach(it))
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "COACH",
                        style = GoaTextStyles.overline,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "A nudge before a clearly worse choice, the turning points after. It never chooses for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                GoaSwitch(checked = state.form.coach, onCheckedChange = null)
            }
        }
        Column(Modifier.fillMaxWidth()) {
            EngravedRule()
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
                if (state.replacesGame) {
                    Text(
                        "Starting replaces the game in progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoaTheme.colors.unverified,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                }
                GoaButton(
                    onClick = { onAction(NewGameAction.FlipCoin) },
                    enabled = state.canStart && !starting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    // It starts the game, and says so: the coin flip is how (opus, round 4).
                    Text(
                        "Start game · flip the coin",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    note: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(title, padding = SectionHeaderPaddingTight, trailing = trailing)
        content()
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Who you play, in two short steps instead of a row per choice: "Whole team | One hero" (2 v 2 and up), then the
 * team or the hero, as portrait chips; one line under them says who the computer plays. 1 v 1 is just the hero.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YouPlay(state: NewGameUiState.Ready, onAction: (NewGameAction) -> Unit, onSpellbook: (String) -> Unit) {
    val control = state.control ?: return
    val teams = state.controls.filterIsInstance<Control.WholeTeam>()
    val heroes = state.controls.filterIsInstance<Control.Hero>()
    val whole = control is Control.WholeTeam
    val myTeam = teamOf(state, control)
    if (teams.isNotEmpty()) {
        val kinds = listOf(false, true)
        GoaSegmentedRow(Modifier.fillMaxWidth()) {
            kinds.forEachIndexed { i, kind ->
                GoaSegment(
                    selected = whole == kind,
                    onClick = {
                        if (whole != kind) {
                            // Keep to the same team: its whole team, or its first hero.
                            val next = if (kind) {
                                teams.firstOrNull { it.team == myTeam }
                            } else {
                                heroes.firstOrNull { teamOf(state, it) == myTeam }
                            }
                            next?.let { onAction(NewGameAction.SetControl(it)) }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (kind) "Whole team" else "One hero", maxLines = 1) }
            }
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "You play" },
    ) {
        (if (whole) teams else heroes).forEach { option ->
            ControlChip(state, option, selected = option == control) { onAction(NewGameAction.SetControl(option)) }
        }
    }
    Text(
        controlNote(state, control),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Choosing to play Gydion: his spell cards one tap away (ADR-040).
    controlled(state, control).map(state::hero).filter { it.spells.isNotEmpty() }.forEach { hero ->
        SpellbookChip(hero, onClick = { onSpellbook(hero.id) })
    }
}

private fun teamOf(state: NewGameUiState.Ready, control: Control): Team = when (control) {
    is Control.WholeTeam -> control.team
    is Control.Hero -> if (control.heroId in state.red) Team.RED else Team.BLUE
}

private fun controlled(state: NewGameUiState.Ready, control: Control): List<String> = when (control) {
    is Control.WholeTeam -> state.form.lineup.team(control.team)
    is Control.Hero -> listOf(control.heroId)
}

/** "The computer plays Brogan, Wasp and Tigerclaw." */
private fun controlNote(state: NewGameUiState.Ready, control: Control): String {
    val yours = controlled(state, control)
    val computer = (state.red + state.blue).filterNot { it in yours }.map { state.hero(it).name }
    return "The computer plays ${PuzzleText.names(computer)}."
}

/** One choice of who you play: the portraits in their team's rings, the mark, the name; the chosen one bronze. */
@Composable
private fun ControlChip(state: NewGameUiState.Ready, control: Control, selected: Boolean, onClick: () -> Unit) {
    val team = teamOf(state, control)
    val color = teamColor(team)
    val ids = controlled(state, control)
    val label = when (control) {
        is Control.WholeTeam -> "All of ${BoardText.team(team)}"
        is Control.Hero -> state.hero(control.heroId).name
    }
    // Chosen is bronze, as every single choice in the app (the selection tokens); the team stays on the portraits'
    // rings and the mark.
    val colors = GoaTheme.colors
    PlateSurface(
        tint = if (selected) colors.selectionFill else Color.Transparent,
        border = if (selected) {
            BorderStroke(2.dp, colors.selection)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f))
        },
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label, ${BoardText.team(team)}" },
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = Spacing.md, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ids.forEach { id ->
                HeroPortrait(
                    id,
                    state.hero(id).portrait,
                    null,
                    Modifier
                        .padding(end = 2.dp)
                        .size(scaledIconSize(36.dp, maxScale = 1.4f))
                        .border(1.5.dp, color, CircleShape),
                    shape = CircleShape,
                    framed = false,
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            TeamMark(team, markSize = 10.dp)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (selected) {
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.selection,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * The computer's strength, 1 to 10 (ADR-039): a slider with a tick per step, the value and its band
 * ("6 · Hard") above it. TalkBack reads "[label], Strength 6 of 10, Hard"; [title] goes before the
 * value (a seat's hero), or nothing under a section heading.
 */
@Composable
private fun StrengthSlider(
    strength: Int,
    label: String,
    title: (@Composable () -> Unit)? = null,
    /** "Gentler" and "Stronger" under the ends: once, under the main slider. */
    ends: Boolean = title == null,
    /** False where the value stands elsewhere (on the section head's line). */
    showValue: Boolean = true,
    onPick: (Int) -> Unit,
) {
    Column {
        if (showValue || title != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Box(Modifier.weight(1f)) { title() }
                    Spacer(Modifier.width(Spacing.sm))
                }
                if (showValue) StrengthValue(strength)
            }
        }
        GoaSlider(
            value = strength.toFloat(),
            onValueChange = { v -> v.roundToInt().let { if (it != strength) onPick(it) } },
            valueRange = AiStrength.MIN.toFloat()..AiStrength.MAX.toFloat(),
            steps = AiStrength.MAX - AiStrength.MIN - 1,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = PlayText.strengthSpoken(strength)
                },
        )
        if (ends) {
            StrengthEnds()
        }
    }
}

/** The strength and its band ("8 · Hard"). */
@Composable
private fun StrengthValue(strength: Int) {
    Text(
        PlayText.strengthValue(strength),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun StrengthEnds() {
    Row(Modifier.fillMaxWidth()) {
        Text(
            "Gentler",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Stronger",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Advanced: a strength per computer seat. */
@Composable
private fun Advanced(state: NewGameUiState.Ready, onAction: (NewGameAction) -> Unit) {
    val open = state.form.advanced
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = open, role = Role.Button) { onAction(NewGameAction.SetAdvanced(it)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Advanced: a strength per seat",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
    }
    if (!open) return
    state.aiSeats.forEach { seat ->
        StrengthSlider(
            seat.strength,
            "${seat.hero.name}'s strength",
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamMark(seat.team, markSize = 9.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(seat.hero.name, style = MaterialTheme.typography.titleSmall)
                }
            },
        ) { onAction(NewGameAction.SetSeatStrength(seat.hero.id, it)) }
    }
}

@Composable
private fun MapChoice(state: NewGameUiState.Ready, onAction: (NewGameAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val map = state.map
    Box {
        PlateSurface(
            onClick = { menu = true },
            enabled = state.maps.size > 1,
            brackets = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics {
                    contentDescription =
                        "Map: ${map?.name ?: "none"}" + if (state.maps.size > 1) ". Change map" else ""
                },
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(map?.name ?: "No map", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if ((map?.lanes ?: 1) > 1) "${map?.lanes} lanes" else "Single lane",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (map?.verified == false) UnverifiedBadge(Modifier.padding(top = 2.dp))
                }
                if (state.maps.size > 1) Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            state.maps.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    leadingIcon = {
                        if (option.id ==
                            map?.id
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = "Chosen")
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        menu = false
                        onAction(NewGameAction.SetMap(option.id))
                    },
                )
            }
        }
    }
}

/** The app flipped the Tie Breaker coin (p.5 step 3): its side, what it decides, and Start. */
@Composable
private fun CoinDialog(coin: Team, starting: Boolean, onStart: () -> Unit, onDismiss: () -> Unit) {
    GoaDialog(
        onDismissRequest = onDismiss,
        icon = { TieBreakerMark(coin, size = 64.dp) },
        title = { Text("${BoardText.team(coin)} shows", textAlign = TextAlign.Center) },
        text = {
            Text(
                "The app flipped the Tie Breaker coin and placed it by the board (p.5). When heroes of both " +
                    "teams tie on initiative, the team it shows acts first, then the coin is flipped (p.7).",
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            GoaButton(onClick = onStart, enabled = !starting, modifier = Modifier.heightIn(min = 48.dp)) {
                if (starting) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Start the game")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
        modifier = Modifier.widthIn(max = 420.dp),
    )
}

private const val LARGE_FONT = 1.3f

/** "3 Waves · 4 Lives": the Wave counters (per Lane) and Life counters (per team) a game starts with (p.5, p.20). */
internal fun startingCounters(lanes: Int, length: GameLength, heroes: Int): String {
    val waves = Counters.waves(lanes, length)
    val life = Counters.life(lanes, length, heroes)
    val wavesText = if (lanes > 1) "$lanes × $waves Waves" else "$waves Waves"
    return "$wavesText · $life Lives"
}

/** What the length's counts are, and where the rulebook sets them. */
private fun lengthNote(state: NewGameUiState.Ready): String {
    val heroes = state.red.size + state.blue.size
    val page = if (state.form.length == GameLength.LONG) "p.23" else "p.5"
    return "With $heroes heroes; Lives are per team ($page)."
}
