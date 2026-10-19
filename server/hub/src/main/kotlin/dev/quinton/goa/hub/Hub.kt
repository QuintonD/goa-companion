package dev.quinton.goa.hub

import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.config.HubConfigStore
import dev.quinton.goa.hub.config.HubPaths
import dev.quinton.goa.hub.config.MembershipStore
import dev.quinton.goa.hub.push.PushService
import dev.quinton.goa.hub.push.PushTransport
import dev.quinton.goa.hub.push.Vapid
import dev.quinton.goa.hub.store.DeviceStore
import dev.quinton.goa.hub.store.Devices
import dev.quinton.goa.hub.store.GroupRatings
import dev.quinton.goa.hub.store.GroupStore
import dev.quinton.goa.hub.store.SyncStateStore
import dev.quinton.goa.hub.web.JoinThrottle
import java.nio.file.Files

/**
 * Everything one hub process holds, wired once (the composition root). Pages reach it through
 * [dev.quinton.goa.hub.feature.HubContext]; tests build one over a temp directory.
 */
class Hub(
    val paths: HubPaths,
    machineClock: HubClock = HubClock.System,
    val catalog: HubCatalog = HubCatalog(),
    pushTransport: PushTransport = PushTransport.jdk(),
) {
    init {
        Files.createDirectories(paths.root)
    }

    val config = HubConfigStore(paths.config)

    /** The machine's clocks in the group's zone (`hub.json`'s `timeZone`, else the machine's). */
    val clock: HubClock = HubClock.zoned(machineClock) { config.get().zone() }
    val membership = MembershipStore(paths.group)
    val groupStore = GroupStore(paths.data, paths.backups, clock)

    /** Ratings, meta and group stats of the group's data: the one replay every page reads. */
    val ratings = GroupRatings(groupStore, catalog, clock)
    val devices = Devices(paths.devices)
    val deviceStore = DeviceStore(paths.devices)
    val syncState = SyncStateStore(paths.syncState)
    val vapid: Vapid = Vapid.loadOrCreate(paths.vapid) { pushSubject() }
    val push = PushService(deviceStore, vapid, clock, pushTransport)

    /** Failed sign-in attempts, throttled per client and overall. */
    val joinThrottle = JoinThrottle(clock)

    /** VAPID `sub`: the hub's public URL (Apple requires an https URL or a mailto). */
    private fun pushSubject(): String = config.get().publicUrl ?: "mailto:goa-hub@localhost"
}
