package dev.quinton.goa.hub.web

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeature
import dev.quinton.goa.hub.feature.HubFeatures
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.testHub
import dev.quinton.goa.hub.web.ui.IconRenderer
import dev.quinton.goa.hub.web.ui.StaticFiles
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What a phone on a data plan downloads: gzip for text, caching for good for versioned files, small
 * portraits where small ones show, launch screens for the home-screen app, and the app's own pages
 * for "not found" and "something went wrong".
 */
class DeliveryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock()

    @Test
    fun `pages, styles, scripts and fonts are gzipped, but never a page that holds the group key`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val token = plainClient().signIn()
            suspend fun get(path: String) = client.get(path) {
                header(HttpHeaders.Cookie, cookie(token))
                header(HttpHeaders.AcceptEncoding, "gzip, deflate, br")
            }
            for (path in listOf(
                "/heroes",
                "/players",
                StaticFiles.url("app.css"),
                StaticFiles.url("htmx.min.js"),
                "/static/fonts/inter_regular.ttf",
            )) {
                val response = get(path)
                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertWithMessage(path).that(response.headers[HttpHeaders.ContentEncoding]).isEqualTo("gzip")
                val plain = GZIPInputStream(ByteArrayInputStream(response.readRawBytes())).readBytes()
                assertThat(plain.size).isGreaterThan(response.readRawBytes().size)
            }
            assertThat(String(GZIPInputStream(ByteArrayInputStream(get("/heroes").readRawBytes())).readBytes()))
                .contains("tab-bar")
            // The invite pages show the key (QR code, link): their length must not depend on it.
            for (path in listOf("/group/invite", "/group/invite?player=nobody", "/join")) {
                assertThat(get(path).headers[HttpHeaders.ContentEncoding]).isNull()
            }
            // Images are compressed already.
            val portrait = hub.catalog.catalog.heroes.first { it.portrait != null }.portrait!!
            assertThat(get(HubCatalog.url(portrait)).headers[HttpHeaders.ContentEncoding]).isNull()
        }
    }

    @Test
    fun `versioned static files are cached for good, and stylesheets version what they reference`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val css = client.get(StaticFiles.url("app.css"))
            assertThat(css.headers[HttpHeaders.CacheControl]).isEqualTo("public, max-age=31536000, immutable")
            val text = css.bodyAsText()
            val fontVersion = StaticFiles.versionOf("fonts/inter_regular.ttf")
            assertThat(text).contains("url(/static/fonts/inter_regular.ttf?v=$fontVersion)")
            assertThat(text).contains("url(/static/material/settings.svg?v=")
            assertThat(Regex("""url\(/static/[^)?]+\)""").find(text)).isNull()
            // The same file under an old version: today's file, briefly cached.
            val stale = client.get("/static/app.css?v=0000")
            assertThat(stale.status).isEqualTo(HttpStatusCode.OK)
            assertThat(stale.headers[HttpHeaders.CacheControl]).isEqualTo("public, max-age=3600")
            val font = client.get("/static/fonts/inter_regular.ttf?v=$fontVersion")
            assertThat(font.headers[HttpHeaders.CacheControl]).contains("immutable")
            // The service worker keeps versioned static files only (never pages, data or the invite).
            val sw = client.get("/sw.js").bodyAsText()
            assertThat(sw).contains("url.pathname.indexOf(\"/static/\") !== 0 || !url.searchParams.has(\"v\")")
        }
    }

    @Test
    fun `lists and avatars get small portraits, the grid a lighter copy, the hero page the full image`() {
        val hub = testHub(tmp.root.toPath(), clock)
        val heroes = hub.catalog.catalog.heroes.filter { it.portrait != null }
        for (hero in heroes) {
            val full = hub.catalog.open(hero.portrait!!)!!.use { it.readBytes() }
            for (width in listOf(HubCatalog.THUMB_SMALL, HubCatalog.THUMB_GRID)) {
                val thumb = hub.catalog.open("thumbs/w$width/${hero.portrait}")
                assertThat(thumb).isNotNull()
                val bytes = thumb!!.use { it.readBytes() }
                assertThat(String(bytes, 0, 4)).isEqualTo("RIFF")
                assertThat(bytes.size).isLessThan(full.size)
            }
        }
        hubTest(hub) {
            val token = plainClient().signIn()
            suspend fun page(path: String) = client.get(path) { header(HttpHeaders.Cookie, cookie(token)) }.bodyAsText()
            val hero = heroes.first()
            val small = HubCatalog.url("thumbs/w${HubCatalog.THUMB_SMALL}/${hero.portrait}")
            val grid = HubCatalog.url("thumbs/w${HubCatalog.THUMB_GRID}/${hero.portrait}")
            val full = HubCatalog.url(hero.portrait!!)
            assertThat(page("/heroes")).contains("src=\"$grid\" srcset=\"$grid 384w, $full 512w\"")
            assertThat(page("/heroes/${hero.id}")).contains("src=\"$full\"")
            assertThat(page("/trivia")).contains(small)
            // The thumbnails are served like the other assets (signed-in browsers only).
            assertThat(client.get(small) { header(HttpHeaders.Cookie, cookie(token)) }.status)
                .isEqualTo(HttpStatusCode.OK)
            assertThat(client.get(small).status).isEqualTo(HttpStatusCode.Unauthorized)
        }
    }

    @Test
    fun `the home-screen app has a launch screen for each iPhone size and a sized touch icon`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val token = plainClient().signIn()
            val html = client.get("/heroes") { header(HttpHeaders.Cookie, cookie(token)) }.bodyAsText()
            assertThat(html)
                .contains("<link href=\"/apple-touch-icon.png\" rel=\"apple-touch-icon\" sizes=\"180x180\">")
            assertThat(html).contains("viewport-fit=cover")
            for (splash in IconRenderer.Splash.entries) {
                assertThat(html)
                    .contains("/icons/${splash.fileName}\" rel=\"apple-touch-startup-image\" media=\"${splash.media}\"")
            }
            val splash = IconRenderer.Splash.IPHONE_12
            val response = client.get("/icons/${splash.fileName}")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val image = ImageIO.read(ByteArrayInputStream(response.readRawBytes()))
            assertThat(image.width).isEqualTo(1170)
            assertThat(image.height).isEqualTo(2532)
            assertThat(image.getRGB(0, 0) and 0xFFFFFF).isEqualTo(0x0B1622)
            // The mark, half the width, centred: bronze on its shield at (48, 32) of the 108-unit art.
            val bronze = image.getRGB(292 + 48 * 585 / 108, 973 + 32 * 585 / 108)
            assertThat((bronze shr 16) and 0xFF).isGreaterThan(0xA0)
            assertThat(client.get("/icons/splash-1x1.png").status).isEqualTo(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `not found is the app's own page, with the tabs when signed in`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val signedOut = plainClient().get("/nowhere")
            assertThat(signedOut.status).isEqualTo(HttpStatusCode.NotFound)
            assertThat(signedOut.bodyAsText()).contains("Page not found")
            val token = plainClient().signIn()
            val page = client.get("/nowhere") { header(HttpHeaders.Cookie, cookie(token)) }
            assertThat(page.status).isEqualTo(HttpStatusCode.NotFound)
            val html = page.bodyAsText()
            assertThat(html).contains("<h1 class=\"top-title\">Page not found</h1>")
            assertThat(html).contains("status-state")
            assertThat(html).contains("href=\"/heroes\"")
            assertThat(html).contains("data-history-back")
            assertThat(html).contains("tab-bar")
            assertThat(client.get("/api/v1/nowhere").bodyAsText()).isEqualTo("Not found.")
        }
    }

    @Test
    fun `a failing page is the app's own error page with Try again, never a stack trace`() {
        val hub = testHub(tmp.root.toPath(), clock)
        val broken = object : HubFeature {
            override fun Route.routes(ctx: HubContext) {
                get("/broken") { error("secret detail") }
            }
        }
        testApplication {
            application { hubModule(hub, HubFeatures + broken) }
            val c = createClient { followRedirects = false }
            val token = c.signIn()
            val page = c.get("/broken?x=1") { header(HttpHeaders.Cookie, cookie(token)) }
            assertThat(page.status).isEqualTo(HttpStatusCode.InternalServerError)
            val html = page.bodyAsText()
            assertThat(html).contains("Something went wrong")
            assertThat(html).contains("href=\"/broken?x=1\"")
            assertThat(html).contains("Try again")
            assertThat(html).contains("tab-bar")
            assertThat(html).doesNotContain("secret detail")
            val fragment = c.get("/broken") {
                header(HttpHeaders.Cookie, cookie(token))
                header("HX-Request", "true")
            }
            assertThat(fragment.status).isEqualTo(HttpStatusCode.InternalServerError)
            assertThat(fragment.bodyAsText()).doesNotContain("<html")
            val signedOut = c.get("/broken")
            assertThat(signedOut.status).isEqualTo(HttpStatusCode.Unauthorized)
        }
    }
}
