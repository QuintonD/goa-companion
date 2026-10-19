package dev.quinton.goa.core.domain.sync

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import org.junit.Test

class SyncMergeTest {
    private val ann = player("ann", "Ann")
    private val bob = player("bob", "Bob")
    private val m1 = match("m1", red = listOf("ann"), blue = listOf("bob"), updatedAt = 10)

    private fun doc(vararg parts: Any) = SyncDocument(
        players = parts.filterIsInstance<dev.quinton.goa.core.model.player.Player>(),
        matches = parts.filterIsInstance<dev.quinton.goa.core.model.match.Match>(),
        triviaRounds = parts.filterIsInstance<TriviaRound>(),
        deletions = parts.filterIsInstance<Deletion>(),
        playerMerges = parts.filterIsInstance<PlayerMerge>(),
    )

    @Test
    fun theLaterEditWinsWhicheverPhoneHasIt() {
        val edited = m1.copy(notes = "Brogan pushed the last wave", updatedAt = 11)
        assertThat(SyncMerge.merge(doc(m1), doc(edited)).matches).containsExactly(edited)
        assertThat(SyncMerge.merge(doc(edited), doc(m1)).matches).containsExactly(edited)
    }

    @Test
    fun anExactTieKeepsTheSameCopyOnEveryPhone() {
        val a = m1.copy(notes = "a")
        val b = m1.copy(notes = "b")
        val ab = SyncMerge.merge(doc(a), doc(b)).matches.single()
        val ba = SyncMerge.merge(doc(b), doc(a)).matches.single()
        assertThat(ab).isEqualTo(ba)
        // The copy whose canonical encoding sorts last: "b".
        assertThat(ab.notes).isEqualTo("b")
    }

    @Test
    fun aTieNeverTradesARealHeroForAnUnknownOne() {
        // Before sync, a backup import on an older catalog stored the newer hero as null; the
        // migration then gave both copies the same backfilled stamp.
        val real = match("m", listOf("ann"), listOf("bob"), updatedAt = 10, heroes = listOf("h0", "new-hero"))
        val cleared = real.copy(
            participants = real.participants.map {
                if (it.heroId ==
                    "new-hero"
                ) {
                    it.copy(heroId = null)
                } else {
                    it
                }
            },
        )
        assertThat(SyncMerge.merge(doc(cleared), doc(real)).matches.single()).isEqualTo(real)
        assertThat(SyncMerge.merge(doc(real), doc(cleared)).matches.single()).isEqualTo(real)
    }

    @Test
    fun aTombstoneBeatsOlderAndEqualEditsButALaterEditBringsTheMatchBack() {
        val deleted = Deletion(DeletionKind.MATCH, "m1", deletedAt = 10)
        assertThat(SyncMerge.merge(doc(m1), doc(deleted)).matches).isEmpty()
        assertThat(SyncMerge.merge(doc(m1.copy(updatedAt = 9)), doc(deleted)).matches).isEmpty()

        val later = m1.copy(updatedAt = 12, notes = "fixed the winner")
        val merged = SyncMerge.merge(doc(later), doc(deleted))
        assertThat(merged.matches).containsExactly(later)
        // The tombstone stays, so a phone still holding the old copy can't bring that one back.
        assertThat(SyncMerge.merge(merged, doc(m1)).matches).containsExactly(later)
        assertThat(merged.deletions).containsExactly(deleted)
    }

    @Test
    fun triviaRoundsMergeLikeMatchesAndARemovedRoundStaysRemoved() {
        val r1 = round("r1", "ann", updatedAt = 10)
        val r2 = round("r2", "bob", updatedAt = 11)
        val both = SyncMerge.merge(doc(ann, bob, r1), doc(ann, bob, r2))
        assertThat(both.triviaRounds).containsExactly(r1, r2).inOrder()
        assertThat(SyncMerge.changes(SyncMerge.normalize(doc(ann, bob, r1)), both).triviaRoundsAdded).isEqualTo(1)

        // Same id: the later write wins, and an exact tie picks the same copy on every phone.
        val rewritten = r1.copy(correct = 16, updatedAt = 12)
        assertThat(SyncMerge.merge(doc(r1), doc(rewritten)).triviaRounds).containsExactly(rewritten)
        val tieA = r1.copy(answerMillis = 1)
        val tieB = r1.copy(answerMillis = 2)
        assertThat(SyncMerge.merge(doc(tieA), doc(tieB))).isEqualTo(SyncMerge.merge(doc(tieB), doc(tieA)))

        // "Remove from leaderboard": the tombstone beats the round on every phone that still has it.
        val removed = Deletion(DeletionKind.TRIVIA_ROUND, "r1", deletedAt = 10)
        val merged = SyncMerge.merge(both, doc(removed))
        assertThat(merged.triviaRounds).containsExactly(r2)
        assertThat(SyncMerge.merge(merged, doc(r1)).triviaRounds).containsExactly(r2)
        assertThat(SyncMerge.changes(both, merged).triviaRoundsRemoved).isEqualTo(1)
        // A match tombstone with the same id doesn't touch the round.
        assertThat(SyncMerge.merge(doc(r1), doc(Deletion(DeletionKind.MATCH, "r1", 99))).triviaRounds)
            .containsExactly(r1)
    }

