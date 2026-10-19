package dev.quinton.goa.hub.web.ui

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import org.slf4j.LoggerFactory
import org.w3c.dom.Element

/**
 * PNG icons for the web app manifest and iOS (`apple-touch-icon`), drawn with Java2D from the app
 * icon's own SVG masters (`design/brand/ic_launcher_{background,foreground}.svg`, packaged under
 * `static/brand/`), so the SVG stays the master and nothing raster is checked in. Supports what
 * those files use (and `svg2vd.py` allows): rects and paths (M L H V C S Z, absolute and relative)
 * filled or stroked with colours or user-space linear/radial gradients.
 */
object IconRenderer {
    enum class Icon(val fileName: String, val size: Int, val maskable: Boolean, val inManifest: Boolean) {
        ICON_192("icon-192.png", 192, maskable = false, inManifest = true),
        ICON_512("icon-512.png", 512, maskable = false, inManifest = true),
        MASKABLE_512("maskable-512.png", 512, maskable = true, inManifest = true),
        APPLE_TOUCH("apple-touch-icon.png", 180, maskable = true, inManifest = false),
        FAVICON("favicon-32.png", 32, maskable = false, inManifest = false),
        ;

        companion object {
            fun byFileName(name: String) = entries.firstOrNull { it.fileName == name }
        }
    }

    /**
     * iOS launch screens for the home-screen app (`apple-touch-startup-image`): the app's dark
     * background with the brand mark, one per iPhone screen ([cssWidth] × [cssHeight] points at [ratio]).
     */
    enum class Splash(val cssWidth: Int, val cssHeight: Int, val ratio: Int) {
        IPHONE_SE(375, 667, 2),
        IPHONE_XR(414, 896, 2),
        IPHONE_X(375, 812, 3),
        IPHONE_XS_MAX(414, 896, 3),
        IPHONE_12(390, 844, 3),
        IPHONE_12_PRO_MAX(428, 926, 3),
        IPHONE_14_PRO(393, 852, 3),
        IPHONE_14_PRO_MAX(430, 932, 3),
        IPHONE_16_PRO(402, 874, 3),
        IPHONE_AIR(420, 912, 3),
        IPHONE_16_PRO_MAX(440, 956, 3),
        ;

        val width get() = cssWidth * ratio
        val height get() = cssHeight * ratio
        val fileName get() = "splash-${width}x$height.png"
        val media get() =
            "(device-width: ${cssWidth}px) and (device-height: ${cssHeight}px) and " +
                "(-webkit-device-pixel-ratio: $ratio) and (orientation: portrait)"

        companion object {
            fun byFileName(name: String) = entries.firstOrNull { it.fileName == name }
        }
    }

    private val cache = ConcurrentHashMap<Icon, ByteArray>()
    private val splashCache = ConcurrentHashMap<Splash, ByteArray>()
    private val log = LoggerFactory.getLogger(IconRenderer::class.java)
    private const val VIEWPORT = 108.0

    /** Zoom for "any" icons: the launcher art leaves room for Android's masks; a plain icon fills more. */
    private const val ANY_ZOOM = 1.3

    /** The icon as PNG bytes, or null if Java2D isn't available (the SVG icon still is). */
    fun png(icon: Icon): ByteArray? = cache[icon] ?: runCatching { render(icon) }
        .onFailure { log.warn("Can't render {}: {}", icon.fileName, it.toString()) }
        .getOrNull()?.also { cache[icon] = it }

    /** The launch screen as PNG bytes, or null if Java2D isn't available. */
    fun png(splash: Splash): ByteArray? = splashCache[splash] ?: runCatching { render(splash) }
        .onFailure { log.warn("Can't render {}: {}", splash.fileName, it.toString()) }
        .getOrNull()?.also { splashCache[splash] = it }

