package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.Product
import dev.quinton.goa.core.model.catalog.ProductKind
import dev.quinton.goa.core.model.catalog.Ruling
import dev.quinton.goa.core.model.catalog.SourceRef
import java.io.File

/** Everything the import produced, before it is written to disk. */
data class ImportResult(
    val heroes: List<Hero>,
    val products: List<Product>,
    val reports: List<HeroReport>,
    val unknownIcons: Set<String>,
    /** Output asset path (relative to `assets/`) → source file it was made from. */
    val assetSources: Map<String, AssetSource>,
    /** Each source-1 logo checked against the card backs (ADR-042); a passing one is the hero's symbol. */
    val symbolChecks: List<DeckSymbols.Check> = emptyList(),
)

data class AssetSource(val source: PinnedSource, val path: String, val maxEdge: Int)

data class HeroReport(
    val heroId: String,
    val product: String,
    val disagreements: List<Disagreement>,
    val unresolved: List<Disagreement>,
    val numbers: CheckResult,
    val texts: CheckResult,
    val imageMatches: List<ImageMatcher.Match>,
    val missingImages: List<String>,
    val notes: List<String>,
    val verified: Boolean,
    val verifiedBecause: String,
)

/** All pinned source files the import reads, loaded once. */
private class SourceData(
    val soaHeroes: List<SoaHero>,
    val soaCards: Map<String, List<SoaCard>>,
    val soaSpells: List<SoaCard>,
    val soaPaths: Set<String>,
    /** Hero ID → goa2-faq hero entry. */
    val faqHeroes: Map<String, FaqHero>,
    val faqPaths: Set<String>,
    val commonFaq: Map<String, FaqEntry>,
    /** Lower-case CSV base name (`arien`, `gydion-spells`) → path in colefin8. */
    val coleFiles: Map<String, String>,
    /** Hero ID → (scan number, path) in dmrediger/goa2. */
    val scans: Map<String, List<Pair<Int, String>>>,
) {
    /** goa2-faq hero code (`ari`) → hero ID (`arien`). */
    val faqCodeToHero: Map<String, String> = faqHeroes.entries.associate { (heroId, h) -> h.id to heroId }
}

/** Raw cross-check material for one hero. */
private class HeroSources(
    val faqHero: FaqHero?,
    val faqCards: List<FaqCard>?,
    val faqSpells: List<FaqCard>?,
    val coleRows: List<ColeRow>?,
    val coleSpells: List<ColeRow>?,
)

class Importer(lock: SourcesLock, private val cache: SourceCache, private val overrides: Overrides) {
    private val soa = lock.source("stats-of-atlantis")
    private val dm = lock.source("dmrediger-goa2")
    private val faq = lock.source("goa2-faq")
    private val cole = lock.source("colefin8-data")

    companion object {
        val PRODUCTS = listOf(
            Product("core", "Core", ProductKind.BASE, released = 2022, bggId = 267609),
            Product("devoted", "Devoted", ProductKind.HERO_PACK, released = 2022, bggId = 356292),
            Product("defiant", "Defiant", ProductKind.HERO_PACK, released = 2022, bggId = 356290),
            Product("renowned", "Renowned", ProductKind.HERO_PACK, released = 2022, bggId = 356291),
            Product("arcane", "Arcane", ProductKind.HERO_PACK, released = 2025, bggId = 412138),
            Product("wayward", "Wayward", ProductKind.HERO_PACK, released = 2025, bggId = 412139),
        )
        const val PRINTING = 2
        const val LOW_CONFIDENCE = 1.3
        private const val ART_GRID = 12
        private val SCAN = Regex("""public/hero-cards/([^/]+)/([^/]+)/\2-(\d+)\.jpeg""")

        fun productId(pack: String): String = if (pack == "BASE") "core" else pack.lowercase()
    }

    private val unknownIcons = sortedSetOf<String>()
    private val assets = linkedMapOf<String, AssetSource>()

