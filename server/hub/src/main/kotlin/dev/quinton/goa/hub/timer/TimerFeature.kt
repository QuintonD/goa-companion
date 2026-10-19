package dev.quinton.goa.hub.timer

import dev.quinton.goa.core.domain.match.Rematch
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.setup.MatchSetup
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.core.presentation.timer.MatchSetupBuilder
import dev.quinton.goa.core.presentation.timer.MatchSetupChoices
import dev.quinton.goa.core.presentation.timer.MatchSetupUiState
import dev.quinton.goa.core.presentation.timer.RematchRequest
import dev.quinton.goa.core.presentation.timer.RunningTimerUi
import dev.quinton.goa.core.presentation.timer.TimerCommand
import dev.quinton.goa.core.presentation.timer.TimerPresets
import dev.quinton.goa.core.presentation.timer.TimerUiMapper
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeature
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.timer.TimerRunPage.timerRoot
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.isHtmx
import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.StaticFiles
import dev.quinton.goa.hub.web.ui.Tab
import dev.quinton.goa.hub.web.ui.respondFragment
import dev.quinton.goa.hub.web.ui.respondPage
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID
import kotlin.random.Random
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.script

/** The Timer area's paths (others link here: keep them). */
object TimerRoutes {
    const val HOME = "/timer"
    const val START = "/timer/start"
    const val SETUP = "/timer/setup"
    const val SETUP_FLIP = "/timer/setup/flip"
    const val SETUP_START = "/timer/setup/start"
    const val RUN = "/timer/run"
    const val CMD = "/timer/run/cmd"
    const val BAR = "/timer/bar"
    const val PRESETS = "/timer/presets"
    const val PRESET_EDIT = "/timer/presets/edit"

    /** "Who won?" for a match (built by the Players area). */
    fun result(matchId: String) = "/players/matches/${matchId.encodeURLParameter()}/result"
}

/**
 * Timer on the web (ADR-030 §9): presets, match setup, the running timer (step and round views), its
 * summary and the "timer running" bar on other pages. The session runs on the hub ([WebTimer]); the
 * page counts down from the hub's instants and asks the hub to advance (`static/timer/timer.js`).
 */
object TimerFeature : HubFeature {
    internal val page = HubPage(
        "Timer",
        Tab.TIMER,
        scripts = listOf("push.js", "timer/timer.js"),
        styles = listOf("timer/timer.css"),
    )

    override fun Route.routes(ctx: HubContext) {
        val timer = WebTimer(ctx)
        homeRoutes(ctx, timer)
        setupRoutes(ctx, timer)
        runRoutes(ctx, timer)
        presetRoutes(timer)
    }

    override fun shellBar(ctx: HubContext, call: ApplicationCall): (FlowContent.() -> Unit)? {
        val path = call.request.local.uri.substringBefore('?')
        if (path == TimerRoutes.RUN) return null
        // Status pages (404, …) render outside the signed-in area: no device, no bar.
        val device = ctx.deviceOrNull(call) ?: return null
        val bar = barModel(ctx, WebTimer(ctx), device.id) ?: return null
        return {
            div("timer-bar-host") {
                id = "timer-bar-host"
                attributes["hx-get"] = TimerRoutes.BAR
                attributes["hx-trigger"] = "timerbar"
                attributes["hx-swap"] = "innerHTML"
                runningBar(bar)
            }
            // The bar's own small clock (no inline script: a same-origin file, CSP-safe).
            script(src = StaticFiles.url("timer/timer-bar.js")) { defer = true }
        }
    }

    // ---- home ----------------------------------------------------------------------------------

