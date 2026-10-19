package dev.quinton.goa.hub.players

import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.cookie
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.ParametersBuilder
import io.ktor.http.parameters

/**
 * A typical group for the Players pages (synthetic names; heroes are the bundled catalog's, the
 * same the hub reads): six players with one archived, a duplicate "Ann B" merged into Ann, a log
 * with a double pilot, a handicap game, an Unknown hero (an id this catalog doesn't know and an
 * empty seat), a pending match from a draft, and a draft with bans.
 */
object PlayersTestGroup {
    /** Day N of the test month, 18:30 UTC (FakeClock's zone), before FakeClock's start. */
    fun at(day: Int, hour: Int = 18): Long = DAY0 + (day - 1) * DAY + hour * HOUR + 30 * MINUTE

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** 2025-09-01T00:00Z, a month before `FakeClock`'s default start (2025-10-09). */
    const val DAY0 = 1_756_684_800_000L

    val ann = Player("p-ann", "Ann", 0xFF3FC1B0, avatarId = 0, createdAt = 1)
    val bob = Player("p-bob", "Bob", 0xFFEE7F6F, avatarId = 1, createdAt = 2)
    val cid = Player("p-cid", "Cid", 0xFF62A8F2, avatarId = 2, createdAt = 3)
    val dee = Player("p-dee", "Dee", 0xFFB892E6, avatarId = 3, createdAt = 4)
    val eve = Player("p-eve", "Eve", 0xFFEE9A52, avatarId = 4, createdAt = 5)
    val fay = Player("p-fay", "Fay", 0xFFC3CCD6, avatarId = 5, createdAt = 6, archived = true)

    /** Ann, added again on another phone and merged into Ann. */
    val annB = Player("p-ann-b", "Ann B", 0xFF6CC27A, avatarId = 6, createdAt = 7)

    val players = listOf(ann, bob, cid, dee, eve, fay, annB)

    const val STRANGER = "hero-from-a-newer-catalog"

    fun red(p: Player, hero: String?, handicap: Boolean = false) = Participant(p.id, hero, Team.RED, handicap)

    fun blue(p: Player, hero: String?, handicap: Boolean = false) = Participant(p.id, hero, Team.BLUE, handicap)

    fun match(
        id: String,
        day: Int,
        winner: Team?,
        vararg seats: Participant,
        status: MatchStatus = MatchStatus.COMPLETED,
        condition: WinCondition? = WinCondition.THRONE,
        duration: Int? = 55,
        notes: String = "",
        hour: Int = 18,
        draftId: String? = null,
    ) = Match(
        id = id,
        createdAt = at(day, hour),
        playedAt = at(day, hour),
        status = status,
        mode = GameMode.QUICK,
        winner = winner,
        winCondition = if (status == MatchStatus.COMPLETED) condition else null,
        durationMin = duration,
        notes = notes,
        draftId = draftId,
        participants = seats.toList(),
        updatedAt = at(day, hour),
    )

    /** [h]: at least 10 hero ids from the catalog. */
    fun matches(h: List<String>): List<Match> = listOf(
        match("m01", 3, Team.RED, red(ann, h[0]), red(bob, h[1]), blue(cid, h[2]), blue(dee, h[3])),
        match(
            "m02",
            4,
            Team.BLUE,
            red(ann, h[0]),
            red(cid, h[4]),
            blue(bob, h[1]),
            blue(dee, h[5]),
            condition = WinCondition.LIFE_COUNTERS,
        ),
        // Ann double-pilots two heroes, one with the Handicap card.
        match("m03", 5, Team.RED, red(ann, h[0]), red(ann, h[6], handicap = true), blue(bob, h[2]), blue(cid, h[3])),
        match("m04", 6, Team.RED, red(ann, h[0]), red(dee, h[1]), blue(eve, h[2]), blue(bob, h[7])),
        // Logged on the other phone with Ann's duplicate: counts as Ann after the merge.
        match("m05", 8, Team.BLUE, red(cid, h[0]), red(dee, h[8]), blue(annB, h[1]), blue(bob, h[2])),
        // An Unknown hero: one id this catalog doesn't know, one seat logged without a hero.
        match(
            "m06",
            10,
            Team.RED,
            red(ann, h[0]),
            red(bob, STRANGER),
            blue(cid, null),
            blue(dee, h[2]),
            condition = WinCondition.LAST_PUSH,
        ),
        match(
            "m07", 12, Team.RED,
            red(
                ann,
                h[0],
            ),
            red(eve, h[3]), blue(bob, h[1]), blue(cid, h[2]), duration = 71, notes = "Comeback on the last wave",
        ),
        match("m08", 14, Team.BLUE, red(dee, h[0]), red(eve, h[4]), blue(ann, h[1]), blue(fay, h[5])),
        match("m09", 15, Team.RED, red(ann, h[1]), red(bob, h[0]), blue(cid, h[2]), blue(dee, h[3]), hour = 20),
        match(
            "m10", 15, null,
            red(
                ann,
                h[3],
            ),
            red(
                cid,
                h[4],
            ),
            blue(bob, h[5]), blue(eve, h[6]), status = MatchStatus.PENDING, duration = null, hour = 21, draftId = "d1",
        ),
    )

    fun drafts(h: List<String>) = listOf(
        DraftRecord(
            id = "d1",
            createdAt = at(15, 21),
            methodId = "house",
            methodKind = DraftMethodKind.CUSTOM,
            pool = h.take(10),
            teamA = Team.RED,
            steps = listOf(
                DraftStepRecord(Team.RED, DraftAction.BAN, h[8]),
                DraftStepRecord(Team.BLUE, DraftAction.BAN, h[9]),
            ),
            matchId = "m10",
        ),
    )

    fun document(hub: Hub): SyncDocument {
        val heroes = hub.catalog.catalog.heroes.map { it.id }.sorted()
        return SyncDocument(
            players = players,
            matches = matches(heroes),
            drafts = drafts(heroes),
            playerMerges = listOf(PlayerMerge(annB.id, ann.id, 10)),
        )
    }

    suspend fun seed(hub: Hub) {
        hub.groupStore.mergeIn(document(hub))
    }
}

/** GET with this browser's cookie. */
suspend fun HttpClient.page(path: String, token: String): HttpResponse = get(path) {
    header(HttpHeaders.Cookie, cookie(token))
}

/** A form POST from the hub's own pages (cookie + same-origin `Origin`). */
suspend fun HttpClient.send(
    path: String,
    token: String,
    origin: String? = HUB_URL,
    fields: ParametersBuilder.() -> Unit = {},
): HttpResponse = submitForm(path, parameters { fields() }) {
    header(HttpHeaders.Cookie, cookie(token))
    origin?.let { header(HttpHeaders.Origin, it) }
}