    @Test
    fun validationRefusesTriviaRoundsTheAppWouldNeverWrite() {
        fun problem(vararg rounds: TriviaRound) =
            SyncValidation.problem(SyncDocument(players = listOf(ann, bob), triviaRounds = rounds.toList()))
        val good = round("good", "ann")
        assertThat(problem(good, round("practice", "bob", kind = TriviaRoundKind.PRACTICE, questions = 7, correct = 3)))
            .isNull()
        assertThat(problem(good, good)).contains("duplicate or empty ids")
        assertThat(problem(round(" ", "ann"))).contains("duplicate or empty ids")
        assertThat(problem(round("ghost", "nobody"))).contains("player nobody is missing")
        assertThat(problem(round("blank", ""))).contains("it has no player")
        assertThat(problem(round("over", "ann", correct = 21))).contains("more right answers than questions")
        assertThat(problem(good.copy(id = "neg", answerMillis = -1))).contains("negative number")
        assertThat(problem(good.copy(id = "neg2", bestStreak = -1))).contains("negative number")
        assertThat(problem(good.copy(id = "streak", bestStreak = 16))).contains("best streak")
        assertThat(problem(round("unfinished", "ann", questions = 12, correct = 4))).contains("unfinished Challenge")
        assertThat(
            problem(round("long", "ann", kind = TriviaRoundKind.PRACTICE, questions = 21, correct = 4)),
        ).contains("more answers than questions in the round")
        assertThat(problem(round("none", "ann", kind = TriviaRoundKind.PRACTICE, questions = 0, correct = 0)))
            .contains("no answers")
        assertThat(problem(round("x", "nobody"))).startsWith("The other phone has trivia rounds this app can't take:")
    }

    @Test
    fun aStoredVersion1DocumentMergedWithAVersion2OneIsAValidVersion2Document() {
        // As a build from before trivia rounds stored it: version 1, no rounds.
        val stored = SyncDocument(version = 1, players = listOf(ann, bob), matches = listOf(m1))
        val current = doc(ann, bob, round("r1", "ann", updatedAt = 3))
        listOf(SyncMerge.merge(stored, current), SyncMerge.merge(current, stored)).forEach { merged ->
            assertThat(merged.version).isEqualTo(SyncDocument.VERSION)
            assertThat(merged.version).isEqualTo(2)
            assertThat(merged.format).isEqualTo(SyncDocument.FORMAT)
            assertThat(merged.matches).containsExactly(m1)
            assertThat(merged.triviaRounds.map { it.id }).containsExactly("r1")
            assertThat(SyncValidation.problem(merged)).isNull()
        }
        // Even two old documents merge into the current version.
        assertThat(SyncMerge.merge(stored, stored).version).isEqualTo(SyncDocument.VERSION)
    }

    @Test
    fun nothingNotDeletedIsLost() {
        val a = doc(ann, m1)
        val b = doc(bob, match("m2", listOf("bob"), listOf("ann"), updatedAt = 3))
        val merged = SyncMerge.merge(a, b)
        assertThat(merged.players.map { it.id }).containsExactly("ann", "bob").inOrder()
        assertThat(merged.matches.map { it.id }).containsExactly("m1", "m2").inOrder()
        assertThat(
            SyncMerge.changes(SyncMerge.normalize(a), merged),
        ).isEqualTo(SyncChanges(playersAdded = 1, matchesAdded = 1))
    }

    @Test
    fun theLaterRatingConstantsWinAndAnEditedSetBeatsTheDefaults() {
        val edited = StampedEloConfig(EloConfig(kEstablished = 15.0), updatedAt = 1)
        val untouched = SyncDocument()
        assertThat(SyncMerge.merge(untouched, SyncDocument(eloConfig = edited)).eloConfig).isEqualTo(edited)
        val newer = StampedEloConfig(EloConfig(kEstablished = 25.0), updatedAt = 2)
        assertThat(SyncMerge.merge(SyncDocument(eloConfig = newer), SyncDocument(eloConfig = edited)).eloConfig)
            .isEqualTo(newer)
    }

    @Test
    fun mergeChainsResolveToTheLastPlayerAndCountAsOnePlayer() {
        val merges = PlayerMerges(listOf(PlayerMerge("ann2", "ann3", 1), PlayerMerge("ann3", "ann", 2)))
        assertThat(merges.resolve("ann2")).isEqualTo("ann")
        assertThat(merges.resolve("bob")).isEqualTo("bob")
        assertThat(merges.mergedAway).containsExactly("ann2", "ann3")

        val log = listOf(
            match("a", listOf("ann2"), listOf("bob"), playedAt = 1),
            match("b", listOf("ann"), listOf("bob"), playedAt = 2),
        )
        val ratings = EloCalculator.replay(merges.resolve(log))
        assertThat(ratings.players.keys).containsExactly("ann", "bob")
        assertThat(ratings.player("ann").games).isEqualTo(2)
    }

