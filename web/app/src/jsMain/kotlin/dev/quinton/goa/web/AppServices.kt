package dev.quinton.goa.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.quinton.goa.web.data.WebServices
import dev.quinton.goa.web.nav.BrowserHistory
import dev.quinton.goa.web.nav.Router
import dev.quinton.goa.web.pwa.OfflineAssets
import dev.quinton.goa.web.pwa.ServiceWorkerClient
import dev.quinton.goa.web.storage.IndexedDbKeyValueStore
import dev.quinton.goa.web.storage.KeyValueStore
import dev.quinton.goa.web.ui.Messages
import dev.quinton.goa.web.ui.ThemeController

/**
 * The app's singletons, created once in `main()` and handed down as [LocalServices] (the web's
 * stand-in for Hilt). [data] holds the data contracts the screens read through (the same interfaces
 * as the phone's, one holder per stream: `data.content.catalog`, `data.group.players`, …); a
 * stream's long-lived services (repositories, the engine worker's client) go in its holder there,
 * never inside a screen.
 */
class AppServices(
    val router: Router,
    val store: KeyValueStore,
    val data: WebServices,
    val theme: ThemeController,
    val messages: Messages,
    val serviceWorker: ServiceWorkerClient,
    val offline: OfflineAssets,
) {
    companion object {
        fun create(): AppServices {
            val store = IndexedDbKeyValueStore()
            return AppServices(
                router = Router(BrowserHistory()),
                store = store,
                data = WebServices(store),
                theme = ThemeController(),
                messages = Messages(),
                serviceWorker = ServiceWorkerClient(),
                offline = OfflineAssets(),
            )
        }
    }
}

val LocalServices = staticCompositionLocalOf<AppServices> { error("AppServices not provided") }

/**
 * State kept while this screen's history entry exists (see [Router.retain]): a form returned to with
 * Back is still filled in. [name] tells several values on one screen apart.
 */
@Composable
fun <T : Any> rememberRetained(name: String, init: () -> T): T = LocalServices.current.router.retain(name, init)
