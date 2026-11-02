package dev.quinton.goa.server.game

import dev.quinton.goa.server.game.http.gameServerModule
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory

fun main() {
    val config = GameServerConfig.fromEnv()
    LoggerFactory.getLogger("goa-game").info(
        "goa-game on {}:{}, data in {}, at most {} rooms, rules build {}",
        config.host,
        config.port,
        config.dataDir,
        config.maxRooms,
        config.rulesBuild,
    )
    embeddedServer(CIO, port = config.port, host = config.host) { gameServerModule(config) }.start(wait = true)
}
