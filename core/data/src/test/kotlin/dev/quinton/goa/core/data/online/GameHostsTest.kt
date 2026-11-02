package dev.quinton.goa.core.data.online

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.online.GameHost
import dev.quinton.goa.core.online.NetworkInfo
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardFetcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GameHostsTest {
    private val builtIn = "https://oldboi.example:8443/game"
    private val defaults = GameServerDefaults(builtIn)

    private fun network(vararg hosts: GameHost, minProtocol: Int = 1) =
        NetworkInfo(NetworkInfo.SCHEMA, minProtocol, hosts.toList())

    @Test
    fun `no network file means the built-in address`() {
        assertThat(chooseHost(null, defaults).base).isEqualTo(builtIn)
    }

    @Test
    fun `the first open host wins, in the file's order`() {
        val info = network(
            GameHost("https://a.example/", "open"),
            GameHost("https://b.example", "open"),
        )
        assertThat(chooseHost(info, defaults).base).isEqualTo("https://a.example")
    }

    @Test
    fun `a closed host is skipped`() {
        val info = network(GameHost("https://a.example", "closed"), GameHost("https://b.example", "open"))
        assertThat(chooseHost(info, defaults).base).isEqualTo("https://b.example")
    }

    @Test
    fun `when only another host is closed the built-in address stands`() {
        val info = network(GameHost("https://a.example", "closed"))
        assertThat(chooseHost(info, defaults).base).isEqualTo(builtIn)
    }

    @Test
    fun `when the built-in address is the one closed there is no host, and the file's note says why`() {
        val info = network(GameHost("$builtIn/", "closed", note = "Back on Sunday"))
        val choice = chooseHost(info, defaults)
        assertThat(choice.base).isNull()
        assertThat(choice.note).isEqualTo("Back on Sunday")
    }

    @Test
    fun `plain HTTP and odd addresses aren't hosts, except a local server in a debug build`() {
        val plain = network(GameHost("http://a.example", "open"))
        assertThat(chooseHost(plain, defaults).base).isEqualTo(builtIn)
        val odd = network(GameHost("https://a.example/one/two", "open"))
        assertThat(chooseHost(odd, defaults).base).isEqualTo(builtIn)

        val local = network(GameHost("http://10.0.2.2:8788", "open"))
        assertThat(chooseHost(local, defaults).base).isEqualTo(builtIn)
        assertThat(chooseHost(local, defaults.copy(allowLocalHttp = true)).base).isEqualTo("http://10.0.2.2:8788")
    }

    @Test
    fun `a file asking for a newer protocol says this build is too old`() {
        val info = network(GameHost(builtIn, "open"), minProtocol = OnlineProtocol.VERSION + 1)
        assertThat(chooseHost(info, defaults).tooOld).isTrue()
        assertThat(chooseHost(network(GameHost(builtIn, "open")), defaults).tooOld).isFalse()
    }

    private class Files(var text: String?) : LeaderboardFetcher {
        val asked = mutableListOf<String>()

        override suspend fun get(url: String): String? {
            asked += url
            return text
        }
    }

    private fun networkFile(origin: String, schema: Int = NetworkInfo.SCHEMA) =
        """{"schema":$schema,"minProtocol":1,"games":[{"origin":"$origin","status":"open"}]}"""

    @Test
    fun `the file is read at most every five minutes, and the last good copy holds when it can't be`() = runTest {
        var now = 0L
        val files = Files(networkFile("https://a.example"))
        val hosts = GameHosts(files, defaults, EditClock { now })

        assertThat(hosts.current().base).isEqualTo("https://a.example")
        assertThat(hosts.current().base).isEqualTo("https://a.example")
        assertThat(files.asked).containsExactly(OnlineRoutes.NETWORK_FILE)

        now += 5 * 60_000L
        files.text = null
        assertThat(hosts.current().base).isEqualTo("https://a.example")
        assertThat(files.asked).hasSize(2)

        now += 5 * 60_000L
        files.text = "<html>a Pages error page</html>"
        assertThat(hosts.current().base).isEqualTo("https://a.example")
    }

    @Test
    fun `a file that can't be read, or of a newer schema, leaves the built-in address`() = runTest {
        val hosts = GameHosts(Files("not json"), defaults) { 0L }
        assertThat(hosts.current().base).isEqualTo(builtIn)
        val newer = GameHosts(Files(networkFile("https://a.example", NetworkInfo.SCHEMA + 1)), defaults) { 0L }
        assertThat(newer.current().base).isEqualTo(builtIn)
    }
}
