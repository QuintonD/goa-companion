package dev.quinton.goa.feature.board.play

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.MAP
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.cards
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.engine
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.heroes
import dev.quinton.goa.feature.board.play.table.FlowKind
import dev.quinton.goa.feature.board.play.table.ResolutionFlow
import dev.quinton.goa.feature.board.play.table.StepKind
import dev.quinton.goa.feature.board.play.table.resolutionFlow
import kotlin.random.Random
import org.junit.Test

/**
 * The resolution flow (ADR-046) on the real rules engine: one entry per hero's card resolution since
 * the person last chose, the defending card as a card, the person's attack defended by an enemy open
 * at first, the end of the round as one entry; and it shows only cards the log names (p.4).
 */
class ResolutionFlowTest {
    private fun name(id: String) = cards.getValue(id).name

    private fun flow(s: EngineSession, from: Int, mine: Set<String>): ResolutionFlow = resolutionFlow(
        s.events,
        from,
        s.setup.seats.associate { it.heroId to it.team },
        mine,
        { heroes.hero(it).name },
        { cards[it]?.name },
    )

    private fun EngineSession.answer(choice: Choice) {
        val d = pending ?: error("No question")
        check(answer(d.id, choice) is dev.quinton.goa.core.game.AnswerResult.Accepted) { "Rejected $choice for $d" }
    }

    private fun EngineSession.action(action: ActionType) = answer(Choice.Action(action))

