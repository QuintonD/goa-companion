package dev.quinton.goa.feature.players.ratings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.SectionHeader
import kotlin.math.roundToInt

data class RatingSettingsActions(
    val onBack: () -> Unit = {},
    val onUpdate: ((EloConfig) -> EloConfig) -> Unit = {},
    val onReset: () -> Unit = {},
)

/** Settings → Ratings: every Elo constant with a plain-language explanation (ADR-011). */
@Composable
fun RatingSettingsScreen(state: RatingSettingsUiState, actions: RatingSettingsActions, modifier: Modifier = Modifier) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    GoaScreen(title = "Ratings", modifier = modifier, onBack = actions.onBack) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@GoaScreen
        }
        val c = state.config
        val update = actions.onUpdate
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xxl),
        ) {
            IntroCard(state)

            // The constants, their words and sliders are shared with the web app (RatingSettingsText).
            RatingSettingsText.sections.forEach { section ->
                SectionHeader(section.title)
                section.note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                section.settings.forEach { setting ->
                    SliderSetting(
                        title = setting.title,
                        explanation = setting.explanation,
                        value = setting.get(c),
                        range = setting.min..setting.max,
                        step = setting.step,
                        format = setting.format,
                        onChange = { v -> update { setting.set(it, v) } },
                    )
                }
            }
            Spacer(Modifier.heightIn(min = Spacing.xl))
            OutlinedButton(
                onClick = { confirmReset = true },
                enabled = !state.isDefault,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = Spacing.lg),
            ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(RatingSettingsText.resetLabel(state.isDefault))
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(RatingSettingsText.RESET_TITLE) },
            text = { Text(RatingSettingsText.RESET_MESSAGE) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    actions.onReset()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IntroCard(state: RatingSettingsUiState) {
    EngravedCard(Modifier.fillMaxWidth().padding(top = Spacing.md)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.md))
                Text(RatingSettingsText.INTRO_TITLE, style = MaterialTheme.typography.titleLarge)
            }
            Text(RatingSettingsText.INTRO, style = MaterialTheme.typography.bodyMedium)
            Text(
                RatingSettingsText.replayNote(state.ratedMatches, state.skippedMatches),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    explanation: String?,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    format: (Double) -> String,
    onChange: (Double) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val steps = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                format(current.toDouble()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (explanation != null) {
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onChange(snap(current.toDouble(), range.start, step)) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.semantics { stateDescription = format(current.toDouble()) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun snap(value: Double, start: Double, step: Double): Double =
    start + ((value - start) / step).roundToInt() * step