    fun run(withImages: Boolean): ImportResult {
        val data = loadSources()
        val imported = data.soaHeroes.map { importHero(it, data, withImages) }
        val order = PRODUCTS.map { it.id }
        val sorted = imported.sortedWith(compareBy({ order.indexOf(it.first.product) }, { it.first.id }))
        val symbolChecks = checkSymbols(data)
        val passed = symbolChecks.filter { it.pass }.map { it.heroId }.toSet()
        passed.forEach { id ->
            assets[symbolPath(id)] = AssetSource(soa, logoPath(id), SYMBOL_MAX_EDGE)
        }
        ITEM_ICONS.forEach { (stat, file) ->
            val path = "src/lib/images/$file"
            if (path in data.soaPaths) assets[itemIconPath(stat)] = AssetSource(soa, path, ITEM_ICON_MAX_EDGE)
        }
        return ImportResult(
            heroes = sorted.map { (hero, _) -> hero.copy(symbol = symbolPath(hero.id).takeIf { hero.id in passed }) },
            products = PRODUCTS,
            reports = sorted.map { it.second },
            unknownIcons = unknownIcons,
            assetSources = assets,
            symbolChecks = symbolChecks,
        )
    }

    private fun logoPath(heroId: String) = "src/lib/images/logos/$heroId.png"

    /** Every source-1 logo against every hero's card back (the last scan in source 2). */
    private fun checkSymbols(data: SourceData): List<DeckSymbols.Check> {
        val logos = data.soaHeroes.map { it.id }.filter { logoPath(it) in data.soaPaths }
            .associateWith { ImageIo.load(cache.file(soa, logoPath(it))) }
        val backs = data.scans.mapValues { (_, scans) -> ImageIo.load(cache.file(dm, scans.maxBy { it.first }.second)) }
        return DeckSymbols.check(logos, backs)
    }

    private fun loadSources(): SourceData {
        val soaHeroes = SoaParser.heroes(cache.text(soa, "src/states.ts"))
        val soaCards = SoaParser.heroCards(cache.text(soa, "src/new_heroes.json"))
        require(
            soaHeroes.map {
                it.id
            }.toSet() == soaCards.keys,
        ) { "states.ts and new_heroes.json list different heroes" }
        val colePaths = cache.tree(cole).tree.map { it.path }.filter { it.startsWith("server/") && it.endsWith(".csv") }
        val scans = cache.tree(dm).tree.mapNotNull { e ->
            SCAN.matchEntire(e.path)?.let { m -> Triple(m.groupValues[2], m.groupValues[3].toInt(), e.path) }
        }
        return SourceData(
            soaHeroes = soaHeroes,
            soaCards = soaCards,
            soaSpells = SoaParser.spells(cache.text(soa, "src/spells.json")),
            soaPaths = cache.tree(soa).tree.map { it.path }.toSet(),
            faqHeroes = FaqParser.heroes(cache.text(faq, "build/data/heroes.json"))
                .associateBy { it.cards.substringAfterLast('/').removeSuffix(".json") },
            faqPaths = cache.tree(faq).tree.map { it.path }.toSet(),
            commonFaq = FaqParser.faqs(cache.text(faq, "build/data/faqs/common.json")),
            coleFiles = colePaths.associateBy { it.removePrefix("server/").removeSuffix(".csv").lowercase() },
            scans = scans.groupBy({ it.first }, { it.second to it.third }),
        )
    }

    private fun heroSources(heroId: String, data: SourceData): HeroSources {
        val faqHero = data.faqHeroes[heroId]
        return HeroSources(
            faqHero = faqHero,
            faqCards = faqHero?.let { FaqParser.cards(cache.text(faq, "build/data/${it.cards}")) },
            faqSpells = faqHero?.spellbook?.let { FaqParser.cards(cache.text(faq, "build/data/$it")) },
            coleRows = data.coleFiles[heroId]?.let { Csv.parse(cache.text(cole, it)) },
            coleSpells = data.coleFiles["$heroId-spells"]?.let { Csv.parse(cache.text(cole, it)) },
        )
    }

