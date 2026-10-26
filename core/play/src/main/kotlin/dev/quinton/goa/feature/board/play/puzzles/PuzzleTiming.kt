package dev.quinton.goa.feature.board.play.puzzles

/** How long things take on the puzzle screen; the phone and the web app use the same (tests run on virtual time). */
data class PuzzleTiming(
    /** A nudge comes by itself after this long without progress (nudges on). */
    val idleNudgeMs: Long = 90_000L,
    /** Each line of the other side's replies shows for this long… */
    val replyLineMs: Long = 700L,
    /** …but all of them together for no longer than this. */
    val replyMaxMs: Long = 3_500L,
    /** The last reply stays this long before the board catches up. */
    val replyHoldMs: Long = 600L,
)
