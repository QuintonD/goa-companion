package dev.quinton.goa.feature.catalog.model

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.catalog.Fixtures
import org.junit.Test

class CatalogSearchIndexTest {
    private val heroes = TestCatalog.heroes
    private val index = CatalogSearchIndex(heroes)

    @Test
    fun blankQueryReturnsEveryHeroUnscored() {
        val hits = index.search("   ")
        assertThat(hits.map { it.heroId }).containsExactlyElementsIn(heroes.map { it.id }).inOrder()
        assertThat(hits.all { it.score == 0 && it.field == null }).isTrue()
    }

    @Test
    fun matchesHeroNameCaseInsensitively() {
        val hits = index.search("TEST HERO 7")
        assertThat(hits.map { it.heroId }).containsExactly("test-hero-7")
        assertThat(hits.single().field).isEqualTo(MatchField.NAME)
    }

    @Test
    fun everyTokenMustMatch() {
        assertThat(index.search("placeholder nonexistentword")).isEmpty()
    }

    @Test
    fun matchesTitle() {
        val hits = index.search("placeholder")
        assertThat(hits).hasSize(heroes.size)
        assertThat(hits.map { it.field }.toSet()).containsExactly(MatchField.TITLE)
    }

    @Test
    fun matchesCardNameAndReportsTheCard() {
        val hits = index.search("red-2a")
        assertThat(hits).hasSize(heroes.size)
        hits.forEach { hit ->
            assertThat(hit.field).isEqualTo(MatchField.CARD_NAME)
            assertThat(hit.card?.id).isEqualTo("${hit.heroId}-red-2a")
        }
    }

    @Test
    fun matchesCardTextIncludingIconWordsAndReminderText() {
        val iconHits = index.search("attack icon")
        assertThat(iconHits).hasSize(heroes.size)
        assertThat(iconHits.first().field).isEqualTo(MatchField.CARD_TEXT)
        assertThat(iconHits.first().card).isNotNull()

        assertThat(index.search("reminder")).hasSize(heroes.size)
    }

    @Test
    fun matchesAliasesAndCompactNames() {
        val hero = Fixtures.heroWith(1) { copy(name = "NebKher", aliases = listOf("Grove Keeper")) }
        val local = CatalogSearchIndex(listOf(hero) + TestCatalog.hero(2))
        assertThat(local.search("neb kher").map { it.heroId }).containsExactly(hero.id)
        assertThat(local.search("grove keeper").single().field).isEqualTo(MatchField.ALIAS)
        assertThat(local.search("grovekeeper").single().heroId).isEqualTo(hero.id)
    }

    @Test
    fun ignoresAccentsAndTypographicApostrophes() {
        val hero = Fixtures.heroWith(1) {
            copy(
                name = "Nébula",
                cards = cards.mapIndexed { i, c ->
                    if (i ==
                        0
                    ) {
                        c.copy(text = "The hero’s shield")
                    } else {
                        c
                    }
                },
            )
        }
        val local = CatalogSearchIndex(listOf(hero))
        assertThat(local.search("NEBULA")).hasSize(1)
        assertThat(local.search("nébula")).hasSize(1)
        assertThat(local.search("hero's shield").single().field).isEqualTo(MatchField.CARD_TEXT)
    }

    @Test
    fun nameMatchesScoreAboveCardTextMatches() {
        val named = Fixtures.heroWith(1) { copy(name = "Tidecaller") }
        val texted = Fixtures.heroWith(2) {
            copy(cards = cards.mapIndexed { i, c -> if (i == 3) c.copy(text = "Summon the tidecaller wave.") else c })
        }
        val hits = CatalogSearchIndex(listOf(texted, named)).search("tidecaller").associateBy { it.heroId }
        assertThat(hits.getValue(named.id).score).isGreaterThan(hits.getValue(texted.id).score)
        assertThat(hits.getValue(texted.id).field).isEqualTo(MatchField.CARD_TEXT)
    }

    @Test
    fun searchesHandicapAndExtras() {
        assertThat(index.search("handicap").map { it.heroId }).hasSize(heroes.size)
        val large = CatalogSearchIndex(Fixtures.largeCatalog(3).heroes)
        val hits = large.search("extra page")
        assertThat(hits).hasSize(3)
        assertThat(hits.map { it.card?.id?.endsWith("extra-1") }.toSet()).containsExactly(true)
    }

