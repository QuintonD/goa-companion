package dev.quinton.goa.feature.board.setup

import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.size

/** A bundled map the board can use; [verified] false = badged Unverified. */
data class MapOption(val id: String, val name: String, val lanes: Int, val verified: Boolean)

/** The line-up being chosen. Kept in the SavedStateHandle, so it survives process death. */
data class NewBoardForm(
    val red: List<String> = emptyList(),
    val blue: List<String> = emptyList(),
    /** The team the next tapped hero joins. */
    val filling: Team = Team.RED,
    /** null = the default map for this many heroes. */
    val mapId: String? = null,
    /** Show every hero, not only the owned products' (Settings). */
    val showAll: Boolean = false,
) {
    fun team(team: Team): List<String> = if (team == Team.RED) red else blue

    val lineup: BoardLineup get() = BoardLineup(red, blue)

    fun contains(heroId: String): Boolean = heroId in red || heroId in blue
}

/**
 * What the line-up picker shows (both teams in pick order, the Red | Blue switch, the hero grid).
 * New board and New game share it; [unavailable] heroes are shown but can't be picked (New game:
 * not playable yet, "coming later").
 */
data class LineupPicker(
    val form: NewBoardForm,
    val heroes: List<BoardHero>,
    val heroById: Map<String, BoardHero>,
    val hiddenHeroes: Int,
    val ownedOnly: Boolean,
    val unavailable: Set<String> = emptySet(),
) {
    fun team(team: Team): List<BoardHero> = form.team(team).map(heroById::hero)

    val full: Boolean
        get() = form.red.size >= NewBoardUiState.MAX_PER_TEAM && form.blue.size >= NewBoardUiState.MAX_PER_TEAM
}

sealed interface NewBoardUiState {
    data object Loading : NewBoardUiState

    data class Ready(
        val form: NewBoardForm,
        /** The heroes to pick from, by name: the owned products' unless [NewBoardForm.showAll]. */
        val heroes: List<BoardHero>,
        val heroById: Map<String, BoardHero>,
        /** Heroes the owned-products filter hides right now (0 = none hidden, or showing all). */
        val hiddenHeroes: Int,
        /** The owned-products filter is on (Settings names some products). */
        val ownedOnly: Boolean,
        val maps: List<MapOption>,
        /** The map "Set up" uses (the chosen one, or the default for this many heroes). */
        val map: MapOption?,
        /** Finished drafts, pending and logged matches, newest first. */
        val sources: List<LineupSource>,
    ) : NewBoardUiState {
        fun team(team: Team): List<BoardHero> = form.team(team).map(heroById::hero)

        val picker: LineupPicker get() = LineupPicker(form, heroes, heroById, hiddenHeroes, ownedOnly)

        val canSetUp: Boolean get() = map != null && form.lineup.size > 0

        val full: Boolean get() = form.red.size >= MAX_PER_TEAM && form.blue.size >= MAX_PER_TEAM

        /** The newest draft or match: "Set up" on it is one tap. */
        val latest: LineupSource? get() = sources.firstOrNull()
    }

    companion object {
        /** Five heroes a side at most (p.4). */
        const val MAX_PER_TEAM = 5
    }
}

sealed interface NewBoardAction {
    /** Adds the hero to the team being filled (or takes it off the board when it's already on). */
    data class Tap(val heroId: String) : NewBoardAction

    data class Fill(val team: Team) : NewBoardAction

    data class Remove(val heroId: String) : NewBoardAction

    /** Moves a hero one place earlier ([by] = -1) or later (+1) in its team's pick order. */
    data class Reorder(val heroId: String, val by: Int) : NewBoardAction

    data class SwitchTeam(val heroId: String) : NewBoardAction

    data class Use(val source: LineupSource) : NewBoardAction

    data class ChooseMap(val mapId: String) : NewBoardAction

    data class ShowAll(val all: Boolean) : NewBoardAction

    data object Clear : NewBoardAction
}

/**
 * New board's (and New game's) line-up rules: picks alternate like a draft: after each one the next
 * goes to the team with fewer heroes (a tie goes to the other team), so "Brogan, Tigerclaw, Arien,
 * Wasp" is Brogan and Arien against Tigerclaw and Wasp; tapping Red or Blue picks for that team next.
 */
object NewBoardRules {
    /**
     * Who picks next after [last] picked: the team with fewer heroes; on a tie, the other team
     * (alternating like a draft); never a full team.
     */
    fun nextTeam(red: Int, blue: Int, last: Team): Team {
        val next = when {
            red < blue -> Team.RED
            blue < red -> Team.BLUE
            else -> last.opponent
        }
        val size = if (next == Team.RED) red else blue
        return if (size >= NewBoardUiState.MAX_PER_TEAM) next.opponent else next
    }

    fun reduce(form: NewBoardForm, action: NewBoardAction): NewBoardForm = when (action) {
        is NewBoardAction.Tap -> when {
            form.contains(action.heroId) -> form.without(action.heroId)

            form.team(form.filling).size < NewBoardUiState.MAX_PER_TEAM -> form.pick(form.filling, action.heroId)

            form.team(form.filling.opponent).size < NewBoardUiState.MAX_PER_TEAM ->
                form.pick(form.filling.opponent, action.heroId)

            else -> form
        }

        is NewBoardAction.Fill -> form.copy(filling = action.team)

        is NewBoardAction.Remove -> form.without(action.heroId)

        is NewBoardAction.Reorder -> form.reorder(action.heroId, action.by)

        is NewBoardAction.SwitchTeam -> form.switchTeam(action.heroId)

        is NewBoardAction.Use -> {
            val red = action.source.lineup.red.take(NewBoardUiState.MAX_PER_TEAM)
            val blue = action.source.lineup.blue.filterNot { it in red }.take(NewBoardUiState.MAX_PER_TEAM)
            form.copy(red = red, blue = blue, filling = nextTeam(red.size, blue.size, Team.BLUE))
        }

        is NewBoardAction.ChooseMap -> form.copy(mapId = action.mapId)

        is NewBoardAction.ShowAll -> form.copy(showAll = action.all)

        NewBoardAction.Clear -> form.copy(red = emptyList(), blue = emptyList(), filling = Team.RED)
    }

    /** Adds [heroId] to [team], then hands the next pick on ([nextTeam]). */
    private fun NewBoardForm.pick(team: Team, heroId: String): NewBoardForm {
        val added = with(team, heroId)
        return added.copy(filling = nextTeam(added.red.size, added.blue.size, team))
    }

    private fun NewBoardForm.reorder(heroId: String, by: Int): NewBoardForm {
        val team = if (heroId in red) Team.RED else Team.BLUE
        val list = team(team).toMutableList()
        val from = list.indexOf(heroId)
        val to = from + by
        if (from < 0 || to !in list.indices) return this
        list.add(to, list.removeAt(from))
        return withTeam(team, list)
    }

    private fun NewBoardForm.switchTeam(heroId: String): NewBoardForm {
        val team = when (heroId) {
            in red -> Team.RED
            in blue -> Team.BLUE
            else -> return this
        }
        if (team(team.opponent).size >= NewBoardUiState.MAX_PER_TEAM) return this
        return without(heroId).with(team.opponent, heroId)
    }

    private fun NewBoardForm.with(team: Team, heroId: String) = withTeam(team, team(team) + heroId)

    private fun NewBoardForm.withTeam(team: Team, heroes: List<String>) =
        if (team == Team.RED) copy(red = heroes) else copy(blue = heroes)

    private fun NewBoardForm.without(heroId: String) = copy(red = red - heroId, blue = blue - heroId)
}
