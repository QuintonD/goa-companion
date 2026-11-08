package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.cardtext.GameIcon
import dev.quinton.goa.core.designsystem.component.CoinIcon
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.LifeCounterIcon
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.WaveCounterIcon
import dev.quinton.goa.core.designsystem.component.horizontalEdgeFade
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.TeamBadge
import dev.quinton.goa.feature.board.ui.badgeOverhang
import dev.quinton.goa.feature.board.ui.cards.CardFocusState

/*
 * The table's widgets (ADR-083, docs/DESIGN.md "Play against the computer"): what floats over the map is a few small
 * plates rather than one box, each icons and numbers with no words of its own; TalkBack hears every one in full.
 * The score ([ScoreWidget]) and the turn's heroes ([TurnChips]) at the top, the map's controls and its key on demand
 * at the end edge ([GameTable]).
 */

/**
 * The round at a glance as one slim pill (ADR-083): each team's Life counter with its number (the team's mark on its
 * corner: never colour alone), the Wave counters left (per Lane, "L1"), the turn's four pips (done solid and dimmer,
 * current bright and ringed, later hollow) and the Tie Breaker coin's showing face. No group names: TalkBack hears
 * them ("Red 3 Life counters. Wave counters …"). It grows with large text to 1.3×, so it stays a pill.
 */
@Composable
internal fun ScoreWidget(table: TableUi, modifier: Modifier = Modifier, framed: Boolean = true) {
    val view = table.view
    val waves = laneWaves(table)
    val fontScale = LocalDensity.current.fontScale
    val cap = minOf(fontScale, WIDGET_MAX_SCALE) / fontScale
    val number = GoaTextStyles.numeric.copy(
        fontSize = 16.sp * cap,
        lineHeight = 18.sp * cap,
        fontWeight = FontWeight.Bold,
    )
    val icon = scaledIconSize(18.dp, WIDGET_MAX_SCALE)
    WidgetPlate(framed, modifier) {
        Row(
            Modifier
                .clearAndSetSemantics { contentDescription = hudSpoken(table) }
                .heightIn(min = scaledIconSize(WIDGET_HEIGHT, WIDGET_MAX_SCALE))
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Team.entries.forEach { team ->
                    val left = view.lifeCounters[team] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            LifeCounterIcon(team, spent = left == 0, size = icon)
                            TeamMark(
                                team,
                                Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 1.dp),
                                markSize = icon * 0.42f,
                            )
                        }
                        Spacer(Modifier.width(3.dp))
                        Text("$left", style = number)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                waves.forEach { (lane, left) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WaveCounterIcon(spent = left == 0, size = icon)
                        lane?.let {
                            Spacer(Modifier.width(2.dp))
                            Text(
                                it.replace("Lane ", "L"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(3.dp))
                        Text("$left", style = number)
                    }
                }
            }
            TurnPips(view.turn, scale = PIP_SCALE)
            TieBreakerMark(view.tieBreaker, 18.dp)
        }
    }
}

/** A widget's own small plate, or none when it lies on another's ([framed] false). */
@Composable
private fun WidgetPlate(framed: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    if (framed) PlateSurface(modifier, shape = WidgetShape, content = content) else Box(modifier) { content() }
}

/** Each Lane's name (null with one Lane) and the Wave counters left in it. */
internal fun laneWaves(table: TableUi): List<Pair<String?, Int>> =
    table.map.lanes.map { lane -> BoardText.lane(table.map, lane.id) to (table.view.wavesLeft[lane.id] ?: 0) }

/** What TalkBack hears for the round at a glance: every counter by name. */
internal fun hudSpoken(table: TableUi): String {
    val view = table.view
    return buildList {
        Team.entries.forEach { team ->
            add("${BoardText.team(team)} ${BoardText.count(view.lifeCounters[team] ?: 0, "Life counter")}")
        }
        laneWaves(table).forEach { (lane, left) ->
            add(listOfNotNull(lane, "${BoardText.count(left, "Wave counter")} left").joinToString(" "))
        }
        add("Round ${view.round}, turn ${view.turn} of $TURNS_IN_ROUND")
        add("Tie Breaker coin shows ${BoardText.team(view.tieBreaker)}")
    }.joinToString(". ")
}

/** Turns in a round (p.7). */
internal const val TURNS_IN_ROUND = 4

/**
 * The turn's heroes as portrait chips on one small plate (ADR-083), in acting order after the reveal, team order
 * before it. Who acts is the largest portrait with a bronze ring and glow; the next a teal ring (and it is the one
 * after); done heroes step back, dimmed, with a tick; nobody needs a word for it. Beside each portrait the initiative
 * as it counts ("=10" in teal while tied); before the reveal, the level, coins and cards in hand instead (a card back
 * at the portrait's head once its card is down). A tap opens the hero's card large (before the reveal, the hero's
 * state); a long press the hero's state. TalkBack hears each chip in full ("1st to act, Wasp, Red, …").
 * [vertical]: one chip under another (sideways, a column at the map's edge). The chips scroll when they don't fit,
 * the acting hero kept in view.
 */
