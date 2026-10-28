package dev.quinton.goa.web.draft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.board.BoardUiState
import dev.quinton.goa.feature.draft.board.DraftBoardText
import dev.quinton.goa.feature.draft.board.PoolTile
import dev.quinton.goa.feature.draft.board.PoolView
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.draft.ui.StepToken
import dev.quinton.goa.feature.draft.ui.TileState
import dev.quinton.goa.feature.draft.ui.actionName
import dev.quinton.goa.feature.draft.ui.info
import dev.quinton.goa.feature.draft.ui.label
import dev.quinton.goa.feature.draft.ui.teamGlyph
import dev.quinton.goa.feature.draft.ui.teamLabel
import dev.quinton.goa.feature.draft.ui.teamName
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.table.HeroArt
import dev.quinton.goa.web.table.MenuItem
import dev.quinton.goa.web.table.MenuSheet
import dev.quinton.goa.web.table.SequenceStrip
import dev.quinton.goa.web.table.teamClass
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.ScrollToTopOnStep
import dev.quinton.goa.web.ui.publishHeight
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The draft in progress: a full-screen pass-and-play board (no tab bar beside a pick). Whose turn it is
 * stays in view; tap a hero to ban or pick it (confirm first), touch and hold for its peek; hidden steps
 * ask to pass the phone first. A finished draft arranges seats and saves a pending match.
 */
@Composable
fun DraftBoardScreen() {
    val services = LocalServices.current
    val router = services.router
    val scope = rememberCoroutineScope()
    val model = remember {
        DraftBoardModel(services, scope) { event ->
            when (event) {
                is BoardEvent.Finished -> if (event.openMatchSetup) {
                    // The board is done (the Draft tab returns to its setup); Match setup opens on the Timer tab.
                    router.navigate(Route.Draft, replace = true)
                    router.navigate(Route.MatchSetup(event.matchId))
                } else {
                    router.back()
                    services.messages.show(
                        "Saved as a pending match. Log the result under Players › Matches.",
                        "View",
                    ) {
                        router.navigate(Route.Matches)
                    }
                }

                BoardEvent.Closed -> router.back()
            }
        }
    }
    val state by model.uiState.collectAsState()
    LaunchedEffect(state.local.message) {
        val text = state.local.message ?: return@LaunchedEffect
        if (state.local.redo !=
            null
        ) {
            services.messages.show(text, "Redo") { model.redo() }
        } else {
            services.messages.show(text)
        }
        model.dismissMessage()
    }
    var menu by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var peek by remember { mutableStateOf<String?>(null) }
    val board = state.board
    val players = state.playerDraft
    Screen(
        state.title,
        actions = {
            if (state.progress != null) {
                Button({
                    type(ButtonType.Button)
                    classes("goa-icon-button")
                    attr("aria-label", "Undo last step")
                    title("Undo last step")
                    if (!state.canUndo || state.privacyGate) disabled()
                    onClick { if (board != null) model.undo() else model.undoPlayerPick() }
                }) { Icon("undo") }
                IconButton("more", "More", onClick = { menu = true })
            }
        },
        flush = true,
    ) {
        // The finished line-up replaces the board in place: start at "Draft complete", not part way down.
        ScrollToTopOnStep(board?.isComplete == true)
        when {
            state.loading -> LoadingState()

            board != null && state.privacyGate -> PrivacyGate(board, model)

            board != null && board.isComplete -> CompletionContent(state, board, model)

            board != null -> HeroBoard(state, board, model, onPeek = { peek = it })

            players != null -> PlayerDraftContent(state, players, model, onPeek = { peek = it })

            else -> EmptyState("swap", "No draft in progress", "Set up players, teams and a method to start one.") {
                GoaButton("Set up a draft", { router.back() })
            }
        }
    }
    val confirming = state.local.confirm
    if (board != null && !board.isComplete && confirming != null) {
        ConfirmDialog(state, board, confirming, model, onPeek = { peek = it })
    }
    peek?.let { id ->
        HeroPeekSheet(state.heroes.info(id), onDismiss = { peek = null }, onOpenHero = { heroId ->
            peek = null
            model.cancelConfirm()
            router.navigate(Route.HeroDetail(heroId))
        })
    }
    if (menu) {
        MenuSheet(
            "Draft",
            listOf(
                MenuItem("Discard draft", "delete", onClick = {
                    confirmDiscard = true
                }),
            ),
            onDismiss = {
                menu =
                    false
            },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            "Discard this draft?",
            onDismiss = { confirmDiscard = false },
            buttons = {
                GoaButton("Keep drafting", { confirmDiscard = false }, ButtonKind.TEXT)
                GoaButton("Discard", {
                    confirmDiscard = false
                    model.discard()
                }, ButtonKind.DANGER)
            },
        ) { Text("Bans and picks so far are thrown away. Nothing is saved to the match log.") }
    }
}

