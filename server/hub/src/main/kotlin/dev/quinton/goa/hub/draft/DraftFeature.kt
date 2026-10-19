package dev.quinton.goa.hub.draft

import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.draft.StepSpec
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.draft.BoardLocal
import dev.quinton.goa.core.presentation.draft.DraftAgain
import dev.quinton.goa.core.presentation.draft.DraftBoard
import dev.quinton.goa.core.presentation.draft.DraftEnvironment
import dev.quinton.goa.core.presentation.draft.DraftProgress
import dev.quinton.goa.core.presentation.draft.DraftSetupPresenter
import dev.quinton.goa.core.presentation.draft.MethodEditUiState
import dev.quinton.goa.core.presentation.draft.PoolKind
import dev.quinton.goa.core.presentation.draft.PreviewLayout
import dev.quinton.goa.core.presentation.draft.QuickAddPlayer
import dev.quinton.goa.core.presentation.draft.SeatTap
import dev.quinton.goa.core.presentation.draft.SetupForm
import dev.quinton.goa.core.presentation.draft.SetupStage
import dev.quinton.goa.core.presentation.draft.StepSection
import dev.quinton.goa.core.presentation.draft.TeamMode
import dev.quinton.goa.core.presentation.draft.withSaved
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeature
import dev.quinton.goa.hub.store.InvalidGroupDataException
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.ui.PlayerPalette
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Draft area (ADR-030 §8): setup, custom methods and the pass-and-play board, run by the same
 * `core:presentation` code as the phone's draft ([DraftSetupPresenter], [DraftBoard]) over the same
 * `DraftEngine`. Every tap is a POST answered with a redirect to a GET (`Cache-Control: no-store`),
 * so a hidden pick never sits in a page the browser keeps: Back and history always re-ask the hub,
 * which shows the board as it is now. A finished draft becomes a PENDING match + `DraftRecord`
 * through `GroupStore` (stamped like the phone's repositories), then Timer or "Who won?".
 *
 * Routes: `/draft` (setup; `?rematch={matchId}` drafts again with that match's group), `/draft/pool`
 * (custom hero pool), `/draft/board`, `/draft/methods`, `/draft/methods/edit/{id|new}`.
 */
class DraftFeature(
    private val environment: (HubContext) -> DraftEnvironment = { HubDraftEnvironment(it.clock) },
) : HubFeature {
    private val locks = ConcurrentHashMap<String, Mutex>()

    private suspend fun <T> locked(deviceId: String, block: suspend () -> T): T =
        locks.computeIfAbsent(deviceId) { Mutex() }.withLock { block() }

    override fun Route.routes(ctx: HubContext) {
        val data = DraftData(ctx)
        val env = environment(ctx)
        val pages = DraftPages(ctx, data)
        val actions = DraftActions(ctx, data, env)

        get(PATH) {
            val rematch = call.request.queryParameters["rematch"]
            if (rematch != null) pages.rematch(call, rematch) else pages.setup(call)
        }
        post("$PATH/rematch") {
            val id = call.device.id
            val params = call.receiveParameters()
            locked(id) { actions.rematch(id, params["match"].orEmpty()) }
            call.seeOther(PATH)
        }
        post("$PATH/setup") {
            val id = call.device.id
            val params = call.receiveParameters()
            call.seeOther(locked(id) { actions.setup(id, params) })
        }
        get("$PATH/pool") { pages.pool(call) }
        get(BOARD) { pages.board(call) }
        post(BOARD) {
            val id = call.device.id
            val params = call.receiveParameters()
            call.seeOther(locked(id) { actions.board(id, params) })
        }
        get(METHODS) { pages.methods(call) }
        post(METHODS) {
            val id = call.device.id
            val params = call.receiveParameters()
            call.seeOther(locked(id) { actions.methods(id, params) })
        }
        get("$METHODS/edit/{key}") { pages.methodEditor(call, call.parameters["key"].orEmpty(), env) }
        post("$METHODS/edit/{key}") {
            val id = call.device.id
            val key = call.parameters["key"].orEmpty()
            val params = call.receiveParameters()
            call.seeOther(locked(id) { actions.editMethod(id, key, params) })
        }
    }

    companion object {
        const val PATH = "/draft"
        const val BOARD = "/draft/board"
        const val METHODS = "/draft/methods"

        /** Where a finished draft continues: the Timer area's match setup, or the Players area's "Who won?". */
        fun timerSetup(matchId: String) = "/timer/setup?match=$matchId"

        fun result(matchId: String) = "/players/matches/$matchId/result"

        const val MATCH_LOG = "/players/matches"
    }
}

/** POST → 303 → GET: the browser's history only ever holds GETs of the current state. */
internal suspend fun ApplicationCall.seeOther(location: String) {
    response.header(HttpHeaders.Location, location)
    respond(HttpStatusCode.SeeOther)
}

/** "op" values are "name" or "name:argument" (the argument may itself contain ':'). */
internal fun Parameters.op(): Pair<String, String> {
    val raw = this["op"].orEmpty()
    return raw.substringBefore(':') to raw.substringAfter(':', "")
}

/** What each POST does: the shared presentation code applied to this browser's slots. */
internal class DraftActions(
    private val ctx: HubContext,
    private val data: DraftData,
    private val env: DraftEnvironment,
) {
    private val store get() = ctx.deviceStore

    // ------------------------------------------------------------------ Setup

    suspend fun rematch(deviceId: String, matchId: String) {
        val match = ctx.groupStore.data().match(matchId) ?: return
        val known = ctx.groupStore.data().players().mapTo(HashSet()) { it.id }
        val form = (store.read(deviceId, DraftSlots.Setup) ?: SetupForm()).redraft(DraftAgain.of(match)).keepOnly(known)
        store.write(deviceId, DraftSlots.Setup, form)
        store.delete(deviceId, DraftSlots.Notes)
    }

    /** Applies one setup action; returns where to go next. */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    suspend fun setup(deviceId: String, params: Parameters): String {
        val (op, arg) = params.op()
        store.delete(deviceId, DraftSlots.Notes)
        val form = data.form(deviceId)
        val state = data.setup(deviceId, form)
        var anchor: String? = null
        val next: SetupForm = when (op) {
            "toggle" -> form.togglePlayer(arg).also { anchor = "player-$arg" }

            "clear" -> form.cleared()

            "quick-add" -> {
                val player = quickAdd(params["name"].orEmpty()) ?: return "${DraftFeature.PATH}#quick-add"
                anchor = "player-$player"
                form.withAddedPlayer(player)
            }

            "team-mode" -> enumOrNull<TeamMode>(arg)?.let { form.withTeamMode(it, state.players, env.newSeed()) }
                ?: form

            "move" -> form.movePlayer(arg).also { anchor = "teams" }

            "lock" -> form.toggleLock(arg).also { anchor = "teams" }

            "balance" -> form.balanced(SetupForm.balanceRatings(state.players), env.newSeed()).also { anchor = "teams" }

            "odd-mode" -> enumOrNull<OddSeatMode>(arg)?.let { form.withOddMode(it) }.also { anchor = "seats" } ?: form

            "double-pilot" -> form.withDoublePilot(arg).also { anchor = "seats" }

            "captain" -> {
                val team = enumOrNull<Team>(arg.substringBefore(':'))
                anchor = "captains"
                if (team == null) form else form.withCaptain(team, arg.substringAfter(':'))
            }

            "method" -> form.withMethod(arg).also { anchor = "methods" }

            "pool" -> enumOrNull<PoolSource>(arg)?.let { form.withPoolSource(it) }.also { anchor = "pool" } ?: form

            "custom-pool" -> {
                val known = data.heroes.keys
                anchor = "pool"
                form.withCustomSelection(params.getAll("hero").orEmpty().filterTo(LinkedHashSet()) { it in known })
                    .withPoolSource(PoolSource.CUSTOM)
            }

            "pool-all" -> return form.withCustomSelection(data.heroes.keys).save(deviceId, "${DraftFeature.PATH}/pool")

            "pool-none" -> return form.withCustomSelection(emptySet()).save(deviceId, "${DraftFeature.PATH}/pool")

            "complexity" -> form.withMaxComplexity(arg.toIntOrNull()?.takeIf { it in 1..MAX_STARS }).also {
                anchor =
                    "pool"
            }

            "step-timer" -> form.withStepTimer(arg == "on").also { anchor = "timer" }

            "step-seconds" -> (
                arg.toIntOrNull()?.takeIf { it in STEP_SECONDS }?.let { form.withStepSeconds(it) }
                    ?: form
                )
                .also { anchor = "timer" }

            "flip" -> form.flipped(env.newSeed())

            "team-a" -> enumOrNull<Team>(arg)?.let { form.withTeamA(it) } ?: form

            "next" -> form.next(state.blocker)

            "back" -> form.back() ?: form

            "stage" -> enumOrNull<SetupStage>(arg)?.let { form.goTo(it) } ?: form

            "discard" -> {
                store.delete(deviceId, DraftSlots.Active)
                store.delete(deviceId, DraftSlots.Board)
                form
            }

            "start" -> return start(deviceId)

            else -> form
        }
        return next.save(deviceId, DraftFeature.PATH + (anchor?.let { "#$it" } ?: ""))
    }

    private fun SetupForm.save(deviceId: String, location: String): String {
        store.write(deviceId, DraftSlots.Setup, this)
        return location
    }

    /** "Quick add a player": the group's player of that name, or a new one; null for a blank name. */
    private suspend fun quickAdd(name: String): String? {
        if (name.isBlank()) return null
        val count = ctx.groupStore.data().players(includeArchived = true).size
        val candidate =
            QuickAddPlayer.create(env.newId(), name.take(MAX_NAME), count, env.nowMillis(), QUICK_ADD_COLORS)
                ?: return null
        return try {
            ctx.groupStore.ensurePlayer(candidate).id
        } catch (_: InvalidGroupDataException) {
            null
        }
    }

    private fun start(deviceId: String): String {
        val form = data.form(deviceId)
        val state = data.setup(deviceId, form)
        val result = DraftSetupPresenter.start(data.inputs(deviceId), state, env) ?: run {
            state.blocker?.let { store.write(deviceId, DraftSlots.Notes, DraftNotes(message = it)) }
            return DraftFeature.PATH
        }
        return result.fold(
            onSuccess = { progress ->
                saveProgress(deviceId, progress, DraftBoardWeb())
                // The next game starts from the same table: players, teams and method stay.
                store.write(deviceId, DraftSlots.Setup, state.form.forNextGame())
                DraftFeature.BOARD
            },
            onFailure = { e ->
                store.write(deviceId, DraftSlots.Notes, DraftNotes(message = DraftSetupPresenter.errorMessage(e)))
                DraftFeature.PATH
            },
        )
    }

    // ------------------------------------------------------------------ Board

    /** Saves the draft and anchors the step clock to the step now on the clock. */
    private fun saveProgress(deviceId: String, progress: DraftProgress?, web: DraftBoardWeb) {
        if (progress == null) {
            store.delete(deviceId, DraftSlots.Active)
            store.delete(deviceId, DraftSlots.Board)
            return
        }
        store.write(deviceId, DraftSlots.Active, progress)
        val session = (progress as? DraftProgress.Board)?.session
        val index = session?.currentStepIndex
        val moved = web.stepDraft != progress.id || web.stepIndex != index
        val anchored = if (moved) {
            web.copy(
                stepDraft = progress.id,
                stepIndex = index,
                stepStartedAt = env.nowMillis(),
            )
        } else {
            web
        }
        store.write(deviceId, DraftSlots.Board, anchored)
    }

    /** Applies one board action; returns where to go next. */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    suspend fun board(deviceId: String, params: Parameters): String {
        val (op, arg) = params.op()
        val state = data.board(deviceId)
        val progress = state.progress ?: return DraftFeature.PATH
        val saved = store.read(deviceId, DraftSlots.Board) ?: DraftBoardWeb()
        val previousRedo = saved.local.redo
        // Any action ends the last message (the phone's snackbar) and its Redo.
        var web = saved.copy(local = saved.local.copy(message = null, redo = null))
        var next: DraftProgress? = progress
        var location = DraftFeature.BOARD
        fun say(e: Throwable) {
            web = web.copy(local = web.local.copy(message = DraftBoard.message(e, data.heroes)))
        }
        fun local(transform: (BoardLocal) -> BoardLocal) {
            web = web.copy(local = transform(web.local))
        }
        fun onBoard(transform: (DraftSession) -> Result<DraftSession>) {
            val board = progress as? DraftProgress.Board ?: return
            transform(board.session).fold(onSuccess = { next = DraftProgress.Board(it) }, onFailure = ::say)
        }
        fun onPlayers(transform: (PlayerDraftSession) -> Result<DraftProgress>) {
            val players = progress as? DraftProgress.Players ?: return
            transform(players.session).fold(onSuccess = { next = it }, onFailure = ::say)
        }
        val session = state.board
        when (op) {
            "choose" -> if (session != null) {
                local { DraftBoard.tapHero(session, it, arg) }
                location += "#confirm"
            }

            "cancel" -> local { it.copy(confirm = null, confirmStep = null) }

            "confirm" -> {
                val heroId = web.local.confirm
                val step = web.local.confirmStep
                local { it.copy(confirm = null, confirmStep = null) }
                if (heroId != null && step != null) onBoard { DraftBoard.confirm(it, heroId, step) }
            }

            "swap-out" -> {
                local { it.copy(confirm = null, confirmStep = null) }
                onBoard { DraftBoard.swapOut(it, arg, env.newSeed()) }
            }

            "reroll" -> onBoard { DraftEngine.reroll(it, env.newSeed()) }

            "undo" -> {
                local { it.copy(confirm = null, confirmStep = null, selectedSeat = null) }
                when (progress) {
                    is DraftProgress.Board -> DraftBoard.undo(progress, data.heroes).fold(
                        onSuccess = { outcome ->
                            next = outcome.progress
                            if (outcome.message !=
                                null
                            ) {
                                local { it.copy(message = outcome.message, redo = outcome.redo) }
                            }
                        },
                        onFailure = ::say,
                    )

                    is DraftProgress.Players -> onPlayers { DraftBoard.undoPlayerPick(it) }
                }
            }

            "redo" -> previousRedo?.let { event -> onBoard { DraftBoard.redo(it, event) } }

            "reveal" -> if (session != null && arg.toIntOrNull() == session.currentStepIndex) {
                local { it.copy(privacyAcknowledged = session.currentStepIndex) }
            }

            "unavailable" -> if (session != null) {
                DraftBoard.unavailableMessage(session, arg, data.heroes, state::playerName)?.let { text ->
                    local { it.copy(message = text) }
                }
            }

            "seat" -> if (session != null) {
                when (val tap = DraftBoard.tapSeat(session, web.local.selectedSeat, arg, data.heroes)) {
                    is SeatTap.Refused -> local { it.copy(selectedSeat = null, message = tap.message) }

                    is SeatTap.Select -> local { it.copy(selectedSeat = tap.seatId) }

                    is SeatTap.Swap -> {
                        local { it.copy(selectedSeat = null) }
                        onBoard { s -> DraftEngine.swapSeats(s, tap.first, tap.second) }
                    }
                }
                location += "#seats"
            }

            "handicap" -> {
                onBoard { DraftBoard.toggleHandicap(it, arg) }
                location += "#seats"
            }

            "finish" -> if (session != null) return finish(deviceId, session, arg, web)

            "discard" -> {
                saveProgress(deviceId, null, web)
                return DraftFeature.PATH
            }

            "choose-for" -> {
                local { it.copy(choosingFor = arg.ifEmpty { null }) }
                if (arg.isNotEmpty()) location += "#chooser"
            }

            "assign" -> {
                val playerId = web.local.choosingFor
                local { it.copy(choosingFor = null) }
                if (playerId != null) onPlayers { DraftBoard.assignHero(it, playerId, arg) }
            }

            "clear-hero" -> onPlayers { DraftBoard.clearHero(it, arg) }

            "draft-player" -> onPlayers { DraftBoard.draftPlayer(it, arg) }

            else -> Unit
        }
        saveProgress(deviceId, next, web)
        return location
    }

    private suspend fun finish(deviceId: String, session: DraftSession, then: String, web: DraftBoardWeb): String {
        val matchId = env.newId()
        val finished = DraftBoard.finish(session, matchId, env.nowMillis()).getOrElse { e ->
            store.write(
                deviceId,
                DraftSlots.Board,
                web.copy(local = web.local.copy(message = DraftBoard.message(e, data.heroes))),
            )
            return DraftFeature.BOARD
        }
        try {
            // The phone's order: the match, then the record that points at it.
            ctx.groupStore.upsertMatch(finished.match)
            ctx.groupStore.saveDraft(finished.record)
        } catch (e: InvalidGroupDataException) {
            store.write(deviceId, DraftSlots.Board, web.copy(local = web.local.copy(message = e.message)))
            return DraftFeature.BOARD
        }
        saveProgress(deviceId, null, web)
        return when (then) {
            "timer" -> DraftFeature.timerSetup(matchId)

            "result" -> DraftFeature.result(matchId)

            else -> {
                store.write(deviceId, DraftSlots.Notes, DraftNotes(savedMatchId = matchId))
                DraftFeature.PATH
            }
        }
    }

    // ------------------------------------------------------------------ Methods

    suspend fun methods(deviceId: String, params: Parameters): String {
        val (op, arg) = params.op()
        val library = data.library(deviceId)
        val custom = library.custom
        return when (op) {
            "duplicate" -> {
                val copy = library.duplicate(arg, env.newId()) ?: return DraftFeature.METHODS
                store.write(deviceId, DraftSlots.Methods, custom.withSaved(copy))
                "${DraftFeature.METHODS}/edit/${copy.id}"
            }

            "delete" -> {
                store.write(deviceId, DraftSlots.Methods, custom.filterNot { it.id == arg })
                DraftFeature.METHODS
            }

            else -> DraftFeature.METHODS
        }
    }

    /** The editor for [key] as last edited in this browser, or freshly opened. */
    fun editorState(deviceId: String, key: String, newId: () -> String): MethodEditUiState {
        val saved = store.read(deviceId, DraftSlots.MethodEdit)
        if (saved != null && saved.key == key) return saved.state
        return MethodEditUiState.forEditing(data.library(deviceId), key.takeIf { it != NEW }, newId())
    }

    @Suppress("CyclomaticComplexMethod")
    suspend fun editMethod(deviceId: String, key: String, params: Parameters): String {
        val (op, arg) = params.op()
        val self = "${DraftFeature.METHODS}/edit/$key"
        if (op == "cancel") {
            store.delete(deviceId, DraftSlots.MethodEdit)
            return DraftFeature.METHODS
        }
        // A method opened by a GET keeps the id that page showed (a new id is only made once).
        var form =
            editorState(deviceId, key) { params["id"]?.removePrefix("custom-")?.takeIf(::isToken) ?: env.newId() }
        // The fields ride along with every button, so typing is never lost.
        params["name"]?.let { form = form.withName(it) }
        val kind = enumOrNull<PoolKind>(params["poolKind"].orEmpty())
        form = when {
            kind != null && kind != form.poolKind -> form.withPoolKind(kind)
            else -> params["poolValue"]?.toIntOrNull()?.let { form.withPoolValue(it) } ?: form
        }
        if (params.contains("stepSeconds")) {
            form = form.withStepSeconds(params["stepSeconds"]?.toIntOrNull()?.takeIf { it in STEP_SECONDS })
        }
        val parts = arg.split(':')
        val section = enumOrNull<StepSection>(parts.getOrNull(0).orEmpty())
        val index = parts.getOrNull(1)?.toIntOrNull()
        form = when {
            op == "add" && section != null -> {
                val action = enumOrNull<DraftAction>(parts.getOrNull(1).orEmpty())
                val team = enumOrNull<TeamSlot>(parts.getOrNull(2).orEmpty())
                if (action != null && team != null) form.addStep(section, StepSpec(team, action)) else form
            }

            section == null || index == null -> when (op) {
                "layout" -> PreviewLayout.fromKey(arg)?.let { form.withLayout(it) } ?: form
                else -> form
            }

            op == "remove" -> form.removeStep(section, index)

            op == "team" -> form.toggleTeam(section, index)

            op == "action" -> form.toggleAction(section, index)

            op == "hidden" -> form.toggleHidden(section, index)

            op == "up" -> form.moveStep(section, index, -1)

            op == "down" -> form.moveStep(section, index, 1)

            else -> form
        }
        if (op == "save" && form.error == null) {
            store.write(deviceId, DraftSlots.Methods, data.library(deviceId).custom.withSaved(form.toMethod()))
            store.delete(deviceId, DraftSlots.MethodEdit)
            return DraftFeature.METHODS
        }
        store.write(deviceId, DraftSlots.MethodEdit, MethodEditWeb(key, form))
        return if (op == "save") "$self#error" else self
    }

    companion object {
        const val NEW = "new"
        const val MAX_STARS = 3
        const val MAX_NAME = 40
        val STEP_SECONDS = listOf(15, 30, 45, 60, 90)

        /**
         * Quick-added players' colours: the palette minus Coral and Sky, which read as a team next
         * to Red and Blue (DESIGN.md; the phone's `DraftSetupViewModel` leaves out the same two).
         */
        private val TEAM_LIKE = setOf(2, 3)
        val QUICK_ADD_COLORS: List<Long> = PlayerPalette.swatches.filterIndexed { i, _ ->
            i !in TEAM_LIKE
        }.map { it.first }

        private val TOKEN = Regex("[A-Za-z0-9-]{1,64}")

        fun isToken(value: String) = TOKEN.matches(value)
    }
}

internal inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }
