package dev.quinton.goa.web.play.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.map.HexLayout
import dev.quinton.goa.feature.board.map.MapPoint
import dev.quinton.goa.feature.board.map.MapRect
import kotlin.math.exp
import kotlin.math.sqrt
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.ExperimentalComposeWebSvgApi
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.svg.Circle
import org.jetbrains.compose.web.svg.G
import org.jetbrains.compose.web.svg.Image
import org.jetbrains.compose.web.svg.Path
import org.jetbrains.compose.web.svg.Rect
import org.jetbrains.compose.web.svg.Svg
import org.jetbrains.compose.web.svg.SvgText
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement
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
 * The board as SVG: crisp at any zoom, the phone's look. One finger pans (or drags a piece, see
 * [drag]), two pinch-zoom, a wheel zooms; a tap reports the space under it to [onTap] (true = it did
 * something), and a second tap on the same space when the first did nothing zooms in. [marks] and
 * [lines] are the overlays, [pieces] glide from space to space. [nodes] are what VoiceOver reads and
 * activates (the map itself is one image to it).
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
    attrs: (AttrsScope<HTMLDivElement>.() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val dragPolicy by rememberUpdatedState(drag)
    val currentScene by rememberUpdatedState(scene)
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragAt by remember { mutableStateOf(MapPoint(0f, 0f)) }
    val layout = scene.layout

    Div({
        classes("goa-map")
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
                camera.setUp(currentScene.layout, w, h, currentScene.lane, currentScene.laneMayCrop)
            }
            onDispose {
                gestures.dispose()
                resize()
            }
        }
        // Keep the framing when the layout changes (Flip, rotation) without a resize.
        if (camera.width > 0f) camera.setUp(layout, camera.width, camera.height, scene.lane, scene.laneMayCrop)

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
                Markup(scene.baseMarkup, "goa-map-base")
                val dropMark = dragging?.let { listOf(HexMark(layout.hexAt(dragAt), MarkStyle.DROP)) }.orEmpty()
                Markup(marksMarkup(layout, marks + dropMark, lines), "goa-map-marks")
                pieces.sortedBy { it.raised || it.id == dragging }.forEach { piece ->
                    key(piece.id) {
                        val at = if (piece.id == dragging) dragAt else layout.center(piece.at)
                        PieceSvg(piece, at, lifted = piece.id == dragging)
                    }
                }
            }
        }
        if (showLabels &&
            camera.unit > 0f
        ) {
            ZoneLabels(scene, camera, bottomTeam, busy = pieces.map { it.at } + marks.map { it.hex })
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

/** A piece: a hero's portrait in its team's ring, a minion's team disc with its glyph, a token's label. */
@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
private fun org.jetbrains.compose.web.dom.ElementScope<org.w3c.dom.svg.SVGElement>.PieceSvg(
    piece: MapPiece,
    at: MapPoint,
    lifted: Boolean,
) {
    val r = PIECE_SIZE / 2f
    val team = piece.team?.let { if (it == Team.RED) "red" else "blue" }
    G({
        classes("goa-piece")
        if (lifted) classes("lifted")
        style { property("transform", "translate(${n(at.x)}px, ${n(at.y)}px)") }
    }) {
        when (val look = piece.look) {
            is PieceLook.Hero -> {
                Circle(0, 0, r, { classes("p-halo") })
                Circle(0, 0, r * 0.82f, { classes("p-medallion") })
                SvgText(look.initials, 0, 0, { classes("p-initials") })
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

            is PieceLook.Minion -> {
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
                    attr("font-size", n(em))
                })
            }
        }
    }
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

private const val TOKEN_CHARS = 16
private const val TOKEN_WIDTH = 0.66f
private const val CHAR_EM = 0.66f
private const val MIN_EM = 0.14f
private const val MAX_EM = 0.27f

/** sqrt(3): a hex's width across its flats, in corner radii. */
val SQRT3: Float = sqrt(3f)
