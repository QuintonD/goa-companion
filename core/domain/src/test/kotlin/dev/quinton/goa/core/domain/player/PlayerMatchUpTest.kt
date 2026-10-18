package dev.quinton.goa.core.domain.player

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.player.PlayerMatchUp.Likeness
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.PlayerMerge
import kotlin.random.Random
import org.junit.Test

/**
 * "Same people?" (ADR-031): which names look like one person entered twice, and which pairs are
 * never offered (they played together, someone said "Different people", archived or merged away).
 */
class PlayerMatchUpTest {
    private fun player(id: String, name: String, createdAt: Long = 1, archived: Boolean = false) =
        Player(id, name, 0xFF3FC1B0, avatarId = 0, createdAt = createdAt, archived = archived)

    private fun match(id: String, red: List<String>, blue: List<String>) = Match(
        id = id,
        createdAt = 1,
        playedAt = 1,
        status = MatchStatus.COMPLETED,
        mode = GameMode.QUICK,
        winner = Team.RED,
        participants = red.map { Participant(it, null, Team.RED) } + blue.map { Participant(it, null, Team.BLUE) },
    )

    @Test
    fun `names are compared without case, spaces, accents or punctuation`() {
        assertThat(PlayerMatchUp.matchKey("  José B. ")).isEqualTo("joseb")
        assertThat(PlayerMatchUp.matchKey("Zoë-Ann")).isEqualTo("zoeann")
        assertThat(PlayerMatchUp.likeness("José", "jose")).isEqualTo(Likeness.SAME_NAME)
        assertThat(PlayerMatchUp.likeness("Ann", "ANN ")).isEqualTo(Likeness.SAME_NAME)
        assertThat(PlayerMatchUp.likeness("O'Neil", "Oneil")).isEqualTo(Likeness.SAME_NAME)
        // Other scripts are letters too.
        assertThat(PlayerMatchUp.likeness("Лена", "лена")).isEqualTo(Likeness.SAME_NAME)
    }

    @Test
    fun `a short name for a longer one needs three letters`() {
        assertThat(PlayerMatchUp.likeness("Ann", "Anna")).isEqualTo(Likeness.PREFIX)
        assertThat(PlayerMatchUp.likeness("Quin", "Quinton")).isEqualTo(Likeness.PREFIX)
        assertThat(PlayerMatchUp.likeness("Rob", "Robert")).isEqualTo(Likeness.PREFIX)
        // One or two letters are initials, not names.
        assertThat(PlayerMatchUp.likeness("Q", "Quinton")).isNull()
        assertThat(PlayerMatchUp.likeness("Qu", "Quinton")).isNull()
        assertThat(PlayerMatchUp.likeness("Q.", "Quinton")).isNull()
    }

    @Test
    fun `one slip from four letters, two from eight`() {
        assertThat(PlayerMatchUp.likeness("Jhon", "John")).isEqualTo(Likeness.SIMILAR) // swapped letters
        assertThat(PlayerMatchUp.likeness("Katherine", "Catherine")).isEqualTo(Likeness.SIMILAR)
        assertThat(PlayerMatchUp.likeness("Stephen", "Steven")).isNull() // two edits at six letters
        assertThat(PlayerMatchUp.likeness("Christopher", "Kristopher")).isEqualTo(Likeness.SIMILAR) // two at ten
        assertThat(PlayerMatchUp.likeness("Marta", "Marga")).isEqualTo(Likeness.SIMILAR)
        // Three letters one apart are usually two people: not suggested.
        assertThat(PlayerMatchUp.likeness("Rob", "Bob")).isNull()
        assertThat(PlayerMatchUp.likeness("Tom", "Tim")).isNull()
        assertThat(PlayerMatchUp.likeness("Jon", "John")).isNull()
        assertThat(PlayerMatchUp.likeness("Ann", "Bob")).isNull()
        assertThat(PlayerMatchUp.likeness("Quinton", "Clinton")).isNull()
    }

