package dev.quinton.goa.feature.board.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
import dev.quinton.goa.core.designsystem.component.LocalBoardStyle
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.assetUrl
import dev.quinton.goa.core.designsystem.component.rememberOfficialBitmap
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.BoardStyle
import dev.quinton.goa.feature.board.BoardHero

/** How a piece looks: on a map with no painting as drawn, on a painting as the board style says. */
enum class PieceLook { DRAWN, DISC, MINIATURE }

/** The look of a piece on the board, or, by default, off it (the tray, sheets), as the device's board style says. */
@Composable
@ReadOnlyComposable
fun pieceLook(painted: Boolean = true): PieceLook = when {
    !painted -> PieceLook.DRAWN
    LocalBoardStyle.current == BoardStyle.MINIATURES -> PieceLook.MINIATURE
    else -> PieceLook.DISC
}

/** The board style's hex grid over the painting. */
@Composable
@ReadOnlyComposable
fun showsGrid(): Boolean = LocalBoardStyle.current == BoardStyle.GRID_AND_DISCS

/**
 * A piece as it stands on the board, [size] across: a hero's official portrait in a ring of its
 * team's colour, a minion by [look] (a team disc with its type's glyph, or the rulebook's miniature on
 * a team-coloured base with a badge of that glyph), a token as a labelled square. Team is never colour
 * alone: a hero and a disc carry the team's shape, and each team's miniatures are their own sculpts.
 * [onBoard]: it stands on a space, so a figure may rise past [size] as a miniature does on the mat; off
 * the board everything stays inside [size]. Decorative: the space under it carries the description.
 */
@Composable
fun PieceToken(
    piece: BoardPiece,
    hero: BoardHero?,
    size: Dp,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
    look: PieceLook = pieceLook(),
    onBoard: Boolean = false,
) {
    val figure = look == PieceLook.MINIATURE && piece is MinionPiece
    val lift = when {
        !lifted -> Modifier
        figure -> Modifier.scale(LIFT_SCALE)
        else -> Modifier.scale(LIFT_SCALE).shadow(size * 0.2f, CircleShape)
    }
    Box(modifier.size(size).then(lift).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        when (piece) {
            is HeroPiece -> if (look == PieceLook.DRAWN) {
                HeroDisc(piece, hero, size, palette)
            } else {
                HeroCoin(piece, hero, if (onBoard) size * COIN_ON_BOARD else size)
            }

            is MinionPiece -> when (look) {
                PieceLook.DRAWN -> MinionDisc(piece, palette)
                PieceLook.DISC -> PaintedDisc(piece, onBoard)
                PieceLook.MINIATURE -> Miniature(piece, onBoard)
            }

            is TokenPiece -> TokenSquare(piece, size, if (look == PieceLook.DRAWN) palette else ArtBoardPalette)
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
            .background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, GoaTheme.colors.medallion)),
            ),
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

/**
 * A hero on the painted board (mock-ups 01–03), [size] across: the portrait in dark, team and dark
 * rings, with the official gear or star on a dark badge at its lower right. It is as wide as a hex at
 * most, so it never covers a neighbour's glyph.
 */
@Composable
private fun HeroCoin(piece: HeroPiece, hero: BoardHero?, size: Dp) {
    val ring = ArtBoardPalette.team(piece.team)
    val badge = rememberOfficialBitmap(OfficialArt.heroSpawn(piece.team))
    Box(Modifier.requiredSize(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(BADGE_GROUND)
                .padding(size * 0.03f)
                .border(size * 0.06f, ring, CircleShape)
                .padding(size * 0.08f)
                .clip(CircleShape)
                .background(PORTRAIT_GROUND),
            contentAlignment = Alignment.Center,
        ) {
            PiecePortrait(piece.heroId, hero?.portrait)
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            drawTeamBadge(piece.team, badge, Offset(w * 0.84f, w * 0.84f), w * 0.21f)
        }
    }
}

/** The official gear or star on a dark round badge; the team's plain shape until it has loaded. */
private fun DrawScope.drawTeamBadge(team: Team, image: ImageBitmap?, center: Offset, r: Float) {
    drawCircle(Color.Black, radius = r * 1.1f, center = center)
    drawCircle(BADGE_GROUND, radius = r, center = center)
    if (image != null) {
        drawCentred(image, center, r * 1.74f, r * 1.74f)
    } else {
        drawTeamMark(team, center, r * 1.3f, ArtBoardPalette.team(team))
    }
}

