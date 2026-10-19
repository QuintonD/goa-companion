package dev.quinton.goa.hub.draft

import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.StepSpec
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.presentation.draft.MethodEditUiState
import dev.quinton.goa.core.presentation.draft.MethodRow
import dev.quinton.goa.core.presentation.draft.MethodsUiState
import dev.quinton.goa.core.presentation.draft.PoolKind
import dev.quinton.goa.core.presentation.draft.PreviewLayout
import dev.quinton.goa.core.presentation.draft.StepSection
import dev.quinton.goa.core.presentation.draft.actionName
import dev.quinton.goa.core.presentation.draft.heroesText
import dev.quinton.goa.core.presentation.draft.originLabel
import dev.quinton.goa.core.presentation.draft.poolText
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.dialogButton
import dev.quinton.goa.hub.web.ui.hubDialog
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.sectionHeader
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.ul

/** The methods list: built-in and custom methods with their order for a chosen layout. */
internal fun FlowContent.methodsPage(state: MethodsUiState) {
    div("card-head") {
        p("label") { +"Preview for" }
        linkButton("New method", "${DraftFeature.METHODS}/edit/${DraftActions.NEW}", ButtonStyle.PRIMARY, Icons.ADD)
    }
    div("chip-row") {
        PreviewLayout.OPTIONS.forEach { layout ->
            val on = layout == state.layout
            a("${DraftFeature.METHODS}?layout=${layout.key}", classes = if (on) "chip selected" else "chip") {
                if (on) attributes["aria-current"] = "true"
                if (on) icon(Icons.CHECK)
                span { +"${layout.label} heroes" }
            }
        }
    }
    sectionHeader("Built in")
    state.builtIn.forEach { methodCard(it, custom = false) }
    sectionHeader("Your methods")
    if (state.custom.isEmpty()) {
        p("hint") {
            +(
                "Duplicate a method to tweak it, or create one from scratch. " +
                    "Pool size, bans and the pick pattern are all editable."
                )
        }
    }
    state.custom.forEach { methodCard(it, custom = true) }
}

private fun FlowContent.methodCard(row: MethodRow, custom: Boolean) {
    val method = row.method
    card("method-card") {
        div("card-head") {
            h3 { +method.name }
            span("badge") {
                +when {
                    row.isDefault -> "Default"
                    method.origin == MethodOrigin.CUSTOM -> "Custom"
                    else -> originLabel(method.origin)
                }
            }
        }
        p { +method.summary }
        p("label") { +(poolText(method.pool) + heroesText(row.preview)) }
        val error = row.preview.error
        if (error != null) p("hint") { +error } else sequenceStrip(row.preview.tokens)
        if (method.notes.isNotEmpty()) {
            details("sources") {
                summary { +"Sources" }
                ul { method.notes.forEach { li { +it } } }
            }
        }
        div("actions end") {
            if (custom) {
                dialogButton("Delete", "delete-${method.id}", ButtonStyle.TEXT, Icons.DELETE)
                linkButton("Edit", "${DraftFeature.METHODS}/edit/${method.id}", ButtonStyle.TEXT, Icons.EDIT)
            } else {
                postForm(DraftFeature.METHODS) { opButton("duplicate:${method.id}", "Duplicate", "btn btn-text") }
            }
        }
    }
    if (custom) {
        hubDialog("delete-${method.id}", "Delete ${method.name}?") {
            p { +"Past drafts keep their record; the method just won't be offered again." }
            postForm(DraftFeature.METHODS, "actions end") {
                button(type = ButtonType.button, classes = "btn btn-text") {
                    attributes["data-dialog-close"] = ""
                    +"Cancel"
                }
                opButton("delete:${method.id}", "Delete", "btn btn-danger")
            }
        }
    }
}

/** The method editor: name, pool, the step order, the step timer, and a live preview. */
internal fun FlowContent.methodEditor(key: String, state: MethodEditUiState) {
    postForm("${DraftFeature.METHODS}/edit/$key", "stack editor") {
        defaultApply()
        input(type = InputType.hidden, name = "id") { value = state.id }
        card {
            div("field") {
                label {
                    htmlFor = "f-name"
                    +"Name"
                }
                input(type = InputType.text, name = "name") {
                    attributes["autocapitalize"] = "sentences"
                    attributes["enterkeyhint"] = "done"
                    id = "f-name"
                    value = state.name
                    maxLength = MethodEditUiState.MAX_NAME.toString()
                    attributes["autocomplete"] = "off"
                }
                state.copyOf?.let { p("hint") { +"Copy of $it — the original stays unchanged" } }
            }
        }
        poolCard(state)
        if (state.fixedSequence == null) {
            stepsCard(StepSection.OPENING, "Opening", "Runs once, in order", state.opening)
            stepsCard(
                StepSection.REPEAT,
                "Then repeat",
                "Cycles until every seat is filled; picks for a full team are skipped",
                state.repeat,
            )
        } else {
            card {
                h3 { +"Sequence" }
                p("hint") { +"Set by the original method for each player count" }
                p {
                    +(
                        "This order depends on the number of heroes, so it can't be edited step by step. " +
                            "You can still change the name, pool and timer."
                        )
                }
            }
        }
        timerCard(state)
        previewCard(state)
        state.error?.let {
            p("blocker error-text") {
                id = "error"
                attributes["role"] = "alert"
                +it
            }
        }
        div("actions") {
            opButton("cancel", "Cancel", "btn btn-text")
            opButton("save", "Save method", "btn btn-primary grow")
        }
    }
}

