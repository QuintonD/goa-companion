package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Another one of Razzle on the board, for other heroes' tests of her FAQ ("During any action … all the
 * Razzles other than the active one is considered 'another hero'").
 */
internal fun PositionBuilder.razzleDouble(id: String, at: Hex, team: Team) = piece(
    PieceState(
        id, RazzleHero.DOUBLE, "Razzle", at, owner = "razzle", team = team, token = false, unit = true,
        removeAtEndOfRound = false, heroBody = "razzle",
    ),
)
