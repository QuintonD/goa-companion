package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.heuristic.DefaultHeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.General
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team

/** Shared checks for the arien–garrus playbooks. */
internal object HeroBookCases {
    fun finishes(hero: String) {
        val twos = listOf(listOf(hero, "wasp") to listOf("xargatha", "min"))
        val threes = listOf(listOf(hero, "wasp", "xargatha") to listOf("min", "misa", "sabina"))
        for (strength in listOf(5, 10)) {
            for ((red, blue) in twos + threes) {
                val level = AiLevel.of(strength)
                val played = AiHarness.play(
                    AiHarness.setup(red, blue, level, level, Team.RED),
                    mapOf(Team.RED to HeuristicAi(strength, 11), Team.BLUE to HeuristicAi(strength, 12)),
                )
                assertThat(played.session.pending).isNull()
                assertThat(played.winner).isNotNull()
            }
        }
    }

    /**
     * The signature note, added to the general score, picks a different card than the general
     * book alone. Strength 10, so the playbook is on and a card play is not re-scored by lookahead.
     */
    fun flips(hero: String, book: HeroHeuristics, session: EngineSession) {
        val d = checkNotNull(session.pending) { "the position asked nothing" }
        assertThat(d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.seat).isEqualTo(hero)
        val ctx = HContext(
            session,
            d,
            d.team,
            HeuristicProfile.of(10),
            playbooks = { id -> if (id == hero) book else DefaultHeroHeuristics },
        )
        fun plain(c: Choice) = General.score(ctx, d, c)
        fun total(c: Choice) = plain(c) + book.adjust(ctx, d, c)
        val without = d.options.maxBy { plain(it) }
        val withBook = d.options.maxBy { total(it) }
        val dump = d.options.joinToString("\n") { "$it plain=${plain(it)} adj=${book.adjust(ctx, d, it)}" }
        assertWithMessage(dump).that(withBook).isNotEqualTo(without)
    }
}
