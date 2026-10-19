package dev.quinton.goa.core.presentation.draft

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.ban
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.settings.AppSettings
import org.junit.Test

/** The setup form's edits and what the setup screen derives from it. */
class DraftSetupTest {
    private val people = listOf("ann", "bo", "cy", "di", "ed").map { player(it) }
    private val ratings = mapOf(
        "ann" to Rating(1400.0, 10, 7, false),
        "bo" to Rating(1300.0, 8, 5, false),
        "cy" to Rating(1100.0, 3, 1, true),
        "di" to Rating(1000.0, 2, 0, true),
    )
    private val inputs = SetupInputs(people, ratings, FakeCatalog.catalog(), AppSettings(), library)

    private fun state(form: SetupForm, active: DraftProgress? = null) = DraftSetupPresenter.derive(inputs, form, active)

    @Test
    fun `house draft is the default method and the stage blockers guide the table`() {
        val empty = state(SetupForm())
        assertThat(empty.form.methodId).isEqualTo("house")
        assertThat(empty.blocker).isEqualTo("Choose at least 2 players")

        val four = SetupForm().withPlayers(listOf("ann", "bo", "cy", "di"))
        assertThat(four.red).containsExactly("ann", "cy").inOrder()
        assertThat(four.blue).containsExactly("bo", "di").inOrder()
        val teams = four.next(state(four).blocker)
        assertThat(teams.stage).isEqualTo(SetupStage.TEAMS)
        assertThat(state(teams).seats).hasSize(4)
        val coin = teams.next(null).next(state(teams.next(null)).blocker)
        assertThat(coin.stage).isEqualTo(SetupStage.COIN)
        assertThat(state(coin).blocker).isEqualTo("Flip the Tie Breaker coin")
        assertThat(coin.flipped(1L).flipCount).isEqualTo(1)
        assertThat(state(coin.withTeamA(Team.BLUE)).blocker).isNull()
        assertThat(coin.back()!!.stage).isEqualTo(SetupStage.HEROES)
        assertThat(coin.goTo(SetupStage.PLAYERS).stage).isEqualTo(SetupStage.PLAYERS)
        assertThat(four.goTo(SetupStage.COIN).stage).isEqualTo(SetupStage.PLAYERS)
        assertThat(four.back()).isNull()
    }

    @Test
    fun `moving, locking and balancing by Elo`() {
        val form = SetupForm().withPlayers(listOf("ann", "bo", "cy", "di"))
        val moved = form.movePlayer("ann")
        assertThat(moved.blue).contains("ann")
        val locked = form.toggleLock("ann").toggleLock("bo")
        assertThat(locked.locked).containsExactly("ann", Team.RED, "bo", Team.BLUE)
        val options = state(locked).players
        val balanced = locked.withTeamMode(TeamMode.BALANCED, options, seed = 1L)
        assertThat(balanced.teamMode).isEqualTo(TeamMode.BALANCED)
        assertThat(balanced.red).contains("ann")
        assertThat(balanced.blue).contains("bo")
        // Ann 1400 + Di 1000 vs Bo 1300 + Cy 1100: equal means.
        assertThat(balanced.red).containsExactly("ann", "di")
        assertThat(balanced.balanceDifference).isEqualTo(0.0)
    }

    @Test
    fun `player draft suggests the most experienced captains and hides team-only methods`() {
        val form = SetupForm().withPlayers(
            listOf("ann", "bo", "cy", "di"),
        ).withTeamMode(TeamMode.PLAYER_DRAFT, state(SetupForm()).players, 0L)
        assertThat(form.redCaptain).isEqualTo("ann")
        assertThat(form.blueCaptain).isEqualTo("bo")
        assertThat(form.withCaptain(Team.BLUE, "ann").redCaptain).isNull()
        val s = state(form)
        assertThat(s.methods.first { it.method.id == "pick-and-ban" }.unavailable)
            .isEqualTo("Not available with Player Draft (needs teams)")
        assertThat(s.methods.first { it.method.id == "all-pick" }.unavailable).isNull()
        assertThat(s.seats).isEmpty()
    }

