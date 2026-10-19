package dev.quinton.goa.hub.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.players.Leaderboard
import dev.quinton.goa.core.presentation.players.MatchEditor
import dev.quinton.goa.core.presentation.players.Meta
import dev.quinton.goa.core.presentation.players.PlayerDetail
import dev.quinton.goa.core.presentation.players.formatPercent
import dev.quinton.goa.core.presentation.players.formatRating
import dev.quinton.goa.core.presentation.players.winLoss
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.players.PlayersTestGroup.ann
import dev.quinton.goa.hub.players.PlayersTestGroup.annB
import dev.quinton.goa.hub.players.PlayersTestGroup.bob
import dev.quinton.goa.hub.players.PlayersTestGroup.fay
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.testHub
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Every Players page renders, empty and with a typical group, and shows exactly what the shared
 * builders compute (the phone's numbers, ADR-030 §1): the tests ask `core:presentation` for the
 * expected values over the hub's own data rather than restating any rule.
 */
class PlayersPagesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock()

    private fun seeded(): Hub = testHub(tmp.root.toPath(), clock).also { runBlocking { PlayersTestGroup.seed(it) } }

    private fun expected(hub: Hub) = PlayersData.of(HubContext(hub))

    /** Text as kotlinx.html writes it (names, notes and numbers are escaped the same way). */
    private fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    @Test
    fun `every page renders for an empty group`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val token = plainClient().signIn()
            val expectations = mapOf(
                "/players" to "Gather your guards",
                "/players/log" to "No matches yet",
                "/players/meta" to "The meta is still forming",
                "/players/same-people" to "No look-alikes",
                "/players/new" to "New player",
                "/players/matches/new" to "Log match",
            )
            expectations.forEach { (path, text) ->
                val response = client.page(path, token)
                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                val html = response.bodyAsText()
                assertThat(html).contains(text)
                assertThat(html).contains("/static/players/players.css")
                assertThat(html).doesNotContain(" style=")
                assertThat(html).doesNotContain("<script>")
            }
            assertThat(client.page("/players/nobody", token).status).isEqualTo(HttpStatusCode.NotFound)
            assertThat(client.page("/players/matches/none", token).status).isEqualTo(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `the leaderboard shows the builder's ranks, ratings, records and trends`() {
        val hub = seeded()
        val view = expected(hub)
        val state = Leaderboard.build(view.players, view.snapshot, showArchived = false)
        hubTest(hub) {
            val token = plainClient().signIn()
            val html = client.page("/players", token).bodyAsText()
            assertThat(state.ranked).isNotEmpty()
            state.ranked.forEach { row ->
                assertThat(html).contains(esc(Leaderboard.spoken(row)))
                assertThat(html).contains(">${formatRating(row.rating.value)}<")
                assertThat(html).contains(winLoss(row.rating.wins, row.rating.games))
            }
            assertThat(html).contains("${state.ratedMatches} rated matches")
            // Ann's duplicate is merged away; Fay is archived and hidden until asked.
            assertThat(html).doesNotContain("Ann B")
            assertThat(html).doesNotContain(">Fay<")
            assertThat(html).contains("Show archived players (1)")
            val archived = client.page("/players?archived=1", token).bodyAsText()
            assertThat(archived).contains(">Fay<")
            assertThat(archived).contains("Hide archived players")
            assertThat(html).contains("Find duplicates")
        }
    }

    @Test
    fun `this browser's player is marked You`() {
        val hub = seeded()
        hubTest(hub) {
            val token = plainClient().signIn()
            val device = hub.devices.list().single()
            // "Me" is read through the merges: the duplicate stands for Ann.
            hub.deviceStore.write(device.id, SharedSlots.Me, annB.id)
            val html = client.page("/players", token).bodyAsText()
            assertThat(html).contains("Ann, you, rating")
            assertThat(html).contains(">You<")
        }
    }

    @Test
    fun `a player page shows rating, chart with a table, heroes, records and recent matches`() {
        val hub = seeded()
        val view = expected(hub)
        val state = PlayerDetail.build(ann.id, view.players, view.matches, view.snapshot, view.catalog, view.zone)
        hubTest(hub) {
            val token = plainClient().signIn()
            val html = client.page("/players/${ann.id}", token).bodyAsText()
            assertThat(html).contains("Rank ${state.rank} of ${state.rankedPlayers}")
            assertThat(html).contains(">${formatRating(state.rating.value)}<")
            assertThat(html).contains("<svg class=\"pl-chart\"")
            assertThat(html).contains("Rating history as a table")
            assertThat(html).contains(state.heroesTitle)
            state.heroes.forEach { assertThat(html).contains(esc(it.name)) }
            assertThat(html).contains(">${state.doublePilotGames}<")
            assertThat(html).contains("How Ann wins")
            assertThat(html).contains("All ${state.totalMatches}")
            state.recentMatches.forEach { assertThat(html).contains("id=\"match-${PlayersPaths.anchor(it.id)}\"") }
            assertThat(html).contains("Invite Ann")
            assertThat(html).contains("/group/invite?player=p-ann")
            assertThat(html).contains("Merge into…")
            assertThat(html).contains("Archive player")
            // The duplicate's page leads to Ann's.
            val merged = plainClient().page("/players/${annB.id}", token)
            assertThat(merged.status).isEqualTo(HttpStatusCode.Found)
            assertThat(merged.headers[HttpHeaders.Location]).isEqualTo("/players/${ann.id}")
            // An archived player can be restored, and isn't invited.
            val fayPage = client.page("/players/${fay.id}", token).bodyAsText()
            assertThat(fayPage).contains("Restore player")
            assertThat(fayPage).contains(">Archived<")
            assertThat(fayPage).doesNotContain("Invite Fay")
        }
    }

    @Test
    fun `your own page offers no invite, and a player with no games shows the empty chart`() {
        val hub = seeded()
        runBlocking { hub.groupStore.upsertPlayer(dev.quinton.goa.hub.player("p-gus", "Gus")) }
        hubTest(hub) {
            val token = plainClient().signIn()
            hub.deviceStore.write(hub.devices.list().single().id, SharedSlots.Me, bob.id)
            assertThat(client.page("/players/${bob.id}", token).bodyAsText()).doesNotContain("Invite Bob")
            val gus = client.page("/players/p-gus", token).bodyAsText()
            assertThat(gus).contains("No rated matches yet")
            assertThat(gus).contains("No matches logged with Gus yet.")
        }
    }

    @Test
    fun `the log shows double pilots, handicaps, unknown heroes, notes and pending follow-ups`() {
        val hub = seeded()
        hubTest(hub) {
            val token = plainClient().signIn()
            val html = client.page("/players/log", token).bodyAsText()
            assertThat(html).contains("double pilot")
            assertThat(html).contains("handicap")
            assertThat(html).contains("Unknown hero")
            assertThat(html).contains("“Comeback on the last wave”")
            assertThat(html).contains("/players/matches/m10/result")
            assertThat(html).contains("Who won?")
            assertThat(html).contains("Resume: setup &amp; timer")
            assertThat(html).contains("/timer/setup?match=m10")
            // m05 was logged with Ann's duplicate: it reads as Ann.
            val m05 = client.page("/players/matches/m05", token).bodyAsText()
            assertThat(m05).contains(">Ann<")
            assertThat(m05).doesNotContain("Ann B")
            assertThat(m05).contains("Rematch")
        }
    }

    @Test
    fun `the meta page shows the hero table, group stats and the ranking note`() {
        val hub = seeded()
        val view = expected(hub)
        val state = Meta.build(view.snapshot, view.byId, view.catalog)
        hubTest(hub) {
            val token = plainClient().signIn()
            val html = client.page("/players/meta", token).bodyAsText()
            assertThat(html).contains(">${state.totalMatches}<")
            assertThat(html).contains(state.largerTeamLabel)
            assertThat(html).contains("Last game night")
            assertThat(html).contains("How games end")
            assertThat(html).contains(esc(state.rankingNote))
            state.sortedHeroes.forEach { row ->
                assertThat(html).contains(esc(row.name))
                assertThat(html).contains(formatPercent(row.meta.winRate))
            }
            assertThat(
                Regex("<tbody>(.*?)</tbody>", RegexOption.DOT_MATCHES_ALL).find(html)!!.value.split("<tr").size - 1,
            )
                .isEqualTo(state.sortedHeroes.size)
            val cards = client.page("/players/meta?view=cards", token).bodyAsText()
            assertThat(cards).contains("pl-hero-card")
            if (state.unranked.isNotEmpty()) assertThat(cards).contains("Not enough games yet")
            val byGames = client.page("/players/meta?sort=GAMES", token).bodyAsText()
            assertThat(byGames).contains("Heroes, sorted by Games")
        }
    }

    @Test
    fun `saved result shows each player's change and the leader`() {
        val hub = seeded()
        val view = expected(hub)
        val saved = MatchEditor.savedSummary(view.match("m09")!!, view.players, view.snapshot.ratings)
        hubTest(hub) {
            val token = plainClient().signIn()
            val html = client.page("/players/matches/m09/saved", token).bodyAsText()
            assertThat(html).contains("Rating changes")
            saved.players.forEach { line -> assertThat(html).contains(">${formatRating(line.rating.value)}<") }
            assertThat(html).contains("On top now: ${saved.leaderName}")
            assertThat(html).contains("/players/matches/m09/rematch")
            val rematch = client.page("/players/matches/m09/rematch", token).bodyAsText()
            assertThat(rematch).contains("/timer/setup?rematch=m09&amp;kind=SAME_SEATS")
            assertThat(rematch).contains("/draft?rematch=m09")
        }
    }

    @Test
    fun `names and notes are escaped everywhere they appear`() {
        val hub = testHub(tmp.root.toPath(), clock)
        val evil = ann.copy(name = "<script>alert(1)</script>")
        val heroes = hub.catalog.catalog.heroes.map { it.id }.sorted()
        runBlocking {
            hub.groupStore.mergeIn(
                dev.quinton.goa.core.domain.sync.SyncDocument(
                    players = listOf(evil, bob),
                    matches = listOf(
                        PlayersTestGroup.match(
                            "x1",
                            3,
                            Team.RED,
                            PlayersTestGroup.red(evil, heroes[0]),
                            PlayersTestGroup.blue(bob, heroes[1]),
                            notes = "</p><img src=x onerror=alert(2)>",
                        ).copy(map = "<b>Map</b>"),
                    ),
                ),
            )
        }
        hubTest(hub) {
            val token = plainClient().signIn()
            for (path in listOf(
                "/players",
                "/players/log",
                "/players/${ann.id}",
                "/players/matches/x1",
                "/players/meta",
                "/players/matches/x1/edit",
            )) {
                val html = client.page(path, token).bodyAsText()
                assertThat(html).doesNotContain("<script>alert")
                assertThat(html).doesNotContain("<img src=x")
                assertThat(html).doesNotContain("<b>Map")
            }
            assertThat(client.page("/players/log", token).bodyAsText()).contains("&lt;img src=x onerror=alert(2)&gt;")
        }
    }

    @Test
    fun `the stats page is the ADR-027 file, as a download`() {
        val hub = seeded()
        hubTest(hub) {
            val token = plainClient().signIn()
            val response = client.page("/players/stats-page", token)
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.headers[HttpHeaders.ContentDisposition]).contains("attachment")
            assertThat(response.headers[HttpHeaders.ContentDisposition]).contains("goa-stats-")
            val html = response.bodyAsText()
            assertThat(html).contains("<!DOCTYPE html>")
            assertThat(html).contains("Ann")
            assertThat(html).doesNotContain("Ann B")
        }
    }
}
