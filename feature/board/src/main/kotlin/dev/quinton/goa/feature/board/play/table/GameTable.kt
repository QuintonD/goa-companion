package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.ui.drawTeamMark
import kotlinx.coroutines.launch

/** What a sheet of the table shows: a card to read, or a hero's dashboard. */
@Immutable
sealed interface TableSheet {
    data class ReadCard(val card: Card) : TableSheet

    data class Hero(val heroId: String) : TableSheet
}

/**
 * The game table (ADR-035), shared by the play screen and the puzzles: the counters (round and
 * turn, Life, Waves, the Tie Breaker coin), every hero's dashboard in a strip (initiative order
 * after the reveal), the real map with the pieces and the pending question on it, and a panel (at
 * the bottom; on the right in landscape) with the question, its options and the player's hand.
 *
 * [onChoose] answers the pending decision. [status] fills the panel while the player has nothing
 * to answer (the computer acting, waiting for the others); [interlude], when given, takes the panel
 * over (a reveal, the end of a round, the coach's nudge). [announcement] is read out by TalkBack.
 */
@Composable
fun GameTable(
    state: TableState,
    onChoose: (Choice) -> Unit,
    modifier: Modifier = Modifier,
    status: @Composable ColumnScope.() -> Unit = {},
    interlude: (@Composable ColumnScope.() -> Unit)? = null,
    announcement: String? = null,
    initialSheet: TableSheet? = null,
) {
    val decision = state.decision
    var picked by remember(decision?.id) { mutableStateOf<Choice?>(null) }
    var sheet by remember { mutableStateOf(initialSheet) }
    val viewport = remember { MapViewport() }
    val pickedHex: Hex? = when (val p = picked) {
        is Choice.Space -> p.hex
        is Choice.Unit -> state.pieces.firstOrNull { it.id == p.unitId }?.at
        is Choice.Token -> state.pieces.firstOrNull { it.id == p.tokenId }?.at
        else -> null
    }
    fun tap(hex: Hex): Boolean {
        val mine = decision?.takeIf { it.mine }
        val occupant = state.pieces.firstOrNull { it.at == hex }
        val option = occupant?.let { mine?.units?.get(it.id) } ?: mine?.spaces?.get(hex)
        if (option != null) {
            // First tap picks (and shows the path), a second tap on it confirms.
            if (picked == option.choice) onChoose(option.choice) else picked = option.choice
            return true
        }
        if (occupant is HeroPiece) {
            sheet = TableSheet.Hero(occupant.heroId)
            return true
        }
        return false
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val sideWidth = minOf(maxWidth * 0.46f, SIDE_PANEL)
        val panelMax = maxHeight * PANEL_MAX
        val orientation = if (landscape) MapOrientation.LANDSCAPE else MapOrientation.PORTRAIT
        val layout = remember(state.map, orientation, state.bottomTeam) {
            MapLayout(state.map, orientation, state.bottomTeam)
        }
        val lane = remember(layout, state.map) { layout.laneRegion(state.map, state.geometry) }
        // A question on the board: bring its options into view, big enough to tap.
        val targets = remember(decision) {
            decision?.takeIf { it.mine }?.let { d ->
                d.spaces.keys + d.units.keys.mapNotNull { id -> state.pieces.firstOrNull { it.id == id }?.at }
            }.orEmpty()
        }
        LaunchedEffect(decision?.id, viewport.unit) {
            if (targets.isEmpty() || viewport.unit == 0f) return@LaunchedEffect
            val region = layout.bounds(targets, pad = 1.1f) ?: return@LaunchedEffect
            viewport.reveal(region, region.center)
        }
        val map = @Composable { mapModifier: Modifier ->
            Box(mapModifier) {
                GameMapView(
                    state = state,
                    layout = layout,
                    viewport = viewport,
                    picked = pickedHex,
                    onTap = ::tap,
                    lane = lane,
                    modifier = Modifier.fillMaxSize(),
                )
                MapButtons(viewport, Modifier.align(Alignment.TopEnd).padding(Spacing.sm))
            }
        }
        val panel = @Composable { panelModifier: Modifier ->
            Panel(
                state,
                picked,
                onPick = { picked = it },
                onChoose = onChoose,
                onRead = { sheet = TableSheet.ReadCard(it) },
                status = status,
                interlude = interlude,
                modifier = panelModifier,
                landscape = landscape,
            )
        }
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Hud { Counters(state) }
                    Hud { HeroStrip(state, onHero = { sheet = TableSheet.Hero(it) }) }
                    map(Modifier.weight(1f).fillMaxWidth())
                }
                panel(Modifier.fillMaxHeight().width(sideWidth))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Hud { Counters(state) }
                Hud { HeroStrip(state, onHero = { sheet = TableSheet.Hero(it) }) }
                map(Modifier.weight(1f).fillMaxWidth())
                panel(Modifier.fillMaxWidth().heightIn(max = panelMax))
            }
        }
        announcement?.let { said ->
            Box(
                Modifier.size(1.dp).semantics {
                    contentDescription = said
                    liveRegion = LiveRegionMode.Polite
                },
            )
        }
    }
    when (val s = sheet) {
        is TableSheet.ReadCard -> BoardSheet(onDismiss = { sheet = null }) { CardReader(s.card, SheetFill) }

        is TableSheet.Hero -> state.hero(s.heroId)?.let { hero ->
            BoardSheet(onDismiss = { sheet = null }) {
                HeroDetails(hero, onRead = { sheet = TableSheet.ReadCard(it) }, modifier = SheetFill)
            }
        }

        null -> Unit
    }
}

