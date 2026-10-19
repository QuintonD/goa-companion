package dev.quinton.goa.hub.players

import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.presentation.players.HeroOption
import dev.quinton.goa.core.presentation.players.MAX_NAME_LENGTH
import dev.quinton.goa.core.presentation.players.MAX_SEATS_PER_TEAM
import dev.quinton.goa.core.presentation.players.MatchEditor
import dev.quinton.goa.core.presentation.players.MatchForm
import dev.quinton.goa.core.presentation.players.PilotOption
import dev.quinton.goa.core.presentation.players.PlayerLook
import dev.quinton.goa.core.presentation.players.SeatDraft
import dev.quinton.goa.core.presentation.players.formatRating
import dev.quinton.goa.core.presentation.players.label
import dev.quinton.goa.core.presentation.players.resultLabel
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.store.InvalidGroupDataException
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.web.isHtmx
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.PlayerPalette
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.notice
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import dev.quinton.goa.hub.web.ui.teamBadge
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.html.ButtonType
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.SELECT
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.optGroup
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea
import kotlinx.html.ul

/** The editor's state between round trips: the form as typed, plus how to show it. */
internal data class EditorState(
    val form: MatchForm,
    /** "Who won?": winner and condition first, the rest optional below. */
    val resultFirst: Boolean,
    /** Errors show after the first save attempt, as on the phone. */
    val showErrors: Boolean = false,
    /** A new match's id, fixed when the form opens, so a double submit saves one match. */
    val newId: String? = null,
    val notice: String? = null,
    val problem: String? = null,
)

/** Logging, editing and "Who won?" (the phone's MatchEditScreen, rules from `MatchEditor`). */
internal fun Route.matchEditorRoutes(ctx: HubContext) {
    get(PlayersPaths.NEW_MATCH) {
        val q = call.request.queryParameters
        val form = newMatchForm(q, ctx.clock.nowMillis())
        val view = PlayersData.of(ctx)
        val newId = q["id"]?.takeIf { isSafeId(it) && view.data.document.matches.none { m -> m.id == it } }
            ?: UUID.randomUUID().toString()
        call.respondEditor(ctx, view, EditorState(form, resultFirst = q["result"] == "1", newId = newId), null)
    }
    post(PlayersPaths.NEW_MATCH) {
        val params = call.receiveParameters()
        val newId = params["id"]?.takeIf(::isSafeId) ?: UUID.randomUUID().toString()
        call.handleEditorPost(ctx, params, existingId = null, newId = newId, resultFirst = params["result"] == "1")
    }
    get("/players/matches/{id}/edit") {
        val id = call.parameters["id"].orEmpty()
        val view = PlayersData.of(ctx)
        val match = view.match(id) ?: return@get call.respondMatchGone(view, id)
        call.respondEditor(ctx, view, EditorState(formOf(match), resultFirst = false), match)
    }
    post("/players/matches/{id}/edit") {
        call.handleEditorPost(
            ctx,
            call.receiveParameters(),
            existingId = call.parameters["id"].orEmpty(),
            resultFirst = false,
        )
    }
    get("/players/matches/{id}/result") {
        val id = call.parameters["id"].orEmpty()
        val view = PlayersData.of(ctx)
        val q = call.request.queryParameters
        val match = view.match(id)
        val newFromTimer = q["new"] == "1" && isSafeId(id) && !view.wasDeleted(id)
        if (match == null && newFromTimer) {
            // The timer's "Log result" without a pending match: a new match with the timer's id, so a
            // double submit saves one match (ADR-023's result handoff).
            val state = EditorState(newMatchForm(q, ctx.clock.nowMillis()), resultFirst = true, newId = id)
            call.respondEditor(ctx, view, state, null)
            return@get
        }
        if (match == null) return@get call.respondMatchGone(view, id)
        val pending = match.status == MatchStatus.PENDING
        val loaded = formOf(match)
        // A game that just ended: the pending match becomes a result, dated from the session start.
        val prefill = call.request.queryParameters["playedAt"]?.toLongOrNull()?.takeIf { pending }
        val form = loaded.copy(
            status = if (pending) MatchStatus.COMPLETED else loaded.status,
            playedAt = prefill?.let(MatchEditor::toMinute) ?: loaded.playedAt,
        )
        val notice = if (pending) {
            null
        } else {
            "This match already has a result (${resultLabel(match.status, match.winner)}). Saving here changes it."
        }
        call.respondEditor(ctx, view, EditorState(form, resultFirst = true, notice = notice), match)
    }
    post("/players/matches/{id}/result") {
        call.handleEditorPost(
            ctx,
            call.receiveParameters(),
            existingId = call.parameters["id"].orEmpty(),
            resultFirst = true,
        )
    }
}

