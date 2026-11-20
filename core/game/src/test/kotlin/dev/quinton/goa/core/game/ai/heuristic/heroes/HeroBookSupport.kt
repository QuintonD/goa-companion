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
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/** Shared checks for the tigerclaw–xargatha playbooks. */
internal object HeroBookSupport {
    fun finishes(hero: String) {
        val mates = listOf("arien", "brogan", "bain", "brynn", "min", "misa", "sabina").filter { it != hero }
        for (size in listOf(2, 3)) {
            for (strength in listOf(5, 10)) {
                val red = listOf(hero) + mates.take(size - 1)
                val blue = mates.drop(size - 1).take(size)
                val level = AiLevel.of(strength)
                val played = AiHarness.play(
                    AiHarness.setup(red, blue, level, level, Team.RED),
                    mapOf(
                        Team.RED to HeuristicAi(strength, 11L),
                        Team.BLUE to HeuristicAi(strength, 12L),
                    ),
                )
                assertWithMessage("$hero ${size}v$size strength $strength")
                    .that(played.session.pending).isNull()
                assertThat(played.winner).isNotNull()
            }
        }
    }

    fun playing(
        hero: String,
        at: Hex,
        hand: List<String>,
        enemyAt: Hex,
        turnNumber: Int = 1,
        thrown: List<String> = emptyList(),
        also: PositionBuilder.() -> Unit = {},
    ): EngineSession = position(TestData.engine, TestData.FOREST) {
        clearMinions()
        turn = turnNumber
        hero("arien", Team.BLUE, enemyAt) {
            played("arien-blue-1")
            if (thrown.isNotEmpty()) discard(*thrown.toTypedArray())
        }
        hero(hero, Team.RED, at) { hand(*hand.toTypedArray()) }
        also()
        phase = PositionPhase.PlayCards(fromSeat = 1)
    }

    /** The general book picks [awayFrom]; the hero note picks something else. */
    fun assertFlips(session: EngineSession, book: HeroHeuristics, awayFrom: String) {
        val pending = session.pending ?: error("no pending decision")
        check(pending.kind == DecisionKind.PLAY_CARD) {
            "expected a card play, got ${pending.kind} for ${pending.seat}: ${pending.prompt}"
        }
        check(pending.seat == book.heroId) { "seat is ${pending.seat}" }
        val plain = pick(session, book, adjust = false)
        val noted = pick(session, book, adjust = true)
        val dump = scores(session, book)
        assertWithMessage(dump).that(plain).isEqualTo(Choice.Card(awayFrom))
        assertWithMessage(dump).that(noted).isNotEqualTo(plain)
    }

    private fun pick(session: EngineSession, book: HeroHeuristics, adjust: Boolean): Choice {
        val pending = session.pending!!
        val ctx = HContext(session, pending, pending.team, HeuristicProfile.of(8))
        return pending.options.maxBy { option ->
            General.score(ctx, pending, option) + if (adjust) book.adjust(ctx, pending, option) else 0.0
        }
    }

    private fun scores(session: EngineSession, book: HeroHeuristics): String {
        val pending = session.pending!!
        val ctx = HContext(session, pending, pending.team, HeuristicProfile.of(8))
        return pending.options.joinToString("\n") { option ->
            val general = General.score(ctx, pending, option)
            val note = book.adjust(ctx, pending, option)
            "$option general=$general note=$note total=${general + note}"
        }
    }
}