@Composable
internal fun TurnChips(
    table: TableUi,
    onHero: (String) -> Unit,
    focus: CardFocusState?,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    framed: Boolean = true,
) {
    val track = table.track
    WidgetPlate(framed, modifier) {
        val chips: @Composable () -> Unit = {
            if (track != null) {
                val open = trackOpener(table, track, focus, onHero)
                val responder = responderId(table)
                track.slots.forEach { slot ->
                    val role = cellRole(slot, responder)
                    val word = cellWord(role, slot, table)
                    val first = slot.cards.firstOrNull()
                    val card = first?.cardId?.takeIf { !first.facedown }?.let(table::card)
                    TurnChip(
                        table = table,
                        heroId = slot.heroId,
                        team = slot.team,
                        offBoard = slot.offBoard,
                        look = chipLook(role),
                        spoken = spokenSlot(table, slot).let { said ->
                            when (role) {
                                CellRole.RESOLVING -> said.replace("acting now", "resolving its card")
                                CellRole.ANSWERS -> "$said, ${word.lowercase()} now"
                                else -> said
                            }
                        },
                        clickLabel = "Look at the card",
                        onClick = open(slot),
                        onLongClick = { onHero(slot.heroId) },
                        action = slot.action?.let { actionIcon(it, card) },
                    ) { muted ->
                        Initiative(slot.initiative, slot.tied, muted)
                    }
                }
            } else {
                heroOrder(table).forEach { hero ->
                    val place = table.initiativeOrder.indexOf(hero.heroId).takeIf { it >= 0 }?.plus(1)
                    TurnChip(
                        table = table,
                        heroId = hero.heroId,
                        team = hero.team,
                        offBoard = hero.at == null,
                        look = if (table.view.acting == hero.heroId) ChipLook.LEAD else ChipLook.LATER,
                        spoken = spokenChip(table, hero, place),
                        clickLabel = showGameState(table.hero(hero.heroId).name),
                        onClick = { onHero(hero.heroId) },
                        onLongClick = null,
                        cardDown = cardDown(table, hero),
                        marker = hero,
                    ) { _ ->
                        HeroNumbers(hero)
                    }
                }
            }
        }
        val lead = track?.slots?.indexOfFirst { it.state == SlotState.NOW } ?: -1
        val density = LocalDensity.current
        if (vertical) {
            val scroll = rememberScrollState()
            LaunchedEffect(lead) {
                if (lead > 1) scroll.scrollTo(with(density) { (CHIP_STEP * (lead - 1)).roundToPx() })
            }
            Column(
                Modifier.verticalScroll(scroll).padding(horizontal = 4.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
            ) { chips() }
        } else {
            val scroll = rememberScrollState()
            LaunchedEffect(lead) {
                if (lead > 1) scroll.scrollTo(with(density) { (CHIP_STEP * (lead - 1)).roundToPx() })
            }
            Row(
                Modifier
                    .horizontalEdgeFade(scroll)
                    .horizontalScroll(scroll)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) { chips() }
        }
    }
}

/** Before the reveal (or with no turn resolving): the heroes in initiative order once known, else by team. */
private fun heroOrder(table: TableUi): List<HeroView> {
    val order = table.initiativeOrder
    return if (order.isNotEmpty()) {
        order.mapNotNull(table::heroView) + table.view.heroes.filter { it.heroId !in order }
    } else {
        table.view.heroes.sortedBy { it.team.ordinal }
    }
}

/** How a chip shows the hero's part in the turn, never by colour alone (size, ring, glow, dimming, a tick). */
internal enum class ChipLook {
    /** Acts (or answers) now: the largest portrait, a bronze ring and glow. */
    LEAD,

    /** Its card resolves while another hero answers it: a thin bronze ring. */
    RESOLVING,

    /** Acts next: a teal ring. */
    NEXT,

    /** Done: dimmed, with a tick. */
    DONE,

    /** Passed or out of the turn: dimmed. */
    GONE,

    /** Acts later: plain. */
    LATER,
}

private fun chipLook(role: CellRole): ChipLook = when (role) {
    CellRole.ACTS, CellRole.ANSWERS -> ChipLook.LEAD
    CellRole.RESOLVING -> ChipLook.RESOLVING
    CellRole.NEXT -> ChipLook.NEXT
    CellRole.DONE -> ChipLook.DONE
    CellRole.PASSED, CellRole.SKIPPED -> ChipLook.GONE
    CellRole.LATER -> ChipLook.LATER
}

/** One hero's chip: the portrait in its team ring (the state as ring, size and dimming), [numbers] beside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TurnChip(
    table: TableUi,
    heroId: String,
    team: Team,
    offBoard: Boolean,
    look: ChipLook,
    spoken: String,
    clickLabel: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    action: GameIcon? = null,
    cardDown: Boolean = false,
    marker: HeroView? = null,
    numbers: @Composable (muted: Boolean) -> Unit,
) {
    val info = table.hero(heroId)
    val past = look == ChipLook.DONE || look == ChipLook.GONE
    val portrait = if (look == ChipLook.LEAD) LEAD_PORTRAIT else CHIP_PORTRAIT
    val ring: Pair<Dp, Color>? = when (look) {
        ChipLook.LEAD -> 2.5.dp to NowBand
        ChipLook.RESOLVING -> 1.5.dp to NowBand
        ChipLook.NEXT -> 2.dp to NextBand
        else -> null
    }
    Row(
        Modifier
            .heightIn(min = CHIP_HEIGHT)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = clickLabel) {
                    onClick()
                    true
                }
                if (onLongClick != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(showGameState(info.name)) {
                            onLongClick()
                            true
                        },
                    )
                }
            }
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(portrait + 8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .then(
                        if (look == ChipLook.LEAD) {
                            Modifier.drawBehind {
                                drawCircle(
                                    Brush.radialGradient(
                                        listOf(NowBand.copy(alpha = 0.55f), Color.Transparent),
                                        radius = size.minDimension * 0.62f,
                                    ),
                                    radius = size.minDimension * 0.62f,
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .then(ring?.let { (w, c) -> Modifier.border(w, c, CircleShape).padding(w + 1.dp) } ?: Modifier),
            ) {
                HeroPortrait(
                    heroId,
                    info.portrait,
                    null,
                    Modifier
                        .size(portrait)
                        .border(2.dp, teamColor(team), CircleShape)
                        .alpha(if (offBoard || past) PAST_ALPHA else 1f),
                    shape = CircleShape,
                    framed = false,
                )
                if (offBoard) {
                    Icon(
                        Icons.Filled.Close,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).size(portrait * 0.7f),
                    )
                }
                TeamBadge(team, Modifier.align(Alignment.BottomEnd).badgeOverhang(BADGE), size = BADGE)
                if (look == ChipLook.DONE) DoneTick(Modifier.align(Alignment.BottomStart).offset((-3).dp, 3.dp))
                action?.let { ActionBadge(it, compact = true, Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp)) }
                if (cardDown) {
                    TableCard(
                        null,
                        Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp),
                        width = 10.dp,
                        backOf = heroId,
                    )
                }
                marker?.let {
                    ActiveMarkerBadge(table, it, Modifier.align(Alignment.TopStart).offset(x = (-5).dp, y = (-4).dp))
                }
            }
        }
        Spacer(Modifier.width(2.dp))
        numbers(past)
    }
}

/** A done hero's tick, on a dark disc at the portrait's foot. */
@Composable
private fun DoneTick(modifier: Modifier) {
    Box(
        modifier
            .size(BADGE)
            .background(Color(0xFF10181E), CircleShape)
            .border(1.dp, GoaTheme.colors.outlineMuted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, null, tint = Color(0xFFE8E2D6), modifier = Modifier.size(BADGE * 0.72f))
    }
}

/** The initiative as it counts, in figures: "=10" in teal while tied with another hero still to act, else "–". */
@Composable
private fun Initiative(initiative: Int?, tied: Boolean, muted: Boolean) {
    Text(
        initiative?.let { if (tied) "=$it" else "$it" } ?: "–",
        style = chipNumber(16f),
        color = when {
            tied -> MaterialTheme.colorScheme.secondary
            muted -> GoaTheme.colors.textMuted
            else -> MaterialTheme.colorScheme.onSurface
        },
        maxLines = 1,
        softWrap = false,
    )
}

/** Before the reveal: the level over the coins and the cards in hand, small figures beside the portrait. */
@Composable
private fun HeroNumbers(hero: HeroView) {
    val style = chipNumber(11f)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Text(
            "Lv${hero.level}",
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CoinIcon(size = scaledIconSize(10.dp, WIDGET_MAX_SCALE))
            Text("${hero.coins}", style = style, color = color, maxLines = 1, softWrap = false)
            TableCard(null, width = 8.dp, backOf = hero.heroId)
            Text("${hero.handCount}", style = style, color = color, maxLines = 1, softWrap = false)
        }
    }
}

/** Figures that grow with large text to 1.3×, so the chips stay small. */
@Composable
private fun chipNumber(size: Float): TextStyle {
    val density = LocalDensity.current
    val scale = minOf(density.fontScale, WIDGET_MAX_SCALE)
    val sp = with(density) { (size * scale).dp.toSp() }
    return GoaTextStyles.numeric.copy(fontSize = sp, lineHeight = sp * 1.15f, fontWeight = FontWeight.Bold)
}

private const val WIDGET_MAX_SCALE = 1.3f
private const val PIP_SCALE = 0.8f
private const val PAST_ALPHA = 0.42f
private val WIDGET_HEIGHT = 32.dp
internal val WidgetShape = RoundedCornerShape(10.dp)
private val CHIP_PORTRAIT = 28.dp
private val LEAD_PORTRAIT = 34.dp
private val CHIP_HEIGHT = 44.dp
private val CHIP_GAP = 4.dp
private val CHIP_STEP = 60.dp
private val BADGE = 11.dp
