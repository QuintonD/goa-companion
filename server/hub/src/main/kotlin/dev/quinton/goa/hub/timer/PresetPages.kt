package dev.quinton.goa.hub.timer

import dev.quinton.goa.core.domain.setup.EndOfRoundStep
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.PhaseSpec
import dev.quinton.goa.core.domain.timer.PresetIssue
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerPresetValidator
import dev.quinton.goa.core.presentation.timer.PresetEdits
import dev.quinton.goa.core.presentation.timer.TimerPresets
import dev.quinton.goa.core.presentation.timer.TimerText
import dev.quinton.goa.core.presentation.timer.TimerUiMapper
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.checkboxField
import dev.quinton.goa.hub.web.ui.errorState
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HtmlBlockTag
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span

/**
 * Timer presets on the web (the phone's Presets and preset editor): select, duplicate, delete,
 * restore the bundled ones, and edit. The editor is one form: every change (add, move, remove a
 * timer) posts the whole draft back and the page re-renders it, so nothing is stored until Save,
 * which runs the same [TimerPresetValidator] as the phone.
 */
internal object PresetPages {
    fun Route.routes(timer: WebTimer) {
        get(TimerRoutes.PRESETS) {
            val view = timer.presets(call.device.id)
            call.respondPage(
                TimerFeature.page.copy(title = "Timer presets", back = TimerRoutes.HOME, actions = emptyList()),
            ) {
                list(this, view)
            }
        }
        post("${TimerRoutes.PRESETS}/{op}") {
            val id = call.device.id
            val presetId = call.receiveParameters()["preset"].orEmpty()
            when (call.parameters["op"]) {
                "duplicate" -> timer.updatePresets(id) { stored, list ->
                    val source = list.firstOrNull { it.id == presetId } ?: return@updatePresets stored
                    stored.copy(
                        presets = TimerPresets.save(list, TimerPresets.duplicate(source, UUID.randomUUID().toString())),
                    )
                }

                "delete" -> timer.updatePresets(id) { stored, list ->
                    TimerPresets.delete(list, presetId)?.let { stored.copy(presets = it) } ?: stored
                }

                "restore" -> timer.updatePresets(id) { stored, list ->
                    stored.copy(presets = TimerPresets.restoreDefaults(list, timer.data.presets))
                }

                "select" -> timer.updatePresets(id) { stored, list ->
                    if (list.any { it.id == presetId }) stored.copy(selectedPresetId = presetId) else stored
                }

                else -> return@post call.respondRedirect(TimerRoutes.PRESETS)
            }
            call.respondRedirect(TimerRoutes.PRESETS)
        }
        get(TimerRoutes.PRESET_EDIT) {
            val view = timer.presets(call.device.id)
            val existing = call.request.queryParameters["id"]?.let { id -> view.list.firstOrNull { it.id == id } }
            val preset = existing ?: PresetEdits.newPreset(view.selected, UUID.randomUUID().toString())
            val endSteps = timer.data.rules.endOfRound
            call.respondPage(editorPage(isNew = existing == null)) {
                editor(this, preset, isNew = existing == null, endSteps, issues = emptyList(), showIssues = false)
            }
        }
        post(TimerRoutes.PRESET_EDIT) {
            val params = call.receiveParameters()
            val endSteps = timer.data.rules.endOfRound
            val isNew = params["new"] == "1"
            val draft = readDraft(params) ?: return@post call.respondRedirect(TimerRoutes.PRESETS)
            val op = params["op"].orEmpty()
            val edited = applyOp(draft, op)
            val issues = TimerPresetValidator.validate(edited, endSteps.map { it.id }.toSet())
            if (op == "save") {
                if (issues.isEmpty()) {
                    val saved = edited.copy(name = edited.name.trim())
                    timer.updatePresets(call.device.id) { stored, list ->
                        stored.copy(presets = TimerPresets.save(list, saved))
                    }
                    return@post call.respondRedirect(TimerRoutes.PRESETS)
                }
                return@post call.respondPage(editorPage(isNew), HttpStatusCode.UnprocessableEntity) {
                    editor(this, edited, isNew, endSteps, issues, showIssues = true)
                }
            }
            call.respondPage(editorPage(isNew)) { editor(this, edited, isNew, endSteps, issues, showIssues = false) }
        }
    }

