package dev.quinton.goa.hub.web

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.store.DeviceSlot
import dev.quinton.goa.hub.testHub
import dev.quinton.goa.hub.web.ui.ShellSlots
import dev.quinton.goa.hub.web.ui.StaticFiles
import dev.quinton.goa.hub.web.ui.ThemeChoice
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayInputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The page shell, static files, the manifest and icons, and the APK page. */
class ShellTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock()

    @Test
    fun `every tab renders the shell with the app's bottom navigation and no inline script or style`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val token = plainClient().signIn()
            for ((path, label) in listOf(
                "/heroes" to "Heroes",
                "/timer" to "Timer",
                "/draft" to "Draft",
                "/players" to "Players",
            )) {
                val response = client.get(path) { header(HttpHeaders.Cookie, cookie(token)) }
                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThat(response.headers[HttpHeaders.CacheControl]).isEqualTo("no-store")
                val html = response.bodyAsText()
                assertThat(html).contains("<nav class=\"tab-bar\" aria-label=\"Main\">")
                assertThat(
                    Regex("<a href=\"$path\" class=\"tab selected\" aria-current=\"page\">").containsMatchIn(html),
                ).isTrue()
                assertThat(html).contains("<span class=\"tab-label\">$label</span>")
                assertThat(html).doesNotContain(" style=")
                assertThat(html).doesNotContain("<style")
                assertThat(Regex("<script(?![^>]*\\ssrc=)").containsMatchIn(html)).isFalse()
                assertThat(Regex("\\son[a-z]+=").containsMatchIn(html)).isFalse()
                assertThat(html).contains("name=\"htmx-config\"")
                assertThat(html).contains("apple-mobile-web-app-capable")
                assertThat(html).contains("/manifest.webmanifest")
            }
            for (path in listOf("/rules", "/trivia", "/group", "/settings", "/app")) {
                val response = client.get(path) { header(HttpHeaders.Cookie, cookie(token)) }
                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThat(response.bodyAsText()).doesNotContain("class=\"tab selected\"")
            }
            // Rules, Card trivia and Settings open from any tab: Back returns to the page that opened them.
            for (path in listOf("/rules", "/trivia", "/settings")) {
                val html = client.get(path) { header(HttpHeaders.Cookie, cookie(token)) }.bodyAsText()
                assertThat(html).contains("aria-label=\"Back\" data-back=\"previous\"")
            }
            val hero = client.get("/heroes/arien") { header(HttpHeaders.Cookie, cookie(token)) }.bodyAsText()
            assertThat(hero).contains("<a href=\"/heroes\" class=\"icon-button\" aria-label=\"Back\" data-back=\"\">")
            val missing = client.get("/players/nope") { header(HttpHeaders.Cookie, cookie(token)) }
            assertThat(missing.status).isEqualTo(HttpStatusCode.NotFound)
            assertThat(missing.bodyAsText()).contains("tab-bar")
        }
    }

    @Test
    fun `the theme slot sets data-theme, dark like the app until chosen, system leaves it to the browser`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val token = plainClient().signIn()
            val device = hub.devices.list().single()
            suspend fun page() = client.get("/heroes") { header(HttpHeaders.Cookie, cookie(token)) }.bodyAsText()
            val fresh = page()
            assertThat(fresh).contains("data-theme=\"dark\"")
            assertThat(fresh).contains("content=\"black-translucent\"")
            hub.deviceStore.write(device.id, ShellSlots.Theme, ThemeChoice.LIGHT)
            val light = page()
            assertThat(light).contains("data-theme=\"light\"")
            // iOS: dark status-bar text on a light page.
            assertThat(light).contains("name=\"apple-mobile-web-app-status-bar-style\" content=\"default\"")
            hub.deviceStore.write(device.id, ShellSlots.Theme, ThemeChoice.SYSTEM)
            val system = page()
            assertThat(system).doesNotContain("data-theme")
            assertThat(system).contains("(prefers-color-scheme: light)")
        }
    }

    @Test
    fun `static files, fonts, crests and htmx are open and cacheable`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val css = client.get(StaticFiles.url("app.css"))
            assertThat(css.status).isEqualTo(HttpStatusCode.OK)
            assertThat(css.headers[HttpHeaders.ContentType]).startsWith("text/css")
            assertThat(css.headers[HttpHeaders.CacheControl]).isEqualTo("public, max-age=31536000, immutable")
            for (path in listOf(
                "/static/htmx.min.js", "/static/hub.js", "/static/join.js", "/static/push.js",
                "/static/fonts/cinzel_semibold.ttf", "/static/fonts/inter_regular.ttf", "/static/fonts/OFL-Inter.txt",
                "/static/crests/crest_23.svg", "/static/icons/nav_heroes_filled.svg", "/static/material/settings.svg",
                "/static/icons/brand_mark.svg",
            )) {
                assertThat(client.get(path).status).isEqualTo(HttpStatusCode.OK)
            }
            assertThat(client.get("/static/../hub.json").status).isEqualTo(HttpStatusCode.NotFound)
            val sw = client.get("/sw.js")
            assertThat(sw.headers["Service-Worker-Allowed"]).isEqualTo("/")
            assertThat(sw.bodyAsText()).contains("notificationclick")
        }
    }

    @Test
    fun `the manifest makes the app installable and its PNG icons render`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val manifest = Json.parseToJsonElement(client.get("/manifest.webmanifest").bodyAsText()).jsonObject
            assertThat(manifest["display"]!!.jsonPrimitive.content).isEqualTo("standalone")
            val icons = manifest["icons"]!!.jsonArray.map { it.jsonObject["src"]!!.jsonPrimitive.content }
            assertThat(icons).containsAtLeast("/icons/icon-192.png", "/icons/icon-512.png", "/icons/maskable-512.png")
            // (path, size, zoom): "any" icons zoom the launcher art 1.3×, maskable ones keep its safe zone.
            for ((path, size, zoom) in listOf(
                Triple("/icons/icon-192.png", 192, 1.3),
                Triple("/icons/maskable-512.png", 512, 1.0),
                Triple("/apple-touch-icon.png", 180, 1.0),
            )) {
                val response = client.get(path)
                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                val image = ImageIO.read(ByteArrayInputStream(response.readRawBytes()))
                assertThat(image.width).isEqualTo(size)
                // Opaque ocean in the corner; bronze on the shield at (48, 32) of the 108-unit art, beside the trident.
                assertThat(image.getRGB(1, 1) ushr 24).isEqualTo(0xFF)
                fun at(unit: Double) = ((54 + (unit - 54) * zoom) / 108 * size).toInt()
                val bronze = image.getRGB(at(48.0), at(32.0))
                assertThat((bronze shr 16) and 0xFF).isGreaterThan(0xA0)
                assertThat(bronze and 0xFF).isLessThan(0xA0)
            }
        }
    }

    @Test
    fun `the app page offers the uploaded APK, or says there is none`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val token = plainClient().signIn()
            val empty = client.get("/app") { header(HttpHeaders.Cookie, cookie(token)) }.bodyAsText()
            assertThat(empty).contains("No app uploaded yet")
            Files.createDirectories(hub.paths.apk.parent)
            Files.write(hub.paths.apk, ByteArray(2048) { 1 })
            val page = client.get("/app") { header(HttpHeaders.Cookie, cookie(token)) }.bodyAsText()
            assertThat(page).contains("Download the app")
            assertThat(page).contains("0.0 MB")
            val download = client.get("/app/goa.apk") { header(HttpHeaders.Cookie, cookie(token)) }
            assertThat(download.headers[HttpHeaders.ContentType]).isEqualTo("application/vnd.android.package-archive")
            assertThat(download.headers[HttpHeaders.ContentDisposition]).contains("goa.apk")
            assertThat(download.readRawBytes()).hasLength(2048)
            assertThat(client.get("/app/goa.apk").status).isEqualTo(HttpStatusCode.Unauthorized)
        }
    }

    @Test
    fun `device slots are typed, per device and survive a reload`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            plainClient().signIn()
            plainClient().signIn()
            val (a, b) = hub.devices.list().map { it.id }
            val slot = DeviceSlot("timer", ListSerializer(Int.serializer()))
            hub.deviceStore.write(a, slot, listOf(1, 2))
            assertThat(hub.deviceStore.read(a, slot)).containsExactly(1, 2).inOrder()
            assertThat(hub.deviceStore.read(b, slot)).isNull()
            assertThat(hub.deviceStore.read(a, "timer", slot.serializer)).containsExactly(1, 2)
            assertThat(hub.devicesFileExists(a, "timer")).isTrue()
            hub.devices.signOut(a)
            assertThat(hub.deviceStore.read(a, slot)).isNull()
        }
        assertThat(HUB_URL).startsWith("https://")
    }

    private fun dev.quinton.goa.hub.Hub.devicesFileExists(id: String, slot: String) =
        Files.isRegularFile(paths.devices.resolve(id).resolve("$slot.json"))
}
