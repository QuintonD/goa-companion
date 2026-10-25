package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.SyncServerControl
import dev.quinton.goa.core.data.SyncServerDefaults
import dev.quinton.goa.core.data.SyncServerRepository
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.data.rating.HeroPilotsRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.data.rating.ReplayRatingsRepository
import dev.quinton.goa.core.data.sync.PrefsGroupRepository
import dev.quinton.goa.core.data.sync.RelayHttp
import dev.quinton.goa.core.data.sync.RelaySyncRepository
import dev.quinton.goa.web.group.FetchRelayHttp
import dev.quinton.goa.web.group.GroupDocumentStore
import dev.quinton.goa.web.group.TabChannel
import dev.quinton.goa.web.group.WebCryptoCipher
import dev.quinton.goa.web.group.WebDraftRecordRepository
import dev.quinton.goa.web.group.WebEloConfigRepository
import dev.quinton.goa.web.group.WebGroupPrefsStore
import dev.quinton.goa.web.group.WebMatchRepository
import dev.quinton.goa.web.group.WebPlayerRepository
import dev.quinton.goa.web.group.WebSyncRepository
import dev.quinton.goa.web.group.WebSyncServerControl
import dev.quinton.goa.web.group.WebTriviaRoundRepository
import dev.quinton.goa.web.storage.scoped
import kotlinx.coroutines.Dispatchers

/**
 * W2 group: the group's synced data (players, match log, drafts, trivia rounds, merges, rating
 * constants; one `SyncDocument` in IndexedDB under `group/`, writes stamped with
 * [WebServices.clock]), the ratings replayed from it, this browser's group membership and its sync
 * with the group's relay (the phone's algorithm, shared: `RelaySyncRepository`). Owned by W2: only
 * W2 edits this file ([WebServices]). Nothing here touches storage or the network until used;
 * `syncServerControl.start()` is called once by the app's `main()`.
 */
class GroupData(
    services: WebServices,
    defaults: SyncServerDefaults = WebSyncDefaults.forThisPage(),
    http: RelayHttp = FetchRelayHttp(),
    tabs: Boolean = true,
) {
    /** Plain HTTP to a test relay on this machine is allowed (the page itself is local). */
    val allowLocalHttp: Boolean = defaults.allowLocalHttp

    private val docs = GroupDocumentStore(
        services.store.scoped("group/"),
        if (tabs) TabChannel.browser("goa-group-document") else TabChannel.None,
    )
    private val prefs = WebGroupPrefsStore(
        services.store.scoped("group/"),
        if (tabs) TabChannel.browser("goa-group-prefs") else TabChannel.None,
    )
    private val playerStore = WebPlayerRepository(docs, services.clock)

    val players: PlayerRepository = playerStore

    val playerMerges: PlayerMergeRepository = playerStore

    val matches: MatchRepository = WebMatchRepository(docs, services.clock)

    val draftRecords: DraftRecordRepository = WebDraftRecordRepository(docs, services.clock)

    val triviaRounds: TriviaRoundRepository = WebTriviaRoundRepository(docs, services.clock)

    val eloConfig: EloConfigRepository = WebEloConfigRepository(docs, services.clock)

    // One replay behind all three, as on the phone.
    private val replay = ReplayRatingsRepository(
        matches,
        draftRecords,
        eloConfig,
        services.content.catalog,
        services.scope,
        Dispatchers.Default,
    )

    val ratings: RatingsRepository = replay

    val ratingsSnapshot: RatingsSnapshotRepository = replay

    val heroPilots: HeroPilotsRepository = replay

    private val groupStore = PrefsGroupRepository(prefs, defaults)

    val group: GroupRepository = groupStore

    val sync: SyncRepository = WebSyncRepository(docs, services.clock)

    val syncServer: SyncServerRepository =
        RelaySyncRepository(groupStore, sync, docs.changes, services.clock, http, WebCryptoCipher)

    val syncServerControl: SyncServerControl =
        WebSyncServerControl(group, syncServer, services.scope, services.clock::now)

    /** Starts automatic server sync (app open, back on screen, after changes); once, from `main()`. */
    fun start() = (syncServerControl as WebSyncServerControl).start()
}
