package dev.quinton.goa.hub.config

import java.nio.file.Path
import java.nio.file.Paths

/**
 * The hub's data directory (`GOA_HUB_DATA`, default `/var/lib/goa-hub`, ADR-030 §2–3). Everything
 * the hub keeps lives here, so a backup is a copy of this one directory.
 *
 * ```
 * hub.json                        config: public URL, Android certificate fingerprints
 * group.json                      the group's id, key and name (0600)
 * data.json                       the group's SyncDocument (SyncJson)
 * backups/data-YYYY-MM-DD.json    the last state of each day, 30 days
 * sync-state.json                 when phones last synced with the hub
 * vapid.json                      the Web Push key pair (0600)
 * devices/<deviceId>/device.json  a signed-in browser
 * devices/<deviceId>/<slot>.json  that browser's own state, one file per feature slot
 * app/goa.apk                     the latest release APK, copied in by the deploy script
 * ```
 */
class HubPaths(val root: Path) {
    val config: Path get() = root.resolve("hub.json")
    val group: Path get() = root.resolve("group.json")
    val data: Path get() = root.resolve("data.json")
    val backups: Path get() = root.resolve("backups")
    val syncState: Path get() = root.resolve("sync-state.json")
    val vapid: Path get() = root.resolve("vapid.json")
    val devices: Path get() = root.resolve("devices")
    val apk: Path get() = root.resolve("app").resolve("goa.apk")

    companion object {
        const val ENV = "GOA_HUB_DATA"
        const val DEFAULT = "/var/lib/goa-hub"

        /** [flag] (`--data`), else `GOA_HUB_DATA`, else the default. */
        fun resolve(flag: String?, env: Map<String, String> = System.getenv()): HubPaths =
            HubPaths(Paths.get(flag ?: env[ENV]?.takeIf { it.isNotBlank() } ?: DEFAULT).toAbsolutePath().normalize())
    }
}