    private fun Route.homeRoutes(ctx: HubContext, timer: WebTimer) {
        get(TimerRoutes.HOME) {
            val id = call.device.id
            val running = timer.read(id)?.takeUnless { timer.loggedAlready(it) }
            if (running != null) return@get call.respondRedirect(TimerRoutes.RUN)
            call.respondPage(page) { TimerHomePage.home(this, homeModel(ctx, timer, id)) }
        }
        post(TimerRoutes.START) {
            val id = call.device.id
            val presets = timer.presets(id)
            timer.start(id, presets.selected, actionSlots = presets.seats)
            call.respondRun(ctx, timer)
        }
        post("${TimerRoutes.HOME}/select") {
            val presetId = call.receiveParameters()["preset"].orEmpty()
            timer.updatePresets(call.device.id) { stored, list ->
                if (list.any { it.id == presetId }) stored.copy(selectedPresetId = presetId) else stored
            }
            call.respondRedirect(TimerRoutes.HOME)
        }
        post("${TimerRoutes.HOME}/seats") {
            val seats = call.receiveParameters()["seats"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            timer.updatePresets(call.device.id) { stored, _ ->
                stored.copy(seats = seats.coerceIn(WebTimer.MIN_SEATS, WebTimer.MAX_SEATS))
            }
            call.respondRedirect(TimerRoutes.HOME)
        }
        post("${TimerRoutes.HOME}/tonight") {
            val params = call.receiveParameters()
            val id = call.device.id
            val view = timer.presets(id)
            val own = TimerPresets.turnSeconds(view.selected)
            when (params["op"]) {
                "save" -> {
                    val seconds = view.tonightSeconds
                    timer.updatePresets(id) { stored, list ->
                        if (seconds == null) {
                            stored
                        } else {
                            val saved = TimerPresets.withTonight(view.selected, seconds)
                            stored.copy(presets = TimerPresets.save(list, saved), tonight = null)
                        }
                    }
                }

                "reset" -> timer.updatePresets(id) { stored, _ -> stored.copy(tonight = null) }

                else -> {
                    val seconds =
                        params["seconds"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val clamped = seconds.coerceIn(TimerPresets.TONIGHT_MIN_SECONDS, TimerPresets.TONIGHT_MAX_SECONDS)
                    timer.updatePresets(id) { stored, _ ->
                        stored.copy(
                            tonight = if (own == null ||
                                clamped == own
                            ) {
                                null
                            } else {
                                WebTonight(view.selectedId, clamped)
                            },
                        )
                    }
                }
            }
            call.respondRedirect(TimerRoutes.HOME)
        }
        post("${TimerRoutes.HOME}/pending/{matchId}/discard") {
            val matchId = call.parameters["matchId"].orEmpty()
            val match = ctx.groupStore.read().matches.firstOrNull { it.id == matchId }
            // Only a still-pending match leaves the log (a result already logged is never touched).
            if (match != null && match.status == MatchStatus.PENDING) ctx.groupStore.deleteMatch(matchId)
            call.respondRedirect(TimerRoutes.HOME)
        }
        get(TimerRoutes.BAR) {
            val bar = barModel(ctx, timer, call.device.id)
            call.response.header("X-Server-Now", ctx.clock.nowMillis().toString())
            call.respondFragment { bar?.let { runningBar(it) } }
        }
    }

    internal fun homeModel(ctx: HubContext, timer: WebTimer, deviceId: String): TimerHomeModel {
        val view = timer.presets(deviceId)
        val data = ctx.groupStore.data()
        val catalog = runCatching { ctx.catalog.catalog }.getOrNull()
        val pending = TimerUiMapper.pendingMatches(data.matches).map {
            TimerUiMapper.pendingItem(it, catalog, TimerFormat.since(it.createdAt, ctx.clock))
        }
        return TimerHomeModel(
            presets = view.list.map(TimerUiMapper::presetOption),
            selectedId = view.selectedId,
            tonight = TimerUiMapper.tonight(view.selected, view.tonightSeconds),
            seats = view.seats,
            pending = pending,
            pushOn = ctx.push.subscriptions(deviceId).isNotEmpty(),
        )
    }

    // ---- match setup ---------------------------------------------------------------------------

    private fun Route.setupRoutes(ctx: HubContext, timer: WebTimer) {
        get(TimerRoutes.SETUP) {
            val query = SetupQuery.from(call.request.queryParameters)
            // A rematch gets its id once, kept in the URL, so Start pressed again (Back, a reload)
            // reuses one match instead of leaving a second pending one (the phone keeps one per screen).
            if (query.rematch != null && query.newId == null) {
                return@get call.respondRedirect(query.copy(newId = UUID.randomUUID().toString()).url())
            }
            val built = setupState(ctx, timer, call.device.id, query)
            call.respondPage(
                page.copy(title = "Match setup", tab = Tab.TIMER, back = TimerRoutes.HOME, actions = emptyList()),
            ) {
                MatchSetupPage.render(this, built, query)
            }
        }
        post(TimerRoutes.SETUP_FLIP) {
            val query = SetupQuery.from(call.receiveParameters())
            val flipped = query.copy(coin = MatchSetup.flipTieBreaker(Random.Default), flips = query.flips + 1)
            call.respondRedirect(flipped.url())
        }
        post(TimerRoutes.SETUP_START) {
            val query = SetupQuery.from(call.receiveParameters())
            val id = call.device.id
            val built = setupState(ctx, timer, id, query)
            if (built.missing) return@post call.respondRedirect(query.url())
            val state = built.state
            val preset =
                timer.presets(id).list.firstOrNull { it.id == state.selectedPresetId } ?: timer.presets(id).selected
            val linked = when {
                query.match != null -> ctx.groupStore.read().matches.firstOrNull { it.id == query.match }

                // Started before (Back, then Start again): the same match, never a second one.
                query.newId != null && ctx.groupStore.read().matches.any { it.id == query.newId } ->
                    ctx.groupStore.read().matches.first { it.id == query.newId }

                // A rematch is saved only now, as the game actually starts (ADR-024).
                built.rematchSource != null && query.kind != null -> Rematch.create(
                    built.rematchSource,
                    query.kind,
                    query.newId ?: UUID.randomUUID().toString(),
                    ctx.clock.nowMillis(),
                ).getOrNull()

                else -> null
            }
            if (linked != null) {
                // The coin as placed at setup is part of the match record (draft coin, or corrected here).
                val setUp = { m: Match ->
                    m.copy(
                        mode = state.selectedMode,
                        tieBreaker =
                            state.tieBreaker ?: m.tieBreaker,
                    )
                }
                // A pending match changes as stored (its logged pilots kept); a rematch is new.
                if (query.match != null || ctx.groupStore.read().matches.any { it.id == linked.id }) {
                    ctx.groupStore.editMatch(linked.id, setUp)
                } else {
                    ctx.groupStore.upsertMatch(setUp(linked))
                }
            }
            val heroes = state.counters?.let { it.largerTeamHeroes + it.smallerTeamHeroes } ?: state.players
            timer.start(
                id,
                preset,
                heroes,
                matchId = linked?.id,
                mode = state.selectedMode,
                tieBreaker = state.tieBreaker,
            )
            call.respondRun(ctx, timer)
        }
    }

    internal class SetupBuilt(
        val state: MatchSetupUiState,
        val missing: Boolean,
        val rematchSource: Match?,
        /** The coin recorded on the linked match (draft or earlier setup). */
        val storedCoin: Team?,
    )

    private fun setupState(ctx: HubContext, timer: WebTimer, deviceId: String, q: SetupQuery): SetupBuilt {
        val data = ctx.groupStore.data()
        val source = q.rematch?.let { data.match(it) }
        val match: Match? = when {
            q.match != null -> data.match(q.match)

            source != null && q.kind != null -> Rematch.create(
                source,
                q.kind,
                "rematch",
                ctx.clock.nowMillis(),
            ).getOrNull()

            else -> null
        }
        val missing = (q.match != null && match == null) || (q.rematch != null && match == null)
        val presets = timer.presets(deviceId)
        val running = timer.read(deviceId)
        val state = MatchSetupBuilder.build(
            timer.data.rules,
            match,
            MatchSetupChoices(
                players = q.players,
                layoutId = q.layout,
                mode = q.mode,
                tieBreaker = q.coin,
                flips = q.flips,
                correctingCoin = q.correcting,
                presetId = q.preset,
            ),
            presets.list.map(TimerUiMapper::presetOption),
            q.preset?.takeIf { id -> presets.list.any { it.id == id } } ?: presets.selectedId,
            q.rematch?.let { r -> q.kind?.let { RematchRequest(r, it) } },
        ).copy(replacesRunningTimer = running != null && running.status != TimerStatus.ENDED)
        return SetupBuilt(state, missing, source, match?.tieBreaker)
    }

    // ---- running timer -------------------------------------------------------------------------

    private fun Route.runRoutes(ctx: HubContext, timer: WebTimer) {
        get(TimerRoutes.RUN) {
            val id = call.device.id
            val s = timer.read(id)?.takeUnless { timer.loggedAlready(it) }
                ?: return@get call.respondRedirect(TimerRoutes.HOME)
            call.respondRunPage(runModel(ctx, timer, id, s, alert = null))
        }
        post(TimerRoutes.CMD) {
            val id = call.device.id
            val cmd = call.receiveParameters()["cmd"].orEmpty()
            when {
                cmd.startsWith("layout:") -> {
                    val layout = when (cmd.removePrefix("layout:")) {
                        "round" -> TimerLayout.ROUND
                        "steps" -> TimerLayout.STEPS
                        else -> return@post call.respond(HttpStatusCode.BadRequest)
                    }
                    // The running timer's view switch saves at once, like the phone's (ADR-026); only this field.
                    ctx.deviceStore.update(id, SharedSlots.Settings) {
                        (it ?: AppSettings()).copy(timerLayout = layout)
                    }
                    call.respondCommand(ctx, timer, timer.perform(id, null).copy(alert = null))
                }

                cmd == "log" -> call.logResult(timer)

                cmd == "clear" -> {
                    timer.clear(id)
                    call.redirectTo(TimerRoutes.HOME)
                }

                cmd == "sync" -> call.respondCommand(ctx, timer, timer.perform(id, null))

                else -> {
                    val current = timer.read(id) ?: return@post call.redirectTo(TimerRoutes.HOME)
                    val command = TimerCommandParser.parse(cmd, current)
                        ?: return@post call.respondText("Unknown command", status = HttpStatusCode.BadRequest)
                    call.respondCommand(ctx, timer, timer.perform(id, command))
                }
            }
        }
    }

    private suspend fun ApplicationCall.respondCommand(ctx: HubContext, timer: WebTimer, outcome: TimerOutcome) {
        val state = outcome.state ?: return redirectTo(TimerRoutes.HOME)
        if (!isHtmx) return respondRedirect(TimerRoutes.RUN)
        val model = runModel(ctx, timer, device.id, state.snapshot, outcome.alert)
        runHeaders(model)
        respondFragment { timerRoot(model) }
    }

    private suspend fun ApplicationCall.logResult(timer: WebTimer) {
        val id = device.id
        val s = timer.read(id)?.takeIf { it.status == TimerStatus.ENDED } ?: return redirectTo(TimerRoutes.HOME)
        val linked = s.matchId
        if (linked != null) {
            timer.clear(id)
            // As the phone's OpenMatch: the pending match becomes a result dated from the game's start.
            return redirectTo("${TimerRoutes.result(linked)}?playedAt=${s.matchStartWall}")
        }
        val reserved = timer.reserveLoggingMatchId(id) ?: return redirectTo(TimerRoutes.HOME)
        val minutes = TimerUiMapper.durationMinutes(timer.engine.readout(s).matchElapsedMillis)
        val mode = TimerUiMapper.legalMode(s, timer.data.rules)?.let {
            GameMode.serializer().descriptor.getElementName(it.ordinal)
        }
        val query = listOfNotNull(
            "new=1",
            "durationMin=$minutes",
            "playedAt=${s.matchStartWall}",
            mode?.let { "mode=$it" },
        ).joinToString("&")
        redirectTo("${TimerRoutes.result(reserved)}?$query")
    }

    /** Full page, or (from htmx, e.g. Start) the page swapped into the body, so this tab keeps its audio unlock. */
    private suspend fun ApplicationCall.respondRun(ctx: HubContext, timer: WebTimer) {
        if (!isHtmx) return respondRedirect(TimerRoutes.RUN)
        val id = device.id
        val s = timer.read(id) ?: return redirectTo(TimerRoutes.HOME)
        response.header("HX-Push-Url", TimerRoutes.RUN)
        respondRunPage(runModel(ctx, timer, id, s, alert = null))
    }

    private suspend fun ApplicationCall.respondRunPage(model: RunModel) {
        runHeaders(model)
        respondPage(page.copy(showTabs = false, actions = emptyList())) {
            div("visually-hidden") {
                id = TimerRunPage.ANNOUNCE_ID
                attributes["aria-live"] = "polite"
                attributes["aria-atomic"] = "true"
            }
            timerRoot(model)
        }
    }

    private fun ApplicationCall.runHeaders(model: RunModel) {
        response.header("X-Server-Now", model.live.serverNow.toString())
        model.live.endsAt?.let { response.header("X-Timer-Ends-At", it.toString()) }
    }

    internal fun runModel(
        ctx: HubContext,
        timer: WebTimer,
        deviceId: String,
        s: TimerSnapshot,
        alert: String?,
    ): RunModel {
        val r = timer.engine.readout(s)
        val settings = ctx.deviceStore.read(deviceId, SharedSlots.Settings) ?: AppSettings()
        val ended = s.status == TimerStatus.ENDED
        val active = if (ended) {
            null
        } else {
            TimerUiMapper.active(s, r, seatsFor(ctx, s), layout = settings.timerLayout)
        }
        val step = r.step
        val running = s.status == TimerStatus.RUNNING
        val shot = s.shotClock
        val firstCue = step.cues.filter { it > 0 }.maxOrNull()
        val live = LiveClock(
            serverNow = ctx.clock.nowMillis(),
            stepKey = "${s.sessionId}:${s.round}:${s.stepIndex}",
            zeroAt = if (running) timer.wallOf(s.runningSince!! - s.accumulatedMillis) else null,
            durationMs = step.durationMillis,
            endsAt = r.phaseEndElapsed?.let(timer::wallOf),
            matchZeroAt = timer.wallOf(s.matchStartElapsed),
            shotZeroAt = shot?.runningSince?.let { timer.wallOf(it - shot.accumulatedMillis) },
            shotDurationMs = shot?.durationMillis,
            cues = step.cues,
            warnMs = firstCue?.let { it * MILLIS },
            announce = active?.let { "Round ${it.round} · ${it.context} · ${it.title}" } ?: "Match over",
        )
        return RunModel(
            snapshot = s,
            active = active,
            summary = if (ended) TimerUiMapper.summary(s, r) else null,
            settings = settings,
            pushOn = ctx.push.subscriptions(deviceId).isNotEmpty(),
            alert = alert,
            live = live,
            ladder = timer.data.ladder,
        )
    }

    private fun seatsFor(ctx: HubContext, s: TimerSnapshot) = s.matchId?.let { id ->
        val data = ctx.groupStore.data()
        data.match(id)?.let { match ->
            TimerUiMapper.seats(
                match,
                runCatching {
                    ctx.catalog.catalog
                }.getOrNull(),
                data.players(includeArchived = true),
            )
        }
    }

    internal fun barModel(ctx: HubContext, timer: WebTimer, deviceId: String): BarModel? {
        val s = timer.read(deviceId) ?: return null
        if (s.status == TimerStatus.ENDED) return null
        val r = timer.engine.readout(s)
        return BarModel(
            ui = RunningTimerUi.from(s, r),
            serverNow = ctx.clock.nowMillis(),
            zeroAt = if (s.status ==
                TimerStatus.RUNNING
            ) {
                timer.wallOf(s.runningSince!! - s.accumulatedMillis)
            } else {
                null
            },
            durationMs = r.step.durationMillis,
        )
    }

    // ---- presets -------------------------------------------------------------------------------

    private fun Route.presetRoutes(timer: WebTimer) = with(PresetPages) { routes(timer) }

    /** htmx follows `HX-Redirect`; a plain form gets a 303. */
    internal suspend fun ApplicationCall.redirectTo(path: String) {
        if (isHtmx) {
            response.header("HX-Redirect", path)
            respond(HttpStatusCode.NoContent)
        } else {
            respondRedirect(path)
        }
    }

    private const val MILLIS = 1000L
}

/** Match setup's choices, carried in the page's links and forms (nothing stored until Start). */
internal data class SetupQuery(
    val match: String? = null,
    val rematch: String? = null,
    val kind: RematchKind? = null,
    /** The id the rematch will be saved with (made once per setup, see `GET /timer/setup`). */
    val newId: String? = null,
    val players: Int? = null,
    val layout: String? = null,
    val mode: GameMode? = null,
    val preset: String? = null,
    val coin: Team? = null,
    val flips: Int = 0,
    val correcting: Boolean = false,
) {
    fun url(): String {
        val params = listOfNotNull(
            match?.let { "match" to it },
            rematch?.let { "rematch" to it },
            kind?.let { "kind" to it.name },
            newId?.let { "id" to it },
            players?.let { "players" to it.toString() },
            layout?.let { "layout" to it },
            mode?.let { "mode" to it.name },
            preset?.let { "preset" to it },
            coin?.let { "coin" to it.name },
            flips.takeIf { it > 0 }?.let { "flips" to it.toString() },
            "correct".takeIf { correcting }?.let { it to "1" },
        )
        val query = params.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
        return if (query.isEmpty()) TimerRoutes.SETUP else "${TimerRoutes.SETUP}?$query"
    }

    /** The same choices as hidden form fields. */
    fun fields(): List<Pair<String, String>> = url().substringAfter('?', "").split('&').filter { it.isNotEmpty() }.map {
        it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
    }

    companion object {
        fun from(p: Parameters) = SetupQuery(
            match = p["match"]?.takeIf { it.isNotBlank() },
            rematch = p["rematch"]?.takeIf { it.isNotBlank() },
            kind = p["kind"]?.let { k -> RematchKind.entries.firstOrNull { it.name == k } },
            newId = p["id"]?.takeIf { id -> id.length in 8..64 && id.all { it.isLetterOrDigit() || it == '-' } },
            players = p["players"]?.toIntOrNull(),
            layout = p["layout"]?.takeIf { it.isNotBlank() },
            mode = p["mode"]?.let { m -> GameMode.entries.firstOrNull { it.name == m } },
            preset = p["preset"]?.takeIf { it.isNotBlank() },
            coin = p["coin"]?.let { c -> Team.entries.firstOrNull { it.name == c } },
            flips = p["flips"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            correcting = p["correct"] == "1",
        )
    }
}

/** The running timer's form commands (`cmd`), checked against the session they act on. */
internal object TimerCommandParser {
    /** Commands without arguments. */
    private val simple: Map<String, TimerCommand> = mapOf(
        "toggle" to TimerCommand.TogglePause,
        "pause" to TimerCommand.Pause,
        "resume" to TimerCommand.Resume,
        "next" to TimerCommand.Next,
        "back" to TimerCommand.Back,
        "restart" to TimerCommand.Restart,
        "skip-end" to TimerCommand.SkipToEndOfRound,
        "end" to TimerCommand.EndMatch,
        "due" to TimerCommand.AdvanceDue,
        "flip-coin" to TimerCommand.FlipTieBreaker,
        "shot-start" to TimerCommand.StartShotClock,
        "shot-cancel" to TimerCommand.CancelShotClock,
    )

    fun parse(cmd: String, s: TimerSnapshot): TimerCommand? {
        simple[cmd]?.let { return it }
        val parts = cmd.split(':')
        val team = Team.entries.firstOrNull { it.name == parts.getOrNull(1) }
        return when (parts[0]) {
            "coin" -> if (parts.getOrNull(1) ==
                "none"
            ) {
                TimerCommand.SetTieBreaker(null)
            } else {
                team?.let { TimerCommand.SetTieBreaker(it) }
            }

            "timeout" -> team?.let { TimerCommand.TimeOut(it) }

            "hero" -> parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0 until s.actionSlots }?.let {
                TimerCommand.HeroResolves(it)
            }

            "jump" -> jump(parts)

            else -> null
        }
    }

    private fun jump(parts: List<String>): TimerCommand? {
        val round = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val step = parts.getOrNull(2)?.toIntOrNull() ?: return null
        return TimerCommand.JumpTo(round, step)
    }
}
