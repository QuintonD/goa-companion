package dev.quinton.goa.feature.board.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.match.Team

/*
 * The table's own parts on top of the design system's plates (ADR-053): what the board screens repeat.
 * What floats over the map is a [PlateSurface]; what sits on a plate is a well ([PlateWell]) or a
 * gold or steel button, as the mock-ups draw them (design/mockups/original-look).
 */

/**
 * The dark plate as a surface: a [Surface]'s behaviour (a click with its ripple and 48 dp target; a plain
 * plate takes the touches over the map) on the slate, its content themed dark.
 */
@Composable
internal fun PlateSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = PlateShape,
    brackets: Boolean = false,
    border: BorderStroke? = null,
    tint: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    GoaPlateTheme {
        val plate = modifier.slatePlate(shape, brackets)
        if (onClick != null) {
            Surface(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                color = tint,
                contentColor = LocalContentColor.current,
                border = border,
                modifier = plate,
                content = content,
            )
        } else {
            Surface(
                shape = shape,
                color = tint,
                contentColor = LocalContentColor.current,
                border = border,
                modifier = plate,
                content = content,
            )
        }
    }
}

/** A recess on a plate, for what groups a few lines (a flow entry, a moment's recap): the slate shows through. */
internal val PlateWell: Color
    @Composable @ReadOnlyComposable
    get() = if (GoaTheme.isDark) Color.Black.copy(alpha = 0.26f) else LIGHT_WELL

/** The light theme's well: the paper a shade deeper, not grey. */
private val LIGHT_WELL = Color(0x1F6A5634)

/**
 * The mock-ups' two buttons on a plate: gold for the answer, steel for the rest. Steel follows the theme (ADR-068):
 * the dark slate on the slate, a light steel with ink on the light theme's paper (no dark slabs on paper); gold is
 * the same metal in both.
 */
internal enum class PlateTone {
    GOLD,
    STEEL,
    ;

    val brush: Brush
        @Composable @ReadOnlyComposable
        get() = when {
            this == GOLD -> GoldBrush
            GoaTheme.isDark -> SteelBrush
            else -> LightSteelBrush
        }

    val content: Color
        @Composable @ReadOnlyComposable
        get() = when {
            this == GOLD -> GoldInk
            GoaTheme.isDark -> SteelInk
            else -> LightSteelInk
        }

    val edge: Color
        @Composable @ReadOnlyComposable
        get() = if (this == STEEL && !GoaTheme.isDark) LightSteelEdge else ToneEdge
}

private val GoldBrush =
    Brush.verticalGradient(0f to Color(0xFFE4C47E), 0.55f to Color(0xFFC9A35B), 1f to Color(0xFFA98744))
private val GoldInk = Color(0xFF0B1622)
private val SteelBrush = Brush.verticalGradient(listOf(Color(0xFF34424B), Color(0xFF222C33)))
private val SteelInk = Color(0xFFE8E2D6)
private val LightSteelBrush = Brush.verticalGradient(listOf(Color(0xFFE8ECEE), Color(0xFFD5DCE0)))
private val LightSteelInk = Color(0xFF16222B)
private val LightSteelEdge = Color(0xFF8B979F)

private val ToneShape = RoundedCornerShape(7.dp)
private val ToneEdge = Color(0xFF0E1215)
private const val TONE_DISABLED = 0.45f

/** A minion's type on its small round badge, as on the board: the team's dark tone, the type's glyph in white. */
@Composable
internal fun MinionTypeBadge(piece: MinionPiece, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val r = this.size.minDimension / 2f
        drawCircle(if (piece.team == Team.RED) TypeBadgeRed else TypeBadgeBlue)
        drawCircle(Color.White, radius = r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
        drawGlyph(BoardGlyph.of(piece.type), center, r * 1.5f, Color.White)
    }
}

private val TypeBadgeRed = Color(0xFF7A1D14)
private val TypeBadgeBlue = Color(0xFF0D3A66)

/** A segmented button's colours: bronze is selected (teal is next, correct, "you can act"). */
@Composable
internal fun bronzeSegments(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    activeBorderColor = MaterialTheme.colorScheme.primary,
)

/** A filled button in a [PlateTone], with [Button]'s behaviour. */
@Composable
internal fun PlateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: PlateTone = PlateTone.GOLD,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ToneShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = tone.content,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = tone.content.copy(alpha = 0.6f),
        ),
        elevation = null,
        contentPadding = contentPadding,
        border = BorderStroke(1.dp, tone.edge),
        modifier = modifier.background(tone.brush, ToneShape, alpha = if (enabled) 1f else TONE_DISABLED),
        content = content,
    )
}

/** A [Surface] in a [PlateTone], for answers with more than a label (an action chip, a "Choose one" option). */
@Composable
internal fun PlateToneSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: PlateTone = PlateTone.STEEL,
    enabled: Boolean = true,
    border: BorderStroke? = BorderStroke(1.dp, tone.edge),
    content: @Composable () -> Unit,
) {
    val ink = tone.content
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ToneShape,
        color = Color.Transparent,
        contentColor = ink,
        border = border,
        modifier = modifier.background(tone.brush, ToneShape, alpha = if (enabled) 1f else TONE_DISABLED),
    ) { CompositionLocalProvider(LocalContentColor provides ink, content = content) }
}

/**
 * The app's own words in a text box (a question, a hint, a note): Inter. Crimson is for what the game prints, and the
 * game says to follow card text exactly (p.3), so words of ours must not look printed.
 */
@Composable
@ReadOnlyComposable
internal fun boxText(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp)

/** [boxText] for a note. */
@Composable
@ReadOnlyComposable
internal fun smallBoxText(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp)

/**
 * A [CardTextBox] whose tab is a heading (TalkBack can jump to it), for a box that stands on its own: a nudge.
 * The word sits on the box's own coloured tab, as ATTACK or COACH do, never on its rim.
 */
@Composable
internal fun HeadedTextBox(
    color: PlateColor,
    heading: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CardTextBox(color, modifier, cap = heading, capHeading = true, content = content)
}

/** The team's mark on the dark badge the mock-ups put at a portrait's foot: team is never colour alone. */
@Composable
internal fun TeamBadge(team: Team, modifier: Modifier = Modifier, size: Dp = 17.dp) {
    Box(
        modifier
            .size(size)
            .background(BadgeGround, CircleShape)
            .border(1.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center,
    ) { TeamMark(team, markSize = size * BADGE_MARK) }
}

/** A [TeamBadge] hung on the bottom end of a portrait in a Box: half of its overhang outside. */
internal fun Modifier.badgeOverhang(size: Dp): Modifier = offset(x = size * BADGE_OVERHANG, y = size * BADGE_OVERHANG)

private val BadgeGround = Color(0xFF10181E)
private const val BADGE_MARK = 0.62f
private const val BADGE_OVERHANG = 0.28f
