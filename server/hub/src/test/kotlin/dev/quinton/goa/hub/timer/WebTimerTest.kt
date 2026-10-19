package dev.quinton.goa.hub.timer

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.core.presentation.timer.TimerUiMapper
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.player
import dev.quinton.goa.hub.push.P256
import dev.quinton.goa.hub.push.PushSubscription
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.testHub
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.parameters
import java.security.interfaces.ECPublicKey
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The web timer through HTTP with a fake clock: every step and time must be what [TimerEngine]
 * computes directly (the phone's path: re-sync, then the command), the push schedule follows every
 * change, and match setup → run → "Who won?" hands over to the Players area.
 */
class WebTimerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock()
    private val engine = TimerEngine(clock, clock)

    private class Browser(val client: HttpClient, val token: String)

    private suspend fun Browser.cmd(cmd: String, htmx: Boolean = true): HttpResponse =
        client.submitForm("/timer/run/cmd", parameters { append("cmd", cmd) }) {
            header(HttpHeaders.Cookie, cookie(token))
            header(HttpHeaders.Origin, HUB_URL)
            if (htmx) header("HX-Request", "true")
        }

    private suspend fun Browser.form(
        path: String,
        values: Map<String, String> = emptyMap(),
        htmx: Boolean = false,
    ): HttpResponse =
        client.submitForm(path, parameters { values.forEach { (k, v) -> append(k, v) } }) {
            header(HttpHeaders.Cookie, cookie(token))
            header(HttpHeaders.Origin, HUB_URL)
            if (htmx) header("HX-Request", "true")
        }

    private suspend fun Browser.page(path: String): HttpResponse = client.get(path) {
        header(HttpHeaders.Cookie, cookie(token))
    }

    private fun Hub.deviceId(): String = devices.list().single().id

    private fun Hub.session(): WebTimerState? = deviceStore.read(deviceId(), TimerSlots.Timer)

    private fun table(): TimerPreset = TimerData.of(testHubCatalog).presets.presets.first { it.id == "table-rules" }

    private val testHubCatalog get() = hubUnderTest.catalog
    private lateinit var hubUnderTest: Hub

    private fun run(block: suspend Browser.(Hub) -> Unit) {
        val hub = testHub(tmp.root.toPath(), clock)
        hubUnderTest = hub
        hubTest(hub) {
            val c = plainClient()
            Browser(c, c.signIn()).block(hub)
        }
    }

    /** The parts of a session that the engine decides (ids and anchors differ by design). */
    private fun TimerSnapshot.essence() = listOf(
        round, stepIndex, status, runningSince, accumulatedMillis, actors, timeOutsUsed, shotClock, undo?.copy(),
        tieBreaker, actionSlots, matchStartElapsed, endedElapsed,
    )

    private fun assertSame(hub: Hub, direct: TimerSnapshot) {
        val stored = hub.session()!!.snapshot
        assertThat(stored.essence()).isEqualTo(direct.essence())
        assertThat(engine.readout(stored)).isEqualTo(engine.readout(direct))
    }

    @Test
    fun `a scripted session through HTTP matches the engine step for step`() = run { hub ->
        // A preset with auto-advance, Action heroes and time-outs, so every command has work to do.
        val preset = table().let { p ->
            p.copy(
                autoAdvance = true,
                timeOutsPerRound = 1,
                phases = p.phases.map { if (it.kind == PhaseKind.ACTION) it.copy(enabled = true) else it },
            )
        }
        hub.deviceStore.write(hub.deviceId(), TimerSlots.Presets, WebTimerPresets(presets = listOf(preset), seats = 3))

        assertThat(form("/timer/start").status).isEqualTo(HttpStatusCode.Found)
        var direct = engine.start("x", preset, TimerData.of(hub.catalog).rules.endOfRound, actionSlots = 3)
        assertSame(hub, direct)

        suspend fun step(cmd: String, advanceMs: Long = 0, apply: (TimerSnapshot) -> TimerSnapshot) {
            clock.advance(advanceMs)
            val response = cmd(cmd)
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            direct = apply(engine.advanceDue(direct))
            assertSame(hub, direct)
            val endsAt = engine.readout(direct).phaseEndElapsed
            assertThat(response.headers["X-Timer-Ends-At"]).isEqualTo(endsAt?.toString())
            assertThat(response.headers["X-Server-Now"]).isEqualTo(clock.nowMillis().toString())
        }

        step("next", 300) { engine.next(it, TimerEngine.NEXT_DEBOUNCE_MS) } // inside the double-tap guard: ignored
        step("next", 2_000) { engine.next(it, TimerEngine.NEXT_DEBOUNCE_MS) } // Turn 1, play a card
        step("pause", 10_000) { engine.pause(it) }
        step("toggle", 60_000) { engine.togglePause(it) } // resume after a minute paused
        step("shot-start", 3_000) { engine.startShotClock(it) }
        step("timeout:RED", 2_000) { engine.timeOut(it, Team.RED) }
        step("timeout:RED", 1_000) { engine.timeOut(it, Team.RED) } // none left: ignored
        step("hero:2", 1_000) { it } // not an Action step yet: the engine ignores it
        step("jump:1:4", 1_000) { engine.jumpTo(it, 4) }
        step("back", 5_000) { engine.back(it) } // within the undo window: the step comes back with its time
        step("jump:9:4", 1_000) { it } // a jump confirmed in another round is dropped
        step("next", 1_000) { engine.next(it, TimerEngine.NEXT_DEBOUNCE_MS) } // the Action hero steps
        step("hero:2", 1_000) { engine.heroResolves(it, 2) }
        step("hero:0", 4_000) { engine.heroResolves(it, 0) }
        assertThat(cmd("hero:7").status).isEqualTo(HttpStatusCode.BadRequest) // no such seat: refused
        assertSame(hub, direct)
        step("restart", 2_000) { engine.restartStep(it) }

        // A long gap (the phone was locked): the read shows every passed phase end, without writing.
        clock.advance(10 * 60_000L)
        val before = hub.session()!!.snapshot
        val page = page("/timer/run")
        assertThat(page.status).isEqualTo(HttpStatusCode.OK)
        assertThat(hub.session()!!.snapshot).isEqualTo(before)
        val caughtUp = engine.advanceDue(direct)
        assertThat(caughtUp.round to caughtUp.stepIndex).isNotEqualTo(direct.round to direct.stepIndex)
        assertThat(
            page.bodyAsText(),
        ).contains("data-step-key=\"${before.sessionId}:${caughtUp.round}:${caughtUp.stepIndex}\"")
        // The page asks to re-sync: the store catches up exactly as the engine does, and the page alerts once.
        val sync = cmd("sync")
        direct = caughtUp
        assertSame(hub, direct)
        assertThat(sync.bodyAsText()).contains("class=\"tr-alert\"")
        assertThat(cmd("sync").bodyAsText()).doesNotContain("class=\"tr-alert\"")

        step("skip-end", 1_000) { engine.skipToEndOfRound(it) }
        step("end", 1_000) { engine.endMatch(it) }
        assertThat(hub.session()!!.snapshot.status).isEqualTo(TimerStatus.ENDED)
    }

    @Test
    fun `the round view lists the round's steps as the phone maps them`() = run { hub ->
        form("/timer/start")
        clock.advance(1_000)
        assertThat(cmd("layout:round").status).isEqualTo(HttpStatusCode.OK)
        assertThat(
            hub.deviceStore.read(hub.deviceId(), SharedSlots.Settings)!!.timerLayout,
        ).isEqualTo(TimerLayout.ROUND)
        clock.advance(1_000)
        val html = cmd("next").bodyAsText()
        val s = hub.session()!!.snapshot
        val rows = TimerUiMapper.roundRows(s, null)
        assertThat(rows).isNotEmpty()
        rows.filter { it.index != s.stepIndex }.forEach { row ->
            assertThat(html).contains("data-jump=\"jump:${s.round}:${row.index}\"")
            assertThat(html).contains("data-jump-name=\"${row.jumpName}\"")
        }
        assertThat(html).contains("data-current-row")
        assertThat(html).contains("Turn 1 of 4")
        cmd("layout:steps")
        assertThat(
            hub.deviceStore.read(hub.deviceId(), SharedSlots.Settings)!!.timerLayout,
        ).isEqualTo(TimerLayout.STEPS)
    }

    @Test
    fun `phase ends are pushed to a subscribed browser and the schedule follows every change`() = run { hub ->
        val id = hub.deviceId()
        form("/timer/start")
        assertThat(hub.push.scheduled(id)).isEmpty() // not subscribed: nothing scheduled

        val keys = P256.generate()
        hub.push.subscribe(
            id,
            PushSubscription(
                endpoint = "https://web.push.apple.com/QGd4abc",
                p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    P256.encodePublic(keys.public as ECPublicKey),
                ),
                auth = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16) { it.toByte() }),
            ),
        )
        cmd("sync")
        val first = hub.push.scheduled(id).single()
        val s = hub.session()!!.snapshot
        assertThat(first.key).isEqualTo("timer")
        assertThat(first.at).isEqualTo(clock.nowMillis() + engine.readout(s).remainingMillis!!)
        assertThat(first.payload.title).isEqualTo("Time's up: Pre-round strategy · Round 1")
        assertThat(first.payload.body).isEqualTo("Overtime is counting · Next: Turn 1")
        assertThat(first.payload.url).isEqualTo("/timer/run")

        clock.advance(5_000)
        cmd("pause")
        assertThat(hub.push.scheduled(id)).isEmpty()
        clock.advance(30_000)
        cmd("resume")
        assertThat(hub.push.scheduled(id).single().at).isEqualTo(first.at + 30_000)
        clock.advance(1_000)
        cmd("next")
        val turn = hub.push.scheduled(id).single()
        assertThat(turn.payload.title).isEqualTo("Time's up: Turn 1 · Round 1")
        cmd("next") // inside the double-tap guard
        assertThat(hub.push.scheduled(id).single()).isEqualTo(turn)
        clock.advance(1_000)
        cmd("next") // Resolve: untimed, nothing to push
        assertThat(hub.push.scheduled(id)).isEmpty()
        clock.advance(1_000)
        cmd("back") // undo: Turn 1 again, its end pushed again
        assertThat(hub.push.scheduled(id).single().payload.title).isEqualTo("Time's up: Turn 1 · Round 1")
        cmd("end")
        assertThat(hub.push.scheduled(id)).isEmpty()
    }

    @Test
    fun `with auto-advance every coming phase end up to an untimed step is scheduled`() = run { hub ->
        val id = hub.deviceId()
        val preset = table().copy(autoAdvance = true)
        hub.deviceStore.write(id, TimerSlots.Presets, WebTimerPresets(presets = listOf(preset)))
        val keys = P256.generate()
        hub.push.subscribe(
            id,
            PushSubscription(
                "https://fcm.googleapis.com/fcm/send/abc",
                Base64.getUrlEncoder().withoutPadding().encodeToString(P256.encodePublic(keys.public as ECPublicKey)),
                Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16)),
            ),
        )
        form("/timer/start")
        val pushes = hub.push.scheduled(id).sortedBy { it.at }
        // Pre-round strategy 1:30, then Play a card 0:30; Resolve is untimed, so the chain stops there.
        assertThat(pushes.map { it.key }).containsExactly("timer", "timer-2")
        assertThat(pushes[1].at - pushes[0].at).isEqualTo(30_000)
        assertThat(pushes[0].payload.body).isEqualTo("Now: Play a card")
        assertThat(pushes[1].payload.title).isEqualTo("Time's up: Turn 1 · Round 1")
        assertThat(pushes[1].payload.body).isEqualTo("Now: Resolve")
        cmd("pause")
        assertThat(hub.push.scheduled(id)).isEmpty()
    }

    @Test
    fun `a rematch started twice from one setup (Back, then Start) is one match`() = run { hub ->
        runBlocking {
            listOf("ann", "bob", "cat", "dan").forEach {
                hub.groupStore.upsertPlayer(player(it, it.replaceFirstChar(Char::uppercase)))
            }
            hub.groupStore.upsertMatch(
                Match(
                    id = "m9",
                    createdAt = clock.nowMillis(),
                    playedAt = clock.nowMillis(),
                    status = MatchStatus.COMPLETED,
                    mode = GameMode.QUICK,
                    winner = Team.RED,
                    participants = listOf(
                        Participant("ann", "arien", Team.RED),
                        Participant("bob", "brogan", Team.RED),
                        Participant("cat", "xargatha", Team.BLUE),
                        Participant("dan", "wasp", Team.BLUE),
                    ),
                ),
            )
        }
        val opened = page("/timer/setup?rematch=m9&kind=SAME_SEATS")
        assertThat(opened.status).isEqualTo(HttpStatusCode.Found)
        val url = opened.headers[HttpHeaders.Location]!!
        val id = Regex("[?&]id=([^&]+)").find(url)!!.groupValues[1]
        assertThat(page(url).bodyAsText()).contains("value=\"$id\"")
        val start = mapOf("rematch" to "m9", "kind" to "SAME_SEATS", "id" to id)
        assertThat(form("/timer/setup/start", start).status).isEqualTo(HttpStatusCode.Found)
        assertThat(hub.session()!!.snapshot.matchId).isEqualTo(id)
        clock.advance(60_000)
        form("/timer/setup/start", start)
        val pending = hub.groupStore.read().matches.filter { it.status == MatchStatus.PENDING }
        assertThat(pending.map { it.id }).containsExactly(id)
        assertThat(hub.session()!!.snapshot.matchId).isEqualTo(id)
    }

    @Test
    fun `a pending match from the draft is set up, timed, and handed to Who won`() = run { hub ->
        runBlocking {
            listOf("ann", "bob", "cat", "dan").forEach {
                hub.groupStore.upsertPlayer(player(it, it.replaceFirstChar(Char::uppercase)))
            }
            hub.groupStore.upsertMatch(
                Match(
                    id = "m1",
                    createdAt = clock.nowMillis(),
                    playedAt = clock.nowMillis(),
                    status = MatchStatus.PENDING,
                    mode = GameMode.QUICK,
                    participants = listOf(
                        Participant("ann", "arien", Team.RED),
                        Participant("bob", "brogan", Team.RED),
                        Participant("cat", "xargatha", Team.BLUE),
                        Participant("dan", "wasp", Team.BLUE),
                    ),
                    draftId = "d1",
                    tieBreaker = Team.RED,
                ),
            )
        }
        val home = page("/timer").bodyAsText()
        assertThat(home).contains("Pending matches")
        assertThat(home).contains("/timer/setup?match=m1")

        val setup = page("/timer/setup?match=m1")
        assertThat(setup.status).isEqualTo(HttpStatusCode.OK)
        val html = setup.bodyAsText()
        assertThat(html).contains("From the drafted match")
        assertThat(html).contains("Red is already showing from the draft. Leave the coin as it is.")
        assertThat(html).contains("4 Life counters")

        val start = form("/timer/setup/start", mapOf("match" to "m1", "coin" to "BLUE", "mode" to "LONG"))
        assertThat(start.status).isEqualTo(HttpStatusCode.Found)
        assertThat(start.headers[HttpHeaders.Location]).isEqualTo("/timer/run")
        val stored = hub.groupStore.read().matches.single { it.id == "m1" }
        assertThat(stored.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(stored.mode).isEqualTo(GameMode.LONG)
        val s = hub.session()!!.snapshot
        assertThat(s.matchId).isEqualTo("m1")
        assertThat(s.actionSlots).isEqualTo(4)
        assertThat(s.tieBreaker).isEqualTo(Team.BLUE)

        // Levels opens the rules' Levels & defeats for the heroes at this table, as the phone's sheet;
        // Back there returns to the timer.
        val levels = Regex("href=\"(/rules/levels\\?heroes=[^\"]*)\"").find(page("/timer/run").bodyAsText())
        assertThat(levels).isNotNull()
        assertThat(levels!!.groupValues[1].substringAfter("heroes=").split(','))
            .containsExactly("arien", "brogan", "xargatha", "wasp")
        val ladder = client.get(levels.groupValues[1]) {
            header(HttpHeaders.Cookie, cookie(token))
            header(HttpHeaders.Referrer, "$HUB_URL/timer/run")
        }.bodyAsText()
        assertThat(ladder).contains("href=\"/timer/run\" class=\"icon-button\"")
        assertThat(page("/rules/levels").bodyAsText()).contains("href=\"/rules\" class=\"icon-button\"")

        // The Action chooser names the match's heroes and pilots.
        clock.advance(47 * 60_000L)
        cmd("end")
        assertThat(hub.groupStore.read().matches.single { it.id == "m1" }.durationMin).isEqualTo(47)
        val log = cmd("log", htmx = false)
        assertThat(log.status).isEqualTo(HttpStatusCode.Found)
        // As the phone: the result is dated from the game's start, not from the draft.
        val result = "/players/matches/m1/result?playedAt=${s.matchStartWall}"
        assertThat(log.headers[HttpHeaders.Location]).isEqualTo(result)
        val minute = dev.quinton.goa.core.presentation.players.MatchEditor.toMinute(s.matchStartWall)
        assertThat(page(result).bodyAsText()).contains("name=\"playedAtMs\" value=\"$minute\"")
        assertThat(hub.session()).isNull()
    }

    @Test
    fun `without a match Log result reserves an id and keeps the summary until saved`() = run { hub ->
        form("/timer/start")
        clock.advance(20 * 60_000L)
        cmd("end")
        val log = cmd("log")
        val target = log.headers["HX-Redirect"]!!
        assertThat(target).startsWith("/players/matches/")
        assertThat(target).contains("/result?new=1&durationMin=20&playedAt=")
        val reserved = hub.session()!!.snapshot.loggingMatchId!!
        assertThat(target).contains(reserved)
        assertThat(cmd("log").headers["HX-Redirect"]).isEqualTo(target) // the same id every time
        assertThat(page("/timer").status).isEqualTo(HttpStatusCode.Found) // the summary stays

        runBlocking {
            hub.groupStore.upsertMatch(
                Match(
                    reserved,
                    clock.nowMillis(),
                    clock.nowMillis(),
                    MatchStatus.COMPLETED,
                    GameMode.QUICK,
                    participants = emptyList(),
                ),
            )
        }
        assertThat(page("/timer").status).isEqualTo(HttpStatusCode.OK) // logged: the home again
    }

    @Test
    fun `the preset editor validates like the phone and stored presets migrate on read`() = run { hub ->
        val id = hub.deviceId()
        val blank = form(
            "/timer/presets/edit",
            mapOf(
                "id" to "p1", "new" to "1", "name" to " ", "phase" to "turn", "p.turn.kind" to "TURN",
                "p.turn.scope" to "TURN",
                "p.turn.enabled" to "true", "p.turn.min" to "0", "p.turn.sec" to "3", "p.turn.was" to "3",
                "op" to "save",
            ),
        )
        assertThat(blank.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
        val blankHtml = blank.bodyAsText()
        assertThat(blankHtml).contains("Give the preset a name")
        assertThat(blankHtml).contains("Duration must be between 5 s and 60 min.")

        val good = form(
            "/timer/presets/edit",
            mapOf(
                "id" to "p1", "new" to "1", "name" to " Quick night ", "phase" to "turn", "p.turn.kind" to "TURN",
                "p.turn.scope" to "TURN", "p.turn.enabled" to "true", "p.turn.min" to "0", "p.turn.sec" to "45",
                "p.turn.was" to "45", "p.turn.name" to "Play a card", "p.turn.cue" to "10", "shotOn" to "true",
                "shot" to "20", "timeOuts" to "0",
                "timeOutSeconds" to "40", "op" to "save",
            ),
        )
        assertThat(good.status).isEqualTo(HttpStatusCode.Found)
        val saved = hub.deviceStore.read(id, TimerSlots.Presets)!!.presets!!.single { it.id == "p1" }
        assertThat(saved.name).isEqualTo("Quick night")
        assertThat(saved.phases.single().seconds).isEqualTo(45)
        assertThat(saved.phases.single().cues).containsExactly(10)
        assertThat(saved.shotClockSeconds).isEqualTo(20)

        // A copy of a bundled preset saved before revision 1 (no Resolve, the Turn phase called "Turn").
        val old = table().let { p ->
            p.copy(
                revision = 0,
                phases = p.phases.filterNot { it.kind == PhaseKind.RESOLVE }
                    .map { if (it.kind == PhaseKind.TURN) it.copy(name = "Turn") else it },
            )
        }
        hub.deviceStore.write(id, TimerSlots.Presets, WebTimerPresets(presets = listOf(old)))
        val list = page("/timer/presets").bodyAsText()
        assertThat(list).contains("Play a card 0:30 ×4 · Resolve")
    }

    @Test
    fun `timer posts need the hub's origin and a signed-in browser`() = run { hub ->
        val noOrigin = client.submitForm("/timer/run/cmd", parameters { append("cmd", "next") }) {
            header(HttpHeaders.Cookie, cookie(token))
        }
        assertThat(noOrigin.status).isEqualTo(HttpStatusCode.Forbidden)
        val elsewhere = client.submitForm("/timer/start") {
            header(HttpHeaders.Cookie, cookie(token))
            header(HttpHeaders.Origin, "https://evil.example")
        }
        assertThat(elsewhere.status).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(hub.session()).isNull()
        val signedOut = client.submitForm("/timer/start") { header(HttpHeaders.Origin, HUB_URL) }
        assertThat(signedOut.status).isNotEqualTo(HttpStatusCode.Found)
        assertThat(hub.session()).isNull()
    }

    @Test
    fun `the other pages show the running timer bar`() = run { hub ->
        form("/timer/start")
        val heroes = page("/heroes").bodyAsText()
        assertThat(heroes).contains("data-timer-bar")
        assertThat(heroes).contains("Pre-round strategy")
        assertThat(heroes).contains("timer/timer-bar.js")
        assertThat(page("/timer/run").bodyAsText()).doesNotContain("data-timer-bar")
        cmd("end")
        assertThat(page("/heroes").bodyAsText()).doesNotContain("data-timer-bar")
    }
}
