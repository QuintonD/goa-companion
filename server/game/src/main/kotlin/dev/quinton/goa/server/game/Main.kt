package dev.quinton.goa.server.game

fun main() {
    val server = GameServer(GameServerConfig.fromEnv())
    Runtime.getRuntime().addShutdownHook(Thread(server::stop))
    server.start(wait = true)
}