    private fun importHero(sh: SoaHero, data: SourceData, withImages: Boolean): Pair<Hero, HeroReport> {
        val heroId = sh.id
        val notes = mutableListOf<String>()
        val mapped = CardMapping.heroCards(heroId, data.soaCards.getValue(heroId), unknownIcons) +
            if (heroId == "gydion") CardMapping.spellCards(heroId, data.soaSpells, unknownIcons) else emptyList()
        val src = heroSources(heroId, data)

        // Cross-checks run on the raw source-1 data, before alignment and overrides.
        val deck = mapped.filter { it.slot != Slot.EXTRA }.map { it.card }
        val spells = mapped.filter { it.slot == Slot.EXTRA }.map { it.card }
        val numbers = merge(
            CrossCheck.numbers(heroId, deck, src.coleRows),
            spells.takeIf { it.isNotEmpty() }?.let { CrossCheck.numbers(heroId, it, src.coleSpells) },
        )
        val texts = merge(
            CrossCheck.texts(heroId, deck, src.faqCards),
            spells.takeIf { it.isNotEmpty() }?.let { CrossCheck.texts(heroId, it, src.faqSpells) },
        )

        val baseHero = buildHero(sh, mapped, src, data, notes)
        val heroDisagreements = CrossCheck.hero(baseHero, src.faqHero)
        var hero = OverrideApplier.hero(baseHero, overrides.heroes[heroId])
        if (overrides.heroes[heroId]?.containsKey("aliases") != true) {
            hero = hero.copy(aliases = aliases(hero, sh, src.faqHero))
        }

        val matches = if (withImages) matchScans(heroId, mapped, data, notes) else emptyList()
        hero = attachImages(hero, mapped, matches, src.faqHero, data)

        val all = numbers.disagreements + texts.disagreements + heroDisagreements
        val unresolved = all.filterNot { overrides.resolves(it.entity, it.field) }
        val (verified, because) = verdict(heroId, numbers, texts, unresolved)
        val missingImages = hero.allCards.filter { it.image == null }.map { it.id }
        val report = HeroReport(
            heroId, hero.product, all, unresolved, numbers, texts, matches, missingImages, notes, verified, because,
        )
        return hero.copy(verified = verified) to report
    }

    private fun buildHero(
        sh: SoaHero,
        mapped: List<MappedCard>,
        src: HeroSources,
        data: SourceData,
        notes: MutableList<String>,
    ): Hero {
        // Prefer goa2-faq wording/paragraphs where its words equal source 1 (no print line wraps).
        val faqByKey = (src.faqCards.orEmpty() + src.faqSpells.orEmpty()).groupBy { CrossCheck.nameKey(it.name) }
        fun aligned(card: Card): Card {
            val handicap = card.id.endsWith("-handicap")
            val candidates = faqByKey[CrossCheck.nameKey(card.name)] ?: return card
            val ref = candidates.firstOrNull { (it.tier == "H") == handicap } ?: candidates.first()
            val reference = TextMarkup.fromFaq(ref.text, unknownIcons)
            val text = TextMarkup.aligned(card.text, reference)
            if (text == null && TextMarkup.agrees(card.text, reference)) {
                notes += "${card.id}: bold keywords do not fit goa2-faq paragraphs; source-1 paragraphs kept"
            }
            return card.copy(text = text ?: return card)
        }
        val cards = mapped.map { m -> OverrideApplier.card(aligned(m.card), overrides.cards[m.card.id]) to m.slot }
        return Hero(
            id = sh.id,
            name = sh.name,
            title = sh.title,
            product = productId(sh.pack),
            complexity = sh.stars,
            stats = HeroStats(
                attack = sh.stats.getOrNull(0),
                defense = sh.stats.getOrNull(1),
                initiative = sh.stats.getOrNull(2),
                movement = sh.stats.getOrNull(3),
            ),
            roles = sh.traits.map { CardMapping.role(it) },
            printing = PRINTING,
            cards = cards.filter { it.second == Slot.DECK }.map { it.first }.sortedWith(CardMapping.deckOrder),
            handicap = cards.singleOrNull { it.second == Slot.HANDICAP }?.first,
            extras = cards.filter { it.second == Slot.EXTRA }.map { it.first },
            faq = rulings(src.faqHero, sh.id, data),
            lore = src.faqHero?.lore?.trim(),
            verified = false,
            sources = sourceRefs(sh.id, src.faqHero != null, src.coleRows != null),
        )
    }

