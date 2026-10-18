package dev.quinton.goa.hub.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.EditStamp
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.players.PlayersTestGroup.ann
import dev.quinton.goa.hub.players.PlayersTestGroup.bob
import dev.quinton.goa.hub.players.PlayersTestGroup.cid
import dev.quinton.goa.hub.players.PlayersTestGroup.dee
import dev.quinton.goa.hub.players.PlayersTestGroup.fay
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.store.SharedWrites
import dev.quinton.goa.hub.testHub
import dev.quinton.goa.hub.web.ui.PlayerPalette
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ParametersBuilder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Every Players write changes the group's data exactly as the phone's Room repositories would
 * (the hub's `SharedWrites` mirrors them): stamped edits, tombstones, merge rows, refusals.
 */
class PlayersWritesTest {
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
    fun `adding a player stamps it like the phone and refuses a taken name, archived included`() {
        val hub = seeded()
        clock.set(1_800_000_000_000)
        hubTest(hub) {
            val token = plainClient().signIn()
            val taken = plainClient().send("/players/new", token) { append("name", "  fay ") }
            assertThat(taken.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
            assertThat(taken.bodyAsText()).contains("“fay” is already in your group")
            val blank = plainClient().send("/players/new", token) { append("name", "   ") }
            assertThat(blank.bodyAsText()).contains("Enter a name")
            val before = hub.groupStore.read()
            val added = plainClient().send("/players/new", token) {
                append("name", "  Gus  ")
                append("color", "9")
                append("crest", "23")
            }
            assertThat(added.status).isEqualTo(HttpStatusCode.Found)
            assertThat(added.headers[HttpHeaders.Location]).isEqualTo("/players")
            val gus = hub.groupStore.read().players.single { it.name == "Gus" }
            assertThat(gus.color).isEqualTo(PlayerPalette.swatches[9].first)
            assertThat(gus.avatarId).isEqualTo(23)
            assertThat(gus.createdAt).isEqualTo(clock.nowMillis())
            // The same write the phone's repository would make.
            val (doc, _) = SharedWrites.upsertPlayer(before, gus, clock.nowMillis())
            assertThat(hub.groupStore.read().players).containsExactlyElementsIn(doc.players)
        }
    }

    @Test
    fun `editing keeps id and createdAt, archiving toggles, both stamped later`() {
        val hub = seeded()
        clock.set(1_800_000_000_000)
        hubTest(hub) {
            val token = plainClient().signIn()
            val refused = plainClient().send("/players/${bob.id}/edit", token) { append("name", "ann") }
            assertThat(refused.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
            val kept = plainClient().send("/players/${bob.id}/edit", token) { append("name", " BOB ") }
            assertThat(kept.headers[HttpHeaders.Location]).isEqualTo("/players/${bob.id}")
            val edited = plainClient().send("/players/${bob.id}/edit", token) {
                append("name", "Bobby")
                append("color", "2")
            }
            assertThat(edited.status).isEqualTo(HttpStatusCode.Found)
            val bobby = hub.groupStore.read().players.single { it.id == bob.id }
            assertThat(bobby).isEqualTo(
                bob.copy(name = "Bobby", color = PlayerPalette.swatches[2].first, updatedAt = clock.nowMillis() + 1),
            )
            clock.advance(5_000)
            plainClient().send("/players/${bob.id}/archive", token) { append("archived", "true") }
            assertThat(hub.groupStore.read().players.single { it.id == bob.id }.archived).isTrue()
            assertThat(hub.groupStore.read().players.single { it.id == bob.id }.updatedAt).isEqualTo(clock.nowMillis())
            plainClient().send("/players/${bob.id}/archive", token) { append("archived", "false") }
            assertThat(hub.groupStore.read().players.single { it.id == bob.id }.archived).isFalse()
        }
    }

    @Test
    fun `merge into records a merge row, and is refused with the shared matches when they played together`() {
        val hub = seeded()
        runBlocking { hub.groupStore.upsertPlayer(dev.quinton.goa.hub.player("p-gus", "Gus", createdAt = 9)) }
        clock.set(1_800_000_000_000)
        hubTest(hub) {
            val token = plainClient().signIn()
            val confirm = client.page("/players/${bob.id}/merge?into=${ann.id}", token).bodyAsText()
            assertThat(confirm).contains("Merge into Ann?")
            assertThat(confirm).contains("Bob (")
            val before = hub.groupStore.read()
            val refused = plainClient().send("/players/${bob.id}/merge", token) { append("into", ann.id) }
            assertThat(refused.status).isEqualTo(HttpStatusCode.Conflict)
            assertThat(refused.bodyAsText()).contains("They played in the same match")
            assertThat(hub.groupStore.read()).isEqualTo(before)
            // An archived player isn't offered, and can't be posted either.
            val archived = plainClient().send("/players/p-gus/merge", token) { append("into", fay.id) }
            assertThat(archived.status).isEqualTo(HttpStatusCode.Found)
            assertThat(hub.groupStore.read()).isEqualTo(before)
            val merged = plainClient().send("/players/p-gus/merge", token) { append("into", ann.id) }
            assertThat(merged.headers[HttpHeaders.Location]).isEqualTo("/players/${ann.id}")
            assertThat(hub.groupStore.read().playerMerges).contains(PlayerMerge("p-gus", ann.id, clock.nowMillis()))
            assertThat(client.page("/players/${ann.id}", token).bodyAsText()).contains("Merged into Ann.")
        }
    }

    @Test
    fun `same people - different is kept on this browser, same person merges the newer into the older`() {
        val hub = seeded()
        runBlocking {
            hub.groupStore.upsertPlayer(dev.quinton.goa.hub.player("p-anna", "Anna", createdAt = 20))
            hub.groupStore.upsertPlayer(dev.quinton.goa.hub.player("p-cidney", "Cidney", createdAt = 21))
        }
        hubTest(hub) {
            val token = plainClient().signIn()
            assertThat(
                client.page("/players", token).bodyAsText(),
            ).contains("pairs of players look like the same person")
            val page = client.page("/players/same-people", token).bodyAsText()
            assertThat(page).contains("Anna")
            assertThat(page).contains("Cidney")
            plainClient().send("/players/same-people/different", token) {
                append("a", cid.id)
                append("b", "p-cidney")
            }
            val device = hub.devices.list().single().id
            assertThat(hub.deviceStore.read(device, PlayersSlots.DifferentPeople)).hasSize(1)
            assertThat(client.page("/players", token).bodyAsText()).contains("2 players might be the same person")
            val confirm = client.page("/players/same-people?a=${ann.id}&b=p-anna", token).bodyAsText()
            assertThat(confirm).contains("Merge Anna into Ann?")
            plainClient().send("/players/same-people/same", token) {
                append("a", ann.id)
                append("b", "p-anna")
            }
            assertThat(hub.groupStore.read().playerMerges.map { it.fromId to it.toId }).contains("p-anna" to ann.id)
            assertThat(client.page("/players/same-people", token).bodyAsText()).contains("Merged Anna into Ann.")
            // Nothing left to review but the "different" answer; ask again brings it back.
            assertThat(client.page("/players/same-people", token).bodyAsText())
                .contains("Ask again about the pair you said were different")
            plainClient().send("/players/same-people/ask-again", token)
            assertThat(hub.deviceStore.read(device, PlayersSlots.DifferentPeople)).isNull()
            assertThat(client.page("/players/same-people", token).bodyAsText()).contains("Cidney")
        }
    }

    @Test
    fun `logging a match stores it as the phone would and shows what it changed`() {
        val hub = seeded()
        clock.set(1_800_000_000_000)
        hubTest(hub) {
            val token = plainClient().signIn()
            val form = client.page("/players/matches/new", token).bodyAsText()
            val id = Regex("name=\"id\" value=\"([^\"]+)\"").find(form)!!.groupValues[1]
            val response = plainClient().send("/players/matches/new", token) {
                append("id", id)
                result(hub)
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.Found)
            assertThat(response.headers[HttpHeaders.Location]).isEqualTo("/players/matches/$id/saved")
            val h = heroes(hub)
            val stored = hub.groupStore.read().matches.single { it.id == id }
            assertThat(stored).isEqualTo(
                Match(
                    id = id,
                    createdAt = clock.nowMillis(),
                    playedAt = PlayersTestGroup.at(20),
                    status = MatchStatus.COMPLETED,
                    mode = GameMode.LONG,
                    map = "Kitchen table",
                    winner = Team.RED,
                    winCondition = WinCondition.THRONE,
                    durationMin = 48,
                    notes = "Close one",
                    participants = listOf(
                        Participant(ann.id, h[0], Team.RED),
                        Participant(bob.id, h[1], Team.RED, handicap = true),
                        Participant(cid.id, h[2], Team.BLUE),
                        Participant(dee.id, h[3], Team.BLUE),
                    ),
                    updatedAt = clock.nowMillis(),
                ),
            )
            // Posting the same form again (a double tap) saves the same match, not a second one.
            plainClient().send("/players/matches/new", token) {
                append("id", id)
                result(hub)
            }
            assertThat(hub.groupStore.read().matches.count { it.id == id }).isEqualTo(1)
            assertThat(client.page("/players/matches/$id/saved", token).bodyAsText()).contains("Rating changes")
        }
    }

    @Test
    fun `the editor refuses what the phone refuses, with the phone's messages`() {
        val hub = seeded()
        val h = heroes(hub)
        hubTest(hub) {
            val token = plainClient().signIn()
            val before = hub.groupStore.read()
            val noPilotNoWinner = plainClient().send("/players/matches/new", token) {
                append("status", "COMPLETED")
                seat("red", 0, null, h[0])
                seat("blue", 0, cid.id, h[0])
                append("op", "save")
            }
            assertThat(noPilotNoWinner.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
            val html = noPilotNoWinner.bodyAsText()
            assertThat(html).contains("Can't save yet")
            assertThat(html).contains("Choose a pilot for every seat.")
            assertThat(html).contains("is picked twice. Each hero can only be in a match once.")
            assertThat(html).contains("Choose the winning team of a completed match.")
            val bothTeams = plainClient().send("/players/matches/new", token) {
                append("status", "COMPLETED")
                append("winner", "RED")
                seat("red", 0, ann.id, h[0])
                seat("blue", 0, ann.id, h[1])
                append("op", "save")
            }
            assertThat(bothTeams.bodyAsText()).contains("Ann can't play for both teams.")
            val emptyTeam = plainClient().send("/players/matches/new", token) {
                append("status", "COMPLETED")
                append("winner", "RED")
                seat("red", 0, ann.id, h[0])
                append("op", "save")
            }
            assertThat(emptyTeam.bodyAsText()).contains("Team Blue needs at least one hero seat.")
            assertThat(hub.groupStore.read()).isEqualTo(before)
            // A pending match may be saved without seats (to keep notes).
            val pending = plainClient().send("/players/matches/new", token) {
                append("status", "PENDING")
                append("notes", "Next week")
                append("op", "save")
            }
            assertThat(pending.status).isEqualTo(HttpStatusCode.Found)
            assertThat(pending.headers[HttpHeaders.Location]).startsWith("/players/log?saved=")
        }
    }

    @Test
    fun `seat buttons add, remove, swap and quick-add a pilot without saving the match`() {
        val hub = seeded()
        val h = heroes(hub)
        hubTest(hub) {
            val token = plainClient().signIn()
            val before = hub.groupStore.read().matches
            val added = plainClient().send("/players/matches/new", token) {
                seat("red", 0, ann.id, h[0])
                append("op", "add-red")
            }.bodyAsText()
            assertThat(added).contains("name=\"red-1-hero\"")
            val removed = plainClient().send("/players/matches/new", token) {
                seat("red", 0, ann.id, h[0])
                seat("red", 1, bob.id, h[1])
                append("op", "remove-red-0")
            }.bodyAsText()
            assertThat(removed).doesNotContain("name=\"red-1-hero\"")
            assertThat(removed).contains("<option value=\"${bob.id}\" selected")
            val swapped = plainClient().send("/players/matches/new", token) {
                seat("red", 0, ann.id, h[0])
                seat("blue", 0, cid.id, h[1])
                append("winner", "RED")
                append("op", "swap")
            }.bodyAsText()
            assertThat(
                Regex(
                    "name=\"blue-0-pilot\".*?<option value=\"${ann.id}\" selected",
                    RegexOption.DOT_MATCHES_ALL,
                ).containsMatchIn(swapped),
            ).isTrue()
            assertThat(swapped).contains("value=\"BLUE\" checked")
            // Double pilot: the same player on two seats of one team.
            val double = plainClient().send("/players/matches/new", token) {
                seat("red", 0, ann.id, h[0])
                seat("red", 1, ann.id, h[1])
                append("op", "refresh")
            }.bodyAsText()
            assertThat(double).contains("Double pilot")
            // Quick add: a new player is created (stamped) and seated in the first free seat.
            val quick = plainClient().send("/players/matches/new", token) {
                seat("red", 0, ann.id, h[0])
                seat("red", 1, null, null)
                append("newPlayer", " Gus ")
                append("op", "add-player")
            }.bodyAsText()
            val gus = hub.groupStore.read().players.single { it.name == "Gus" }
            assertThat(quick).contains("Added Gus.")
            assertThat(
                Regex(
                    "name=\"red-1-pilot\".*?<option value=\"${gus.id}\" selected",
                    RegexOption.DOT_MATCHES_ALL,
                ).containsMatchIn(quick),
            ).isTrue()
            // A known name, archived included, is picked instead of a second player.
            plainClient().send("/players/matches/new", token) {
                append("newPlayer", "fay")
                append("op", "add-player")
            }
            assertThat(hub.groupStore.read().players.count { it.name.equals("fay", ignoreCase = true) }).isEqualTo(1)
            assertThat(hub.groupStore.read().players.single { it.id == fay.id }.archived).isFalse()
            assertThat(hub.groupStore.read().matches).isEqualTo(before)
        }
    }

    @Test
    fun `editing keeps identity, draft and tie breaker, and a match deleted meanwhile says so`() {
        val hub = seeded()
        runBlocking {
            val m09 = hub.groupStore.read().matches.single { it.id == "m09" }
            hub.groupStore.upsertMatch(m09.copy(tieBreaker = Team.BLUE))
        }
        clock.set(1_800_000_000_000)
        hubTest(hub) {
            val token = plainClient().signIn()
            val original = hub.groupStore.read().matches.single { it.id == "m09" }
            val form = client.page("/players/matches/m09/edit", token).bodyAsText()
            assertThat(form).contains("Edit match")
            assertThat(form).contains("Save changes")
            val response = plainClient().send("/players/matches/m09/edit", token) { result(hub, winner = Team.BLUE) }
            assertThat(response.headers[HttpHeaders.Location]).isEqualTo("/players/log?saved=m09#match-m09")
            val edited = hub.groupStore.read().matches.single { it.id == "m09" }
            assertThat(edited.createdAt).isEqualTo(original.createdAt)
            assertThat(edited.tieBreaker).isEqualTo(Team.BLUE)
            assertThat(edited.winner).isEqualTo(Team.BLUE)
            assertThat(edited.updatedAt).isEqualTo(EditStamp.next(clock.nowMillis(), original.updatedAt))
            // Deleted on a phone while this form was open.
            runBlocking { hub.groupStore.deleteMatch("m07") }
            val gone = plainClient().send("/players/matches/m07/edit", token) { result(hub) }
            assertThat(gone.status).isEqualTo(HttpStatusCode.Conflict)
            assertThat(gone.bodyAsText()).contains("deleted on another phone or browser while you were editing it")
            assertThat(hub.groupStore.read().matches.none { it.id == "m07" }).isTrue()
            assertThat(client.page("/players/matches/m07/edit", token).bodyAsText()).contains("This match was deleted")
        }
    }

    @Test
    fun `who won turns a pending match into a result, dated from the session start`() {
        val hub = seeded()
        clock.set(1_800_000_000_000)
        hubTest(hub) {
            val token = plainClient().signIn()
            val start = PlayersTestGroup.at(16, 19)
            val page = client.page("/players/matches/m10/result?playedAt=$start", token).bodyAsText()
            assertThat(page).contains("Which team won?")
            assertThat(page).contains("How did they win?")
            assertThat(page).contains("Everything below is optional")
            assertThat(page).contains("Save result")
            assertThat(page).contains("value=\"${localTime(start)}\"")
            assertThat(page).contains("Discard pending match")
            val pending = hub.groupStore.read().matches.single { it.id == "m10" }
            val response = plainClient().send("/players/matches/m10/result", token) {
                append("playedAt", localTime(start))
                append("status", "COMPLETED")
                append("winner", "BLUE")
                append("condition", "LAST_PUSH")
                pending.participants.groupBy { it.team }.forEach { (team, seats) ->
                    seats.forEachIndexed { i, s -> seat(team.name.lowercase(), i, s.playerId, s.heroId, s.handicap) }
                }
                append("op", "save")
            }
            assertThat(response.headers[HttpHeaders.Location]).isEqualTo("/players/matches/m10/saved")
            val result = hub.groupStore.read().matches.single { it.id == "m10" }
            assertThat(result.status).isEqualTo(MatchStatus.COMPLETED)
            assertThat(result.winner).isEqualTo(Team.BLUE)
            assertThat(result.winCondition).isEqualTo(WinCondition.LAST_PUSH)
            assertThat(result.playedAt).isEqualTo(start)
            assertThat(result.draftId).isEqualTo("d1")
            assertThat(result.createdAt).isEqualTo(pending.createdAt)
            // Asked again: it already has a result now.
            assertThat(client.page("/players/matches/m10/result", token).bodyAsText())
                .contains("This match already has a result")
        }
    }

    @Test
    fun `deleting leaves a tombstone and undo brings the match back for a while`() {
        val hub = seeded()
        clock.set(1_800_000_000_000)
        hubTest(hub) {
            val token = plainClient().signIn()
            assertThat(client.page("/players/matches/m09/delete", token).bodyAsText()).contains("Delete this match?")
            assertThat(
                client.page("/players/matches/m10/delete", token).bodyAsText(),
            ).contains("Discard this pending match?")
            val before = hub.groupStore.read()
            val deleted = plainClient().send("/players/matches/m09/delete", token)
            assertThat(deleted.headers[HttpHeaders.Location]).isEqualTo("/players/log")
            val expected = SharedWrites.deleteMatch(before, "m09", clock.nowMillis())
            assertThat(hub.groupStore.read().deletions).containsExactlyElementsIn(expected.deletions)
            assertThat(hub.groupStore.read().deletions.single { it.id == "m09" }.kind).isEqualTo(DeletionKind.MATCH)
            assertThat(client.page("/players/log", token).bodyAsText()).contains("Match deleted.")
            clock.advance(10_000)
            val undone = plainClient().send(PlayersPaths.UNDO, token)
            assertThat(undone.headers[HttpHeaders.Location]).isEqualTo(PlayersPaths.logAt("m09"))
            val back = hub.groupStore.read().matches.single { it.id == "m09" }
            assertThat(back.copy(updatedAt = 0)).isEqualTo(before.matches.single { it.id == "m09" }.copy(updatedAt = 0))
            assertThat(back.updatedAt).isGreaterThan(
                hub.groupStore.read().deletions.single {
                    it.id == "m09"
                }.deletedAt,
            )
            // Too late: no undo after 30 s.
            plainClient().send("/players/matches/m01/delete", token)
            clock.advance(PlayersSlots.UNDO_WINDOW_MS + 1)
            assertThat(client.page("/players/log", token).bodyAsText()).doesNotContain("Match deleted.")
            plainClient().send(PlayersPaths.UNDO, token)
            assertThat(hub.groupStore.read().matches.none { it.id == "m01" }).isTrue()
            // Deleting a match that's gone already says so.
            plainClient().send("/players/matches/m01/delete", token)
            assertThat(client.page("/players/log", token).bodyAsText()).contains("already deleted")
        }
    }

    @Test
    fun `every players write needs the hub's own origin`() {
        val hub = seeded()
        hubTest(hub) {
            val token = plainClient().signIn()
            val before = hub.groupStore.read()
            val posts = listOf(
                "/players/new",
                "/players/${bob.id}/edit",
                "/players/${bob.id}/archive",
                "/players/${bob.id}/merge",
                "/players/same-people/same",
                "/players/same-people/different",
                "/players/same-people/ask-again",
                "/players/matches/new",
                "/players/matches/m09/edit",
                "/players/matches/m10/result",
                "/players/matches/m09/delete",
                PlayersPaths.UNDO,
            )
            for (path in posts) {
                for (origin in listOf(null, "https://evil.example")) {
                    val response = plainClient().send(path, token, origin = origin) {
                        append("name", "Mallory")
                        append("into", ann.id)
                        append("archived", "true")
                    }
                    assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
                }
            }
            assertThat(hub.groupStore.read()).isEqualTo(before)
        }
    }
}
