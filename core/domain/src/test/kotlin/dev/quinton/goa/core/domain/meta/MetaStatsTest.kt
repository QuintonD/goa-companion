package dev.quinton.goa.core.domain.meta

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.domain.rating.blue
import dev.quinton.goa.core.domain.rating.match
import dev.quinton.goa.core.domain.rating.red
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class MetaStatsTest {
    @Test
    fun `wilson lower bound matches reference values`() {
        assertThat(MetaStats.wilsonLowerBound(8, 10)).isWithin(1e-12).of(0.49015684672072346)
        assertThat(MetaStats.wilsonLowerBound(3, 3)).isWithin(1e-12).of(0.4384939195509822)
        assertThat(MetaStats.wilsonLowerBound(30, 40)).isWithin(1e-12).of(0.5980574093302989)
        assertThat(MetaStats.wilsonLowerBound(0, 5)).isEqualTo(0.0)
        assertThat(MetaStats.wilsonLowerBound(0, 0)).isEqualTo(0.0)
        // 30–10 outranks 3–0.
        assertThat(MetaStats.wilsonLowerBound(30, 40)).isGreaterThan(MetaStats.wilsonLowerBound(3, 3))
    }

    @Test
    fun `pick and win rates count rated matches only`() {
        val matches = listOf(
            match("m1", 1, Team.RED, red("p1", "a"), blue("p2", "b")),
            match("m2", 2, Team.BLUE, red("p1", "a"), blue("p2", "c")),
            match("m3", 3, Team.RED, red("p1", "c"), blue("p2", "b")),
            match("m4", 4, Team.RED, red("p1", "c"), blue("p2", "b")),
            match("pending", 5, null, red("p1", "a"), blue("p2", "b"), status = MatchStatus.PENDING),
        )
        val report = MetaStats.compute(matches, emptyList())
        assertThat(report.totalMatches).isEqualTo(4)
        val a = report.hero("a")!!
        assertThat(a.record).isEqualTo(WinLoss(games = 2, wins = 1))
        assertThat(a.pickRate).isWithin(1e-12).of(0.5)
        assertThat(a.winRate).isWithin(1e-12).of(0.5)
        assertThat(a.banRate).isNull()
        val b = report.hero("b")!!
        assertThat(b.record).isEqualTo(WinLoss(games = 3, wins = 0))
        assertThat(b.pickRate).isWithin(1e-12).of(0.75)
        assertThat(report.hero("c")!!.record).isEqualTo(WinLoss(games = 3, wins = 3))
    }

    @Test
    fun `ban rate uses drafts that contain bans`() {
        fun draft(id: String, vararg steps: DraftStepRecord) = DraftRecord(
            id = id,
            createdAt = 0,
            methodId = "house",
            methodKind = DraftMethodKind.CUSTOM,
            pool = emptyList(),
            teamA = Team.RED,
            steps = steps.toList(),
        )
        val drafts = listOf(
            draft("d1", DraftStepRecord(Team.RED, DraftAction.BAN, "a"), DraftStepRecord(Team.BLUE, DraftAction.BAN, "b")),
            draft("d2", DraftStepRecord(Team.RED, DraftAction.BAN, "a"), DraftStepRecord(Team.RED, DraftAction.PICK, "c")),
            // No bans: not part of the denominator.
            draft("d3", DraftStepRecord(Team.RED, DraftAction.PICK, "a")),
        )
        val report = MetaStats.compute(emptyList(), drafts)
        assertThat(report.draftsWithBans).isEqualTo(2)
        assertThat(report.hero("a")!!.bans).isEqualTo(2)
        assertThat(report.hero("a")!!.banRate).isWithin(1e-12).of(1.0)
        assertThat(report.hero("b")!!.banRate).isWithin(1e-12).of(0.5)
        assertThat(report.hero("a")!!.winRate).isNull()
        assertThat(report.usage().getValue("a").bans).isEqualTo(2)
    }

    @Test
    fun `heroes are ranked by wilson bound above the minimum sample, then unranked by games`() {
        val matches = buildList {
            // x: 5–0 (ranked, best), y: 4–2 (ranked), z: 2–0 (unranked, too few games), w: 0–6.
            repeat(5) { add(match("x$it", it.toLong(), Team.RED, red("p1", "x"), blue("p2", "w"))) }
            repeat(4) { add(match("y$it", 10L + it, Team.RED, red("p1", "y"), blue("p2", "q"))) }
            repeat(2) { add(match("yl$it", 20L + it, Team.BLUE, red("p1", "y"), blue("p2", "q"))) }
            repeat(2) { add(match("z$it", 30L + it, Team.RED, red("p1", "z"), blue("p2", "w"))) }
        }
        val report = MetaStats.compute(matches, emptyList())
        val order = report.heroes.map { it.heroId }
        assertThat(order.take(4)).containsExactly("x", "y", "q", "w").inOrder()
        assertThat(order.last()).isEqualTo("z")
        assertThat(report.hero("z")!!.ranked).isFalse()
        assertThat(report.hero("x")!!.ranked).isTrue()
    }

    @Test
    fun `best with hero needs three games and prefers the higher win rate`() {
        val matches = buildList {
            repeat(3) { add(match("a$it", it.toLong(), Team.RED, red("ann", "h"), blue("bob", "g"))) }
            add(match("b0", 10, Team.RED, red("bob", "h"), blue("ann", "g")))
            add(match("b1", 11, Team.RED, red("bob", "h"), blue("ann", "g")))
            add(match("c0", 20, Team.BLUE, red("cid", "h"), blue("ann", "g")))
            add(match("c1", 21, Team.RED, red("cid", "h"), blue("ann", "g")))
            add(match("c2", 22, Team.RED, red("cid", "h"), blue("ann", "g")))
            add(match("c3", 23, Team.RED, red("cid", "h"), blue("ann", "g")))
        }
        val report = MetaStats.compute(matches, emptyList())
        // ann 3–0 on h beats cid 3–1; bob has only 2 games on h.
        assertThat(report.hero("h")!!.bestPlayer!!.playerId).isEqualTo("ann")
        assertThat(report.bestHeroesByPlayer.getValue("cid").single().record).isEqualTo(WinLoss(4, 3))
        assertThat(report.bestHeroesByPlayer.getValue("bob").map { it.heroId }).containsExactly("g")
    }

    @Test
    fun `synergy pairs and larger team stats`() {
        val matches = (0 until 5).map { i ->
            match("m$i", i.toLong(), if (i < 4) Team.RED else Team.BLUE, red("p1", "a"), red("p2", "b"), blue("p3", "c"))
        }
        val report = MetaStats.compute(matches, emptyList())
        assertThat(report.synergies).hasSize(1)
        assertThat(report.synergies.single().heroA).isEqualTo("a")
        assertThat(report.synergies.single().record).isEqualTo(WinLoss(5, 4))
        assertThat(report.largerTeam).isEqualTo(WinLoss(5, 4))
    }

    @Test
    fun `empty input gives an empty report`() {
        val report = MetaStats.compute(emptyList(), emptyList())
        assertThat(report.totalMatches).isEqualTo(0)
        assertThat(report.heroes).isEmpty()
        assertThat(report.usage()).isEmpty()
    }
}