@Composable
private fun Panel(
    state: TableState,
    picked: Choice?,
    onPick: (Choice?) -> Unit,
    onChoose: (Choice) -> Unit,
    onRead: (Card) -> Unit,
    status: @Composable ColumnScope.() -> Unit,
    interlude: (@Composable ColumnScope.() -> Unit)?,
    modifier: Modifier,
    landscape: Boolean,
) {
    val decision = state.decision
    Surface(
        modifier = modifier,
        shape = if (landscape) {
            RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
        } else {
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        },
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        shadowElevation = 6.dp,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (interlude != null) {
                interlude()
            } else {
                if (decision != null) DecisionPanel(state, decision, picked, onPick, onChoose, onRead)
                if (decision == null || !decision.mine) status()
            }
            val handShown = decision?.mine == true &&
                (decision.kind == DecisionKind.PLAY_CARD || decision.kind == DecisionKind.DEFEND)
            if (!handShown && state.hand.isNotEmpty()) Hand(state, onRead)
        }
    }
}

/** The viewer's hand: tap a card to read its full text. */
@Composable
private fun Hand(state: TableState, onRead: (Card) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "${state.viewer?.name?.let { "$it's hand" } ?: "Your hand"} · tap to read".uppercase(),
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        CardRow(
            state.hand.map { it.id to it },
            56.dp,
            onClick = { id -> state.hand.firstOrNull { it.id == id }?.let(onRead) },
            actionLabel = "Read",
        )
    }
}

/**
 * The counters and the hero strip are table furniture: they grow with large text up to
 * [MAX_HUD_SCALE], never enough to push the map off a phone (TalkBack reads them in full).
 */
@Composable
private fun Hud(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = minOf(density.fontScale, MAX_HUD_SCALE)
    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) { content() }
}

/** Round and turn (pips), Life counters, Wave counters, the Tie Breaker coin. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Counters(state: TableState) {
    val view = state.view
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Round ${view.round}, turn ${view.turn} of 4, ${TableText.phase(view.phase)}"
            },
        ) {
            Text(
                if (view.phase ==
                    GamePhase.END_OF_ROUND
                ) {
                    "Round ${view.round} · end"
                } else {
                    "Round ${view.round} · Turn ${view.turn}"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(Spacing.xs))
            TurnPips(view.turn, view.phase)
        }
        Team.entries.forEach { team ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${BoardText.team(team)} Life counters: ${view.lifeCounters[team] ?: 0}"
                },
            ) {
                TeamMark(team, markSize = scaledIconSize(10.dp))
                Spacer(Modifier.width(3.dp))
                Text("Life ${view.lifeCounters[team] ?: 0}", style = GoaTextStyles.numeric, color = teamColor(team))
            }
        }
        val waves = view.wavesLeft.values.sum()
        Text(
            if (view.wavesLeft.size > 1) {
                view.wavesLeft.values.joinToString(" / ", prefix = "Waves ")
            } else {
                "Waves $waves"
            },
            style = GoaTextStyles.numeric,
            modifier = Modifier.semantics { contentDescription = "Wave counters left: $waves" },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Tie Breaker coin: ${BoardText.team(view.tieBreaker)} side up"
            },
        ) {
            Coin(view.tieBreaker)
            Spacer(Modifier.width(3.dp))
            Text("Coin", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The Tie Breaker coin: a bronze disc with the team's shape on it. */