    /** Card scans (source 2) identified via source-1 artwork or `overrides.images`; portrait from source 3. */
    private fun attachImages(
        hero: Hero,
        mapped: List<MappedCard>,
        matches: List<ImageMatcher.Match>,
        faqHero: FaqHero?,
        data: SourceData,
    ): Hero {
        val bySlug = mapped.filter { it.artSlug != null }.associateBy { it.artSlug }
        val imageFor = matches.mapNotNull { m -> bySlug[m.art]?.let { it.card.id to m.scan } }.toMap() +
            overrides.images.filterKeys { it.startsWith("${hero.id}-") }.mapValues { it.value.path }
        imageFor.forEach { (cardId, path) ->
            assets["official/cards/$cardId.webp"] = AssetSource(dm, path, ImageIo.CARD_MAX_EDGE)
        }
        val portrait = portraitSource(hero.id, faqHero, data)
        if (portrait != null) assets["official/heroes/${hero.id}/portrait.webp"] = portrait

        fun withImage(card: Card) = card.copy(image = "official/cards/${card.id}.webp".takeIf { card.id in imageFor })
        return hero.copy(
            cards = hero.cards.map(::withImage),
            handicap = hero.handicap?.let(::withImage),
            extras = hero.extras.map(::withImage),
            portrait = portrait?.let { "official/heroes/${hero.id}/portrait.webp" },
        )
    }

    /** The `verified` rule of docs/DOMAIN.md#sources, with a human-readable reason. */
    private fun verdict(
        heroId: String,
        numbers: CheckResult,
        texts: CheckResult,
        unresolved: List<Disagreement>,
    ): Pair<Boolean, String> {
        val numbersOk = (numbers.present && numbers.unmatched.isEmpty()) || heroId in overrides.imageChecks
        val textsOk = texts.present && texts.unmatched.isEmpty()
        val problems = buildList {
            if (unresolved.isNotEmpty()) add("${unresolved.size} unresolved disagreement(s)")
            if (!numbersOk && !numbers.present) add("no number cross-check source (colefin8) and no image check")
            if (!numbersOk && numbers.present) add("${numbers.unmatched.size} card(s) missing in colefin8")
            if (!textsOk && !texts.present) add("no goa2-faq text")
            if (!textsOk && texts.present) add("${texts.unmatched.size} card(s) missing in goa2-faq")
        }
        val numbersHow = if (heroId in
            overrides.imageChecks
        ) {
            "numbers checked against card images"
        } else {
            "numbers agree with colefin8"
        }
        val because = problems.joinToString("; ").ifEmpty { "$numbersHow, text agrees with goa2-faq" }
        return problems.isEmpty() to because
    }

    private fun merge(a: CheckResult, b: CheckResult?): CheckResult = when (b) {
        null -> a
        else -> CheckResult(a.disagreements + b.disagreements, a.unmatched + b.unmatched, a.present && b.present)
    }

