package dev.quinton.goa.core.presentation.group

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.PlayerMerge
import java.time.ZoneOffset
import org.junit.Test

/** The linking rules the phone's "Which player are you?" and the hub's `/group/me` share (ADR-031). */
class PlayerLinkTest {
    private val people = listOf(person("dee", "dee"), person("ann", "Ann"), person("Bob", "bob"), person("cid", "Cid"))
    private val none = PlayerMerges.NONE

    @Test
    fun theListIsByNameIgnoringCase() {
        assertThat(PlayerLink.choices(people).map { it.name }).containsExactly("Ann", "bob", "Cid", "dee").inOrder()
    }

    @Test
    fun youAreAskedOnlyAboutAnActivePlayerWhoIsNotAlreadyYou() {
        assertThat(PlayerLink.invited("ann", null, none, people)?.name).isEqualTo("Ann")
        // Already this device's player: nothing to ask.
        assertThat(PlayerLink.invited("ann", "ann", none, people)).isNull()
        // Not in the data (yet): the list without a question.
        assertThat(PlayerLink.invited("ghost", null, none, people)).isNull()
        assertThat(PlayerLink.invited(null, "ann", none, people)).isNull()
    }

    @Test
    fun aMergedAwayInvitedPlayerReadsAsTheOneTheyBecame() {
        val merges = PlayerMerges(listOf(PlayerMerge("ann-2", "ann", 1)))
        assertThat(PlayerLink.invited("ann-2", null, merges, people)?.id).isEqualTo("ann")
        // …and when that is already you, there is nothing to ask.
        assertThat(PlayerLink.invited("ann-2", "ann", merges, people)).isNull()
        assertThat(PlayerLink.me("ann-2", merges)).isEqualTo("ann")
    }

    @Test
    fun yesOffersToMergeTheOldMeWithItsMatches() {
        val quin = person("quin", "Quin")
        val all = people + quin
        val log = listOf(match("solo", listOf("quin"), listOf("cid")), match("other", listOf("ann"), listOf("cid")))
        val ann = all.first { it.id == "ann" }
        assertThat(PlayerLink.mergeOffer(ann, "quin", none, all, log)).isEqualTo(MergeOffer(quin, 1, ann))
        // A fresh device, or one that already was this player: nothing to merge.
        assertThat(PlayerLink.mergeOffer(ann, null, none, all, log)).isNull()
        assertThat(PlayerLink.mergeOffer(ann, "ann", none, all, log)).isNull()
        // The old player is gone (merged into the invited one already).
        val merged = PlayerMerges(listOf(PlayerMerge("quin", "ann", 1)))
        assertThat(PlayerLink.mergeOffer(ann, "quin", merged, people, log)).isNull()
    }

    @Test
    fun addMeAsksAboutLookAlikesFirstAndRefusesATakenName() {
        val group = listOf(person("rob", "Robert"), person("ann", "Ann"), person("anna", "Anna"))
        assertThat(PlayerLink.addCheck("Zed", group)).isNull()
        val similar = PlayerLink.addCheck("Roberto", group)!!
        assertThat(similar.candidates.map { it.name }).containsExactly("Robert")
        assertThat(similar.nameTaken).isFalse()
        val taken = PlayerLink.addCheck(" ann ", group)!!
        assertThat(taken.nameTaken).isTrue()
        assertThat(taken.candidates.first().name).isEqualTo("Ann")
        assertThat(PlayerLink.addCheckText(taken)).contains("is already in the group")
        assertThat(PlayerLink.addCheckText(similar)).contains("have a name like “Roberto”")
    }

    @Test
    fun aNewPlayerTakesTheFirstFreeColourAndTheNextCrest() {
        val palette = listOf(10L, 20L, 30L)
        val two = listOf(person("a", "A", color = 10L), person("b", "B", color = 30L))
        val p = PlayerLink.newPlayer("id", "Zed", two, palette, crests = 24, now = 99)
        assertThat(p.color).isEqualTo(20L)
        assertThat(p.avatarId).isEqualTo(2)
        assertThat(p.createdAt).isEqualTo(99)
        val full = palette.mapIndexed { i, c -> person("p$i", "P$i", color = c) } + person("x", "X", color = 10L)
        // Every colour taken: the next one in turn.
        assertThat(PlayerLink.newPlayer("id", "Zed", full, palette, crests = 3, now = 0).color).isEqualTo(20L)
        assertThat(PlayerLink.newPlayer("id", "Zed", full, palette, crests = 3, now = 0).avatarId).isEqualTo(1)
    }

    @Test
    fun aRefusalListsTheSharedMatchesOldestFirst() {
        val byId = people.associateBy { it.id }
        val lines = PlayerLink.refusalLines(
            listOf(
                match("late", listOf("ann", "Bob"), listOf("cid"), playedAt = 2 * DAY),
                match("early", listOf("ann"), listOf("ghost"), playedAt = 0),
            ),
            byId,
            ZoneOffset.UTC,
        )
        assertThat(
            lines,
        ).containsExactly("1970-01-01 · Ann vs Unknown player", "1970-01-03 · Ann, bob vs Cid").inOrder()
        assertThat(PlayerLink.refusalText("Ann", "browser")).endsWith("this browser is Ann now.")
        assertThat(PlayerLink.refusalText(null)).endsWith("Both stay.")
    }

    @Test
    fun aPersonalInviteCarriesThePlayerAndSaysWhoItIsFor() {
        val group = GroupCredentials("2b1e7a3c-4d5e-4f60-8a9b-0c1d2e3f4a5b", ByteArray(32) { it.toByte() }, "Table")
        val merges = PlayerMerges(listOf(PlayerMerge("ann-2", "ann", 1)))
        val everyone = people + person("old", "Old", archived = true)
        val choosing = GroupInvites.view(group, HUB, everyone, merges, "cid", InviteFor.Choosing)
        assertThat(choosing.choosing).isTrue()
        assertThat(choosing.invite).isNull()
        // Archived players and this device's own player are left out.
        assertThat(choosing.players.map { it.id }).containsExactly("ann", "Bob", "dee").inOrder()

        val forAnn = GroupInvites.view(group, HUB, everyone, merges, "cid", InviteFor.One("ann-2"))
        assertThat(forAnn.player?.id).isEqualTo("ann")
        assertThat(forAnn.invite).isEqualTo(GroupInvite.format(group, HUB, "ann"))
        assertThat(forAnn.invite).startsWith("$HUB/join#goa-sync:1?")
        assertThat(forAnn.shareText).startsWith("For Ann: ")

        val anyone = GroupInvites.view(group, HUB, everyone, merges, null, InviteFor.Anyone)
        assertThat(GroupInvite.parseInvite(anyone.invite!!).getOrThrow().playerId).isNull()
        assertThat(GroupInvites.view(group, HUB, everyone, merges, null, InviteFor.One("ghost")).choosing).isTrue()
    }

    private companion object {
        const val HUB = "https://goa.example.org"
        const val DAY = 86_400_000L

        fun person(id: String, name: String, color: Long = 1L, archived: Boolean = false) =
            Player(id = id, name = name, color = color, avatarId = 0, createdAt = 0, archived = archived)

        fun match(id: String, red: List<String>, blue: List<String>, playedAt: Long = 0) = Match(
            id = id,
            createdAt = playedAt,
            playedAt = playedAt,
            status = MatchStatus.COMPLETED,
            mode = GameMode.QUICK,
            winner = Team.RED,
            participants = red.map { Participant(it, null, Team.RED) } + blue.map { Participant(it, null, Team.BLUE) },
        )
    }
}
