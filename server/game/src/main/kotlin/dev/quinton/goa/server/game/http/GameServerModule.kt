package dev.quinton.goa.server.game.http

import dev.quinton.goa.core.online.HealthInfo
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.server.game.GameServerConfig
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/** The path Funnel publishes this server under; it may arrive with or without it (docs/ONLINE.md). */
const val PUBLIC_MOUNT = "/game"

fun Application.gameServerModule(config: GameServerConfig) {
    routing {
        api(config)
        route(PUBLIC_MOUNT) { api(config) }
    }
}

private fun Route.api(config: GameServerConfig) {
    get(OnlineRoutes.HEALTH) {
        val health = HealthInfo(
            ok = true,
            protocol = OnlineProtocol.VERSION,
            rulesBuild = config.rulesBuild,
            status = "open",
            rooms = 0,
            maxRooms = config.maxRooms,
        )
        val body = OnlineProtocol.json.encodeToString(HealthInfo.serializer(), health)
        call.respondText(body, ContentType.Application.Json)
    }
}
