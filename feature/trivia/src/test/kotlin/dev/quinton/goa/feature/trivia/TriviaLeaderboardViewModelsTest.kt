package dev.quinton.goa.feature.trivia

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.LeaderboardPeriod
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaChallenge
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.core.testing.FakeTriviaProgressRepository
import dev.quinton.goa.core.testing.FakeTriviaRoundRepository
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.trivia.home.ChallengeStart
import dev.quinton.goa.feature.trivia.home.TriviaHomeUiState
import dev.quinton.goa.feature.trivia.home.TriviaHomeViewModel
import dev.quinton.goa.feature.trivia.home.playingAsNote
import dev.quinton.goa.feature.trivia.leaderboard.LeaderboardTab
import dev.quinton.goa.feature.trivia.leaderboard.TriviaLeaderboardUiState
import dev.quinton.goa.feature.trivia.leaderboard.TriviaLeaderboardViewModel
import dev.quinton.goa.feature.trivia.quiz.QuizQuestionUi
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizUiState
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizViewModel
import dev.quinton.goa.feature.trivia.quiz.challengeLine
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Playing as, the Challenge, recording rounds and the leaderboard (ADR-032). */
@OptIn(ExperimentalCoroutinesApi::class)
class TriviaLeaderboardViewModelsTest {
    private val catalog = TestCatalog.catalog
    private val ann = Player("ann", "Ann", 0xFF3FC1B0, avatarId = 1, createdAt = 1)
    private val sam = Player("sam", "Sam", 0xFFD9B44A, avatarId = 2, createdAt = 2)
    private val zoe = Player("zoe", "Zoe", 0xFF62A8F2, avatarId = 3, createdAt = 3)
    private val old = Player("old", "Old", 0xFFC3CCD6, avatarId = 4, createdAt = 4, archived = true)

    private val progress = FakeTriviaProgressRepository()
    private val settings = FakeSettingsRepository()
    private val players = FakePlayerRepository(listOf(ann, sam, zoe, old))
    private val groups = FakeGroupRepository()
    private val rounds = FakeTriviaRoundRepository()

    private var tick = 0L
    private var ids = 0

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun setMe(id: String?) {
        groups.current.value = GroupState(null, "this-phone", null, id, emptyList(), null)
    }

    private fun home() =
        TriviaHomeViewModel(null, FakeCatalogRepository(catalog), settings, progress, players, groups, Dispatchers.Main)

    private fun quiz(route: TriviaQuizRoute, seed: Int = 4) = TriviaQuizViewModel(
        route,
        FakeCatalogRepository(catalog),
        settings,
        progress,
        players,
        groups,
        rounds,
        EditClock { NOW },
        Random(seed),
        ticker = { tick },
        newId = { "round-${++ids}" },
    )

    private val challenge = TriviaQuizRoute(TriviaText.encode(TriviaChallenge.CONFIG), challenge = true)
    private val practice =
        TriviaQuizRoute(TriviaText.encode(TriviaConfig(heroIds = listOf("test-hero-1"), length = 10, duels = false)))

