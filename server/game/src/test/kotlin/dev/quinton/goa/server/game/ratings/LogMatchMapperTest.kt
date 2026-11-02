package dev.quinton.goa.server.game.ratings

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.server.game.store.GameRow
import dev.quinton.goa.server.game.store.GameSeatRow
import java.io.File
import org.junit.Test

class LogMatchMapperTest {
    /** The Lanes the shipped catalog gives each map, so the mode follows the real maps. */
    private val lanes: Map<String, Int> = File("../../app/src/main/assets").let { assets ->
        CatalogFiles.parseMapIndex(assets.resolve(CatalogFiles.MAPS_INDEX_PATH).readText()).associateWith { id ->
            CatalogFiles.parseMap(id, assets.resolve(CatalogFiles.mapPath(id)).readText()).lanes.size
        }
    }

    private val mapper = LogMatchMapper { lanes.getValue(it) }

    private fun game(mapId: String = "forgotten-island", length: GameLength = GameLength.QUICK): GameRow {
        val setup = GameSetup(
            mapId = mapId,
            seats = listOf(
                SeatSetup("arien", Team.RED, Controller.Human),
                SeatSetup("brogan", Team.BLUE, Controller.Human),
            ),
            length = length,
            tieBreaker = Team.BLUE,
        )
        return GameRow(
            id = "game-1",
            roomCode = "ABCD2345",
            setup = setup,
            start = SavedGame(engineVersion = 1, setup = setup, payload = ""),
            rulesBuild = "test",
            engineVersion = 1,
            ranked = true,
            mapId = mapId,
            length = length,
            createdAt = START,
        )
    }

    private val seats = listOf(
        GameSeatRow("game-1", "brogan", "account-b", Team.BLUE, handicap = true, seatOrder = 1),
        GameSeatRow("game-1", "arien", "account-a", Team.RED, handicap = false, seatOrder = 0),
    )

    @Test
    fun everyFieldOfTheLogRow() {
        val finished = START + 31 * MINUTE + 59_000
        val match = mapper.matchOf(game(), seats, Team.RED, WinReason.THRONE, finished)

        assertThat(match.id).isEqualTo("game-1")
        assertThat(match.createdAt).isEqualTo(START)
        assertThat(match.playedAt).isEqualTo(finished)
        assertThat(match.updatedAt).isEqualTo(finished)
        assertThat(match.status).isEqualTo(MatchStatus.COMPLETED)
        assertThat(match.mode).isEqualTo(GameMode.QUICK)
        assertThat(match.map).isEqualTo("forgotten-island")
        assertThat(match.winner).isEqualTo(Team.RED)
        assertThat(match.winCondition).isEqualTo(WinCondition.THRONE)
        assertThat(match.durationMin).isEqualTo(31)
        assertThat(match.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(match.notes).isEmpty()
        assertThat(match.draftId).isNull()
        assertThat(match.participants).containsExactly(
            Participant("account-a", "arien", Team.RED, handicap = false),
            Participant("account-b", "brogan", Team.BLUE, handicap = true),
        ).inOrder()
    }

    @Test
    fun everyWinReasonHasItsCondition() {
        val expected = mapOf(
            WinReason.THRONE to WinCondition.THRONE,
            WinReason.LAST_PUSH to WinCondition.LAST_PUSH,
            WinReason.LIFE_COUNTERS to WinCondition.LIFE_COUNTERS,
            WinReason.CARD_TEXT to WinCondition.OTHER,
        )
        // A new reason fails here until someone decides what it means in the log.
        assertThat(expected.keys).containsExactlyElementsIn(WinReason.entries)
        for ((reason, condition) in expected) {
            val match = mapper.matchOf(game(), seats, Team.BLUE, reason, START + MINUTE)
            assertThat(match.winCondition).isEqualTo(condition)
        }
    }

    @Test
    fun theModeFollowsTheMapThenTheLength() {
        assertThat(lanes).containsEntry("forgotten-island", 1)
        assertThat(lanes).containsEntry("across-the-river", 2)
        val cases = listOf(
            Triple("forgotten-island", GameLength.QUICK, GameMode.QUICK),
            Triple("forgotten-island", GameLength.LONG, GameMode.LONG),
            Triple("across-the-river", GameLength.QUICK, GameMode.DOUBLE_LANE),
            Triple("across-the-river", GameLength.LONG, GameMode.DOUBLE_LANE),
        )
        for ((map, length, mode) in cases) {
            val match = mapper.matchOf(game(map, length), seats, Team.RED, WinReason.LAST_PUSH, START + MINUTE)
            assertThat(match.mode).isEqualTo(mode)
        }
    }

    @Test
    fun aClockThatWentBackNeverGivesANegativeDuration() {
        val match = mapper.matchOf(game(), seats, Team.RED, WinReason.THRONE, START - MINUTE)
        assertThat(match.durationMin).isEqualTo(0)
    }

    private companion object {
        const val START = 1_790_000_000_000L
        const val MINUTE = 60_000L
    }
}
