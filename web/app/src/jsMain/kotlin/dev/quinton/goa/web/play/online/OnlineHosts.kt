package dev.quinton.goa.web.play.online

import dev.quinton.goa.core.data.sync.RelayHttp
import dev.quinton.goa.core.data.sync.RelayNetworkException
import dev.quinton.goa.core.data.sync.RelayRequest
import dev.quinton.goa.core.online.NetworkInfo
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardCache
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardFetcher
import dev.quinton.goa.web.storage.KeyValueStore
import kotlinx.browser.document
import kotlinx.browser.window

/** A person's choice in `network.json` (docs/ONLINE.md): this host takes no new players for now. */
private const val CLOSED = "closed"
private const val NETWORK_FOR_MS = 5 * 60_000L
private const val MAX_FILE_BYTES = 4L * 1024 * 1024

/** `https://host:8443/game` → `https://host:8443`; null when it isn't an http(s) address. */
fun originOf(address: String): String? = Regex("^https?://[^/?#]+").find(address.trim())?.value

/**
 * Whether the page's `connect-src` lets it reach [address]. A site rebuild adds a host to the policy
 * (`goa.gameServer`, ADR-056), so a host only `network.json` knows of must not be tried: it could never answer.
 */
fun connectSrcAllows(policy: String?, address: String): Boolean {
    if (policy == null) return true // the dev server has no policy
    val origin = originOf(address) ?: return false
    val sources = policy.split(';').map { it.trim().split(Regex("\\s+")) }
        .firstOrNull { it.first() == "connect-src" }?.drop(1) ?: return false
    val host = origin.substringBeforeLast(':', origin)
    return origin in sources || "$host:*" in sources
}

/**
 * The address to play on: the first open host of [network] this page may reach. Null when every listed host is
 * closed. The built-in address when there is no readable file, no host listed, or none this build can reach.
 */
fun chooseHost(network: NetworkInfo?, builtIn: String, allowed: (String) -> Boolean): String? {
    val listed = network?.games.orEmpty().map { it.copy(origin = it.origin.trimEnd('/')) }
    if (listed.isEmpty()) return builtIn
    val open = listed.filter { it.status != CLOSED }
    if (open.isEmpty()) return null
    return open.firstOrNull { allowed(it.origin) }?.origin
        ?: builtIn.takeIf { home -> listed.none { it.origin == home.trimEnd('/') && it.status == CLOSED } }
}

/**
 * Where live games are (docs/ONLINE.md "Where things run"): `network.json`, read before connecting and kept a
 * few minutes, else the built-in address. [override] is a test server's address, honoured on a localhost page
 * only: on Pages the origin's storage is shared with every other Pages site of the account.
 */
class OnlineHosts(
    private val read: suspend (String) -> String?,
    private val builtIn: String,
    private val override: suspend () -> String?,
    private val now: () -> Long,
    private val allowed: (String) -> Boolean = { connectSrcAllows(pagePolicy(), it) },
) {
    private var network: NetworkInfo? = null
    private var readAt: Long? = null

    /** The address of the game server, or null when every host is closed. */
    suspend fun base(): String? = override() ?: chooseHost(network(), builtIn, allowed)

    /** `network.json` asks for a newer protocol than this build's: the app needs updating. */
    suspend fun tooOld(): Boolean = (network()?.minProtocol ?: 0) > OnlineProtocol.VERSION

    /** Reads `network.json` again and returns its text as read, for the leaderboard's fallback. */
    suspend fun networkFile(): String? {
        val text = read(OnlineRoutes.NETWORK_FILE)
        adopt(text)
        return text
    }

    /** Whether [url] is on a host `network.json` has closed, going by the last read of it. */
    fun isClosed(url: String): Boolean {
        val origin = originOf(url) ?: return false
        return network?.games.orEmpty().any { it.status == CLOSED && originOf(it.origin) == origin }
    }

    /** [url] with the built-in address swapped for a test server's. */
    suspend fun rewrite(url: String): String {
        val local = override() ?: return url
        return if (url.startsWith(builtIn)) local.trimEnd('/') + url.removePrefix(builtIn) else url
    }

    private suspend fun network(): NetworkInfo? {
        val last = readAt
        if (last != null && now() - last < NETWORK_FOR_MS) return network
        networkFile()
        return network
    }

    private fun adopt(text: String?) {
        network = text?.let {
            runCatching { OnlineProtocol.json.decodeFromString(NetworkInfo.serializer(), it) }.getOrNull()
        }?.takeIf { it.schema in 1..NetworkInfo.SCHEMA }
        readAt = now()
    }
}

private fun pagePolicy(): String? =
    document.querySelector("meta[http-equiv=\"Content-Security-Policy\"]")?.getAttribute("content")

/** The text of a public file; null on any failure, a bad status or a timeout. */
suspend fun readPublicFile(http: RelayHttp, url: String): String? = try {
    http.exchange(RelayRequest("GET", url, emptyMap())) { response ->
        if (response.code in 200..299) response.body(MAX_FILE_BYTES)?.decodeToString() else null
    }
} catch (_: RelayNetworkException) {
    null
}

/**
 * [LeaderboardFetcher] over fetch: never throws. A host that `network.json` has closed is not asked. Pages sites
 * of one account share the PWA's origin, so the leaderboard file needs no new CSP source.
 */
class WebLeaderboardFetcher(private val http: RelayHttp, private val hosts: OnlineHosts) : LeaderboardFetcher {
    override suspend fun get(url: String): String? = when {
        url == OnlineRoutes.NETWORK_FILE -> hosts.networkFile()
        hosts.isClosed(url) -> null
        else -> readPublicFile(http, hosts.rewrite(url))
    }
}

/** The last good board, under `online/` in IndexedDB, for when nothing can be reached. */
class WebLeaderboardCache(private val store: KeyValueStore) : LeaderboardCache {
    override suspend fun read(): String? = store.get(KEY)

    override suspend fun write(text: String) = store.put(KEY, text)

    private companion object {
        const val KEY = "leaderboard"
    }
}

/** Whether the browser is on an Android phone or tablet: only there can a link open the Android app. */
fun onAndroid(): Boolean = "Android" in window.navigator.userAgent
