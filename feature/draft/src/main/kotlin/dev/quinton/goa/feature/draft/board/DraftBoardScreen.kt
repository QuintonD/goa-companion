package dev.quinton.goa.feature.draft.board

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.EdgeFadeGutter
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.PlateStyle
import dev.quinton.goa.core.designsystem.component.SearchFieldIcon
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.bleedHorizontally
import dev.quinton.goa.core.designsystem.component.horizontalEdgeFade
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.ui.ConcealedTile
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.draft.ui.HeroPeekSheet
import dev.quinton.goa.feature.draft.ui.HeroTile
import dev.quinton.goa.feature.draft.ui.PeekCommit
import dev.quinton.goa.feature.draft.ui.RoleChips
import dev.quinton.goa.feature.draft.ui.SequenceStrip
import dev.quinton.goa.feature.draft.ui.StepClock
import dev.quinton.goa.feature.draft.ui.StepToken
import dev.quinton.goa.feature.draft.ui.TileState
import dev.quinton.goa.feature.draft.ui.actionName
import dev.quinton.goa.feature.draft.ui.crossedOut
import dev.quinton.goa.feature.draft.ui.info
import dev.quinton.goa.feature.draft.ui.label
import dev.quinton.goa.feature.draft.ui.plateEdge
import dev.quinton.goa.feature.draft.ui.plural
import dev.quinton.goa.feature.draft.ui.rememberReducedMotion
import dev.quinton.goa.feature.draft.ui.teamGlyph
import dev.quinton.goa.feature.draft.ui.teamLabel
import dev.quinton.goa.feature.draft.ui.teamName
import dev.quinton.goa.feature.draft.ui.withoutTeamGlyph

interface BoardActions {
    fun tapHero(heroId: String) {}

    /** A dimmed tile (dealt to another player) was tapped. */
    fun tapUnavailable(heroId: String) {}

    fun confirm() {}

    fun cancelConfirm() {}

    /** The peek's Pick or Ban button: the hero for the step on the clock, at once (the sheet was the look). */
    fun choose(heroId: String) {
        tapHero(heroId)
        confirm()
    }

    fun swapOut(heroId: String) {}

    fun reroll() {}

    fun undo() {}

    fun redo() {}

    fun acknowledgePrivacy() {}

    fun selectSeat(seatId: String) {}

    fun toggleHandicap(seatId: String) {}

    fun finish(openMatchSetup: Boolean) {}

    /** Opens the finished line-up on a new board (hero ids per team in pick order). */
    fun tryOnBoard(red: List<String>, blue: List<String>) {}

    fun discard() {}

    fun back() {}

    fun dismissMessage() {}

    fun openHero(heroId: String) {}

    /** The hero's page at its spellbook (Gydion's 22 spell cards). */
    fun openSpellbook(heroId: String) {}

    fun chooseHeroFor(playerId: String?) {}

    fun assignHero(heroId: String) {}

    fun clearHero(playerId: String) {}

    fun draftPlayer(playerId: String) {}

    fun undoPlayerPick() {}

    companion object {
        val None = object : BoardActions {}
    }
}

