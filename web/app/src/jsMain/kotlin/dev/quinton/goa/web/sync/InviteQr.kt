package dev.quinton.goa.web.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.web.ui.Load
import dev.quinton.goa.web.ui.escapeMarkup
import dev.quinton.goa.web.ui.rememberLoad
import kotlin.js.Promise
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Video
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLVideoElement

/**
 * The QR libraries, loaded on first use rather than with app.js (ADR-043 "Addendum (R3 first load)"):
 * webpack splits them into chunks (`<id>.chunk.js`, precached with the shell for offline use), so a
 * first visit that never opens the Group screen doesn't download them.
 */
private object QrLibraries {
    private var loading: Promise<dynamic>? = null

    /** The two modules as `[qrcode-generator, jsQR]`. */
    suspend fun load(): Array<dynamic> {
        val promise = loading ?: importBoth().also { loading = it }
        return try {
            promise.await().unsafeCast<Array<dynamic>>()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            loading = null // offline before the chunk was cached: the next try fetches again
            throw e
        }
    }

    private fun importBoth(): Promise<dynamic> = js(
        "Promise.all([import(\"qrcode-generator\"), import(\"jsqr\")]).then(function (m) { " +
            "return [m[0].default || m[0], m[1].default || m[1]]; })",
    )
}

/** QR codes for invites: drawing (qrcode-generator, MIT) and reading (jsQR, Apache-2.0). */
object InviteQr {
    /** Loads the QR libraries (a small chunk, cached); call before [svg] or [read]. */
    suspend fun load() {
        val (generator, reader) = QrLibraries.load()
        qrcode = generator
        jsQR = reader
    }

    private var qrcode: dynamic = null
    private var jsQR: dynamic = null

    /**
     * The QR code of [text] as SVG markup: black modules on white with a quiet zone, error
     * correction M (the phone's ZXing settings), one unit per module so it scales crisply.
     * Needs [load] first.
     */
    fun svg(text: String, title: String): String {
        val qr = qrcode(0, "M")
        qr.addData(text, "Byte")
        qr.make()
        val count = qr.getModuleCount() as Int
        val size = count + 2 * QUIET_ZONE
        val path = buildString {
            for (row in 0 until count) {
                for (col in 0 until count) {
                    if (qr.isDark(row, col) as Boolean) append("M${col + QUIET_ZONE} ${row + QUIET_ZONE}h1v1h-1z")
                }
            }
        }
        // The group's name is whatever the invite said: escaped for the attribute, quotes included.
        val safeTitle = escapeMarkup(title)
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $size $size" shape-rendering="crispEdges" """ +
            """role="img" aria-label="$safeTitle"><rect width="$size" height="$size" fill="#fff"/>""" +
            """<path d="$path" fill="#000"/></svg>"""
    }

    /** The text of a QR code in the pixels of [canvas], or null when none is found. Needs [load] first. */
    fun read(canvas: HTMLCanvasElement): String? {
        val context = canvas.getContext("2d").asDynamic() ?: return null
        val image = context.getImageData(0, 0, canvas.width, canvas.height)
        val options: dynamic = js("({ inversionAttempts: 'attemptBoth' })")
        val found = jsQR(image.data, canvas.width, canvas.height, options)
        return if (found == null) null else found.data as String?
    }

    private const val QUIET_ZONE = 2
}

/** The invite's QR code, big and on white (scanners need the contrast). */
@Composable
fun QrCode(text: String, title: String) {
    val loaded = rememberLoad(text to title) {
        InviteQr.load()
        InviteQr.svg(text, title)
    }
    val svg = (loaded.state as? Load.Ready)?.value
    if (svg == null) {
        // While the QR library loads (or if it can't, offline before it was cached): the same white
        // square, so the layout doesn't jump; the invite link beside it still works.
        Div({
            classes("goa-w2-qr")
            attr("role", "img")
            attr("aria-label", if (loaded.state is Load.Failed) "$title (couldn't be drawn offline)" else title)
        })
        return
    }
    key(svg) {
        Div({
            classes("goa-w2-qr")
            ref { element ->
                element.innerHTML = svg
                onDispose { }
            }
        })
    }
}

/**
 * Reads an invite QR code with the camera (`getUserMedia`, the back camera; Safari has no
 * BarcodeDetector, so frames go through jsQR). Calls [onFound] once with the text; [onProblem] with
 * words when there is no camera or no permission. The camera stops when this leaves the screen.
 */
@Composable
fun QrScanner(onFound: (String) -> Unit, onProblem: (String) -> Unit) {
    var status by remember { mutableStateOf("Starting the camera…") }
    var video: HTMLVideoElement? by remember { mutableStateOf(null) }
    DisposableEffect(video) {
        val element = video ?: return@DisposableEffect onDispose { }
        val scope = MainScope()
        var stream: dynamic = null
        scope.launch {
            val media = window.navigator.asDynamic().mediaDevices
            if (media == null || media.getUserMedia == null) {
                onProblem(NO_CAMERA)
                return@launch
            }
            stream = try {
                val constraints: dynamic = js("({ video: { facingMode: 'environment' }, audio: false })")
                (media.getUserMedia(constraints) as Promise<dynamic>).await()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                onProblem(if (e.asDynamic().name == "NotAllowedError") NO_PERMISSION else NO_CAMERA)
                return@launch
            }
            element.asDynamic().srcObject = stream
            element.setAttribute("playsinline", "true")
            (element.play() as? Promise<*>)?.let {
                try {
                    it.await()
                } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
                    // iOS plays muted inline video without a gesture; a refusal shows a still frame.
                }
            }
            try {
                InviteQr.load()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Throwable) {
                onProblem(NO_READER) // the chunk didn't load (offline before it was cached): words, not the error
                return@launch
            }
            status = "Point the camera at the invite QR code"
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            var found: String? = null
            while (isActive && found == null) {
                delay(FRAME_MS)
                found = readFrame(element, canvas)
            }
            found?.let(onFound)
        }
        onDispose {
            scope.cancel()
            val tracks = stream?.getTracks()
            if (tracks != null) (tracks as Array<dynamic>).forEach { it.stop() }
        }
    }
    Div({ classes("goa-w2-scanner") }) {
        Video({
            attr("muted", "")
            attr("playsinline", "")
            attr("autoplay", "")
            attr("aria-label", "Camera")
            ref { element ->
                video = element
                onDispose { video = null }
            }
        })
        Div({ classes("goa-w2-scanner-frame") })
    }
    P({
        classes("t-body-medium", "c-variant")
        attr("role", "status")
    }) { Text(status) }
}

/** The QR code in the video's current frame (scaled down to [MAX_SIDE]), or null. */
private fun readFrame(video: HTMLVideoElement, canvas: HTMLCanvasElement): String? {
    val width = video.videoWidth
    val height = video.videoHeight
    if (width == 0 || height == 0) return null
    val scale = minOf(1.0, MAX_SIDE / maxOf(width, height).toDouble())
    canvas.width = (width * scale).toInt()
    canvas.height = (height * scale).toInt()
    canvas.getContext("2d").asDynamic().drawImage(video, 0, 0, canvas.width, canvas.height)
    return InviteQr.read(canvas)
}

private const val FRAME_MS = 200L
private const val MAX_SIDE = 800

const val NO_CAMERA = "This browser can't use a camera here. Paste the invite instead."
const val NO_READER = "The QR reader couldn't load (offline?). Paste the invite instead."
const val NO_PERMISSION =
    "The camera isn't allowed for this site. Allow it in the browser's settings, or paste the invite instead."
