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
        P08WalkThePlank,
        P10ShotgunSplash,
        P12FastTravelWithinTheZone,
        P19SmokeOnTheLine,
        P20ClearTheSmoke,
    )
}
