package dev.quinton.goa.tools.catalogimport

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Small copies of the official images for the web app's lists (ADR-043 "Addendum (R3 first load)"):
 * a hero page's card grid shows each scan about 120 points wide, so a phone downloads a 384-pixel copy
 * instead of the full scan; the hero grid's deck symbols are 30 points, so 96 pixels. The full images
 * stay unmodified and are what the card viewer, the hero header and "Download everything" use; the
 * copies are derived (downscaled), never edited, and carry the same attribution (official/SOURCES.md).
 *
 *     WebThumbnails <official dir> <output dir>
 *
 * writes `cards/<id>.webp` and `heroes/<id>/symbol.webp` under the output directory (the web app puts
 * them at `official/thumbs/`). Run by `:tools:catalog-import:webThumbnails` for `:web:app`.
 */
object WebThumbnails {
    /** Width of a card thumbnail (the web app's `srcset` names it `384w`). */
    const val CARD_WIDTH = 384

    /** Edge of a deck symbol thumbnail (square). */
    const val SYMBOL_EDGE = 96

    /** What to make: source file under the official dir → (path under the output, long edge). */
    fun plan(official: File): List<Pair<File, Pair<String, Int>>> {
        val cards = official.resolve("cards").listFiles { f -> f.extension == "webp" }.orEmpty().sortedBy { it.name }
            .map { card -> card to ("cards/${card.name}" to cardLongEdge(card)) }
        val symbols = official.resolve("heroes").listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
            .mapNotNull { hero -> hero.resolve("symbol.webp").takeIf { it.isFile } }
            .map { symbol -> symbol to ("heroes/${symbol.parentFile.name}/symbol.webp" to SYMBOL_EDGE) }
        return cards + symbols
    }

    /** Cards are 546 × 768 (upright) or 737 × 510 (Gydion's spells): [CARD_WIDTH] wide either way. */
    private fun cardLongEdge(card: File): Int {
        val image = ImageIo.load(card)
        return if (image.height >= image.width) {
            CARD_WIDTH * image.height / image.width
        } else {
            CARD_WIDTH
        }
    }

    fun write(official: File, output: File, threads: Int = 3) {
        output.deleteRecursively()
        val pool = Executors.newFixedThreadPool(threads)
        val jobs = plan(official).map { (source, target) ->
            pool.submit { ImageIo.writeWebp(source, output.resolve(target.first), target.second) }
        }
        pool.shutdown()
        jobs.forEach { it.get() }
        pool.awaitTermination(1, TimeUnit.MINUTES)
    }
}

fun main(args: Array<String>) {
    require(args.size == 2) { "usage: WebThumbnails <official dir> <output dir>" }
    WebThumbnails.write(File(args[0]), File(args[1]))
}
