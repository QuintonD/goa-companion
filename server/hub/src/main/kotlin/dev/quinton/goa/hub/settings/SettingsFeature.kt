package dev.quinton.goa.hub.settings

import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.core.presentation.settings.OwnedProducts
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeature
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.isHtmx
import dev.quinton.goa.hub.web.ui.ShellSlots
import dev.quinton.goa.hub.web.ui.ThemeChoice
import dev.quinton.goa.hub.web.ui.respondFragment
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Settings (PRODUCT.md *Settings*, ADR-030 §8): what the phone's Settings offers that makes sense per
 * browser — theme (`ShellSlots.Theme`), owned products, sounds, haptics and the running-timer view
 * (`SharedSlots.Settings`, written only here), lock-screen alerts — plus the group's rating
 * constants (shared, stamped like the app), backup export and Merge import, and About.
 */
object SettingsFeature : HubFeature {
    const val PATH = "/settings"
    const val RATINGS_PATH = "/settings/ratings"
    const val BACKUP_PATH = "/settings/backup"
    const val ABOUT_PATH = "/settings/about"

    override fun Route.routes(ctx: HubContext) {
        get(PATH) { call.respondSettingsPage(ctx) }
        post("$PATH/theme") { setTheme(ctx, call) }
        post("$PATH/products") {
            val form = call.receiveParameters()
            val all = ctx.catalog.catalog.products.mapTo(LinkedHashSet()) { it.id }
            val ticked = form.getAll("product").orEmpty().toSet()
            val next = update(ctx, call) { OwnedProducts.fromForm(it, all, form["everything"] == "true", ticked) }
            answer(call, SettingsCard.COLLECTION) { collectionCard(ctx, next) }
        }
        post("$PATH/table") {
            val form = call.receiveParameters()
            val next = update(ctx, call) {
                it.copy(hapticsEnabled = form["haptics"] == "true", soundsEnabled = form["sounds"] == "true")
            }
            answer(call, SettingsCard.TABLE) { tableCard(next) }
        }
        post("$PATH/timer-layout") {
            val layout = call.receiveParameters()["layout"]?.let { v ->
                TimerLayout.entries.firstOrNull { it.name == v }
            }
            val next = update(ctx, call) { s -> layout?.let { s.copy(timerLayout = it) } ?: s }
            answer(call, SettingsCard.TIMER) { timerLayoutCard(next) }
        }
        ratingsRoutes(ctx)
        backupRoutes(ctx)
        get(ABOUT_PATH) { call.respondAboutPage(ctx) }
    }

    /** This browser's settings; missing = the app's defaults. */
    fun settings(ctx: HubContext, deviceId: String): AppSettings =
        ctx.deviceStore.read(deviceId, SharedSlots.Settings) ?: AppSettings()

    fun theme(ctx: HubContext, deviceId: String): ThemeChoice =
        ctx.deviceStore.read(deviceId, ShellSlots.Theme) ?: ThemeChoice.DEFAULT

    /**
     * Stores [settings] and [theme] together: the shell reads `ShellSlots.Theme`; `AppSettings.theme`
     * mirrors it, so a backup carries the browser's theme as the phone's does.
     */
    suspend fun store(ctx: HubContext, deviceId: String, settings: AppSettings, theme: ThemeChoice? = null) {
        val chosen = theme ?: themeOf(settings.theme)
        ctx.deviceStore.write(deviceId, ShellSlots.Theme, chosen)
        ctx.deviceStore.update(deviceId, SharedSlots.Settings) { settings.copy(theme = modeOf(chosen)) }
    }

    fun themeOf(mode: ThemeMode): ThemeChoice = ThemeChoice.valueOf(mode.name)

    fun modeOf(choice: ThemeChoice): ThemeMode = ThemeMode.valueOf(choice.name)

    private suspend fun setTheme(ctx: HubContext, call: ApplicationCall) {
        val choice = call.receiveParameters()["theme"]?.let { v -> ThemeChoice.entries.firstOrNull { it.name == v } }
        if (choice != null) store(ctx, call.device.id, settings(ctx, call.device.id), choice)
        // The whole page changes colour: always a full page.
        call.respondRedirect("$PATH#${SettingsCard.APPEARANCE.id}")
    }

    private suspend fun update(
        ctx: HubContext,
        call: ApplicationCall,
        change: (AppSettings) -> AppSettings,
    ): AppSettings {
        val id = call.device.id
        val theme = theme(ctx, id)
        return ctx.deviceStore.update(id, SharedSlots.Settings) { old ->
            change(old ?: AppSettings()).copy(theme = modeOf(theme))
        } ?: AppSettings()
    }

    /** htmx swaps the card it came from; without script, back to the page at that card. */
    private suspend fun answer(call: ApplicationCall, card: SettingsCard, render: kotlinx.html.FlowContent.() -> Unit) {
        if (call.isHtmx) call.respondFragment { render() } else call.respondRedirect("$PATH#${card.id}")
    }
}

/** The cards of `/settings`, with their anchors. */
internal enum class SettingsCard(val id: String) {
    APPEARANCE("appearance"),
    COLLECTION("collection"),
    TABLE("table"),
    TIMER("running-timer"),
    ALERTS("alerts"),
}