/** Search, role filter and "hide taken" for the pool (screen state only). */
private class PoolFilters(val query: String, val roles: Set<Role>, val hideTaken: Boolean) {
    fun matches(hero: HeroInfo): Boolean =
        (query.isBlank() || hero.name.contains(query.trim(), ignoreCase = true)) &&
            (roles.isEmpty() || hero.roles.any { it in roles })
}

@Composable
private fun HeroBoard(state: BoardUiState, session: DraftSession, model: DraftBoardModel, onPeek: (String) -> Unit) {
    val tokens = remember(session) {
        session.plan.map { StepToken("${actionName(it.action)} ${teamGlyph(it.team)}", it.action, it.team, it.hidden) }
    }
    val pool = remember(session) { PoolView.of(session) }
    var query by remember { mutableStateOf("") }
    var roles by remember { mutableStateOf(emptySet<Role>()) }
    var hideTaken by remember { mutableStateOf(false) }
    val filters = PoolFilters(query, roles, hideTaken)
    val concealed = remember(session) { session.concealedIds() }
    Div({ classes("goa-draft-board") }) {
        // Sticky: whose turn it is stays on screen however far the pool is scrolled.
        Div({
            classes("goa-draft-banner-slot")
            publishHeight("--pinned-top")
        }) { StepBanner(session, state) }
        Div({ classes("goa-draft-side") }) {
            SequenceStrip(tokens, current = session.currentStepIndex, wrap = false)
            if (session.canEditPool) PoolEditBanner(session, model)
            TeamsPanel(state, session, concealed)
        }
        Div({ classes("goa-draft-pool") }) {
            PoolHeader(
                left = pool.left,
                total = session.pool.size,
                hideTaken = hideTaken,
                onHideTaken = { hideTaken = it },
                searchable = session.pool.size > PoolView.SEARCH_FROM,
                query = query,
                onQuery = { query = it },
                roles = Role.entries.filter { role -> session.pool.any { role in state.heroes.info(it).roles } },
                selectedRoles = roles,
                onToggleRole = { role -> roles = if (role in roles) roles - role else roles + role },
            )
            val open = pool.open.filter { filters.matches(state.heroes.info(it.heroId)) }
            val others = pool.otherHands.filter { filters.matches(state.heroes.info(it.heroId)) }
            pool.handOwner?.let {
                H2({
                    classes("t-title-small", "c-primary", "goa-draft-subheading")
                }) { Text("${state.playerName(it)}'s heroes") }
            }
            PoolTiles(state, open, model, onPeek)
            if (others.isNotEmpty()) {
                H2({ classes("t-title-small", "c-primary", "goa-draft-subheading") }) { Text("Dealt to other players") }
                PoolTiles(state, others, model, onPeek)
            }
            if (open.isEmpty() &&
                others.isEmpty()
            ) {
                P({ classes("t-body-medium", "c-variant") }) { Text("No heroes match.") }
            }
            val taken = if (hideTaken) {
                emptyList()
            } else {
                pool.taken.filter {
                    filters.matches(state.heroes.info(it.heroId))
                }
            }
            if (taken.isNotEmpty()) TakenStrip(state, taken, onPeek)
        }
    }
}

