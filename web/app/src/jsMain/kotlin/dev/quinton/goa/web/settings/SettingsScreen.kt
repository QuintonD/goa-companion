package dev.quinton.goa.web.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.pwa.AssetManifest
import dev.quinton.goa.web.pwa.BuildInfo
import dev.quinton.goa.web.pwa.DeviceStorage
import dev.quinton.goa.web.pwa.OfflinePlan
import dev.quinton.goa.web.pwa.OfflineStatus
import dev.quinton.goa.web.pwa.StorageEstimate
import dev.quinton.goa.web.storage.getJson
import dev.quinton.goa.web.storage.putJson
import dev.quinton.goa.web.ui.BodyText
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LinearProgress
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.Segmented
import dev.quinton.goa.web.ui.ThemeMode
import kotlin.js.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * Settings: the theme, the web app's offline copy ("Download everything for offline", storage used
 * and whether the browser may clear it), updates, About. Streams add their own sections (timer
 * sounds, rating constants, puzzle nudges) as on Android.
 */
@Composable
fun SettingsScreen() {
    val services = LocalServices.current
    Screen("Settings") {
        SectionHeader("Appearance")
        GoaCard {
            H3({ classes("t-title-medium", "goa-card-title") }) { Text("Theme") }
            Segmented(
                label = "Theme",
                options = ThemeMode.entries,
                selected = services.theme.mode,
                optionLabel = { it.label },
                onSelect = services.theme::select,
            )
        }
        SectionHeader("Offline")
        OfflineCard()
        SectionHeader("App")
        GoaCard(flush = true) {
            RowList {
                ListRow(
                    title = "Version ${BuildInfo.version}",
                    subtitle = if (BuildInfo.isDev) {
                        "Development build"
                    } else {
                        "Build ${BuildInfo.build.take(
                            BUILD_ID_SHOWN,
                        )}"
                    },
                    leadingIcon = "refresh",
                    onClick = { checkForUpdate(services) },
                    trailing = "Check for update",
                )
                ListRow("About", leadingIcon = "info", route = Route.About)
            }
        }
    }
}

private fun checkForUpdate(services: dev.quinton.goa.web.AppServices) {
    val sw = services.serviceWorker
    when {
        BuildInfo.isDev -> services.messages.show("Updates come with the published site, not the dev server.")

        sw.updateReady -> sw.applyUpdate()

        else -> {
            sw.checkForUpdate(force = true)
            services.messages.show("Checking… if there is a new version, \"Reload\" appears here.")
        }
    }
}

/** When this device last finished "Download everything for offline" (IndexedDB `offline/download`). */
@Serializable
private data class OfflineRecord(val build: String, val finishedAt: Double)

private sealed interface OfflineUi {
    data object Checking : OfflineUi

    data class Unavailable(val reason: String) : OfflineUi

    data class Ready(val manifest: AssetManifest, val status: OfflineStatus) : OfflineUi
}

@Composable
private fun OfflineCard() {
    val services = LocalServices.current
    val offline = services.offline
    var ui by remember { mutableStateOf<OfflineUi>(OfflineUi.Checking) }
    var storage by remember { mutableStateOf<StorageEstimate?>(null) }
    var persisted by remember { mutableStateOf<Boolean?>(null) }
    var record by remember { mutableStateOf<OfflineRecord?>(null) }
    val progress = offline.progress

    // Re-checked whenever a download starts or ends.
    LaunchedEffect(progress == null) {
        if (progress != null) return@LaunchedEffect
        storage = DeviceStorage.estimate()
        persisted = DeviceStorage.persisted()
        record = runCatching { services.store.getJson<OfflineRecord>(OFFLINE_KEY) }.getOrNull()
        ui = when {
            !offline.supported -> OfflineUi.Unavailable("This browser can't keep the app offline here.")

            else -> try {
                val manifest = offline.manifest()
                OfflineUi.Ready(manifest, offline.status(manifest))
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                console.warn("Offline status unavailable", e)
                OfflineUi.Unavailable(
                    if (BuildInfo.isDev) {
                        "The offline copy is part of the published site (the dev server has no file list)."
                    } else {
                        "Couldn't read the site's file list. Try again when online."
                    },
                )
            }
        }
    }

    val scope = rememberCoroutineScope()
    fun download(manifest: AssetManifest) {
        scope.launch { persisted = DeviceStorage.persist() ?: persisted }
        offline.start(manifest) { result ->
            if (result.isSuccess) {
                runCatching { services.store.putJson(OFFLINE_KEY, OfflineRecord(BuildInfo.build, Date.now())) }
                services.messages.show("Everything is on this device: the app works offline.")
            } else {
                services.messages.show("The download stopped. Check the connection and try again.")
            }
        }
    }

    GoaCard {
        Div({ classes("goa-stack") }) {
            val state = ui
            when {
                progress != null -> {
                    P({ classes("t-title-medium") }) { Text("Downloading card scans and art…") }
                    LinearProgress(progress.fraction, "Download progress")
                    BodyText(
                        "${progress.doneFiles} of ${progress.totalFiles} images · " +
                            "${OfflinePlan.formatBytes(
                                progress.doneBytes,
                            )} of ${OfflinePlan.formatBytes(progress.totalBytes)}",
                    )
                    GoaButton("Stop", offline::stop, ButtonKind.OUTLINED)
                }

                state == OfflineUi.Checking -> LinearProgress(null, "Checking what is on this device")

                state is OfflineUi.Unavailable -> BodyText(state.reason)

                state is OfflineUi.Ready -> ReadyOffline(state, onDownload = { download(state.manifest) })
            }
            record?.let {
                BodyText("Last downloaded ${Date(it.finishedAt).toLocaleDateString()}.", "t-body-small")
            }
            StorageLines(storage, persisted)
        }
    }
}

@Composable
private fun ReadyOffline(state: OfflineUi.Ready, onDownload: () -> Unit) {
    val status = state.status
    if (status.complete) {
        Div({ classes("goa-inline") }) {
            Icon("check", IconSize.SMALL)
            P({ classes("t-title-medium") }) { Text("Everything is on this device") }
        }
        BodyText("All ${status.totalFiles} card scans, portraits and symbols work offline.")
    } else {
        P({ classes("t-title-medium") }) { Text("Download everything for offline") }
        val already = when (status.cachedFiles) {
            0 -> ""
            1 -> " 1 is on this device already."
            else -> " ${status.cachedFiles} are on this device already."
        }
        BodyText(
            "Heroes, rules and the app itself already work offline. Card scans and art load as you " +
                "open them; download all ${status.totalFiles} now " +
                "(${OfflinePlan.formatBytes(status.totalBytes - status.cachedBytes)}) to have them at a table " +
                "without signal.$already",
        )
        GoaButton("Download everything", onDownload, icon = "download", block = true)
    }
}

@Composable
private fun StorageLines(storage: StorageEstimate?, persisted: Boolean?) {
    storage?.let {
        Div({ classes("goa-inline", "c-variant") }) {
            Icon("storage", IconSize.SMALL)
            BodyText("Using ${OfflinePlan.formatBytes(it.usageBytes)} on this device.")
        }
    }
    when (persisted) {
        true -> BodyText("Kept: the browser won't clear this app's data on its own.", "t-body-small")

        else -> BodyText(
            "Safari may clear a website's data after 7 days without a visit. Add the app to your Home " +
                "Screen to keep it (Share > Add to Home Screen). Your group's sync keeps a copy of " +
                "players and matches either way.",
            "t-body-small",
        )
    }
}

private const val OFFLINE_KEY = "offline/download"
private const val BUILD_ID_SHOWN = 8
