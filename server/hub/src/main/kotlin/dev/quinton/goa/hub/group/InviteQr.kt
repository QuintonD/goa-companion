package dev.quinton.goa.hub.group

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The invite QR code as server-rendered SVG (no image request, no script): the same code as the
 * phone's `InviteQr` (error correction M, a one-module quiet zone), black modules on white, one
 * unit per module. Each row's dark runs are one `M x y h n v1 h-n z` subpath.
 */
object InviteQr {
    fun matrix(text: String): BitMatrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
    )

    /** The `<svg>` element for [text]; [label] is its accessible name (escaped here). */
    fun svg(text: String, label: String): String {
        val m = matrix(text)
        val path = buildString {
            for (y in 0 until m.height) {
                var x = 0
                while (x < m.width) {
                    if (!m[x, y]) {
                        x++
                        continue
                    }
                    val start = x
                    while (x < m.width && m[x, y]) x++
                    val run = x - start
                    append("M").append(start).append(' ').append(y).append('h').append(run).append("v1h-").append(run)
                        .append('z')
                }
            }
        }
        return """<svg xmlns="http://www.w3.org/2000/svg" class="qr-code" viewBox="0 0 ${m.width} ${m.height}" """ +
            """role="img" aria-label="${escape(label)}" shape-rendering="crispEdges">""" +
            """<rect width="${m.width}" height="${m.height}" fill="#fff"/><path fill="#000" d="$path"/></svg>"""
    }

    private fun escape(text: String): String = buildString {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