@Composable
private fun PoolTiles(state: BoardUiState, tiles: List<PoolTile>, model: DraftBoardModel, onPeek: (String) -> Unit) {
    Div({ classes("goa-draft-grid") }) {
        tiles.forEach { tile ->
            key(tile.heroId) {
                HeroTile(
                    hero = state.heroes.info(tile.heroId),
                    state = tile.state,
                    compact = true,
                    onClick = when (tile.state) {
                        TileState.Available -> ({ model.tapHero(tile.heroId) })
                        TileState.Inactive -> ({ model.tapUnavailable(tile.heroId) })
                        else -> null
                    },
                    onPeek = { onPeek(tile.heroId) },
                )
            }
        }
    }
}

@Composable
private fun PoolHeader(
    left: Int,
    total: Int,
    hideTaken: Boolean,
    onHideTaken: (Boolean) -> Unit,
    searchable: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    roles: List<Role>,
    selectedRoles: Set<Role>,
    onToggleRole: (Role) -> Unit,
) {
    Div({ classes("goa-draft-pool-header") }) {
        Div({ classes("goa-table-heading-row") }) {
            H2({ classes("t-title-medium", "goa-table-tight") }) { Text("Pool · $left of $total left") }
            Chip("Hide taken", hideTaken, onClick = { onHideTaken(!hideTaken) })
        }
        if (searchable) {
            Div({ classes("goa-draft-search") }) {
                Icon("search", IconSize.SMALL)
                Input(InputType.Search) {
                    classes("goa-table-input")
                    value(query)
                    placeholder("Search heroes")
                    attr("aria-label", "Search heroes")
                    attr("enterkeyhint", "search")
                    onInput { onQuery(it.value) }
                    onKeyDown { if (it.key == "Enter") (it.target.asDynamic()).blur() }
                }
                if (query.isNotEmpty()) IconButton("close", "Clear search", onClick = { onQuery("") })
            }
            Div({ classes("goa-chip-row") }) {
                roles.forEach { role -> Chip(role.label, role in selectedRoles, onClick = { onToggleRole(role) }) }
            }
        }
        P({
            classes("t-body-small", "c-variant", "goa-table-tight")
        }) { Text("Touch and hold a hero for roles, stats and starting cards.") }
    }
}

/** Banned and picked heroes as small chips: they stay visible but leave the grid to open heroes. */
@Composable
private fun TakenStrip(state: BoardUiState, taken: List<PoolTile>, onPeek: (String) -> Unit) {
    H2({ classes("t-title-small", "c-primary", "goa-draft-subheading") }) { Text("Taken") }
    Div({ classes("goa-draft-taken") }) {
        taken.forEach { tile ->
            val team = when (val s = tile.state) {
                is TileState.Banned -> s.team
                is TileState.Picked -> s.team
                else -> return@forEach
            }
            val banned = tile.state is TileState.Banned
            val hero = state.heroes.info(tile.heroId)
            key(tile.heroId) {
                Button({
                    type(ButtonType.Button)
                    classes("goa-draft-taken-chip", teamClass(team))
                    if (banned) classes("banned")
                    attr("aria-label", "${hero.name}, ${if (banned) "banned" else "picked"} by ${teamName(team)}")
                    onClick { onPeek(tile.heroId) }
                }) {
                    HeroArt(hero.id, hero.portrait, hero.name, extraClass = "goa-draft-taken-art")
                    if (banned) {
                        Icon("block", IconSize.SMALL)
                    } else {
                        Span({
                            classes("goa-table-team-text", teamClass(team))
                        }) { Text(teamGlyph(team)) }
                    }
                    Span({ classes("t-label-large") }) { Text(hero.name) }
                }
            }
        }
    }
}

@Composable
private fun StepBanner(session: DraftSession, state: BoardUiState) {
    val step = session.currentStep ?: return
    Div({
        classes("goa-draft-banner", teamClass(step.team))
        attr("aria-live", "polite")
    }) {
        Div({ classes("goa-table-row-text") }) {
            P({
                classes("goa-draft-banner-title", "goa-table-tight")
            }) { Text(DraftBoardText.bannerTitle(session).orEmpty()) }
            P({
                classes("t-body-medium", "c-variant", "goa-table-tight")
            }) { Text(DraftBoardText.bannerDetail(session, state::playerName).orEmpty()) }
        }
        state.local.remainingSeconds?.let { remaining ->
            Span({
                classes("goa-draft-step-clock", "t-numeric")
                if (remaining < 0) classes("over")
                attr("aria-label", DraftBoardText.spokenStepClock(remaining))
            }) { Text(DraftBoardText.stepClock(remaining)) }
        }
    }
}

