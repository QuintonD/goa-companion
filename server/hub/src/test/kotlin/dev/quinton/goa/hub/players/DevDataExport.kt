package dev.quinton.goa.hub.players

import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.HubClock
import dev.quinton.goa.hub.TestGroup
import dev.quinton.goa.hub.config.HubPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Local tries only: writes a hub data directory with a realistic group (not a regular test). */
class DevDataExport {
    @Test
    fun export() {
        val dir = System.getenv("GOA_PLAYERS_EXPORT")
        assumeTrue(dir != null)
        val url = System.getenv("GOA_PLAYERS_URL") ?: "http://localhost:8093"
        val path = Path.of(dir!!)
        Files.createDirectories(path)
        val hub = Hub(HubPaths(path), HubClock.System)
        hub.membership.join(TestGroup, System.currentTimeMillis())
        hub.config.update { it.copy(publicUrl = url) }
        val heroes = hub.catalog.catalog.heroes.map { it.id }.sorted()
        val names = listOf("Quinton", "Marta", "Rob", "Jess", "Theo", "Priya", "Sam", "Lena")
        val players = names.mapIndexed { i, n ->
            Player(
                "p-$i",
                n,
                listOf(
                    0xFF3FC1B0,
                    0xFFD9B44A,
                    0xFFEE7F6F,
                    0xFF62A8F2,
                    0xFFB892E6,
                    0xFF6CC27A,
                    0xFFEE85B8,
                    0xFFEE9A52,
                )[i],
                i,
                createdAt =
                    i.toLong() + 1,
            )
        }.toMutableList()
        players[7] = players[7].copy(archived = true)
        val dup = Player("p-dup", "Marta B", 0xFFC3CCD6, 9, createdAt = 50)
        val lookalike = Player("p-quin", "Quin", 0xFFAECF55, 10, createdAt = 60)
        val random = Random(7)
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val matches = (0 until 46).map { n ->
            val active = players.take(7).shuffled(random)
            val size = if (n % 7 == 3) 5 else 4
            val chosen = active.take(size)
            val pool = heroes.shuffled(random)
            val seats = mutableListOf<Participant>()
            val red = chosen.take(size / 2 + size % 2)
            val blue = chosen.drop(red.size)
            red.forEachIndexed { i, p -> seats += Participant(p.id, pool[i], Team.RED, handicap = size == 5 && i == 0) }
            blue.forEachIndexed { i, p -> seats += Participant(p.id, pool[10 + i], Team.BLUE) }
            if (size == 5) seats += Participant(blue[0].id, pool[15], Team.BLUE) // a double pilot
            if (n == 20) seats[1] = seats[1].copy(heroId = null) // an Unknown hero
            val played = now - (46 - n) * day / 2 - (n % 3) * 3_600_000L
            Match(
                id = "m-$n",
                createdAt = played,
                playedAt = played,
                status = if (n == 45) MatchStatus.PENDING else MatchStatus.COMPLETED,
                mode = if (n % 5 == 0) GameMode.LONG else GameMode.QUICK,
                winner = if (n == 45) {
                    null
                } else if (random.nextInt(100) < 52) {
                    Team.RED
                } else {
                    Team.BLUE
                },
                winCondition = if (n == 45) null else WinCondition.entries[random.nextInt(4)].takeIf { n % 9 != 0 },
                durationMin = 40 + random.nextInt(50),
                notes = if (n == 30) "Comeback on the last wave & a <b>great</b> throne push" else "",
                participants = seats,
                updatedAt = played,
            )
        }
        val withDup = matches.map { m ->
            if (m.id ==
                "m-2"
            ) {
                m.copy(
                    participants = m.participants.map {
                        if (it.playerId ==
                            "p-1"
                        ) {
                            it.copy(playerId = dup.id)
                        } else {
                            it
                        }
                    },
                )
            } else {
                m
            }
        }
        runBlocking {
            hub.groupStore.mergeIn(
                SyncDocument(
                    players = players + dup + lookalike,
                    matches = withDup,
                    playerMerges = listOf(PlayerMerge(dup.id, "p-1", now - day)),
                ),
            )
        }
        Files.writeString(path.resolve("invite.txt"), GroupInvite.format(TestGroup, url))
        println("INVITE " + GroupInvite.format(TestGroup, url))
    }
}