/** [image] fitted into [width] × [height] around [center], in float transforms: it stays true when zoomed. */
private fun DrawScope.drawCentred(
    image: ImageBitmap,
    center: Offset,
    width: Float,
    height: Float,
    filter: ColorFilter? = null,
) {
    val scale = minOf(width / image.width, height / image.height)
    drawScaled(image, center.x - image.width * scale / 2f, center.y - image.height * scale / 2f, scale, filter)
}

private fun DrawScope.drawScaled(
    image: ImageBitmap,
    left: Float,
    top: Float,
    scale: Float,
    filter: ColorFilter? = null,
) {
    withTransform({
        translate(left, top)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawImage(image, colorFilter = filter, filterQuality = FilterQuality.Medium)
    }
}

/**
 * The official portrait, loaded big enough to stay sharp when zoomed in; the hero's sigil until (or
 * unless) it loads.
 */
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

/** A minion disc on the painting (mock-up 02): the team's disc and glyph, the official gear or star on a dark badge. */
@Composable
private fun PaintedDisc(piece: MinionPiece, onBoard: Boolean) {
    val badge = rememberOfficialBitmap(OfficialArt.heroSpawn(piece.team))
    Canvas(Modifier.fillMaxSize()) {
        drawPaintedDisc(
            piece,
            badge,
            size.width / if (onBoard) PIECE_UNITS else DISC_OFF_BOARD_UNITS,
        )
    }
}

private fun DrawScope.drawPaintedDisc(piece: MinionPiece, badge: ImageBitmap?, u: Float) {
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Color.Black.copy(alpha = 0.5f), radius = u * 0.72f, center = c + Offset(0f, u * 0.08f))
    drawCircle(ArtBoardPalette.halo, radius = u * 0.72f, center = c)
    drawCircle(if (piece.team == Team.RED) DISC_RED else DISC_BLUE, radius = u * 0.65f, center = c)
    drawGlyph(BoardGlyph.of(piece.type), c, u * 0.85f, ArtBoardPalette.onTeam)
    drawTeamBadge(piece.team, badge, c + Offset(u * 0.58f, u * 0.58f), u * 0.34f)
}

/**
 * A minion as the rulebook's miniature, on a small team-coloured base, with a round badge of its type's
 * glyph at its lower right (mock-up 09 B); the Heavy clearly larger. Until the image has loaded (and if it
 * fails) it is a disc. Off the board the figure is scaled to stay inside the piece's box.
 */
@Composable
private fun Miniature(piece: MinionPiece, onBoard: Boolean) {
    val image = rememberOfficialBitmap(LookArt.miniature(piece.type, piece.team))
    val badge = rememberOfficialBitmap(OfficialArt.heroSpawn(piece.team))
    Canvas(Modifier.fillMaxSize()) {
        val box = size.minDimension
        if (image == null) {
            drawPaintedDisc(piece, badge, box / if (onBoard) PIECE_UNITS else DISC_OFF_BOARD_UNITS)
            return@Canvas
        }
        if (onBoard) {
            // The boards draw the badge above every figure: MiniatureBadge.
            drawFigure(piece, image, Offset(size.width / 2f, size.height / 2f), box / PIECE_UNITS)
        } else {
            val u = box / FIGURE_OFF_BOARD_UNITS
            val c = Offset(size.width / 2f - u * 0.1f, size.height / 2f + u * FIGURE_LIFT)
            drawFigure(piece, image, c, u)
            drawTypeBadge(piece, c, u)
        }
    }
}

/** The figure standing on the space centred at [c]; [u] = px per hex radius. */
private fun DrawScope.drawFigure(piece: MinionPiece, image: ImageBitmap, c: Offset, u: Float) {
    val ring = ArtBoardPalette.team(piece.team)
    val base = c + Offset(0f, u * BASE_Y)
    val baseSize = Size(u * BASE_WIDTH, u * BASE_HEIGHT)
    val baseTopLeft = base - Offset(baseSize.width / 2f, baseSize.height / 2f)
    drawOval(ring.copy(alpha = 0.35f), baseTopLeft, baseSize, style = Stroke(width = u * 0.2f))
    drawOval(BASE_SHADE, baseTopLeft, baseSize)
    drawOval(ring, baseTopLeft, baseSize, style = Stroke(width = u * 0.08f))

    val height = u * if (piece.type == MinionType.HEAVY) HEAVY_HEIGHT else FIGURE_HEIGHT
    val scale = height / image.height
    val left = c.x - image.width * scale / 2f
    val top = c.y + u * FIGURE_FOOT - height
    // A dark edge, four offset copies: the figure's outline holds on sand as on lava. No blur to pay for.
    val edge = u * 0.035f
    listOf(Offset(edge, 0f), Offset(-edge, 0f), Offset(0f, edge), Offset(0f, -edge)).forEach { d ->
        drawScaled(image, left + d.x, top + d.y, scale, FigureEdge)
    }
    drawScaled(image, left, top + u * 0.05f, scale, FigureEdge)
    drawScaled(image, left, top, scale)
}