    @Test
    fun `names without letters only match themselves`() {
        assertThat(PlayerMatchUp.likeness("42", "42 ")).isEqualTo(Likeness.SAME_NAME)
        assertThat(PlayerMatchUp.likeness("42", "17")).isNull()
        assertThat(PlayerMatchUp.likeness("🙂", "Ann")).isNull()
    }

    @Test
    fun `edit distance counts a swap of neighbours as one edit`() {
        assertThat(PlayerMatchUp.editDistance("ab", "ba")).isEqualTo(1)
        assertThat(PlayerMatchUp.editDistance("kitten", "sitting")).isEqualTo(3)
        assertThat(PlayerMatchUp.editDistance("", "abc")).isEqualTo(3)
        assertThat(PlayerMatchUp.editDistance("same", "same")).isEqualTo(0)
    }

    @Test
    fun `the older player is the one kept`() {
        val rob = player("p-rob", "Robert", createdAt = 5)
        val robby = player("p-robby", "Rob", createdAt = 9)
        val pair = PlayerMatchUp.suggest(listOf(robby, rob), emptyList()).single()
        assertThat(pair.keep).isEqualTo(rob)
        assertThat(pair.other).isEqualTo(robby)
        // Same creation time: the smaller id.
        val a = player("a", "Dee", createdAt = 1)
        val b = player("b", "dee", createdAt = 1)
        assertThat(PlayerMatchUp.suggest(listOf(b, a), emptyList()).single().keep).isEqualTo(a)
    }

    @Test
    fun `players who played in the same match are never suggested`() {
        val ann = player("ann", "Ann")
        val anna = player("anna", "Anna", createdAt = 2)
        // Teammates: two people.
        assertThat(PlayerMatchUp.suggest(listOf(ann, anna), listOf(match("m1", listOf("ann", "anna"), listOf("x")))))
            .isEmpty()
        // Opponents: two people.
        assertThat(PlayerMatchUp.suggest(listOf(ann, anna), listOf(match("m1", listOf("ann"), listOf("anna")))))
            .isEmpty()
        // Each in their own matches: may be one person.
        val apart = listOf(match("m1", listOf("ann"), listOf("x")), match("m2", listOf("anna"), listOf("x")))
        assertThat(PlayerMatchUp.suggest(listOf(ann, anna), apart)).hasSize(1)
    }

    @Test
    fun `a match logged with a merged-away id counts for the player it joined`() {
        val ann = player("ann", "Ann")
        val anna = player("anna", "Anna", createdAt = 2)
        val old = player("ann-old", "Annie", createdAt = 3)
        val merges = PlayerMerges(listOf(PlayerMerge("ann-old", "ann", 1)))
        val log = listOf(match("m1", listOf("ann-old"), listOf("anna")))
        assertThat(PlayerMatchUp.suggest(listOf(ann, anna, old), log, merges)).isEmpty()
    }

    @Test
    fun `archived and merged-away players are left out`() {
        val ann = player("ann", "Ann")
        val archived = player("anna", "Anna", createdAt = 2, archived = true)
        val gone = player("ann-2", "Ann", createdAt = 3)
        val merges = PlayerMerges(listOf(PlayerMerge("ann-2", "ann", 1)))
        assertThat(PlayerMatchUp.suggest(listOf(ann, archived, gone), emptyList(), merges)).isEmpty()
    }

    @Test
    fun `different people stay different, also after one of them is merged`() {
        val ann = player("ann", "Ann")
        val anna = player("anna", "Anna", createdAt = 2)
        val key = PlayerMatchUp.pairKey("anna", "ann")
        assertThat(key).isEqualTo(PlayerMatchUp.pairKey("ann", "anna"))
        assertThat(PlayerMatchUp.suggest(listOf(ann, anna), emptyList(), dismissed = setOf(key))).isEmpty()
        // "Anna" was later merged into "Annabel": still the person this phone said was someone else.
        val annabel = player("annabel", "Annabel", createdAt = 0)
        val merges = PlayerMerges(listOf(PlayerMerge("anna", "annabel", 1)))
        val pairs = PlayerMatchUp.suggest(listOf(ann, anna, annabel), emptyList(), merges, setOf(key))
        assertThat(pairs).isEmpty()
        // Garbage keys are ignored.
        assertThat(PlayerMatchUp.suggest(listOf(ann, anna), emptyList(), dismissed = setOf("", "\n", "x"))).hasSize(1)
    }

