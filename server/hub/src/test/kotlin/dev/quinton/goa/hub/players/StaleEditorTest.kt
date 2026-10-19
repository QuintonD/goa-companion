package dev.quinton.goa.hub.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.players.PlayersTestGroup.ann
import dev.quinton.goa.hub.players.PlayersTestGroup.bob
import dev.quinton.goa.hub.players.PlayersTestGroup.cid
import dev.quinton.goa.hub.players.PlayersTestGroup.dee
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.testHub
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ParametersBuilder
import io.ktor.http.parameters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A match changed on another phone or browser while the web editor was open (ADR-030 hands-on
 * notes): the stale form must not overwrite that change, and the person is told.
 */
class StaleEditorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock()

    private fun seeded(): Hub = testHub(tmp.root.toPath(), clock).also { runBlocking { PlayersTestGroup.seed(it) } }

    private fun heroes(hub: Hub) = hub.catalog.catalog.heroes.map { it.id }.sorted()

    private val local = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    private fun localTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).format(local)

    /** A two-against-two result form. */
    private fun ParametersBuilder.result(hub: Hub, winner: Team? = Team.RED, op: String = "save") {
        val h = heroes(hub)
        append("playedAt", localTime(PlayersTestGroup.at(20)))
        append("mode", "LONG")
        append("status", "COMPLETED")
        winner?.let { append("winner", it.name) }
        append("condition", "THRONE")
        append("duration", "48")
        append("notes", "Close one")
        append("map", "Kitchen table")
        seat("red", 0, ann.id, h[0])
        seat("red", 1, bob.id, h[1], handicap = true)
        seat("blue", 0, cid.id, h[2])
        seat("blue", 1, dee.id, h[3])
        append("op", op)
    }

    private fun ParametersBuilder.seat(team: String, i: Int, pilot: String?, hero: String?, handicap: Boolean = false) {
        append("$team-$i", "1")
        append("$team-$i-pilot", pilot.orEmpty())
        append("$team-$i-hero", hero.orEmpty())
        if (handicap) append("$team-$i-handicap", "true")
    }

    @Test
    fun `a match changed on another device while the editor was open is not overwritten by the stale form`() {
        val hub = seeded()
        clock.set(1_800_000_000_000)
        hubTest(hub) {
            val token = plainClient().signIn()
            val form = client.page("/players/matches/m09/edit", token).bodyAsText()
            val base = Regex("""name="base" value="(\d+)"""").find(form)!!.groupValues[1]
            assertThat(base.toLong()).isEqualTo(hub.groupStore.read().matches.single { it.id == "m09" }.updatedAt)
            // A phone sets the map meanwhile (it arrives through a sync).
            clock.advance(60_000)
            runBlocking {
                val m09 = hub.groupStore.read().matches.single { it.id == "m09" }
                hub.groupStore.upsertMatch(m09.copy(map = "Phone's table"))
            }
            val changed = hub.groupStore.read()

            val refused = plainClient().send("/players/matches/m09/edit", token) {
                result(hub, winner = Team.BLUE)
                append("base", base)
            }
            assertThat(refused.status).isEqualTo(HttpStatusCode.Conflict)
            assertThat(refused.bodyAsText()).contains("changed on another phone or browser while you were editing it")
            assertThat(hub.groupStore.read()).isEqualTo(changed)

            // The editor posts with htmx, which doesn't swap a 409: the browser goes to the match and is told.
            val viaHtmx = plainClient().submitForm(
                "/players/matches/m09/edit",
                parameters {
                    result(hub, winner = Team.BLUE)
                    append("base", base)
                },
            ) {
                header(HttpHeaders.Cookie, cookie(token))
                header(HttpHeaders.Origin, HUB_URL)
                header("HX-Request", "true")
            }
            assertThat(viaHtmx.status).isEqualTo(HttpStatusCode.OK)
            assertThat(viaHtmx.headers["HX-Redirect"]).isEqualTo("/players/matches/m09")
            assertThat(client.page("/players/matches/m09", token).bodyAsText()).contains("your changes weren't saved")
            assertThat(hub.groupStore.read()).isEqualTo(changed)

            // Opened again, the form starts from the new version and saves.
            val again = client.page("/players/matches/m09/edit", token).bodyAsText()
            val fresh = Regex("""name="base" value="(\d+)"""").find(again)!!.groupValues[1]
            val saved = plainClient().send("/players/matches/m09/edit", token) {
                result(hub, winner = Team.BLUE)
                append("base", fresh)
            }
            assertThat(saved.status).isEqualTo(HttpStatusCode.Found)
            assertThat(hub.groupStore.read().matches.single { it.id == "m09" }.winner).isEqualTo(Team.BLUE)
        }
    }
}
