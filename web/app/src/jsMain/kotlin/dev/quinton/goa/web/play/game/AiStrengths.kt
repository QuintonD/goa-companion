package dev.quinton.goa.web.play.game

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.web.ui.Icon
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.max
import org.jetbrains.compose.web.attributes.min
import org.jetbrains.compose.web.attributes.step
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/*
 * How strong the computer plays, the one place the web app names it (PRODUCT §9, ADR-039): a
 * strength 1..10 with the phone's words (PlayText.strengthValue / strengthSpoken / strengthNote /
 * strength). New game's form (NewGameRules.computer in core:play) makes it a Controller.Ai(strength);
 * the worker seats it with the phone's rule (EngineHost.aiFor → AiPlayers.forApp).
 */
object AiStrengths {
    /** A computer seat's band and strength in words ("Hard 8"). */
    fun label(computer: Controller.Ai): String = PlayText.strength(computer)
}

/**
 * The computer's strength, 1 to 10, as on the phone: the value and its band ("6 · Normal") above a
 * slider with a tick per step, "Gentler" and "Stronger" under its ends ([ends], once under the main
 * one). − and + step it by one: a slider's thumb is small on an iPhone, and VoiceOver users get a
 * plain button too. [label] names it ("Computer strength", "Wasp's strength"); VoiceOver reads the
 * value as "Strength 6 of 10, Normal". [title] goes before the value (a seat's hero).
 */
@Composable
fun AiStrengthSlider(
    value: Int,
    label: String,
    title: (@Composable () -> Unit)? = null,
    ends: Boolean = title == null,
    onChange: (Int) -> Unit,
) {
    val strength = AiStrength.coerce(value)
    val fill = (strength - AiStrength.MIN) * PERCENT / (AiStrength.MAX - AiStrength.MIN)
    Div({ classes("goa-strength") }) {
        Div({ classes("goa-strength-head") }) {
            if (title != null) Div({ classes("goa-strength-title") }) { title() }
            Span({
                classes("t-title-medium", "goa-strength-value")
                attr("aria-hidden", "true")
            }) { Text(PlayText.strengthValue(strength)) }
        }
        Div({ classes("goa-strength-row") }) {
            StepButton("remove", "$label: gentler", enabled = strength > AiStrength.MIN) { onChange(strength - 1) }
            Input(InputType.Range) {
                classes("goa-range")
                min(AiStrength.MIN.toString())
                max(AiStrength.MAX.toString())
                step(1)
                value(strength)
                attr("aria-label", label)
                attr("aria-valuetext", PlayText.strengthSpoken(strength))
                // The filled part of the track, up to the thumb.
                style { property("--fill", "$fill%") }
                onInput { event ->
                    val picked = event.value?.toInt()?.let(AiStrength::coerce)
                    if (picked != null && picked != strength) onChange(picked)
                }
            }
            StepButton("add", "$label: stronger", enabled = strength < AiStrength.MAX) { onChange(strength + 1) }
        }
        if (ends) {
            Div({
                classes("goa-strength-ends", "t-label-small", "goa-quiet")
                attr("aria-hidden", "true")
            }) {
                Span { Text("Gentler") }
                Span { Text("Stronger") }
            }
        }
    }
}

@Composable
private fun StepButton(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Button({
        classes("goa-icon-button", "goa-strength-step")
        attr("type", "button")
        attr("aria-label", label)
        if (!enabled) disabled()
        onClick { onClick() }
    }) { Icon(icon) }
}

private const val PERCENT = 100
