package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.puzzles.Puzzle

/**
 * Every encoded puzzle, one line each (ENCODING.md). The book lists those whose heroes are all
 * playable; ids are `p` + the concept number, so encoders working in parallel never collide.
 */
object Puzzles {
    /** The 50 shipped puzzles in the book's order: hard (1), harder (2), hardest (3) (STATUS.md). */
    val all: List<Puzzle> = listOf(
        P01ThroughTheLine,
        P08WalkThePlank,
        P15Quickdraw,
        P17IntoTheWall,
        P19SmokeOnTheLine,
        P33EvenABlockedShotPushes,
        P34RespawnWhereItCounts,
        P35OnslaughtsForcedStep,
        P39StaticBarrier,
        P45TheLesserEvil,
        P47SwoopIn,
        P50ShootPushScoot,
        P52BattleFury,
        P55ThePushStripsTheGuard,
        P59MindGrip,
        P07RangedAtPointBlank,
        P12FastTravelWithinTheZone,
        P16GetOverHere,
        P22ChangeOfPlans,
        P23LiftUpIntoTheLine,
        P24ReverseTime,
        P25ColdSnap,
        P27RainOfArrows,
        P30ThreeObstacles,
        P32FightAsOne,
        P38StripTheSilver,
        P40CarryAway,
        P44AFistfulOfCoins,
        P46HurryUp,
        P49PreyDrivesDilemma,
        P51IceInTheVeins,
        P58UnexpectedJourney,
        P60HoldMySake,
        P02WorthTwoCounters,
        P04PriceOnHerHead,
        P05BeatTheDrum,
        P13EmptyTheSpawnPoint,
        P21ChargeOntoTheSpawnPoint,
        P26BloodFuryToTheThrone,
        P28AngryStampede,
        P29ThreatenTheGuards,
        P31KnifeOfTheLivingDead,
        P36WhoGoesFirst,
        P41TheDuelistsOath,
        P42TheCourteousTrap,
        P43CountTheMinions,
        P53DeathGrenade,
        P54ChaosIncarnate,
        P56PileOnTheFreshWave,
        P57TurnedToStone,
    )

    /** Proven but not shipped (STATUS.md "cut"): kept so they can come back. */
    val cut: List<Puzzle> = listOf(
        P03MeleeFirstThenTheHeavy,
        P06TheHandsOfTheClock,
        P09PhantomAtFiveSpaces,
        P10ShotgunSplash,
        P11ByTheWatersEdge,
        P14LongThrust,
        P18RunecasterAtMaximumRange,
        P20ClearTheSmoke,
        P37ThePoisonedClock,
        P48NecromancyAtTheBell,
    )
}
