package dev.quinton.goa.web.play.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardMapArt
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.BoardStyle
import dev.quinton.goa.feature.board.map.ART_FEATHER
import dev.quinton.goa.feature.board.map.DIM_BRIGHTNESS
import dev.quinton.goa.feature.board.map.DIM_SATURATION
import dev.quinton.goa.feature.board.map.HexLayout
import dev.quinton.goa.feature.board.map.LitArea
import dev.quinton.goa.feature.board.map.MapLighting
import dev.quinton.goa.feature.board.map.MapPoint
import dev.quinton.goa.feature.board.map.MapRect
import dev.quinton.goa.web.LocalServices
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.ExperimentalComposeWebSvgApi
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Canvas
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.svg.Circle
import org.jetbrains.compose.web.svg.Ellipse
import org.jetbrains.compose.web.svg.G
import org.jetbrains.compose.web.svg.Image
import org.jetbrains.compose.web.svg.Path
import org.jetbrains.compose.web.svg.Rect
import org.jetbrains.compose.web.svg.Svg
import org.jetbrains.compose.web.svg.SvgText
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.Image as HtmlImage
import org.w3c.dom.events.Event

/** The map a board shows: its layout (turned for the screen), the Lanes' Battle Zones, what it opens on. */
class MapScene(
    val map: BoardMap,
    val layout: HexLayout,
    /** The current Battle Zone of each Lane, in map order. */
    val battleZones: List<String>,
    /** The Lane between the hero spawn areas: what the board opens on. */
    val lane: MapRect?,
) {
    /** A single-lane map may crop its sides to show the Lane big; a double-lane map shows its whole region. */
    val laneMayCrop: Boolean get() = map.lanes.size < 2

    val baseMarkup: String by lazy { mapMarkup(map, layout, battleZones.toSet()) }
}

/** How pieces are drawn: on a map with no painting as ever, on a painting as the board style says. */
enum class PieceStyle { DRAWN, DISC, MINIATURE }

/** The device's board style, from the app's settings (Android: LocalBoardStyle). */
@Composable
fun rememberBoardStyle(): BoardStyle {
    val settings = LocalServices.current.data.content.settings
    val current by remember(settings) { settings.settings.map { it.boardStyle } }.collectAsState(BoardStyle.MINIATURES)
    return current
}

/** What a piece looks like. */
sealed interface PieceLook {
    /** A hero: the official portrait in a ring of its team's colour ([initials] until it loads). */
    data class Hero(val portrait: String?, val initials: String) : PieceLook

    data class Minion(val type: MinionType) : PieceLook

    /** A token by its short label (an obstacle), or one of the engine's pieces. */
    data class Token(val label: String) : PieceLook
}

/** A piece on the map. [immune]: ringed with a dashed shield ring (p.13 Heavy minion immunity). */
data class MapPiece(
    val id: String,
    val at: Hex,
    val look: PieceLook,
    val team: Team?,
    val immune: Boolean = false,
    /** Drawn above its neighbours (the selection). */
    val raised: Boolean = false,
)

/** A small word over a piece at a fixed size ("NOW" over the acting hero, ADR-041). */
data class PieceTag(val pieceId: String, val text: String)

/**
 * Dragging pieces (the sandbox): [selected] drags at once; any other piece [pieceAt] a space is lifted
 * by a long press. Dropped on a space: [onDrop] (moves it, or swaps it with the piece there).
 */
class DragPolicy(
    val pieceAt: (Hex) -> String?,
    val selected: () -> String?,
    val onDrop: (pieceId: String, hex: Hex) -> Unit,
)

/** An invisible button over the map for VoiceOver and the keyboard: a piece or a space to act on. */
data class MapNode(val key: String, val label: String, val pressed: Boolean = false, val onActivate: (() -> Unit)?)

/**
 * The board as SVG: crisp at any zoom, the phone's look, over the map's painting once that has loaded
 * ([PaintingLayers]; until then, if it fails, or with none, the drawn map). One finger pans (or drags a
 * piece, see [drag]), two pinch-zoom, a wheel zooms; a tap reports the space under it to [onTap] (true =
 * it did something), and a second tap on the same space when the first did nothing zooms in. [marks] and
 * [lines] are the overlays, [pieces] glide from space to space. On a painting the spaces marks offer take
 * the light, with [litAlso] (the mover's own space). [nodes] are what VoiceOver reads and activates (the
 * map itself is one image to it).
 */