@Composable
private fun PoolEditBanner(session: DraftSession, model: DraftBoardModel) {
    Div({ classes("goa-draft-pool-edit") }) {
        Div({ classes("goa-table-row-text") }) {
            Span({ classes("t-title-small") }) { Text("Pool of ${session.pool.size} dealt") }
            Span({
                classes("t-body-small", "c-variant")
            }) { Text("Before the first step you can re-roll it, or tap a hero to swap it out.") }
        }
        GoaButton("Re-roll", model::reroll, ButtonKind.OUTLINED, icon = "dice")
    }
}

@Composable
private fun TeamsPanel(state: BoardUiState, session: DraftSession, concealed: Set<String>) {
    Div({ classes("goa-draft-teams") }) {
        for (team in listOf(session.teamA, session.teamB)) TeamColumn(state, session, team, concealed)
    }
}

@Composable
private fun TeamColumn(state: BoardUiState, session: DraftSession, team: Team, concealed: Set<String>) {
    val active = session.currentStep?.team == team
    // Only the team on the clock may peek at its own concealed choices: the pass-the-phone gate has
    // just been acknowledged by that team, so this reveals nothing to the other team.
    val canPeek = active && session.currentStep?.hidden == true
    val picks = DraftEngine.assignments(session).filter { it.seat.team == team }
    val bans = session.bans.filter { it.team == team }
    // Role coverage from revealed picks only, so a hidden pick never shows through.
    val covered = picks.mapNotNull {
        it.heroId?.takeIf { id -> id !in concealed }
    }.flatMap { state.heroes.info(it).roles }.toSet()
    Div({
        classes("goa-draft-team", teamClass(team))
        if (active) classes("active")
    }) {
        Div({ classes("goa-table-heading-row") }) {
            Span({ classes("t-title-small", "goa-table-team-text", teamClass(team)) }) { Text(teamLabel(team)) }
            Span({
                classes("t-label-medium", "c-variant")
                attr("aria-label", if (team == session.teamA) "Team A" else "Team B")
            }) { Text(if (team == session.teamA) "A" else "B") }
        }
        picks.forEach { (seat, heroId) ->
            Div({ classes("goa-draft-seat-line") }) {
                val hidden = heroId != null && heroId in concealed
                when {
                    hidden -> Span({
                        classes("goa-draft-concealed", teamClass(team))
                        attr("aria-hidden", "true")
                    }) { Icon("lock", IconSize.SMALL) }

                    heroId != null -> HeroArt(
                        heroId,
                        state.heroes.info(heroId).portrait,
                        state.heroes.info(heroId).name,
                        extraClass = "goa-draft-seat-art",
                    )

                    else -> Span({ classes("goa-draft-seat-empty", teamClass(team)) })
                }
                Div({ classes("goa-table-row-text") }) {
                    if (hidden) {
                        HiddenChoice("Hidden pick", state.heroes.info(heroId.orEmpty()).name, canPeek)
                    } else {
                        Span({
                            classes(
                                "t-body-medium",
                                if (heroId ==
                                    null
                                ) {
                                    "c-variant"
                                } else {
                                    "goa-table-plain"
                                },
                                "goa-table-ellipsis",
                            )
                        }) {
                            Text(heroId?.let { state.heroes.info(it).name } ?: "Open seat")
                        }
                    }
                    Span({ classes("t-label-medium", "c-variant", "goa-table-ellipsis") }) {
                        Span({ classes("goa-table-team-text", teamClass(team)) }) { Text(teamGlyph(team) + " ") }
                        Text(state.playerName(seat.pilotId) + if (seat.handicap) " · H" else "")
                    }
                }
            }
        }
        if (covered.isNotEmpty()) RoleChips(Role.entries.filter { it in covered }, tint = teamClass(team))
        if (bans.isNotEmpty()) {
            Div({ classes("goa-draft-bans") }) {
                bans.forEach { ban ->
                    val name = state.heroes.info(ban.heroId).name
                    Span({ classes("goa-draft-ban", teamClass(team)) }) {
                        Icon("block", IconSize.SMALL)
                        if (ban.heroId in concealed) {
                            HiddenChoice("Hidden ban", name, canPeek)
                        } else {
                            Span({
                                classes("t-label-medium")
                                attr("aria-label", "Banned: $name")
                            }) { Text(name) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A concealed choice. When [canPeek] (the team on the clock looking at its own hidden choice), press
 * and hold shows the hero until released.
 */
@Composable
private fun HiddenChoice(label: String, secret: String, canPeek: Boolean) {
    var revealed by remember { mutableStateOf(false) }
    if (!canPeek) {
        Span({ classes("t-body-medium", "c-variant") }) { Text(label) }
        return
    }
    Button({
        type(ButtonType.Button)
        classes("goa-draft-hidden-peek")
        attr("aria-label", "$label. Your team's choice: press and hold to see it.")
        addEventListener("pointerdown") { revealed = true }
        listOf("pointerup", "pointerleave", "pointercancel").forEach { name ->
            addEventListener(name) { revealed = false }
        }
        onContextMenu { it.preventDefault() }
    }) {
        Text(if (revealed) secret else "$label · hold to see")
    }
}

@Composable
private fun ConfirmDialog(
    state: BoardUiState,
    session: DraftSession,
    heroId: String,
    model: DraftBoardModel,
    onPeek: (String) -> Unit,
) {
    val step = session.currentStep ?: return
    val hero = state.heroes.info(heroId)
    val ban = step.action == DraftAction.BAN
    val pilot = step.seatId?.let { session.seat(it) }?.pilotId?.let { state.playerName(it) }
    AlertDialog(
        if (ban) "Ban ${hero.name}?" else "Pick ${hero.name}?",
        onDismiss = model::cancelConfirm,
        buttons = {
            GoaButton("Cancel", model::cancelConfirm, ButtonKind.TEXT)
            Button({
                type(ButtonType.Button)
                classes("goa-button", "goa-draft-team-button", teamClass(step.team))
                onClick { model.confirm() }
            }) { Text("${teamGlyph(step.team)} ${if (ban) "Ban" else "Pick"}") }
        },
    ) {
        Div({ classes("goa-draft-confirm") }) {
            HeroArt(hero.id, hero.portrait, hero.name, extraClass = "goa-draft-confirm-art")
            if (hero.title.isNotEmpty()) P({ classes("t-body-medium", "goa-table-tight") }) { Text(hero.title) }
            ComplexityStars(hero.complexity)
            RoleChips(hero.roles)
            P({
                classes("t-body-medium", "goa-table-tight")
            }) { Text(DraftBoardText.confirmText(session, pilot).orEmpty()) }
            GoaButton("Stats and starting cards", { onPeek(heroId) }, ButtonKind.TEXT, icon = "info")
            if (session.canEditPool) {
                GoaButton("Swap out of the pool instead", {
                    model.swapOut(heroId)
                }, ButtonKind.TEXT, icon = "swap")
            }
        }
    }
}

@Composable
private fun PrivacyGate(session: DraftSession, model: DraftBoardModel) {
    val step = session.currentStep ?: return
    Div({ classes("goa-draft-privacy", teamClass(step.team)) }) {
        Span({ classes("goa-draft-privacy-mark") }) { Icon("lock", IconSize.LARGE) }
        H2({ classes("t-headline-small", "goa-table-team-text", teamClass(step.team), "goa-table-center") }) {
            Text("Pass the phone to ${teamLabel(step.team)}")
        }
        P({
            classes("t-body-large", "c-variant", "goa-table-center")
        }) { Text(DraftBoardText.privacyText(session).orEmpty()) }
        Button({
            type(ButtonType.Button)
            classes("goa-button", "goa-draft-team-button", "large", teamClass(step.team))
            onClick { model.acknowledgePrivacy() }
        }) { Text("I'm ${teamName(step.team)} — show the board") }
    }
}
