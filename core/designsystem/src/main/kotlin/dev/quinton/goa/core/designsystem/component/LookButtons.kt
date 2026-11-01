package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaTheme

/*
 * The look's buttons (ADR-053): metal, as the mock-ups draw them. These colours are the same in both
 * themes: the words are dark on bronze and light on steel, checked in ContrastTest.
 */

private val METAL_SHAPE = RoundedCornerShape(7.dp)

/**
 * A metal face for a button: bronze for the main action, steel otherwise. Its top edge catches the light.
 */
private fun Modifier.metalFace(bronze: Boolean, enabled: Boolean): Modifier = drawWithCache {
    val fill = when {
        !enabled -> Brush.verticalGradient(listOf(STEEL_LOW, STEEL_LOW))
        bronze -> Brush.verticalGradient(0f to BRONZE_TOP, BRONZE_MID_AT to BRONZE_MID, 1f to BRONZE_LOW)
        else -> Brush.verticalGradient(listOf(STEEL_TOP, STEEL_LOW))
    }
    val radius = CornerRadius(7.dp.toPx())
    val edge = 1.dp.toPx()
    val sheen = Color.White.copy(alpha = if (bronze) BRONZE_SHEEN else STEEL_SHEEN)
    onDrawBehind {
        drawRoundRect(fill, cornerRadius = radius)
        drawRoundRect(EDGE_DARK, cornerRadius = radius, style = Stroke(edge))
        if (enabled) {
            drawLine(
                sheen,
                Offset(radius.x, edge * 1.5f),
                Offset(size.width - radius.x, edge * 1.5f),
                strokeWidth = edge,
            )
        }
    }
}

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
            disabledContentColor = STEEL_INK.copy(alpha = DISABLED_INK),
        ),
        modifier = modifier.metalFace(bronze, enabled),
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
            disabledContentColor = STEEL_INK.copy(alpha = DISABLED_INK),
        ),
        modifier = modifier.slatePlate(METAL_SHAPE, brackets = false),
        content = content,
    )
}

/** A word as a top-bar action: light with a dark edge, since bronze is lost on the steel banner. */
@Composable
fun BannerTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = GoaTheme.colors
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.onBanner,
            disabledContentColor = colors.onBannerVariant.copy(alpha = BANNER_DISABLED),
        ),
    ) {
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
private const val DISABLED_INK = 0.5f
private const val BANNER_DISABLED = 0.6f
