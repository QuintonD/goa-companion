package dev.quinton.goa.hub.players

import dev.quinton.goa.core.domain.rating.RatingsSnapshot
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.store.DeviceSlot
import dev.quinton.goa.hub.store.GroupData
import dev.quinton.goa.hub.store.SharedSlots
import io.ktor.server.application.ApplicationCall
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/**
 * What every Players page reads (ADR-030): the group's data as the phones' screens see it (merged
 * players resolved, merged-away ones hidden), the catalog, and one ratings snapshot of the whole
 * log (`GroupRatings`: [RatingsSnapshot.compute], the phones' replay). Nothing here computes a statistic.
 */
class PlayersData(
    val data: GroupData,
    val catalog: Catalog,
    val snapshot: RatingsSnapshot,
    val zone: ZoneId,
    val today: LocalDate,
) {
    /** Every visible player, archived included (as `observePlayers(includeArchived = true)`). */
    val players: List<Player> = data.players(includeArchived = true)

    val byId: Map<String, Player> = players.associateBy { it.id }

    /** The log, newest first, pilots resolved. */
    val matches: List<Match> get() = data.matches

    fun match(id: String): Match? = data.match(id)

    /** True when [id] was a match that has been deleted (a tombstone exists). */
    fun wasDeleted(id: String): Boolean =
        data.document.deletions.any { it.kind == DeletionKind.MATCH && it.id == id }

    companion object {
        fun of(ctx: HubContext): PlayersData {
            val doc = ctx.groupStore.document.value
            val snapshot = ctx.ratings.of(doc)
            return PlayersData(GroupData(doc), ctx.catalog.catalog, snapshot, ctx.clock.zone, ctx.clock.today())
        }
    }
}

/** A match this browser just deleted, for "Undo" in the log (the phone's `RecentlyDeletedMatch`). */
@Serializable
data class DeletedMatch(val match: Match, val deletedAt: Long)

/** The Players area's per-browser state (ADR-030 §3). */
object PlayersSlots {
    /** "Different people" answers on this browser, as `PlayerMatchUp.pairKey`s (ADR-031). */
    val DifferentPeople = DeviceSlot("players-different", SetSerializer(String.serializer()))

    /** The match last deleted here, offered for undo for [UNDO_WINDOW_MS]. */
    val Undo = DeviceSlot("players-undo", DeletedMatch.serializer())

    /** A one-time message for the next Players page (the phone's snackbar). */
    val Flash = DeviceSlot("players-flash", String.serializer())

    /** As long as the phone offers "Undo" after a delete. */
    const val UNDO_WINDOW_MS = 30_000L
}

/** This browser's player ("Which player are you?", written by the Group pages), resolved through the merges. */
fun HubContext.meId(call: ApplicationCall, data: GroupData): String? =
    deviceStore.read(device(call).id, SharedSlots.Me)?.let { data.player(it)?.id }

/** Shows [message] once on the next Players page of this browser. */
fun HubContext.flash(call: ApplicationCall, message: String) =
    deviceStore.write(device(call).id, PlayersSlots.Flash, message)

/** The pending one-time message, removed as it is read. */
suspend fun HubContext.takeFlash(call: ApplicationCall): String? {
    var taken: String? = null
    deviceStore.update(device(call).id, PlayersSlots.Flash) {
        taken = it
        null
    }
    return taken
}