    private suspend fun ReceiveTurbine<TriviaHomeUiState>.ready(): TriviaHomeUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is TriviaHomeUiState.Ready) return item
        }
    }

    private fun TriviaQuizViewModel.playing() = uiState.value as TriviaQuizUiState.Playing

    private fun TriviaQuizViewModel.summary() = uiState.value as TriviaQuizUiState.Summary

    private fun TriviaQuizViewModel.reply(right: Boolean) = answer(
        when (val q = playing().question) {
            is QuizQuestionUi.Number ->
                TriviaAnswer.Number(if (right) q.value else q.choices.firstOrNull { it != q.value } ?: (q.value + 1))

            is QuizQuestionUi.Duel -> TriviaAnswer.Duel(
                if (right) {
                    q.right
                } else {
                    DuelPick.entries.first {
                        it != q.right
                    }
                },
            )
        },
    )

    /** Answers [count] questions ([right] decides each), each taking [millis] of answer time. */
    private fun TriviaQuizViewModel.play(count: Int, millis: Long = 2_000, right: (Int) -> Boolean = { true }) {
        repeat(count) { i ->
            tick += millis
            reply(right(i))
            next()
        }
    }

    // ---- Playing as -----------------------------------------------------------------------

    @Test
    fun `playing as defaults to the stored pick, else this phone's You, else Guest`() = runTest {
        home().uiState.test { assertThat(ready().playingAs.isGuest).isTrue() }

        setMe("ann")
        home().uiState.test {
            val playing = ready().playingAs
            assertThat(playing.player).isEqualTo(ann)
            // Active players only, You first.
            assertThat(playing.players.map { it.id }).containsExactly("ann", "sam", "zoe").inOrder()
        }

        progress.playingAsState.value = "sam"
        home().uiState.test { assertThat(ready().playingAs.player).isEqualTo(sam) }

        // A stored pick who has since been archived falls back to You.
        progress.playingAsState.value = "old"
        home().uiState.test { assertThat(ready().playingAs.player).isEqualTo(ann) }

        // Guest picked on purpose stays Guest even with a You.
        progress.playingAsState.value = TriviaProgressRepository.GUEST
        home().uiState.test { assertThat(ready().playingAs.isGuest).isTrue() }
    }

    @Test
    fun `the home notes whose progress this is when someone else plays`() = runTest {
        setMe("ann")
        progress.playingAsState.value = "sam"
        home().uiState.test {
            assertThat(playingAsNote(ready().playingAs))
                .isEqualTo("Practice progress here stays Ann's; Sam's rounds go on the leaderboard.")
        }
        progress.playingAsState.value = TriviaProgressRepository.GUEST
        home().uiState.test {
            assertThat(
                playingAsNote(ready().playingAs),
            ).isEqualTo("Practice progress here stays Ann's; Guest rounds aren't recorded.")
        }
        progress.playingAsState.value = "ann"
        home().uiState.test { assertThat(playingAsNote(ready().playingAs)).isNull() }
        // No You: nothing to explain.
        setMe(null)
        progress.playingAsState.value = "sam"
        home().uiState.test { assertThat(playingAsNote(ready().playingAs)).isNull() }
    }

    @Test
    fun `the picker plays as a player, Guest, or a newly added one`() = runTest {
        val vm = home()
        vm.uiState.test {
            ready()
            vm.playAs("zoe")
            assertThat(progress.playingAsState.value).isEqualTo("zoe")
            vm.playAs(TriviaProgressRepository.GUEST)
            assertThat(progress.playingAsState.value).isEqualTo(TriviaProgressRepository.GUEST)
            vm.addPlayer("  Nia ")
            val nia = players.state.value.single { it.name == "Nia" }
            assertThat(progress.playingAsState.value).isEqualTo(nia.id)
            // A name that exists picks that player instead of creating a second one.
            vm.addPlayer("sam")
            assertThat(players.state.value.count { it.name.equals("sam", ignoreCase = true) }).isEqualTo(1)
            assertThat(progress.playingAsState.value).isEqualTo("sam")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Challenge start ------------------------------------------------------------------

    @Test
    fun `a challenge needs a named player, then opens the fixed quiz`() = runTest {
        val vm = home()
        vm.uiState.test {
            assertThat(ready().playingAs.isGuest).isTrue()
            assertThat(vm.startChallenge()).isEqualTo(ChallengeStart.PickPlayer)

            // Picking from the "who's playing?" sheet stores the pick before the Challenge opens.
            var opened: TriviaQuizRoute? = null
            vm.playAsAndChallenge("sam") { route ->
                assertThat(progress.playingAsState.value).isEqualTo("sam")
                opened = route
            }
            assertThat(opened).isEqualTo(challenge)
            while (ready().playingAs.player != sam) Unit
            assertThat(vm.startChallenge()).isEqualTo(ChallengeStart.Open(challenge))
            cancelAndIgnoreRemainingEvents()
        }
        var added: TriviaQuizRoute? = null
        vm.addPlayer("Nia") { added = it }
        assertThat(added).isEqualTo(challenge)
    }

    // ---- Recording rounds -----------------------------------------------------------------

    @Test
    fun `a finished practice round is recorded exactly once, when its summary appears`() = runTest {
        progress.playingAsState.value = "sam"
        val vm = quiz(practice)
        vm.play(9)
        assertThat(rounds.state.value).isEmpty()
        vm.play(1)
        assertThat(vm.uiState.value).isInstanceOf(TriviaQuizUiState.Summary::class.java)
        val round = rounds.state.value.single()
        assertThat(round.playerId).isEqualTo("sam")
        assertThat(round.kind).isEqualTo(TriviaRoundKind.PRACTICE)
        assertThat(round.questions).isEqualTo(10)
        assertThat(round.correct).isEqualTo(10)
        assertThat(round.length).isEqualTo(10)
        assertThat(round.answerMillis).isEqualTo(20_000)
        assertThat(round.playedAt).isEqualTo(NOW)
        assertThat(round.catalogVersion).isEqualTo(catalog.dataVersion)
        assertThat(vm.summary().playerName).isEqualTo("Sam")

        // Ending again or moving on changes nothing; Play again is a new round.
        vm.stop()
        vm.next()
        assertThat(rounds.state.value).hasSize(1)
        vm.playAgain()
        vm.play(2)
        vm.stop()
        assertThat(rounds.state.value.map { it.id }).containsExactly("round-1", "round-2")
        assertThat(rounds.state.value.last().questions).isEqualTo(2)
    }

    @Test
    fun `leaving mid-round, playing as Guest and an unfinished challenge record nothing`() = runTest {
        progress.playingAsState.value = "sam"
        val left = quiz(practice)
        left.play(4)
        left.viewModelScope.cancel() // Back → Leave
        assertThat(rounds.state.value).isEmpty()

        progress.playingAsState.value = TriviaProgressRepository.GUEST
        val guest = quiz(practice)
        guest.play(10)
        assertThat(guest.summary().playerName).isNull()
        assertThat(rounds.state.value).isEmpty()

        progress.playingAsState.value = "sam"
        val early = quiz(challenge)
        early.play(12)
        early.stop()
        assertThat(rounds.state.value).isEmpty()
        assertThat(challengeLine(early.summary())).isEqualTo("Unfinished challenges don't go on the leaderboard.")
        // Nothing answered at all: nothing to record either.
        val none = quiz(practice)
        none.stop()
        assertThat(rounds.state.value).isEmpty()
    }

    @Test
    fun `a finished challenge is recorded with its answer time and shows the player's place`() = runTest {
        progress.playingAsState.value = "sam"
        rounds.state.value = listOf(
            round("z1", "zoe", correct = 19),
            round("s0", "sam", correct = 18, millis = 50_000),
            round("a1", "ann", correct = 12),
        )
        val vm = quiz(challenge)
        assertThat(vm.playing().challenge).isTrue()
        assertThat(vm.playing().length).isEqualTo(20)
        vm.play(20, millis = 3_000) { it != 0 && it != 7 } // 18 right, slower than Sam's best
        val recorded = rounds.state.value.single { it.id == "round-1" }
        assertThat(recorded.kind).isEqualTo(TriviaRoundKind.CHALLENGE)
        assertThat(recorded.questions).isEqualTo(20)
        assertThat(recorded.correct).isEqualTo(18)
        assertThat(recorded.answerMillis).isEqualTo(60_000)
        assertThat(recorded.heroCount).isEqualTo(catalog.heroes.size)
        val summary = vm.summary()
        assertThat(summary.challenge!!.answerMillis).isEqualTo(60_000)
        assertThat(summary.challenge!!.standing!!.place).isEqualTo(2)
        assertThat(challengeLine(summary)).isEqualTo("2nd of 3 · your best 18/20")

        // A new best on the next Challenge.
        vm.playAgain()
        vm.play(20, millis = 1_000)
        assertThat(challengeLine(vm.summary())).isEqualTo("New best! · 1st of 3")
    }

    // ---- Learning progress ----------------------------------------------------------------

    @Test
    fun `learning progress moves only for this phone's You, or anyone while no You is set`() = runTest {
        setMe("ann")
        progress.playingAsState.value = "sam"
        val sams = quiz(practice)
        sams.play(3)
        assertThat(progress.state.value).isEmpty()
        sams.stop()
        assertThat(sams.summary().progressSaved).isFalse()
        assertThat(sams.summary().learned).isEqualTo(0)

        progress.playingAsState.value = TriviaProgressRepository.GUEST
        quiz(practice).play(3)
        assertThat(progress.state.value).isEmpty()

        progress.playingAsState.value = "ann"
        quiz(practice).play(3)
        assertThat(progress.state.value).isNotEmpty()

        progress.state.value = emptyMap()
        setMe(null)
        progress.playingAsState.value = TriviaProgressRepository.GUEST
        val anyone = quiz(practice)
        anyone.play(3)
        assertThat(progress.state.value).isNotEmpty()
        anyone.stop()
        assertThat(anyone.summary().progressSaved).isTrue()
    }

    // ---- Answer time ----------------------------------------------------------------------

    @Test
    fun `answer time runs from a question appearing to its answer and pauses in the background`() = runTest {
        progress.playingAsState.value = "sam"
        val vm = quiz(practice)
        tick += 1_000
        vm.onVisible(false) // the app goes to the background
        tick += 60_000
        vm.onVisible(true)
        tick += 500
        vm.reply(true)
        // Reading the card scan after the answer doesn't count.
        tick += 30_000
        vm.next()
        tick += 2_000
        vm.reply(false)
        vm.stop()
        assertThat(rounds.state.value.single().answerMillis).isEqualTo(3_500)
    }

    // ---- Leaderboard screen ---------------------------------------------------------------

    private fun leaderboard() =
        TriviaLeaderboardViewModel(rounds, players, groups, EditClock { NOW }, SavedStateHandle())

    private suspend fun ReceiveTurbine<TriviaLeaderboardUiState>.board(): TriviaLeaderboardUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is TriviaLeaderboardUiState.Ready) return item
        }
    }

    @Test
    fun `the leaderboard shows both boards, switches period and marks You`() = runTest {
        setMe("sam")
        rounds.state.value = listOf(
            round("z1", "zoe", correct = 19, at = NOW - 40 * DAY),
            round("s1", "sam", correct = 17, millis = 102_000, at = NOON),
            practiceRound("a1", "ann", correct = 40, questions = 50),
            round("o1", "old", correct = 20), // archived: not ranked
        )
        val vm = leaderboard()
        vm.uiState.test {
            val all = board()
            assertThat(all.tab).isEqualTo(LeaderboardTab.CHALLENGE)
            assertThat(all.rows.map { it.player.id }).containsExactly("zoe", "sam").inOrder()
            val samRow = all.rows[1]
            assertThat(samRow.place).isEqualTo(2)
            assertThat(samRow.isMe).isTrue()
            assertThat(samRow.main).isEqualTo("17/20")
            assertThat(samRow.detail).startsWith("1:42 · ")
            assertThat(samRow.spoken).startsWith("2nd place, Sam, you, best 17 of 20 right in 1 minute 42 seconds")

            vm.selectPeriod(LeaderboardPeriod.LAST_30_DAYS)
            assertThat(board().rows.map { it.player.id }).containsExactly("sam")

            vm.selectTab(LeaderboardTab.PRACTICE)
            val practice = board()
            assertThat(practice.rows.map { it.player.id }).containsExactly("ann", "sam").inOrder()
            assertThat(practice.rows.first().main).isEqualTo("40")
            assertThat(practice.rows.first().detail).isEqualTo("80% · best streak 5 · 1 round")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a player's rounds open in a sheet and a removed round leaves the board`() = runTest {
        rounds.state.value = listOf(
            round("s1", "sam", correct = 17, at = NOON),
            round("s2", "sam", correct = 12, at = NOON + DAY),
            round("z1", "zoe", correct = 15),
        )
        val vm = leaderboard()
        vm.uiState.test {
            board()
            vm.openPlayer("sam")
            val open = board().selected!!
            assertThat(open.player).isEqualTo(sam)
            assertThat(open.rounds.map { it.id }).containsExactly("s2", "s1").inOrder()
            assertThat(open.rounds.last().label).startsWith("Sam's Challenge on ")
            assertThat(open.rounds.last().label).endsWith("(17/20)")

            vm.removeRound("s1")
            val after = board()
            assertThat(rounds.state.value.map { it.id }).containsExactly("s2", "z1")
            assertThat(after.selected!!.rounds.map { it.id }).containsExactly("s2")
            // Sam's best is now the 12: Zoe moves ahead.
            assertThat(after.rows.map { it.player.id }).containsExactly("zoe", "sam").inOrder()

            vm.closePlayer()
            assertThat(board().selected).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty leaderboard knows whether anything was ever played`() = runTest {
        val vm = leaderboard()
        vm.uiState.test {
            val empty = board()
            assertThat(empty.rows).isEmpty()
            assertThat(empty.anyRounds).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun round(id: String, player: String, correct: Int, millis: Long = 100_000, at: Long = NOON) = TriviaRound(
        id = id,
        playerId = player,
        kind = TriviaRoundKind.CHALLENGE,
        playedAt = at,
        length = 20,
        questions = 20,
        correct = correct,
        bestStreak = 5,
        answerMillis = millis,
        heroCount = 12,
        catalogVersion = 1,
    )

    private fun practiceRound(id: String, player: String, correct: Int, questions: Int) = round(id, player, correct)
        .copy(kind = TriviaRoundKind.PRACTICE, length = 0, questions = questions)

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000

        /** 12:00 UTC on a day well inside the last 30 days of [NOW]: the same date in every time zone. */
        const val NOON = 1_760_000_000_000L - (1_760_000_000_000L % DAY) + DAY / 2
        const val NOW = NOON + 3 * DAY
    }
}
