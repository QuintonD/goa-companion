package dev.quinton.goa.hub.feature

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.HubClock
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.config.HubConfig
import dev.quinton.goa.hub.push.PushService
import dev.quinton.goa.hub.store.Device
import dev.quinton.goa.hub.store.DeviceStore
import dev.quinton.goa.hub.store.Devices
import dev.quinton.goa.hub.store.GroupStore
import dev.quinton.goa.hub.store.SyncStateStore
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.deviceOrNull
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import kotlinx.html.FlowContent

/**
 * One area of the web app (Heroes, Timer, Draft, Players, Rules, Group, Settings…). Register it with
 * one line in [HubFeatures]. Its [routes] are mounted inside the signed-in area: every request
 * there has a device (`call.device`), and every POST has passed the same-origin check.
 */
interface HubFeature {
    fun Route.routes(ctx: HubContext)

    /**
     * An optional bar shown above the bottom tabs on every page of this browser (the phone's
     * "timer running" bar); null = nothing to show for this call.
     */
    fun shellBar(ctx: HubContext, call: ApplicationCall): (FlowContent.() -> Unit)? = null
}

/** What a [HubFeature] gets to work with. */
class HubContext(private val hub: Hub) {
    /** The group's shared data; write only through its stamping helpers. */
    val groupStore: GroupStore get() = hub.groupStore

    /** Each browser's own state, in typed slots. */
    val deviceStore: DeviceStore get() = hub.deviceStore

    /** The signed-in browsers (list, rename, sign out). */
    val devices: Devices get() = hub.devices

    /** The bundled hero catalog and the other `catalog/` files. */
    val catalog: HubCatalog get() = hub.catalog

    val clock: HubClock get() = hub.clock

    /** Web Push to a device, now or scheduled. */
    val push: PushService get() = hub.push

    /** `hub.json` (public URL, Android certificate), re-read when it changes. */
    val config: HubConfig get() = hub.config.get()

    /** The group this hub belongs to (null only before the first join). */
    val group: GroupCredentials? get() = hub.membership.group()

    /** When phones last synced with the hub. */
    val syncState: SyncStateStore get() = hub.syncState

    /** The browser making [call] (inside a feature's routes it is always signed in). */
    fun device(call: ApplicationCall): Device = call.device

    fun deviceOrNull(call: ApplicationCall): Device? = call.deviceOrNull
}
