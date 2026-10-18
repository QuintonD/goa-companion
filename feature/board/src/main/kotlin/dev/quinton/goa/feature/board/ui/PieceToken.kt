package dev.quinton.goa.feature.board.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.quinton.goa.core.designsystem.component.HeroSigil
import dev.quinton.goa.core.designsystem.component.assetUrl
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero

/**
 * A piece as it stands on the board, [size] across: a hero's official portrait in a ring of its
 * team's colour, a minion as a team disc with its type's glyph, a token as a labelled square.
 * Heroes and minions carry the team's shape (◆ Red / ● Blue) so team is never colour alone.
 * Decorative: the space under it carries the description.
 */
@Composable
fun PieceToken(
    piece: BoardPiece,
    hero: BoardHero?,
    size: Dp,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
) {
    val lift = if (lifted) Modifier.scale(LIFT_SCALE).shadow(size * 0.2f, CircleShape) else Modifier
    Box(modifier.size(size).then(lift).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        when (piece) {
            is HeroPiece -> HeroDisc(piece, hero, size, palette)
            is MinionPiece -> MinionDisc(piece, palette)
            is TokenPiece -> TokenSquare(piece, size, palette)
        }
    }
}

@Composable
private fun HeroDisc(piece: HeroPiece, hero: BoardHero?, size: Dp, palette: BoardPalette) {
    val ring = palette.team(piece.team)
    val ringWidth = size * 0.1f
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(palette.halo)
            .padding(size * 0.03f)
            .border(ringWidth, ring, CircleShape)
            .padding(ringWidth)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, GoaTheme.colors.medallion))),
        contentAlignment = Alignment.Center,
    ) {
        PiecePortrait(piece.heroId, hero?.portrait)
    }
    // The team's shape, bottom right, on a halo so it reads on any portrait.
    Canvas(Modifier.fillMaxSize()) {
        val mark = this.size.minDimension * 0.34f
        val center = Offset(this.size.width - mark * 0.55f, this.size.height - mark * 0.55f)
        drawTeamMark(piece.team, center, mark, ring, halo = palette.halo)
    }
}

/** The official portrait, loaded big enough to stay sharp when zoomed in; the hero's sigil until (or unless) it loads. */
@Composable
private fun PiecePortrait(heroId: String, portrait: String?) {
    var loaded by remember(portrait) { mutableStateOf(false) }
    if (portrait == null || !loaded) {
        HeroSigil(heroId, Modifier.fillMaxSize().padding(4.dp), tint = MaterialTheme.colorScheme.primary)
    }
    if (portrait != null) {
        val context = LocalPlatformContext.current
        val request = remember(portrait, context) {
            ImageRequest.Builder(context).data(assetUrl(portrait)).size(PORTRAIT_PX).build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { loaded = true },
            onError = { loaded = false },
        )
    }
}

@Composable
private fun MinionDisc(piece: MinionPiece, palette: BoardPalette) {
    val color = palette.team(piece.team)
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(palette.halo, radius = r * 0.86f, center = c)
        drawCircle(color, radius = r * 0.78f, center = c)
        drawCircle(palette.halo.copy(alpha = 0.35f), radius = r * 0.66f, center = c, style = Stroke(width = r * 0.05f))
        drawGlyph(BoardGlyph.of(piece.type), c, r * 0.98f, palette.onTeam)
        val mark = r * 0.5f
        drawTeamMark(piece.team, Offset(c.x + r * 0.6f, c.y + r * 0.6f), mark, color, halo = palette.halo)
    }
}

@Composable
private fun TokenSquare(piece: TokenPiece, size: Dp, palette: BoardPalette) {
    val fontScale = LocalDensity.current.fontScale
    val shape = RoundedCornerShape(size * 0.16f)
    val edge = piece.team?.let(palette::team) ?: palette.halo
    Box(
        Modifier
            .size(size * 0.82f)
            .background(palette.token, shape)
            .border(size * 0.06f, edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            piece.label.take(TOKEN_CHARS),
            color = palette.onToken,
            // Sized to the piece, not the font scale: the token must keep its label inside.
            fontSize = (size.value * 0.27f / fontScale).sp,
            lineHeight = (size.value * 0.3f / fontScale).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = size * 0.04f),
        )
    }
}

/** A team's shape as a small composable (lists, tray). */
@Composable
fun TeamShape(team: Team, size: Dp, palette: BoardPalette, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawTeamMark(team, Offset(this.size.width / 2f, this.size.height / 2f), this.size.minDimension, palette.team(team)) }
}

private const val PORTRAIT_PX = 256
private const val LIFT_SCALE = 1.18f
private const val TOKEN_CHARS = 8
