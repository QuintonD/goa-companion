package dev.quinton.goa.feature.draft.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.StartingCards
import dev.quinton.goa.core.domain.draft.SlotStep
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.Role as HeroRole
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team

/** Motion tokens (docs/DESIGN.md: 200–300 ms emphasised). */
internal object DraftMotion {
    const val SHORT = 200
    const val MEDIUM = 300
}

/** True when the system "Remove animations" setting is on; animations then snap. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

internal fun teamGlyph(team: Team): String = if (team == Team.RED) "◆" else "●"

internal fun teamName(team: Team): String = if (team == Team.RED) "Red" else "Blue"

/** "◆ Red" — team identity is never colour-only. */
internal fun teamLabel(team: Team): String = "${teamGlyph(team)} ${teamName(team)}"

internal fun actionName(action: DraftAction): String = if (action == DraftAction.BAN) "Ban" else "Pick"

/** "1 game", "2 games"; [many] for irregular plurals ("heroes"). */
internal fun plural(count: Int, one: String, many: String = "${one}s"): String =
    "$count ${if (count == 1) one else many}"

/** Official role names (rulebook p.21 Hero Profile card). */
internal val HeroRole.label: String
    get() = when (this) {
        HeroRole.TACTICIAN -> "Tactician"
        HeroRole.DISABLER -> "Disabler"
        HeroRole.DURABLE -> "Durable"
        HeroRole.MELEE -> "Melee"
        HeroRole.PUSHER -> "Pusher"
        HeroRole.DAMAGER -> "Damager"
        HeroRole.SNIPER -> "Sniper"
        HeroRole.TOKENS -> "Tokens"
        HeroRole.FARMING -> "Farming"
        HeroRole.HEALER -> "Healer"
    }

/** One of a hero's starting cards, for the draft's hero peek. */
@Immutable
data class CardInfo(val name: String, val color: CardColor, val tier: Int?, val image: String? = null)

/**
 * The five starting cards (RB p.6, Hero Setup), selected from the catalog by [StartingCards]:
 * Gold, Silver, then Tier I by colour. Basic cards whose own text says they start in the deck
 * (Takahide's extra golds) are left out, since card text overrides the rulebook (RB p.3).
 */
internal fun startingCards(hero: Hero): List<CardInfo> =
    StartingCards.of(hero).map { CardInfo(it.name, it.color, it.tier, it.image) }

/** Display data for one hero; unknown ids render as "Unknown hero" (ARCHITECTURE.md). */
@Immutable
data class HeroInfo(
    val id: String,
    val name: String,
    val title: String = "",
    val portrait: String? = null,
    /** The deck symbol (official, ADR-042); null for unknown heroes. */
    val symbol: String? = null,
    val complexity: Int? = null,
    val known: Boolean = true,
    val roles: List<HeroRole> = emptyList(),
    val stats: HeroStats = HeroStats(),
    val startingCards: List<CardInfo> = emptyList(),
    /** Spell cards outside the deck (Gydion's spellbook, the hero JSON's `<hero>-spell-` extras). */
    val spellCards: Int = 0,
) {
    companion object {
        fun of(hero: Hero) = HeroInfo(
            id = hero.id,
            name = hero.name,
            title = hero.title,
            portrait = hero.portrait,
            symbol = hero.symbol,
            complexity = hero.complexity,
            roles = hero.roles,
            stats = hero.stats,
            startingCards = startingCards(hero),
            spellCards = hero.extras.count { it.id.startsWith("${hero.id}-spell-") },
        )

        fun unknown(id: String) = HeroInfo(id, "Unknown hero", known = false)
    }
}

internal fun Map<String, HeroInfo>.info(id: String): HeroInfo = this[id] ?: HeroInfo.unknown(id)

/** Visual state of a hero tile on the board. */
sealed interface TileState {
    data object Available : TileState

    /** Not choosable now (e.g. dealt to another seat) but still in play. */
    data object Inactive : TileState

    data class Banned(val team: Team) : TileState

    data class Picked(val team: Team) : TileState
}

/**
 * A hero tile: official art in our frame, name, and its draft state (struck/dimmed when banned).
 * [onLongClick] opens the hero peek; a tile without [onClick] then opens it on tap too.
 */
