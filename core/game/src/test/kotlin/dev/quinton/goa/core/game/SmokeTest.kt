package dev.quinton.goa.core.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmokeTest {
    @Test
    fun `a random first game ends with a winner`() {
        for (seed in 1L..20L) {
            val s = TestData.newGame()
            val n = playOut(s, RandomPlayer(seed))
            val v = s.view(null)
            val ev = s.events
            val heroDefeats = ev.filterIsInstance<GameEvent.Defeated>().count { it.unitId in setOf("wasp", "xargatha", "arien", "brogan") }
            val minionDefeats = ev.filterIsInstance<GameEvent.Defeated>().size - heroDefeats
            val attacksOnHeroes = ev.filterIsInstance<GameEvent.Attacked>().count { it.targetId in setOf("wasp", "xargatha", "arien", "brogan") }
            val pushes = ev.filterIsInstance<GameEvent.LanePushed>().size
            val actions = ev.filterIsInstance<GameEvent.ActionChosen>().groupingBy { it.action }.eachCount()
            println(
                "seed $seed: $n decisions, round ${v.round}, winner ${v.winner} by ${v.winReason}; heroAttacks $attacksOnHeroes " +
                    "heroDefeats $heroDefeats minionDefeats $minionDefeats pushes $pushes life ${v.lifeCounters} actions $actions",
            )
            assertThat(v.winner).isNotNull()
        }
        val s = TestData.newGame()
        playOut(s, RandomPlayer(3))
        s.events.take(400).forEach { println("  ${it.text}") }
    }
}