    @Test
    fun searchReturnsWithin100MsOnA50HeroBy21CardCatalog() {
        val catalog = Fixtures.largeCatalog(50)
        assertThat(catalog.heroes.sumOf { it.allCards.size }).isEqualTo(50 * 21)
        val buildStart = System.nanoTime()
        val large = CatalogSearchIndex(catalog.heroes)
        val buildMs = (System.nanoTime() - buildStart) / 1_000_000.0
        val queries = listOf(
            "test", "hero 42", "card", "kari", "zzz-no-match", "range coin", "spaces", "synthetic 17-3",
            "minion", "e", "test hero 9 kari", "extra page", "a", "defeat minion mo",
        )
        repeat(WARMUP) { queries.forEach { large.search(it); large.searchCards(it) } }
        val worstMs = queries.flatMap { query ->
            (1..RUNS).map {
                val start = System.nanoTime()
                large.search(query)
                large.searchCards(query)
                (System.nanoTime() - start) / 1_000_000.0
            }
        }.max()
        assertThat(worstMs).isLessThan(100.0)
        assertThat(buildMs).isLessThan(1_000.0)
    }

    /** Synthetic stand-ins for the "fast travel" case (Emmitt's title and "Fast Forward" card). */
    private val travelHeroes = listOf(
        Fixtures.heroWith(1) {
            copy(
                name = "Alpha",
                title = "the Traveler",
                cards = cards.mapIndexed { i, c -> if (i == 0) c.copy(name = "Fast Forward") else c },
            )
        },
        Fixtures.heroWith(2) {
            copy(
                name = "Beta",
                cards = cards.mapIndexed { i, c ->
                    when (i) {
                        3 -> c.copy(text = "**This turn:** Enemy heroes in radius cannot fast travel.")
                        4 -> c.copy(text = "You may fast travel, then move 1 space.")
                        5 -> c.copy(text = "Move fast; you may travel through obstacles.")
                        else -> c
                    }
                },
            )
        },
    )

    @Test
    fun aCardPhraseOutranksAHeroMatchedThroughItsTitleAndAnotherCard() {
        val travel = CatalogSearchIndex(travelHeroes)
        val alpha = travel.search("fast travel").single { it.heroId == "test-hero-1" }
        assertThat(alpha.quality).isEqualTo(MatchQuality.PARTIAL)

        val cards = travel.searchCards("fast travel")
        // Both phrase cards first, then the card that has both words apart; never "Fast Forward".
        assertThat(cards.map { it.card.id }).containsExactly(
            "test-hero-2-red-2a",
            "test-hero-2-red-2b",
            "test-hero-2-red-3a",
        ).inOrder()
        assertThat(cards.map { it.quality })
            .containsExactly(MatchQuality.PHRASE, MatchQuality.PHRASE, MatchQuality.ALL_WORDS).inOrder()
        assertThat(cards.first().quality).isGreaterThan(alpha.quality)
    }

    @Test
    fun cardSearchListsEveryMatchingCardNotJustOnePerHero() {
        val cards = index.searchCards("reminder")
        assertThat(cards).hasSize(heroes.sumOf { it.allCards.size })
        assertThat(cards.map { it.card.id }.toSet()).hasSize(cards.size)
        assertThat(index.searchCards("  ")).isEmpty()
    }

    @Test
    fun aHeroNameNarrowsCardsToThatHero() {
        val cards = index.searchCards("test hero 7 red-2a")
        assertThat(cards.map { it.card.id }).containsExactly("test-hero-7-red-2a")
        assertThat(cards.single().quality).isEqualTo(MatchQuality.ALL_WORDS)
    }

    @Test
    fun aHeroNameMatchIsTheStrongestQuality() {
        assertThat(index.search("test hero 7").single().quality).isEqualTo(MatchQuality.NAME)
        assertThat(index.search("placeholder").first().quality).isEqualTo(MatchQuality.NAME)
    }

    private companion object {
        const val WARMUP = 5
        const val RUNS = 5
    }
}
