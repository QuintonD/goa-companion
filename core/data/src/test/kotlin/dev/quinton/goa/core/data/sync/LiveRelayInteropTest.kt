package dev.quinton.goa.core.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.SyncServerDefaults
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.data.match.RoomMatchRepository
import dev.quinton.goa.core.data.player.RoomPlayerRepository
import dev.quinton.goa.core.data.rating.FakeEloConfigRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.datastore.sync.GroupSyncStore
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The phone's real sync stack (Room, DataStore, OkHttp, javax.crypto) against a real relay running
 * on this machine, with the web app in between (ADR-043 W2): `web/e2e/scripts/interop-live.mjs`
 * starts `server/relay`, runs [phoneUploads], then the browser (Playwright, `interop.spec.ts`) which
 * opens the phone's upload and adds a match, then [aFreshPhoneGetsTheBrowsersMatch]. Skipped
 * unless the script wrote `build/live-relay.json` (url, group id, key, phase, out).
 */
@RunWith(RobolectricTestRunner::class)
class LiveRelayInteropTest {
    @get:Rule val tmp = TemporaryFolder()

    private val config: JsonObject? = File("build/live-relay.json").takeIf { it.exists() }
        ?.let { Json.parseToJsonElement(it.readText()).jsonObject }

    private fun value(name: String) = requireNotNull(config?.get(name)?.jsonPrimitive?.content) { name }

    private inner class Phone {
        val clock = EditClock { System.currentTimeMillis() }
        val db = GoaDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        val groups = DataStoreGroupRepository(
            GroupSyncStore(
                PreferenceDataStoreFactory.create(produceFile = {
                    File(tmp.newFolder(), "g.preferences_pb")
                }),
            ),
            SyncServerDefaults(value("url"), allowLocalHttp = true),
        )
        val players = RoomPlayerRepository(db, clock)
        val matches = RoomMatchRepository(db, clock)
        val elo = FakeEloConfigRepository(clock = { System.currentTimeMillis() })
        val sync = RoomSyncRepository(db, elo, clock)
        val server = OkHttpSyncServerRepository(groups, sync, db, elo, clock, SyncServerHttpClient.create())

        suspend fun join() = groups.join(
            GroupCredentials(
                value("groupId"),
                value("keyHex").chunked(2).map {
                    it.toInt(16).toByte()
                }.toByteArray(),
                "Live",
            ),
        )

        suspend fun syncNow() = withContext(Dispatchers.IO) { server.sync() }

        suspend fun ratings() = EloCalculator.replay(matches.observeMatches().first(), elo.config.first())
    }

    private fun match(id: String, red: List<String>, blue: List<String>, heroes: List<String>, winner: Team, at: Long) =
        Match(
            id = id,
            createdAt = at,
            playedAt = at,
            status = MatchStatus.COMPLETED,
            mode = GameMode.QUICK,
            winner = winner,
            participants = red.mapIndexed { i, p -> Participant(p, heroes[i], Team.RED) } +
                blue.mapIndexed { i, p -> Participant(p, heroes[red.size + i], Team.BLUE) },
        )

    @Test
    fun phoneUploads() = runBlocking {
        assumeTrue(config?.get("phase")?.jsonPrimitive?.content == "seed")
        val phone = Phone()
        phone.join()
        val day = 86_400_000L
        val now = System.currentTimeMillis() - 3 * day
        listOf("Ann", "Bob", "Cid", "Dee").forEachIndexed { i, name ->
            phone.players.upsert(Player("live-${name.lowercase()}", name, 0xFF3FC1B0, i, createdAt = now + i))
        }
        val ids = listOf("live-ann", "live-bob", "live-cid", "live-dee")
        phone.matches.upsert(
            match("live-1", ids.take(2), ids.drop(2), listOf("arien", "brogan", "wasp", "sabina"), Team.RED, now),
        )
        phone.matches.upsert(
            match(
                "live-2",
                listOf(ids[0], ids[2]),
                listOf(ids[1], ids[3]),
                listOf("misa", "dodger", "tigerclaw", "xargatha"),
                Team.BLUE,
                now + day,
            ),
        )
        phone.matches.upsert(
            match(
                "live-3",
                listOf(ids[0], ids[3]),
                listOf(ids[1], ids[2]),
                listOf("whisper", "ursafar", "arien", "brogan"),
                Team.RED,
                now + 2 * day,
            ),
        )
        val result = phone.syncNow()
        assertThat(result).isInstanceOf(SyncServerResult.Synced::class.java)
        assertThat((result as SyncServerResult.Synced).uploaded).isTrue()
        // What the browser must show: the phone's ratings, rounded as the leaderboard shows them.
        val ratings = phone.ratings()
        File(value("out")).writeText(
            ids.joinToString(",") { "$it=${kotlin.math.round(ratings.player(it).value).toInt()}" },
        )
    }

    @Test
    fun aFreshPhoneGetsTheBrowsersMatch() = runBlocking {
        assumeTrue(config?.get("phase")?.jsonPrimitive?.content == "check")
        val phone = Phone()
        phone.join()
        val result = phone.syncNow()
        assertThat(result).isInstanceOf(SyncServerResult.Synced::class.java)
        val log = phone.matches.observeMatches().first()
        assertThat(log.map { it.id }).containsAtLeast("live-1", "live-2", "live-3")
        val fromBrowser = log.filter { !it.id.startsWith("live-") }
        assertThat(fromBrowser).hasSize(1)
        assertThat(fromBrowser.single().winner).isEqualTo(Team.BLUE)
        assertThat(fromBrowser.single().participants.map { it.playerId }.toSet())
            .containsExactly("live-ann", "live-bob", "live-cid", "live-dee")
        val ratings = phone.ratings()
        File(value("out")).writeText(
            listOf("live-ann", "live-bob", "live-cid", "live-dee")
                .joinToString(",") { "$it=${kotlin.math.round(ratings.player(it).value).toInt()}" },
        )
    }
}
