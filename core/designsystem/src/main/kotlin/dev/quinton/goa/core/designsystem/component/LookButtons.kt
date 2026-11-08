package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing

/*
 * The look's buttons (ADR-053): metal, as the mock-ups draw them. These colours are the same in both
 * themes: the words are dark on bronze and light on steel, checked in ContrastTest.
 */

private val METAL_SHAPE = RoundedCornerShape(7.dp)

/**
 * A metal face for a button: bronze for the main action, steel otherwise. Its top edge catches the light.
 * Disabled, a flat, unlit slate ([off]) with a visible edge: the same shape, clearly off, never a faded bronze.
 */
private fun Modifier.metalFace(bronze: Boolean, off: DisabledFace?): Modifier = drawWithCache {
    val fill = when {
        off != null -> Brush.verticalGradient(listOf(off.fill, off.fill))
        bronze -> Brush.verticalGradient(0f to BRONZE_TOP, BRONZE_MID_AT to BRONZE_MID, 1f to BRONZE_LOW)
        else -> Brush.verticalGradient(listOf(STEEL_TOP, STEEL_LOW))
    }
    val radius = CornerRadius(7.dp.toPx())
    val edge = 1.dp.toPx()
    val sheen = Color.White.copy(alpha = if (bronze) BRONZE_SHEEN else STEEL_SHEEN)
    onDrawBehind {
        drawRoundRect(fill, cornerRadius = radius)
        drawRoundRect(off?.edge ?: EDGE_DARK, cornerRadius = radius, style = Stroke(edge))
        if (off == null) {
            drawLine(
                sheen,
                Offset(radius.x, edge * 1.5f),
                Offset(size.width - radius.x, edge * 1.5f),
                strokeWidth = edge,
            )
        }
    }
}

/**
 * A disabled button's face, edge and words, per theme: a filled, unlit slab (never an outline, which would pass for a
 * text field), its words muted but legible (≥ 4.5:1 on the face, ContrastTest). Every disabled button wears it.
 */
class DisabledFace internal constructor(val fill: Color, val edge: Color, val ink: Color) {
    companion object {
        /** On the slate and the dark canvas. */
        val Dark = DisabledFace(fill = Color(0xFF333C43), edge = Color(0xFF55616A), ink = Color(0xFFA9B2B9))

        /** On the light theme's paper. */
        val Light = DisabledFace(fill = Color(0xFFD9D2C5), edge = Color(0xFF8C8475), ink = Color(0xFF5C554A))

        /** The face for the current theme. */
        val current: DisabledFace
            @Composable get() = if (GoaTheme.isDark) Dark else Light
    }
}

/**
 * Why a button is off, said right under it ("Name the group to start it."): an info glyph and a short line in the
 * muted tone. Show it only while the button is disabled.
 */
@Composable
fun DisabledReason(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, end = Spacing.xs).size(scaledIconSize(16.dp, 1.4f)),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Bronze for an icon on a steel face (an action's glyph beside its light words): the bright top of the bronze, so the
 * glyph keeps up with the words (≥ 6:1 on the steel, ContrastTest). The steel is the same in both themes, so is this.
 */
val SteelIconBronze: Color = Color(0xFFE4C47E)

/** A metal button: filled bronze with a sheen for the main action, steel for the other. */
@Composable
fun MetalButton(
    onClick: () -> Unit,
    bronze: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val off = if (enabled) null else DisabledFace.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = METAL_SHAPE,
        contentPadding = contentPadding,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = if (bronze) INK else STEEL_INK,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = off?.ink ?: STEEL_INK,
        ),
        modifier = modifier.metalFace(bronze, off),
        content = content,
    )
}

/** The app's filled button: bronze metal. Use it instead of Material's `Button`. */
@Composable
fun GoaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    MetalButton(onClick, bronze = true, modifier, enabled, contentPadding, content)
}

/** A secondary button over art: a small slate plate, so its words keep their contrast on the art. */
@Composable
fun SlateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ButtonWithIconContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    // Over art in both themes: the slate, never the light theme's paper.
    GoaDarkTheme {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = METAL_SHAPE,
            contentPadding = contentPadding,
            border = null,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = STEEL_INK,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = STEEL_INK,
            ),
            modifier = modifier.disabled(enabled).slatePlate(METAL_SHAPE, brackets = false),
            content = content,
        )
    }
}

/**
 * The app's secondary button: a slate plate with a steel bevel (the light theme's paper with the same steel), words
 * in the theme's ink; the bronze [GoaButton] stays the screen's one main action. Disabled it becomes the filled,
 * unlit [DisabledFace] every disabled button wears, so it never passes for the outlined text field beside it; say
 * why under it with [DisabledReason]. Use it instead of Material's `OutlinedButton`.
 */
@Composable
fun GoaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val off = if (enabled) null else DisabledFace.current
    val steel = ControlSteel.current
    // A solid face (the button's own shape), not a drawn one: the touch target around a short button stays clear.
    val face = if (GoaTheme.isDark) SLATE_FACE else PAPER_FACE
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = METAL_SHAPE,
        contentPadding = contentPadding,
        border = if (off == null) BorderStroke(1.dp, steel.bevel()) else null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = face,
            contentColor = ink,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = off?.ink ?: ink,
        ),
        modifier = if (off == null) modifier else modifier.metalFace(bronze = false, off = off),
        content = content,
    )
}

/**
 * A word as a top-bar action: light with a dark edge, since bronze is lost on the steel banner. With [icon], the
 * glyph leads the word (a destination named in full, "Group sync", where the glyph alone could be misread); [badge]
 * puts the attention dot on it.
 */
@Composable
fun BannerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    badge: Boolean = false,
) {
    val colors = GoaTheme.colors
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = if (icon != null) {
            ButtonDefaults.TextButtonWithIconContentPadding
        } else {
            ButtonDefaults.TextButtonContentPadding
        },
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.onBanner,
            disabledContentColor = colors.onBannerVariant.copy(alpha = BANNER_DISABLED),
        ),
    ) {
        if (icon != null) {
            if (badge) {
                BadgedBox(badge = { Badge() }) { Icon(icon, contentDescription = null, Modifier.size(20.dp)) }
            } else {
                Icon(icon, contentDescription = null, Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                shadow = Shadow(Color.Black, Offset(0f, 1f), blurRadius = 3f),
            ),
        )
    }
}

private val INK = Color(0xFF0B1622)
private val STEEL_INK = Color(0xFFE8E2D6)
private val STEEL_TOP = Color(0xFF34424B)
private val STEEL_LOW = Color(0xFF222C33)
private val BRONZE_TOP = Color(0xFFE4C47E)
private val BRONZE_MID = Color(0xFFC9A35B)
private val BRONZE_LOW = Color(0xFFA98744)
private val EDGE_DARK = Color(0xFF15191B)
private const val BRONZE_MID_AT = 0.55f
private const val BRONZE_SHEEN = 0.6f
private const val STEEL_SHEEN = 0.18f

/** A disabled button: its own shape and colours, faded as a whole (Material's 38 %, a little more on the slate). */
const val DISABLED_ALPHA = 0.42f

/** Fades a disabled control as a whole, so it keeps its shape and reads as the button it will become. */
fun Modifier.disabled(enabled: Boolean): Modifier = if (enabled) this else alpha(DISABLED_ALPHA)
private const val BANNER_DISABLED = 0.6f
private val SLATE_FACE = Color(0xFF242B30)
private val PAPER_FACE = Color(0xFFFAF7F0)