/**
 * A new match, pre-filled from a link (the timer's "Log result": `playedAt` epoch ms, `durationMin`
 * or `duration` minutes, `mode` quick | long | double_lane); bad values are ignored, as on the phone.
 */
private fun newMatchForm(q: Parameters, nowMillis: Long): MatchForm {
    val now = MatchEditor.toMinute(nowMillis)
    return MatchForm(
        playedAt = q["playedAt"]?.toLongOrNull()?.let(MatchEditor::toMinute)?.coerceAtMost(now) ?: now,
        mode = MatchEditor.gameModeOrNull(q["mode"]) ?: GameMode.QUICK,
        duration = MatchForm.durationText((q["durationMin"] ?: q["duration"])?.toIntOrNull()),
        red = List(MatchEditor.NEW_SEATS_PER_TEAM) { SeatDraft(it.toLong()) },
        blue = List(MatchEditor.NEW_SEATS_PER_TEAM) { SeatDraft((it + MAX_SEATS_PER_TEAM).toLong()) },
    )
}

private fun formOf(match: Match): MatchForm {
    var key = 0L
    return MatchForm.from(match) { key++ }
}

/** Ids we accept from a form or a link for a new match (UUIDs and the like). */
private fun isSafeId(id: String) = id.length in 8..64 && id.all { it.isLetterOrDigit() || it == '-' }

private suspend fun ApplicationCall.handleEditorPost(
    ctx: HubContext,
    params: Parameters,
    existingId: String?,
    newId: String? = null,
    resultFirst: Boolean,
) {
    val view = PlayersData.of(ctx)
    val original = existingId?.let { view.match(it) }
    if (existingId != null && original == null) {
        // Deleted on another phone or browser while this form was open: say so, store nothing.
        respondDeletedMeanwhile(view, existingId)
        return
    }
    val now = ctx.clock.nowMillis()
    val op = params["op"].orEmpty()
    var form = EditorForms.edit(EditorForms.parse(params, ctx.clock.zone, now, original), op)
    var notice: String? = null
    if (op == "add-player") {
        val name = PlayerLook.nameInput(params["newPlayer"].orEmpty()).trim()
        if (name.isNotEmpty()) {
            val added = addPilot(ctx, view, name)
            form = EditorForms.seatFirstFree(form, added.id)
            val known = view.byId[added.id]?.takeIf { !it.archived } != null
            notice = if (known) "${added.name} is already in your group." else "Added ${added.name}."
        }
    }
    val state = EditorState(
        form = form,
        resultFirst = resultFirst,
        showErrors = params["showErrors"] == "1",
        newId = newId,
        notice = notice,
    )
    if (op != "save") {
        respondEditor(ctx, PlayersData.of(ctx), state, original)
        return
    }
    val fresh = PlayersData.of(ctx)
    val everyone = fresh.data.document.players.associateBy { it.id } + fresh.byId
    if (MatchEditor.errors(form, fresh.catalog, everyone, now).isNotEmpty()) {
        respondEditor(ctx, fresh, state.copy(showErrors = true), original, refusedStatus())
        return
    }
    val id = existingId ?: newId ?: UUID.randomUUID().toString()
    val previous = original ?: fresh.match(id)
    val match = form.toMatch(id, previous?.createdAt ?: now, previous?.draftId)
        .copy(tieBreaker = previous?.tieBreaker) // Not edited here; keep the coin from setup.
    try {
        ctx.groupStore.upsertMatch(match)
    } catch (e: InvalidGroupDataException) {
        respondEditor(ctx, fresh, state.copy(problem = e.message), original, refusedStatus())
        return
    }
    val target = if (MatchEditor.isNewResult(match, original, resultFirst)) {
        PlayersPaths.saved(id)
    } else {
        PlayersPaths.logAt(id)
    }
    if (isHtmx) {
        response.header("HX-Redirect", target)
        respondText("")
    } else {
        respondRedirect(target)
    }
}