@Composable
internal fun Coin(team: Team, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val bronze = MaterialTheme.colorScheme.primary
    val color = teamColor(team)
    val halo = MaterialTheme.colorScheme.background
    Canvas(Modifier.size(scaledIconSize(size, maxScale = 1.4f)).clearAndSetSemantics {}) {
        drawCircle(bronze)
        drawCircle(halo, radius = this.size.minDimension * 0.4f)
        drawTeamMark(team, Offset(this.size.width / 2f, this.size.height / 2f), this.size.minDimension * 0.5f, color)
    }
}

/** Four pips, one per turn (p.7): done filled, current ringed, to come hollow. */
@Composable
private fun TurnPips(turn: Int, phase: GamePhase) {
    val done = MaterialTheme.colorScheme.primary
    val todo = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(width = 44.dp, height = 10.dp).clearAndSetSemantics {}) {
        val r = size.height / 2f
        repeat(4) { i ->
            val c = Offset(r + i * (size.width - 2 * r) / 3f, r)
            val n = i + 1
            when {
                n < turn || phase == GamePhase.END_OF_ROUND -> drawCircle(done, r, c)

                n == turn -> {
                    drawCircle(done, r * 0.55f, c)
                    drawCircle(done, r * 0.92f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.3f))
                }

                else -> drawCircle(todo, r * 0.85f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.25f))
            }
        }
    }
}

/**
 * Every hero's dashboard in short: portrait in the team's ring, level, coins, cards in hand, item
 * icons, markers and Active effects. After the reveal they stand in initiative order (p.7) with the
 * played card's initiative, the acting hero ringed in bronze. Tap one for its cards (open information, p.4).
 */
@Composable
private fun HeroStrip(state: TableState, onHero: (String) -> Unit) {
    val revealed = state.initiative.isNotEmpty()
    val heroes = if (revealed) {
        state.initiative.map { it.hero } +
            state.heroes.filter { h -> state.initiative.none { it.hero.id == h.id } }
    } else {
        state.heroes
    }
    Column(Modifier.fillMaxWidth()) {
        if (revealed) {
            Text(
                "INITIATIVE ORDER · p.7",
                style = GoaTextStyles.overline,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.screen),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            heroes.forEachIndexed { i, hero ->
                val initiative = state.initiative.firstOrNull { it.hero.id == hero.id }
                HeroChip(hero, initiative?.let { (i + 1) to it.initiative }, onClick = { onHero(hero.id) })
            }
        }
    }
}

@Composable
private fun HeroChip(hero: TableHero, order: Pair<Int, Int?>?, onClick: () -> Unit) {
    val color = teamColor(hero.team)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (hero.acting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (hero.acting) 2.dp else 1.dp,
            if (hero.acting) MaterialTheme.colorScheme.primary else color.copy(alpha = 0.7f),
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(max = 260.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    order?.let { (n, init) -> "Acts ${ordinal(n)}" + (init?.let { ", initiative $it" } ?: "") },
                    heroSummary(hero),
                ).joinToString(". ") + ". Show cards"
            },
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = Spacing.sm, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (order != null) {
                Text(
                    "${order.first}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            HeroFace(hero, scaledIconSize(30.dp, maxScale = 1.4f))
            Spacer(Modifier.width(Spacing.xs))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamMark(hero.team, markSize = 8.dp)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        hero.name + if (hero.human) " (you)" else "",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(
                            order?.second?.let { "Init $it" },
                            "Lv ${hero.level}",
                            "${hero.coins}c",
                            BoardText.count(hero.handCount, "card"),
                            "off board".takeIf { hero.at == null },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (hero.items.isNotEmpty()) {
                        Spacer(Modifier.width(3.dp))
                        ItemIcons(hero.items, size = 12.dp)
                    }
                    if (hero.markers.isNotEmpty() || hero.activeEffects.isNotEmpty()) {
                        Spacer(Modifier.width(3.dp))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                "${hero.markers.size + hero.activeEffects.size}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ordinal(n: Int): String = when (n) {
    1 -> "first"
    2 -> "second"
    3 -> "third"
    4 -> "fourth"
    else -> "$n."
}

@Composable
private fun MapButtons(viewport: MapViewport, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    FilledTonalIconButton(
        onClick = { scope.launch { if (viewport.isFit) viewport.animateLane() else viewport.animateFit() } },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.size(48.dp),
    ) {
        if (viewport.isFit) {
            Icon(Icons.Outlined.ZoomIn, contentDescription = "Zoom to the lane")
        } else {
            Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Fit the whole board")
        }
    }
}

private val SIDE_PANEL = 380.dp
private const val PANEL_MAX = 0.55f
private const val MAX_HUD_SCALE = 1.3f
