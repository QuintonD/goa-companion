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
        P02WorthTwoCounters,
        P03MeleeFirstThenTheHeavy,
        P04PriceOnHerHead,
        P05BeatTheDrum,
        P06TheHandsOfTheClock,
        P07RangedAtPointBlank,
        P09PhantomAtFiveSpaces,
        P11ByTheWatersEdge,
        P13EmptyTheSpawnPoint,
        P15Quickdraw,
        P31KnifeOfTheLivingDead,
        P32FightAsOne,
        P33EvenABlockedShotPushes,
        P36WhoGoesFirst,
        P37ThePoisonedClock,
        P41TheDuelistsOath,
        P43CountTheMinions,
        P45TheLesserEvil,
        P46HurryUp,
        P47SwoopIn,
    )
}
