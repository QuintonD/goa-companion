package dev.quinton.goa.feature.board.play

import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.bundledMaps

/** The scripted game's setup: Arien is the person's unless [humans] says otherwise. */
fun fakeSetup(humans: Set<String> = setOf("arien"), level: AiLevel = AiLevel.HARD, coin: Team = Team.RED) = GameSetup(
    mapId = "forgotten-island",
    seats = FAKE_LINEUP.red.map { SeatSetup(it, Team.RED, controller(it, humans, level)) } +
        FAKE_LINEUP.blue.map { SeatSetup(it, Team.BLUE, controller(it, humans, level)) },
    tieBreaker = coin,
)

private fun controller(heroId: String, humans: Set<String>, level: AiLevel) =
    if (heroId in humans) Controller.Human else Controller.Ai(level)

/** A fresh scripted game as it is saved when New game starts it. */
fun fakeInProgress(
    factory: FakeGameFactory,
    setup: GameSetup = fakeSetup(),
    coach: Boolean = true,
    pace: AiPace = AiPace.NORMAL,
): GameInProgress = GameInProgress(factory.newGame(setup).save(), coach = coach, pace = pace, startedAt = 1L)

fun fakeFactory() = FakeGameFactory(bundledMaps)
