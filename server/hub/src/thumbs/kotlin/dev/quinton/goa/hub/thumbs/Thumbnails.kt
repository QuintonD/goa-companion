package dev.quinton.goa.hub.thumbs

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.ScaleMethod
import com.sksamuel.scrimage.webp.WebpImageReader
import com.sksamuel.scrimage.webp.WebpWriter
import java.io.File
import kotlin.math.roundToInt

/**
 * Build step of the hub (`generateThumbnails`): smaller copies of the bundled hero portraits for
 * lists, avatars and the grid on a phone's data plan. The full images stay as they are (hero page,
 * card viewer). Writes `<out>/goa-assets/thumbs/w<width>/official/heroes/<hero>/portrait.webp`.
 *
 * Arguments: `<official/heroes dir> <out dir> <width>…`.
 */
fun main(args: Array<String>) {
    require(args.size >= 3) { "Usage: <heroes dir> <out dir> <width>…" }
    val source = File(args[0])
    val out = File(args[1])
    val widths = args.drop(2).map { it.toInt() }
    out.deleteRecursively()
    val portraits = source.walkTopDown().filter { it.isFile && it.extension.equals("webp", ignoreCase = true) }.toList()
    portraits.forEach { file ->
        val image = ImmutableImage.loader().withImageReaders(listOf(WebpImageReader())).fromFile(file)
        val relative = file.relativeTo(source).invariantSeparatorsPath
        widths.forEach { width ->
            val target = File(out, "goa-assets/thumbs/w$width/official/heroes/$relative")
            target.parentFile.mkdirs()
            val scaled = if (image.width > width) {
                image.scaleTo(width, (image.height * width.toDouble() / image.width).roundToInt(), ScaleMethod.Lanczos3)
            } else {
                image
            }
            scaled.output(WebpWriter.DEFAULT.withQ(QUALITY).withM(METHOD), target)
        }
    }
    println("Thumbnails: ${portraits.size} portraits at ${widths.joinToString()} px")
}

private const val QUALITY = 78
private const val METHOD = 6