/** A refused save re-renders the form: 422, except for htmx, which swaps only 2xx answers. */
private fun ApplicationCall.refusedStatus() = if (isHtmx) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity

/** The pilot picker's quick add: a known name (archived included) picks that player, as on the phone. */
private suspend fun addPilot(ctx: HubContext, view: PlayersData, name: String): Player = ctx.groupStore.ensurePlayer(
    Player(
        id = UUID.randomUUID().toString(),
        name = name,
        color = PlayerLook.newColor(view.players, PlayerPalette.swatches.map { it.first }),
        avatarId = PlayerLook.newCrest(view.players, PlayerPalette.CRESTS),
        createdAt = ctx.clock.nowMillis(),
    ),
)

private fun teamOf(name: String?): Team? = Team.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

private suspend fun ApplicationCall.respondDeletedMeanwhile(view: PlayersData, id: String) {
    respondPage(playersPage("Match", back = PlayersPaths.LOG), HttpStatusCode.Conflict) {
        div("error-state") {
            attributes["role"] = "alert"
            p {
                +(
                    if (view.wasDeleted(id)) {
                        "This match was deleted on another phone or browser while you were editing it. Your changes weren't saved."
                    } else {
                        "This match doesn't exist any more. Your changes weren't saved."
                    }
                    )
            }
        }
        a(PlayersPaths.LOG, classes = "btn btn-tonal") { +"Match log" }
    }
}

/** Reading the posted editor and turning it back into fields. */
internal object EditorForms {
    /** Seats read per team: more than the editor adds, so a stored match with more (an import) keeps them all. */
    private const val MAX_POSTED_SEATS = 20

