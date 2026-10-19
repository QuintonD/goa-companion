package dev.quinton.goa.feature.draft.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.draft.teamGlyph
import dev.quinton.goa.core.presentation.draft.teamName

/**
 * The Tie Breaker coin. Each new [flipCount] spins it (two 300 ms half-turns per landing, snaps
 * when animations are off) and lands on [result]; null shows a neutral bronze face.
 */
@Composable
internal fun TieBreakerCoin(result: Team?, flipCount: Int, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val reduced = rememberReducedMotion()
    val rotation = remember { Animatable(if (result == Team.BLUE) 180f else 0f) }
    LaunchedEffect(flipCount, result) {
        if (result == null) return@LaunchedEffect
        val landing = if (result == Team.RED) 0f else 180f
        val base = (rotation.value / 360f).toInt() * 360f
        val target = base + 360f * 2 + landing
        if (reduced || flipCount == 0) {
            rotation.snapTo(landing)
        } else {
            rotation.animateTo(
                target,
                tween(durationMillis = DraftMotion.MEDIUM * 4, easing = FastOutSlowInEasing),
            )
        }
    }
    val angle = ((rotation.value % 360f) + 360f) % 360f
    val face = when {
        result == null -> null
        angle < 90f || angle > 270f -> Team.RED
        else -> Team.BLUE
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 12f * density
            }
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = when (result) {
                    null -> "Tie Breaker coin, not flipped yet"
                    else -> "Tie Breaker coin shows ${teamName(result)}. ${teamName(result)} is Team A"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CoinFace(face, mirrored = face == Team.BLUE, size = size)
    }
}

@Composable
private fun CoinFace(team: Team?, mirrored: Boolean, size: Dp) {
    val bronze = MaterialTheme.colorScheme.primary
    val accent = team?.let { teamColor(it) } ?: bronze
    Box(
        Modifier
            .size(size)
            .graphicsLayer { if (mirrored) rotationY = 180f }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    // 0.22 keeps the team glyph at >= 3:1 on the face in both themes (WCAG non-text contrast).
                    listOf(accent.copy(alpha = 0.22f), MaterialTheme.colorScheme.surface),
                ),
            )
            .border(6.dp, bronze, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size - 28.dp)
                .border(1.dp, bronze.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = team?.let { teamGlyph(it) } ?: "?",
                    color = accent,
                    fontSize = (size.value * 0.34f).sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = team?.let { teamName(it).uppercase() } ?: "TIE BREAKER",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
