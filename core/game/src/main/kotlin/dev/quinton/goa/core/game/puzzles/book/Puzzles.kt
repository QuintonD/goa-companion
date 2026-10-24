package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.puzzles.Puzzle

/**
 * Every encoded puzzle, one line each (ENCODING.md). The book lists those whose heroes are all
 * playable; ids are `p` + the concept number, so encoders working in parallel never collide.
 */
object Puzzles {
    /** The 50 shipped puzzles in the book's order (STATUS.md). */
    val all: List<Puzzle> = listOf(
        P01ThroughTheLine,
        P02WorthTwoCounters,
        P04PriceOnHerHead,
        P05BeatTheDrum,
        P07RangedAtPointBlank,
        P08WalkThePlank,
        P12FastTravelWithinTheZone,
        P13EmptyTheSpawnPoint,
        P15Quickdraw,
        P16GetOverHere,
        P17IntoTheWall,
        P19SmokeOnTheLine,
        P21ChargeOntoTheSpawnPoint,
        P22ChangeOfPlans,
        P23LiftUpIntoTheLine,
        P24ReverseTime,
        P25ColdSnap,
        P26BloodFuryToTheThrone,
        P27RainOfArrows,
        P28AngryStampede,
        P29ThreatenTheGuards,
        P30ThreeObstacles,
        P31KnifeOfTheLivingDead,
        P32FightAsOne,
        P33EvenABlockedShotPushes,
        P34RespawnWhereItCounts,
        P35OnslaughtsForcedStep,
        P36WhoGoesFirst,
        P38StripTheSilver,
        P39StaticBarrier,
        P40CarryAway,
        P41TheDuelistsOath,
        P42TheCourteousTrap,
        P43CountTheMinions,
        P44AFistfulOfCoins,
        P45TheLesserEvil,
        P46HurryUp,
        P47SwoopIn,
        P49PreyDrivesDilemma,
        P50ShootPushScoot,
        P51IceInTheVeins,
        P52BattleFury,
        P53DeathGrenade,
        P54ChaosIncarnate,
        P55ThePushStripsTheGuard,
        P56PileOnTheFreshWave,
        P57TurnedToStone,
        P58UnexpectedJourney,
        P59MindGrip,
        P60HoldMySake,
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
