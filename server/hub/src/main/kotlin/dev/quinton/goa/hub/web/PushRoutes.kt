package dev.quinton.goa.hub.web

import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.push.PushPayload
import dev.quinton.goa.hub.push.PushSubscription
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Web Push for the signed-in browser (ADR-030 §9), used by `static/push.js`:
 * `GET /api/v1/push/key` → `{"publicKey": …}` (VAPID application server key);
 * `POST /api/v1/push/subscribe` with `PushSubscription.toJSON()` → 204;
 * `POST /api/v1/push/unsubscribe` with `{"endpoint": …}` → 204;
 * `POST /api/v1/push/test` → sends "Notifications are on" to this browser, `{"sent": n}`.
 */
internal fun Route.pushRoutes(hub: Hub) {
    get("$PUSH_PATH/key") {
        call.respondText(
            buildJsonObject {
                put("publicKey", hub.push.publicKey)
            }.toString(),
            ContentType.Application.Json,
        )
    }
    post("$PUSH_PATH/subscribe") {
        val subscription =
            runCatching { parseSubscription(Json.parseToJsonElement(call.receiveText()).jsonObject) }.getOrNull()
                ?: return@post call.respondText("Not a push subscription.", status = HttpStatusCode.BadRequest)
        try {
            hub.push.subscribe(call.device.id, subscription)
        } catch (e: IllegalArgumentException) {
            return@post call.respondText(e.message.orEmpty(), status = HttpStatusCode.BadRequest)
        }
        call.respond(HttpStatusCode.NoContent)
    }
    post("$PUSH_PATH/unsubscribe") {
        val endpoint =
            runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject["endpoint"]?.jsonPrimitive?.content }
                .getOrNull() ?: return@post call.respondText("No endpoint.", status = HttpStatusCode.BadRequest)
        hub.push.unsubscribe(call.device.id, endpoint)
        call.respond(HttpStatusCode.NoContent)
    }
    post("$PUSH_PATH/test") {
        val result = hub.push.send(
            call.device.id,
            PushPayload("Notifications are on", "The timer can now reach you when your phone is locked."),
        )
        call.respondText(buildJsonObject { put("sent", result.sent) }.toString(), ContentType.Application.Json)
    }
}

private fun parseSubscription(json: JsonObject): PushSubscription {
    val keys = json.getValue("keys").jsonObject
    return PushSubscription(
        endpoint = json.getValue("endpoint").jsonPrimitive.content,
        p256dh = keys.getValue("p256dh").jsonPrimitive.content,
        auth = keys.getValue("auth").jsonPrimitive.content,
    )
}

internal const val PUSH_PATH = "/api/v1/push"
