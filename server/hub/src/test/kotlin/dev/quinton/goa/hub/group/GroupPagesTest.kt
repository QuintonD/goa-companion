package dev.quinton.goa.hub.group

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.TestGroup
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.match
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.player
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.testHub
import dev.quinton.goa.hub.web.SignInSlots
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Group on the web (ADR-030 §8, ADR-031 §4): who am I, the invite, signed-in browsers. */
class GroupPagesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock()

    private fun hubWithPlayers(): Hub = testHub(tmp.root.toPath(), clock).also { hub ->
        runBlocking {
            listOf("ann" to "Ann", "bob" to "Bob", "cid" to "Cid", "robert" to "Robert").forEach { (id, name) ->
                hub.groupStore.upsertPlayer(player(id, name))
            }
        }
    }

    private class Browser(val client: HttpClient, val token: String) {
        suspend fun get(path: String): HttpResponse = client.get(path) { header(HttpHeaders.Cookie, cookie(token)) }

        suspend fun post(path: String, vararg fields: Pair<String, String>, origin: String? = HUB_URL): HttpResponse =
            client.submitForm(path, parameters { fields.forEach { (k, v) -> append(k, v) } }) {
                header(HttpHeaders.Cookie, cookie(token))
                origin?.let { header(HttpHeaders.Origin, it) }
            }
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.browser(
        invite: String = GroupInvite.format(TestGroup, HUB_URL),
    ): Browser {
        val client = plainClient()
        return Browser(client, client.signIn(invite))
    }

    private fun Hub.deviceIdOf(browser: Browser) = devices.list().first {
        it.id ==
            dev.quinton.goa.hub.store.Devices.idOf(browser.token)
    }.id

    @Test
    fun `a personal invite asks You're Bob, and Yes makes this browser Bob`() {
        val hub = hubWithPlayers()
        hubTest(hub) {
            val b = browser(GroupInvite.format(TestGroup, HUB_URL, playerId = "bob"))
            val id = hub.deviceIdOf(b)
            val page = b.get("/group/me").bodyAsText()
            assertThat(page).contains("You're Bob?")
            assertThat(page).contains("Yes, I'm Bob")
            val yes = b.post("/group/me/invited", "answer" to "yes", "next" to "/group")
            assertThat(yes.status).isEqualTo(HttpStatusCode.Found)
            assertThat(yes.headers[HttpHeaders.Location]).isEqualTo("/group")
            assertThat(hub.deviceStore.read(id, SharedSlots.Me)).isEqualTo("bob")
            assertThat(hub.deviceStore.read(id, SignInSlots.InvitedPlayerId)).isNull()
            assertThat(b.get("/group").bodyAsText()).contains("You: Bob")
            assertThat(b.get("/group/me").bodyAsText()).doesNotContain("You're Bob?")
        }
    }

    @Test
    fun `Not me forgets the invite and shows the list`() {
        val hub = hubWithPlayers()
        hubTest(hub) {
            val b = browser(GroupInvite.format(TestGroup, HUB_URL, playerId = "bob"))
            val id = hub.deviceIdOf(b)
            val no = b.post("/group/me/invited", "answer" to "no", "next" to "/group")
            assertThat(no.headers[HttpHeaders.Location]).isEqualTo("/group/me")
            assertThat(hub.deviceStore.read(id, SignInSlots.InvitedPlayerId)).isNull()
            assertThat(hub.deviceStore.read(id, SharedSlots.Me)).isNull()
            val page = b.get("/group/me").bodyAsText()
            assertThat(page).doesNotContain("You're Bob?")
            assertThat(page).contains("Pick yourself")
            b.post("/group/me/choose", "player" to "cid", "next" to "/group")
            assertThat(hub.deviceStore.read(id, SharedSlots.Me)).isEqualTo("cid")
        }
    }

    @Test
    fun `an invited player missing from the data asks nothing, a merged-away one reads as who they became`() {
        val hub = hubWithPlayers()
        runBlocking {
            hub.groupStore.upsertPlayer(player("bob-2", "Bobby"))
            hub.groupStore.mergePlayers("bob-2", "bob")
        }
        hubTest(hub) {
            val ghost = browser(GroupInvite.format(TestGroup, HUB_URL, playerId = "ghost"))
            val page = ghost.get("/group/me").bodyAsText()
            assertThat(page).doesNotContain("You're")
            assertThat(page).contains("Robert")
            // Yes without anyone to be is a no-op.
            ghost.post("/group/me/invited", "answer" to "yes")
            assertThat(hub.deviceStore.read(hub.deviceIdOf(ghost), SharedSlots.Me)).isNull()

            val merged = browser(GroupInvite.format(TestGroup, HUB_URL, playerId = "bob-2"))
            assertThat(merged.get("/group/me").bodyAsText()).contains("You're Bob?")
        }
    }

    @Test
    fun `Yes on a browser that was someone else offers the merge, and refuses two people who played together`() {
        val hub = hubWithPlayers()
        runBlocking {
            hub.groupStore.upsertPlayer(player("quin", "Quin"))
            hub.groupStore.upsertMatch(match("solo", listOf("quin"), listOf("cid"), playedAt = 1))
        }
        hubTest(hub) {
            val b = browser(GroupInvite.format(TestGroup, HUB_URL, playerId = "bob"))
            val id = hub.deviceIdOf(b)
            hub.deviceStore.write(id, SharedSlots.Me, "quin")
            val yes = b.post("/group/me/invited", "answer" to "yes", "next" to "/group")
            assertThat(yes.headers[HttpHeaders.Location]).isEqualTo("/group/me/merge?next=%2Fgroup")
            assertThat(hub.deviceStore.read(id, SharedSlots.Me)).isEqualTo("bob")
            val offer = b.get("/group/me/merge?next=%2Fgroup").bodyAsText()
            assertThat(offer).contains("Merge your old player into Bob?")
            assertThat(offer).contains("Before, this browser was “Quin” (1 match)")
            b.post("/group/me/merge", "next" to "/group")
            assertThat(PlayerMerges(hub.groupStore.read().playerMerges).resolve("quin")).isEqualTo("bob")

            // Another browser: its old player sat at the same table as the invited one.
            runBlocking {
                hub.groupStore.upsertPlayer(player("dee", "Dee"))
                hub.groupStore.upsertMatch(match("together", listOf("dee", "ann"), listOf("cid"), playedAt = 2))
            }
            val c = browser(GroupInvite.format(TestGroup, HUB_URL, playerId = "ann"))
            hub.deviceStore.write(hub.deviceIdOf(c), SharedSlots.Me, "dee")
            c.post("/group/me/invited", "answer" to "yes")
            val refused = c.post("/group/me/merge", "next" to "/group")
            val text = refused.bodyAsText()
            assertThat(text).contains("Can't merge")
            assertThat(text).contains("this browser is Ann now.")
            assertThat(text).contains("1970-01-01 · Dee, Ann vs Cid")
            assertThat(PlayerMerges(hub.groupStore.read().playerMerges).resolve("dee")).isEqualTo("dee")
            // A merge nobody was offered is ignored.
            c.post("/group/me/merge", "next" to "/group")
            assertThat(PlayerMerges(hub.groupStore.read().playerMerges).resolve("dee")).isEqualTo("dee")
        }
    }

    @Test
    fun `Add me asks Is one of these you first, and never adds a taken name`() {
        val hub = hubWithPlayers()
        runBlocking {
            hub.groupStore.upsertPlayer(player("old", "Zed"))
            hub.groupStore.archivePlayer("old", archived = true)
        }
        hubTest(hub) {
            val b = browser()
            val id = hub.deviceIdOf(b)
            val blank = b.post("/group/me/add", "name" to "  ")
            assertThat(blank.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
            assertThat(blank.bodyAsText()).contains("Enter your name")

            val similar = b.post("/group/me/add", "name" to "Roberto").bodyAsText()
            assertThat(similar).contains("Is one of these you?")
            assertThat(similar).contains("No, add Roberto")
            assertThat(hub.groupStore.data().players().map { it.name }).doesNotContain("Roberto")

            val taken = b.post("/group/me/add", "name" to "robert", "confirmed" to "true").bodyAsText()
            assertThat(taken).contains("“robert” is already in the group")
            assertThat(taken).doesNotContain("No, add robert")
            assertThat(hub.groupStore.data().players()).hasSize(4)

            val archived = b.post("/group/me/add", "name" to "zed")
            assertThat(archived.bodyAsText()).contains("“Zed” is already in the group. If that's you, pick it above.")

            val added = b.post("/group/me/add", "name" to "Roberto", "confirmed" to "true", "next" to "/heroes")
            assertThat(added.headers[HttpHeaders.Location]).isEqualTo("/heroes")
            val me = hub.groupStore.data().players().single { it.name == "Roberto" }
            assertThat(hub.deviceStore.read(id, SharedSlots.Me)).isEqualTo(me.id)
            // Stamped like the phone's repository, with the first colour nobody uses.
            assertThat(me.updatedAt).isEqualTo(clock.nowMillis())
            assertThat(me.color).isNotEqualTo(0xFF3FC1B0)
        }
    }

    @Test
    fun `next only goes to a path on this hub`() {
        assertThat(GroupFeature.safeNext("/players/ann")).isEqualTo("/players/ann")
        assertThat(GroupFeature.safeNext("//evil.example")).isEqualTo("/group")
        assertThat(GroupFeature.safeNext("https://evil.example")).isEqualTo("/group")
        assertThat(GroupFeature.safeNext("/\\evil.example")).isEqualTo("/group")
        assertThat(GroupFeature.safeNext(null)).isEqualTo("/group")
    }

    @Test
    fun `the group page lists signed-in browsers and phones, and any browser can be signed out`() {
        val hub = hubWithPlayers()
        hub.syncState.record("p1", "Galaxy S25 Ultra", clock.nowMillis() - 5 * 60_000)
        hubTest(hub) {
            val mine = browser()
            val other = browser()
            val otherId = hub.deviceIdOf(other)
            val page = mine.get("/group").bodyAsText()
            assertThat(page).contains("Table Night")
            assertThat(page).contains("This browser")
            assertThat(page).contains("Galaxy S25 Ultra")
            assertThat(page).contains("5 min ago")
            assertThat(page).contains("/group/devices/$otherId/sign-out")
            assertThat(page).contains("href=\"/app\"")

            assertThat(mine.get("/group/devices/$otherId/sign-out").bodyAsText()).contains("Sign out")
            val out = mine.post("/group/devices/$otherId/sign-out")
            assertThat(out.headers[HttpHeaders.Location]).isEqualTo("/group")
            assertThat(other.get("/group").status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(mine.get("/group").status).isEqualTo(HttpStatusCode.OK)

            val self = mine.post("/group/devices/${hub.deviceIdOf(mine)}/sign-out")
            assertThat(self.headers[HttpHeaders.Location]).isEqualTo("/")
            assertThat(self.headers.getAll(HttpHeaders.SetCookie)!!.single()).startsWith("goa_device=; Max-Age=0")
            val after = mine.get("/")
            assertThat(after.status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(after.bodyAsText()).contains("Scan the group's invite QR code to sign in.")
            assertThat(hub.devices.list()).isEmpty()
        }
    }

    @Test
    fun `the invite QR decodes to exactly the invite link, personal one included`() {
        val hub = hubWithPlayers()
        hubTest(hub) {
            val b = browser()
            val chooser = b.get("/group/invite").bodyAsText()
            assertThat(chooser).contains("Who's the invite for?")
            assertThat(chooser).contains("/group/invite?for=anyone")
            assertThat(chooser).contains("/group/invite?player=bob")

            val forBob = b.get("/group/invite?player=bob").bodyAsText()
            val expected = GroupInvite.format(TestGroup, HUB_URL, "bob")
            assertThat(expected).contains("&p=bob")
            assertThat(decodeQr(forBob)).isEqualTo(expected)
            assertThat(forBob).contains("value=\"${expected.replace("&", "&amp;")}\"")
            assertThat(forBob).contains("Invite Bob")
            assertThat(forBob).contains("For Bob: ")
            assertThat(forBob).contains("Only browsers already signed in to the group can see this page.")

            val anyone = b.get("/group/invite?for=anyone").bodyAsText()
            assertThat(decodeQr(anyone)).isEqualTo(GroupInvite.format(TestGroup, HUB_URL))
            // A player who is gone: back to the chooser.
            assertThat(b.get("/group/invite?player=ghost").bodyAsText()).contains("Who's the invite for?")
        }
    }

    @Test
    fun `the invite and every group page refuse a signed-out browser`() {
        val hub = hubWithPlayers()
        hubTest(hub) {
            val response = plainClient().get("/group/invite?player=bob")
            assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
            val body = response.bodyAsText()
            assertThat(body).doesNotContain("goa-sync")
            assertThat(body).doesNotContain("<svg")
            assertThat(plainClient().get("/group").status).isEqualTo(HttpStatusCode.Unauthorized)
        }
    }

    @Test
    fun `state changes need the hub's own origin`() {
        val hub = hubWithPlayers()
        hubTest(hub) {
            val b = browser()
            val id = hub.deviceIdOf(b)
            val forged = b.post("/group/me/choose", "player" to "ann", origin = "https://evil.example")
            assertThat(forged.status).isEqualTo(HttpStatusCode.Forbidden)
            val none = b.post("/group/me/choose", "player" to "ann", origin = null)
            assertThat(none.status).isEqualTo(HttpStatusCode.Forbidden)
            assertThat(hub.deviceStore.read(id, SharedSlots.Me)).isNull()
            val other = browser()
            b.post("/group/devices/${hub.deviceIdOf(other)}/sign-out", origin = "https://evil.example")
            assertThat(other.get("/group").status).isEqualTo(HttpStatusCode.OK)
        }
    }

    @Test
    fun `the page markup has no inline script or style`() {
        val hub = hubWithPlayers()
        hubTest(hub) {
            val b = browser(GroupInvite.format(TestGroup, HUB_URL, playerId = "bob"))
            listOf("/group", "/group/me", "/group/invite", "/group/invite?player=bob").forEach { path ->
                val html = b.get(path).bodyAsText()
                assertThat(html).doesNotContain("style=")
                assertThat(html).doesNotContain("onclick")
                assertThat(Regex("<script(?![^>]*src=)").containsMatchIn(html)).isFalse()
            }
        }
    }

    /** Rasterises the page's QR SVG (our own `M x y h n v1 h-n z` runs) and reads it with ZXing. */
    private fun decodeQr(html: String): String {
        val svg =
            Regex(
                "<svg[^>]*class=\"qr-code\"[^>]*viewBox=\"0 0 (\\d+) (\\d+)\"[^>]*>" +
                    ".*?<path fill=\"#000\" d=\"([^\"]*)\"",
            )
                .find(html) ?: error("No QR code in the page")
        val (w, h, d) = svg.destructured
        val width = w.toInt()
        val height = h.toInt()
        val dark = Array(height) { BooleanArray(width) }
        Regex("M(\\d+) (\\d+)h(\\d+)v1h-\\d+z").findAll(d).forEach { m ->
            val (x, y, n) = m.destructured
            for (i in 0 until n.toInt()) dark[y.toInt()][x.toInt() + i] = true
        }
        val scale = 4
        val border = 4 * scale
        val size = width * scale + 2 * border
        val pixels = IntArray(size * size) { 0xFFFFFFFF.toInt() }
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!dark[y][x]) continue
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        pixels[(border + y * scale + dy) * size + border + x * scale + dx] = 0xFF000000.toInt()
                    }
                }
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to false)).text
    }
}
