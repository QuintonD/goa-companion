package dev.quinton.goa.feature.catalog.model

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.catalog.HeroLogSummary
import dev.quinton.goa.core.domain.catalog.HeroPilotStat
import dev.quinton.goa.core.domain.meta.HeroPilots
import dev.quinton.goa.core.domain.meta.PlayerHeroStat
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.model.player.Player
import org.junit.Test

class HeroLogSummaryTest {
    private val ana = Player("ana", "Ana", 0xFF3FC1B0, 1, 0)
    private val ben = Player("ben", "Ben", 0xFFC9A35B, 2, 0)

    private fun stat(player: String, games: Int, wins: Int) = PlayerHeroStat(player, "h", WinLoss(games, wins))

    @Test
    fun mapsTheSharedMetaResultKeepingItsOrderAndAttachingPlayers() {
        val pilots = HeroPilots(
            heroId = "h",
            record = WinLoss(7, 6),
            minGames = 3,
            best = listOf(stat("ben", 3, 3), stat("ana", 3, 2)),
            all = listOf(stat("ana", 3, 2), stat("ben", 3, 3), stat("ghost", 1, 1)),
        )
        val summary = HeroLogSummary.of(pilots, listOf(ana, ben))
        assertThat(summary.games).isEqualTo(7)
        assertThat(summary.wins).isEqualTo(6)
        assertThat(summary.minGames).isEqualTo(3)
        assertThat(summary.bestPilots.map { it.playerId }).containsExactly("ben", "ana").inOrder()
        assertThat(summary.bestPilots.first()).isEqualTo(HeroPilotStat("ben", ben, 3, 3))
        assertThat(summary.allPilots.map { it.playerId }).containsExactly("ana", "ben", "ghost").inOrder()
        assertThat(summary.allPilots.last().player).isNull()
    }

    @Test
    fun nobodyPlayedTheHero() {
        val summary = HeroLogSummary.of(HeroPilots("h", WinLoss.NONE, 3, emptyList(), emptyList()), listOf(ana))
        assertThat(summary.games).isEqualTo(0)
        assertThat(summary.allPilots).isEmpty()
        assertThat(summary.bestPilots).isEmpty()
    }
}
