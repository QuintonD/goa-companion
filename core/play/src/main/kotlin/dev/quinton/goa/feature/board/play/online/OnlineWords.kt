package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.VoidReason

/** What online play says to people, in one place, for the phone and the web. */
object OnlineWords {
    /** The server is down or out of reach; running games resume from their saved answers when it returns. */
    const val UNREACHABLE: String =
        "The server can't be reached; your games wait for it. Playing against the computer still works."

    const val RECONNECTING: String = "Connection lost. Getting back to your game…"

    /** The answer reached the server after the question had moved on, or wasn't accepted. */
    const val ANSWER_NOT_TAKEN: String = "That answer didn't count, so here is the question as it stands now."

    /** The server no longer has the game, and never sent how it ended. */
    const val GAME_GONE: String = "This game isn't on the server any more."

    const val NOT_A_CODE: String = "That doesn't look like a room code. It's 8 letters and numbers, or the whole link."

    const val NAME_NEEDED: String = "Pick a name for the others to see."

    const val RECOVERY_CODE_NEEDED: String = "Enter the recovery code from your other device."

    const val SEAT_FIRST: String = "Take a seat first."

    const val HERO_FIRST: String = "Pick a hero first."

    const val SEAT_TAKEN: String = "Someone is already sitting there."

    const val HERO_TAKEN: String = "That hero is already taken, or can't be played online yet."

    const val ROOM_CLOSED: String = "This room has closed."

    const val SAVE_RECOVERY_CODE: String =
        "This code is the only way back to your account on a new phone: there is no email, and it won't be " +
            "shown again. Save it somewhere safe."

    fun refusal(reason: Refusal, retryAfterSeconds: Int? = null): String {
        val words = when (reason) {
            Refusal.UPDATE_APP ->
                "This needs a newer version of the app. Update it and come back: your seat waits for you."

            Refusal.ROOMS_FULL ->
                "Every room on the server is in use right now. Playing against the computer still works."

            Refusal.NO_SUCH_ROOM -> "There's no room with that code. Check it with whoever invited you."

            Refusal.ROOM_TAKEN -> "Someone got there first: that room, seat or hero has been taken."

            Refusal.BAD_TOKEN ->
                "The server doesn't know this device's account any more. Your recovery code brings it back."

            Refusal.RATE_LIMITED -> "That was too many tries in a short time."

            Refusal.DRAINING ->
                "The server is about to be updated, so there are no new rooms for now. Running games carry on."

            Refusal.NOT_HOST -> "Only the person who made the room can start it."

            Refusal.NOT_READY -> "Every seat needs a player with a hero, and everyone ready."

            Refusal.TOO_MANY_GAMES -> "You already have 3 games running. Finish or leave one first."

            Refusal.TOO_MANY_ROOMS -> "You already have 3 open rooms. Start or leave one first."
        }
        return if (retryAfterSeconds == null) words else "$words ${tryAgainIn(retryAfterSeconds)}"
    }

    private fun tryAgainIn(seconds: Int): String = when {
        seconds < 60 -> "Try again in a moment."
        seconds < 120 -> "Try again in about a minute."
        else -> "Try again in about ${seconds / 60} minutes."
    }

    fun voided(reason: VoidReason): String = when (reason) {
        VoidReason.RULES_CHANGED -> "A rules fix changed this game's past, so it can't go on. It won't count."
        VoidReason.ABANDONED -> "Nobody came back for a day, so this game was closed. It won't count."
    }

    fun presence(presence: Presence): String = when (presence) {
        Presence.HERE -> "here"
        Presence.AWAY -> "away"
        Presence.COMPUTER -> "away · the computer answers until they're back"
    }
}
