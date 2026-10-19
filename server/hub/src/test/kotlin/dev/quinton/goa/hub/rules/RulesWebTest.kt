package dev.quinton.goa.hub.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rules.DefeatExceptions
import dev.quinton.goa.core.domain.rules.LadderLookup
import dev.quinton.goa.core.domain.rules.RulesSearch
import dev.quinton.goa.core.model.rules.RulesExcerpt
import dev.quinton.goa.core.presentation.rules.RulesText
import dev.quinton.goa.core.presentation.rules.defeatNumbers
import dev.quinton.goa.core.presentation.rules.excerptLines
import dev.quinton.goa.core.presentation.rules.ladderCells
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.testHub
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The rules pages quote the bundled reference word for word, escaped, with the printed pages. */
class RulesWebTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun withHub(block: suspend RulesClient.(Hub) -> Unit) {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            val client = plainClient()
            RulesClient(client, client.signIn()).block(hub)
        }
    }

    class RulesClient(val client: HttpClient, val token: String) {
        suspend fun get(path: String, htmxTarget: String? = null) = client.get(path) {
            header(HttpHeaders.Cookie, cookie(token))
            if (htmxTarget != null) {
                header("HX-Request", "true")
                header("HX-Target", htmxTarget)
            }
        }

        suspend fun page(path: String, htmxTarget: String? = null): String {
            val response = get(path, htmxTarget)
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            return response.bodyAsText()
        }
    }

    private fun reference(hub: Hub) = requireNotNull(RulesFeature.reference(hub.catalog))

    /** The page's text with entities decoded: what a reader sees. */
    private fun shown(html: String) = html.replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun assertVerbatim(html: String, excerpt: RulesExcerpt) {
        val text = shown(html)
        excerptLines(excerpt.text).forEach { line -> assertThat(text).contains(line.text) }
        excerpt.heading?.let { assertThat(text).contains(it) }
        assertThat(html).contains("p. ${excerpt.page}")
        excerpt.table.flatten().forEach { cell -> assertThat(text).contains(cell) }
        // Nothing stored is ever emitted as markup.
        assertThat(Regex("<script(?![^>]*\\ssrc=)").containsMatchIn(html)).isFalse()
    }

    @Test
    fun `the topic list and its search are the phone's`() = withHub { hub ->
        val reference = reference(hub)
        val html = page("/rules")
        reference.topics.forEach { assertThat(html).contains("href=\"/rules/${it.id}\"") }
        assertThat(html).contains("href=\"/rules/levels\"")
        assertThat(shown(html)).contains(RulesText.LIST_SOURCE)

        val query = "respawn"
        val fragment = page("/rules?q=$query", htmxTarget = "rules-results")
        assertThat(fragment).doesNotContain("<html")
        val expected = RulesSearch.filter(reference.topics, query)
        val shown = Regex("href=\"/rules/([a-z-]+)\" class=\"row topic-row\"").findAll(fragment).map {
            it.groupValues[1]
        }
        assertThat(shown.toList()).containsExactlyElementsIn(expected.map { it.id }).inOrder()
        assertThat(fragment).contains("${expected.size} of ${reference.topics.size} topics")
        // Ladder words keep the Levels & defeats card; others hide it.
        assertThat(page("/rules?q=coin", "rules-results")).contains("/rules/levels")
        assertThat(page("/rules?q=zzzz", "rules-results")).contains(RulesText.EMPTY_TITLE)
    }

    @Test
    fun `every topic quotes its excerpts word for word with summaries labelled`() = withHub { hub ->
        reference(hub).topics.forEach { topic ->
            val html = page("/rules/${topic.id}")
            topic.excerpts.forEach { assertVerbatim(html, it) }
            topic.summary?.let {
                assertThat(html).contains("<p class=\"summary-label\">Summary</p>")
            }
            assertThat(html).contains(RulesText.pages(topic.pages))
        }
        assertThat(get("/rules/no-such-topic").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `levels and defeats shows the ladder, the picked level and the cards that change it`() = withHub { hub ->
        val ladder = reference(hub).ladder
        val html = page("/rules/levels?level=3")
        ladder.levels.forEach { row ->
            val cells = ladderCells(ladder, row)
            assertThat(html).contains("aria-label=\"Show level ${row.level}\"")
            cells.drop(1).forEach { assertThat(html).contains("<td>$it</td>") }
        }
        val row3 = requireNotNull(LadderLookup.row(ladder, 3))
        defeatNumbers(row3).forEach { assertThat(html).contains("<span class=\"big-digit\">${it.value}</span>") }
        // Picking the selected level again clears it.
        assertThat(html).contains("href=\"/rules/levels\" class=\"level selected\"")
        (ladder.defeat + ladder.levelUp + ladder.cardText).forEach { assertVerbatim(html, it) }
        val all = DefeatExceptions.find(hub.catalog.catalog.heroes)
        all.forEach { (hero, card) -> assertThat(shown(html)).contains("${hero.name} · ${card.name}") }
        assertThat(html).contains("Cards that change this")

        // A match's heroes limit the cards (the timer links with ?heroes=).
        val first = all.firstOrNull() ?: return@withHub
        val match = page("/rules/levels?heroes=${first.hero.id}")
        assertThat(match).contains("Cards in this match that change this")
        assertThat(shown(match)).contains("${first.hero.name} · ${first.card.name}")
        val fragment = page("/rules/levels?level=2", htmxTarget = "ladder")
        assertThat(fragment).doesNotContain("<html")
        assertThat(fragment).contains("id=\"ladder\"")
    }
}
