package dev.quinton.goa.tools.catalogimport

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.ScaleMethod
import com.sksamuel.scrimage.webp.WebpImageReader
import com.sksamuel.scrimage.webp.WebpWriter
import java.io.File
import kotlin.math.roundToInt

/** Image decoding, region signatures and WebP encoding (scrimage + bundled libwebp). */
object ImageIo {
    const val QUALITY = 80
    const val CARD_MAX_EDGE = 768
    const val PORTRAIT_MAX_EDGE = 512

    fun load(file: File): ImmutableImage = if (file.name.endsWith(".webp", ignoreCase = true)) {
        ImmutableImage.loader().withImageReaders(listOf(WebpImageReader())).fromFile(file)
    } else {
        ImmutableImage.loader().fromFile(file)
    }

    /** RGB signature of a fractional [box] (`x0, y0, x1, y1`) scaled to [w]×[h]. */
    fun feature(image: ImmutableImage, box: FloatArray, w: Int, h: Int, gray: Boolean = false): Feature {
        val x0 = (box[0] * image.width).roundToInt()
        val y0 = (box[1] * image.height).roundToInt()
        val x1 = (box[2] * image.width).roundToInt()
        val y1 = (box[3] * image.height).roundToInt()
        val region = image.subimage(x0, y0, x1 - x0, y1 - y0).scaleTo(w, h, ScaleMethod.Bilinear)
        val pixels = region.pixels()
        return if (gray) {
            Feature(
                FloatArray(pixels.size) {
                    (
                        pixels[it].red() * 0.299f + pixels[it].green() * 0.587f +
                            pixels[it].blue() * 0.114f
                        )
                },
            )
        } else {
            Feature(
                FloatArray(pixels.size * 3) {
                    val p = pixels[it / 3]
                    when (it % 3) {
                        0 -> p.red().toFloat()
                        1 -> p.green().toFloat()
                        else -> p.blue().toFloat()
                    }
                },
            )
        }
    }

    /** Downscales to [maxEdge] on the long edge (never upscales) and writes WebP at [QUALITY]. */
    fun writeWebp(source: File, target: File, maxEdge: Int) {
        val image = load(source)
        val long = maxOf(image.width, image.height)
        val scaled = if (long > maxEdge) {
            val factor = maxEdge.toDouble() / long
            image.scaleTo(
                (image.width * factor).roundToInt(),
                (image.height * factor).roundToInt(),
                ScaleMethod.Lanczos3,
            )
        } else {
            image
        }
        target.parentFile.mkdirs()
        scaled.output(WebpWriter.DEFAULT.withQ(QUALITY).withM(6), target)
    }
}