    private fun editorPage(isNew: Boolean) =
        TimerFeature.page.copy(
            title = if (isNew) "New preset" else "Edit preset",
            back = TimerRoutes.PRESETS,
            actions = emptyList(),
        )

    // ---- list ----------------------------------------------------------------------------------

    private fun list(out: FlowContent, view: PresetsView) = with(out) {
        view.list.forEach { preset ->
            val option = TimerUiMapper.presetOption(preset)
            val selected = preset.id == view.selectedId
            card(if (selected) "tr-preset-card is-selected" else "tr-preset-card") {
                div("tr-preset-head") {
                    div {
                        h3 { +option.name }
                        p("tr-muted") { +option.summary }
                    }
                    if (selected) span("tr-badge") { +"Selected" }
                }
                div("actions") {
                    if (!selected) opButton("select", preset.id, "Use", ButtonStyle.TONAL)
                    a("${TimerRoutes.PRESET_EDIT}?id=${preset.id}", classes = ButtonStyle.OUTLINED.css) {
                        icon(Icons.EDIT)
                        span { +"Edit" }
                    }
                    opButton("duplicate", preset.id, "Duplicate", ButtonStyle.TEXT)
                    if (view.list.size > 1) opButton("delete", preset.id, "Delete", ButtonStyle.TEXT)
                }
            }
        }
        div("actions") {
            a(TimerRoutes.PRESET_EDIT, classes = ButtonStyle.PRIMARY.css) {
                icon(Icons.ADD)
                span { +"New preset" }
            }
            opButton("restore", "", "Restore defaults", ButtonStyle.TEXT)
        }
    }

    private fun FlowContent.opButton(op: String, presetId: String, label: String, style: ButtonStyle) {
        postForm("${TimerRoutes.PRESETS}/$op", classes = "tr-inline-form") {
            input(type = InputType.hidden, name = "preset") { value = presetId }
            button(type = ButtonType.submit, classes = style.css) {
                if (op == "delete") attributes["data-confirm"] = "Delete this preset?"
                +label
            }
        }
    }

    // ---- editor --------------------------------------------------------------------------------

    @Suppress("LongParameterList")
    private fun editor(
        out: FlowContent,
        preset: TimerPreset,
        isNew: Boolean,
        endSteps: List<EndOfRoundStep>,
        issues: List<PresetIssue>,
        showIssues: Boolean,
    ) = with(out) {
        form(action = TimerRoutes.PRESET_EDIT, method = FormMethod.post, classes = "stack tr-editor") {
            hiddenField("id", preset.id)
            if (isNew) hiddenField("new", "1")
            hiddenField("revision", preset.revision.toString())
            hiddenField("turns", preset.turnsPerRound.toString())
            hiddenField("volumeKeys", preset.volumeKeys.toString())
            if (showIssues) errorState("Fix the highlighted items to save.")
            val nameError = showIssues && issues.any { it.kind == PresetIssue.Kind.BLANK_NAME }
            div(if (nameError) "field invalid" else "field") {
                label {
                    htmlFor = "f-name"
                    +"Preset name"
                }
                input(type = InputType.text, name = "name") {
                    id = "f-name"
                    value = preset.name
                    placeholder = "e.g. Table rules, fast"
                    if (nameError) attributes["aria-invalid"] = "true"
                }
                if (nameError) p("field-error") { +"Give the preset a name" }
            }
            checkboxField(
                "autoAdvance",
                "Auto-advance: move on when a timed phase hits zero (off: overtime counts up)",
                preset.autoAdvance,
                switch = true,
            )
            scopeSection(
                preset,
                PhaseScope.ROUND_START,
                "Round start",
                "Once per round, before turn 1.",
                endSteps,
                issues,
                showIssues,
            )
            scopeSection(
                preset,
                PhaseScope.TURN,
                "Every turn",
                "Each of the ${preset.turnsPerRound} turns: play a card, then resolve after the reveal. " +
                    "The Action timer repeats for every hero and replaces Resolve when on.",
                endSteps,
                issues,
                showIssues,
            )
            scopeSection(
                preset,
                PhaseScope.ROUND_END,
                "End of round",
                "Runs on a checklist step (the Upgrade timer on Level Up) or after the checklist.",
                endSteps,
                issues,
                showIssues,
            )
            tableRules(preset, issues, showIssues)
            div("actions tr-editor-actions") {
                button(type = ButtonType.submit, classes = ButtonStyle.PRIMARY.css) {
                    attributes["name"] = "op"
                    attributes["value"] = "save"
                    +"Save"
                }
                a(TimerRoutes.PRESETS, classes = ButtonStyle.TEXT.css) { +"Cancel" }
            }
        }
    }

