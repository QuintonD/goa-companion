package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.sync.RelayHttp
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.feature.board.play.online.GameConnector
import dev.quinton.goa.feature.board.play.online.OnlineGateway
import dev.quinton.goa.feature.board.play.online.OnlineIdentityStore
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardRepository
import dev.quinton.goa.web.engine.EngineRequest
import dev.quinton.goa.web.engine.EngineResponse
import dev.quinton.goa.web.group.FetchRelayHttp
import dev.quinton.goa.web.play.online.OnlineHosts
import dev.quinton.goa.web.play.online.WebGameConnector
import dev.quinton.goa.web.play.online.WebLeaderboardCache
import dev.quinton.goa.web.play.online.WebLeaderboardFetcher
import dev.quinton.goa.web.play.online.WebOnlineGateway
import dev.quinton.goa.web.play.online.WebOnlineIdentityStore
import dev.quinton.goa.web.play.online.readPublicFile
import dev.quinton.goa.web.pwa.BuildInfo
import dev.quinton.goa.web.storage.scoped
import kotlinx.coroutines.flow.first

/**
 * Online games (ADR-056): the account, the game server's REST and socket, the public leaderboard. Owned by the
 * Web stream of docs/ONLINE.md. Nothing here touches storage or the network until used.
 */
class OnlineData(private val services: WebServices) {
    private val store = services.store.scoped("online/")
    private val http: RelayHttp = FetchRelayHttp(ONLINE_TIMEOUT_MS)
    private val fileHttp: RelayHttp = FetchRelayHttp(FILE_TIMEOUT_MS)

    val hosts = OnlineHosts(
        read = { readPublicFile(fileHttp, it) },
        builtIn = DEFAULT_URL,
        override = { if (WebSyncDefaults.forThisPage().allowLocalHttp) store.get(TEST_SERVER_KEY) else null },
        now = services.clock::now,
    )

    val identities: OnlineIdentityStore = WebOnlineIdentityStore(store)

    val gateway: OnlineGateway = WebOnlineGateway(http, identities, hosts::base)

    val connector: GameConnector = WebGameConnector(hosts::base)

    val leaderboard = LeaderboardRepository(
        fetcher = WebLeaderboardFetcher(fileHttp, hosts),
        cache = WebLeaderboardCache(store),
        builtInHost = DEFAULT_URL,
        now = services.clock::now,
    )

    /** Names this build in the server's logs. */
    val client: String get() = "web/${BuildInfo.version}"

    suspend fun catalog(): Catalog = services.content.catalog.catalog.first()

    suspend fun catalogVersion(): Int = runCatching { catalog().dataVersion }.getOrDefault(0)

    /** The heroes the engine can play, in the catalog's order: the same list the server's engine checks. */
    suspend fun playableHeroes(): List<String> {
        val info = services.play.engine.call(EngineRequest.Info) as? EngineResponse.Info
        val playable = info?.playableHeroes.orEmpty()
        return catalog().heroes.map { it.id }.filter { it in playable }
    }

    companion object {
        /** `goa.gameServer` in gradle.properties; the site's CSP is built from the same property. */
        const val DEFAULT_URL = "https://oldboi.taila4d38d.ts.net:8443/game"
        private const val TEST_SERVER_KEY = "testServer"
        private const val ONLINE_TIMEOUT_MS = 30_000
        private const val FILE_TIMEOUT_MS = 10_000
    }
}
