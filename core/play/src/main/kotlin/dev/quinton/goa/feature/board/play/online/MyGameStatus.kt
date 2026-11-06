package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.online.MyGame

/** Where a game to go back to stands, for its row: the ones that need you first. */
enum class MyGameState { YOUR_TURN, WAITING, IN_PLAY, OVER }

/** [words]: "Your turn", "Waiting for Bob", "In play", "Finished". */
data class MyGameStatus(val state: MyGameState, val words: String)

object MyGameStatuses {
    /** [you] is the signed-in account; the server's `asked` names seats, its `over` an ended game (ONLINE.md). */
    fun of(game: MyGame, you: String?): MyGameStatus {
        if (game.over) return MyGameStatus(MyGameState.OVER, "Finished · see the result")
        val asked = game.seats.filter { it.seat in game.asked }
        return when {
            asked.any { you != null && it.accountId == you } -> MyGameStatus(MyGameState.YOUR_TURN, "Your turn")

            asked.isNotEmpty() -> {
                val names = asked.map { it.name.ifBlank { FORMER } }.distinct()
                MyGameStatus(MyGameState.WAITING, "Waiting for ${names(names)}")
            }

            else -> MyGameStatus(MyGameState.IN_PLAY, "In play")
        }
    }

    /** Your turn first, then waiting, in play and finished; the server's order within each. */
    fun sorted(games: List<MyGame>, you: String?): List<MyGame> = games.sortedBy { of(it, you).state.ordinal }

    private fun names(names: List<String>): String = when (names.size) {
        1 -> names.single()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    /** A deleted account's seat, as the leaderboard and the log name it. */
    private const val FORMER = "Former player"
}