    private fun aliases(hero: Hero, sh: SoaHero, faqHero: FaqHero?): List<String> {
        val candidates = buildList {
            add("${sh.name} ${sh.title}")
            if (faqHero != null) {
                add("${faqHero.name} ${faqHero.title.replaceFirstChar { it.lowercase() }}")
                if (faqHero.name != hero.name) add(faqHero.name)
            }
        }
        return candidates.filter { !it.equals(hero.displayName, ignoreCase = true) }.distinct()
    }

    private fun rulings(faqHero: FaqHero?, heroId: String, data: SourceData): List<Ruling> {
        if (faqHero == null) return emptyList()
        val own = "build/data/faqs/$heroId.json".takeIf { it in data.faqPaths }
            ?.let { FaqParser.faqs(cache.text(faq, it)).values.toList() }.orEmpty()
        val shared = data.commonFaq.values.filter { e ->
            e.ref.any { data.faqCodeToHero[it.substringBefore('-')] == heroId }
        }
        fun ruling(
            e: FaqEntry,
            source: String,
        ) = Ruling(TextMarkup.plainRuling(e.q), TextMarkup.plainRuling(e.a), source)
        return own.map { ruling(it, "goa2-faq") } + shared.map { ruling(it, "goa2-faq (common)") }
    }

    private fun sourceRefs(heroId: String, hasFaq: Boolean, hasCole: Boolean): List<SourceRef> = buildList {
        add(SourceRef(soa.name, soa.blobUrl("src/new_heroes.json"), soa.pinned))
        if (hasFaq) add(SourceRef(faq.name, faq.blobUrl("build/data/cards/$heroId.json"), faq.pinned))
        if (hasCole) add(SourceRef(cole.name, "https://github.com/${cole.repo}/tree/${cole.sha}/server", cole.pinned))
        add(SourceRef(dm.name, "https://github.com/${dm.repo}/tree/${dm.sha}/public/hero-cards", dm.pinned))
    }

    private fun portraitSource(heroId: String, faqHero: FaqHero?, data: SourceData): AssetSource? {
        val faqPath = faqHero?.icon?.let { "public$it" }?.takeIf { it in data.faqPaths }
        if (faqPath != null) return AssetSource(faq, faqPath, ImageIo.PORTRAIT_MAX_EDGE)
        val soaPath = "src/lib/images/avatars/$heroId.webp"
        return if (soaPath in data.soaPaths) AssetSource(soa, soaPath, ImageIo.PORTRAIT_MAX_EDGE) else null
    }

    private fun matchScans(
        heroId: String,
        mapped: List<MappedCard>,
        data: SourceData,
        notes: MutableList<String>,
    ): List<ImageMatcher.Match> {
        val heroScans = data.scans[heroId].orEmpty()
        val withArt = mapped.filter { it.artSlug != null }
        if (heroScans.isEmpty() || withArt.isEmpty()) return emptyList()
        val arts = withArt.mapNotNull { m ->
            val path = "src/lib/images/cards/$heroId/${m.artSlug}.webp"
            if (path in data.soaPaths) {
                m.artSlug!! to
                    ImageIo.feature(ImageIo.load(cache.file(soa, path)), ImageMatcher.ART_BOX, ART_GRID, ART_GRID)
            } else {
                notes += "No source-1 artwork ${m.artSlug} for ${m.card.id}"
                null
            }
        }.toMap()
        // Scan 0 is the hero profile card (DOMAIN.md); it never shows a deck card.
        val candidates = heroScans.filter { it.first != 0 }.associate { (_, path) ->
            path to
                ImageIo.load(cache.file(dm, path))
        }
        val scanFeatures = candidates.mapValues { ImageIo.feature(it.value, ImageMatcher.ART_BOX, ART_GRID, ART_GRID) }
        val matches = resolveTwins(ImageMatcher.assign(arts, scanFeatures), withArt, arts) { scan ->
            ImageIo.feature(candidates.getValue(scan), ImageMatcher.BADGE_BOX, 16, 8, gray = true)
        }
        val unused = candidates.keys - matches.map { it.scan }.toSet()
        if (unused.size > 1) {
            val names = unused.sorted().joinToString { it.substringAfterLast('/') }
            notes += "Unmatched scans (expected only the card back): $names"
        }
        return matches
    }