@Composable
fun DraftBoardDestination(
    viewModel: DraftBoardViewModel,
    onBack: () -> Unit,
    onOpenHero: (String) -> Unit,
    onFinished: (matchId: String, openMatchSetup: Boolean) -> Unit,
    onTryOnBoard: (red: List<String>, blue: List<String>) -> Unit = { _, _ -> },
    onOpenSpellbook: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.boardEvents.collect { event ->
            when (event) {
                is BoardEvent.Finished -> onFinished(event.matchId, event.openMatchSetup)
                BoardEvent.Closed -> onBack()
            }
        }
    }
    val actions = remember(viewModel) {
        object : BoardActions {
            override fun tapHero(heroId: String) = viewModel.tapHero(heroId)
            override fun tapUnavailable(heroId: String) = viewModel.tapUnavailable(heroId)
            override fun confirm() = viewModel.confirm()
            override fun cancelConfirm() = viewModel.cancelConfirm()
            override fun swapOut(heroId: String) = viewModel.swapOut(heroId)
            override fun reroll() = viewModel.reroll()
            override fun undo() = viewModel.undo()
            override fun redo() = viewModel.redo()
            override fun acknowledgePrivacy() = viewModel.acknowledgePrivacy()
            override fun selectSeat(seatId: String) = viewModel.selectSeat(seatId)
            override fun toggleHandicap(seatId: String) = viewModel.toggleHandicap(seatId)
            override fun finish(openMatchSetup: Boolean) = viewModel.finish(openMatchSetup)
            override fun tryOnBoard(red: List<String>, blue: List<String>) = onTryOnBoard(red, blue)
            override fun discard() = viewModel.discard()
            override fun back() = onBack()
            override fun dismissMessage() = viewModel.dismissMessage()
            override fun openHero(heroId: String) = onOpenHero(heroId)
            override fun openSpellbook(heroId: String) = onOpenSpellbook(heroId)
            override fun chooseHeroFor(playerId: String?) = viewModel.chooseHeroFor(playerId)
            override fun assignHero(heroId: String) = viewModel.assignHero(heroId)
            override fun clearHero(playerId: String) = viewModel.clearHero(playerId)
            override fun draftPlayer(playerId: String) = viewModel.draftPlayer(playerId)
            override fun undoPlayerPick() = viewModel.undoPlayerPick()
        }
    }
    DraftBoardScreen(state, actions)
}

