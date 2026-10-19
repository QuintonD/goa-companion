package dev.quinton.goa.hub.settings

import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.core.presentation.settings.OwnedProducts
import dev.quinton.goa.hub.feature.AppDownloadFeature
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.group.scriptButton
import dev.quinton.goa.hub.settings.SettingsFeature.ABOUT_PATH
import dev.quinton.goa.hub.settings.SettingsFeature.BACKUP_PATH
import dev.quinton.goa.hub.settings.SettingsFeature.PATH
import dev.quinton.goa.hub.settings.SettingsFeature.RATINGS_PATH
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.ThemeChoice
import dev.quinton.goa.hub.web.ui.hubButton
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.listRow
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import io.ktor.server.application.ApplicationCall
import kotlinx.html.ButtonType
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span

internal val SettingsStyles = listOf("settings/settings.css")
internal val SettingsScripts = listOf("push.js", "settings/settings.js")

internal fun settingsPage(title: String, back: String = PATH) =
    HubPage(title, back = back, styles = SettingsStyles, scripts = SettingsScripts)

/** `/settings`: the phone's Settings screen, per browser, plus links to the group-wide pages. */
internal suspend fun ApplicationCall.respondSettingsPage(ctx: HubContext) {
    val id = device.id
    val settings = SettingsFeature.settings(ctx, id)
    val theme = SettingsFeature.theme(ctx, id)
    val subscribed = ctx.push.subscriptions(id).isNotEmpty()
    respondPage(settingsPage("Settings", back = "/heroes")) {
        sectionHeader("Appearance")
        themeCard(theme)
        sectionHeader("Collection")
        collectionCard(ctx, settings)
        sectionHeader("At the table")
        tableCard(settings)
        timerLayoutCard(settings)
        alertsCard(subscribed)
        sectionHeader("Game setup")
        div("link-card") {
            listRow("Timer presets", "Phase lengths, cues and auto-advance · on the Timer tab", href = "/timer")
            listRow("Draft methods", "House draft, official methods, your own · on the Draft tab", href = "/draft")
            listRow("Ratings", "Elo constants and double-pilot weighting · shared with the group", href = RATINGS_PATH)
        }
        sectionHeader("Data")
        div("link-card") {
            listRow("Group", "Who you are, invite friends, signed-in browsers", href = "/group") {
                span("row-icon") { icon(Icons.GROUP) }
            }
            listRow("Backup and restore", "Export the group's data, or merge a backup in", href = BACKUP_PATH) {
                span("row-icon") { icon(Icons.DOWNLOAD) }
            }
            listRow("Get the Android app", "For friends with an Android phone", href = AppDownloadFeature.PATH) {
                span("row-icon") { icon(Icons.DEVICES) }
            }
            listRow("About", "Version, credits and licences", href = ABOUT_PATH) {
                span("row-icon") { icon(Icons.INFO) }
            }
        }
    }
}

private fun FlowContent.themeCard(theme: ThemeChoice) {
    section("card") {
        id = SettingsCard.APPEARANCE.id
        h3 { +"Theme" }
        p("muted") { +"Dark is easiest on the eyes at the table." }
        postForm("$PATH/theme") {
            segmented(
                "Theme",
                "theme",
                listOf(ThemeChoice.DARK to "Dark", ThemeChoice.LIGHT to "Light", ThemeChoice.SYSTEM to "System")
                    .map { (value, label) -> Triple(value.name, label, value == theme) },
            )
        }
    }
}

internal fun FlowContent.collectionCard(ctx: HubContext, settings: AppSettings) {
    val everything = OwnedProducts.ownsEverything(settings)
    val options = OwnedProducts.options(settings, ctx.catalog.catalog)
    section("card") {
        id = SettingsCard.COLLECTION.id
        postForm("$PATH/products", classes = "settings-form") {
            autoSave(SettingsCard.COLLECTION)
            switchRow(
                "everything",
                "I own every product",
                OwnedProducts.summary(options, everything),
                checked = everything,
            )
            div("product-list") {
                options.forEach { option ->
                    label(if (everything) "check product disabled" else "check product") {
                        input(type = InputType.checkBox, name = "product") {
                            value = option.id
                            checked = option.owned
                            disabled = everything
                        }
                        span("check-text") {
                            span("check-title") { +option.name }
                            OwnedProducts.detail(option)?.let { span("check-subtitle") { +it } }
                        }
                    }
                }
            }
            saveButton()
        }
    }
}