@Composable
internal fun HeroTile(
    hero: HeroInfo,
    state: TileState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val reduced = rememberReducedMotion()
    val alpha by animateFloatAsState(
        targetValue = when (state) {
            TileState.Available -> 1f
            TileState.Inactive -> 0.45f
            is TileState.Banned -> 0.38f
            is TileState.Picked -> 0.6f
        },
        animationSpec = tween(if (reduced) 0 else DraftMotion.MEDIUM),
        label = "tileAlpha",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.secondary
            state is TileState.Picked -> teamColor(state.team)
            else -> Color.Transparent
        },
        animationSpec = tween(if (reduced) 0 else DraftMotion.SHORT),
        label = "tileBorder",
    )
    val stateText = when (state) {
        TileState.Available -> "available"
        TileState.Inactive -> "not available to this seat"
        is TileState.Banned -> "banned by ${teamName(state.team)}"
        is TileState.Picked -> "picked by ${teamName(state.team)}"
    }
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(shape)
            .then(
                when {
                    onLongClick != null -> Modifier.combinedClickable(
                        role = Role.Button,
                        onClickLabel = when {
                            onClick == null -> "Show ${hero.name}"
                            state == TileState.Inactive -> "Why ${hero.name} can't be chosen"
                            else -> "Choose ${hero.name}"
                        },
                        onLongClickLabel = "Show ${hero.name}",
                        onLongClick = onLongClick,
                        onClick = onClick ?: onLongClick,
                    )

                    onClick != null ->
                        Modifier.clickable(role = Role.Button, onClickLabel = "Choose ${hero.name}", onClick = onClick)

                    else -> Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = hero.name
                stateDescription = stateText
            }
            .padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .border(BorderStroke(2.dp, borderColor), shape),
        ) {
            HeroPortrait(
                heroId = hero.id,
                portrait = hero.portrait,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(alpha)
                    .then(
                        if (state is TileState.Banned) Modifier.strike(MaterialTheme.colorScheme.error) else Modifier,
                    ),
            )
            when (state) {
                is TileState.Banned -> StateChip(
                    text = "Banned",
                    team = state.team,
                    icon = true,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.xs),
                )

                is TileState.Picked -> StateChip(
                    text = teamName(state.team),
                    team = state.team,
                    icon = false,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.xs),
                )

                else -> Unit
            }
        }
        Spacer(Modifier.height(if (compact) 2.dp else Spacing.xs))
        Text(
            text = hero.name,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            textDecoration = if (state is TileState.Banned) TextDecoration.LineThrough else null,
            color = if (state == TileState.Available) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun Modifier.strike(color: Color): Modifier = drawWithContent {
    drawContent()
    drawLine(
        color = color.copy(alpha = 0.85f),
        start = Offset(size.width * 0.12f, size.height * 0.12f),
        end = Offset(size.width * 0.88f, size.height * 0.88f),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

@Composable
private fun StateChip(text: String, team: Team, icon: Boolean, modifier: Modifier = Modifier) {
    val color = teamColor(team)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, color),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(2.dp))
            }
            Text(
                "${teamGlyph(team)} $text",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}

/** A concealed (hidden) pick: shown as a lock until the hidden run ends. */
@Composable
internal fun ConcealedTile(team: Team, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, teamColor(team), MaterialTheme.shapes.medium)
            .semantics { contentDescription = "Hidden pick by ${teamName(team)}" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = teamColor(team))
    }
}

/** One step in a sequence preview, already resolved to a display team label. */
@Immutable
data class StepToken(val label: String, val action: DraftAction, val team: Team?, val hidden: Boolean = false)

/** Tokens for slot steps; [teamA] maps A/B to Red/Blue once the coin is known. */
internal fun List<SlotStep>.tokens(teamA: Team? = null): List<StepToken> = map { step ->
    val team = teamA?.let { if (step.team == TeamSlot.A) it else it.opponent }
    val who = team?.let { teamGlyph(it) } ?: step.team.name
    StepToken("${actionName(step.action)} $who", step.action, team, step.hidden)
}

/**
 * Compact sequence preview: "Ban A · Pick A · …" chips. Bans are outlined, picks filled; the
 * [current] step is highlighted and finished steps fade.
 */
@Composable
internal fun SequenceStrip(
    tokens: List<StepToken>,
    modifier: Modifier = Modifier,
    current: Int? = null,
    wrap: Boolean = true,
) {
    val description = tokens.joinToString(", ") { it.label.replace("◆", "Red").replace("●", "Blue") }
    val content: @Composable () -> Unit = {
        tokens.forEachIndexed { i, token -> StepChipView(token, index = i, current = current) }
    }
    val semantics = Modifier.semantics(mergeDescendants = true) { contentDescription = "Sequence: $description" }
    if (wrap) {
        FlowRow(
            modifier.then(semantics),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) { content() }
    } else {
        Row(
            modifier.then(semantics).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) { content() }
    }
}

@Composable
private fun StepChipView(token: StepToken, index: Int, current: Int?) {
    val accent = token.team?.let { teamColor(it) } ?: MaterialTheme.colorScheme.primary
    val done = current != null && index < current
    val active = current == index
    val isBan = token.action == DraftAction.BAN
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            active -> accent.copy(alpha = 0.28f)
            isBan -> Color.Transparent
            else -> accent.copy(alpha = 0.12f)
        },
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = if (done) 0.3f else 0.8f)),
        modifier = Modifier.alpha(if (done) 0.5f else 1f),
    ) {
        Text(
            text = (if (token.hidden) "🔒 " else "") + token.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (isBan &&
                !active
            ) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

/** Section card with an engraved border, used across draft screens. */
@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(Spacing.md))
            content()
        }
    }
}

/** Large tappable selectable row/card with a title and explanation (≥ 48 dp). */
@Composable
internal fun ChoiceCard(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(
                alpha = 0.10f,
            )
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = Spacing.sm, vertical = 2.dp),
                    )
                }
            }
            if (body.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            extra?.let {
                Spacer(Modifier.height(Spacing.sm))
                it()
            }
        }
    }
}

/*
 * Selection colours in the bronze palette. The shared scheme leaves Material's default
 * (purple-grey) secondaryContainer, so selected chips/segments set their colours explicitly.
 */
@Composable
internal fun goaFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.primary,
)

/** Filter chip in a team's colour (Tie Breaker "Red shows" / "Blue shows"). */
@Composable
internal fun teamFilterChipColors(team: Team): SelectableChipColors {
    val color = teamColor(team)
    return FilterChipDefaults.filterChipColors(
        labelColor = color,
        selectedContainerColor = color.copy(alpha = 0.18f),
        selectedLabelColor = color,
        selectedLeadingIconColor = color,
    )
}

@Composable
internal fun goaSegmentedColors(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    activeContentColor = MaterialTheme.colorScheme.primary,
    activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
    inactiveContainerColor = Color.Transparent,
    inactiveBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
)

@Composable
internal fun goaTonalButtonColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
    contentColor = MaterialTheme.colorScheme.secondary,
)

@Composable
internal fun goaTonalIconColors(): IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    contentColor = MaterialTheme.colorScheme.primary,
)

/** Centered full-size loading/blank helper. */
@Composable
internal fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
