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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.draft.BoardUiState
import dev.quinton.goa.core.presentation.draft.DraftBoard
import dev.quinton.goa.core.presentation.draft.HeroInfo
import dev.quinton.goa.core.presentation.draft.PoolTile
import dev.quinton.goa.core.presentation.draft.PoolView
import dev.quinton.goa.core.presentation.draft.StepToken
import dev.quinton.goa.core.presentation.draft.TileState
import dev.quinton.goa.core.presentation.draft.actionName
import dev.quinton.goa.core.presentation.draft.info
import dev.quinton.goa.core.presentation.draft.label
import dev.quinton.goa.core.presentation.draft.teamGlyph
import dev.quinton.goa.core.presentation.draft.teamLabel
import dev.quinton.goa.core.presentation.draft.teamName
import dev.quinton.goa.feature.draft.ui.ConcealedTile
import dev.quinton.goa.feature.draft.ui.HeroPeekSheet
import dev.quinton.goa.feature.draft.ui.HeroTile
import dev.quinton.goa.feature.draft.ui.RoleChips
import dev.quinton.goa.feature.draft.ui.SequenceStrip
import dev.quinton.goa.feature.draft.ui.rememberReducedMotion
import kotlin.math.abs

interface BoardActions {
    fun tapHero(heroId: String) {}

    /** A dimmed tile (dealt to another player) was tapped. */
    fun tapUnavailable(heroId: String) {}

    fun confirm() {}

    fun cancelConfirm() {}

    fun swapOut(heroId: String) {}

    fun reroll() {}

    fun undo() {}

    fun redo() {}

    fun acknowledgePrivacy() {}

    fun selectSeat(seatId: String) {}

    fun toggleHandicap(seatId: String) {}

    fun finish(openMatchSetup: Boolean) {}

    fun discard() {}

    fun back() {}

    fun dismissMessage() {}

    fun openHero(heroId: String) {}

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
            override fun discard() = viewModel.discard()
            override fun back() = onBack()
            override fun dismissMessage() = viewModel.dismissMessage()
            override fun openHero(heroId: String) = onOpenHero(heroId)
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
    val title = board?.method?.name ?: players?.let { "Player Draft" } ?: "Draft"
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
                    message = "Set up players, teams and a method to start one.",
                    modifier = Modifier.align(Alignment.Center),
                    action = { Button(onClick = actions::back) { Text("Set up a draft") } },
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
    val confirming = state.local.confirm
    if (board != null && !board.isComplete && confirming != null) {
        ConfirmDialog(state, board, confirming, actions, onPeek = { peek = it })
    }
    peek?.let { id ->
        HeroPeekSheet(
            hero = state.heroes.info(id),
            onDismiss = { peek = null },
            onOpenHero = { heroId ->
                peek = null
                actions.cancelConfirm()
                actions.openHero(heroId)
            },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
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
    val tokens = remember(session) { DraftBoard.sequenceTokens(session) }
    val pool = remember(session) { PoolView.of(session) }
    var query by rememberSaveable { mutableStateOf("") }
    var roleKeys by rememberSaveable { mutableStateOf("") }
    var hideTaken by rememberSaveable { mutableStateOf(false) }
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
    val concealed = remember(session) { session.concealedIds() }
    val chrome: @Composable () -> Unit = {
        SequenceStrip(tokens, current = session.currentStepIndex, wrap = false)
    }
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
                    chrome()
                    if (session.poolEditable()) PoolEditBanner(session, actions)
                    TeamsPanel(state, session, concealed, stacked = true)
                }
                PoolGrid(
                    state,
                    session,
                    pool,
                    filters,
                    actions,
                    onPeek,
                    Modifier.weight(1f - SIDE_WEIGHT),
                    topGap = Spacing.screen - Spacing.xs,
                ) {}
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // Sticky: whose turn it is stays on screen however far the pool is scrolled.
                StepBanner(
                    session,
                    state.local.remainingSeconds,
                    state::playerName,
                    // Large text: a smaller headline so the sticky banner leaves room for the pool.
                    compact = LocalDensity.current.fontScale > LARGE_FONT_SCALE,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
                PoolGrid(state, session, pool, filters, actions, onPeek, Modifier.weight(1f), topGap = Spacing.xs) {
                    item(span = { GridItemSpan(maxLineSpan) }) { chrome() }
                    if (session.poolEditable()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { PoolEditBanner(session, actions) }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { TeamsPanel(state, session, concealed) }
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
    actions: BoardActions,
    onPeek: (String) -> Unit,
    modifier: Modifier,
    topGap: Dp,
    leading: LazyGridScope.() -> Unit,
) {
    // Dense tiles (about five across on a phone) so a full roster fits in about two screens;
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
                filters = filters,
                searchable = session.pool.size > SEARCH_FROM,
                roles = Role.entries.filter { role -> session.pool.any { role in state.heroes.info(it).roles } },
            )
        }
        val owner = pool.handOwner
        if (owner != null) {
            item(span = full) { SubHeading("${state.playerName(owner)}'s heroes") }
        }
        poolTiles(state, open, actions, onPeek)
        if (others.isNotEmpty()) {
            item(span = full) { SubHeading("Dealt to other players") }
            poolTiles(state, others, actions, onPeek)
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
    actions: BoardActions,
    onPeek: (String) -> Unit,
) {
    items(tiles, key = { it.heroId }) { tile ->
        HeroTile(
            hero = state.heroes.info(tile.heroId),
            state = tile.state,
            compact = true,
            onClick = when (tile.state) {
                TileState.Available -> ({ actions.tapHero(tile.heroId) })
                TileState.Inactive -> ({ actions.tapUnavailable(tile.heroId) })
                else -> null
            },
            onLongClick = { onPeek(tile.heroId) },
        )
    }
}

@Composable
private fun PoolHeader(left: Int, total: Int, filters: PoolFilters, searchable: Boolean, roles: List<Role>) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Pool · $left of $total left",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            GoaFilterChip(
                selected = filters.hideTaken,
                onClick = { filters.onHideTaken(!filters.hideTaken) },
                label = "Hide taken",
            )
        }
        if (searchable) {
            OutlinedTextField(
                value = filters.query,
                onValueChange = filters.onQuery,
                placeholder = { Text("Search heroes") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(roles, key = { it.name }) { role ->
                    GoaFilterChip(
                        selected = role in filters.roles,
                        onClick = { filters.onToggleRole(role) },
                        label = role.label,
                    )
                }
            }
        }
        Text(
            "Long-press a hero for roles, stats and starting cards.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
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
    val color = teamColor(team)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
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
                Modifier.size(32.dp).alpha(if (banned) 0.45f else 1f),
            )
            Spacer(Modifier.width(Spacing.xs))
            if (banned) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
            } else {
                Text(teamGlyph(team), color = color, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(2.dp))
            }
            Text(
                hero.name,
                style = MaterialTheme.typography.labelLarge,
                textDecoration = if (banned) TextDecoration.LineThrough else null,
                color = if (banned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
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
    val text = DraftBoard.stepBanner(session, playerName) ?: return
    val color = teamColor(step.team)
    val reduced = rememberReducedMotion()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(2.dp, color),
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = if (compact) Spacing.sm else Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = text.title,
                    transitionSpec = {
                        val d = if (reduced) 0 else 250
                        (fadeIn(tween(d)) + slideInVertically(tween(d)) { it / 3 }) togetherWith fadeOut(tween(d))
                    },
                    label = "banner",
                ) { text ->
                    val typography = MaterialTheme.typography
                    Text(
                        text,
                        style = if (compact) typography.titleLarge else typography.headlineSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (remaining != null) {
                Spacer(Modifier.width(Spacing.sm))
                StepClock(remaining)
            }
        }
    }
}

@Composable
private fun StepClock(remaining: Int) {
    val over = remaining < 0
    val text = (if (over) "+" else "") + "%d:%02d".format(abs(remaining) / 60, abs(remaining) % 60)
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics {
                contentDescription =
                    if (over) "Overtime ${abs(remaining)} seconds" else "$remaining seconds left"
            },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun PoolEditBanner(session: DraftSession, actions: BoardActions) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pool of ${session.pool.size} dealt", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Before the first step you can re-roll it, or tap a hero to swap it out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = actions::reroll, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Re-roll")
            }
        }
    }
}

