package dev.quinton.goa.hub.draft

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.DraftSetup
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.ban
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.PlayerDraftPhase
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.draft.DraftAgain
import dev.quinton.goa.core.presentation.draft.DraftProgress
import dev.quinton.goa.core.presentation.draft.MethodLibrary
import dev.quinton.goa.core.presentation.draft.SetupForm
import dev.quinton.goa.core.presentation.draft.SetupStage
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.match
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.player
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.testHub
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import java.nio.file.Files
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Draft area over HTTP: the same drafts as `DraftEngine` directly, hidden picks never in a
 * response before they are revealed, the Player Draft, undo, rematch, finishing, and the origin rule.
 */
class DraftFeatureTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock()

    private fun hub(players: Int = 10): Hub = testHub(tmp.root.toPath(), clock).also { hub ->
        runBlocking { (0 until players).forEach { hub.groupStore.upsertPlayer(player("p$it", "Player $it")) } }
    }

    private fun draftTest(hub: Hub, block: suspend DraftBrowser.() -> Unit) = hubTest(hub) {
        val client = plainClient()
        DraftBrowser(hub, client, client.signIn()).block()
    }

    private val Hub.library: MethodLibrary get() = MethodLibrary.of(
        MethodLibrary.decodeBundled(catalog.readText(DraftData.METHODS_PATH)),
        emptyList(),
    )

    private fun Hub.heroIds(): List<String> = catalog.catalog.heroes.map { it.id }

    /** Sets up [n] players with [methodId] through the setup pages; returns the form as it was at Start. */
    private suspend fun DraftBrowser.setUp(n: Int, methodId: String, odd: OddSeatMode? = null): SetupForm {
        listOf(DraftSlots.Setup, DraftSlots.Active, DraftSlots.Board, DraftSlots.Notes).forEach { delete(it) }
        for (i in 0 until n) setup("toggle:p$i")
        setup("next")
        odd?.let { setup("odd-mode:${it.name}") }
        setup("next")
        setup("method:$methodId")
        setup("next")
        setup("team-a:RED")
        return slot(DraftSlots.Setup)!!
    }

    @Test
    fun `every bundled method runs the same over HTTP as DraftEngine directly, 2 to 10 players`() {
        val hub = hub()
        val rng = Random(7)
        draftTest(hub) {
            var drafted = 0
            for (method in hub.library.builtIn) {
                for (n in 2..10) {
                    val odds = if (SeatLayouts.offersOddChoice(n)) OddSeatMode.entries.toList() else listOf(null)
                    for (odd in odds) if (parity(hub, method, n, odd, rng)) drafted++
                }
            }
            // Most combinations draft; those that can't (a lineup without that size) start nothing.
            assertThat(drafted).isGreaterThan(50)
        }
    }

    /** One draft through the pages, checked step by step against the engine; false if it can't start. */
    private suspend fun DraftBrowser.parity(
        hub: Hub,
        method: DraftMethod,
        n: Int,
        odd: OddSeatMode?,
        rng: Random,
    ): Boolean {
        val form = setUp(n, method.id, odd)
        val location = setup("start")
        val active = slot(DraftSlots.Active) as DraftProgress.Board?
        val seats = SeatLayouts.build(form.red, form.blue, form.oddMode, form.doublePilot).getOrThrow()
        if (active == null) {
            assertThat(location).isEqualTo("/draft")
            // The engine refuses this layout too.
            val any = DraftEngine.start(DraftSetup("x", 0, method, seats, Team.RED, hub.heroIds(), 1L))
            assertThat(any.isFailure).isTrue()
            return false
        }
        assertThat(location).isEqualTo("/draft/board")
        var expected: DraftSession = DraftEngine.start(
            DraftSetup(
                active.id,
                active.session.createdAt,
                method,
                seats,
                Team.RED,
                hub.heroIds(),
                active.session.seed,
            ),
        ).getOrThrow()
        assertThat(active.session).isEqualTo(expected)
        // The next game's default: same players and teams, back at the first stage, no coin.
        assertThat(slot(DraftSlots.Setup)).isEqualTo(form.forNextGame())
        while (!expected.isComplete) {
            val hero = DraftEngine.available(expected).random(rng)
            assertThat(board("choose:$hero")).isEqualTo("/draft/board#confirm")
            board("confirm")
            expected = DraftEngine.apply(expected, DraftCommand(hero)).getOrThrow()
            assertThat((slot(DraftSlots.Active) as DraftProgress.Board).session).isEqualTo(expected)
        }
        val before = hub.groupStore.read()
        assertThat(board("finish:later")).isEqualTo("/draft")
        val matchId = slot(DraftSlots.Notes)!!.savedMatchId!!
        assertFinished(hub, before, expected, matchId)
        assertThat(slot(DraftSlots.Active)).isNull()
        return true
    }

    /** Exactly the phone's writes: one PENDING match and its draft record, stamped. */
    private fun assertFinished(
        hub: Hub,
        before: dev.quinton.goa.core.domain.sync.SyncDocument,
        session: DraftSession,
        matchId: String,
    ) {
        val after = hub.groupStore.read()
        assertThat(after.matches.map { it.id }).containsExactlyElementsIn(before.matches.map { it.id } + matchId)
        assertThat(after.drafts.map { it.id }).containsExactlyElementsIn(before.drafts.map { it.id } + session.id)
        val expectedMatch = DraftEngine.toPendingMatch(session, matchId, clock.nowMillis()).getOrThrow()
        val match = after.matches.single { it.id == matchId }
        assertThat(match.status).isEqualTo(MatchStatus.PENDING)
        assertThat(match.copy(updatedAt = expectedMatch.updatedAt)).isEqualTo(expectedMatch)
        val record = after.drafts.single { it.id == session.id }
        val expectedRecord = DraftEngine.toRecord(session, matchId)
        assertThat(record.copy(updatedAt = expectedRecord.updatedAt)).isEqualTo(expectedRecord)
        assertThat(record.updatedAt).isAtLeast(clock.nowMillis())
    }

    private val blind = DraftMethod(
        id = "custom-blind",
        name = "Blind",
        kind = DraftMethodKind.CUSTOM,
        pool = PoolRule.Random(15),
        sequence = SequenceRule.Pattern(
            opening = listOf(ban(TeamSlot.A, hidden = true), ban(TeamSlot.B, hidden = true)),
            repeat = listOf(pick(TeamSlot.A, hidden = true), pick(TeamSlot.B, hidden = true)),
        ),
    )

    @Test
    fun `a hidden choice never appears in any response until the hidden run ends`() {
        val hub = hub()
        draftTest(hub) {
            hub.deviceStore.write(deviceId, DraftSlots.Methods, listOf(blind))
            setUp(4, blind.id)
            setup("start")
            val heroes = hub.catalog.catalog.heroes.associateBy { it.id }
            var session = (slot(DraftSlots.Active) as DraftProgress.Board).session
            val concealed = mutableListOf<String>()
            while (!session.isComplete) {
                // The gate: no pool, no seats, no name of anything chosen so far.
                val gate = page("/draft/board")
                assertThat(gate).contains("Pass the phone to")
                assertThat(gate).contains("<title>Blind · Guards of Atlantis</title>")
                concealed.forEach { id -> assertThat(gate).doesNotContain(heroes.getValue(id).name) }
                board("reveal:${session.currentStepIndex}")
                val html = page("/draft/board")
                // A concealed hero is drawn exactly like a free one: same tile, same count, never "taken".
                val free = DraftEngine.available(session).first { it !in concealed }
                for (id in concealed + free) {
                    assertThat(html).contains("type=\"submit\" class=\"hero-tile\" name=\"op\" value=\"choose:$id\"")
                    assertThat(occurrences(html, "choose:$id\"")).isEqualTo(1)
                    assertThat(occurrences(html, ">${heroes.getValue(id).name}<")).isEqualTo(1)
                }
                assertThat(occurrences(html, "Hidden ban") + occurrences(html, "Hidden pick")).isEqualTo(concealed.size)
                assertThat(html).doesNotContain("Taken")
                val hero = DraftEngine.available(session).first()
                // Neither the redirect nor anything kept in the history names the hero.
                assertThat(board("choose:$hero")).doesNotContain(hero)
                board("confirm")
                concealed += hero
                session = (slot(DraftSlots.Active) as DraftProgress.Board?)?.session ?: break
                // Undo of a still-hidden choice is announced without the hero, and Redo puts it back.
                if (concealed.size == 2) {
                    board("undo")
                    val undone = page("/draft/board")
                    // The same team still holds the phone (its gate was passed): the undone ban is a free tile again.
                    assertThat(undone).contains("<p>Undid a hidden ban</p>")
                    concealed.forEach { id ->
                        assertThat(occurrences(undone, ">${heroes.getValue(id).name}<")).isEqualTo(1)
                    }
                    board("redo")
                    assertThat((slot(DraftSlots.Active) as DraftProgress.Board).session).isEqualTo(session)
                }
            }
            // The run is over: the completion page names every hero.
            val done = page("/draft/board")
            assertThat(done).contains("Draft complete")
            concealed.forEach { id -> assertThat(done).contains(heroes.getValue(id).name) }
        }
    }

    @Test
    fun `a choice the hub refuses for a concealed hero says nothing about it`() {
        val hub = hub()
        draftTest(hub) {
            hub.deviceStore.write(deviceId, DraftSlots.Methods, listOf(blind))
            setUp(4, blind.id)
            setup("start")
            val first = (slot(DraftSlots.Active) as DraftProgress.Board).session.pool.first()
            board("reveal:0")
            board("choose:$first")
            board("confirm")
            board("reveal:1")
            board("choose:$first")
            board("confirm")
            val html = page("/draft/board")
            assertThat(html).contains("Choose a different hero")
            assertThat((slot(DraftSlots.Active) as DraftProgress.Board).session.history).hasSize(1)
        }
    }

    @Test
    fun `undo and redo on the board, with the step named once it is public`() {
        val hub = hub()
        draftTest(hub) {
            setUp(4, "house")
            setup("start")
            val session = (slot(DraftSlots.Active) as DraftProgress.Board).session
            val hero = session.pool[3]
            board("choose:$hero")
            board("confirm")
            board("undo")
            val name = hub.catalog.catalog.hero(hero)!!.name
            assertThat(page("/draft/board")).contains("Undid Ban $name")
            assertThat((slot(DraftSlots.Active) as DraftProgress.Board).session).isEqualTo(session)
            board("redo")
            assertThat((slot(DraftSlots.Active) as DraftProgress.Board).session.history.single().heroId).isEqualTo(hero)
            // Any other action ends the message and its Redo.
            board("dismiss")
            assertThat(page("/draft/board")).doesNotContain("Undid")
        }
    }

    @Test
    fun `player draft - heroes, captains, the forced last pick, the hand-off and undo`() {
        val hub = hub()
        draftTest(hub) {
            listOf(DraftSlots.Setup, DraftSlots.Active).forEach { delete(it) }
            for (i in 0 until 5) setup("toggle:p$i")
            setup("next")
            setup("team-mode:PLAYER_DRAFT")
            val captains = slot(DraftSlots.Setup)!!
            assertThat(captains.redCaptain).isNotNull()
            assertThat(captains.blueCaptain).isNotNull()
            setup("next")
            setup("method:all-pick")
            setup("next")
            setup("team-a:BLUE")
            setup("start")
            var players = (slot(DraftSlots.Active) as DraftProgress.Players).session
            players.selection.players.forEachIndexed { i, id ->
                assertThat(board("choose-for:$id")).isEqualTo("/draft/board#chooser")
                board("assign:${players.selection.pool[i]}")
            }
            players = (slot(DraftSlots.Active) as DraftProgress.Players).session
            assertThat(players.phase).isEqualTo(PlayerDraftPhase.CAPTAINS)
            val remaining = players.captains.remaining
            board("draft-player:${remaining[0]}")
            board("draft-player:${remaining[1]}")
            // One player was left: the captain on the clock had no choice, so the hub made that pick.
            val handOff = slot(DraftSlots.Active) as DraftProgress.Board
            assertThat(handOff.session.isComplete).isTrue()
            assertThat(handOff.session.fromPlayerDraft).isTrue()
            assertThat(handOff.playerDraft!!.captains.picks.last().auto).isTrue()
            assertThat(handOff.playerDraft!!.captains.blue).contains(remaining[2])
            assertThat(page("/draft/board")).contains("Everyone plays the hero chosen for them.")
            // Undo takes back the forced pick together with the pick that forced it.
            board("undo")
            val back = (slot(DraftSlots.Active) as DraftProgress.Players).session
            assertThat(back.captains.remaining).containsExactly(remaining[1], remaining[2]).inOrder()
            assertThat(back.selection.choices).hasSize(5)
        }
    }

    @Test
    fun `finishing goes to the timer's match setup or to Who won, with the match saved first`() {
        val hub = hub()
        draftTest(hub) {
            for (then in listOf("timer", "result")) {
                setUp(4, "all-random")
                setup("start")
                val session = (slot(DraftSlots.Active) as DraftProgress.Board).session
                val before = hub.groupStore.read()
                val location = board("finish:$then")
                val matchId = hub.groupStore.read().matches.single { it.draftId == session.id }.id
                assertThat(location).isEqualTo(
                    if (then == "timer") "/timer/setup?match=$matchId" else "/players/matches/$matchId/result",
                )
                assertFinished(hub, before, session, matchId)
                // A second tap (a double submit) saves nothing more.
                assertThat(board("finish:$then")).isEqualTo("/draft")
                assertThat(hub.groupStore.read().matches).hasSize(before.matches.size + 1)
            }
        }
    }

    @Test
    fun `draft again from a match shows the group on GET and fills the setup only on POST`() {
        val hub = hub()
        val played = match("m1", red = listOf("p0", "p1", "p2"), blue = listOf("p3", "p4"))
        runBlocking { hub.groupStore.upsertMatch(played) }
        draftTest(hub) {
            delete(DraftSlots.Setup)
            val html = page("/draft?rematch=m1")
            assertThat(html).contains("Draft again with the same group?")
            assertThat(html).contains("Player 3")
            assertThat(slot(DraftSlots.Setup)).isNull()
            assertThat(post("/draft/rematch", "match" to "m1")).isEqualTo("/draft")
            val form = slot(DraftSlots.Setup)!!
            assertThat(form).isEqualTo(SetupForm().redraft(DraftAgain.of(hub.groupStore.data().match("m1")!!)))
            assertThat(form.red).containsExactly("p0", "p1", "p2").inOrder()
            assertThat(form.oddMode).isEqualTo(OddSeatMode.HANDICAP)
            assertThat(form.stage).isEqualTo(SetupStage.PLAYERS)
            assertThat(page("/draft?rematch=nope")).contains("That match isn't in the group's log.")
        }
    }

    @Test
    fun `balancing by Elo uses the group's match log`() {
        val hub = hub(players = 4)
        runBlocking {
            // p0 and p1 win three games together: they end up on different teams.
            repeat(3) { i ->
                hub.groupStore.upsertMatch(
                    match("w$i", red = listOf("p0", "p1"), blue = listOf("p2", "p3"), playedAt = i.toLong()),
                )
            }
        }
        draftTest(hub) {
            for (i in 0 until 4) setup("toggle:p$i")
            setup("next")
            setup("team-mode:BALANCED")
            val form = slot(DraftSlots.Setup)!!
            assertThat(form.red.toSet()).isNotEqualTo(setOf("p0", "p1"))
            assertThat(form.blue.toSet()).isNotEqualTo(setOf("p0", "p1"))
            assertThat(page("/draft")).contains("Average Elo differs by")
        }
    }

    @Test
    fun `custom methods are kept per browser and never synced`() {
        val hub = hub()
        draftTest(hub) {
            val before = hub.groupStore.read()
            val edit = post("/draft/methods", "op" to "duplicate:house")
            assertThat(edit).startsWith("/draft/methods/edit/custom-")
            val key = edit.substringAfterLast('/')
            assertThat(page(edit)).contains("House draft (copy)")
            post(edit, "op" to "add:REPEAT:PICK:A", "name" to "Our draft", "poolKind" to "RANDOM", "poolValue" to "12")
            assertThat(slot(DraftSlots.MethodEdit)!!.state.name).isEqualTo("Our draft")
            assertThat(post(edit, "op" to "save", "name" to "Our draft", "poolKind" to "RANDOM", "poolValue" to "12"))
                .isEqualTo("/draft/methods")
            val saved = slot(DraftSlots.Methods)!!.single()
            assertThat(saved.id).isEqualTo(key)
            assertThat(saved.name).isEqualTo("Our draft")
            assertThat(saved.pool).isEqualTo(PoolRule.Random(12))
            assertThat(page("/draft/methods")).contains("Our draft")
            // A name left blank is refused with the editor's message.
            val blank = post("/draft/methods/edit/new", "op" to "save", "name" to " ")
            assertThat(blank).isEqualTo("/draft/methods/edit/new#error")
            assertThat(page("/draft/methods/edit/new")).contains("Give the method a name")
            post("/draft/methods", "op" to "delete:$key")
            assertThat(slot(DraftSlots.Methods)).isEmpty()
            assertThat(hub.groupStore.read()).isEqualTo(before)
        }
    }

    @Test
    fun `pages never change state on GET, and every POST needs the hub's own origin`() {
        val hub = hub()
        hubTest(hub) {
            val client = plainClient()
            val token = client.signIn()
            val browser = DraftBrowser(hub, client, token)
            for (path in listOf(
                "/draft",
                "/draft/pool",
                "/draft/board",
                "/draft/methods",
                "/draft/methods/edit/new",
                "/draft/methods/edit/house",
            )) {
                val html = browser.page(path)
                assertThat(html).doesNotContain(" style=")
                assertThat(Regex("<script(?![^>]*\\ssrc=)").containsMatchIn(html)).isFalse()
                assertThat(Regex("\\son[a-z]+=").containsMatchIn(html)).isFalse()
            }
            val dir = tmp.root.toPath().resolve("devices").resolve(browser.deviceId)
            val slots = Files.list(dir).use { files -> files.map { it.fileName.toString() }.toList() }
            assertThat(slots.filter { it.startsWith("draft-") }).isEmpty()

            for (origin in listOf(null, "https://evil.example")) {
                val response = client.submitForm("/draft/setup", parameters { append("op", "toggle:p0") }) {
                    header(HttpHeaders.Cookie, cookie(token))
                    origin?.let { header(HttpHeaders.Origin, it) }
                }
                assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
            }
            assertThat(browser.slot(DraftSlots.Setup)).isNull()
            browser.setup("toggle:p0")
            assertThat(browser.slot(DraftSlots.Setup)!!.selected).containsExactly("p0")
        }
    }

    @Test
    fun `the setup form survives and the coin, stage and quick add work like the phone`() {
        val hub = hub(players = 2)
        draftTest(hub) {
            setup("quick-add", "name" to "  Zed ")
            setup("quick-add", "name" to "zed")
            val zed = hub.groupStore.data().players().single { it.name == "Zed" }
            assertThat(zed.color).isIn(DraftActions.QUICK_ADD_COLORS)
            assertThat(slot(DraftSlots.Setup)!!.selected).containsExactly(zed.id)
            setup("toggle:p0")
            assertThat(setup("next")).isEqualTo("/draft")
            assertThat(slot(DraftSlots.Setup)!!.stage).isEqualTo(SetupStage.TEAMS)
            setup("next")
            setup("next")
            setup("flip")
            val flipped = slot(DraftSlots.Setup)!!
            assertThat(flipped.teamA).isNotNull()
            assertThat(flipped.flipCount).isEqualTo(1)
            setup("stage:PLAYERS")
            assertThat(slot(DraftSlots.Setup)!!.stage).isEqualTo(SetupStage.PLAYERS)
            setup("stage:COIN") // only earlier stages can be jumped to
            assertThat(slot(DraftSlots.Setup)!!.stage).isEqualTo(SetupStage.PLAYERS)
        }
    }
}
