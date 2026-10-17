package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.rules.LevelRow
import dev.quinton.goa.core.model.rules.RulesExcerpt
import dev.quinton.goa.core.model.rules.RulesReference
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

/** The text layer of a PDF, one string per PDF page (index 0 = PDF page 1). */
object PdfText {
    fun pages(pdf: File): List<String> = Loader.loadPDF(pdf).use { doc ->
        (1..doc.numberOfPages).map { page ->
            PDFTextStripper().apply {
                startPage = page
                endPage = page
            }.getText(doc)
        }
    }
}

/**
 * Comparison form for rulebook text. The PDF's text layer differs from the printed page in ways
 * that carry no meaning: line breaks and spacing (incl. letter-spaced table labels), ligatures,
 * curly quotes, bullets, case of small-caps headings, and hyphens/dashes (the layer drops
 * hyphens at line breaks — "doublelane" — and writes an em dash as "--"). All of those are
 * removed; every letter, digit and other punctuation mark must still match in order.
 */
object RulesText {
    private val dropped = Regex("[\\s\\-­‐‑‒–—―−•▪●�]")

    fun compact(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace('‘', '\'').replace('’', '\'').replace('‚', '\'').replace('′', '\'')
        .replace('“', '"').replace('”', '"').replace('„', '"').replace('″', '"')
        .replace(dropped, "")
}

/**
 * Excerpts reviewed by hand against the rendered page because the PDF's text layer is scrambled
 * there (overlapping text frames interleave their characters). A confirmation is tied to the exact
 * rulebook file: when the pinned SHA-256 changes, every confirmation fails until re-reviewed.
 */
@Serializable
data class RulesConfirmations(
    val comment: String? = null,
    val confirm: List<ConfirmedExcerpt> = emptyList(),
)

@Serializable
data class ConfirmedExcerpt(
    val page: Int,
    /** The excerpt's first words, verbatim. */
    val startsWith: String,
    /** SHA-256 of the rulebook PDF the excerpt was reviewed against. */
    val rulebookSha256: String,
    val reason: String,
)

/**
 * Drift guard for `rules-reference.json`: every excerpt (and heading) must appear, in
 * [RulesText.compact] form, on its printed page of the pinned rulebook, and the ladder numbers
 * must match the rulebook's three tables (p.13, p.15, p.23). Returns human-readable problems.
 */
class RulesReferenceCheck(
    private val reference: RulesReference,
    /** One entry per PDF page. */
    pdfPages: List<String>,
    private val rulebook: PinnedDocument,
    private val confirmations: RulesConfirmations,
) {
    private val pages = pdfPages.map(RulesText::compact)
    private val used = mutableSetOf<ConfirmedExcerpt>()

    fun problems(): List<String> {
        val out = mutableListOf<String>()
        if (reference.rulebook != rulebook.id) out += "rules-reference.json names rulebook '${reference.rulebook}'"
        if (reference.printing != rulebook.printing) {
            out +=
                "rules-reference.json is for printing ${reference.printing}; the pinned rulebook is ${rulebook.printing}"
        }
        reference.ladder.allExcerpts.forEach { out += excerptProblems("ladder", it) }
        reference.topics.forEach { topic ->
            if (topic.excerpts.isEmpty()) out += "${topic.id}: no excerpts"
            topic.excerpts.forEach { out += excerptProblems(topic.id, it) }
            val pagesUsed = topic.excerpts.map { it.page }.toSet()
            if (pagesUsed !=
                topic.pages.toSet()
            ) {
                out += "${topic.id}: pages ${topic.pages} but excerpts cite ${pagesUsed.sorted()}"
            }
        }
        out += ladderProblems(reference.ladder.levels)
        confirmations.confirm.filter { it !in used }.forEach {
            out += "Unused confirmation (p.${it.page} \"${it.startsWith}\"): remove it from rules-confirm.json"
        }
        return out
    }

    private fun excerptProblems(where: String, excerpt: RulesExcerpt): List<String> {
        val page = pageText(excerpt.page) ?: return listOf("$where: page ${excerpt.page} is not in the rulebook")
        val out = mutableListOf<String>()
        excerpt.heading?.let { heading ->
            if (RulesText.compact(heading) !in
                page
            ) {
                out += "$where p.${excerpt.page}: heading \"$heading\" not on the page"
            }
        }
        val found = RulesText.compact(excerpt.text) in page
        val confirmation = confirmations.confirm.firstOrNull {
            it.page == excerpt.page && excerpt.text.startsWith(it.startsWith)
        }
        when {
            confirmation != null -> {
                used += confirmation
                if (found) {
                    out +=
                        "$where p.${excerpt.page}: \"${preview(
                            excerpt,
                        )}\" now matches the text layer; drop its confirmation"
                } else if (!confirmation.rulebookSha256.equals(rulebook.sha256, ignoreCase = true)) {
                    out += "$where p.${excerpt.page}: \"${preview(excerpt)}\" was confirmed against another rulebook " +
                        "file; re-review it against the page image"
                }
            }

            !found -> out += "$where p.${excerpt.page}: excerpt not found on the page: \"${preview(excerpt)}\""
        }
        if (excerpt.table.isNotEmpty()) {
            if (excerpt.table.map { it.size }.distinct().size != 1) {
                out += "$where p.${excerpt.page}: table rows have different cell counts"
            }
            // The text layer reads a table row by row, left to right.
            val cells = excerpt.table.flatten().joinToString(" ")
            if (RulesText.compact(cells) !in page) {
                out += "$where p.${excerpt.page}: table not found on the page: \"${cells.take(PREVIEW)}\""
            }
        }
        return out
    }

    /** p.13 rows, p.15 rows and the p.23 summary table, as they appear in the text layer. */
    private fun ladderProblems(levels: List<LevelRow>): List<String> {
        if (levels.map { it.level } != (1..MAX_LEVEL).toList()) return listOf("ladder: levels must be 1..$MAX_LEVEL")
        val expected = listOf(
            LADDER_PAGE to "Lowest card Tier " + levels.joinToString(" ") { roman(it.lowestTier) },
            LADDER_PAGE to "Coins (Defeat)" + levels.joinToString(" ") { "${it.defeatCoins}" },
            LADDER_PAGE to "Assist Coins" + levels.joinToString(" ") { "${it.assistCoins}" },
            // The p.15 text layer puts the labels first, then the rows: max Tier, level, cost.
            LEVEL_UP_PAGE to "Max card Tier**",
            LEVEL_UP_PAGE to "Level Up cost",
            LEVEL_UP_PAGE to listOf(
                levels.joinToString(" ") { it.maxTier?.let(::roman) ?: "-" },
                levels.joinToString(" ") { "${it.level}" },
                levels.joinToString(" ") { it.levelUpCost?.toString() ?: "-" },
            ).joinToString(" "),
            SUMMARY_PAGE to levels.joinToString(" ") {
                "${it.level} ${it.defeatCoins} ${roman(it.lowestTier)} ${it.lifeCounters}"
            },
        )
        val out = expected.mapNotNull { (page, row) ->
            "ladder: row \"$row\" not on p.$page".takeIf { RulesText.compact(row) !in pageText(page).orEmpty() }
        }.toMutableList()
        // p.23 prints one column "Assist coins / Life counters", so both must agree.
        levels.filter { it.assistCoins != it.lifeCounters }.forEach {
            out += "ladder: level ${it.level} assist coins ${it.assistCoins} != Life counters ${it.lifeCounters} (p.23)"
        }
        return out
    }

    private fun pageText(printedPage: Int): String? = pages.getOrNull(printedPage + PDF_PAGE_OFFSET - 1)

    private fun preview(excerpt: RulesExcerpt) = excerpt.text.take(PREVIEW).replace('\n', ' ')

    companion object {
        /** PDF page = printed page + 1 (the cover has no number). */
        const val PDF_PAGE_OFFSET = 1
        const val MAX_LEVEL = 8
        private const val LADDER_PAGE = 13
        private const val LEVEL_UP_PAGE = 15
        private const val SUMMARY_PAGE = 23
        private const val PREVIEW = 60

        fun roman(tier: Int): String = when (tier) {
            1 -> "I"
            2 -> "II"
            3 -> "III"
            4 -> "IV"
            else -> error("Tier $tier")
        }

        /**
         * Runs the check against the pinned rulebook (downloaded into the cache if needed) and
         * returns its problems. [dumpDir], if set, receives each page's raw text layer as
         * `p<printed page>.txt` for authoring excerpts.
         */
        fun run(root: File, lock: SourcesLock, cache: SourceCache, dumpDir: File? = null): List<String> {
            val reference = RulesReference.parse(File(root, "app/src/main/assets/${RulesReference.PATH}").readText())
            val doc = lock.document(reference.rulebook)
            val pdfPages = PdfText.pages(cache.document(doc))
            dumpDir?.let { dir ->
                dir.mkdirs()
                pdfPages.forEachIndexed { i, text -> File(dir, "p${i + 1 - PDF_PAGE_OFFSET}.txt").writeText(text) }
            }
            val confirmFile = File(root, "tools/catalog-import/rules-confirm.json")
            val confirmations = if (confirmFile.isFile) {
                Json.decodeFromString(RulesConfirmations.serializer(), confirmFile.readText())
            } else {
                RulesConfirmations()
            }
            return RulesReferenceCheck(reference, pdfPages, doc, confirmations).problems()
        }
    }
}