@Composable
fun DraftBoardScreen(state: BoardUiState, actions: BoardActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.local.message) {
        state.local.message?.let {
            val redo = state.local.redo != null
            val result = snackbar.showSnackbar(
                message = it,
                actionLabel = if (redo) "Redo" else null,
                duration = if (redo) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) actions.redo()
            actions.dismissMessage()
        }
    }
    var menu by remember { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var peek by rememberSaveable { mutableStateOf<String?>(null) }
    val board = state.board
    val players = state.playerDraft
    val title = state.title
    val canUndo = state.canUndo
    GoaScreen(
        title = title,
        onBack = actions::back,
        actions = {
            if (state.progress != null) {
                IconButton(
                    onClick = if (board != null) actions::undo else actions::undoPlayerPick,
                    enabled = canUndo && !state.privacyGate,
                    // Disabled must read as disabled at arm's length, not as a slightly dimmer icon.
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last step")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Discard draft") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menu = false
                                confirmDiscard = true
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Unit

                board != null && state.privacyGate -> PrivacyGate(board, actions)

                board != null && board.isComplete -> CompletionContent(state, board, actions)

                board != null -> HeroBoard(state, board, actions, onPeek = { peek = it })

                players != null -> PlayerDraftContent(state, players, actions, onPeek = { peek = it })

                else -> EmptyState(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "No draft in progress",
                    modifier = Modifier.align(Alignment.Center),
                    action = { GoaButton(onClick = actions::back) { Text("Set up a draft") } },
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
    peek?.let { id ->
        HeroPeekSheet(
            hero = state.heroes.info(id),
            commit = board?.let {
                peekCommit(
                    state,
                    it,
                    id,
                    onCommit = {
                        peek = null
                        actions.choose(id)
                    },
                    onSwapOut = {
                        peek = null
                        actions.swapOut(id)
                    },
                )
            },
            note = if (board != null) DraftBoardText.notDealt(state, id).takeIf { notDealtTile(board, id) } else null,
            onDismiss = { peek = null },
            onOpenHero = { heroId ->
                peek = null
                actions.cancelConfirm()
                actions.openHero(heroId)
            },
            onOpenSpellbook = { heroId ->
                peek = null
                actions.cancelConfirm()
                actions.openSpellbook(heroId)
            },
        )
    }
    if (confirmDiscard) {
        GoaDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this draft?") },
            text = { Text("Bans and picks so far are thrown away. Nothing is saved to the match log.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    actions.discard()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep drafting") } },
        )
    }
}

/**
 * From the peek, the step's explicit commitment for a hero that can be chosen now: a short "Pick Arien" with
 * whom it is for above it ("◆ Red · for Ada · Pick 1 of 2"). The button commits: the sheet was the look.
 * Nothing while a hidden step waits for its team.
 */
private fun peekCommit(
    state: BoardUiState,
    session: DraftSession,
    heroId: String,
    onCommit: () -> Unit,
    onSwapOut: () -> Unit,
): PeekCommit? {
    if (session.isComplete || state.privacyGate) return null
    val step = session.currentStep ?: return null
    val free = PoolView.of(session).open.any { it.heroId == heroId && it.state == TileState.Available }
    if (!free) return null
    val hero = state.heroes.info(heroId)
    val verb = actionName(step.action)
    // The banner's own words: "◆ Red · Ada: pick 1 of 2".
    val recipient = listOfNotNull(
        teamLabel(step.team),
        DraftBoardText.turnLine(session, state::playerName),
    ).joinToString(" · ")
    return PeekCommit(
        team = step.team,
        label = "$verb ${hero.name}",
        recipient = recipient,
        onCommit = onCommit,
        onSwapOut = onSwapOut.takeIf { session.poolEditable() },
        // The step's clock keeps running while the hero is read: its badge comes along into the sheet.
        remainingSeconds = state.local.remainingSeconds,
    )
}

/** A dimmed tile in a dealt-hands draft: the peek says why it can't be chosen now. */
private fun notDealtTile(session: DraftSession, heroId: String): Boolean {
    val pool = PoolView.of(session)
    return (pool.open + pool.otherHands).any { it.heroId == heroId && it.state == TileState.Inactive }
}

/**
 * True while the pool may still be re-rolled or swapped (before the first real choice). Never
 * for a Player Draft: everyone plays their pre-selected hero [RB p.22].
 */
internal fun DraftSession.poolEditable(): Boolean = canEditPool

/** Search, role filter and "hide taken" for the pool (screen state only). */
private class PoolFilters(
    val query: String,
    val roles: Set<Role>,
    val hideTaken: Boolean,
    val onQuery: (String) -> Unit,
    val onToggleRole: (Role) -> Unit,
    val onHideTaken: (Boolean) -> Unit,
) {
    fun matches(hero: HeroInfo): Boolean =
        (query.isBlank() || hero.name.contains(query.trim(), ignoreCase = true)) &&
            (roles.isEmpty() || hero.roles.any { it in roles })
}

@Composable
private fun HeroBoard(state: BoardUiState, session: DraftSession, actions: BoardActions, onPeek: (String) -> Unit) {
    val tokens = remember(session) {
        session.plan.map { StepToken("${actionName(it.action)} ${teamGlyph(it.team)}", it.action, it.team, it.hidden) }
    }
    val pool = remember(session) { PoolView.of(session) }
    var query by rememberSaveable { mutableStateOf("") }
    var roleKeys by rememberSaveable { mutableStateOf("") }
    var hideTaken by rememberSaveable { mutableStateOf(false) }
    var teamsOpen by rememberSaveable { mutableStateOf(false) }
    val roles = remember(roleKeys) {
        roleKeys.split(',').mapNotNull { key -> Role.entries.firstOrNull { it.name == key } }.toSet()
    }
    val filters = PoolFilters(
        query = query,
        roles = roles,
        hideTaken = hideTaken,
        onQuery = { query = it },
        onToggleRole = { role ->
            roleKeys = (if (role in roles) roles - role else roles + role).joinToString(",") { it.name }
        },
        onHideTaken = { hideTaken = it },
    )
    val poolRoles = remember(session, state.heroes) {
        Role.entries.filter { role -> session.pool.any { role in state.heroes.info(it).roles } }
    }
    val searchable = session.pool.size > PoolView.SEARCH_FROM
    val concealed = remember(session) { session.concealedIds() }
    // The steps by mark only ("4 · ◆ picks 2"): the banner above names the team, and short chips keep the step
    // before, the step in play and the next in view.
    val chrome: @Composable (Dp) -> Unit = { gutter ->
        SequenceStrip(tokens, current = session.currentStepIndex, wrap = false, named = false, gutter = gutter)
    }
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Landscape on a phone: the board's chrome moves to a side column so the pool keeps
        // the full height instead of a sliver under the banner.
        val landscape = maxWidth > maxHeight && maxHeight < LANDSCAPE_MAX_HEIGHT
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(SIDE_WEIGHT)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    StepBanner(session, state.local.remainingSeconds, state::playerName, compact = true)
                    chrome(Spacing.sm)
                    if (session.poolEditable()) PoolEditBanner(session, actions)
                    TeamsPanel(state, session, concealed, stacked = true)
                }
                PoolGrid(
                    state,
                    session,
                    pool,
                    filters,
                    poolRoles,
                    onPeek,
                    Modifier.weight(1f - SIDE_WEIGHT),
                    topGap = Spacing.screen - Spacing.xs,
                    filtersInGrid = true,
                ) {}
            }
        } else {
            // Large text or a short phone: only the banner stays put; the rest scrolls with the pool so the
            // pool still shows.
            val large = LocalDensity.current.fontScale > LARGE_FONT_SCALE || maxHeight < STICKY_MIN_HEIGHT
            val detailsCap = maxHeight * DETAILS_MAX_FRACTION
            val toggleTeams = { teamsOpen = !teamsOpen }
            Column(Modifier.fillMaxSize()) {
                // Sticky, top to bottom: whose turn it is, the run of steps, both teams at a glance, the pool's
                // search and filters. The pool comes straight after; the full rosters open on demand.
                Column(
                    Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    StepBanner(
                        session,
                        state.local.remainingSeconds,
                        state::playerName,
                        compact = LocalDensity.current.fontScale > LARGE_FONT_SCALE,
                    )
                    if (!large) {
                        chrome(Spacing.screen)
                        TeamsBar(state, session, concealed, teamsOpen, toggleTeams, scrollCap = detailsCap)
                        if (searchable) PoolSearch(filters, poolRoles)
                    }
                }
                PoolGrid(
                    state,
                    session,
                    pool,
                    filters,
                    poolRoles,
                    onPeek,
                    Modifier.weight(1f),
                    topGap = Spacing.xs,
                    filtersInGrid = large || !searchable,
                ) {
                    if (large) {
                        item(span = full) { chrome(Spacing.screen) }
                        item(span = full) { TeamsBar(state, session, concealed, teamsOpen, toggleTeams, null) }
                    }
                    if (session.poolEditable()) {
                        item(span = full) { PoolEditBanner(session, actions) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoolGrid(
    state: BoardUiState,
    session: DraftSession,
    pool: PoolView,
    filters: PoolFilters,
    roles: List<Role>,
    onPeek: (String) -> Unit,
    modifier: Modifier,
    topGap: Dp,
    filtersInGrid: Boolean,
    leading: LazyGridScope.() -> Unit,
) {
    // Four across on a phone (three at 360 dp), names large enough to read at arm's length;
    // they grow with the font scale so names stay legible.
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.7f)
    val open = pool.open.filter { filters.matches(state.heroes.info(it.heroId)) }
    val others = pool.otherHands.filter { filters.matches(state.heroes.info(it.heroId)) }
    val taken = if (filters.hideTaken) {
        emptyList()
    } else {
        pool.taken.filter { filters.matches(state.heroes.info(it.heroId)) }
    }
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TILE_MIN_WIDTH * fontScale),
        // No top content padding: items scrolling up would show through it, as a strip under
        // the sticky banner. The gap above the first item is a spacer that scrolls away instead.
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier.fillMaxHeight(),
    ) {
        item(span = full) { Spacer(Modifier.height(topGap)) }
        leading()
        item(span = full) {
            PoolHeader(
                left = pool.left,
                total = session.pool.size,
                filters = filters.takeIf { filtersInGrid },
                searchable = session.pool.size > PoolView.SEARCH_FROM,
                roles = roles,
            )
        }
        val owner = pool.handOwner
        if (owner != null) {
            item(span = full) { SubHeading("${state.playerName(owner)}'s heroes") }
        }
        poolTiles(state, open, onPeek)
        if (others.isNotEmpty()) {
            item(span = full) { SubHeading("Dealt to other players") }
            poolTiles(state, others, onPeek)
        }
        if (open.isEmpty() && others.isEmpty()) {
            item(span = full) {
                Text(
                    "No heroes match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
        }
        if (taken.isNotEmpty()) {
            item(span = full) { TakenStrip(state, taken, onPeek) }
        }
    }
}

private fun LazyGridScope.poolTiles(
    state: BoardUiState,
    tiles: List<PoolTile>,
    onPeek: (String) -> Unit,
) {
    items(tiles, key = { it.heroId }) { tile ->
        val hero = state.heroes.info(tile.heroId)
        // One tap, one meaning: the tile opens the hero's sheet, and the step's Pick or Ban is the button there.
        // No inspect button on the art, so looking can never start a pick.
        HeroTile(
            hero = hero,
            state = tile.state,
            compact = true,
            onClick = { onPeek(tile.heroId) },
            clickLabel = when (tile.state) {
                TileState.Available -> "Choose ${hero.name}"
                TileState.Inactive -> "Why ${hero.name} can't be chosen"
                else -> "Show ${hero.name}"
            },
        )
    }
}

/**
 * The pool's heading, "Pool · 9 of 12 left". With [filters] (landscape, large text, a pool too small to search)
 * the filters sit with it and scroll; otherwise they are pinned above the pool ([PoolSearch]).
 */
@Composable
private fun PoolHeader(left: Int, total: Int, filters: PoolFilters?, searchable: Boolean, roles: List<Role>) {
    Column(Modifier.padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
            Text(
                "Pool · $left of $total left",
                style = GoaTextStyles.sectionHead,
                color = GoaTheme.colors.heading,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (filters != null) HideTakenChip(filters)
        }
        if (filters != null && searchable) {
            SearchField(filters, Modifier.fillMaxWidth())
            RoleFilters(filters, roles)
        }
    }
}

/** Pinned above the pool: search with Hide taken beside it, then the role filters. */
@Composable
private fun PoolSearch(filters: PoolFilters, roles: List<Role>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(filters, Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.sm))
            HideTakenChip(filters)
        }
        RoleFilters(filters, roles)
    }
}

@Composable
private fun HideTakenChip(filters: PoolFilters) {
    GoaFilterChip(
        selected = filters.hideTaken,
        onClick = { filters.onHideTaken(!filters.hideTaken) },
        label = "Hide taken",
    )
}

@Composable
private fun SearchField(filters: PoolFilters, modifier: Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    // A dark steel slot in both themes, as the Heroes and Rules searches are.
    GoaPlateTheme {
        OutlinedTextField(
            value = filters.query,
            onValueChange = filters.onQuery,
            placeholder = { Text("Search heroes", maxLines = 1) },
            leadingIcon = { SearchFieldIcon() },
            trailingIcon = if (filters.query.isNotEmpty()) {
                {
                    IconButton(onClick = { filters.onQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GoaTheme.colors.plate,
                unfocusedContainerColor = GoaTheme.colors.plate,
                unfocusedBorderColor = SEARCH_EDGE,
            ),
            modifier = modifier,
        )
    }
}

@Composable
private fun RoleFilters(filters: PoolFilters, roles: List<Role>) {
    val roleRow = rememberLazyListState()
    // Ten roles scroll: the rail runs to the screen's edges, its first chip on the gutter, a chevron at the edge.
    LazyRow(
        Modifier.bleedHorizontally(Spacing.screen).horizontalEdgeFade(roleRow),
        state = roleRow,
        contentPadding = EdgeFadeGutter,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(roles, key = { it.name }) { role ->
            GoaFilterChip(
                selected = role in filters.roles,
                onClick = { filters.onToggleRole(role) },
                label = role.label,
            )
        }
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text,
        style = GoaTextStyles.sectionHead,
        color = GoaTheme.colors.heading,
        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
    )
}

/** Banned and picked heroes as small chips: they stay visible but leave the grid to open heroes. */
@Composable
private fun TakenStrip(state: BoardUiState, taken: List<PoolTile>, onPeek: (String) -> Unit) {
    Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SubHeading("Taken")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            taken.forEach { tile ->
                TakenChip(state.heroes.info(tile.heroId), tile.state, onClick = { onPeek(tile.heroId) })
            }
        }
    }
}

@Composable
private fun TakenChip(hero: HeroInfo, state: TileState, onClick: () -> Unit) {
    val team = when (state) {
        is TileState.Banned -> state.team
        is TileState.Picked -> state.team
        else -> return
    }
    val banned = state is TileState.Banned
    GoaPlateTheme {
        val color = teamColor(team)
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            color = GoaTheme.colors.plate,
            border = BorderStroke(1.dp, color.copy(alpha = if (banned) 0.5f else 0.9f)),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = "${hero.name}, ${if (banned) "banned" else "picked"} by ${teamName(team)}"
                },
        ) {
            Row(
                Modifier.padding(start = Spacing.xs, end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroPortrait(
                    hero.id,
                    hero.portrait,
                    null,
                    Modifier
                        .size(32.dp)
                        .then(if (banned) Modifier.crossedOut() else Modifier)
                        .alpha(if (banned) 0.45f else 1f),
                    framed = false,
                )
                Spacer(Modifier.width(Spacing.xs))
                TeamMark(team, markSize = 10.dp)
                Spacer(Modifier.width(2.dp))
                if (banned) {
                    Icon(Icons.Filled.Block, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    hero.name,
                    style = MaterialTheme.typography.labelLarge,
                    textDecoration = if (banned) TextDecoration.LineThrough else null,
                    color = with(MaterialTheme.colorScheme) { if (banned) onSurfaceVariant else onSurface },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StepBanner(
    session: DraftSession,
    remaining: Int?,
    playerName: (String?) -> String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val step = session.currentStep ?: return
    val reduced = rememberReducedMotion()
    // A slate plate edged in the team's colour, the step on its title plate, the team's mark beside it.
    GoaPlate(
        modifier
            .fillMaxWidth()
            .plateEdge(teamColor(step.team))
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = if (compact) Spacing.xs else Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamMark(step.team, markSize = if (compact) 20.dp else 26.dp)
                Spacer(Modifier.width(Spacing.sm))
                AnimatedContent(
                    targetState = DraftBoardText.bannerTitle(session).orEmpty().withoutTeamGlyph(),
                    transitionSpec = {
                        val d = if (reduced) 0 else 250
                        (fadeIn(tween(d)) + slideInVertically(tween(d)) { it / 3 }) togetherWith fadeOut(tween(d))
                    },
                    label = "banner",
                    modifier = Modifier.weight(1f),
                ) { text ->
                    TitlePlate(
                        text,
                        style = if (compact) GoaTextStyles.plateTitle else STEP_STYLE,
                        heading = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // The step's clock beside its title: one row for "who, what, how long".
                if (remaining != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    StepClock(remaining)
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            // Whose turn inside the step, on its own line ("Ada: pick 1 of 2"); the step's number is on its chip.
            Text(
                DraftBoardText.turnLine(session, playerName).orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PoolEditBanner(session: DraftSession, actions: BoardActions) {
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Pool of ${session.pool.size} dealt",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            GoaOutlinedButton(onClick = actions::reroll, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Re-roll")
            }
        }
    }
}

/**
 * Both teams on the sticky part of the board. Closed: one line per team at a glance (seats as small portraits,
 * bans counted), so the pool sits right under it. Open: the full rosters with roles and bans, capped at
 * [scrollCap] (scrolling inside) so the pool never disappears; null where the bar scrolls with the pool.
 */
@Composable
private fun TeamsBar(
    state: BoardUiState,
    session: DraftSession,
    concealed: Set<String>,
    open: Boolean,
    onToggle: () -> Unit,
    scrollCap: Dp?,
) {
    if (!open) {
        TeamsSummary(state, session, concealed, onToggle)
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val capped = if (scrollCap != null) {
            Modifier.heightIn(max = scrollCap).verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Box(capped) { TeamsPanel(state, session, concealed) }
        TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
            Spacer(Modifier.width(Spacing.xs))
            Text("Hide teams")
        }
    }
}

/** The closed team bar: per team its mark and name, a portrait (or an empty ring) per seat, its bans counted. */
@Composable
private fun TeamsSummary(state: BoardUiState, session: DraftSession, concealed: Set<String>, onExpand: () -> Unit) {
    val teams = listOf(session.teamA, session.teamB)
    val assignments = DraftEngine.assignments(session)
    val spoken = teams.joinToString(". ") { team ->
        val seats = assignments.filter { it.seat.team == team }
        val heroes = seats.mapNotNull { it.heroId }
            .map { if (it in concealed) "a hidden pick" else state.heroes.info(it).name }
        val openSeats = seats.count { it.heroId == null }
        val bans = session.bans.count { it.team == team }
        listOfNotNull(
            teamName(team),
            heroes.joinToString(", ").ifEmpty { null },
            plural(openSeats, "open seat").takeIf { openSeats > 0 },
            plural(bans, "ban").takeIf { bans > 0 },
        ).joinToString(", ")
    }
    GoaPlate(
        Modifier
            .fillMaxWidth()
            .clip(PlateShape)
            .clickable(onClickLabel = "Show teams", role = androidx.compose.ui.semantics.Role.Button) { onExpand() }
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        brackets = false,
        style = PlateStyle.Secondary,
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            teams.forEachIndexed { i, team ->
                if (i > 0) {
                    Box(
                        Modifier
                            .padding(horizontal = Spacing.sm)
                            .width(1.dp)
                            .height(40.dp)
                            .background(GoaTheme.colors.engravedBorder),
                    )
                }
                TeamGlance(
                    state,
                    team,
                    assignments.filter { it.seat.team == team }.map { it.heroId },
                    bans = session.bans.count { it.team == team },
                    concealed = concealed,
                    modifier = Modifier.weight(1f),
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.padding(start = Spacing.xs).size(24.dp),
            )
        }
    }
}

@Composable
private fun TeamGlance(
    state: BoardUiState,
    team: Team,
    seats: List<String?>,
    bans: Int,
    concealed: Set<String>,
    modifier: Modifier,
) {
    val color = teamColor(team)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamMark(team, markSize = 14.dp)
            Spacer(Modifier.width(Spacing.xs))
            Text(
                teamName(team),
                style = MaterialTheme.typography.labelLarge,
                color = color,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (bans > 0) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(2.dp))
                Text(
                    plural(bans, "ban"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            seats.forEach { heroId ->
                val seat = Modifier.weight(1f, fill = false).widthIn(max = GLANCE_ART).aspectRatio(1f)
                when {
                    heroId == null -> Box(seat.border(1.dp, color.copy(alpha = 0.5f), MaterialTheme.shapes.small))
                    heroId in concealed -> ConcealedTile(team, seat)
                    else -> HeroPortrait(heroId, state.heroes.info(heroId).portrait, null, seat)
                }
            }
        }
    }
}

@Composable
private fun TeamsPanel(state: BoardUiState, session: DraftSession, concealed: Set<String>, stacked: Boolean = false) {
    // Side by side normally (the two plates the same height); stacked at large font scales and in the landscape
    // side column.
    if (stacked || LocalDensity.current.fontScale > LARGE_FONT) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (team in listOf(session.teamA, session.teamB)) {
                TeamColumn(state, session, team, concealed, Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (team in listOf(session.teamA, session.teamB)) {
                TeamColumn(state, session, team, concealed, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun TeamColumn(
    state: BoardUiState,
    session: DraftSession,
    team: Team,
    concealed: Set<String>,
    modifier: Modifier,
) {
    val onClock = session.currentStep?.team
    val active = onClock == team
    // Only the team on the clock may peek at its own concealed choices: the pass-the-phone gate
    // has just been acknowledged by that team, so this reveals nothing to the other team.
    val canPeek = active && session.currentStep?.hidden == true
    val picks = DraftEngine.assignments(session).filter { it.seat.team == team }
    val bans = session.bans.filter { it.team == team }
    // Role coverage from revealed picks only, so a hidden pick never shows through.
    val covered = picks.mapNotNull { it.heroId?.takeIf { id -> id !in concealed } }
        .flatMap { state.heroes.info(it).roles }
        .toSet()
    GoaPlate(
        modifier.plateEdge(if (active) teamColor(team) else null),
        contentPadding = PaddingValues(0.dp),
    ) {
        val color = teamColor(team)
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamMark(team, markSize = 14.dp)
                Spacer(Modifier.width(Spacing.xs))
                // The team by its colour and mark only: "Team A" is the method editor's word, not the table's.
                Text(
                    teamName(team),
                    style = GoaTextStyles.sectionHead,
                    color = color,
                    modifier = Modifier.weight(1f),
                )
            }
            picks.forEach { (seat, heroId) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 36.dp)) {
                    val hidden = heroId != null && heroId in concealed
                    when {
                        hidden -> ConcealedTile(team, Modifier.size(SEAT_ART))

                        heroId != null -> HeroPortrait(
                            heroId,
                            state.heroes.info(heroId).portrait,
                            null,
                            Modifier.size(SEAT_ART),
                        )

                        else -> Box(
                            Modifier
                                .size(SEAT_ART)
                                .border(1.dp, color.copy(alpha = 0.4f), MaterialTheme.shapes.medium),
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        if (hidden) {
                            HiddenChoice("Hidden pick", state.heroes.info(heroId.orEmpty()).name, canPeek)
                        } else {
                            Text(
                                heroId?.let { state.heroes.info(it).name } ?: "Open seat",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (heroId == null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        PilotLine(team, state.playerName(seat.pilotId) + if (seat.handicap) " · H" else "")
                    }
                }
            }
            if (covered.isNotEmpty()) {
                // Labelled: the tags are the roles this team's revealed picks cover, not a filter.
                Text(
                    "Roles covered",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RoleChips(Role.entries.filter { it in covered }, highlight = color)
            }
            if (bans.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    bans.forEach { ban ->
                        BanChip(
                            team = team,
                            name = state.heroes.info(ban.heroId).name,
                            hidden = ban.heroId in concealed,
                            canPeek = canPeek,
                        )
                    }
                }
            }
        }
    }
}

/** The pilot's name with the team's shape, so a player colour is never read as a team colour. */
@Composable
internal fun PilotLine(team: Team, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamMark(team, markSize = 8.dp)
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A ban as its own small chip, so several bans never run together. */
@Composable
private fun BanChip(team: Team, name: String, hidden: Boolean, canPeek: Boolean) {
    val color = teamColor(team)
    Row(
        Modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Block, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(Spacing.xs))
        if (hidden) {
            HiddenChoice("Hidden ban", name, canPeek, small = true)
        } else {
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = TextDecoration.LineThrough,
                modifier = Modifier.semantics { contentDescription = "Banned: $name" },
            )
        }
    }
}

/**
 * A concealed choice. When [canPeek] (the team on the clock looking at its own hidden choice),
 * press and hold shows the hero until released.
 */
@Composable
private fun HiddenChoice(label: String, secret: String, canPeek: Boolean, small: Boolean = false) {
    var revealed by remember { mutableStateOf(false) }
    val style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    Text(
        text = when {
            revealed -> secret
            canPeek -> "$label · hold to see"
            else -> label
        },
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (revealed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (canPeek) {
            Modifier
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        revealed = true
                        tryAwaitRelease()
                        revealed = false
                    })
                }
                .semantics { contentDescription = "$label. Your team's choice: press and hold to see it." }
        } else {
            Modifier
        },
    )
}

@Composable
private fun PrivacyGate(session: DraftSession, actions: BoardActions) {
    val step = session.currentStep ?: return
    val color = teamColor(step.team)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(color.copy(alpha = 0.16f))
                .border(2.dp, color, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = color, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        TeamMark(step.team, markSize = 26.dp)
        Spacer(Modifier.height(Spacing.md))
        TitlePlate(
            "Pass the phone to ${teamName(step.team)}",
            Modifier.fillMaxWidth(),
            style = STEP_STYLE,
            maxLines = 3,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            DraftBoardText.privacyText(session).orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = actions::acknowledgePrivacy,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = MaterialTheme.colorScheme.background,
            ),
            modifier = Modifier.heightIn(min = 56.dp),
        ) { Text("I'm ${teamName(step.team)} — show the board") }
    }
}

private val STEP_STYLE = GoaTextStyles.plateTitle.copy(fontSize = 19.sp, lineHeight = 24.sp)
private val SEARCH_EDGE = Color(0xFF5D6B70)

private const val LARGE_FONT = 1.5f
private const val DISABLED_ALPHA = 0.3f

private const val SIDE_WEIGHT = 0.42f
private val LANDSCAPE_MAX_HEIGHT = 520.dp
private val TILE_MIN_WIDTH = 84.dp
private val SEAT_ART = 32.dp
private val GLANCE_ART = 26.dp

/** Below this height the board pins only its step banner. */
private val STICKY_MIN_HEIGHT = 640.dp

/** The open team rosters take at most this share of the board's height; they scroll inside beyond it. */
private const val DETAILS_MAX_FRACTION = 0.45f
