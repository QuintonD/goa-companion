package dev.quinton.goa.hub.web

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.HubToken
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.TestGroup
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.testHub
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Request bodies are capped before anything reads them: `POST /join` is open to the internet, and an
 * unbounded form body used to fill the heap (a 600 MB post wedged a hub with `-Xmx256m`).
 */
class RequestLimitsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val tooBig = "invite=" + "A".repeat(FORM_BODY_LIMIT)

    /** A form body sent chunked (no Content-Length), as a client that hides the size would. */
    private class Chunked(private val bytes: ByteArray) : OutgoingContent.WriteChannelContent() {
        override val contentType = ContentType.Application.FormUrlEncoded

        override suspend fun writeTo(channel: ByteWriteChannel) {
            bytes.asList().chunked(CHUNK).forEach { channel.writeFully(it.toByteArray()) }
        }
    }

    @Test
    fun `an oversized join is refused before it is read, with or without a length`() {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            val declared = client.post(JOIN_PATH) {
                header(HttpHeaders.Origin, HUB_URL)
                setBody(TextContent(tooBig, ContentType.Application.FormUrlEncoded))
            }
            assertThat(declared.status).isEqualTo(HttpStatusCode.PayloadTooLarge)

            val chunked = client.post(JOIN_PATH) {
                header(HttpHeaders.Origin, HUB_URL)
                setBody(Chunked(tooBig.toByteArray()))
            }
            assertThat(chunked.status).isEqualTo(HttpStatusCode.PayloadTooLarge)
            assertThat(hub.devices.list()).isEmpty()
        }
    }

    @Test
    fun `signed-in forms have the same cap, and a normal form still works`() {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            val client = plainClient()
            val token = client.signIn()
            val big = client.post("/settings/theme") {
                header(HttpHeaders.Cookie, cookie(token))
                header(HttpHeaders.Origin, HUB_URL)
                setBody(Chunked(("theme=LIGHT&x=" + "A".repeat(FORM_BODY_LIMIT)).toByteArray()))
            }
            assertThat(big.status).isEqualTo(HttpStatusCode.PayloadTooLarge)
            val small = client.post("/settings/theme") {
                header(HttpHeaders.Cookie, cookie(token))
                header(HttpHeaders.Origin, HUB_URL)
                setBody(TextContent("theme=LIGHT", ContentType.Application.FormUrlEncoded))
            }
            assertThat(small.status).isEqualTo(HttpStatusCode.Found)
        }
    }

    @Test
    fun `the sync API and the backup upload keep their own 8 MB limit`() {
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            val bigger = ByteArray(FORM_BODY_LIMIT * 2) { 'x'.code.toByte() }
            val sync = client.post(SYNC_PATH) {
                header(HttpHeaders.Authorization, "Bearer ${HubToken.derive(TestGroup)}")
                contentType(ContentType.parse(SyncCodec.FILE_MIME_TYPE))
                setBody(bigger)
            }
            // Read and refused as "not a sync file", not cut off.
            assertThat(sync.status).isEqualTo(HttpStatusCode.UnprocessableEntity)

            val client = plainClient()
            val token = client.signIn()
            val upload = client.post(BACKUP_IMPORT_PATH) {
                header(HttpHeaders.Cookie, cookie(token))
                header(HttpHeaders.Origin, HUB_URL)
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "file",
                                bigger,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "application/json")
                                    append(HttpHeaders.ContentDisposition, "filename=\"backup.json\"")
                                },
                            )
                        },
                    ),
                )
            }
            assertThat(upload.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
        }
    }

    private companion object {
        const val CHUNK = 8 * 1024
    }
}