    @Test
    fun `odd counts seat a double pilot on the smaller team or play with Handicap cards`() {
        val form = SetupForm().withPlayers(listOf("ann", "bo", "cy", "di", "ed"))
        assertThat(form.red).hasSize(3)
        val double = state(form)
        assertThat(double.seats).hasSize(6)
        assertThat(double.seatedDoublePilot).isEqualTo("bo")
        assertThat(state(form.withDoublePilot("di")).seatedDoublePilot).isEqualTo("di")
        val handicap = state(form.withOddMode(OddSeatMode.HANDICAP))
        assertThat(handicap.seats).hasSize(5)
        assertThat(handicap.seats.filter { it.handicap }.map { it.team }.toSet()).containsExactly(Team.RED)
    }

    @Test
    fun `a filter that leaves too few heroes blocks the method, and a small random pool is flagged`() {
        val form = SetupForm(
            stage = SetupStage.HEROES,
        ).withPlayers(listOf("ann", "bo", "cy", "di")).copy(stage = SetupStage.HEROES)
        val few = form.withPoolSource(PoolSource.CUSTOM).withCustomSelection(
            FakeCatalog.catalog().heroes.take(3).map {
                it.id
            }.toSet(),
        )
        assertThat(state(few).blocker).isEqualTo("Needs 6 heroes, only 3 match the filters")
        val some = few.withCustomSelection(FakeCatalog.catalog().heroes.take(8).map { it.id }.toSet())
        assertThat(state(some).poolNote).isEqualTo("Only 8 heroes match your filters, so the pool is 8 instead of 15.")
        assertThat(state(some).blocker).isNull()
        val dealtWithBans = DraftMethod(
            id = "custom-x",
            name = "X",
            kind = DraftMethodKind.CUSTOM,
            pool = PoolRule.DealPerSeat(3),
            sequence = SequenceRule.Pattern(
                opening = listOf(ban(TeamSlot.A)),
                repeat = listOf(pick(TeamSlot.A), pick(TeamSlot.B)),
            ),
        )
        val custom = DraftSetupPresenter.derive(
            inputs.copy(methods = MethodLibrary.of(bundledMethods, listOf(dealtWithBans))),
            form,
            null,
        )
        assertThat(custom.methods.first { it.method.id == "custom-x" }.unavailable).isNotNull()
    }

    @Test
    fun `starting deals a board and the form becomes the next game's default`() {
        val form = SetupForm().withPlayers(
            listOf("ann", "bo", "cy", "di"),
        ).copy(stage = SetupStage.COIN, teamA = Team.RED)
        val s = state(form)
        val progress = DraftSetupPresenter.start(inputs, s, FixedEnvironment())!!.getOrThrow() as DraftProgress.Board
        assertThat(progress.session.method.id).isEqualTo("house")
        assertThat(progress.session.pool).hasSize(15)
        val next = form.forNextGame()
        assertThat(next.stage).isEqualTo(SetupStage.PLAYERS)
        assertThat(next.teamA).isNull()
        assertThat(next.lastGame).isTrue()
        assertThat(next.red).isEqualTo(form.red)
        assertThat(DraftSetupPresenter.resumeInfo(progress)).isEqualTo(ResumeInfo("House draft", "Step 1 of 6"))
        assertThat(DraftSetupPresenter.start(inputs, state(form.copy(teamA = null)), FixedEnvironment())).isNull()
    }

    @Test
    fun `the last-game note needs the group to have played the last completed match`() {
        val form = SetupForm().withPlayers(listOf("ann", "bo", "cy", "di")).forNextGame()
        assertThat(DraftSetupPresenter.lastGameNote(form, emptyList())).isNull()
        val sameTeams = match("m1", listOf("bo", "di"), listOf("ann", "cy"))
        assertThat(
            DraftSetupPresenter.lastGameNote(form, listOf(sameTeams)),
        ).isEqualTo("Same players and teams as last game")
        val other = match("m2", listOf("ann", "bo"), listOf("cy", "di"), playedAt = 10)
        assertThat(
            DraftSetupPresenter.lastGameNote(form, listOf(sameTeams, other)),
        ).isEqualTo("Same players as last game")
        assertThat(
            DraftSetupPresenter.lastGameNote(
                form.togglePlayer("ann").togglePlayer("ann").copy(lastGame = false),
                listOf(other),
            ),
        )
            .isNull()
    }