    /** Basic card and Handicap card frequently share one illustration: decide by the top-right badge. */
    private fun resolveTwins(
        matches: List<ImageMatcher.Match>,
        withArt: List<MappedCard>,
        arts: Map<String, Feature>,
        badge: (String) -> Feature,
    ): List<ImageMatcher.Match> {
        val handicap = withArt.firstOrNull { it.source.handicapped } ?: return matches
        val twinSlug = if (handicap.source.color == "SILVER") "Silver" else "Gold"
        val otherSlug = if (twinSlug == "Gold") "Silver" else "Gold"
        val bySlug = matches.associateBy { it.art }
        val twin = bySlug[twinSlug]
        val h = bySlug["Handicap"]
        val other = bySlug[otherSlug]
        if (twin == null || h == null || other == null) return matches
        if (!ImageMatcher.sameArt(arts.getValue(twinSlug), arts.getValue("Handicap"), arts.values)) return matches
        val swap = ImageMatcher.shouldSwapTwins(badge(twin.scan), badge(h.scan), badge(other.scan))
        return matches.map {
            when (it.art) {
                twinSlug -> it.copy(scan = if (swap) h.scan else twin.scan, method = "badge")
                "Handicap" -> it.copy(scan = if (swap) twin.scan else h.scan, method = "badge")
                else -> it
            }
        }
    }
}

/** Writes images, deleting stale outputs; re-encodes only when the source or settings changed. */
class AssetWriter(private val assetsDir: File, private val cache: SourceCache, private val manifestFile: File) {
    /**
     * [extracted]: images that aren't files in a pinned repo (the rulebook's graphics, already
     * extracted to the cache) → (manifest key naming their origin, the extracted file, max edge).
     */
    fun write(assets: Map<String, AssetSource>, extracted: Map<String, ExtractedAsset> = emptyMap()): Int {
        val manifest = if (manifestFile.isFile) {
            manifestFile.readLines().filter { '\t' in it }.associate {
                it.substringBefore('\t') to
                    it.substringAfter('\t')
            }
        } else {
            emptyMap()
        }
        val next = sortedMapOf<String, String>()
        var encoded = 0
        assets.entries.toList().parallelStream().forEach { (out, src) ->
            val key = "${src.source.repo}@${src.source.sha}:${src.path}|q${ImageIo.QUALITY}|${src.maxEdge}"
            val target = File(assetsDir, out)
            if (!(target.isFile && manifest[out] == key)) {
                ImageIo.writeWebp(cache.file(src.source, src.path), target, src.maxEdge)
                synchronized(this) { encoded++ }
            }
            synchronized(next) { next[out] = key }
        }
        extracted.forEach { (out, e) ->
            val key = "${e.key}|q${ImageIo.QUALITY}|${e.maxEdge}"
            val target = File(assetsDir, out)
            if (!(target.isFile && manifest[out] == key)) {
                ImageIo.writeWebp(e.file, target, e.maxEdge)
                encoded++
            }
            next[out] = key
        }
        // Remove generated images that are no longer produced.
        (listOf("official/cards", "official/heroes") + UI_ASSET_DIRS).map {
            File(assetsDir, it)
        }.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "webp" }.forEach { f ->
                if (f.relativeTo(assetsDir).invariantSeparatorsPath !in next) f.delete()
            }
        }
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(next.entries.joinToString("\n") { "${it.key}\t${it.value}" } + "\n")
        return encoded
    }
}

/** An image made from a pinned document rather than a repo file (see [AssetWriter.write]). */
data class ExtractedAsset(val key: String, val file: File, val maxEdge: Int)