    @Test
    fun anAttackDefendedByAnEnemyIsOpenAndNamesTheDefenseCard() {
        // Brogan (the person) attacks Wasp with Onslaught (Attack 4); Wasp defends with Lift Up (Defense 5).
        val s = position(engine, MAP) {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1", "wasp-red-1") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        val before = s.events.size
        s.action(ActionType.PRIMARY)
        s.answer(Choice.Card("wasp-blue-1"))
        val flow = flow(s, before, mine = setOf("brogan"))
        val entry = flow.entries.single()
        assertThat(entry.kind).isEqualTo(FlowKind.HERO)
        assertThat(entry.heroId).isEqualTo("brogan")
        assertThat(entry.cardId).isEqualTo("brogan-gold")
        assertThat(entry.line).isEqualTo("Onslaught → Wasp defended with Lift Up (5 vs 4)")
        assertThat(entry.open).isTrue()
        assertThat(entry.mine).isTrue()
        val defense = entry.steps.single { it.kind == StepKind.DEFENSE }
        assertThat(defense.cards).containsExactly("wasp-blue-1")
        assertThat(defense.success).isTrue()
        assertThat(defense.heroId).isEqualTo("wasp")
        // The defense's own "Wasp discards Lift Up." is folded into the defense step.
        assertThat(entry.steps.map { it.text }).doesNotContain("Wasp discards Lift Up.")
        assertThat(
            entry.steps.map {
                it.kind
            },
        ).containsAtLeast(StepKind.ACTION, StepKind.ATTACK, StepKind.DEFENSE).inOrder()
        assertThat(entry.spoken).isEqualTo("Brogan: Onslaught, Wasp defended with Lift Up (5 against 4).")
    }

    @Test
    fun aMoveThenAttackWithAFailedDefenseIsOneEntryWithEveryStep() {
        // Brogan's Mad Dash: "Before the attack: Move 2 spaces in a straight line to a space adjacent to an
        // enemy unit, then target that unit." Attack 6 against Wasp's Lift Up (Defense 5): Wasp is defeated.
        val s = position(engine, MAP) {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(3, -2)) { played("brogan-red-1") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1", "wasp-red-1") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        val before = s.events.size
        s.action(ActionType.PRIMARY)
        while (s.pending?.kind != DecisionKind.DEFEND) s.answer(s.pending!!.options.first())
        val defend = s.events.size
        s.answer(Choice.Card("wasp-blue-1"))
        // The person is Wasp: their last answer was the defense; the whole of Brogan's card is the entry.
        val entry = flow(s, defend, mine = setOf("wasp")).entries.single()
        assertThat(entry.heroId).isEqualTo("brogan")
        assertThat(entry.line).startsWith("Mad Dash → Wasp defeated: Lift Up fell short (5 vs 6)")
        assertThat(entry.important).isTrue()
        assertThat(entry.open).isFalse() // the enemy's attack on the person: bold, not opened
        val kinds = entry.steps.map { it.kind }
        assertThat(
            kinds,
        ).containsAtLeast(StepKind.ACTION, StepKind.MOVE, StepKind.ATTACK, StepKind.DEFENSE, StepKind.DEFEAT)
            .inOrder()
        assertThat(entry.steps.single { it.kind == StepKind.DEFENSE }.success).isFalse()
        assertThat(entry.steps.single { it.kind == StepKind.DEFEAT }.important).isTrue()
        // Seen from before Brogan's choice too: the same single entry.
        assertThat(flow(s, before, mine = setOf("wasp")).entries.map { it.key }).containsExactly(entry.key)
    }

    @Test
    fun aForcedDiscardShowsTheDiscardedCardAndNamesIt() {
        // Wasp's Shock: "After the attack: An enemy hero in radius and not adjacent to you discards a card, if able."
        val s = position(engine, MAP) {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { played("wasp-red-1") }
            hero("brogan", Team.BLUE, Hex(0, -2)) { hand("brogan-silver") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1", "arien-green-1") }
            phase = PositionPhase.Resolving(next = "wasp")
        }
        val before = s.events.size
        s.action(ActionType.PRIMARY)
        while (s.pending?.kind != DecisionKind.CHOOSE_CARD || s.pending?.seat != "arien") {
            val d = s.pending!!
            s.answer(if (d.kind == DecisionKind.DEFEND) d.options.last() else d.options.first())
        }
        s.answer(Choice.Card("arien-green-1"))
        val entry = flow(s, before, mine = setOf("wasp")).entries.single()
        val discard = entry.steps.single { it.kind == StepKind.CARDS && it.heroId == "arien" }
        assertThat(discard.cards).containsExactly("arien-green-1")
        assertThat(entry.line).contains("Arien discarded ${name("arien-green-1")}")
        assertThat(entry.line).startsWith("Shock → Brogan defeated")
    }

    @Test
    fun aThreeVsThreeTurnGivesOneEntryPerHeroThatResolvedBeforeThePersonsNextQuestion() {
        val s = position(engine, MAP) {
            clearMinions()
            hero("tigerclaw", Team.RED, Hex(1, -2)) { hand("tigerclaw-green-1", "tigerclaw-gold") }
            hero("arien", Team.RED, Hex(0, -4)) { hand("arien-red-1") }
            hero("brogan", Team.RED, Hex(-1, -1)) { hand("brogan-red-1") }
            hero("wasp", Team.BLUE, Hex(4, -5)) { hand("wasp-red-1") }
            hero("xargatha", Team.BLUE, Hex(5, -7)) { hand("xargatha-red-1") }
            hero("gydion", Team.BLUE, Hex(3, -7)) { hand("gydion-red-1") }
            phase = PositionPhase.PlayCards()
        }
        var from = 0
        repeat(MAX_STEPS) {
            val d = s.pending!!
            if (d.seat == "tigerclaw" && d.kind == DecisionKind.CHOOSE_ACTION) return@repeat
            if (d.seat == "tigerclaw") from = s.events.size
            s.answer(simple(d, play = if (d.seat == "tigerclaw") "tigerclaw-green-1" else null))
        }
        check(s.pending?.seat == "tigerclaw" && s.pending?.kind == DecisionKind.CHOOSE_ACTION)
        val flow = flow(s, from, mine = setOf("tigerclaw"))
        val acted = s.events.drop(from).filterIsInstance<GameEvent.ActionChosen>().map { it.heroId }
        assertThat(acted).hasSize(5)
        assertThat(flow.fresh).isTrue()
        assertThat(
            flow.entries.filter {
                it.kind == FlowKind.HERO
            }.map { it.heroId },
        ).containsExactlyElementsIn(acted).inOrder()
        assertThat(flow.entries.map { it.key }.distinct()).hasSize(flow.entries.size)
        flow.entries.filter { it.kind == FlowKind.HERO }.forEach { e ->
            assertThat(e.line).startsWith("${cards.getValue(e.cardId!!).name} → ")
            assertThat(e.steps.first().kind).isEqualTo(StepKind.ACTION)
            assertThat(e.mine).isFalse()
        }
    }

    @Test
    fun theEndOfTheRoundIsOneEntryWithTheMinionBattleInItsLine() {
        val s = EndOfRoundGame.session()
        val before = s.events.size
        EndOfRoundGame.next(s) // Gydion removes a minion; the round ends up to his level up
        val flow = flow(s, before, mine = setOf("gydion"))
        val round = flow.entries.single { it.kind == FlowKind.END_OF_ROUND }
        assertThat(round.title).isEqualTo("End of round 1")
        assertThat(round.line).contains("Minion Battle: Red removes 1")
        assertThat(round.important).isTrue()
        assertThat(round.steps.any { it.important && it.text.startsWith("Minion Battle") }).isTrue()
    }

    @Test
    fun nothingNewShowsTheLastEntryBeforeCollapsed() {
        val s = position(engine, MAP) {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1", "wasp-red-1") }
            phase = PositionPhase.Resolving(next = "brogan")
        }
        s.action(ActionType.PRIMARY)
        s.answer(Choice.Card("wasp-blue-1"))
        val flow = flow(s, s.events.size, mine = setOf("brogan"))
        assertThat(flow.fresh).isFalse()
        assertThat(flow.entries.single().heroId).isEqualTo("brogan")
        assertThat(flow.entries.single().open).isFalse()
    }

    @Test
    fun aFacedownDiscardShowsNoCard() {
        val events = listOf(
            GameEvent.TurnStarted(1, 2, "Turn 2 of round 1."),
            GameEvent.ActionChosen("wasp", "wasp-red-1", ActionType.PRIMARY, "Wasp uses Shock: primary action."),
            GameEvent.CardsChanged("arien", "Arien discards a facedown card."),
        )
        val flow = resolutionFlow(
            events,
            0,
            mapOf("wasp" to Team.RED, "arien" to Team.BLUE),
            setOf("arien"),
            { heroes.hero(it).name },
            { cards[it]?.name },
        )
        val entry = flow.entries.single()
        assertThat(entry.steps.single { it.kind == StepKind.CARDS }.cards).isEmpty()
        assertThat(entry.line).isEqualTo("Shock → Arien discarded a facedown card")
    }

    /**
     * No peeking (p.4): in random 3v3 games, at every question, each card the flow shows for the asking
     * seat is a card its step's sentence names (the log never names a hidden card, RULINGS 51/53), and
     * an entry's card is the card its hero revealed.
     */
    @Test
    fun theFlowShowsOnlyCardsTheLogNames() {
        val setup = GameSetup(
            MAP,
            listOf("tigerclaw", "wasp", "gydion").map { SeatSetup(it, Team.RED, Controller.Human) } +
                listOf("takahide", "arien", "brogan").map { SeatSetup(it, Team.BLUE, Controller.Human) },
            length = GameLength.QUICK,
            tieBreaker = Team.RED,
        )
        val s = engine.newGame(setup) as EngineSession
        val rng = Random(SEED)
        val answered = mutableMapOf<String, Int>()
        var shown = 0
        repeat(MAX_DECISIONS) {
            val d = s.pending ?: return@repeat
            val flow = flow(s, answered[d.seat] ?: 0, mine = setOf(d.seat))
            val revealed = s.events.filterIsInstance<GameEvent.CardsRevealed>().flatMap { it.played.values }.toSet()
            flow.entries.forEach { e ->
                e.cardId?.let { assertWithMessage(e.line).that(revealed).contains(it) }
                e.steps.forEach { step ->
                    val cardsShown = if (step.kind == StepKind.ACTION) listOfNotNull(e.cardId) else step.cards
                    cardsShown.forEach { id ->
                        shown++
                        assertWithMessage(step.text).that(step.text).contains(name(id))
                    }
                }
            }
            answered[d.seat] = s.events.size
            s.answer(d.id, d.options[rng.nextInt(d.options.size)])
        }
        assertThat(shown).isGreaterThan(0)
    }

    /** First options, Movement over other actions, the given card to play: a quiet turn. */
    private fun simple(d: Decision, play: String?): Choice = when {
        d.kind == DecisionKind.PLAY_CARD && play != null -> Choice.Card(play)

        d.kind == DecisionKind.CHOOSE_ACTION ->
            d.options.firstOrNull { (it as? Choice.Action)?.action == ActionType.MOVEMENT } ?: d.options.first()

        else -> d.options.first()
    }

    private companion object {
        const val MAX_STEPS = 60
        const val MAX_DECISIONS = 600
        const val SEED = 46L
    }
}
