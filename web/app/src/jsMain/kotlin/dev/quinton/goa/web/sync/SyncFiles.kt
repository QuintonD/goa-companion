package dev.quinton.goa.web.sync

import dev.quinton.goa.core.domain.sync.SyncFileCodec
import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement

/** "Share sync file" / "Import sync file" in a browser (the phone's SyncFiles, ADR-025). */
object SyncFiles {
    /** Refuse anything bigger: a group's sync file is tens to hundreds of KB. */
    private const val MAX_BYTES = 32 * 1024 * 1024

    /** `goa-sync-friday-nights-2026-10-25.goasync`, as the phone names it. */
    fun fileName(groupName: String): String {
        val safe = groupName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "group" }.take(30)
        val d = Date()
        val today = "${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-" +
            d.getDate().toString().padStart(2, '0')
        return "goa-sync-$safe-$today.${SyncFileCodec.FILE_EXTENSION}"
    }

    /** The share sheet with the file where the browser can share files (iOS), else a download. */
    suspend fun share(bytes: ByteArray, fileName: String, title: String) {
        val parts = arrayOf<Any>(dev.quinton.goa.web.group.view(bytes))
        val options: dynamic = js("({})")
        options.type = SyncFileCodec.FILE_MIME_TYPE
        val file: dynamic = js("new File(parts, fileName, options)")
        val navigator = window.navigator.asDynamic()
        val data: dynamic = js("({})")
        data.files = arrayOf(file)
        data.title = title
        val canShare = navigator.canShare != null && navigator.share != null && navigator.canShare(data) == true
        if (canShare) {
            try {
                (navigator.share(data) as Promise<Any?>).await()
                return
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                // Cancelled by the person (AbortError): nothing to do.
                if (e.asDynamic().name == "AbortError") return
            }
        }
        val url = js("URL").createObjectURL(file) as String
        val link = document.createElement("a") as HTMLAnchorElement
        link.href = url
        link.download = fileName
        link.click()
        window.setTimeout({ js("URL").revokeObjectURL(url) }, REVOKE_MS)
    }

    /** Lets the person pick a file and hands its bytes to [onFile] (nothing when cancelled or too big). */
    fun pick(onFile: (ByteArray?) -> Unit) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        // Some apps hand the file over as octet-stream: accept any file, as the phone does.
        input.onchange = {
            val file = input.files?.item(0)
            if (file != null) {
                if (file.size.toInt() > MAX_BYTES) {
                    onFile(null)
                } else {
                    (file.asDynamic().arrayBuffer() as Promise<ArrayBuffer>).then { buffer ->
                        onFile(Int8Array(buffer).unsafeCast<ByteArray>())
                    }
                }
            }
        }
        input.click()
    }

    private const val REVOKE_MS = 60_000
}

/** What other phones see this browser as ("Safari on iPhone"), when no player is chosen. */
object BrowserName {
    fun get(): String {
        val agent = window.navigator.userAgent
        val device = when {
            "iPhone" in agent -> "iPhone"
            "iPad" in agent -> "iPad"
            "Android" in agent -> "Android"
            "Macintosh" in agent -> "Mac"
            "Windows" in agent -> "Windows"
            else -> "this computer"
        }
        val browser = when {
            "Edg/" in agent -> "Edge"
            "Firefox/" in agent || "FxiOS" in agent -> "Firefox"
            "CriOS" in agent || "Chrome/" in agent -> "Chrome"
            "Safari/" in agent -> "Safari"
            else -> "Browser"
        }
        return "$browser on $device"
    }
}
