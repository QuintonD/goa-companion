package dev.quinton.goa.hub.settings

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.presentation.settings.RatingConstant
import dev.quinton.goa.core.presentation.settings.RatingConstants
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.settings.SettingsFeature.RATINGS_PATH
import dev.quinton.goa.hub.store.InvalidGroupDataException
import dev.quinton.goa.hub.web.isHtmx
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.errorState
import dev.quinton.goa.hub.web.ui.hubButton
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.notice
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.respondFragment
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.output
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span

/**
 * Settings → Ratings: the group's Elo constants (ADR-011), shared with every phone. The controls,
 * ranges, steps and readings are the app's ([RatingConstants]); a change is stored through
 * `GroupStore.updateEloConfig` (sanitized, stamped after the current change, as the app's
 * repository does), so it reaches the phones at their next sync. Saving unchanged values stores
 * nothing, and only the constants moved on the form are written ([RatingConstants.applyChanged]),
 * so a stale form can't override a newer change from a phone.
 */
internal fun Route.ratingsRoutes(ctx: HubContext) {
    get(RATINGS_PATH) { call.respondRatings(ctx) }
    post(RATINGS_PATH) {
        val form = call.receiveParameters()
        val values = RatingConstants.all.associate { it.key to form[it.key] }
        val shown = RatingConstants.all.associate { it.key to form[SHOWN_PREFIX + it.key] }
        val error = try {
            // Only the constants moved on this form, applied to the stored ones under the lock: a
            // form opened before a phone changed another constant never writes that one back.
            ctx.groupStore.updateEloConfig { current -> RatingConstants.applyChanged(current, shown, values) }
            null
        } catch (e: InvalidGroupDataException) {
            e.message
        }
        if (call.isHtmx) {
            call.respondFragment(if (error == null) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity) {
                ratingsForm(ctx, error)
            }
        } else if (error != null) {
            call.respondRatings(ctx, error)
        } else {
            call.respondRedirect(RATINGS_PATH)
        }
    }
    get("$RATINGS_PATH/reset") {
        call.respondPage(settingsPage(RatingConstants.RESET_TITLE, back = RATINGS_PATH)) {
            card {
                p { +RatingConstants.RESET_TEXT }
                p("muted") { +"The constants are the group's: every phone gets the defaults at its next sync." }
                postForm("$RATINGS_PATH/reset", classes = "actions end") {
                    linkButton("Cancel", RATINGS_PATH, ButtonStyle.TEXT)
                    hubButton("Reset", ButtonStyle.DANGER)
                }
            }
        }
    }
    post("$RATINGS_PATH/reset") {
        if (ctx.groupStore.data().eloConfig != EloConfig.DEFAULT) ctx.groupStore.resetEloConfig()
        call.respondRedirect(RATINGS_PATH)
    }
}

private suspend fun ApplicationCall.respondRatings(ctx: HubContext, error: String? = null) {
    val status = if (error == null) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity
    respondPage(settingsPage("Ratings"), status) {
        val replay = ctx.ratings.current().ratings
        card("intro-card") {
            h2 { +RatingConstants.INTRO_TITLE }
            p { +RatingConstants.INTRO }
            p("muted small") { +RatingConstants.replayNote(replay.replayOrder.size, replay.skipped.size) }
        }
        notice(
            "Advanced. These constants are the group's: a change here reaches every phone at its next sync, " +
                "and everyone's ratings are recalculated with it.",
        )
        ratingsForm(ctx, error)
    }
}

/** The constants as range inputs; with htmx each change saves at once and the form comes back. */
private fun FlowContent.ratingsForm(ctx: HubContext, error: String?) {
    val config = ctx.groupStore.data().eloConfig
    section {
        id = "ratings"
        error?.let { errorState(it) }
        postForm(RATINGS_PATH, classes = "ratings-form") {
            attributes["hx-post"] = RATINGS_PATH
            attributes["hx-trigger"] = "change"
            attributes["hx-target"] = "#ratings"
            attributes["hx-swap"] = "outerHTML"
            RatingConstants.sections.forEach { section ->
                sectionHeader(section.title)
                section.note?.let { p("muted small") { +it } }
                section.constants.forEach { constant -> slider(constant, constant.get(config)) }
            }
            div("nojs-only") { hubButton("Save", ButtonStyle.TONAL) }
        }
        div("actions") {
            if (config == EloConfig.DEFAULT) {
                span("muted") { +"Using default settings" }
            } else {
                linkButton("Reset to defaults", "$RATINGS_PATH/reset", ButtonStyle.OUTLINED, Icons.DELETE)
            }
        }
    }
}

private fun FlowContent.slider(constant: RatingConstant, value: Double) {
    val fieldId = "r-${constant.key}"
    div("slider") {
        div("slider-head") {
            label {
                htmlFor = fieldId
                +constant.title
            }
            output(classes = "slider-value") {
                attributes["for"] = fieldId
                +constant.format(value)
            }
        }
        constant.explanation?.let { p("muted small") { +it } }
        input(type = InputType.hidden, name = SHOWN_PREFIX + constant.key) { this.value = number(value) }
        input(type = InputType.range, name = constant.key) {
            id = fieldId
            min = number(constant.range.start)
            max = number(constant.range.endInclusive)
            step = number(constant.step)
            this.value = number(value)
            attributes["aria-valuetext"] = constant.format(value)
        }
    }
}

/** The hidden field carrying the value a constant was shown with. */
private const val SHOWN_PREFIX = "shown-"

/** A plain decimal for HTML attributes ("0.05", "100"), never "1.0E-4". */
private fun number(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
