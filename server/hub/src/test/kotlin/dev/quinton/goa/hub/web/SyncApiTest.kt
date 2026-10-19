package dev.quinton.goa.hub.web

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.HubToken
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncJson
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.hub.OtherGroup
import dev.quinton.goa.hub.TestGroup
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.match
import dev.quinton.goa.hub.player
import dev.quinton.goa.hub.testHub
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The Android ↔ hub sync API (ADR-030 §4) with real sealed sync files. */
class SyncApiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val phoneA = SyncDocument(
        players = listOf(player("ann", "Ann"), player("bob", "Bob")),
        matches = listOf(match("m1", listOf("ann"), listOf("bob"), updatedAt = 10)),
    )
    private val phoneB = SyncDocument(
        players = listOf(player("ann", "Ann"), player("cid", "Cid")),
        matches = listOf(match("m2", listOf("cid"), listOf("ann"), updatedAt = 20)),
    )

    private suspend fun HttpClient.sync(
        doc: SyncDocument,
        group: GroupCredentials = TestGroup,
        token: String? = HubToken.derive(group),
        body: ByteArray = SyncCodec.encodeFile(doc, group, createdAt = 1),
    ): HttpResponse = post("/api/v1/sync") {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        contentType(ContentType.parse(SyncCodec.FILE_MIME_TYPE))
        setBody(body)
    }

    @Test
    fun `a phone's file is merged and the merged data comes back sealed`() {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            val first = client.sync(phoneA)
            assertThat(first.status).isEqualTo(HttpStatusCode.OK)
            assertThat(first.headers[HttpHeaders.ContentType]).startsWith(SyncCodec.FILE_MIME_TYPE)
            assertThat(SyncCodec.decodeFile(first.readRawBytes(), TestGroup)).isEqualTo(SyncMerge.normalize(phoneA))

            val second = client.sync(phoneB)
            val merged = SyncCodec.decodeFile(second.readRawBytes(), TestGroup)
            assertThat(merged).isEqualTo(SyncMerge.merge(phoneA, phoneB))
            assertThat(hub.groupStore.read()).isEqualTo(merged)
            // Idempotent: the same file again changes nothing.
            assertThat(SyncCodec.decodeFile(client.sync(phoneB).readRawBytes(), TestGroup)).isEqualTo(merged)
            assertThat(hub.syncState.read().lastSyncAt).isNotNull()
        }
    }

    @Test
    fun `a hero this hub's catalog doesn't know is stored and sent back as the phone logged it`() {
        val hub = testHub(tmp.root.toPath())
        val newer = phoneA.copy(
            matches = phoneA.matches.map { m ->
                m.copy(
                    participants = m.participants.mapIndexed { i, p ->
                        if (i ==
                            0
                        ) {
                            p.copy(heroId = "hero-from-2027")
                        } else {
                            p
                        }
                    },
                )
            },
        )
        hubTest(hub) {
            val reply = SyncCodec.decodeFile(client.sync(newer).readRawBytes(), TestGroup)
            assertThat(reply.matches.first().participants.first().heroId).isEqualTo("hero-from-2027")
            assertThat(hub.groupStore.read().matches.first().participants.first().heroId).isEqualTo("hero-from-2027")
        }
    }

    @Test
    fun `the phone's name is recorded when it gives one`() {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            client.post("/api/v1/sync") {
                header(HttpHeaders.Authorization, "Bearer ${HubToken.derive(TestGroup)}")
                header("X-Goa-Device-Id", "install-1")
                header("X-Goa-Device-Name", "Galaxy S25 Ultra")
                setBody(SyncCodec.encodeFile(phoneA, TestGroup, createdAt = 1))
            }
            assertThat(hub.syncState.read().clients.single().name).isEqualTo("Galaxy S25 Ultra")
            // The app sends the name percent-encoded (header values are ASCII); each phone is its own row.
            client.post("/api/v1/sync") {
                header(HttpHeaders.Authorization, "Bearer ${HubToken.derive(TestGroup)}")
                header("X-Goa-Device-Id", "install-2")
                header("X-Goa-Device-Name", "Zo%C3%AB%20Ann")
                setBody(SyncCodec.encodeFile(phoneA, TestGroup, createdAt = 1))
            }
            // Without a name a phone is "A phone", never its install id.
            client.post("/api/v1/sync") {
                header(HttpHeaders.Authorization, "Bearer ${HubToken.derive(TestGroup)}")
                header("X-Goa-Device-Id", "install-3")
                setBody(SyncCodec.encodeFile(phoneA, TestGroup, createdAt = 1))
            }
            assertThat(hub.syncState.read().clients.map { it.name })
                .containsExactly("A phone", "Zoë Ann", "Galaxy S25 Ultra").inOrder()
            assertThat(deviceName("100%")).isEqualTo("100%")
        }
    }

    @Test
    fun `a wrong token or another group's file is 401`() {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            assertThat(client.sync(phoneA, token = null).status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(client.sync(phoneA, token = "x".repeat(43)).status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(
                client.sync(phoneA, token = HubToken.derive(OtherGroup)).status,
            ).isEqualTo(HttpStatusCode.Unauthorized)
            // The right token, but a file sealed for another group.
            val other = SyncCodec.encodeFile(phoneA, OtherGroup, createdAt = 1)
            assertThat(client.sync(phoneA, body = other).status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(hub.groupStore.read()).isEqualTo(SyncDocument.EMPTY)
        }
    }

    @Test
    fun `another sync format version is 409 and says which side to update`() {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            val newer = withDocumentVersion(
                SyncCodec.encodeFile(phoneA, TestGroup, createdAt = 1),
                SyncDocument.VERSION + 1,
            )
            val response = client.sync(phoneA, body = newer)
            assertThat(response.status).isEqualTo(HttpStatusCode.Conflict)
            assertThat(response.bodyAsText()).contains("Update the hub")
            val older = withDocumentVersion(
                SyncCodec.encodeFile(phoneA, TestGroup, createdAt = 1),
                SyncDocument.VERSION - 1,
            )
            assertThat(client.sync(phoneA, body = older).bodyAsText()).contains("Update the app")
        }
    }

    @Test
    fun `invalid data is 422 with the validation message and nothing is stored`() {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            val bad =
                SyncDocument(
                    players = listOf(player("ann")),
                    matches = listOf(match("m1", listOf("ann"), listOf("ghost"))),
                )
            val response = client.sync(bad)
            assertThat(response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
            assertThat(response.bodyAsText()).contains("player ghost is missing")
            assertThat(
                client.sync(phoneA, body = "not a file".toByteArray()).status,
            ).isEqualTo(HttpStatusCode.UnprocessableEntity)
            val file = SyncJson.parseToJsonElement(
                SyncCodec.encodeFile(phoneA, TestGroup, createdAt = 1).decodeToString(),
            ).jsonObject
            val data = file.getValue("data").jsonPrimitive.content
            val flipped = data.substring(0, 40) + (if (data[40] == 'A') 'B' else 'A') + data.substring(41)
            val tampered = kotlinx.serialization.json.JsonObject(file + ("data" to JsonPrimitive(flipped))).toString()
            assertThat(
                client.sync(phoneA, body = tampered.toByteArray()).status,
            ).isEqualTo(HttpStatusCode.UnprocessableEntity)
            assertThat(hub.groupStore.read()).isEqualTo(SyncDocument.EMPTY)
        }
    }

    @Test
    fun `a body over 8 MB is 413`() {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            val response = client.sync(phoneA, body = ByteArray(MAX_BODY_BYTES + 1))
            assertThat(response.status).isEqualTo(HttpStatusCode.PayloadTooLarge)
        }
    }

    @Test
    fun `a hub without a group answers 503 and health is always ok`() {
        val hub = testHub(tmp.root.toPath(), joined = false)
        hubTest(hub) {
            assertThat(client.sync(phoneA).status).isEqualTo(HttpStatusCode.ServiceUnavailable)
            val health = client.get("/api/v1/health")
            assertThat(health.status).isEqualTo(HttpStatusCode.OK)
            assertThat(health.bodyAsText()).isEqualTo("""{"status":"ok"}""")
        }
    }

    @Test
    fun `several phones syncing at once converge on the same data`() {
        val hub = testHub(tmp.root.toPath())
        val phones = (1..8).map { i ->
            SyncDocument(
                players = listOf(player("ann", "Ann", updatedAt = i.toLong()), player("p$i", "P$i")),
                matches = listOf(match("m$i", listOf("ann"), listOf("p$i"), updatedAt = 100L + i)),
            )
        }
        hubTest(hub) {
            val answers = coroutineScope { phones.map { doc -> async { client.sync(doc) } }.awaitAll() }
            assertThat(answers.map { it.status }.toSet()).containsExactly(HttpStatusCode.OK)
            val expected = SyncMerge.mergeAll(phones)
            assertThat(hub.groupStore.read()).isEqualTo(expected)
            // Each phone, applying its answer and syncing once more, ends with exactly the hub's data.
            val second = phones.map { SyncCodec.decodeFile(client.sync(it).readRawBytes(), TestGroup) }
            assertThat(second.toSet()).containsExactly(expected)
        }
    }

    private fun withDocumentVersion(file: ByteArray, version: Int): ByteArray {
        val json = SyncJson.parseToJsonElement(file.decodeToString()).jsonObject
        val changed = kotlinx.serialization.json.JsonObject(json + ("documentVersion" to JsonPrimitive(version)))
        return changed.toString().toByteArray()
    }
}
