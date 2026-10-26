package dev.quinton.goa.web.players

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.feature.players.ratings.RatingSetting
import dev.quinton.goa.feature.players.ratings.RatingSettingsText
import dev.quinton.goa.feature.players.ratings.RatingSettingsUiState
import dev.quinton.goa.feature.players.ratings.ratingSettingsState
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * How ratings are calculated (Settings → Ratings, ADR-011): every Elo constant with its
 * explanation (the shared RatingSettingsText); a change replays the whole match log. The constants
 * are the group's: they sync, and the later change wins.
 */
@Composable
fun RatingSettingsScreen() {
    val services = LocalServices.current
    val group = services.data.group
    val scope = rememberCoroutineScope()
    val state by remember {
        combine(group.eloConfig.config, group.ratingsSnapshot.observeSnapshot(), ::ratingSettingsState)
    }.collectAsState(RatingSettingsUiState())
    var confirmReset by remember { mutableStateOf(false) }
    Screen("Ratings") {
        if (state.loading) {
            LoadingState()
            return@Screen
        }
        GoaCard {
            Div({ classes("goa-w2-line") }) {
                Span({ classes("c-primary") }) { Icon("g_tune") }
                H2({ classes("t-title-large", "goa-w2-tight0") }) { Text(RatingSettingsText.INTRO_TITLE) }
            }
            P({ classes("t-body-medium") }) { Text(RatingSettingsText.INTRO) }
            P({ classes("t-body-small", "c-variant", "goa-w2-tight0") }) {
                Text(RatingSettingsText.replayNote(state.ratedMatches, state.skippedMatches))
            }
        }
        RatingSettingsText.sections.forEach { section ->
            SectionHeader(section.title)
            section.note?.let { P({ classes("t-body-small", "c-variant") }) { Text(it) } }
            section.settings.forEach { setting ->
                SliderSetting(setting, setting.get(state.config)) { v ->
                    scope.launch { group.eloConfig.update { setting.set(it, v) } }
                }
            }
        }
        Div({ classes("goa-w2-spaced") }) {
            GoaButton(
                RatingSettingsText.resetLabel(state.isDefault),
                { confirmReset = true },
                ButtonKind.OUTLINED,
                icon = "refresh",
                enabled = !state.isDefault,
                block = true,
            )
        }
    }
    if (confirmReset) {
        AlertDialog(RatingSettingsText.RESET_TITLE, onDismiss = { confirmReset = false }, buttons = {
            GoaButton("Cancel", { confirmReset = false }, ButtonKind.TEXT)
            GoaButton("Reset", {
                confirmReset = false
                scope.launch { group.eloConfig.reset() }
            }, ButtonKind.TEXT)
        }) { P { Text(RatingSettingsText.RESET_MESSAGE) } }
    }
}

/** A range slider (a real `<input type=range>`, so VoiceOver can adjust it) that saves when let go. */
@Composable
private fun SliderSetting(setting: RatingSetting, value: Double, onChange: (Double) -> Unit) {
    var current by remember(value) { mutableStateOf(value) }
    val id = "rating-" + setting.title.lowercase().replace(' ', '-')
    Div({ classes("goa-w2-slider") }) {
        Div({ classes("goa-w2-line") }) {
            Label(forId = id, attrs = { classes("t-title-medium", "goa-w2-grow") }) { Text(setting.title) }
            Span({ classes("t-title-medium", "c-primary") }) { Text(setting.format(current)) }
        }
        setting.explanation?.let { P({ classes("t-body-small", "c-variant", "goa-w2-tight0") }) { Text(it) } }
        Input(InputType.Range) {
            id(id)
            attr("min", setting.min.toString())
            attr("max", setting.max.toString())
            attr("step", setting.step.toString())
            value(current.toString())
            attr("aria-valuetext", setting.format(current))
            onInput { current = setting.snap(it.value?.toDouble() ?: current) }
            onChange { onChange(setting.snap(it.value?.toDouble() ?: current)) }
        }
    }
}
