package dev.quinton.goa.feature.board.play.setup

import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.setup.LineupPicker
import dev.quinton.goa.feature.board.setup.LineupSource
import dev.quinton.goa.feature.board.setup.MapOption
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.NewBoardForm
import dev.quinton.goa.feature.board.setup.NewBoardRules

/** Who the person plays: one hero, or every hero of a team (the rest are the computer). */
sealed interface Control {
    data class Hero(val heroId: String) : Control

    data class WholeTeam(val team: Team) : Control
}

/** New game in two steps: the line-up, then the options. */
enum class NewGameStep { LINEUP, OPTIONS }

/** The game being set up. Kept in the SavedStateHandle, so it survives process death. */
data class NewGameForm(
    val lineup: NewBoardForm = NewBoardForm(),
    val step: NewGameStep = NewGameStep.LINEUP,
    /** null = the default: your whole first team (one hero in 1v1). */
    val control: Control? = null,
    /** The computer's strength, 1..10 (ADR-039). */
    val strength: Int = AiStrength.DEFAULT,
    /** Advanced: a strength per computer seat, overriding [strength]. */
    val seatStrengths: Map<String, Int> = emptyMap(),
    val advanced: Boolean = false,
    val length: GameLength = GameLength.QUICK,
    val mapId: String? = null,
    val coach: Boolean = true,
    /** The Tie Breaker coin as the app flipped it (p.5 step 3); null until Start. */
    val coin: Team? = null,
)

/** One computer seat in the advanced list. */
data class AiSeatUi(val hero: BoardHero, val team: Team, val strength: Int)

sealed interface NewGameUiState {
    data object Loading : NewGameUiState

    /** No hero (or no map) can be played yet: the engine is still being built and tested. */
    data object ComingSoon : NewGameUiState

    data class Ready(
        val form: NewGameForm,
        val picker: LineupPicker,
        val sources: List<LineupSource>,
        /** Playable maps, verified single lane first. */
        val maps: List<MapOption>,
        val map: MapOption?,
        val playable: Int,
        /** The control choices for this line-up, and the chosen one. */
        val controls: List<Control>,
        val control: Control?,
        val aiSeats: List<AiSeatUi>,
        /** A saved game will be replaced by starting this one. */
        val replacesGame: Boolean,
    ) : NewGameUiState {
        val red: List<String> get() = form.lineup.red
        val blue: List<String> get() = form.lineup.blue

        /** Both teams the same size, at least one hero each. */
        val canContinue: Boolean get() = red.isNotEmpty() && red.size == blue.size

        val canStart: Boolean get() = canContinue && map != null && control != null

        fun hero(id: String): BoardHero = picker.heroById.hero(id)
    }
}

sealed interface NewGameAction {
    data class Lineup(val action: NewBoardAction) : NewGameAction

    data class Step(val step: NewGameStep) : NewGameAction

    data class SetControl(val control: Control) : NewGameAction

    data class SetStrength(val strength: Int) : NewGameAction

    data class SetSeatStrength(val heroId: String, val strength: Int) : NewGameAction

    data class SetAdvanced(val open: Boolean) : NewGameAction

    data class SetLength(val length: GameLength) : NewGameAction

    data class SetMap(val mapId: String) : NewGameAction

    data class SetCoach(val on: Boolean) : NewGameAction

    /** The app flips the Tie Breaker coin (p.5 step 3), shown before Start. */
    data object FlipCoin : NewGameAction

    data object CancelCoin : NewGameAction
}

/** 2v2 is the rulebook's standard; 1v1 is allowed but not its way (p.20). */
fun lineupNote(red: Int, blue: Int): String = when {
    red == 0 && blue == 0 -> "Pick heroes for both teams. Two each is the rulebook's standard."
    red != blue -> "Both teams need the same number of heroes."
    red == 1 -> "Allowed, but not the rulebook's way: with two players, each controls two heroes (p.20)."
    red == 2 -> "The rulebook's standard game."
    else -> "${red * 2} heroes on the board."
}

