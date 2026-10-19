package dev.quinton.goa.hub.store

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.RatingsSnapshot
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.draft.DraftData
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.heroes.HeroRecords
import dev.quinton.goa.hub.match
import dev.quinton.goa.hub.player
import dev.quinton.goa.hub.players.PlayersData
import dev.quinton.goa.hub.testHub
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Every hub page reads its numbers from one snapshot, computed from exactly the inputs the phone's
 * `ReplayRatingsRepository` uses: the log with merged pilots resolved, Unknown heroes cleared with
 * the catalog, and the group's constants as the phone reads them (sanitized).
 */
class GroupRatingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `players, heroes, draft and settings pages share the phones' replay of the same data`() {
        val clock = FakeClock()
        val hub = testHub(tmp.root.toPath(), clock)
        val known = hub.catalog.catalog.heroes.map { it.id }
        val doc = SyncDocument(
            players = listOf(player("ann"), player("ann-2"), player("bob"), player("cy")),
            matches = listOf(
                match("m1", listOf("ann"), listOf("bob"), playedAt = 1, updatedAt = 1),
                match("m2", listOf("ann-2"), listOf("cy"), playedAt = 2, updatedAt = 2),
                match("m3", listOf("bob"), listOf("cy"), playedAt = 3, updatedAt = 3),
            ).map { m ->
                // Real heroes for most seats, one hero this catalog doesn't know.
                m.copy(participants = m.participants.mapIndexed { i, p -> p.copy(heroId = known[i]) })
            } + match("m4", listOf("ann"), listOf("cy"), playedAt = 4, updatedAt = 4),
            playerMerges = listOf(PlayerMerge("ann-2", "ann", 5)),
            // Constants as an app with wider limits might send them: the phones clamp them when reading.
            eloConfig = StampedEloConfig(EloConfig.DEFAULT.copy(kProvisional = 500.0), 7),
        )
        runBlocking { hub.groupStore.mergeIn(doc) }

        val stored = hub.groupStore.read()
        val merges = PlayerMerges(stored.playerMerges)
        val phone = RatingsSnapshot.compute(
            merges.resolve(stored.matches.sortedWith(compareByDescending<Match> { it.playedAt })),
            stored.drafts,
            stored.eloConfig.config.sanitized(),
            known.toSet(),
            ZoneId.of("UTC"),
        )
        val web = hub.ratings.current()
        assertThat(web.config.kProvisional).isEqualTo(100.0)
        assertThat(web.ratings.players).isEqualTo(phone.ratings.players)
        assertThat(web.ratings.heroes).isEqualTo(phone.ratings.heroes)
        assertThat(web.ratings.players.keys).doesNotContain("ann-2")

        val ctx = HubContext(hub)
        assertThat(PlayersData.of(ctx).snapshot).isSameInstanceAs(web)
        assertThat(HeroRecords.current(ctx).heroRatings).isEqualTo(phone.ratings.heroes)
        assertThat(DraftData(ctx).ratings()).isEqualTo(phone.ratings.players)
    }
}
