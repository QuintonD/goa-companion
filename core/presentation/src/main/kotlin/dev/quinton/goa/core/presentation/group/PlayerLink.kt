package dev.quinton.goa.core.presentation.group

import dev.quinton.goa.core.domain.player.PlayerMatchUp
import dev.quinton.goa.core.domain.player.PlayerNames
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import java.time.Instant
import java.time.ZoneId

/** After "Yes, I'm …": this device already was [old] (solo use before joining); merge it into [into]? */
data class MergeOffer(val old: Player, val oldMatches: Int, val into: Player)

/** "Add me" checks first (ADR-031): players whose names are like [name]. */
data class AddCheck(
    val name: String,
    val candidates: List<Player>,
    /** One of them has exactly this name, so a new player needs another one. */
    val nameTaken: Boolean,
)

/**
 * "Which player are you?" (ADR-025, ADR-031): linking a phone or a browser to its player. The
 * rules the Android screen and the hub's `/group/me` page share; each keeps "me" and the invite's
 * player in its own storage (the phone's `group_sync` DataStore, the browser's device slots).
 *
 * `active` lists are the visible, non-archived players (merged-away duplicates hidden), as the
 * phone's `PlayerRepository.observePlayers()` and the hub's `GroupData.players()` return them.
 */
object PlayerLink {
    /** Longest name "Add me" takes, as the phone's text field. */
    const val MAX_NAME = 24

    /** The list to pick yourself from: active players by name. */
    fun choices(active: List<Player>): List<Player> = active.sortedBy { it.name.lowercase() }

    /** This device's player, read through the merges (null when none is chosen). */
    fun me(meId: String?, merges: PlayerMerges): String? = meId?.let(merges::resolve)

    /**
     * The player a personal invite was made for, to ask "You're <name>?": resolved through the
     * merges, active, and not already this device's player. Null when there is nothing to ask, or
     * the player isn't in the data yet (the first sync failed): then the list shows without a question.
     */
    fun invited(invitedId: String?, meId: String?, merges: PlayerMerges, active: List<Player>): Player? {
        val me = me(meId, merges)
        val id = invitedId?.let(merges::resolve)?.takeIf { it != me } ?: return null
        return active.firstOrNull { it.id == id }
    }

    /**
     * "Yes, I'm <invited>": if this device was another active player before (used alone before
     * joining), offer to merge that one in, with how many [matches] (pilots resolved) it played.
     */
    fun mergeOffer(
        invited: Player,
        meId: String?,
        merges: PlayerMerges,
        active: List<Player>,
        matches: List<Match>,
    ): MergeOffer? {
        val oldId = me(meId, merges)?.takeIf { it != invited.id } ?: return null
        val old = active.firstOrNull { it.id == oldId } ?: return null
        return MergeOffer(old, matches.count { oldId in it.playerIds }, invited)
    }

    /**
     * "Add me to the group": the players with a name like [name] ("Is one of these you?"), or null
     * when there are none and [name] can be added straight away.
     */
    fun addCheck(name: String, active: List<Player>): AddCheck? {
        val similar = PlayerMatchUp.similar(name, active)
        if (similar.isEmpty()) return null
        return AddCheck(name, similar, nameTaken = similar.any { PlayerNames.same(it.name, name) })
    }

    /** "Add me" without a name. */
    const val NAME_MISSING = "Enter your name"

    /** "Add me" with the name of a player who isn't offered (archived players' names stay taken). */
    fun nameTaken(existing: Player): String =
        "“${existing.name}” is already in the group. If that's you, pick it above."

    /**
     * The player "Add me" creates: the first palette colour no one uses yet (else the next in
     * turn) and the next crest. [everyone] includes archived players; [palette] is the stored
     * (dark) tone of each swatch, in order; [crests] how many crests there are.
     */
    fun newPlayer(
        id: String,
        name: String,
        everyone: List<Player>,
        palette: List<Long>,
        crests: Int,
        now: Long,
    ): Player {
        val used = everyone.map { it.color }.toSet()
        return Player(
            id = id,
            name = name,
            color = palette.firstOrNull { it !in used } ?: palette[Math.floorMod(everyone.size, palette.size)],
            avatarId = everyone.size % crests,
            createdAt = now,
        )
    }

    /** "The two played in the same match…": one line per match, "2026-10-12 · Ann, Bob vs Cid, Dee", oldest first. */
    fun refusalLines(matches: List<Match>, people: Map<String, Player>, zone: ZoneId): List<String> =
        matches.sortedBy { it.playedAt }.map { m ->
            fun side(team: Team) = m.team(team).map { people[it.playerId]?.name ?: "Unknown player" }
                .distinct().joinToString()
            val day = Instant.ofEpochMilli(m.playedAt).atZone(zone).toLocalDate()
            "$day · ${side(Team.RED)} vs ${side(Team.BLUE)}"
        }

    /** The refusal's explanation; [me] is who this [device] ("phone", "browser") is now. */
    fun refusalText(me: String?, device: String = "phone"): String =
        "The two played in the same match, so they are two people (one person can't sit on both " +
            "teams, and teammates are two players). Both stay" + (me?.let { "; this $device is $it now." } ?: ".")

    /** "Merge your old player into <into>?" */
    fun mergeOfferText(offer: MergeOffer, device: String = "phone"): String {
        val old = offer.old.name
        val into = offer.into.name
        val matches = if (offer.oldMatches == 1) "1 match" else "${offer.oldMatches} matches"
        return "Before, this $device was “$old” ($matches). If that was you too, merge $old into $into: those " +
            "matches count for $into, on every phone after the next sync, and $old disappears from the " +
            "lists. This can't be undone. If $old is someone else, keep both."
    }

    /** "Is one of these you?" */
    fun addCheckText(check: AddCheck): String = if (check.nameTaken) {
        "“${check.name}” is already in the group. If that's you, pick it. If not, add yourself " +
            "with another name, like a last initial."
    } else {
        "These players have a name like “${check.name}”. If one is you, pick it: your matches and " +
            "rating are already there."
    }
}
