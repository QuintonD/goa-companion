package dev.quinton.goa.feature.board.play.online.leaderboard

import dev.quinton.goa.core.online.LeaderRow
import dev.quinton.goa.core.online.LeaderboardSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeaderboardPresenterTest {
    private val snapshot = LeaderboardSnapshot(
        schema = 1,
        generatedAt = 0,
        logSha256 = "abc",
        matches = 6,
        eloConfig = 1,
        players = listOf(LeaderRow("a", "Ann", 1560, 6, 5), LeaderRow("b", "Former player", 1440, 6, 1)),
        // A hero whose id happens to be an account id is still not "you".
        heroes = listOf(LeaderRow("a", "Arien", 1530, 6, 4)),
    )

    private fun load(
        ageMillis: Long = 12 * MINUTE,
        origin: LeaderboardOrigin = LeaderboardOrigin.PAGES,
        board: LeaderboardSnapshot = snapshot,
    ) = LeaderboardLoad(board, origin, ageMillis)

    @Test
    fun theWordsForAFreshBoard() {
        val cases = listOf(
            0L to "Updated just now",
            59_999L to "Updated just now",
            MINUTE to "Updated 1 min ago",
            12 * MINUTE to "Updated 12 min ago",
            59 * MINUTE to "Updated 59 min ago",
            60 * MINUTE to "Updated 1 h ago",
            23 * HOUR to "Updated 23 h ago",
            24 * HOUR to "Updated 1 day ago",
            3 * DAY to "Updated 3 days ago",
        )
        for ((age, words) in cases) {
            assertEquals(words, LeaderboardPresenter.status(load(age)))
            assertEquals(words, LeaderboardPresenter.status(load(age, LeaderboardOrigin.SERVER)))
        }
    }

    @Test
    fun theWordsForTheLastCopy() {
        val ui = LeaderboardPresenter.present(load(3 * DAY, LeaderboardOrigin.LAST_COPY))
        assertEquals("Last copy, from 3 days ago: the server can't be reached", ui.status)
        assertTrue(ui.stale)
        assertEquals(2, ui.rows.size)
        assertEquals(
            "Last copy, from 1 day ago: the server can't be reached",
            LeaderboardPresenter.status(load(DAY, LeaderboardOrigin.LAST_COPY)),
        )
        assertEquals(
            "Last copy, from 40 min ago: the server can't be reached",
            LeaderboardPresenter.status(load(40 * MINUTE, LeaderboardOrigin.LAST_COPY)),
        )
    }

    @Test
    fun aFreshBoardIsNotStale() {
        assertFalse(LeaderboardPresenter.present(load()).stale)
        assertFalse(LeaderboardPresenter.present(load(origin = LeaderboardOrigin.SERVER)).stale)
    }

    @Test
    fun noBoardAnywhereSaysSo() {
        val ui = LeaderboardPresenter.present(null)
        assertEquals("No leaderboard yet: the server can't be reached", ui.empty)
        assertNull(ui.status)
        assertTrue(ui.rows.isEmpty())
        assertFalse(ui.stale)
    }

    @Test
    fun aFirstReadIsNotAFailureYet() {
        val ui = LeaderboardPresenter.present(null, loading = true)
        assertTrue(ui.loading)
        assertNull(ui.empty)
    }

    @Test
    fun aBoardWithNoRowsYetSaysWhatItTakes() {
        val none = snapshot.copy(matches = 2, players = emptyList(), heroes = emptyList())
        val players = LeaderboardPresenter.present(load(board = none))
        assertEquals("No players yet: a player appears after 5 rated games", players.empty)
        assertEquals("Updated 12 min ago", players.status)
        val heroes = LeaderboardPresenter.present(load(board = none), LeaderboardTab.HEROES)
        assertEquals("No heroes yet: a hero appears after 5 rated games", heroes.empty)
    }

    @Test
    fun bothTabsAreOfferedAndTheChosenOneShows() {
        val players = LeaderboardPresenter.present(load())
        assertEquals(listOf("Players", "Heroes"), players.tabs.map { it.label })
        assertEquals(LeaderboardTab.PLAYERS, players.tab)
        assertEquals(listOf("Ann", "Former player"), players.rows.map { it.name })

        val heroes = LeaderboardPresenter.present(load(), LeaderboardTab.HEROES)
        assertEquals(LeaderboardTab.HEROES, heroes.tab)
        assertEquals(listOf("Arien"), heroes.rows.map { it.name })
    }

    @Test
    fun rowsCarryRankAndNumbersInTheFilesOrder() {
        val row = LeaderboardPresenter.present(load()).rows[1]
        assertEquals(LeaderboardRowUi(2, "b", "Former player", 1440, 6, 1, isYou = false), row)
    }

    @Test
    fun yourOwnRowIsMarkedOnThePlayersTabOnly() {
        fun marks(tab: LeaderboardTab, accountId: String?) =
            LeaderboardPresenter.present(load(), tab, accountId).rows.map { it.isYou }

        assertEquals(listOf(false, true), marks(LeaderboardTab.PLAYERS, "b"))
        assertEquals(listOf(false), marks(LeaderboardTab.HEROES, "a"))
        assertEquals(listOf(false, false), marks(LeaderboardTab.PLAYERS, "nobody"))
        assertEquals(listOf(false, false), marks(LeaderboardTab.PLAYERS, null))
    }

    @Test
    fun aRefreshKeepsTheBoardOnScreen() {
        val ui = LeaderboardPresenter.present(load(), loading = true)
        assertTrue(ui.loading)
        assertEquals(2, ui.rows.size)
        assertEquals("Updated 12 min ago", ui.status)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