    private val LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    /**
     * The posted editor as a [MatchForm]. What the person didn't touch keeps the stored value, as
     * the phone's editor does: the exact `playedAt` (the field shows minutes), a map or notes longer
     * than the fields allow (from a backup or an older app), every seat of [original]. Picking
     * another winner without touching the status completes the match ([MatchForm.withWinner]).
     */
    fun parse(params: Parameters, zone: java.time.ZoneId, now: Long, original: Match? = null): MatchForm {
        fun seats(team: Team): List<SeatDraft> {
            val prefix = team.name.lowercase()
            return (0 until MAX_POSTED_SEATS).mapNotNull { i ->
                if (params["$prefix-$i"] == null) return@mapNotNull null
                SeatDraft(
                    key = (team.ordinal * MAX_POSTED_SEATS + i).toLong(),
                    playerId = params["$prefix-$i-pilot"]?.takeIf { it.isNotEmpty() },
                    heroId = params["$prefix-$i-hero"]?.takeIf { it.isNotEmpty() },
                    handicap = params["$prefix-$i-handicap"] == "true",
                )
            }
        }
        val shownAt = params["playedAtMs"]?.toLongOrNull()
        val fallback = shownAt ?: MatchEditor.toMinute(now)
        val text = params["playedAt"]
        val picked = text?.takeUnless { shownAt != null && it == local(shownAt, zone) }?.let {
            try {
                LocalDateTime.parse(it, LOCAL).atZone(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
        val map = params["map"].orEmpty()
        val notes = params["notes"].orEmpty().replace("\r\n", "\n")
        val status = MatchStatus.entries.firstOrNull { it.name == params["status"] } ?: MatchStatus.COMPLETED
        val winner = Team.entries.firstOrNull { it.name == params["winner"] }
        val form = MatchForm(
            // A time later than now is taken as "just now", as the phone's pickers do.
            playedAt = picked?.let { minOf(it, now) } ?: fallback,
            mode = GameMode.entries.firstOrNull { it.name == params["mode"] } ?: GameMode.QUICK,
            map = if (original != null && map == original.map.orEmpty()) map else map.take(MatchEditor.MAX_TEXT),
            status = status,
            winner = winner,
            winCondition = WinCondition.entries.firstOrNull { it.name == params["condition"] },
            duration = MatchEditor.durationInput(params["duration"].orEmpty()),
            notes = if (original != null && notes == original.notes) notes else notes.take(MatchEditor.MAX_NOTES),
            red = seats(Team.RED),
            blue = seats(Team.BLUE),
        )
        val winnerPicked = winner != null && winner.name != params["shownWinner"] &&
            status.name == params["shownStatus"]
        return if (winnerPicked) form.withWinner(winner) else form
    }

    /** The seat buttons: "add-red", "remove-blue-1", "swap" (the phone's addSeat, removeSeat, swapTeams). */
    fun edit(form: MatchForm, op: String): MatchForm {
        val parts = op.split('-')
        val team = teamOf(parts.getOrNull(1))
        return when {
            op == "swap" -> form.copy(red = form.blue, blue = form.red, winner = form.winner?.opponent)

            parts.size == 2 && parts[0] == "add" && team != null && form.seats(team).size < MAX_SEATS_PER_TEAM ->
                form.withSeats(team, form.seats(team) + SeatDraft(nextKey(form)))

            parts.size == 3 && parts[0] == "remove" && team != null -> {
                val index = parts[2].toIntOrNull() ?: -1
                form.withSeats(team, form.seats(team).filterIndexed { at, _ -> at != index })
            }

            else -> form
        }
    }

    fun nextKey(form: MatchForm): Long = (form.red + form.blue).maxOfOrNull { it.key }?.plus(1) ?: 0L

    /** Seats a just-added pilot in the first seat without one (Red first), as the picker seats them. */
    fun seatFirstFree(form: MatchForm, playerId: String): MatchForm {
        for (team in Team.entries) {
            val seats = form.seats(team)
            val free = seats.indexOfFirst { it.playerId == null }
            if (free >=
                0
            ) {
                return form.withSeats(
                    team,
                    seats.mapIndexed { i, s ->
                        if (i ==
                            free
                        ) {
                            s.copy(playerId = playerId)
                        } else {
                            s
                        }
                    },
                )
            }
        }
        return form
    }

    fun local(epochMillis: Long, zone: java.time.ZoneId): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).format(LOCAL)
}

private suspend fun ApplicationCall.respondEditor(
    ctx: HubContext,
    view: PlayersData,
    state: EditorState,
    original: Match?,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val isNew = original == null
    val form = state.form
    val title = when {
        state.resultFirst -> "Who won?"
        isNew -> "Log match"
        else -> "Edit match"
    }
    val action = when {
        isNew -> PlayersPaths.NEW_MATCH
        state.resultFirst -> PlayersPaths.result(original!!.id)
        else -> PlayersPaths.editMatch(original!!.id)
    }
    val back = original?.let { PlayersPaths.match(it.id) } ?: PlayersPaths.LOG
    val everyone = view.data.document.players.associateBy { it.id } + view.byId
    val errors = MatchEditor.errors(form, view.catalog, everyone, ctx.clock.nowMillis())
    val owned = ctx.deviceStore.read(ctx.device(this).id, SharedSlots.Settings)?.ownedProducts.orEmpty()
    val editor = EditorView(
        state = state,
        errors = if (state.showErrors) errors.map { it.message } else emptyList(),
        pilots = MatchEditor.pilotOptions(view.players, form, view.snapshot.ratings),
        heroes = MatchEditor.heroOptions(view.catalog),
        owned = owned,
        playersById = everyone,
        fromDraft = original?.draftId != null,
        isNew = isNew,
        zone = ctx.clock.zone,
        now = ctx.clock.nowMillis(),
    )
    respondPage(playersPage(title, back = back), status) {
        form(action = action, method = FormMethod.post, classes = "pl-editor") {
            id = "match-editor"
            attributes["hx-post"] = action
            attributes["hx-target"] = "#match-editor"
            attributes["hx-select"] = "#match-editor"
            attributes["hx-swap"] = "outerHTML"
            attributes["hx-trigger"] =
                "submit, change from:select, change from:.pl-handicap-input"
            // A finished game's result not saved anywhere yet (the timer's "Log result"): leaving asks first.
            if (state.resultFirst && isNew) attributes["data-unsaved"] = "true"
            editor.render(this, action)
        }
        if (!isNew) {
            val discard = original?.status == MatchStatus.PENDING
            a(PlayersPaths.delete(original!!.id), classes = "btn btn-text pl-danger-text") {
                icon(Icons.DELETE)
                span { +(if (discard) "Discard pending match" else "Delete match") }
            }
        }
        p("pl-hint") { +"Ratings recompute from the whole match log whenever a match is saved, edited or deleted." }
    }
}

/** Renders the editor form (both layouts). */
private class EditorView(
    val state: EditorState,
    val errors: List<String>,
    val pilots: List<PilotOption>,
    val heroes: List<HeroOption>,
    val owned: Set<String>,
    val playersById: Map<String, Player>,
    val fromDraft: Boolean,
    val isNew: Boolean,
    val zone: java.time.ZoneId,
    val now: Long,
) {
    private val edited = state.form

    fun render(out: FORM, action: String) = with(out) {
        hidden("playedAtMs", edited.playedAt.toString())
        // What the result showed, so picking a winner can complete the match ([MatchForm.withWinner]).
        hidden("shownWinner", edited.winner?.name.orEmpty())
        hidden("shownStatus", edited.status.name)
        if (state.showErrors || errors.isNotEmpty()) hidden("showErrors", "1")
        state.newId?.let { hidden("id", it) }
        if (state.resultFirst && isNew) hidden("result", "1")
        // Enter in a text field saves (the first submit button decides).
        button(type = ButtonType.submit, classes = "visually-hidden") {
            name = "op"
            value = "save"
            attributes["tabindex"] = "-1"
            attributes["aria-hidden"] = "true"
            +"Save"
        }
        state.problem?.let { errorBox(listOf(it)) }
        if (errors.isNotEmpty()) errorBox(errors)
        state.notice?.let { notice(it) }
        if (state.resultFirst) {
            winnerAndCondition(header = false)
            p("pl-hint") { +"Everything below is optional: check the seats, then save." }
            teams(action)
            whenSection()
            details()
            sectionHeader("Match status")
            statusChoice()
        } else {
            if (fromDraft && edited.status == MatchStatus.PENDING) {
                p("pl-hint") { +"Created from a draft. Fill in the result when the game ends." }
            }
            whenSection()
            teams(action)
            div("pl-result-block") {
                sectionHeader("Result")
                statusChoice()
                div("pl-winner-block") { winnerAndCondition(header = true) }
            }
            details()
        }
        button(type = ButtonType.submit, classes = "btn btn-primary pl-wide") {
            name = "op"
            value = "save"
            +when {
                state.resultFirst && edited.status == MatchStatus.COMPLETED -> "Save result"
                isNew -> "Save match"
                else -> "Save changes"
            }
        }
    }

    private fun FlowContent.errorBox(messages: List<String>) {
        div("error-state pl-errors") {
            id = "errors"
            attributes["role"] = "alert"
            attributes["tabindex"] = "-1"
            div {
                p("pl-errors-title") { +"Can't save yet" }
                ul { messages.forEach { li { +it } } }
            }
        }
    }

    @Suppress("LongParameterList")
    private fun FlowContent.opButton(
        label: String,
        op: String,
        action: String,
        anchor: String,
        css: String = "btn btn-text",
        spoken: String? = null,
    ) {
        button(type = ButtonType.submit, classes = css) {
            name = "op"
            value = op
            attributes["formaction"] = "$action#$anchor"
            attributes["formnovalidate"] = ""
            spoken?.let { attributes["aria-label"] = it }
            +label
        }
    }

    private fun FlowContent.whenSection() {
        sectionHeader("When and where")
        div("field") {
            label {
                htmlFor = "f-playedAt"
                +"Date and time"
            }
            input(type = InputType.dateTimeLocal, name = "playedAt") {
                id = "f-playedAt"
                value = EditorForms.local(edited.playedAt, zone)
                // Matches are logged after they're played: nothing later than now.
                max = EditorForms.local(now, zone)
            }
        }
        segmented("mode", "Mode", GameMode.entries.map { it.name to it.label() }, edited.mode.name)
        div("field") {
            label {
                htmlFor = "f-map"
                +"Map or table (optional)"
            }
            input(type = InputType.text, name = "map") {
                attributes["autocomplete"] = "off"
                attributes["enterkeyhint"] = "done"
                id = "f-map"
                value = edited.map
                maxLength = MatchEditor.MAX_TEXT.toString()
                attributes["autocapitalize"] = "sentences"
            }
        }
    }

    private fun FlowContent.statusChoice() {
        segmented(
            "status",
            "Match status",
            listOf(MatchStatus.COMPLETED, MatchStatus.PENDING, MatchStatus.ABANDONED).map { it.name to it.label() },
            edited.status.name,
        )
    }

    private fun FlowContent.segmented(
        name: String,
        legend: String,
        options: List<Pair<String, String>>,
        selected: String,
    ) {
        fieldSet("pl-segmented") {
            legend("visually-hidden") { +legend }
            options.forEach { (value, text) ->
                label("pl-segment") {
                    input(type = InputType.radio, name = name) {
                        this.value = value
                        checked = value == selected
                    }
                    span { +text }
                }
            }
        }
    }

    private fun FlowContent.winnerAndCondition(header: Boolean) {
        fieldSet("pl-winners") {
            legend(if (header) "pl-legend" else "pl-legend pl-legend-big") {
                +(if (header) "Winner" else "Which team won?")
            }
            Team.entries.forEach { team ->
                val pilots = MatchEditor.pilotNames(edited, team, playersById)
                label("pl-winner-option ${if (team == Team.RED) "pl-red" else "pl-blue"}") {
                    input(type = InputType.radio, name = "winner") {
                        value = team.name
                        checked = edited.winner == team
                    }
                    span("pl-winner-label") {
                        teamBadge(team)
                        span { +" won" }
                    }
                    if (pilots.isNotEmpty()) span("pl-winner-pilots") { +pilots.joinToString(" · ") }
                }
            }
        }
        fieldSet("pl-conditions") {
            legend(if (header) "pl-legend" else "pl-legend pl-legend-big") {
                +(if (header) "Win condition" else "How did they win?")
            }
            div("chip-row") {
                (WinCondition.entries.map { it.name to it.label() } + ("" to "Not recorded")).forEach { (value, text) ->
                    label("pl-chip") {
                        input(type = InputType.radio, name = "condition") {
                            this.value = value
                            checked = (edited.winCondition?.name ?: "") == value
                        }
                        span { +text }
                    }
                }
            }
        }
    }

    private fun FlowContent.details() {
        div("field") {
            label {
                htmlFor = "f-duration"
                +"Duration (minutes, optional)"
            }
            input(type = InputType.text, name = "duration") {
                attributes["enterkeyhint"] = "done"
                id = "f-duration"
                value = edited.duration
                attributes["inputmode"] = "numeric"
                attributes["pattern"] = "[0-9]*"
                maxLength = "3"
            }
        }
        div("field") {
            label {
                htmlFor = "f-notes"
                +"Notes"
            }
            textArea(rows = "2") {
                id = "f-notes"
                name = "notes"
                maxLength = MatchEditor.MAX_NOTES.toString()
                attributes["autocapitalize"] = "sentences"
                +edited.notes
            }
        }
    }

    private fun FlowContent.teams(action: String) {
        div("pl-section-head") {
            id = "teams"
            sectionHeader("Teams")
            opButton("Swap", "swap", action, "teams", spoken = "Swap teams")
        }
        Team.entries.forEach { team -> teamEditor(team, action) }
        div("pl-add-player") {
            div("field") {
                label {
                    htmlFor = "f-newPlayer"
                    +"Someone missing? Add them"
                }
                input(type = InputType.text, name = "newPlayer") {
                    attributes["enterkeyhint"] = "done"
                    id = "f-newPlayer"
                    maxLength = MAX_NAME_LENGTH.toString()
                    placeholder = if (pilots.isEmpty()) "Name of the first player" else "Name"
                    attributes["autocapitalize"] = "words"
                    attributes["autocomplete"] = "off"
                }
            }
            opButton(
                "Add",
                "add-player",
                action,
                "teams",
                css = "btn btn-tonal",
                spoken = "Add this player and seat them",
            )
        }
    }

    private fun FlowContent.teamEditor(team: Team, action: String) {
        val prefix = team.name.lowercase()
        val seats = edited.seats(team)
        val doublePilots = edited.doublePilots(team)
        div("pl-team-editor") {
            div("pl-team-editor-head") {
                teamBadge(team)
                span("pl-seat-count") { +"· ${seats.size} ${if (seats.size == 1) "seat" else "seats"}" }
                if (seats.size < MAX_SEATS_PER_TEAM) {
                    opButton("Add seat", "add-$prefix", action, "teams", spoken = "Add seat to ${team.label()}")
                }
            }
            if (seats.isEmpty()) p("pl-problem") { +"No seats. Add at least one hero for ${team.label()}." }
            seats.forEachIndexed { i, seat ->
                val problem = MatchEditor.seatProblem(edited, team, seat, state.showErrors)
                val heroLabel = seat.heroId?.let { id -> heroes.firstOrNull { it.id == id }?.name } ?: "Unknown hero"
                fieldSet(
                    if (problem !=
                        null
                    ) {
                        "pl-seat-editor pl-seat-$prefix invalid"
                    } else {
                        "pl-seat-editor pl-seat-$prefix"
                    },
                ) {
                    legend("visually-hidden") { +"${team.label()} seat ${i + 1}" }
                    hidden("$prefix-$i", "1")
                    div("pl-seat-fields") {
                        div("field") {
                            label {
                                htmlFor = "f-$prefix-$i-hero"
                                +"Hero"
                            }
                            select {
                                id = "f-$prefix-$i-hero"
                                name = "$prefix-$i-hero"
                                heroOptions(seat)
                            }
                        }
                        div("field") {
                            label {
                                htmlFor = "f-$prefix-$i-pilot"
                                +"Pilot"
                            }
                            select {
                                id = "f-$prefix-$i-pilot"
                                name = "$prefix-$i-pilot"
                                pilotOptions(team, seat)
                            }
                        }
                    }
                    div("pl-seat-flags") {
                        label("check pl-handicap") {
                            input(
                                type = InputType.checkBox,
                                name = "$prefix-$i-handicap",
                                classes = "pl-handicap-input",
                            ) {
                                value = "true"
                                checked = seat.handicap
                            }
                            span { +"Handicap" }
                        }
                        if (seat.playerId != null && seat.playerId in doublePilots) pill("Double pilot", "pl-pill-teal")
                        opButton(
                            "Remove",
                            "remove-$prefix-$i",
                            action,
                            "teams",
                            css = "btn btn-text pl-remove",
                            spoken = "Remove seat: $heroLabel",
                        )
                    }
                    problem?.let { p("pl-problem") { +it } }
                }
            }
        }
    }

    private fun SELECT.heroOptions(seat: SeatDraft) {
        val taken = edited.heroesInUse().toSet() - setOfNotNull(seat.heroId)
        option {
            value = ""
            selected = seat.heroId == null
            +"Unknown hero"
        }
        fun group(list: List<HeroOption>) = list.forEach { hero ->
            option {
                value = hero.id
                selected = hero.id == seat.heroId
                +(hero.name + (if (hero.id in taken) " — in this match" else ""))
            }
        }
        // A hero this catalog doesn't know (an old backup, a newer phone) stays selectable as itself.
        seat.heroId?.takeIf { id -> heroes.none { it.id == id } }?.let { unknown ->
            option {
                value = unknown
                selected = true
                +"Unknown hero ($unknown)"
            }
        }
        if (owned.isEmpty()) {
            group(heroes)
        } else {
            val (mine, others) = heroes.partition { it.product in owned }
            if (mine.isNotEmpty()) optGroup("Your collection") { group(mine) }
            if (others.isNotEmpty()) optGroup("Other heroes") { group(others) }
        }
    }

    private fun SELECT.pilotOptions(team: Team, seat: SeatDraft) {
        val sameTeam = edited.seats(team).filter { it.key != seat.key }.mapNotNull { it.playerId }.toSet()
        val otherTeam = edited.seats(team.opponent).mapNotNull { it.playerId }.toSet()
        option {
            value = ""
            selected = seat.playerId == null
            +"Choose pilot"
        }
        pilots.forEach { pilot ->
            val id = pilot.player.id
            option {
                value = id
                selected = id == seat.playerId
                +buildString {
                    append(pilot.player.name)
                    append(if (pilot.rating.games > 0) " · Elo ${formatRating(pilot.rating.value)}" else " · Unrated")
                    when {
                        id in otherTeam -> append(" — on ${team.opponent.label()}")
                        id in sameTeam -> append(" — double pilot")
                    }
                }
            }
        }
        // A seated player missing from the list (merged-away or deleted elsewhere) stays as is.
        seat.playerId?.takeIf { id -> pilots.none { it.player.id == id } }?.let { missing ->
            option {
                value = missing
                selected = true
                +(playersById[missing]?.name ?: "Unknown player")
            }
        }
    }
}
