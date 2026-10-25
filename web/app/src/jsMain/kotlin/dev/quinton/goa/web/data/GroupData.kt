package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.SyncServerControl
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.data.rating.HeroPilotsRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.web.data.stub.W2DraftRecordStub
import dev.quinton.goa.web.data.stub.W2EloConfigStub
import dev.quinton.goa.web.data.stub.W2GroupStub
import dev.quinton.goa.web.data.stub.W2MatchStub
import dev.quinton.goa.web.data.stub.W2PlayerStub
import dev.quinton.goa.web.data.stub.W2RatingsStub
import dev.quinton.goa.web.data.stub.W2SyncStub
import dev.quinton.goa.web.data.stub.W2TriviaRoundStub

/**
 * W2 group (feature/web-group): the group's synced data (players, match log, drafts, trivia rounds,
 * merges, rating constants; one `SyncDocument` in IndexedDB, writes stamped with
 * [WebServices.clock]), the ratings replayed from it, this browser's group membership and sync.
 * Owned by W2: only W2 edits this file ([WebServices]).
 */
@Suppress("UnusedPrivateProperty", "unused")
class GroupData(private val services: WebServices) {
    val players: PlayerRepository = W2PlayerStub

    val playerMerges: PlayerMergeRepository = W2PlayerStub

    val matches: MatchRepository = W2MatchStub

    val draftRecords: DraftRecordRepository = W2DraftRecordStub

    val triviaRounds: TriviaRoundRepository = W2TriviaRoundStub

    val eloConfig: EloConfigRepository = W2EloConfigStub

    // One ReplayRatingsRepository(matches, draftRecords, eloConfig, services.content.catalog,
    // services.scope, Dispatchers.Default) behind all three, as on the phone.
    val ratings: RatingsRepository = W2RatingsStub

    val ratingsSnapshot: RatingsSnapshotRepository = W2RatingsStub

    val heroPilots: HeroPilotsRepository = W2RatingsStub

    val group: GroupRepository = W2GroupStub

    val sync: SyncRepository = W2SyncStub

    val syncServer: SyncServerRepository = W2SyncStub

    val syncServerControl: SyncServerControl = W2SyncStub
}
