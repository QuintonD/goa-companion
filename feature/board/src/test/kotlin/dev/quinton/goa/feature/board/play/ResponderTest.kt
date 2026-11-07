package dev.quinton.goa.feature.board.play

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.decisionDirective
import dev.quinton.goa.feature.board.play.table.decisionOwner
import dev.quinton.goa.feature.board.play.table.responderId
import dev.quinton.goa.feature.board.play.table.turnLog
import org.junit.Test

/**
 * Who acts and who answers (UI hill climb, round 3): while one hero resolves its card and another must answer it,
 * the panel's strip names both and the turn strip knows the one asked; when the acting hero decides, one name.
 */
class ResponderTest {
    private val all = setOf("arien", "brogan", "wasp", "tigerclaw")

    private fun tableAt(kind: DecisionKind): TableUi {
        val session = fakeFactory().newGame(fakeSetup(humans = all)) as FakeGameSession
        repeat(MAX_ANSWERS) {
            val pending = session.pending
            if (pending != null && pending.kind != kind) session.answer(pending.id, session.preferred()!!)
        }
        val catalog = bundledCatalog
        val cards = catalog.heroes.flatMap { hero -> hero.allCards }.associateBy { card -> card.id }
        return TableUi(
            map = bundledMaps.first { map -> map.id == "forgotten-island" },
            view = session.view("arien"),
            heroes = catalog.boardHeroes(),
            cards = cards,
            mine = all,
            decision = session.pending,
            asking = true,
            bottomTeam = Team.RED,
            turnLog = turnLog(session.events),
        )
    }

    @Test
    fun `a defence names the defender against the attacker, and the defender answers`() {
        val table = tableAt(DecisionKind.DEFEND)
        val defender = table.decision!!.seat
        val acting = table.track!!.acting!!.heroId
        assertThat(defender).isNotEqualTo(acting)
        assertThat(responderId(table)).isEqualTo(defender)
        val attacker = table.pieceName(table.decision!!.context.attackerId!!)
        assertThat(decisionOwner(table)).isEqualTo("You choose · ${table.hero(defender).name}")
        assertThat(decisionDirective(table)).isEqualTo("Defend against $attacker")
    }

    @Test
    fun `the acting hero's own question names one hero`() {
        val table = tableAt(DecisionKind.CHOOSE_ACTION)
        assertThat(responderId(table)).isNull()
        val name = table.hero(table.decision!!.seat).name
        assertThat(decisionOwner(table)).isEqualTo("You choose · $name")
        assertThat(decisionDirective(table)).isEqualTo("Choose an action")
    }

    private companion object {
        const val MAX_ANSWERS = 80
    }
}
