package dev.quinton.goa.feature.board.online

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.feature.board.play.online.LobbyUi
import org.junit.Test

/** The lobby's start checklist counts what the server's room says, and names who starts. */
class OnlineLobbyChecksTest {
    private val partial =
        room(size = 2, filled = 3, hero = { listOf("arien", "brogan", null)[it] }, ready = { it == 1 })

    @Test
    fun theHostSeesSeatsHeroesAndReadiness() {
        val checks = startChecks(LobbyUi(code = CODE, room = partial, you = "acc", connected = true))
        assertThat(checks.map { it.words })
            .containsExactly("3 of 4 seats taken", "2 of 3 heroes chosen", "1 of 4 ready").inOrder()
        assertThat(checks.none { it.done }).isTrue()
    }

    @Test
    fun aGuestAlsoSeesWhoStarts() {
        val checks = startChecks(LobbyUi(code = CODE, room = partial, you = "acc-bob", connected = true))
        assertThat(checks.last().words).isEqualTo("Ann starts the game")
    }

    @Test
    fun everythingDoneReadsDone() {
        val heroes = listOf("arien", "brogan", "wasp", "tigerclaw")
        val full = room(size = 2, filled = 4, hero = { heroes[it] }, ready = { true })
        val checks = startChecks(LobbyUi(code = CODE, room = full, you = "acc", connected = true))
        assertThat(checks.map { it.words }).containsExactly("Every seat taken", "Every hero chosen", "Everyone ready")
        assertThat(checks.all { it.done }).isTrue()
    }
}