    @Test
    fun `pairs come closest first, then by name`() {
        val people = listOf(
            player("1", "Quinton", createdAt = 1),
            player("2", "Quin", createdAt = 2),
            player("3", "Bea", createdAt = 3),
            player("4", "bea", createdAt = 4),
            player("5", "Marta", createdAt = 5),
            player("6", "Marga", createdAt = 6),
        )
        val pairs = PlayerMatchUp.suggest(people, emptyList())
        assertThat(pairs.map { it.keep.name to it.other.name })
            .containsExactly("Bea" to "bea", "Quinton" to "Quin", "Marta" to "Marga").inOrder()
        assertThat(pairs.map { it.likeness }).containsExactly(Likeness.SAME_NAME, Likeness.PREFIX, Likeness.SIMILAR)
            .inOrder()
    }

    @Test
    fun `add me lists similar players, the same name first`() {
        val people = listOf(player("1", "Annabel"), player("2", "Ann"), player("3", "Bob"), player("4", "Anne"))
        assertThat(PlayerMatchUp.similar("ann", people).map { it.name }).containsExactly("Ann", "Annabel", "Anne")
            .inOrder()
        assertThat(PlayerMatchUp.similar("Eve", people)).isEmpty()
    }

    private val names = listOf(
        "Ann", "Anna", "Anne", "Bob", "Rob", "Robert", "Quinton", "Quin", "Q", "José", "Jose", "Dee", "Dee",
        "Katherine", "Catherine", "Marta", "Marga", "Eve", "Evan", "Cid",
    )

    private fun Random.people() = names.shuffled(this).take(nextInt(2, names.size)).mapIndexed { i, name ->
        player("p$i", name, createdAt = nextLong(0, 4), archived = nextInt(10) == 0)
    }

    private fun Random.log(people: List<Player>) = List(nextInt(0, 8)) { m ->
        val seats = people.shuffled(this).take(nextInt(2, minOf(6, people.size) + 1))
        match("m$m", seats.take(seats.size / 2).map { it.id }, seats.drop(seats.size / 2).map { it.id })
    }

    @Test
    fun `input order never changes the pairs`() = repeat(300) { seed ->
        val random = Random(seed)
        val people = random.people()
        val log = random.log(people)
        val pairs = PlayerMatchUp.suggest(people, log)
        assertThat(PlayerMatchUp.suggest(people.shuffled(random), log.shuffled(random))).isEqualTo(pairs)
        assertThat(PlayerMatchUp.suggest(people.reversed(), log.reversed())).isEqualTo(pairs)
    }

    @Test
    fun `a suggested pair never shared a match and is never archived`() = repeat(300) { seed ->
        val random = Random(seed)
        val people = random.people()
        val log = random.log(people)
        PlayerMatchUp.suggest(people, log).forEach { pair ->
            assertThat(log.none { pair.keep.id in it.playerIds && pair.other.id in it.playerIds }).isTrue()
            assertThat(pair.keep.archived || pair.other.archived).isFalse()
            assertThat(PlayerMatchUp.OLDER_FIRST.compare(pair.keep, pair.other)).isLessThan(0)
            assertThat(PlayerMatchUp.likeness(pair.keep.name, pair.other.name)).isEqualTo(pair.likeness)
        }
    }

    @Test
    fun `likeness is symmetric`() {
        names.forEach { a ->
            names.forEach { b -> assertThat(PlayerMatchUp.likeness(a, b)).isEqualTo(PlayerMatchUp.likeness(b, a)) }
        }
    }
}
