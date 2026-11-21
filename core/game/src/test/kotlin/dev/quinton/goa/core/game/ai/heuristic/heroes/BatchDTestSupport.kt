package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.heuristic.General
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/** Shared checks for the batch D playbooks (tigerclaw–xargatha). */
internal object BatchDTestSupport {
    /** The hero finishes 2v2 and 3v3 games at strengths 5 and 10, on either side. */
    fun finishes(hero: String) {
        val mates = listOf("arien", "brogan", "bain", "brynn", "min", "misa", "sabina").filter { it != hero }
        for (size in listOf(2, 3)) {
            for (strength in listOf(5, 10)) {
                val red = listOf(hero) + mates.take(size - 1)
                val blue = mates.drop(size - 1).take(size)
                val (r, b) = if (strength == 5) red to blue else blue to red
                val level = AiLevel.of(strength)
                val played = AiHarness.play(
                    AiHarness.setup(r, b, level, level, Team.RED),
                    mapOf(
                        Team.RED to HeuristicAi(strength, 11L + size),
                        Team.BLUE to HeuristicAi(strength, 12L + size),
                    ),
                )
                assertWithMessage("$hero ${size}v$size strength $strength").that(played.session.pending).isNull()
                assertThat(played.winner).isNotNull()
            }
        }
    }

    /**
     * Turn [turnNumber], cards being played: Arien (Blue) has played; [hero] (Red) is to choose
     * from [hand]. [enemy] adds to Arien; [also] adds minions, tokens and the rest.
     */
    @Suppress("LongParameterList")
    fun playing(
        hero: String,
        at: Hex,
        hand: List<String>,
        enemyAt: Hex,
        turnNumber: Int = 3,
        mine: PositionBuilder.HeroBuilder.() -> Unit = {},
        enemy: PositionBuilder.HeroBuilder.() -> Unit = {},
        also: PositionBuilder.() -> Unit = {},
    ): EngineSession = position(TestData.engine, TestData.FOREST) {
        clearMinions()
        turn = turnNumber
        hero("arien", Team.BLUE, enemyAt) {
            played("arien-blue-1")
            enemy()
        }
        hero(hero, Team.RED, at) {
            hand(*hand.toTypedArray())
            mine()
        }
        also()
        phase = PositionPhase.PlayCards(fromSeat = 1)
    }

    /** What strength 8 plays with only the general book, and with [book]'s notes added. */
    fun picks(session: EngineSession, book: HeroHeuristics): Pair<Choice, Choice> {
        val pending = session.pending ?: error("no pending decision")
        val ctx = HContext(session, pending, pending.team, HeuristicProfile.of(8))
        val plain = pending.options.maxBy { General.score(ctx, pending, it) }
        val noted = pending.options.maxBy { General.score(ctx, pending, it) + book.adjust(ctx, pending, it) }
        return plain to noted
    }

    /** The general book plays [from]; the hero's note turns it into [to]. A null side is "anything else". */
    fun assertFlips(session: EngineSession, book: HeroHeuristics, from: String?, to: String?) {
        val pending = session.pending ?: error("no pending decision")
        check(pending.kind == DecisionKind.PLAY_CARD) { "expected a card play, got ${pending.kind}: ${pending.prompt}" }
        check(pending.seat == book.heroId) { "seat is ${pending.seat}" }
        val (plain, noted) = picks(session, book)
        val dump = scores(session, book)
        if (from != null) assertWithMessage(dump).that(plain).isEqualTo(Choice.Card(from))
        if (to != null) assertWithMessage(dump).that(noted).isEqualTo(Choice.Card(to))
        assertWithMessage(dump).that(noted).isNotEqualTo(plain)
    }

    fun scores(session: EngineSession, book: HeroHeuristics): String {
        val pending = session.pending!!
        val ctx = HContext(session, pending, pending.team, HeuristicProfile.of(8))
        return pending.options.joinToString("\n", prefix = "\n") { option ->
            val general = General.score(ctx, pending, option)
            val note = book.adjust(ctx, pending, option)
            "$option general=${"%.2f".format(general)} note=${"%.2f".format(note)}"
        }
    }
}
