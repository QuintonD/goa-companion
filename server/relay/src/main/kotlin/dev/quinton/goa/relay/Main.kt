package dev.quinton.goa.relay

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory

fun main() {
    val config = RelayConfig.fromEnv()
    val log = LoggerFactory.getLogger("goa-relay")
    val store = GroupStore(config.dataDir, config.historyKeep, config.maxGroups)
    if (!store.isAvailable()) log.warn("{} is not writable; requests get 503 until it is", config.dataDir)
    log.info(
        "goa-relay protocol {} on {}:{}, data in {}, max {} groups, {} req/min per client, {} global, browsers from {}",
        PROTOCOL, config.host, config.port, config.dataDir, config.maxGroups,
        config.perClientPerMinute, config.globalPerMinute, config.corsOrigins.ifEmpty { setOf("none") },
    )
    embeddedServer(CIO, port = config.port, host = config.host) {
        relayModule(config, store)
    }.start(wait = true)
}
