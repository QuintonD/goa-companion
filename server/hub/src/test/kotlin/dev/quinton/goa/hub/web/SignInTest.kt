package dev.quinton.goa.hub.web

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.OtherGroup
import dev.quinton.goa.hub.TestGroup
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.store.Devices
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.testHub
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Web sign-in, the signed-in area and the request rules (ADR-030 §7). */
class SignInTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock()

    private suspend fun HttpClient.join(
        invite: String,
        origin: String? = HUB_URL,
        forwardedFor: String? = null,
        json: Boolean = false,
    ): HttpResponse = submitForm("/join", parameters { append("invite", invite) }) {
        origin?.let { header(HttpHeaders.Origin, it) }
        forwardedFor?.let { header(HttpHeaders.XForwardedFor, it) }
        if (json) header(HttpHeaders.Accept, "application/json")
    }

    private val goodInvite = GroupInvite.format(TestGroup, HUB_URL)
    private val wrongKey = GroupInvite.format(GroupCredentials(TestGroup.groupId, ByteArray(32) { 9 }, TestGroup.name))

    @Test
    fun `joining with the group's invite sets the device cookie and asks which player you are`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            val response = client.join(goodInvite)
            assertThat(response.status).isEqualTo(HttpStatusCode.Found)
            // A new browser doesn't know its player yet: "Which player are you?" once (ADR-031).
            assertThat(response.headers[HttpHeaders.Location]).isEqualTo(WHO_ARE_YOU_PATH)
            val setCookie = response.headers.getAll(HttpHeaders.SetCookie)!!.single()
            assertThat(
                setCookie,
            ).matches("goa_device=[A-Za-z0-9_-]{43}; Max-Age=34560000; Path=/; Secure; HttpOnly; SameSite=Lax")
            val token = setCookie.substringAfter("=").substringBefore(";")
            val device = hub.devices.list().single()
            assertThat(device.id).isEqualTo(Devices.idOf(token))
            // The data directory holds the hash, never the cookie itself.
            assertThat(
                tmp.root.walk().none {
                    it.name.contains(token) || it.readTextOrEmpty().contains(token)
                },
            ).isTrue()

            val page = client.get("/heroes") { header(HttpHeaders.Cookie, cookie(token)) }
            assertThat(page.status).isEqualTo(HttpStatusCode.OK)
        }
    }

    @Test
    fun `join js gets JSON, and a personal invite remembers the player for the Group page`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val response = plainClient().join(GroupInvite.format(TestGroup, HUB_URL, playerId = "ann"), json = true)
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo("""{"redirect":"$WHO_ARE_YOU_PATH"}""")
            val device = hub.devices.list().single()
            assertThat(hub.deviceStore.read(device.id, SignInSlots.InvitedPlayerId)).isEqualTo("ann")
        }
    }

    @Test
    fun `a browser that knows its player lands on the app, unless a personal invite names someone else`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            val token = client.signIn()
            val device = hub.devices.list().single()
            hub.deviceStore.write(device.id, SharedSlots.Me, "ann")
            suspend fun again(invite: String) = client.submitForm("/join", parameters { append("invite", invite) }) {
                header(HttpHeaders.Origin, HUB_URL)
                header(HttpHeaders.Cookie, cookie(token))
            }.headers[HttpHeaders.Location]
            assertThat(again(goodInvite)).isEqualTo("/")
            assertThat(again(GroupInvite.format(TestGroup, HUB_URL, playerId = "ann"))).isEqualTo("/")
            assertThat(again(GroupInvite.format(TestGroup, HUB_URL, playerId = "bob"))).isEqualTo(WHO_ARE_YOU_PATH)
        }
    }

    @Test
    fun `a wrong key, another group or garbage is refused without a cookie`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            val wrong = client.join(wrongKey, json = true)
            assertThat(wrong.status).isEqualTo(HttpStatusCode.Forbidden)
            assertThat(wrong.headers[HttpHeaders.SetCookie]).isNull()
            assertThat(client.join(GroupInvite.format(OtherGroup)).status).isEqualTo(HttpStatusCode.Forbidden)
            val garbage = client.join("hello")
            assertThat(garbage.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(garbage.bodyAsText()).contains("a Guards of Atlantis group invite")
            assertThat(hub.devices.list()).isEmpty()
        }
    }

    @Test
    fun `pages, assets and the API need a signed-in device`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            val page = client.get("/heroes")
            assertThat(page.status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(page.bodyAsText()).contains("invite QR code to sign in.")
            assertThat(client.get("/").status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(client.get("/assets/catalog/index.json").status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(
                client.get("/assets/official/cards/arien-gold.webp").status,
            ).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(client.get("/api/v1/push/key").status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(client.get("/heroes") { header(HttpHeaders.Cookie, cookie("x".repeat(43))) }.status)
                .isEqualTo(HttpStatusCode.Unauthorized)

            val token = client.signIn()
            val asset = client.get("/assets/official/cards/arien-gold.webp") {
                header(HttpHeaders.Cookie, cookie(token))
            }
            assertThat(asset.status).isEqualTo(HttpStatusCode.OK)
            assertThat(asset.headers[HttpHeaders.ContentType]).isEqualTo("image/webp")
            assertThat(client.get("/assets/../hub.json") { header(HttpHeaders.Cookie, cookie(token)) }.status)
                .isEqualTo(HttpStatusCode.NotFound)
            val home = client.get("/") { header(HttpHeaders.Cookie, cookie(token)) }
            assertThat(home.headers[HttpHeaders.Location]).isEqualTo("/heroes")
        }
    }

    @Test
    fun `a signed-out device's cookie stops working`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            val token = client.signIn()
            val out = client.post("/signout") {
                header(HttpHeaders.Cookie, cookie(token))
                header(HttpHeaders.Origin, HUB_URL)
            }
            assertThat(out.status).isEqualTo(HttpStatusCode.Found)
            assertThat(hub.devices.list()).isEmpty()
            assertThat(
                client.get("/heroes") {
                    header(HttpHeaders.Cookie, cookie(token))
                }.status,
            ).isEqualTo(HttpStatusCode.Unauthorized)
        }
    }

    @Test
    fun `a request still running when its device is signed out can't bring it back`() {
        val hub = testHub(tmp.root.toPath(), clock)
        val (device, token) = hub.devices.create("Lost phone", clock.nowMillis())
        // The lost phone's request authenticated just before another browser signed it out…
        hub.devices.signOut(device.id)
        // …and then records "last seen", renames, or writes one of its slots.
        hub.devices.touch(device, clock.nowMillis() + 3_600_000)
        hub.devices.rename(device.id, "Still here")
        val slotWrite = runCatching { hub.deviceStore.write(device.id, SharedSlots.Me, "ann") }
        assertThat(slotWrite.isFailure).isTrue()
        assertThat(hub.devices.find(token)).isNull()
        assertThat(hub.devices.list()).isEmpty()
        assertThat(tmp.root.resolve("devices").listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `state-changing requests must come from the hub's own origin`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            val token = client.signIn()
            val evil = client.post("/signout") {
                header(HttpHeaders.Cookie, cookie(token))
                header(HttpHeaders.Origin, "https://evil.example")
            }
            assertThat(evil.status).isEqualTo(HttpStatusCode.Forbidden)
            val none = client.post("/signout") { header(HttpHeaders.Cookie, cookie(token)) }
            assertThat(none.status).isEqualTo(HttpStatusCode.Forbidden)
            // The request's own Host doesn't count once a public URL is configured.
            val hostOnly = client.post("/signout") {
                header(HttpHeaders.Cookie, cookie(token))
                header(HttpHeaders.Origin, "http://localhost")
            }
            assertThat(hostOnly.status).isEqualTo(HttpStatusCode.Forbidden)
            assertThat(
                client.join(goodInvite, origin = "https://evil.example").status,
            ).isEqualTo(HttpStatusCode.Forbidden)
            assertThat(hub.devices.list()).hasSize(1)
            val sameOrigin = client.post("/signout") {
                header(HttpHeaders.Cookie, cookie(token))
                header("Sec-Fetch-Site", "same-origin")
            }
            assertThat(sameOrigin.status).isEqualTo(HttpStatusCode.Found)
        }
    }

    @Test
    fun `without a public URL the Origin host must match the Host header`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hub.config.update { it.copy(publicUrl = null) }
        hubTest(hub) {
            val client = plainClient()
            assertThat(
                client.join(goodInvite, origin = "http://evil.example").status,
            ).isEqualTo(HttpStatusCode.Forbidden)
            assertThat(client.join(goodInvite, origin = "http://localhost").status).isEqualTo(HttpStatusCode.Found)
        }
    }

    @Test
    fun `every response carries noindex and the strict CSP`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            for (path in listOf("/heroes", "/join", "/static/app.css", "/no-such-page", "/api/v1/health")) {
                val response = client.get(path)
                assertThat(response.headers["X-Robots-Tag"]).isEqualTo("noindex, nofollow")
                assertThat(response.headers["Content-Security-Policy"]).isEqualTo(CSP)
                assertThat(response.headers["X-Frame-Options"]).isEqualTo("DENY")
                assertThat(response.headers["Referrer-Policy"]).isEqualTo("same-origin")
            }
            assertThat(CSP).doesNotContain("unsafe")
            assertThat(client.get("/join").headers[HttpHeaders.StrictTransportSecurity]).isNotNull()
        }
    }

    @Test
    fun `without a group there is no join page from any address`() {
        val hub = testHub(tmp.root.toPath(), clock, joined = false)
        hubTest(hub) {
            val client = plainClient()
            for (forwarded in listOf(null, "127.0.0.1", "203.0.113.9")) {
                for (path in listOf("/", "/join", "/heroes", "/setup")) {
                    val response = client.get(path) { forwarded?.let { header(HttpHeaders.XForwardedFor, it) } }
                    val body = response.bodyAsText()
                    assertThat(body).doesNotContain("<form")
                    assertThat(body).doesNotContain("<textarea")
                    if (path != "/setup") {
                        assertThat(response.status).isEqualTo(HttpStatusCode.ServiceUnavailable)
                        assertThat(body).contains("run goa-hub join on the server")
                    }
                }
                val post = client.join(goodInvite, origin = "http://localhost", forwardedFor = forwarded)
                assertThat(post.status).isEqualTo(HttpStatusCode.ServiceUnavailable)
                assertThat(
                    client.post("/setup") {
                        header(HttpHeaders.Origin, HUB_URL)
                    }.status,
                ).isNotEqualTo(HttpStatusCode.OK)
            }
            assertThat(hub.membership.group()).isNull()
            assertThat(hub.devices.list()).isEmpty()
        }
    }

    @Test
    fun `failed joins are throttled for everyone together when the proxy names no client`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            repeat(JoinThrottle.UNKNOWN_CLIENTS) {
                assertThat(client.join(wrongKey).status).isEqualTo(HttpStatusCode.Forbidden)
            }
            assertThat(client.join(wrongKey).status).isEqualTo(HttpStatusCode.TooManyRequests)
            assertThat(client.join("garbage").status).isEqualTo(HttpStatusCode.TooManyRequests)
            clock.advance(JoinThrottle.LOCKOUT_MS + 1)
            assertThat(client.join(wrongKey).status).isEqualTo(HttpStatusCode.Forbidden)
        }
    }

    @Test
    fun `successful joins never count, so a whole table can sign in`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            repeat(JoinThrottle.OVERALL + 5) {
                assertThat(client.join(goodInvite).status).isEqualTo(HttpStatusCode.Found)
            }
            assertThat(hub.devices.list()).hasSize(JoinThrottle.OVERALL + 5)
        }
    }

    @Test
    fun `behind a proxy each client has its own budget, and forging addresses hits the overall cap`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            repeat(JoinThrottle.PER_CLIENT) { client.join(wrongKey, forwardedFor = "198.51.100.1") }
            assertThat(
                client.join(wrongKey, forwardedFor = "198.51.100.1").status,
            ).isEqualTo(HttpStatusCode.TooManyRequests)
            // A client's own X-Forwarded-For is ignored: the proxy's last hop is the address.
            assertThat(
                client.join(wrongKey, forwardedFor = "10.9.9.9, 198.51.100.1").status,
            ).isEqualTo(HttpStatusCode.TooManyRequests)
            assertThat(client.join(goodInvite, forwardedFor = "198.51.100.2").status).isEqualTo(HttpStatusCode.Found)

            repeat(JoinThrottle.OVERALL) { i -> client.join(wrongKey, forwardedFor = "192.0.2.$i") }
            assertThat(
                client.join(wrongKey, forwardedFor = "198.51.100.3").status,
            ).isEqualTo(HttpStatusCode.TooManyRequests)
        }
    }

    @Test
    fun `nobody can lock the group out, the right invite gets in while the throttle is closed`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            val client = plainClient()
            // A stranger floods /join with junk from rotating addresses and without any.
            repeat(JoinThrottle.OVERALL) { i -> client.join(wrongKey, forwardedFor = "192.0.2.$i") }
            repeat(JoinThrottle.UNKNOWN_CLIENTS) { client.join(wrongKey) }
            assertThat(client.join(wrongKey).status).isEqualTo(HttpStatusCode.TooManyRequests)
            assertThat(client.join(wrongKey, forwardedFor = "198.51.100.9").status)
                .isEqualTo(HttpStatusCode.TooManyRequests)

            assertThat(client.join(goodInvite).status).isEqualTo(HttpStatusCode.Found)
            assertThat(client.join(goodInvite, forwardedFor = "198.51.100.9").status).isEqualTo(HttpStatusCode.Found)
            assertThat(hub.devices.list()).hasSize(2)
        }
    }

    @Test
    fun `assetlinks lists the configured certificate for the Android app`() {
        val hub = testHub(tmp.root.toPath(), clock)
        hubTest(hub) {
            assertThat(client.get("/.well-known/assetlinks.json").bodyAsText()).isEqualTo("[]")
            hub.config.update { it.copy(androidCertSha256 = listOf("ab".repeat(32))) }
            val body = client.get("/.well-known/assetlinks.json").bodyAsText()
            assertThat(body).contains("\"package_name\":\"dev.quinton.goa\"")
            assertThat(body).contains(List(32) { "AB" }.joinToString(":"))
            assertThat(body).contains("delegate_permission/common.handle_all_urls")
        }
    }
}

private fun java.io.File.readTextOrEmpty(): String = if (isFile) readText() else ""
