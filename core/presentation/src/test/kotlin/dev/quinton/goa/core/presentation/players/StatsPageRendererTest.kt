package dev.quinton.goa.core.presentation.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.meta.GroupStats
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.RatingsSnapshot
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.presentation.players.Fixtures.ann
import dev.quinton.goa.core.presentation.players.Fixtures.blue
import dev.quinton.goa.core.presentation.players.Fixtures.bob
import dev.quinton.goa.core.presentation.players.Fixtures.cid
import dev.quinton.goa.core.presentation.players.Fixtures.match
import dev.quinton.goa.core.presentation.players.Fixtures.red
import java.io.File
import org.junit.Test

class StatsPageRendererTest {
    private fun snapshot(matches: List<Match>, drafts: List<DraftRecord> = emptyList()): RatingsSnapshot {
        val ratings = EloCalculator.replay(matches, EloConfig.DEFAULT)
        val meta = MetaStats.compute(matches, drafts)
        return RatingsSnapshot(ratings, meta, EloConfig.DEFAULT, GroupStats.compute(matches, ratings))
    }

    private fun page(
        players: List<Player> = Fixtures.players,
        matches: List<Match> = Fixtures.matches,
        drafts: List<DraftRecord> = Fixtures.drafts,
    ): StatsPage = StatsPage.build(
        players = players,
        matches = matches,
        snapshot = snapshot(matches, drafts),
        catalog = TestCatalog.catalog,
        nowMillis = Fixtures.at(17, 12),
        appVersion = "0.1.0",
    )

    private fun render(page: StatsPage = page()) = StatsPageRenderer.render(page).also(::assertWellFormed)

    /** The part of [html] between [start] and the next [end]. */
    private fun segment(html: String, start: String, end: String): String {
        val from = html.indexOf(start)
        assertThat(from).isAtLeast(0)
        return html.substring(from, html.indexOf(end, from))
    }

    @Test
    fun everyUserEnteredStringIsEscaped() {
        val evil = ann.copy(name = "<script>alert('x')</script>")
        val amp = bob.copy(name = "Tom & \"Jerry\"")
        val players = listOf(evil, amp, cid, Fixtures.dee)
        val matches = Fixtures.matches.map {
            if (it.id == "m07") it.copy(map = "<b>Map</b>", notes = "</li><img src=x onerror=alert(1)> & more") else it
        }
        val html = render(page(players, matches))

        assertThat(html).doesNotContain("<script")
        assertThat(html).doesNotContain("<img")
        assertThat(html).doesNotContain("<b>")
        assertThat(html).contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;")
        assertThat(html).contains("Tom &amp; &quot;Jerry&quot;")
        assertThat(html).doesNotContain("Tom & ")
        assertThat(html).contains("&lt;b&gt;Map&lt;/b&gt;")
        assertThat(html).contains("“&lt;/li&gt;&lt;img src=x onerror=alert(1)&gt; &amp; more”")
        // Every ampersand left in the page starts an entity.
        assertThat(Regex("&(?!amp;|lt;|gt;|quot;|#39;)").find(html)).isNull()
    }

    @Test
    fun pageIsSelfContainedWithLanguageViewportAndDatedTitle() {
        val html = render()
        assertThat(html).startsWith("<!DOCTYPE html>\n<html lang=\"en\">")
        assertThat(html).contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        assertThat(html).contains("<meta charset=\"utf-8\">")
        assertThat(html).contains("<title>Guards of Atlantis stats · 2026-10-17</title>")
        assertThat(html).contains("prefers-color-scheme:dark")
        assertThat(html).contains("@media print")
        // No network: no scripts, links, sources, imports or fonts; url() only for the inline gradient.
        listOf("http:", "https:", "//", "<script", "<link", "src=", "@import", "@font-face").forEach {
            assertThat(html.replace("<!--", "")).doesNotContain(it)
        }
        assertThat(Regex("url\\((?!#goa-bronze\\))").find(html)).isNull()
        assertThat(html).contains(
            "Generated Sat 17 Oct 2026 by Guards of Atlantis Companion 0.1.0, ratings as of the last logged match " +
                "(Thu 15 Oct 2026).",
        )
    }

    @Test
    fun emptyGroupRendersEveryEmptyState() {
        val html = render(page(players = emptyList(), matches = emptyList(), drafts = emptyList()))
        assertThat(html).contains("No rated matches yet.")
        assertThat(html).contains("The meta is still forming.")
        assertThat(html).contains("No matches logged yet.")
        assertThat(html).doesNotContain("id=\"group\"")
        assertThat(html).doesNotContain("id=\"best-heroes\"")
        assertThat(html).contains("ratings as of the last logged match.</p>")
        assertThat(html).contains("0 rated matches")
    }

    @Test
    fun heroesBelowTheMinimumSampleAreListedButNotRanked() {
        val p = page()
        assertThat(p.meta.unranked).isNotEmpty()
        val html = render(p)
        val ranked = segment(html, "<h3>Ranked</h3>", "</table>")
        val unranked = segment(html, "<h3>Not enough games yet</h3>", "</table>")
        p.meta.ranked.forEachIndexed { i, row ->
            assertThat(ranked).contains("<td class=\"n rank\">${i + 1}</td><th scope=\"row\">${row.name}</th>")
        }
        p.meta.unranked.forEach { row ->
            assertThat(unranked).contains("<tr><th scope=\"row\">${row.name}</th>")
            assertThat(ranked).doesNotContain(">${row.name}<")
        }
        assertThat(unranked).doesNotContain("class=\"n rank\"")
        assertThat(html).contains("Ranking needs 5 games.")
    }