/**
 * New game's rules (PRODUCT §9), shared by the phone's NewGameViewModel and the web app: the form's
 * reducer (a hero that can't be played yet is never seated; changing the line-up drops the coin),
 * who the person may control, the computer seats, and the setup a ready form starts.
 */
object NewGameRules {
    /** [form] after [action]; [unavailable] heroes can't be added; [flip] tosses the Tie Breaker coin (p.5). */
    fun reduce(form: NewGameForm, action: NewGameAction, unavailable: Set<String>, flip: () -> Team): NewGameForm =
        when (action) {
            is NewGameAction.Lineup -> {
                val blocked = (action.action as? NewBoardAction.Tap)?.heroId?.let { id ->
                    id in unavailable && !form.lineup.contains(id)
                } == true
                if (blocked) form else form.copy(lineup = NewBoardRules.reduce(form.lineup, action.action), coin = null)
            }

            is NewGameAction.Step -> form.copy(step = action.step)

            is NewGameAction.SetControl -> form.copy(control = action.control)

            is NewGameAction.SetStrength -> form.copy(
                strength = AiStrength.coerce(action.strength),
                seatStrengths = emptyMap(),
            )

            is NewGameAction.SetSeatStrength ->
                form.copy(seatStrengths = form.seatStrengths + (action.heroId to AiStrength.coerce(action.strength)))

            is NewGameAction.SetAdvanced -> form.copy(advanced = action.open)

            is NewGameAction.SetLength -> form.copy(length = action.length)

            is NewGameAction.SetMap -> form.copy(mapId = action.mapId)

            is NewGameAction.SetCoach -> form.copy(coach = action.on)

            NewGameAction.FlipCoin -> form.copy(coin = flip())

            NewGameAction.CancelCoin -> form.copy(coin = null)
        }

    /** One hero each, or a whole team when it has more than one hero (p.20: two heroes per player). */
    fun controls(lineup: NewBoardForm): List<Control> {
        if (lineup.red.isEmpty() || lineup.red.size != lineup.blue.size) return emptyList()
        val teams = if (lineup.red.size > 1) Team.entries.map { Control.WholeTeam(it) } else emptyList()
        return teams + (lineup.red + lineup.blue).map { Control.Hero(it) }
    }

    fun controls(control: Control, heroId: String, team: Team): Boolean = when (control) {
        is Control.Hero -> control.heroId == heroId
        is Control.WholeTeam -> control.team == team
    }

    fun aiSeats(form: NewGameForm, control: Control?, heroes: Map<String, BoardHero>): List<AiSeatUi> {
        if (control == null) return emptyList()
        return Team.entries.flatMap { team ->
            form.lineup.team(team).filterNot { controls(control, it, team) }.map { id ->
                AiSeatUi(heroes.hero(id), team, form.seatStrengths[id] ?: form.strength)
            }
        }
    }

    /**
     * The computer seat a form asks for: its strength 1..10 (ADR-039), the seat's own under Advanced or
     * the one for all; this is the one place the form becomes a controller.
     */
    fun computer(form: NewGameForm, heroId: String): Controller.Ai =
        Controller.Ai(form.seatStrengths[heroId] ?: form.strength)

    /** The setup for a form that can start (seats in pick order per team), or null. */
    fun setup(form: NewGameForm, control: Control?, mapId: String?): GameSetup? {
        val coin = form.coin ?: return null
        if (control == null || mapId == null) return null
        val red = form.lineup.red
        val blue = form.lineup.blue
        if (red.isEmpty() || red.size != blue.size) return null
        fun seat(heroId: String, team: Team) = SeatSetup(
            heroId = heroId,
            team = team,
            controller = if (controls(control, heroId, team)) Controller.Human else computer(form, heroId),
        )
        return GameSetup(mapId, red.map { seat(it, Team.RED) } + blue.map { seat(it, Team.BLUE) }, form.length, coin)
    }
}
