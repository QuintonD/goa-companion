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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SearchField
import dev.quinton.goa.feature.board.list.LineupHeroes
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.SpellbookChip
import dev.quinton.goa.feature.board.play.table.SpellbookSheet
import dev.quinton.goa.feature.board.play.table.TieBreakerMark
import dev.quinton.goa.feature.board.play.table.catalogSpellbook
import dev.quinton.goa.feature.board.setup.HeroGrid
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.SourcesContent
import dev.quinton.goa.feature.board.setup.TeamSwitch
import dev.quinton.goa.feature.board.setup.Teams
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
                    TextButton(onClick = { onAction(NewGameAction.Lineup(NewBoardAction.Clear)) }) { Text("Clear") }
                }
                IconButton(onClick = { sources = true }, enabled = ready.sources.isNotEmpty()) {
                    Icon(Icons.Outlined.History, contentDescription = "Use a draft or match")
                }
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
            "${BoardText.count(state.playable, "hero")} can play so far; $later more join as their cards are tested.",
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
            Button(
                onClick = { onAction(NewGameAction.Step(NewGameStep.OPTIONS)) },
                enabled = state.canContinue,
                modifier = Modifier.heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = Spacing.xl),
            ) { Text("Next", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** 2v2 is the rulebook's standard; 1v1 is allowed but not its way (p.20). */
internal fun lineupNote(red: Int, blue: Int): String = when {
    red == 0 && blue == 0 -> "Pick heroes for both teams. Two each is the rulebook's standard."
    red != blue -> "Both teams need the same number of heroes."
    red == 1 -> "Allowed, but not the rulebook's way: with two players, each controls two heroes (p.20)."
    red == 2 -> "The rulebook's standard game."
    else -> "${red * 2} heroes on the board."
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
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Surface(
                onClick = { onAction(NewGameAction.Step(NewGameStep.LINEUP)) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Line-up. Change" },
            ) {
                Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
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

            Section("You play") {
                state.controls.forEach { control ->
                    ControlRow(
                        state,
                        control,
                        control == state.control,
                        onSpellbook,
                    ) { onAction(NewGameAction.SetControl(control)) }
                }
            }

            Section("Computer strength") {
                StrengthSlider(state.form.strength, "Computer strength") { onAction(NewGameAction.SetStrength(it)) }
                Text(
                    PlayText.strengthNote(state.form.strength),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.aiSeats.size > 1) Advanced(state, onAction)
            }

            Section("Length") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    GameLength.entries.forEachIndexed { i, length ->
                        SegmentedButton(
                            selected = state.form.length == length,
                            onClick = { onAction(NewGameAction.SetLength(length)) },
                            shape = SegmentedButtonDefaults.itemShape(i, GameLength.entries.size),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(PlayText.length(length)) }
                    }
                }
                Text(
                    PlayText.lengthNote(state.form.length),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Map") { MapChoice(state, onAction) }

            Section("Coach") {
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
                        Text(if (state.form.coach) "On" else "Off", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "A gentle nudge before a clearly worse choice, and the turning points after the game. " +
                                "It never chooses for you; switch it in the game too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Switch(checked = state.form.coach, onCheckedChange = null)
                }
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
                Button(
                    onClick = { onAction(NewGameAction.FlipCoin) },
                    enabled = state.canStart && !starting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Flip the Tie Breaker coin", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            title.uppercase(),
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun ControlRow(
    state: NewGameUiState.Ready,
    control: Control,
    selected: Boolean,
    onSpellbook: (String) -> Unit,
    onClick: () -> Unit,
) {
    val (title, note, heroes) = when (control) {
        is Control.WholeTeam -> {
            val ids = state.form.lineup.team(control.team)
            Triple(
                "All of ${BoardText.team(control.team)}",
                ids.joinToString(", ") { state.hero(it).name } + ". The other team is the computer.",
                ids,
            )
        }

        is Control.Hero -> {
            val team = if (control.heroId in state.red) Team.RED else Team.BLUE
            val mates = state.form.lineup.team(team) - control.heroId
            Triple(
                state.hero(control.heroId).name,
                BoardText.team(team) +
                    if (mates.isEmpty()) {
                        ""
                    } else {
                        ". Your teammate${if (mates.size > 1) "s" else ""} " +
                            mates.joinToString(", ") { state.hero(it).name } +
                            " play${if (mates.size > 1) "" else "s"} as the computer."
                    },
                listOf(control.heroId),
            )
        }
    }
    val team = when (control) {
        is Control.WholeTeam -> control.team
        is Control.Hero -> if (control.heroId in state.red) Team.RED else Team.BLUE
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) teamColor(team).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) teamColor(team) else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(Spacing.xs))
            heroes.forEach { id ->
                HeroPortrait(
                    id,
                    state.hero(id).portrait,
                    null,
                    Modifier.padding(end = 2.dp).size(32.dp).border(1.5.dp, teamColor(team), CircleShape),
                    shape = CircleShape,
                    framed = false,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamMark(team, markSize = 9.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Choosing to play Gydion: his spell cards one tap away (ADR-040).
                heroes.map(state::hero).filter { it.spells.isNotEmpty() }.forEach { hero ->
                    SpellbookChip(hero, onClick = { onSpellbook(hero.id) }, Modifier.padding(top = Spacing.xs))
                }
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
    onPick: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (title != null) {
                Box(Modifier.weight(1f)) { title() }
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                PlayText.strengthValue(strength),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
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
        Surface(
            onClick = { menu = true },
            enabled = state.maps.size > 1,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
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
    AlertDialog(
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
            Button(onClick = onStart, enabled = !starting, modifier = Modifier.heightIn(min = 48.dp)) {
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.widthIn(max = 420.dp),
    )
}

private const val LARGE_FONT = 1.3f