@Composable
private fun TeamsPanel(state: BoardUiState, session: DraftSession, concealed: Set<String>, stacked: Boolean = false) {
    // Side by side normally; stacked at large font scales and in the landscape side column.
    if (stacked || LocalDensity.current.fontScale > LARGE_FONT) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (team in listOf(session.teamA, session.teamB)) {
                TeamColumn(state, session, team, concealed, Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (team in listOf(session.teamA, session.teamB)) {
                TeamColumn(state, session, team, concealed, Modifier.weight(1f))
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
    val color = teamColor(team)
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
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) color else GoaTheme.colors.engravedBorder),
    ) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    teamLabel(team),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (team == session.teamA) "A" else "B",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = if (team == session.teamA) "Team A" else "Team B"
                    },
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
            if (covered.isNotEmpty()) RoleChips(Role.entries.filter { it in covered }, highlight = color)
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
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Block, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(Spacing.xs))
            if (hidden) {
                HiddenChoice("Hidden ban", name, canPeek, small = true)
            } else {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { contentDescription = "Banned: $name" },
                )
            }
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
private fun ConfirmDialog(
    state: BoardUiState,
    session: DraftSession,
    heroId: String,
    actions: BoardActions,
    onPeek: (String) -> Unit,
) {
    val step = session.currentStep ?: return
    val hero = state.heroes.info(heroId)
    val color = teamColor(step.team)
    val ban = step.action == DraftAction.BAN
    AlertDialog(
        onDismissRequest = actions::cancelConfirm,
        icon = {
            HeroPortrait(hero.id, hero.portrait, null, Modifier.size(width = 96.dp, height = 120.dp))
        },
        title = {
            Text(
                if (ban) "Ban ${hero.name}?" else "Pick ${hero.name}?",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (hero.title.isNotEmpty()) Text(hero.title, style = MaterialTheme.typography.bodyMedium)
                ComplexityStars(hero.complexity, showLabel = true)
                RoleChips(hero.roles)
                Text(
                    DraftBoard.confirmText(session, state::playerName).orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { onPeek(heroId) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Stats and starting cards")
                }
                if (session.poolEditable()) {
                    TextButton(onClick = { actions.swapOut(heroId) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Swap out of the pool instead")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = actions::confirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = color,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("${teamGlyph(step.team)} ${if (ban) "Ban" else "Pick"}") }
        },
        dismissButton = { TextButton(onClick = actions::cancelConfirm) { Text("Cancel") } },
    )
}

@Composable
private fun PrivacyGate(session: DraftSession, actions: BoardActions) {
    val step = session.currentStep ?: return
    val text = DraftBoard.privacyText(session) ?: return
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
        Spacer(Modifier.height(Spacing.xl))
        Text(
            text.title,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text.detail,
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

private const val LARGE_FONT = 1.5f
private const val DISABLED_ALPHA = 0.3f

/** Pools larger than this get search and role filters. */
private const val SEARCH_FROM = 10
private const val SIDE_WEIGHT = 0.42f
private val LANDSCAPE_MAX_HEIGHT = 520.dp
private val TILE_MIN_WIDTH = 68.dp
private val SEAT_ART = 32.dp