    @Test
    fun oppositeMergesOnTwoPhonesFormACycleThatEveryPhoneBreaksAlike() {
        // Phone A merged ann → anna, phone B merged anna → ann, before they synced.
        val a = SyncDocument(playerMerges = listOf(PlayerMerge("ann", "anna", 5)))
        val b = SyncDocument(playerMerges = listOf(PlayerMerge("anna", "ann", 6)))
        val merged = SyncMerge.merge(a, b)
        val resolver = PlayerMerges(merged.playerMerges)
        assertThat(resolver.resolve("ann")).isEqualTo("ann")
        assertThat(resolver.resolve("anna")).isEqualTo("ann")
        assertThat(resolver.mergedAway).containsExactly("anna")
    }

    @Test
    fun twoPhonesMergingOneDuplicateIntoDifferentPlayersKeepTheLaterMerge() {
        val a = SyncDocument(playerMerges = listOf(PlayerMerge("x", "y", 5)))
        val b = SyncDocument(playerMerges = listOf(PlayerMerge("x", "z", 7)))
        assertThat(SyncMerge.merge(a, b).playerMerges).containsExactly(PlayerMerge("x", "z", 7))
        assertThat(SyncMerge.merge(b, a).playerMerges).containsExactly(PlayerMerge("x", "z", 7))
    }

    @Test
    fun aMergeIsRefusedWhenTheTwoPlayedInTheSameMatch() {
        val log = listOf(
            match("against", listOf("ann"), listOf("ann2")),
            match("together", listOf("ann", "ann2"), listOf("bob")),
            match("apart", listOf("ann"), listOf("bob")),
        )
        assertThat(PlayerMerges.conflicts("ann2", "ann", log).map { it.id }).containsExactly("against", "together")
        assertThat(PlayerMerges.conflicts("bob", "cid", log)).isEmpty()
    }

    @Test
    fun aMatchThatPutsAMergedPlayerOnBothTeamsIsSkippedByTheReplayOnEveryPhone() {
        // A match logged on another phone after the merge, naming both ids on opposite teams.
        val merges = PlayerMerges(listOf(PlayerMerge("ann2", "ann", 1)))
        val resolved = merges.resolve(match("late", listOf("ann"), listOf("ann2")))
        assertThat(EloCalculator.skipReason(resolved)).isNotNull()
    }

    @Test
    fun unknownHeroesAreClearedOnlyWhenRead() {
        val known = setOf("h0", "h1")
        val m = match("m", listOf("ann", "cid"), listOf("bob"), heroes = listOf("h0", "new-hero", "h1"))
        val cleared = UnknownHeroes.clear(m, known)
        assertThat(cleared.participants.map { it.heroId }).containsExactly("h0", null, "h1").inOrder()
        assertThat(cleared.participants.map { it.playerId }).isEqualTo(m.participants.map { it.playerId })
        assertThat(UnknownHeroes.ids(listOf(m), emptyList(), known)).containsExactly("new-hero")
        // An empty catalog knows nothing, so it clears nothing.
        assertThat(UnknownHeroes.clear(m, emptySet())).isSameInstanceAs(m)
        // The stored copy keeps the id, so a merge never trades the real hero for null.
        assertThat(SyncMerge.merge(doc(m), doc(m)).matches.single().participants[1].heroId).isEqualTo("new-hero")
    }

    @Test
    fun editStampsAlwaysMoveForwardEvenWhenTheClockIsBehind() {
        assertThat(EditStamp.next(now = 100)).isEqualTo(100)
        assertThat(EditStamp.next(now = 100, 40)).isEqualTo(100)
        assertThat(EditStamp.next(now = 100, 500)).isEqualTo(501)
        assertThat(EditStamp.next(now = 100, 50, null, 700)).isEqualTo(701)
    }

    @Test
    fun validationRefusesWhatABackupImportRefuses() {
        val twice = match("twice", listOf("ann"), listOf("bob"), heroes = listOf("h1", "h1"))
        val both = match("both", listOf("ann"), listOf("ann"))
        val ghost = match("ghost", listOf("ann"), listOf("nobody"))
        fun problem(vararg m: dev.quinton.goa.core.model.match.Match) =
            SyncValidation.problem(SyncDocument(players = listOf(ann, bob), matches = m.toList()))
        assertThat(problem(m1)).isNull()
        assertThat(problem(twice)).contains("hero h1 is listed twice")
        assertThat(problem(both)).contains("Ann is on both teams")
        assertThat(problem(ghost)).contains("player nobody is missing")
        assertThat(SyncValidation.problem(SyncDocument(version = SyncDocument.VERSION + 1))).contains("newer version")
        assertThat(
            SyncValidation.problem(
                SyncDocument(players = listOf(ann), playerMerges = listOf(PlayerMerge("ann", "zed", 1))),
            ),
        ).contains("merges a player")
    }
}