    @Suppress("LongParameterList")
    private fun HtmlBlockTag.scopeSection(
        preset: TimerPreset,
        scope: PhaseScope,
        title: String,
        hint: String,
        endSteps: List<EndOfRoundStep>,
        issues: List<PresetIssue>,
        showIssues: Boolean,
    ) {
        sectionHeader(title)
        p("tr-muted") { +hint }
        val phases = preset.phases.filter { it.scope == scope }
        phases.forEachIndexed { i, phase ->
            phaseCard(
                phase,
                canUp = i > 0,
                canDown = i < phases.lastIndex,
                endSteps,
                issues.filter {
                    it.phaseId ==
                        phase.id
                },
                showIssues,
            )
        }
        button(type = ButtonType.submit, classes = ButtonStyle.OUTLINED.css) {
            attributes["name"] = "op"
            attributes["value"] = "add:${scope.name}"
            icon(Icons.ADD)
            span { +"Add ${title.lowercase()} timer" }
        }
    }

    @Suppress("LongParameterList", "LongMethod")
    private fun HtmlBlockTag.phaseCard(
        phase: PhaseSpec,
        canUp: Boolean,
        canDown: Boolean,
        endSteps: List<EndOfRoundStep>,
        issues: List<PresetIssue>,
        showIssues: Boolean,
    ) {
        val p = "p.${phase.id}"
        hiddenField("phase", phase.id)
        hiddenField("$p.kind", phase.kind.name)
        hiddenField("$p.scope", phase.scope.name)
        hiddenField("$p.was", phase.seconds?.toString().orEmpty())
        fieldSet(if (showIssues && issues.isNotEmpty()) "card tr-phase invalid" else "card tr-phase") {
            legend("visually-hidden") { +phase.name }
            div("tr-phase-head") {
                label("check switch") {
                    input(type = InputType.checkBox, name = "$p.enabled") {
                        value = "true"
                        checked = phase.enabled
                        attributes["role"] = "switch"
                    }
                    span { +"On" }
                }
                span("tr-phase-kind") { +TimerText.kindLabel(phase.kind) }
                div("tr-phase-tools") {
                    phaseOp("move:${phase.id}:-1", "Move ${phase.name} up", "↑", canUp)
                    phaseOp("move:${phase.id}:1", "Move ${phase.name} down", "↓", canDown)
                    if (phase.kind == PhaseKind.CUSTOM) phaseOp("remove:${phase.id}", "Remove ${phase.name}", "✕", true)
                }
            }
            div("field") {
                label {
                    htmlFor = "f-$p-name"
                    +"Name"
                }
                input(type = InputType.text, name = "$p.name") {
                    id = "f-$p-name"
                    value = phase.name
                }
            }
            val seconds = phase.seconds
            div("tr-duration") {
                label("check") {
                    input(type = InputType.checkBox, name = "$p.untimed") {
                        value = "true"
                        checked = seconds == null
                    }
                    span { +"Untimed (counts up, never alerts)" }
                }
                div("tr-duration-fields") {
                    numberField("$p.min", "Minutes", (seconds ?: DEFAULT_SECONDS) / SECONDS_PER_MINUTE, 0, MAX_MINUTES)
                    numberField(
                        "$p.sec",
                        "Seconds",
                        (seconds ?: DEFAULT_SECONDS) % SECONDS_PER_MINUTE,
                        0,
                        SECONDS_PER_MINUTE - 1,
                    )
                }
            }
            if (seconds != null) {
                div("tr-cues") {
                    span("tr-muted") { +"Cues (seconds left)" }
                    div("chip-row") {
                        PresetEdits.CUE_OPTIONS.filter { it < seconds }.forEach { cue ->
                            label("chip tr-cue") {
                                input(type = InputType.checkBox, name = "$p.cue") {
                                    value = cue.toString()
                                    checked = cue in phase.cues
                                }
                                span { +(if (cue == 0) "Time's up" else "${cue}s") }
                            }
                        }
                    }
                }
            }
            if (phase.scope == PhaseScope.ROUND_END) {
                div("field") {
                    label {
                        htmlFor = "f-$p-end"
                        +"Runs on"
                    }
                    select {
                        id = "f-$p-end"
                        name = "$p.end"
                        option {
                            value = ""
                            selected = phase.endStepId == null
                            +"After the checklist"
                        }
                        endSteps.forEachIndexed { i, step ->
                            option {
                                value = step.id
                                selected = phase.endStepId == step.id
                                +"Step ${i + 1}: ${step.title}"
                            }
                        }
                    }
                }
            }
            if (showIssues) {
                issues.mapNotNull(PresetEdits::issueText).forEach { p("field-error") { +it } }
            }
        }
    }

