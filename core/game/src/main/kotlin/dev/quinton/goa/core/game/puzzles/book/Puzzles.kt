package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.puzzles.Puzzle

/**
 * Every encoded puzzle, one line each (ENCODING.md). The book lists those whose heroes are all
 * playable; ids are `p` + the concept number, so encoders working in parallel never collide.
 */
object Puzzles {
    val all: List<Puzzle> = listOf(
        P01ThroughTheLine,
        P14LongThrust,
        P17IntoTheWall,
        P34RespawnWhereItCounts,
        P38StripTheSilver,
        P39StaticBarrier,
        P40CarryAway,
        P42TheCourteousTrap,
        P44AFistfulOfCoins,
        P51IceInTheVeins,
        P53DeathGrenade,
        P55ThePushStripsTheGuard,
    )
}