@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
fun BoardSvg(
    scene: MapScene,
    camera: MapCamera,
    pieces: List<MapPiece>,
    marks: List<HexMark>,
    onTap: (hex: Hex, at: ScreenPoint) -> Boolean,
    label: String,
    lines: List<MapLine> = emptyList(),
    tags: List<PieceTag> = emptyList(),
    drag: DragPolicy? = null,
    nodes: List<MapNode> = emptyList(),
    showLabels: Boolean = true,
    bottomTeam: Team = Team.RED,
    litAlso: List<Hex> = emptyList(),
    attrs: (AttrsScope<HTMLDivElement>.() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val dragPolicy by rememberUpdatedState(drag)
    val currentScene by rememberUpdatedState(scene)
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragAt by remember { mutableStateOf(MapPoint(0f, 0f)) }
    val layout = scene.layout
    val art = scene.map.art
    var painted by remember(art?.image) { mutableStateOf(false) }
    val boardStyle = rememberBoardStyle()
    val style = when {
        !painted -> PieceStyle.DRAWN
        boardStyle == BoardStyle.MINIATURES -> PieceStyle.MINIATURE
        else -> PieceStyle.DISC
    }
    val spotlight = remember(marks, litAlso) { spotlightOf(marks, litAlso) }
    val maxHex = if (art != null) MapCamera.MAX_HEX_ON_ART else MapCamera.MAX_HEX

    Div({
        classes("goa-map")
        if (painted && art != null) {
            classes("painted")
            style { property("background", art.sea) }
        }
        attrs?.invoke(this)
    }) {
        DisposableRefEffect { element ->
            val gestures = MapGestureHandler(
                element = element,
                camera = camera,
                layout = { currentScene.layout },
                onTap = { hex, at -> tap(hex, at) },
                onDoubleTap = { at -> scope.launch { camera.zoomStep(at) } },
                drag = { dragPolicy },
                onDrag = { id, at ->
                    dragging = id
                    if (at != null) dragAt = at
                },
            )
            val resize = observeSize(element) { w, h ->
                camera.setUp(
                    currentScene.layout,
                    w,
                    h,
                    currentScene.lane,
                    currentScene.laneMayCrop,
                    if (currentScene.map.art != null) MapCamera.MAX_HEX_ON_ART else MapCamera.MAX_HEX,
                )
            }
            onDispose {
                gestures.dispose()
                resize()
            }
        }
        // Keep the framing when the layout changes (Flip, rotation) without a resize.
        if (camera.width > 0f) camera.setUp(layout, camera.width, camera.height, scene.lane, scene.laneMayCrop, maxHex)

        val zones = remember(scene) { MapLighting.zones(scene.map, scene.battleZones) }
        val lit = remember(scene, zones, spotlight) {
            if (spotlight.isEmpty()) zones else MapLighting.spaces(scene.map, spotlight)
        }
        if (art != null) PaintingLayers(art, layout, camera, lit, onLoaded = { painted = true })

        Svg(attrs = {
            classes("goa-map-svg")
            attr("role", "img")
            attr("aria-label", label)
        }) {
            Markup(MAP_DEFS, "goa-map-defs")
            G({
                attr(
                    "transform",
                    "translate(${n(camera.pan.x)} ${n(camera.pan.y)}) scale(${camera.scale})",
                )
            }) {
                val grid = boardStyle == BoardStyle.GRID_AND_DISCS
                val base = if (painted) {
                    remember(scene, lit, zones, spotlight, grid) {
                        val zoneLine = if (spotlight.isEmpty()) null else zones
                        paintedMapMarkup(scene.map, layout, scene.battleZones.toSet(), lit, zoneLine, grid)
                    }
                } else {
                    scene.baseMarkup
                }
                Markup(base, "goa-map-base")
                val dropMark = dragging?.let { listOf(HexMark(layout.hexAt(dragAt), MarkStyle.DROP)) }.orEmpty()
                Markup(marksMarkup(layout, marks + dropMark, lines, painted), "goa-map-marks")
                // Figures stand: the one nearer the bottom of the screen in front; the selection above all.
                val ordered = pieces.sortedBy { layout.center(it.at).y }.sortedBy { it.raised || it.id == dragging }
                ordered.forEach { piece ->
                    key(piece.id) {
                        val at = if (piece.id == dragging) dragAt else layout.center(piece.at)
                        PieceSvg(piece, at, lifted = piece.id == dragging, style = style)
                    }
                }
                // A type badge is how a figure is read: every badge above every figure.
                if (style == PieceStyle.MINIATURE) {
                    ordered.forEach { piece ->
                        val look = piece.look as? PieceLook.Minion ?: return@forEach
                        key("badge-${piece.id}") {
                            val at = if (piece.id == dragging) dragAt else layout.center(piece.at)
                            G({
                                classes("goa-piece-badge")
                                if (piece.id == dragging) classes("lifted")
                                style { property("transform", "translate(${n(at.x)}px, ${n(at.y)}px)") }
                            }) { TypeBadge(look.type, piece.team) }
                        }
                    }
                }
            }
        }
        if (showLabels &&
            camera.unit > 0f
        ) {
            ZoneLabels(scene, camera, bottomTeam, busy = pieces.map { it.at } + marks.map { it.hex }, painted = painted)
        }
        tags.forEach { tag ->
            val piece = pieces.firstOrNull { it.id == tag.pieceId } ?: return@forEach
            val at = camera.mapToScreen(layout.center(piece.at))
            Span({
                classes("goa-map-tag")
                attr("aria-hidden", "true")
                style {
                    property("left", "${n(at.x)}px")
                    property("top", "${n(at.y - PIECE_SIZE / 2f * camera.scale)}px")
                }
            }) { Text(tag.text) }
        }
        if (nodes.isNotEmpty()) {
            Div({ classes("goa-visually-hidden") }) {
                nodes.forEach { node ->
                    key(node.key) {
                        Button({
                            attr("type", "button")
                            if (node.pressed) attr("aria-pressed", "true")
                            if (node.onActivate == null) attr("aria-disabled", "true")
                            onClick { node.onActivate?.invoke() }
                        }) { Text(node.label) }
                    }
                }
            }
        }
    }
}

/** What takes the light on a painting: where a piece can go or a question offers; nothing else marked does. */
private fun spotlightOf(marks: List<HexMark>, litAlso: List<Hex>): Set<Hex> {
    val reach = marks.filter { it.style in LIT_STYLES }.map { it.hex }.toSet()
    if (reach.isEmpty()) return emptySet()
    return reach + litAlso + marks.filter { it.style == MarkStyle.SELECTION }.map { it.hex }
}

private val LIT_STYLES = setOf(MarkStyle.MOVEMENT, MarkStyle.FAST_TRAVEL, MarkStyle.TARGET)

/**
 * The painting, as HTML layers under the SVG, not inside it: gestures rewrite the SVG's transform every
 * frame, which repaints an SVG, and an image inside it would jank on an iPhone. Each element here is its
 * own composited layer, moved by the same matrix, so a pan or a pinch only changes a transform: the dimmed
 * picture, baked once into a canvas at the image's size (no filter to run; WebKit dropped a CSS filter on a
 * layer this large), a band that fades its edge into the sea, and the picture in full colour clipped to the
 * [lit] spaces (in the image's own pixels, so a new orientation doesn't touch it). The picture loads on
 * demand (the service worker keeps `official/maps` once seen, it isn't precached); [onLoaded] switches the
 * board over, and if it never comes the drawn map stays.
 */
@Composable
private fun PaintingLayers(art: BoardMapArt, layout: HexLayout, camera: MapCamera, lit: LitArea, onLoaded: () -> Unit) {
    val p = remember(layout, art) { layout.artPlacement(art) }
    val s = camera.scale
    fun matrix(factor: Float) =
        "matrix(${p.a * s * factor},${p.b * s * factor},${p.c * s * factor},${p.d * s * factor}," +
            "${camera.pan.x + p.e * s},${camera.pan.y + p.f * s})"
    val clip = remember(art, lit) { litClipPath(art, lit) }
    val loaded by rememberUpdatedState(onLoaded)
    Canvas({
        classes("goa-map-art", "dim")
        attr("width", art.width.toString())
        attr("height", art.height.toString())
        attr("aria-hidden", "true")
        style { property("transform", matrix(1f)) }
    }) {
        DisposableRefEffect(art.image) { canvas ->
            val image = HtmlImage()
            image.onload = {
                // After a frame: the drawn map stays up while the picture is baked.
                window.setTimeout({
                    bakeDimmed(canvas, image)
                    loaded()
                }, 0)
            }
            image.src = art.image
            onDispose { image.onload = null }
        }
    }
    // A small layer scaled up: gradients need no resolution, so this costs next to no memory.
    Div({
        classes("goa-map-art", "feather")
        attr("aria-hidden", "true")
        style {
            property("width", "${art.width / FEATHER_SCALE}px")
            property("height", "${art.height / FEATHER_SCALE}px")
            property("--sea", art.sea)
            property("--band-x", "${100f * art.pxPerUnit.toFloat() * ART_FEATHER / art.width}%")
            property("--band-y", "${100f * art.pxPerUnit.toFloat() * ART_FEATHER / art.height}%")
            property("transform", matrix(FEATHER_SCALE))
        }
    }) {
        listOf("l", "r", "t", "b").forEach { edge -> Div({ classes("edge", edge) }) }
    }
    Img(art.image, "") {
        classes("goa-map-art", "lit")
        attr("decoding", "async")
        attr("draggable", "false")
        style {
            property("clip-path", clip)
            property("-webkit-clip-path", clip)
            property("transform", matrix(1f))
        }
    }
}

/** Draws [image] on [canvas] as it looks outside the light: brightness and saturation as MapArt's DIM_*. */
private fun bakeDimmed(canvas: HTMLCanvasElement, image: HTMLImageElement) {
    val context = canvas.getContext("2d") as CanvasRenderingContext2D
    context.drawImage(image, 0.0, 0.0)
    val width = canvas.width.toDouble()
    val height = canvas.height.toDouble()
    try {
        val pixels = context.getImageData(0.0, 0.0, width, height)
        dimPixels(pixels.data, DIM_SATURATION, DIM_BRIGHTNESS)
        context.putImageData(pixels, 0.0, 0.0)
    } catch (_: Throwable) {
        // The pixels can't be read: dim without taking the colour down.
        context.fillStyle = "rgba(0, 0, 0, ${1f - DIM_BRIGHTNESS})"
        context.fillRect(0.0, 0.0, width, height)
    }
}

/** CSS's saturate() then brightness() on RGBA bytes: a plain loop, some 3 million pixels, once per map. */
@Suppress("UNUSED_PARAMETER")
private fun dimPixels(data: dynamic, saturation: Float, brightness: Float) {
    js(
        """
        var n = data.length;
        for (var i = 0; i < n; i += 4) {
            var r = data[i], g = data[i + 1], b = data[i + 2];
            var grey = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            data[i] = (grey + (r - grey) * saturation) * brightness;
            data[i + 1] = (grey + (g - grey) * saturation) * brightness;
            data[i + 2] = (grey + (b - grey) * saturation) * brightness;
        }
        """,
    )
}

/** A group whose children are [markup] (set as one string: hundreds of hexes, no component each). */
@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
private fun org.jetbrains.compose.web.dom.ElementScope<org.w3c.dom.svg.SVGElement>.Markup(markup: String, cls: String) {
    G({ classes(cls) }) {
        DisposableRefEffect(markup) { el ->
            el.innerHTML = markup
            onDispose { }
        }
    }
}

/**
 * A piece: a hero's portrait in its team's ring, a minion's team disc with its glyph or, by [style], its
 * miniature (the badge of its type drawn apart, above every figure), a token's label.
 */
@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
private fun org.jetbrains.compose.web.dom.ElementScope<org.w3c.dom.svg.SVGElement>.PieceSvg(
    piece: MapPiece,
    at: MapPoint,
    lifted: Boolean,
    style: PieceStyle,
) {
    val r = PIECE_SIZE / 2f
    val team = piece.team?.let { if (it == Team.RED) "red" else "blue" }
    G({
        classes("goa-piece")
        if (lifted) classes("lifted")
        style { property("transform", "translate(${n(at.x)}px, ${n(at.y)}px)") }
    }) {
        when (val look = piece.look) {
            is PieceLook.Hero -> if (style != PieceStyle.DRAWN) {
                HeroCoin(look, piece.team)
            } else {
                Circle(0, 0, r, { classes("p-halo") })
                Circle(0, 0, r * 0.82f, { classes("p-medallion") })
                // Text at 10 px scaled down: WebKit's minimum font size would blow up a 0.5 px font.
                SvgText(look.initials, 0, 0, {
                    classes("p-initials")
                    attr("transform", "scale(0.05)")
                })
                if (look.portrait != null) {
                    Image(look.portrait, {
                        attr("x", n(-r * 0.8f))
                        attr("y", n(-r * 0.8f))
                        attr("width", n(r * 1.6f))
                        attr("height", n(r * 1.6f))
                        attr("clip-path", "url(#goa-piece-clip)")
                        attr("preserveAspectRatio", "xMidYMid slice")
                    })
                }
                Circle(0, 0, r * 0.88f, { classes("p-ring", "m-stroke-$team") })
                piece.team?.let { Mark(it, MapPoint(r - 0.34f * 0.55f * 2f, r - 0.34f * 0.55f * 2f), 0.34f * 2f * r) }
            }

            is PieceLook.Minion -> if (style == PieceStyle.MINIATURE && piece.team != null) {
                Figure(look.type, piece.team)
                if (piece.immune) ImmuneRing()
            } else if (style == PieceStyle.DISC && piece.team != null) {
                PaintedDisc(look.type, piece.team)
                if (piece.immune) ImmuneRing()
            } else {
                Circle(0, 0, r * 0.86f, { classes("p-halo") })
                Circle(0, 0, r * 0.78f, { classes("m-fill-$team") })
                Circle(0, 0, r * 0.66f, { classes("p-inner") })
                Path(Glyph.of(look.type).path, {
                    classes("p-glyph")
                    attr("transform", "translate(${n(-r * 0.49f)} ${n(-r * 0.49f)}) scale(${r * 0.98f / 24f})")
                })
                piece.team?.let { Mark(it, MapPoint(r * 0.6f, r * 0.6f), r * 0.5f) }
                if (piece.immune) Circle(0, 0, 0.84f, { classes("p-immune") })
            }

            is PieceLook.Token -> {
                val side = PIECE_SIZE * 0.82f
                Rect(-side / 2f, -side / 2f, side, side, {
                    classes("p-token")
                    if (team != null) classes("m-stroke-$team")
                    attr("rx", n(PIECE_SIZE * 0.16f))
                })
                val words = look.label.take(TOKEN_CHARS)
                val em = tokenEm(words) * PIECE_SIZE
                SvgText(words, 0, 0, {
                    classes("p-token-label")
                    attr("font-size", "10")
                    attr("transform", "scale(${n4(em / 10f)})")
                })
            }
        }
    }
}

/** A hero on the painting (mock-ups 01–03): the portrait in its rings, the official mark on a dark badge. */
@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
private fun SvgScope.HeroCoin(look: PieceLook.Hero, team: Team?) {
    val slug = team?.let { if (it == Team.RED) "red" else "blue" }
    Circle(0, 0, COIN_R, { classes("p-coin") })
    Circle(0, 0, COIN_R * 0.82f, { classes("p-coin-ring", "m-stroke-$slug") })
    Circle(0, 0, COIN_R * 0.71f, { classes("p-medallion") })
    SvgText(look.initials, 0, 0, {
        classes("p-initials")
        attr("transform", "scale(0.05)")
    })
    if (look.portrait != null) {
        Image(look.portrait, {
            attr("x", n(-COIN_R * 0.71f))
            attr("y", n(-COIN_R * 0.71f))
            attr("width", n(COIN_R * 1.42f))
            attr("height", n(COIN_R * 1.42f))
            attr("clip-path", "url(#goa-piece-clip)")
            attr("preserveAspectRatio", "xMidYMid slice")
        })
    }
    if (team != null) TeamBadge(team, COIN_R * 0.68f, COIN_R * 0.42f)
}

/** The official gear or star on a dark round badge. */
@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
private fun SvgScope.TeamBadge(team: Team, at: Float, r: Float) {
    Circle(at, at, r * 1.1f, { classes("p-badge-edge") })
    Circle(at, at, r, { classes("p-badge") })
    val side = r * 1.74f
    Image(spawnArt(SpawnPoint(team, SpawnKind.HERO)), {
        attr("x", n(at - side / 2f))
        attr("y", n(at - side / 2f))
        attr("width", n(side))
        attr("height", n(side))
    })
}

/** A minion disc on the painting (mock-up 02). */
@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
private fun SvgScope.PaintedDisc(type: MinionType, team: Team) {
    val slug = if (team == Team.RED) "red" else "blue"
    Circle(0, 0, 0.72f, { classes("p-coin") })
    Circle(0, 0, 0.65f, { classes("p-disc-$slug") })
    Path(Glyph.of(type).path, {
        classes("p-glyph")
        attr("transform", "translate(-0.425 -0.425) scale(${0.85f / 24f})")
    })
    TeamBadge(team, 0.58f, 0.34f)
}

/**
 * The rulebook's miniature on a small team-coloured base (mock-up 09 B), the Heavy clearly larger; mock-up
 * 01's sizes on a hex of radius 20: a figure 31 px tall (the Heavy 38), its foot 12 px below the centre.
 */
@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
private fun SvgScope.Figure(type: MinionType, team: Team) {
    val slug = if (team == Team.RED) "red" else "blue"
    Ellipse(0, 0.45f, 0.75f, 0.325f, { classes("p-base", "m-stroke-$slug") })
    val height = if (type == MinionType.HEAVY) 2.0f else 1.55f
    val path = miniatureArt(type, team)
    val baked = bakedFigure(path)
    // The baked figure has a margin for its edge, the same share all round.
    val drawn = if (baked != null) height * FIGURE_MARGIN else height
    val foot = 0.6f + (drawn - height) / 2f
    Image(baked ?: path, {
        classes("p-figure")
        attr("x", n(-FIGURE_BOX / 2f))
        attr("y", n(foot - drawn))
        attr("width", n(FIGURE_BOX))
        attr("height", n(drawn))
        attr("preserveAspectRatio", "xMidYMax meet")
    })
}

/** The miniatures with a dark edge, by path: one image each, whatever the number of figures on the board. */
private val bakedFigures = mutableStateMapOf<String, String>()

/**
 * The miniature at [path] with a dark edge, so a figure holds on sand and on lava as on the phone. Baked once
 * into a single image rather than filtered or layered in the SVG: gestures repaint the SVG every frame, and a
 * filter or five images per figure is what would jank an iPhone. Null until it is ready (and if it fails).
 */
@Composable
private fun bakedFigure(path: String): String? {
    val baked = bakedFigures[path]
    if (baked == null) {
        DisposableEffect(path) {
            val image = HtmlImage()
            image.onload = {
                runCatching { bakeFigure(image) }.getOrNull()?.let { bakedFigures[path] = it }
            }
            image.src = path
            onDispose { image.onload = null }
        }
    }
    return baked
}

private fun bakeFigure(image: HTMLImageElement): String {
    val w = image.naturalWidth
    val h = image.naturalHeight
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    val mx = (h * (FIGURE_MARGIN - 1f) / 2f).toDouble()
    val my = mx
    canvas.width = w + 2 * mx.toInt()
    canvas.height = h + 2 * my.toInt()
    val shape = document.createElement("canvas") as HTMLCanvasElement
    shape.width = w
    shape.height = h
    val tint = shape.getContext("2d") as CanvasRenderingContext2D
    tint.drawImage(image, 0.0, 0.0)
    tint.globalCompositeOperation = "source-in"
    tint.fillStyle = "rgba(0, 0, 0, 0.85)"
    tint.fillRect(0.0, 0.0, w.toDouble(), h.toDouble())
    val out = canvas.getContext("2d") as CanvasRenderingContext2D
    val x = mx.toInt().toDouble()
    val y = my.toInt().toDouble()
    // As the phone's figure: 0.035 of a hex radius all round, 0.05 below; a figure is 1.55 radii tall.
    val edge = h * FIGURE_EDGE
    listOf(edge to 0.0, -edge to 0.0, 0.0 to edge, 0.0 to -edge, 0.0 to edge * FIGURE_DROP).forEach { (dx, dy) ->
        out.drawImage(shape, x + dx, y + dy)
    }
    out.drawImage(image, x, y)
    return canvas.toDataURL("image/png")
}

/** A miniature's type at its lower right: dark team tone, white hairline, the glyph in white. */
@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
private fun SvgScope.TypeBadge(type: MinionType, team: Team?) {
    val slug = if (team == Team.RED) "red" else "blue"
    Circle(0.68f, 0.57f, 0.41f, { classes("p-badge-edge") })
    Circle(0.68f, 0.53f, 0.37f, { classes("p-type-$slug") })
    val side = 0.555f
    Path(Glyph.of(type).path, {
        classes("p-type-glyph")
        attr("transform", "translate(${n4(0.68f - side / 2f)} ${n4(0.53f - side / 2f)}) scale(${n4(side / 24f)})")
    })
}

@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
private fun SvgScope.ImmuneRing() {
    Circle(0, 0, 0.84f, { classes("p-immune-halo") })
    Circle(0, 0, 0.84f, { classes("p-immune") })
}

@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
private fun org.jetbrains.compose.web.dom.ElementScope<org.w3c.dom.svg.SVGElement>.Mark(
    team: Team,
    at: MapPoint,
    size: Float,
) {
    val slug = if (team == Team.RED) "red" else "blue"
    val markup = teamMark(team, at, size, "p-mark m-fill-$slug")
    G {
        DisposableRefEffect(markup) { el ->
            el.innerHTML = markup
            onDispose { }
        }
    }
}

/** The label's font size as a share of the piece: as large as fits its longest word on one line. */
private fun tokenEm(label: String): Float {
    val longest = label.split(' ').maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
    return (TOKEN_WIDTH / (CHAR_EM * longest)).coerceIn(MIN_EM, MAX_EM)
}

private typealias SvgScope = org.jetbrains.compose.web.dom.ElementScope<org.w3c.dom.svg.SVGElement>

/** Watches [element]'s size (rotation, a panel opening); returns the way to stop. */
private fun observeSize(element: Element, onSize: (Float, Float) -> Unit): () -> Unit {
    val callback: (dynamic) -> Unit = {
        val rect = element.getBoundingClientRect()
        onSize(rect.width.toFloat(), rect.height.toFloat())
    }
    val observer: dynamic = js("new ResizeObserver(callback)")
    observer.observe(element)
    callback(null)
    return { observer.disconnect() }
}

/**
 * The map's touches: pointer events (touch, pen, mouse) and the wheel. Pans with one finger, pinch-zooms
 * with two, drags a piece (the selected one at once, another after a long press), reports taps.
 */
private class MapGestureHandler(
    private val element: Element,
    private val camera: MapCamera,
    private val layout: () -> HexLayout,
    private val onTap: (Hex, ScreenPoint) -> Boolean,
    private val onDoubleTap: (ScreenPoint) -> Unit,
    private val drag: () -> DragPolicy?,
    private val onDrag: (String?, MapPoint?) -> Unit,
) {
    private enum class Mode { TAP, PAN, PINCH, DRAG }

    private val pointers = LinkedHashMap<Int, ScreenPoint>()
    private var mode = Mode.TAP
    private var start = ScreenPoint(0f, 0f)
    private var travelled = 0f
    private var piece: String? = null
    private var pieceIsSelected = false
    private var longPress = 0
    private var lastTap: Triple<Double, Hex, Boolean>? = null
    private var pinchDistance = 0f
    private var pinchCentre = ScreenPoint(0f, 0f)

    private val down: (Event) -> Unit = { e -> onDown(e.asDynamic()) }
    private val move: (Event) -> Unit = { e -> onMove(e.asDynamic()) }
    private val up: (Event) -> Unit = { e -> onUp(e.asDynamic(), cancelled = e.type == "pointercancel") }
    private val wheel: (Event) -> Unit = { e -> onWheel(e.asDynamic()) }

    init {
        element.addEventListener("pointerdown", down)
        element.addEventListener("pointermove", move)
        element.addEventListener("pointerup", up)
        element.addEventListener("pointercancel", up)
        element.addEventListener("wheel", wheel, js("({ passive: false })"))
    }

    fun dispose() {
        element.removeEventListener("pointerdown", down)
        element.removeEventListener("pointermove", move)
        element.removeEventListener("pointerup", up)
        element.removeEventListener("pointercancel", up)
        element.removeEventListener("wheel", wheel)
        window.clearTimeout(longPress)
    }

    private fun local(e: dynamic): ScreenPoint {
        val rect = element.getBoundingClientRect()
        return ScreenPoint((e.clientX as Double - rect.left).toFloat(), (e.clientY as Double - rect.top).toFloat())
    }

    private fun hexAt(p: ScreenPoint): Hex = layout().hexAt(camera.screenToMap(p))

    private fun onDown(e: dynamic) {
        // Buttons over the map (labels, hidden nodes) handle their own presses.
        if ((e.button as Int) > 0) return
        val p = local(e)
        pointers[e.pointerId as Int] = p
        runCatching { element.asDynamic().setPointerCapture(e.pointerId) }
        if (pointers.size == 1) {
            mode = Mode.TAP
            start = p
            travelled = 0f
            val policy = drag()
            piece = policy?.pieceAt?.invoke(hexAt(p))
            pieceIsSelected = piece != null && piece == policy?.selected?.invoke()
            if (piece != null && !pieceIsSelected) {
                longPress = window.setTimeout({
                    if (mode == Mode.TAP && pointers.size == 1) {
                        mode = Mode.DRAG
                        onDrag(piece, camera.screenToMap(start))
                    }
                }, LONG_PRESS_MS)
            }
        } else {
            window.clearTimeout(longPress)
            if (mode == Mode.DRAG) onDrag(null, null)
            mode = Mode.PINCH
            startPinch()
        }
    }

    private fun startPinch() {
        val (a, b) = pointers.values.toList().let { it[0] to it[1] }
        pinchDistance = (a - b).length()
        pinchCentre = ScreenPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }

    private fun onMove(e: dynamic) {
        val id = e.pointerId as Int
        val previous = pointers[id] ?: return
        val p = local(e)
        pointers[id] = p
        e.preventDefault()
        when (mode) {
            Mode.PINCH -> if (pointers.size >= 2) {
                val (a, b) = pointers.values.toList().let { it[0] to it[1] }
                val distance = (a - b).length()
                val centre = ScreenPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                if (pinchDistance > 0f) camera.transform(distance / pinchDistance, centre, centre - pinchCentre)
                pinchDistance = distance
                pinchCentre = centre
            }

            Mode.DRAG -> onDrag(piece, camera.screenToMap(p))

            Mode.PAN -> camera.panBy(p - previous)

            Mode.TAP -> {
                travelled += (p - previous).length()
                if (travelled > TOUCH_SLOP) {
                    window.clearTimeout(longPress)
                    if (pieceIsSelected) {
                        mode = Mode.DRAG
                        onDrag(piece, camera.screenToMap(p))
                    } else {
                        mode = Mode.PAN
                        camera.panBy(p - start)
                    }
                }
            }
        }
    }

    private fun onUp(e: dynamic, cancelled: Boolean) {
        val id = e.pointerId as Int
        if (pointers.remove(id) == null) return
        window.clearTimeout(longPress)
        if (pointers.isNotEmpty()) {
            if (mode == Mode.PINCH && pointers.size >= 2) startPinch()
            if (pointers.size == 1) {
                // One finger left after a pinch: it pans from here.
                mode = Mode.PAN
            }
            return
        }
        val p = local(e)
        when (mode) {
            Mode.DRAG -> {
                val dragged = piece
                onDrag(null, null)
                lastTap = null
                if (!cancelled && dragged != null) drag()?.onDrop?.invoke(dragged, hexAt(p))
            }

            Mode.TAP -> if (!cancelled) tapped(start)

            else -> lastTap = null
        }
        mode = Mode.TAP
    }

    private fun tapped(at: ScreenPoint) {
        val hex = hexAt(at)
        val now = window.performance.now()
        val last = lastTap
        val double = last != null && last.third && last.second == hex && now - last.first < DOUBLE_TAP_MS
        if (double) {
            lastTap = null
            onDoubleTap(at)
            return
        }
        val did = onTap(hex, at)
        lastTap = Triple(now, hex, !did)
    }

    private fun onWheel(e: dynamic) {
        e.preventDefault()
        val p = local(e)
        val dy = (e.deltaY as Double).toFloat()
        camera.transform(exp(-dy * WHEEL_ZOOM), p)
    }

    companion object {
        const val TOUCH_SLOP = 8f
        const val LONG_PRESS_MS = 450
        const val DOUBLE_TAP_MS = 320.0
        const val WHEEL_ZOOM = 0.0015f
    }
}

/** Diameter of a piece in map units (a hex is √3 ≈ 1.73 across its flats), as on the phone. */
const val PIECE_SIZE = 1.46f

/** A hero's coin on a painting: as wide as a hex across its flats. */
private const val COIN_R = 0.86f

/** Wide enough for any miniature, which is fitted to its height and centred. */
private const val FIGURE_BOX = 3.2f

/** A baked figure's height against the miniature's, for the room its edge takes. */
private const val FIGURE_MARGIN = 1.1f
private const val FIGURE_EDGE = 0.035 / 1.55
private const val FIGURE_DROP = 1.4

/** How much smaller the feather layer is laid out than the picture. */
private const val FEATHER_SCALE = 8f

private const val TOKEN_CHARS = 16
private const val TOKEN_WIDTH = 0.66f
private const val CHAR_EM = 0.66f
private const val MIN_EM = 0.14f
private const val MAX_EM = 0.27f

private fun n4(value: Float): String = ((value * 10_000f).roundToInt() / 10_000.0).toString()

/** sqrt(3): a hex's width across its flats, in corner radii. */
val SQRT3: Float = sqrt(3f)
