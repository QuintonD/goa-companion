package dev.quinton.goa.feature.board.play.online.leaderboard

import dev.quinton.goa.core.online.GameHost
import dev.quinton.goa.core.online.LeaderRow
import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.NetworkInfo
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class LeaderboardRepositoryTest {
    private val builtIn = "https://built.in:8443/game"
    private val builtInBoard = "$builtIn/v1/leaderboard"
    private val now = 10_000_000L

    private class FakeCache(var text: String? = null) : LeaderboardCache {
        override suspend fun read(): String? = text

        override suspend fun write(text: String) {
            this.text = text
        }
    }

    private class FakeWeb(private val files: Map<String, String>) : LeaderboardFetcher {
        val asked = ArrayList<String>()

        override suspend fun get(url: String): String? {
            asked += url
            return files[url]
        }
    }

    private fun board(generatedAt: Long = now - 60_000, name: String = "Ann", schema: Int = 1) = LeaderboardSnapshot(
        schema = schema,
        generatedAt = generatedAt,
        logSha256 = "abc",
        matches = 6,
        eloConfig = 1,
        players = listOf(LeaderRow("a", name, 1540, 6, 4)),
        heroes = emptyList(),
    )

    private fun text(snapshot: LeaderboardSnapshot) =
        OnlineProtocol.json.encodeToString(LeaderboardSnapshot.serializer(), snapshot)

    private fun network(vararg hosts: String, schema: Int = 1) = OnlineProtocol.json.encodeToString(
        NetworkInfo.serializer(),
        NetworkInfo(schema, 1, hosts.map { GameHost(it, "open") }),
    )

    private fun repository(web: LeaderboardFetcher, cache: LeaderboardCache = FakeCache()) =
        LeaderboardRepository(web, cache, builtIn) { now }

    @Test
    fun pagesComesFirstAndAsksNothingElse() = runTest {
        val cache = FakeCache()
        val web = FakeWeb(
            mapOf(OnlineRoutes.LEADERBOARD_FILE to text(board()), builtInBoard to text(board(name = "Server"))),
        )
        val load = assertNotNull(repository(web, cache).load())

        assertEquals(LeaderboardOrigin.PAGES, load.origin)
        assertEquals("Ann", load.snapshot.players.single().name)
        assertEquals(60_000, load.ageMillis)
        assertEquals(listOf(OnlineRoutes.LEADERBOARD_FILE), web.asked)
        assertEquals(text(board()), cache.text)
    }

    @Test
    fun theFullFallbackOrder() = runTest {
        val cache = FakeCache(text(board(generatedAt = now - 3 * DAY, name = "Old")))
        val web = FakeWeb(
            mapOf(OnlineRoutes.NETWORK_FILE to network("https://one.example", "https://two.example/")),
        )
        val load = assertNotNull(repository(web, cache).load())

        assertEquals(
            listOf(
                OnlineRoutes.LEADERBOARD_FILE,
                OnlineRoutes.NETWORK_FILE,
                "https://one.example/v1/leaderboard",
                "https://two.example/v1/leaderboard",
                builtInBoard,
            ),
            web.asked,
        )
        assertEquals(LeaderboardOrigin.LAST_COPY, load.origin)
        assertEquals("Old", load.snapshot.players.single().name)
        assertEquals(3 * DAY, load.ageMillis)
    }

    @Test
    fun aHostTakesOverAndItsBoardBecomesTheLastCopy() = runTest {
        val cache = FakeCache(text(board(name = "Old")))
        val fresh = text(board(name = "Fresh"))
        val web = FakeWeb(
            mapOf(
                OnlineRoutes.NETWORK_FILE to network("https://one.example", "https://two.example"),
                "https://two.example/v1/leaderboard" to fresh,
                builtInBoard to text(board(name = "Unused")),
            ),
        )
        val load = assertNotNull(repository(web, cache).load())

        assertEquals(LeaderboardOrigin.SERVER, load.origin)
        assertEquals("Fresh", load.snapshot.players.single().name)
        assertEquals(fresh, cache.text)
        assertEquals(false, builtInBoard in web.asked)
    }

    @Test
    fun theBuiltInAddressIsTriedOnceEvenWhenListed() = runTest {
        val web = FakeWeb(mapOf(OnlineRoutes.NETWORK_FILE to network("$builtIn/", "https://one.example")))
        assertNull(repository(web).load())
        assertEquals(
            listOf(
                OnlineRoutes.LEADERBOARD_FILE,
                OnlineRoutes.NETWORK_FILE,
                builtInBoard,
                "https://one.example/v1/leaderboard",
            ),
            web.asked,
        )
    }

    @Test
    fun aBrokenOrNewerNetworkFileFallsBackToTheBuiltInAddress() = runTest {
        for (bad in listOf(null, "<html>404</html>", "{}", network("https://future.example", schema = 2))) {
            val files = buildMap {
                if (bad != null) put(OnlineRoutes.NETWORK_FILE, bad)
                put(builtInBoard, text(board()))
            }
            val web = FakeWeb(files)
            val load = assertNotNull(repository(web).load(), "network file: $bad")

            assertEquals(LeaderboardOrigin.SERVER, load.origin)
            assertEquals(false, "https://future.example/v1/leaderboard" in web.asked)
        }
    }

    @Test
    fun aMalformedOrNewerFileCountsAsAFailedRead() = runTest {
        val depth = 50_000
        val bad = listOf(
            "",
            "not json",
            "<!doctype html><html>404</html>",
            "[]",
            "{}",
            """{"schema":1}""",
            text(board()).dropLast(5),
            text(board(schema = LeaderboardSnapshot.SCHEMA + 1)),
            text(board(schema = 0)),
            """{"schema":1,"players":${"[".repeat(depth)}${"]".repeat(depth)}}""",
        )
        for (file in bad) {
            val cache = FakeCache()
            val web = FakeWeb(mapOf(OnlineRoutes.LEADERBOARD_FILE to file, builtInBoard to text(board())))
            val load = assertNotNull(repository(web, cache).load(), "file: ${file.take(40)}")

            assertEquals(LeaderboardOrigin.SERVER, load.origin, "file: ${file.take(40)}")
            assertEquals(text(board()), cache.text, "a bad file is never kept")
        }
    }

    @Test
    fun aBadFileNeverReplacesTheLastGoodCopy() = runTest {
        val good = text(board(name = "Good"))
        val cache = FakeCache(good)
        val web = FakeWeb(mapOf(OnlineRoutes.LEADERBOARD_FILE to text(board(schema = 99)), builtInBoard to "oops"))
        val load = assertNotNull(repository(web, cache).load())

        assertEquals(LeaderboardOrigin.LAST_COPY, load.origin)
        assertEquals(good, cache.text)
    }

    @Test
    fun aDamagedOrNewerLastCopyIsNoBoard() = runTest {
        for (copy in listOf("garbage", text(board(schema = 99)))) {
            assertNull(repository(FakeWeb(emptyMap()), FakeCache(copy)).load())
        }
    }

    @Test
    fun nothingAnywhereIsNoBoard() = runTest {
        assertNull(repository(FakeWeb(emptyMap())).load())
    }

    @Test
    fun anUnknownFieldInAFileIsFine() = runTest {
        val file = text(board()).dropLast(1) + ""","future":{"a":[1,2]}}"""
        val web = FakeWeb(mapOf(OnlineRoutes.LEADERBOARD_FILE to file))
        assertEquals(LeaderboardOrigin.PAGES, repository(web).load()?.origin)
    }

    @Test
    fun aPhoneClockBehindTheServerIsNeverANegativeAge() = runTest {
        val web = FakeWeb(mapOf(OnlineRoutes.LEADERBOARD_FILE to text(board(generatedAt = now + 5_000))))
        assertEquals(0, repository(web).load()?.ageMillis)
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