/** The round badge of a miniature's type at its lower right: dark team tone, white hairline, the glyph in white. */
private fun DrawScope.drawTypeBadge(piece: MinionPiece, c: Offset, u: Float) {
    val badgeAt = c + Offset(u * BADGE_X, u * BADGE_Y)
    val r = u * BADGE_RADIUS
    drawCircle(Color.Black.copy(alpha = 0.55f), radius = r * 1.12f, center = badgeAt + Offset(0f, u * 0.04f))
    drawCircle(if (piece.team == Team.RED) BADGE_RED else BADGE_BLUE, radius = r, center = badgeAt)
    drawCircle(Color.White, radius = r, center = badgeAt, style = Stroke(width = u * 0.05f))
    drawGlyph(BoardGlyph.of(piece.type), badgeAt, r * 1.5f, Color.White)
}

/**
 * A miniature's type badge on the board, as a layer of its own: figures overlap their neighbours, and a
 * badge is how a player reads a figure, so the boards draw every badge above every figure.
 */
@Composable
fun MiniatureBadge(piece: MinionPiece, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        drawTypeBadge(piece, Offset(this.size.width / 2f, this.size.height / 2f), this.size.minDimension / PIECE_UNITS)
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
        // Sized to the piece, not the font scale: the token must keep its label inside, and a word
        // is never split across lines ("Turr / et" on the game table): the longest word sets the size.
        val em = tokenEm(piece.label)
        Text(
            piece.label.take(TOKEN_CHARS),
            color = palette.onToken,
            fontSize = (size.value * em / fontScale).sp,
            lineHeight = (size.value * em * LINE / fontScale).sp,
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
    Canvas(modifier.size(size)) {
        drawTeamMark(
            team,
            Offset(this.size.width / 2f, this.size.height / 2f),
            this.size.minDimension,
            palette.team(team),
        )
    }
}

private const val PORTRAIT_PX = 256
private const val LIFT_SCALE = 1.18f
private const val TOKEN_CHARS = 16

/** A piece's box in hex radii (PIECE_SIZE): its size tells a canvas how large a hex is. */
private const val PIECE_UNITS = 1.46f

/** A hero's coin on the board against the piece's box: a hex's width across its flats (√3 of 1.46). */
private const val COIN_ON_BOARD = 1.18f

/** Hex radii in the box of a disc off the board: the badge stays inside. */
private const val DISC_OFF_BOARD_UNITS = 2.2f

/** Mock-up 01 on a hex of radius 20: a figure 31 px tall (the Heavy 38), its foot 12 px below the centre. */
private const val FIGURE_HEIGHT = 1.55f
private const val HEAVY_HEIGHT = 2.0f
private const val FIGURE_FOOT = 0.6f
private const val BASE_Y = 0.45f
private const val BASE_WIDTH = 1.5f
private const val BASE_HEIGHT = 0.65f
private const val BADGE_X = 0.68f
private const val BADGE_Y = 0.53f
private const val BADGE_RADIUS = 0.37f

/** Hex radii in the box of a figure off the board, and how far down it shifts so a Heavy's head stays inside. */
private const val FIGURE_OFF_BOARD_UNITS = 2.75f
private const val FIGURE_LIFT = 0.65f

private val BADGE_GROUND = Color(0xFF10181E)
private val PORTRAIT_GROUND = Color(0xFF15252F)
private val BADGE_RED = Color(0xFF7A1D14)
private val BADGE_BLUE = Color(0xFF0D3A66)
private val DISC_RED = Color(0xFFD9574B)
private val DISC_BLUE = Color(0xFF3F7FD6)
private val BASE_SHADE = Color(0xA6000000)
private val FigureEdge = ColorFilter.tint(Color(0xD9000000))

/** The label's font size as a share of the piece: as large as fits its longest word on one line. */
internal fun tokenEm(label: String): Float {
    val longest = label.take(TOKEN_CHARS).split(' ').maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
    return (TOKEN_WIDTH / (CHAR_EM * longest)).coerceIn(MIN_EM, MAX_EM)
}

/** The token's inner width (0.82 of the piece, less padding), as a share of the piece. */
private const val TOKEN_WIDTH = 0.66f

/** An average bold character's width, in em. */
private const val CHAR_EM = 0.66f
private const val MIN_EM = 0.14f
private const val MAX_EM = 0.27f
private const val LINE = 1.1f