    @Test
    fun `draft again takes each team's players, the double pilot and the Handicap layout from the match`() {
        val doublePiloted = Match(
            id = "m",
            createdAt = 0,
            playedAt = 0,
            status = MatchStatus.COMPLETED,
            mode = GameMode.QUICK,
            participants = listOf(
                Participant("ann", "h1", Team.RED),
                Participant("bo", "h2", Team.RED),
                Participant("cy", "h3", Team.RED),
                Participant("di", "h4", Team.BLUE),
                Participant("ed", "h5", Team.BLUE),
                Participant("ed", "h6", Team.BLUE),
            ),
        )
        val group = DraftAgain.of(doublePiloted)
        assertThat(group).isEqualTo(DraftAgain(listOf("ann", "bo", "cy"), listOf("di", "ed"), "ed", handicap = false))
        val form = SetupForm(
            methodId = "all-pick",
            stepTimer = true,
            teamA = Team.RED,
            stage = SetupStage.COIN,
        ).redraft(group)
        assertThat(form.selected).containsExactly("ann", "bo", "cy", "di", "ed").inOrder()
        assertThat(form.oddMode).isEqualTo(OddSeatMode.DOUBLE_PILOT)
        assertThat(form.doublePilot).isEqualTo("ed")
        assertThat(form.methodId).isEqualTo("all-pick")
        assertThat(form.stepTimer).isTrue()
        assertThat(form.stage).isEqualTo(SetupStage.PLAYERS)
        assertThat(form.teamA).isNull()
        assertThat(form.lastGame).isTrue()

        val uneven = DraftAgain.of(doublePiloted.copy(participants = doublePiloted.participants.dropLast(1)))
        assertThat(uneven.handicap).isTrue()
        assertThat(SetupForm().redraft(uneven).oddMode).isEqualTo(OddSeatMode.HANDICAP)
    }

    @Test
    fun `quick add takes a colour and crest nobody has together, and ignores blank names`() {
        val colors = listOf(1L, 2L, 3L)
        val existing = List(25) { i ->
            dev.quinton.goa.core.model.player.Player("p$i", "P$i", colors[i % 3], avatarId = i % 24, createdAt = 0)
        }
        val created = QuickAddPlayer.create("p", "  Fay ", existing, now = 7L, colors = colors)!!
        assertThat(created.name).isEqualTo("Fay")
        // In turn it would be colour 2 with crest 1, which P1 has: the next crest instead.
        assertThat(created.color).isEqualTo(2L)
        assertThat(created.avatarId).isEqualTo(2)
        assertThat(QuickAddPlayer.create("p", " ", emptyList(), 0L, listOf(1L))).isNull()
        assertThat(SetupForm().withAddedPlayer("ann").withAddedPlayer("ann").selected).containsExactly("ann")
    }

    @Test
    fun `players who no longer exist drop out of a restored form`() {
        val form = SetupForm().withPlayers(listOf("ann", "bo", "cy")).copy(stage = SetupStage.HEROES, teamA = Team.RED)
        val kept = form.keepOnly(setOf("ann", "bo"))
        assertThat(kept.selected).containsExactly("ann", "bo")
        assertThat(kept.stage).isEqualTo(SetupStage.PLAYERS)
        assertThat(kept.teamA).isNull()
        assertThat(form.keepOnly(setOf("ann", "bo", "cy"))).isSameInstanceAs(form)
    }

    private fun match(id: String, red: List<String>, blue: List<String>, playedAt: Long = 0) = Match(
        id = id,
        createdAt = playedAt,
        playedAt = playedAt,
        status = MatchStatus.COMPLETED,
        mode = GameMode.QUICK,
        winner = Team.RED,
        participants = red.map { Participant(it, null, Team.RED) } + blue.map { Participant(it, null, Team.BLUE) },
    )

    @Test
    fun `quick add keeps names to the player editor's limit on the phone and the web`() {
        val long = "  " + "A".repeat(40) + "  "
        val player = QuickAddPlayer.create("id", long, existing = emptyList(), now = 1, colors = listOf(1L))!!
        assertThat(player.name).isEqualTo("A".repeat(dev.quinton.goa.core.presentation.players.MAX_NAME_LENGTH))
        assertThat(QuickAddPlayer.create("id", "   ", emptyList(), 1, listOf(1L))).isNull()
    }
}
