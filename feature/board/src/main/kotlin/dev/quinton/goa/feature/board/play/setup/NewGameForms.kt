package dev.quinton.goa.feature.board.play.setup

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.setup.NewBoardForm
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** [NewGameForm] in the SavedStateHandle, as JSON (the coin is never kept: it is flipped again). */
internal object NewGameForms {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Stored(
        val red: List<String> = emptyList(),
        val blue: List<String> = emptyList(),
        val filling: Team = Team.RED,
        val showAll: Boolean = false,
        val step: NewGameStep = NewGameStep.LINEUP,
        val controlHero: String? = null,
        val controlTeam: Team? = null,
        val strength: Int? = null,
        val seatStrengths: Map<String, Int> = emptyMap(),
        /** Before the slider (ADR-039): read as the level's strength. */
        val level: AiLevel? = null,
        val seatLevels: Map<String, AiLevel> = emptyMap(),
        val advanced: Boolean = false,
        val length: GameLength = GameLength.QUICK,
        val mapId: String? = null,
        val coach: Boolean = true,
    )

    fun encode(form: NewGameForm): String = json.encodeToString(
        Stored.serializer(),
        Stored(
            red = form.lineup.red,
            blue = form.lineup.blue,
            filling = form.lineup.filling,
            showAll = form.lineup.showAll,
            step = form.step,
            controlHero = (form.control as? Control.Hero)?.heroId,
            controlTeam = (form.control as? Control.WholeTeam)?.team,
            strength = form.strength,
            seatStrengths = form.seatStrengths,
            advanced = form.advanced,
            length = form.length,
            mapId = form.mapId,
            coach = form.coach,
        ),
    )

    fun decode(stored: String): NewGameForm? = try {
        val s = json.decodeFromString(Stored.serializer(), stored)
        NewGameForm(
            lineup = NewBoardForm(red = s.red, blue = s.blue, filling = s.filling, showAll = s.showAll),
            step = s.step,
            control = s.controlHero?.let { Control.Hero(it) } ?: s.controlTeam?.let { Control.WholeTeam(it) },
            strength = AiStrength.coerce(s.strength ?: s.level?.strength ?: AiStrength.DEFAULT),
            seatStrengths =
                s.seatLevels.mapValues {
                    it.value.strength
                } + s.seatStrengths.mapValues { AiStrength.coerce(it.value) },
            advanced = s.advanced,
            length = s.length,
            mapId = s.mapId,
            coach = s.coach,
        )
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
