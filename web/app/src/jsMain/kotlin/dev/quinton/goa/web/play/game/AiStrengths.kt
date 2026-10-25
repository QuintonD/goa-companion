package dev.quinton.goa.web.play.game

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.feature.board.play.PlayText
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/*
 * How strong the computer plays, the one place the web app names it (PRODUCT §9). Today the four
 * levels (AiLevel); when the strength slider lands (ADR-039, feature/v07-ai), the value becomes a
 * strength 1..10: change [AiStrengthPicker] to a range input over AiStrength.MIN..MAX with
 * PlayText.strengthValue / strengthSpoken / strengthNote, [AiStrengths.label] to PlayText.strength,
 * and New game's form (NewGameRules.computer in core:play) maps it to Controller.Ai(strength). The
 * worker's single mapping from a seat to a player is EngineHost.aiFor.
 */
object AiStrengths {
    /** The choices offered, weakest first. */
    val choices: List<AiLevel> = AiLevel.entries

    fun label(level: AiLevel): String = PlayText.level(level)

    /** A computer seat's strength in words ("Hard"). */
    fun label(computer: Controller.Ai): String = PlayText.level(computer.level)

    fun note(level: AiLevel): String = PlayText.levelNote(level)
}

/**
 * Picks the computer's strength: a segmented row of the levels with the chosen one's note under it.
 * [label] names the group for VoiceOver ("Computer level", "Wasp's level").
 */
@Composable
fun AiStrengthPicker(value: AiLevel, label: String, onChange: (AiLevel) -> Unit, showNote: Boolean = true) {
    Div({
        classes("goa-segmented")
        attr("role", "radiogroup")
        attr("aria-label", label)
    }) {
        AiStrengths.choices.forEach { level ->
            Button({
                classes("goa-segment")
                if (level == value) classes("selected")
                attr("type", "button")
                attr("role", "radio")
                attr("aria-checked", (level == value).toString())
                onClick { onChange(level) }
            }) { Span { Text(AiStrengths.label(level)) } }
        }
    }
    if (showNote) P({ classes("t-body-small", "goa-quiet") }) { Text(AiStrengths.note(value)) }
}