    @Test
    fun doublePilotIsShownOncePerSeatWithOneRatingChange() {
        val m = match("dp", 16, Team.BLUE, red(bob, 3), red(cid, 4), blue(ann, 1), blue(ann, 2))
        val html = render(page(players = listOf(ann, bob, cid), matches = listOf(m), drafts = emptyList()))
        val log = segment(html, "<ol class=\"matches\">", "</ol>")
        assertThat(Regex("<span class=\"pilot\">Ann</span>").findAll(log).count()).isEqualTo(2)
        assertThat(Regex("Double pilot").findAll(log).count()).isEqualTo(2)
        assertThat(log).contains("Test Hero 1")
        assertThat(log).contains("Test Hero 2")
        // Three players, four seats: each player's rating change appears once.
        assertThat(Regex("<span class=\"num (up|down|muted)\">").findAll(log).count()).isEqualTo(3)
        // The leaderboard counts Ann once.
        val board = segment(html, "<section id=\"leaderboard\"", "</section>")
        assertThat(Regex("<th scope=\"row\">Ann").findAll(board).count()).isEqualTo(1)
    }

    @Test
    fun unknownHeroesReadAsTheAppShowsThem() {
        val m = match(
            "u1",
            16,
            Team.RED,
            red(ann, 1),
            Participant(bob.id, "retired-hero", Team.RED, false),
            blue(cid, null),
            blue(Fixtures.dee, 2),
        )
        val html = render(page(matches = listOf(m), drafts = emptyList()))
        val log = segment(html, "<ol class=\"matches\">", "</ol>")
        assertThat(Regex("Unknown hero").findAll(log).count()).isEqualTo(2)
        assertThat(html).doesNotContain("retired-hero")
    }

    @Test
    fun matchLogIsCappedAndTheFileStaysSmall() {
        val roster = (1..12).map {
            Player("p$it", "Player number $it", 0xFF3FC1B0, avatarId = it, createdAt = it.toLong())
        }
        val matches = (0 until 500).map { i ->
            val seats = (0 until 6).map { s -> roster[(i + s * 5) % roster.size] }.distinct().take(4)
            val heroes = (0 until 4).map { s -> (i + s * 7) % 20 + 1 }.distinct()
            Match(
                id = "big-$i",
                createdAt = Fixtures.at(1) + i * 3_600_000L,
                playedAt = Fixtures.at(1) + i * 3_600_000L,
                status = MatchStatus.COMPLETED,
                mode = GameMode.QUICK,
                winner = if (i % 3 == 0) Team.BLUE else Team.RED,
                winCondition = WinCondition.THRONE,
                durationMin = 50,
                notes = "A fairly long note about how this particular match went, to pad the size out.",
                participants = seats.mapIndexed { s, p ->
                    val team = if (s % 2 == 0) Team.RED else Team.BLUE
                    Participant(p.id, Fixtures.hero(heroes[s % heroes.size]), team, false)
                },
            )
        }
        val html = render(page(players = roster, matches = matches, drafts = emptyList()))
        assertThat(Regex("<li class=\"card match\">").findAll(html).count()).isEqualTo(StatsPage.MATCH_LOG_CAP)
        assertThat(html).contains("The newest 100 of 500 matches, newest first.")
        assertThat(html.toByteArray(Charsets.UTF_8).size).isLessThan(1_000_000)
        File("build/stats-page").mkdirs()
        File("build/stats-page/sample-500-matches.html").writeText(html, Charsets.UTF_8)
    }

    /** Writes a sample page from the fixtures (with hostile names) for a manual look. */
    @Test
    fun rendersSamplePageIntoBuild() {
        val players = Fixtures.players.map {
            when (it.id) {
                bob.id -> it.copy(name = "Bob <script>")
                cid.id -> it.copy(name = "Cid & Co")
                else -> it
            }
        }
        val matches = Fixtures.matches.map {
            if (it.id == "m07") it.copy(map = "Dueling <grounds>") else it
        }
        val html = render(page(players, matches))
        File("build/stats-page").mkdirs()
        File("build/stats-page/sample.html").writeText(html, Charsets.UTF_8)
        assertThat(html).contains("Bob &lt;script&gt;")
        assertThat(html).contains("Cid &amp; Co")
    }

    /** Balanced, correctly nested tags (void and self-closed elements aside); no stray `<` in text. */
    private fun assertWellFormed(html: String) {
        val void = setOf("meta", "br", "hr", "img", "input", "link", "wbr")
        val stack = ArrayDeque<String>()
        val body = html.removePrefix("<!DOCTYPE html>")
        val tag = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:\\s[^<>]*)?)>")
        var last = 0
        for (m in tag.findAll(body)) {
            val between = body.substring(last, m.range.first)
            assertThat(between).doesNotContain("<")
            assertThat(between).doesNotContain(">")
            last = m.range.last + 1
            val (closing, name, attrs) = m.destructured
            when {
                closing.isNotEmpty() -> assertThat(stack.removeLastOrNull()).isEqualTo(name)
                attrs.endsWith("/") || name in void -> Unit
                else -> stack.addLast(name)
            }
        }
        assertThat(stack).isEmpty()
    }
}
