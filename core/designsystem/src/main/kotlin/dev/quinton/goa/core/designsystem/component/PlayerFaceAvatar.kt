package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import dev.quinton.goa.core.designsystem.icon.PlayerCrests
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.player.PlayerFace

/**
 * Each player's most-played hero by player id (`PlayerFaces`, derived from the match log, never stored or synced): the
 * small badge on their crest ([PlayerAvatar] with `showFace`). Without it the crest stands alone.
 */
val LocalPlayerFaces = compositionLocalOf<Map<String, PlayerFace>> { emptyMap() }

/**
 * A hero portrait in a steel ring: the official portrait, cropped round, inside a lit steel band with a dark edge (the
 * podium's steel). The most-played hero badge on a crest ([PlayerAvatar]). Decorative.
 */
@Composable
fun HeroFaceAvatar(face: PlayerFace, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val ring = max(2.dp, size * RING_SHARE)
    Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        HeroPortrait(
            face.heroId,
            face.portrait,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(ring * INSET_SHARE),
            shape = CircleShape,
            framed = false,
        )
        Canvas(Modifier.fillMaxSize()) {
            val band = ring.toPx()
            val edge = 1.dp.toPx()
            val radius = this.size.minDimension / 2f
            // The band, lit from the top left like the crests' metal.
            drawCircle(
                Brush.linearGradient(
                    listOf(STEEL_LIGHT, STEEL_MID, STEEL_DARK),
                    start = Offset.Zero,
                    end = Offset(this.size.width, this.size.height),
                ),
                radius = radius - band / 2f,
                style = Stroke(band),
            )
            // Dark edges outside and inside the band, so it holds on cream paper and on navy alike.
            drawCircle(STEEL_EDGE, radius = radius - edge / 2f, style = Stroke(edge))
            drawCircle(STEEL_EDGE.copy(alpha = INNER_EDGE_ALPHA), radius = radius - band, style = Stroke(edge))
        }
    }
}

/**
 * An online account (ADR-056): a crest of its own, picked from the 24 by its account id so it stays the same
 * everywhere (accounts choose no crest), with its most-played hero as the badge when one is known, like a group
 * player's [PlayerAvatar].
 */
@Composable
fun AccountAvatar(
    id: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    face: PlayerFace? = null,
) {
    val account = Player(id = id, name = name, color = 0, avatarId = accountCrest(id), createdAt = 0)
    CompositionLocalProvider(LocalPlayerFaces provides (face?.let { mapOf(id to it) } ?: emptyMap())) {
        PlayerAvatar(account, modifier, size, showFace = face != null)
    }
}

/** The crest index an online account shows: stable for its id. */
fun accountCrest(id: String): Int = Math.floorMod(id.hashCode(), PlayerCrests.count)

/** Below this the hero badge would be too small to recognise: [PlayerAvatar] draws the crest alone. */
val FACE_MIN_SIZE = 40.dp

/** The hero badge's diameter as a share of the crest's box. */
internal const val FACE_BADGE_SHARE = 0.46f

private const val RING_SHARE = 0.075f

/** The portrait tucks a little under the band, so no sliver of backdrop shows between them. */
private const val INSET_SHARE = 0.8f
private const val INNER_EDGE_ALPHA = 0.7f

private val STEEL_LIGHT = Color(0xFFEEF2F5)
private val STEEL_MID = Color(0xFFB7C1CA)
private val STEEL_DARK = Color(0xFF6E7B87)
private val STEEL_EDGE = Color(0xFF15181C)
