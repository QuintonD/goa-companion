package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Img
import org.w3c.dom.HTMLImageElement

/**
 * An official image offered with its small copy (`Thumbnails`, ADR-043 "Addendum (R3 first load)"):
 * [srcset] and [sizes] let the browser pick the copy when it is sharp enough at the size on screen. If
 * the picked file can't load (the copy, offline, when only the full images were downloaded) the image
 * falls back to [src] alone; [onFailed] runs when that fails too. Without a [srcset] it is a plain img.
 */
@Composable
fun OfficialImg(
    src: String,
    srcset: String?,
    sizes: String,
    alt: String,
    onFailed: () -> Unit,
    attrs: AttrBuilderContext<HTMLImageElement>? = null,
) {
    var fullOnly by remember(src) { mutableStateOf(false) }
    val offered = srcset?.takeUnless { fullOnly }
    Img(src, alt = alt) {
        if (offered != null) {
            attr("srcset", offered)
            attr("sizes", sizes)
        }
        attrs?.invoke(this)
        addEventListener("error") {
            if (offered != null) fullOnly = true else onFailed()
        }
    }
}
