package dev.quinton.goa.hub.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.presentation.catalog.CatalogBrowser
import dev.quinton.goa.core.presentation.catalog.CatalogControls
import dev.quinton.goa.core.presentation.catalog.CatalogFilter
import dev.quinton.goa.core.presentation.catalog.CatalogSort
import dev.quinton.goa.core.presentation.catalog.CatalogUiState
import dev.quinton.goa.core.presentation.catalog.DeckLayout
import dev.quinton.goa.core.presentation.catalog.HeroDetailUiState
import dev.quinton.goa.core.presentation.catalog.HeroPersonalStats
import dev.quinton.goa.core.presentation.catalog.printedValues
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.match
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.player
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.testHub
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import kotlinx.html.body
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Heroes pages show exactly the bundled catalog, through the same :core:presentation builders
 * as the phone (results compared with them, not with hand-written expectations).
 */
class HeroesWebTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun withHub(block: suspend HeroesClient.(Hub) -> Unit) {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            val client = plainClient()
            val token = client.signIn()
            HeroesClient(client, token).block(hub)
        }
    }

    class HeroesClient(val client: HttpClient, val token: String) {
        suspend fun page(path: String, htmxTarget: String? = null): String {
            val response = client.get(path) {
                header(HttpHeaders.Cookie, cookie(token))
                if (htmxTarget != null) {
                    header("HX-Request", "true")
                    header("HX-Target", htmxTarget)
                }
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            return response.bodyAsText()
        }
    }

    /** The visible text of [html] (entities decoded), to compare with stored text. */
    private fun text(html: String) = html.replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun heroNamesInGrid(html: String): List<String> =
        Regex("<h3 class=\"tile-title\">([^<]*)</h3>").findAll(html).map { text(it.groupValues[1]) }.toList()

    private fun state(
        hub: Hub,
        query: String,
        filter: CatalogFilter = CatalogFilter(),
        owned: Set<String> = emptySet(),
    ) =
        CatalogUiState.build(
            hub.catalog.catalog,
            HeroesFeature.index(hub.catalog.catalog),
            owned,
            query,
            CatalogControls(filter = filter, sort = CatalogSort.NAME),
        ) as CatalogUiState.Ready

    @Test
    fun `the grid lists every hero in the builder's order, and owned products limit it`() = withHub { hub ->
        val all = heroNamesInGrid(page("/heroes"))
        assertThat(all).containsExactlyElementsIn(state(hub, "").heroes.map { it.hero.name }).inOrder()
        assertThat(all).hasSize(hub.catalog.catalog.heroes.size)

        val device = hub.devices.list().single()
        val owned = setOf(hub.catalog.catalog.products.first().id)
        hub.deviceStore.write(device.id, SharedSlots.Settings, AppSettings(ownedProducts = owned))
        val ownedPage = page("/heroes")
        assertThat(heroNamesInGrid(ownedPage))
            .containsExactlyElementsIn(state(hub, "", owned = owned).heroes.map { it.hero.name }).inOrder()
        assertThat(ownedPage).contains("Include products you")
        // "Show all products" brings the others back (the phone's switch).
        val everything = heroNamesInGrid(page("/heroes?all=1"))
        assertThat(everything).hasSize(hub.catalog.catalog.heroes.size)
    }

    @Test
    fun `card search lists every matching card like the phone, and htmx gets only the results`() = withHub { hub ->
        val expected = state(hub, "block")
        val html = page("/heroes?q=block")
        val cards = Regex("<span class=\"card-result-name\">([^<]*)</span>").findAll(html).map {
            text(it.groupValues[1])
        }
        assertThat(cards.toList()).containsExactlyElementsIn(expected.cards.map { it.card.name }).inOrder()
        assertThat(html).contains(expected.resultSummary("block"))

        val fragment = page("/heroes?q=block", htmxTarget = "results")
        assertThat(fragment).doesNotContain("<html")
        assertThat(fragment).contains("id=\"results\"")
        assertThat(fragment).contains("hx-swap-oob")
        // A card's kind narrows a multi-word query ("brogan ultimate").
        val ultimate = state(hub, "brogan ultimate").cards.first().card.name
        assertThat(text(page("/heroes?q=brogan+ultimate"))).contains(ultimate)
    }

    @Test
    fun `filters in the URL give the builder's heroes and removable chips`() = withHub { hub ->
        val catalog = hub.catalog.catalog
        val role = catalog.heroes.first().roles.first()
        val filter = CatalogFilter(complexities = setOf(1, 2), roles = setOf(role))
        val html = page("/heroes?c=1&c=2&r=${role.name.lowercase()}")
        assertThat(heroNamesInGrid(html)).containsExactlyElementsIn(state(hub, "", filter).heroes.map { it.hero.name })
        assertThat(html).contains("aria-label=\"Remove filter ★ Challenging\"")
        assertThat(html).contains("Filters</span><span id=\"filter-count\"> · 2</span>")
    }

    @Test
    fun `search addresses drop the form's empty and default fields`() = withHub { _ ->
        suspend fun get(path: String, htmx: Boolean = false) = client.get(path) {
            header(HttpHeaders.Cookie, cookie(token))
            if (htmx) {
                header("HX-Request", "true")
                header("HX-Target", "results")
            }
        }
        // What the form sends without JS: a plain request is sent to the tidy address.
        val plain = get("/heroes?q=&a=&sort=name")
        assertThat(plain.status).isEqualTo(HttpStatusCode.Found)
        assertThat(plain.headers[HttpHeaders.Location]).isEqualTo("/heroes")
        val typed = get("/heroes?q=block&a=&sort=name&c=2&c=1")
        assertThat(typed.headers[HttpHeaders.Location]).isEqualTo("/heroes?q=block&c=1&c=2")
        // htmx (search as you type) answers at once and pushes the tidy address instead.
        val swap = get("/heroes?q=block&a=&sort=elo", htmx = true)
        assertThat(swap.status).isEqualTo(HttpStatusCode.OK)
        assertThat(swap.headers["HX-Push-Url"]).isEqualTo("/heroes?q=block&sort=elo")
        // An address that is already tidy is left alone.
        assertThat(get("/heroes?q=block&sort=elo").status).isEqualTo(HttpStatusCode.OK)
        assertThat(get("/heroes?q=block", htmx = true).headers["HX-Push-Url"]).isNull()
        val rules = client.get("/rules?q=") { header(HttpHeaders.Cookie, cookie(token)) }
        assertThat(rules.headers[HttpHeaders.Location]).isEqualTo("/rules")
    }

    @Test
    fun `a search is escaped wherever it is shown`() = withHub { _ ->
        val html = page("/heroes?q=%3Cscript%3Ealert(1)%3C%2Fscript%3E")
        assertThat(html).doesNotContain("<script>alert")
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;")
    }

    @Test
    fun `hero pages show every card with printed plus values, bold keywords, FAQ, trivia link`() = withHub { hub ->
        val catalog = hub.catalog.catalog
        for (hero in catalog.heroes) {
            val html = page("/heroes/${hero.id}")
            val shown = text(html)
            hero.allCards.forEach { assertThat(shown).contains(it.name) }
            hero.faq.forEach { assertThat(shown).contains(it.question) }
            assertThat(html).contains("/trivia?hero=${hero.id}")
            hero.cards.forEach { card ->
                card.printedValues().filter { it.plus }.forEach { value ->
                    assertThat(html).contains("aria-label=\"${value.spoken}\"")
                    assertThat(html).contains("<span>${value.printed}</span>")
                }
            }
            // Ultimate tiles show their text; bold keywords render bold.
            hero.cards.filter { it.tier == 4 }.forEach { ultimate ->
                CardText.parse(ultimate.text).filterIsInstance<CardText.Segment.Strong>().forEach { strong ->
                    val words = strong.segments.filterIsInstance<CardText.Segment.Text>().joinToString("") { it.value }
                    assertThat(shown).contains(words)
                }
            }
        }
        // At least one printed "+" value exists in the bundled data, so the loop above checked something.
        assertThat(catalog.heroes.flatMap { it.cards }.any { c -> c.printedValues().any { it.plus } }).isTrue()
    }

    @Test
    fun `bold keywords and icons in card text are marked up, never as raw markup`() {
        val html = createHTML().html {
            body { cardText("**This turn:** Gain 1 {coin} and ~(Reminder.) <b>x</b>\nNext {nope_icon}.") }
        }
        assertThat(html).contains("<strong>This turn:</strong>")
        assertThat(html).contains("role=\"img\" aria-label=\"Coin\"")
        assertThat(html).contains("<i class=\"reminder\">(Reminder.)</i>")
        assertThat(html).contains("&lt;b&gt;x&lt;/b&gt;")
        assertThat(html).contains("<br>")
        assertThat(html).contains("nope icon")
        assertThat(html).doesNotContain("**")
    }

    @Test
    fun `Gydion's spellbook is listed by tier`() = withHub { hub ->
        val gydion = hub.catalog.catalog.hero("gydion") ?: return@withHub
        val layout = DeckLayout.of(gydion)
        if (!layout.extrasAreSpellbook) return@withHub
        val shown = text(page("/heroes/gydion"))
        assertThat(shown).contains("Spellbook")
        layout.extras.forEach { assertThat(shown).contains(it.name) }
    }

    @Test
    fun `an unverified hero is badged`() {
        val hero = testCatalogHero().copy(verified = false)
        val state = HeroDetailUiState.Ready(hero, "Test Core", DeckLayout.of(hero), HeroPersonalStats.EMPTY)
        val html = createHTML().html { body { heroDetail(state) } }
        assertThat(html).contains("badge-pill unverified")
        assertThat(html).doesNotContain("badge-pill verified")
    }

    private fun testCatalogHero() = dev.quinton.goa.hub.catalog.HubCatalog().catalog.heroes.first()

    @Test
    fun `your table comes from the match log`() = withHub { hub ->
        val hero = hub.catalog.catalog.heroes.first()
        runBlocking {
            hub.groupStore.upsertPlayer(player("ann", "Ann"))
            hub.groupStore.upsertPlayer(player("bob", "Bob"))
            hub.groupStore.upsertMatch(
                match("m1", red = listOf("ann"), blue = listOf("bob")).let { m ->
                    // Both teams need a known hero for the hero side of the replay (EloCalculator.heroSpaceApplies).
                    val other = hub.catalog.catalog.heroes[1].id
                    m.copy(
                        participants = m.participants.mapIndexed { i, p ->
                            p.copy(
                                heroId = if (i ==
                                    0
                                ) {
                                    hero.id
                                } else {
                                    other
                                },
                            )
                        },
                    )
                },
            )
        }
        val stats = HeroRecords.current(dev.quinton.goa.hub.feature.HubContext(hub)).stats(hero.id)
        val html = page("/heroes/${hero.id}")
        assertThat(stats.hasGames).isTrue()
        stats.metrics.forEach { assertThat(text(html)).contains(it.value) }
        assertThat(html).contains("Ann")
    }

    @Test
    fun `the card viewer lists the deck in order with alt text and opens at the card`() = withHub { hub ->
        val hero = hub.catalog.catalog.heroes.first()
        val cards = DeckLayout.of(hero).orderedCards
        val target = cards[2]
        val html = page("/heroes/${hero.id}/cards/${target.id}")
        val ids = Regex("<figure class=\"viewer-card[^\"]*\" id=\"card-([^\"]+)\"").findAll(html).map {
            it.groupValues[1]
        }
        assertThat(ids.toList()).containsExactlyElementsIn(cards.map { it.id }).inOrder()
        cards.filter { it.image != null }.forEach { assertThat(html).contains("alt=\"${it.name}\"") }
        assertThat(html).contains("${hero.name} · 3 of ${cards.size}")
        assertThat(html).contains("<script src=\"/static/heroes/viewer.js")
        val missing = client.get("/heroes/${hero.id}/cards/nope") { header(HttpHeaders.Cookie, cookie(token)) }
        assertThat(missing.status).isEqualTo(HttpStatusCode.NotFound)
        val unknown = client.get("/heroes/nobody") { header(HttpHeaders.Cookie, cookie(token)) }
        assertThat(unknown.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `grid or list is saved like the phone's catalogListView`() = withHub { hub ->
        val response = client.submitForm(
            "/heroes/view",
            parameters {
                append("view", "list")
                append("back", "/heroes?q=a")
            },
        ) {
            header(HttpHeaders.Cookie, cookie(token))
            header(HttpHeaders.Origin, HUB_URL)
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.Found)
        assertThat(response.headers[HttpHeaders.Location]).isEqualTo("/heroes?q=a")
        val device = hub.devices.list().single()
        assertThat(hub.deviceStore.read(device.id, SharedSlots.Settings)?.catalogListView).isTrue()
        assertThat(page("/heroes")).contains("class=\"hero-list\"")
        // Only that field changes; a foreign "back" goes home.
        val offsite = client.submitForm(
            "/heroes/view",
            parameters {
                append("view", "grid")
                append("back", "https://example.com/")
            },
        ) {
            header(HttpHeaders.Cookie, cookie(token))
            header(HttpHeaders.Origin, HUB_URL)
        }
        assertThat(offsite.headers[HttpHeaders.Location]).isEqualTo("/heroes")
    }

    @Test
    fun `cards searched through the builder also appear in CatalogBrowser order`() = withHub { hub ->
        val catalog = hub.catalog.catalog
        val index = HeroesFeature.index(catalog)
        val heroes = CatalogBrowser.browse(catalog, index, "travel", CatalogFilter(), CatalogSort.NAME, emptySet())
        val expected = CatalogBrowser.cards(index, "travel", heroes).map { it.card.name }
        val html = page("/heroes?q=travel")
        val cards = Regex("<span class=\"card-result-name\">([^<]*)</span>").findAll(html).map {
            text(it.groupValues[1])
        }
        assertThat(cards.toList()).isEqualTo(expected)
    }
}