    private fun HtmlBlockTag.tableRules(preset: TimerPreset, issues: List<PresetIssue>, showIssues: Boolean) {
        sectionHeader("Table rules")
        p("tr-muted") { +"House rules, not in the rulebook." }
        card {
            label("check") {
                input(type = InputType.checkBox, name = "shotOn") {
                    value = "true"
                    checked = preset.shotClockSeconds != null
                }
                span { +"Hurry up: on-demand countdown for a slow decision, in any phase or in overtime" }
            }
            numberField(
                "shot",
                "Hurry up length (seconds)",
                preset.shotClockSeconds ?: TimerPreset.DEFAULT_SHOT_CLOCK_SECONDS,
                TimerPresetValidator.MIN_SECONDS,
                TimerPresetValidator.MAX_CUE_SECONDS,
            )
            numberField(
                "timeOuts",
                "Time-outs per team per round (0 = off)",
                preset.timeOutsPerRound,
                TimerPresetValidator.TIME_OUTS_RANGE.first,
                TimerPresetValidator.TIME_OUTS_RANGE.last,
            )
            numberField(
                "timeOutSeconds",
                "Time-out length (seconds, added to the current phase)",
                preset.timeOutSeconds,
                TimerPresetValidator.MIN_SECONDS,
                TimerPresetValidator.MAX_CUE_SECONDS,
            )
            val bad = issues.filter {
                it.kind == PresetIssue.Kind.SHOT_CLOCK_OUT_OF_RANGE ||
                    it.kind == PresetIssue.Kind.TIME_OUTS_OUT_OF_RANGE
            }
            if (showIssues) {
                bad.forEach { issue ->
                    p("field-error") {
                        +(
                            if (issue.kind == PresetIssue.Kind.SHOT_CLOCK_OUT_OF_RANGE) {
                                "Hurry up must be between ${TimerPresetValidator.MIN_SECONDS} s and 10 min."
                            } else {
                                "Time-outs: up to ${TimerPresetValidator.TIME_OUTS_RANGE.last}, each up to 10 min."
                            }
                            )
                    }
                }
            }
        }
    }

    private fun HtmlBlockTag.phaseOp(op: String, label: String, glyph: String, enabled: Boolean) {
        button(type = ButtonType.submit, classes = "icon-button tr-phase-op") {
            attributes["name"] = "op"
            attributes["value"] = op
            attributes["aria-label"] = label
            attributes["title"] = label
            if (!enabled) attributes["disabled"] = "disabled"
            span {
                attributes["aria-hidden"] = "true"
                +glyph
            }
        }
    }

