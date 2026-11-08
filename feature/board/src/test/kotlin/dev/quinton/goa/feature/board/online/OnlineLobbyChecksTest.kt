package dev.quinton.goa.feature.board.online

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.feature.board.play.online.LobbyUi
import org.junit.Test

/** What the lobby's start waits for, counted from the server's room once per seat, and who starts. */
class OnlineLobbyChecksTest {
    private val partial =
        room(size = 2, filled = 3, hero = { listOf("arien", "brogan", null)[it] }, ready = { it == 1 })

    @Test
    fun theHostSeesEachSeatOnce() {
        val blockers = startBlockers(LobbyUi(code = CODE, room = partial, you = "acc", connected = true))
        assertThat(blockers.map { it.kind })
            .containsExactly(BlockerKind.JOIN, BlockerKind.HERO, BlockerKind.READY).inOrder()
        assertThat(blockerSummary(blockers)).isEqualTo(
            "Waiting for one more player to join, Cid to choose a hero and you to get ready.",
        )
    }

    @Test
    fun aGuestAlsoSeesWhoStarts() {
        val blockers = startBlockers(LobbyUi(code = CODE, room = partial, you = "acc-bob", connected = true))
        assertThat(blockers.last().words).isEqualTo("Ann to start the game")
    }

    @Test
    fun nothingHoldsUpAFullReadyRoom() {
        val heroes = listOf("arien", "brogan", "wasp", "tigerclaw")
        val full = room(size = 2, filled = 4, hero = { heroes[it] }, ready = { true })
        assertThat(startBlockers(LobbyUi(code = CODE, room = full, you = "acc", connected = true))).isEmpty()
    }
}
