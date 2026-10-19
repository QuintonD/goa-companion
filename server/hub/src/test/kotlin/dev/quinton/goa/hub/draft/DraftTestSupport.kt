package dev.quinton.goa.hub.draft

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.store.DeviceSlot
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters

/** A signed-in browser driving the Draft pages the way the forms do (POST → 303 → GET). */
class DraftBrowser(private val hub: Hub, private val client: HttpClient, private val token: String) {
    val deviceId: String get() = hub.devices.list().single().id

    suspend fun get(path: String): HttpResponse = client.get(path) { header(HttpHeaders.Cookie, cookie(token)) }

    suspend fun page(path: String): String {
        val response = get(path)
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.headers[HttpHeaders.CacheControl]).isEqualTo("no-store")
        return response.bodyAsText()
    }

    /** Posts a form; returns the redirect target (every Draft POST answers 303). */
    suspend fun post(path: String, vararg fields: Pair<String, String>): String {
        val response = client.submitForm(
            path,
            parameters { fields.forEach { (k, v) -> append(k, v) } },
        ) {
            header(HttpHeaders.Cookie, cookie(token))
            header(HttpHeaders.Origin, HUB_URL)
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.SeeOther)
        return response.headers[HttpHeaders.Location].orEmpty()
    }

    suspend fun setup(op: String, vararg fields: Pair<String, String>) = post("/draft/setup", "op" to op, *fields)

    suspend fun board(op: String) = post("/draft/board", "op" to op)

    fun <T> slot(slot: DeviceSlot<T>): T? = hub.deviceStore.read(deviceId, slot)

    fun delete(slot: DeviceSlot<*>) = hub.deviceStore.delete(deviceId, slot)
}

/** How often [needle] occurs in [text]. */
fun occurrences(text: String, needle: String): Int = text.split(needle).size - 1