    private fun HtmlBlockTag.numberField(name: String, label: String, value: Int, min: Int, max: Int) {
        val fieldId = "f-${name.replace('.', '-')}"
        div("field tr-number") {
            label {
                htmlFor = fieldId
                +label
            }
            input(type = InputType.number, name = name) {
                id = fieldId
                this.value = value.toString()
                attributes["min"] = min.toString()
                attributes["max"] = max.toString()
                attributes["inputmode"] = "numeric"
            }
        }
    }

    private fun HtmlBlockTag.hiddenField(name: String, value: String) {
        input(type = InputType.hidden, name = name) { this.value = value }
    }

    // ---- reading the form ----------------------------------------------------------------------

    /** The draft preset from the editor form; null when the form isn't one (no id). */
    internal fun readDraft(p: Parameters): TimerPreset? {
        val id = p["id"]?.takeIf { it.isNotBlank() } ?: return null
        val phases = p.getAll("phase").orEmpty().distinct().mapNotNull { phaseId -> readPhase(p, phaseId) }
        val shotOn = p["shotOn"] == "true"
        return TimerPreset(
            id = id,
            name = p["name"].orEmpty(),
            phases = phases,
            turnsPerRound = p["turns"]?.toIntOrNull() ?: TimerPreset.RULEBOOK_TURNS_PER_ROUND,
            autoAdvance = p["autoAdvance"] == "true",
            revision = p["revision"]?.toIntOrNull() ?: 0,
            shotClockSeconds = if (shotOn) p["shot"]?.toIntOrNull() ?: TimerPreset.DEFAULT_SHOT_CLOCK_SECONDS else null,
            timeOutsPerRound = p["timeOuts"]?.toIntOrNull() ?: 0,
            timeOutSeconds = p["timeOutSeconds"]?.toIntOrNull() ?: TimerPreset.DEFAULT_TIME_OUT_SECONDS,
            volumeKeys = p["volumeKeys"] == "true",
        )
    }

    private fun readPhase(p: Parameters, phaseId: String): PhaseSpec? {
        val key = "p.$phaseId"
        val kind = PhaseKind.entries.firstOrNull { it.name == p["$key.kind"] } ?: return null
        val scope = PhaseScope.entries.firstOrNull { it.name == p["$key.scope"] } ?: return null
        val untimed = p["$key.untimed"] == "true"
        val seconds = if (untimed) {
            null
        } else {
            (p["$key.min"]?.toIntOrNull() ?: 0) * SECONDS_PER_MINUTE + (p["$key.sec"]?.toIntOrNull() ?: 0)
        }
        val was = p["$key.was"]?.toIntOrNull()
        val cues = p.getAll("$key.cue").orEmpty().mapNotNull { it.toIntOrNull() }.distinct().sortedDescending()
        val phase = PhaseSpec(
            id = phaseId,
            name = p["$key.name"].orEmpty(),
            kind = kind,
            scope = scope,
            seconds = was,
            enabled = p["$key.enabled"] == "true",
            cues = if (was == null) emptyList() else cues,
            endStepId = if (scope == PhaseScope.ROUND_END) p["$key.end"]?.takeIf { it.isNotBlank() } else null,
        )
        // A changed length drops the cues that no longer fit, as the phone's editor does.
        return if (seconds == was) phase else PresetEdits.setSeconds(phase, seconds)
    }

    /** Applies an editor button (add/move/remove a timer) to the draft; "save" and others change nothing. */
    internal fun applyOp(draft: TimerPreset, op: String): TimerPreset {
        val parts = op.split(':')
        return when (parts[0]) {
            "add" -> PhaseScope.entries.firstOrNull { it.name == parts.getOrNull(1) }
                ?.let { PresetEdits.addPhase(draft, it, UUID.randomUUID().toString()) } ?: draft

            "move" -> {
                val dir = parts.getOrNull(2)?.toIntOrNull() ?: return draft
                PresetEdits.move(draft, parts.getOrNull(1).orEmpty(), dir.coerceIn(-1, 1))
            }

            "remove" -> PresetEdits.removePhase(draft, parts.getOrNull(1).orEmpty())

            else -> draft
        }
    }

    private const val SECONDS_PER_MINUTE = 60
    private const val MAX_MINUTES = 60
    private const val DEFAULT_SECONDS = PresetEdits.DEFAULT_NEW_SECONDS
}