private fun FlowContent.stepsCard(section: StepSection, title: String, subtitle: String, steps: List<StepSpec>) {
    card("steps") {
        id = "steps-${section.name.lowercase()}"
        h3 { +title }
        p("hint") { +subtitle }
        if (steps.isEmpty()) p("hint") { +"No steps" }
        ol("step-rows") {
            steps.forEachIndexed { i, step ->
                li("step-row") {
                    span("step-index") { +"${i + 1}." }
                    opButton(
                        "action:${section.name}:$i",
                        actionName(step.action),
                        if (step.action == DraftAction.BAN) "chip step-ban" else "chip step-pick",
                    ) { attributes["aria-label"] = "${actionName(step.action)}, tap to switch" }
                    opButton("team:${section.name}:$i", "Team ${step.team.name}", "chip") {
                        attributes["aria-label"] = "Team ${step.team.name}, tap to switch"
                    }
                    opButton(
                        "hidden:${section.name}:$i",
                        if (step.hidden) "Hidden" else "Open",
                        if (step.hidden) "chip selected" else "chip",
                    ) {
                        attributes["aria-pressed"] = step.hidden.toString()
                        attributes["aria-label"] =
                            if (step.hidden) "Hidden step, tap to open" else "Open step, tap to hide"
                    }
                    span("step-moves") {
                        opButton("up:${section.name}:$i", "↑", "icon-button", enabled = i > 0) {
                            attributes["aria-label"] =
                                "Move up"
                        }
                        opButton("down:${section.name}:$i", "↓", "icon-button", enabled = i < steps.lastIndex) {
                            attributes["aria-label"] = "Move down"
                        }
                        opButton("remove:${section.name}:$i", null, "icon-button") {
                            attributes["aria-label"] = "Remove step ${i + 1}"
                            icon(Icons.CLOSE)
                        }
                    }
                }
            }
        }
        div("chip-row") {
            for (action in listOf(DraftAction.BAN, DraftAction.PICK)) {
                for (team in TeamSlot.entries) {
                    opButton(
                        "add:${section.name}:${action.name}:${team.name}",
                        "+ ${actionName(action)} ${team.name}",
                        "chip",
                    )
                }
            }
        }
    }
}

private fun FlowContent.poolCard(state: MethodEditUiState) {
    card {
        h3 { +"Hero pool" }
        p("hint") { +poolText(state.pool) }
        div("inline-fields") {
            div("field") {
                label {
                    htmlFor = "f-pool-kind"
                    +"Pool"
                }
                select {
                    id = "f-pool-kind"
                    name = "poolKind"
                    PoolKind.entries.forEach { kind ->
                        option {
                            value = kind.name
                            selected = kind == state.poolKind
                            +kind.label
                        }
                    }
                }
            }
            if (state.poolKind != PoolKind.ALL) {
                div("field narrow") {
                    label {
                        htmlFor = "f-pool-value"
                        +"N"
                    }
                    input(type = InputType.number, name = "poolValue") {
                        id = "f-pool-value"
                        value = state.poolValue.toString()
                        min = (if (state.poolKind == PoolKind.SEATS_PLUS) 0 else 1).toString()
                        max = MethodEditUiState.MAX_POOL.toString()
                        attributes["inputmode"] = "numeric"
                    }
                }
            }
        }
        opButton("apply", "Update", "btn btn-text")
    }
}

private fun FlowContent.timerCard(state: MethodEditUiState) {
    card {
        h3 { +"Step timer" }
        p("hint") { +(state.stepSeconds?.let { "$it seconds per step by default" } ?: "Off by default") }
        div("field") {
            label {
                htmlFor = "f-seconds"
                +"Seconds per step"
            }
            select {
                id = "f-seconds"
                name = "stepSeconds"
                option {
                    value = ""
                    selected = state.stepSeconds == null
                    +"Off"
                }
                DraftActions.STEP_SECONDS.forEach { s ->
                    option {
                        value = s.toString()
                        selected = state.stepSeconds == s
                        +"${s}s"
                    }
                }
            }
        }
    }
}

private fun FlowContent.previewCard(state: MethodEditUiState) {
    card {
        h3 { +"Preview" }
        p("hint") { +"The exact order for a seat layout" }
        div("chip-row") {
            PreviewLayout.OPTIONS.forEach { layout ->
                opChip(
                    "layout:${layout.key}",
                    "${layout.label} heroes",
                    layout == state.layout,
                )
            }
        }
        val preview = state.preview
        val error = preview.error
        if (error != null) {
            p("warning-text") { +error }
        } else {
            sequenceStrip(preview.tokens)
            if (preview.heroesNeeded > 0) p("hint") { +"Needs at least ${preview.heroesNeeded} heroes in the pool" }
        }
    }
}
