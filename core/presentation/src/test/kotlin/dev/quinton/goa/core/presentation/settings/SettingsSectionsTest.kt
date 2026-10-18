package dev.quinton.goa.core.presentation.settings

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Product
import dev.quinton.goa.core.model.catalog.ProductKind
import dev.quinton.goa.core.model.settings.AppSettings
import org.junit.Test

/** Settings sections shared by the phone's Settings and the hub's `/settings` (ADR-030 §1). */
class SettingsSectionsTest {
    private val ids = listOf("core", "devoted", "arcane")
    private val all = ids.toSet()
    private val catalog = Catalog(
        dataVersion = 1,
        printing = 2,
        products = ids.mapIndexed { i, id ->
            Product(
                id,
                id.replaceFirstChar(Char::uppercase),
                ProductKind.entries.first(),
                2022 + i,
            )
        },
        heroes = emptyList(),
    )

    @Test
    fun emptyMeansEveryProductAndTheRowsSaySo() {
        val options = OwnedProducts.options(AppSettings(), catalog)
        assertThat(options.map { it.owned }).containsExactly(true, true, true)
        assertThat(OwnedProducts.summary(options, ownsEverything = true)).contains("All heroes")
        val narrowed = OwnedProducts.options(AppSettings(ownedProducts = setOf("core")), catalog)
        assertThat(OwnedProducts.summary(narrowed, ownsEverything = false)).isEqualTo("1 of 3 products owned")
        assertThat(OwnedProducts.detail(narrowed[0])).isEqualTo("2022")
        assertThat(OwnedProducts.detail(narrowed[0].copy(heroCount = 7))).isEqualTo("2022 · 7 heroes")
        assertThat(OwnedProducts.detail(narrowed[0].copy(released = null))).isNull()
    }

    @Test
    fun togglingNeverEmptiesThePoolAndAllTickedIsEverything() {
        val start = AppSettings()
        val oneOff = OwnedProducts.toggle(start, all, "devoted")
        assertThat(oneOff.ownedProducts).containsExactly("core", "arcane")
        assertThat(OwnedProducts.toggle(oneOff, all, "devoted").ownedProducts).isEmpty()
        val last = AppSettings(ownedProducts = setOf("core"))
        assertThat(OwnedProducts.toggle(last, all, "core")).isEqualTo(last)
        // Switching "everything" off keeps every product ticked.
        assertThat(OwnedProducts.setOwnsEverything(start, all, false).ownedProducts).isEqualTo(all)
        assertThat(OwnedProducts.setOwnsEverything(last, all, true).ownedProducts).isEmpty()
    }

    @Test
    fun aSubmittedProductListFollowsTheSameRules() {
        val narrowed = AppSettings(ownedProducts = setOf("core", "devoted"))
        assertThat(OwnedProducts.fromForm(narrowed, all, everything = false, ticked = setOf("core", "x")).ownedProducts)
            .containsExactly("core")
        // The last product can't be unticked; all of them is "everything".
        assertThat(OwnedProducts.fromForm(narrowed, all, everything = false, ticked = emptySet())).isEqualTo(narrowed)
        assertThat(OwnedProducts.fromForm(narrowed, all, everything = false, ticked = all).ownedProducts).isEmpty()
        assertThat(
            OwnedProducts.fromForm(narrowed, all, everything = true, ticked = emptySet()).ownedProducts,
        ).isEmpty()
        // Switching "everything" off (the products were disabled, so none are posted) ticks them all.
        assertThat(OwnedProducts.fromForm(AppSettings(), all, everything = false, ticked = emptySet()).ownedProducts)
            .isEqualTo(all)
    }

    @Test
    fun ratingConstantsCoverEveryFieldWithTheAppsRangesAndReadings() {
        assertThat(RatingConstants.all.map { it.key }.toSet()).hasSize(RatingConstants.all.size)
        val tuned = RatingConstants.all.fold(EloConfig.DEFAULT) { c, k -> k.set(c, k.range.endInclusive) }
        // Every constant writes its own field: setting each to its maximum changes all eleven.
        assertThat(RatingConstants.all.count { it.get(tuned) != it.get(EloConfig.DEFAULT) }).isEqualTo(11)
        val byKey = RatingConstants.all.associateBy { it.key }
        assertThat(byKey.getValue("doublePilotWeight").format(0.5)).isEqualTo("×1.5")
        assertThat(byKey.getValue("seatAdvantage").format(-15.0)).isEqualTo("−15 pts")
        assertThat(byKey.getValue("seatAdvantage").format(10.0)).isEqualTo("+10 pts")
        assertThat(byKey.getValue("provisionalGames").format(1.0)).isEqualTo("1 game")
        assertThat(byKey.getValue("heroInfluence").format(0.05)).isEqualTo("0.05")
        assertThat(byKey.getValue("quickMultiplier").format(1.0)).isEqualTo("×1.0")
        assertThat(RatingConstants.replayNote(1, 0)).contains("(1 rated match)")
        assertThat(RatingConstants.replayNote(4, 2)).contains("(4 rated matches, 2 pending or without a result)")
    }

    @Test
    fun aSubmittedFormSnapsToTheSlidersGridAndKeepsWhatItCantRead() {
        val values = mapOf(
            "kEstablished" to "33",
            "heroInfluence" to "0.33",
            "seatAdvantage" to "-500",
            "provisionalGames" to "12.4",
            "initialRating" to "abc",
            "longMultiplier" to "NaN",
        )
        val config = RatingConstants.apply(EloConfig.DEFAULT, values)
        assertThat(config.kEstablished).isEqualTo(35.0)
        assertThat(config.heroInfluence).isWithin(1e-9).of(0.35)
        assertThat(config.seatAdvantage).isEqualTo(-100.0)
        assertThat(config.provisionalGames).isEqualTo(12)
        assertThat(config.initialRating).isEqualTo(EloConfig.DEFAULT.initialRating)
        assertThat(config.longMultiplier).isEqualTo(EloConfig.DEFAULT.longMultiplier)
        assertThat(RatingConstants.apply(EloConfig.DEFAULT, emptyMap())).isEqualTo(EloConfig.DEFAULT)
    }
}
