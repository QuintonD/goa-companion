package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.SavedPayload
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json

/**
 * Whole games recorded by `HeuristicDiagTest` (`AI_H_DIAG_SAVE`): the start and every answer. [at] rebuilds the
 * position just before one question of the game by replaying the recorded answers, with no player asked (the
 * engine is deterministic, so this is the game's own position).
 */
internal object RecordedGames {
    private fun load(name: String): SavedGame {
        val stream = RecordedGames::class.java.getResourceAsStream("/lastpush/$name.json.gz")
            ?: error("no recorded game $name")
        val text = GZIPInputStream(stream).bufferedReader().use { it.readText() }
        return Json.decodeFromString(SavedGame.serializer(), text)
    }

    /**
     * The game [name] just before the [nth] question (0 = the first) asked of [seat] of [kind] in [round], [turn].
     * The answer the game gave there is [Position.played].
     */
    fun at(name: String, round: Int, turn: Int, seat: String, kind: DecisionKind, nth: Int = 0): Position {
        val saved = load(name)
        val payload = EngineSession.JSON.decodeFromString(SavedPayload.serializer(), saved.payload)
        val start = SavedGame(
            saved.engineVersion,
            saved.setup,
            EngineSession.JSON.encodeToString(SavedPayload.serializer(), SavedPayload(payload.initial, emptyList())),
        )
        val s = TestData.engine.restore(start) as EngineSession
        var seen = 0
        for (a in payload.answers) {
            val d = s.pending ?: break
            val st = s.state
            if (st.round == round && st.turn == turn && d.seat == seat && d.kind == kind) {
                if (seen == nth) return Position(s, a.choice)
                seen++
            }
            s.answer(a.decisionId, a.choice)
        }
        error("$name: no $kind of $seat in round $round turn $turn (#$nth)")
    }

    class Position(val session: EngineSession, val played: dev.quinton.goa.core.game.Choice)
}