    private fun render(splash: Splash): ByteArray {
        val image = BufferedImage(splash.width, splash.height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.color = SPLASH_BACKGROUND
            g.fillRect(0, 0, splash.width, splash.height)
            // The launcher foreground (108-unit canvas, art in its middle two thirds) at half the width.
            val box = splash.width * SPLASH_MARK
            g.translate((splash.width - box) / 2, (splash.height - box) / 2)
            g.scale(box / VIEWPORT, box / VIEWPORT)
            draw(g, "static/brand/ic_launcher_foreground.svg")
        } finally {
            g.dispose()
        }
        ImageIO.setUseCache(false)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private val SPLASH_BACKGROUND = Color(0x0B1622)
    private const val SPLASH_MARK = 0.5

    private fun render(icon: Icon): ByteArray {
        val image = BufferedImage(icon.size, icon.size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            val scale = icon.size / VIEWPORT
            g.scale(scale, scale)
            if (icon != Icon.FAVICON) draw(g, "static/brand/ic_launcher_background.svg")
            val zoom = when {
                icon == Icon.FAVICON -> FAVICON_ZOOM
                icon.maskable -> 1.0
                else -> ANY_ZOOM
            }
            g.transform(
                AffineTransform().apply {
                    translate(VIEWPORT / 2, VIEWPORT / 2)
                    scale(zoom, zoom)
                    translate(-VIEWPORT / 2, -VIEWPORT / 2 + if (icon == Icon.FAVICON) FAVICON_SHIFT else 0.0)
                },
            )
            draw(g, "static/brand/ic_launcher_foreground.svg")
        } finally {
            g.dispose()
        }
        // No disk cache: the hub writes only under its data directory.
        ImageIO.setUseCache(false)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private const val FAVICON_ZOOM = 1.85
    private const val FAVICON_SHIFT = 1.5

    private fun draw(g: Graphics2D, resource: String) {
        val stream =
            checkNotNull(IconRenderer::class.java.classLoader.getResourceAsStream(resource)) { "Missing $resource" }
        val doc = stream.use {
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isNamespaceAware = false
            }.newDocumentBuilder().parse(it)
        }
        val gradients = HashMap<String, Paint>()
        doc.getElementsByTagName("linearGradient").forEachElement { gradients[it.getAttribute("id")] = linear(it) }
        doc.getElementsByTagName("radialGradient").forEachElement { gradients[it.getAttribute("id")] = radial(it) }
        val children = doc.documentElement.childNodes
        for (el in (0 until children.length).mapNotNull { children.item(it) as? Element }) {
            val shape = when (el.tagName) {
                "rect" -> Rectangle2D.Double(el.num("x"), el.num("y"), el.num("width"), el.num("height"))
                "path" -> SvgPath.parse(el.getAttribute("d"))
                else -> null
            } ?: continue
            paintOf(el.getAttribute("fill").ifEmpty { "#000000" }, el.optNum("fill-opacity"), gradients)?.let {
                g.paint = it
                g.fill(shape)
            }
            paintOf(el.getAttribute("stroke"), el.optNum("stroke-opacity"), gradients)?.let {
                g.paint = it
                g.stroke = BasicStroke(el.optNum("stroke-width")?.toFloat() ?: 1f, cap(el), join(el))
                g.draw(shape)
            }
        }
    }

    private fun paintOf(spec: String, opacity: Double?, gradients: Map<String, Paint>): Paint? = when {
        spec.isEmpty() || spec == "none" -> null
        spec.startsWith("url(#") -> gradients[spec.removePrefix("url(#").removeSuffix(")")]
        else -> color(spec, opacity ?: 1.0)
    }

    private fun linear(el: Element): Paint {
        val (fractions, colors) = stops(el)
        return LinearGradientPaint(
            Point2D.Double(el.num("x1"), el.num("y1")),
            Point2D.Double(el.num("x2"), el.num("y2")),
            fractions,
            colors,
            MultipleGradientPaint.CycleMethod.NO_CYCLE,
        )
    }

    private fun radial(el: Element): Paint {
        val (fractions, colors) = stops(el)
        return RadialGradientPaint(Point2D.Double(el.num("cx"), el.num("cy")), el.num("r").toFloat(), fractions, colors)
    }

    private fun stops(el: Element): Pair<FloatArray, Array<Color>> {
        val stops = ArrayList<Pair<Float, Color>>()
        el.getElementsByTagName("stop").forEachElement {
            stops +=
                it.num("offset").toFloat() to color(it.getAttribute("stop-color"), it.optNum("stop-opacity") ?: 1.0)
        }
        return stops.map { it.first }.toFloatArray() to stops.map { it.second }.toTypedArray()
    }

    private fun color(hex: String, opacity: Double): Color {
        val rgb = hex.removePrefix("#").toInt(HEX)
        return Color(
            (rgb shr 16) and 0xFF,
            (rgb shr 8) and 0xFF,
            rgb and 0xFF,
            (opacity * 255).toInt().coerceIn(0, 255),
        )
    }

    private fun cap(el: Element) = when (el.getAttribute("stroke-linecap")) {
        "round" -> BasicStroke.CAP_ROUND
        "square" -> BasicStroke.CAP_SQUARE
        else -> BasicStroke.CAP_BUTT
    }

    private fun join(el: Element) = when (el.getAttribute("stroke-linejoin")) {
        "round" -> BasicStroke.JOIN_ROUND
        "bevel" -> BasicStroke.JOIN_BEVEL
        else -> BasicStroke.JOIN_MITER
    }

    private fun Element.num(name: String) = optNum(name) ?: 0.0

    private fun Element.optNum(name: String) = getAttribute(name).takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    private inline fun org.w3c.dom.NodeList.forEachElement(action: (Element) -> Unit) {
        for (i in 0 until length) (item(i) as? Element)?.let(action)
    }

    private const val HEX = 16
}

/** SVG path data → [Path2D] (M L H V C S Z, absolute and relative, implicit repeats). */
internal object SvgPath {
    private val TOKEN = Regex("[MmLlHhVvCcSsZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun parse(d: String): Path2D.Double {
        val tokens = TOKEN.findAll(d).map { it.value }.toList()
        val path = Path2D.Double()
        var i = 0
        var cmd = ' '
        var x = 0.0
        var y = 0.0
        var startX = 0.0
        var startY = 0.0
        var lastCtrlX = 0.0
        var lastCtrlY = 0.0
        var lastWasCubic = false
        fun num() = tokens[i++].toDouble()
        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) cmd = tokens[i++][0]
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0.0
            val oy = if (rel) y else 0.0
            var cubic = false
            when (cmd.uppercaseChar()) {
                'M' -> {
                    x = ox + num()
                    y = oy + num()
                    path.moveTo(x, y)
                    startX = x
                    startY = y
                    cmd = if (rel) 'l' else 'L' // further pairs are line-tos
                }

                'L' -> {
                    x = ox + num()
                    y = oy + num()
                    path.lineTo(x, y)
                }

                'H' -> {
                    x = ox + num()
                    path.lineTo(x, y)
                }

                'V' -> {
                    y = oy + num()
                    path.lineTo(x, y)
                }

                'C' -> {
                    val x1 = ox + num()
                    val y1 = oy + num()
                    lastCtrlX = ox + num()
                    lastCtrlY = oy + num()
                    x = ox + num()
                    y = oy + num()
                    path.curveTo(x1, y1, lastCtrlX, lastCtrlY, x, y)
                    cubic = true
                }

                'S' -> {
                    val x1 = if (lastWasCubic) 2 * x - lastCtrlX else x
                    val y1 = if (lastWasCubic) 2 * y - lastCtrlY else y
                    lastCtrlX = ox + num()
                    lastCtrlY = oy + num()
                    x = ox + num()
                    y = oy + num()
                    path.curveTo(x1, y1, lastCtrlX, lastCtrlY, x, y)
                    cubic = true
                }

                'Z' -> {
                    require(i >= tokens.size || tokens[i][0].isLetter()) { "Numbers after Z in a path" }
                    path.closePath()
                    x = startX
                    y = startY
                }

                else -> error("Unsupported path command $cmd")
            }
            lastWasCubic = cubic
        }
        return path
    }
}