internal fun FlowContent.tableCard(settings: AppSettings) {
    section("card") {
        id = SettingsCard.TABLE.id
        postForm("$PATH/table", classes = "settings-form") {
            autoSave(SettingsCard.TABLE)
            switchRow(
                "haptics",
                "Haptics",
                "Vibrate on timer cues and draft turns. iPhones can't vibrate for a web page; this works where " +
                    "the browser can (Chrome on Android).",
                checked = settings.hapticsEnabled,
            )
            switchRow("sounds", "Sounds", "Play timer cues and phase alerts", checked = settings.soundsEnabled)
            saveButton()
        }
    }
}

internal fun FlowContent.timerLayoutCard(settings: AppSettings) {
    section("card") {
        id = SettingsCard.TIMER.id
        h3 { +"Running timer" }
        p("muted") {
            +"Step by step shows one big dial per phase; Whole round lists every phase of the round on one screen. "
            +"Also switchable from the timer's top bar."
        }
        postForm("$PATH/timer-layout") {
            autoSave(SettingsCard.TIMER, trigger = "submit")
            segmented(
                "Running timer",
                "layout",
                listOf(TimerLayout.STEPS to "Step by step", TimerLayout.ROUND to "Whole round")
                    .map { (value, label) -> Triple(value.name, label, value == settings.timerLayout) },
            )
        }
    }
}

/** "Lock-screen alerts": the push subscription of this browser (`push.js`; `settings.js` drives it). */
private fun FlowContent.alertsCard(subscribed: Boolean) {
    section("card alerts") {
        id = SettingsCard.ALERTS.id
        attributes["data-push"] = if (subscribed) "on" else "off"
        div("card-title-row") {
            icon(Icons.NOTIFICATIONS)
            h3 { +"Lock-screen alerts" }
        }
        p("push-status") {
            attributes["role"] = "status"
            attributes["aria-live"] = "polite"
            +if (subscribed) {
                "On: the timer alerts this browser at each phase end, even when the phone is locked."
            } else {
                "Off. Turning them on needs JavaScript and, on an iPhone, the app on the Home Screen (iOS 16.4 or later)."
            }
        }
        div("actions") {
            scriptButton("Turn on", ButtonStyle.PRIMARY, Icons.NOTIFICATIONS, mapOf("data-push-action" to "on"))
            scriptButton("Send a test", ButtonStyle.TONAL, null, mapOf("data-push-action" to "test"))
            scriptButton("Turn off", ButtonStyle.TEXT, null, mapOf("data-push-action" to "off"))
        }
    }
}

/**
 * Changes save at once with htmx (the card is swapped in place): on [trigger] — `change` for
 * switches and boxes, `submit` for segmented buttons. Without script the form posts as it is.
 */
private fun FORM.autoSave(card: SettingsCard, trigger: String = "change") {
    attributes["hx-post"] = action
    attributes["hx-trigger"] = trigger
    attributes["hx-target"] = "#${card.id}"
    attributes["hx-select"] = "#${card.id}"
    attributes["hx-swap"] = "outerHTML"
}

private fun FORM.saveButton() {
    div("nojs-only") { hubButton("Save", ButtonStyle.TONAL) }
}

/** A labelled switch row: [title], [subtitle], the switch on the right (a checkbox with role=switch). */
private fun FlowContent.switchRow(name: String, title: String, subtitle: String?, checked: Boolean) {
    label("switch-row") {
        span("check-text") {
            span("check-title") { +title }
            subtitle?.let { span("check-subtitle") { +it } }
        }
        input(type = InputType.checkBox, name = name, classes = "switch-input") {
            value = "true"
            this.checked = checked
            attributes["role"] = "switch"
        }
    }
}

/** Segmented buttons: each is a submit button carrying its value; the chosen one is pressed. */
private fun FlowContent.segmented(label: String, name: String, options: List<Triple<String, String, Boolean>>) {
    div("segmented") {
        attributes["role"] = "group"
        attributes["aria-label"] = label
        options.forEach { (value, text, selected) ->
            button(type = ButtonType.submit, classes = if (selected) "segment selected" else "segment") {
                this.name = name
                this.value = value
                attributes["aria-pressed"] = selected.toString()
                if (selected) icon(Icons.CHECK)
                span { +text }
            }
        }
    }
}
