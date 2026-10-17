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
import java.util.Locale
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

            SectionHeader("Pilot and hero")
            SliderSetting(
                title = "Hero influence",
                explanation =
                    "How much a hero's rating shifts its pilot's strength in a match. Winning with a strong " +
                        "hero earns the player a little less.",
                value = c.heroInfluence,
                range = 0.0..1.0,
                step = 0.05,
                format = ::decimal2,
                onChange = { v -> update { it.copy(heroInfluence = v) } },
            )
            SliderSetting(
                title = "Pilot influence",
                explanation =
                    "How much the pilot's rating shifts a hero's strength. A great player winning with a hero " +
                        "earns the hero a little less.",
                value = c.pilotInfluence,
                range = 0.0..1.0,
                step = 0.05,
                format = ::decimal2,
                onChange = { v -> update { it.copy(pilotInfluence = v) } },
            )

            SectionHeader("Uneven teams")
            SliderSetting(
                title = "Seat advantage",
                explanation = "Rating points per extra hero a team fields (e.g. 3 heroes against 2). 0 trusts the " +
                    "Handicap cards to balance it. Check the larger-team win rate on the Meta tab before changing it.",
                value = c.seatAdvantage,
                range = -100.0..100.0,
                step = 5.0,
                format = { "${signed(it.roundToInt())} pts" },
                onChange = { v -> update { it.copy(seatAdvantage = v) } },
            )
            SliderSetting(
                title = "Double pilot weight",
                explanation =
                    "A player controlling two heroes carried more of the result. Their change is multiplied " +
                        "by 1 + weight for each extra hero (0.5 → ×1.5).",
                value = c.doublePilotWeight,
                range = 0.0..2.0,
                step = 0.1,
                format = { "×${decimal2(1 + it)}" },
                onChange = { v -> update { it.copy(doublePilotWeight = v) } },
            )

            SectionHeader("Speed of change")
            SliderSetting(
                title = "K while provisional",
                explanation = "Maximum points per match while a player or hero is still provisional, so new ratings " +
                    "settle quickly.",
                value = c.kProvisional,
                range = 0.0..100.0,
                step = 5.0,
                format = { it.roundToInt().toString() },
                onChange = { v -> update { it.copy(kProvisional = v) } },
            )
            SliderSetting(
                title = "K when established",
                explanation = "Maximum points per match afterwards.",
                value = c.kEstablished,
                range = 0.0..100.0,
                step = 5.0,
                format = { it.roundToInt().toString() },
                onChange = { v -> update { it.copy(kEstablished = v) } },
            )
            SliderSetting(
                title = "Provisional games",
                explanation = "Rated games before a rating stops being provisional.",
                value = c.provisionalGames.toDouble(),
                range = 0.0..30.0,
                step = 1.0,
                format = { "${it.roundToInt()} games" },
                onChange = { v -> update { it.copy(provisionalGames = v.roundToInt()) } },
            )

            SectionHeader("Game modes")
            Text(
                "Scale every rating change by game length. 1.0 treats all modes alike.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SliderSetting(
                title = "Quick",
                explanation = null,
                value = c.quickMultiplier,
                range = 0.0..3.0,
                step = 0.1,
                format = { "×${decimal2(it)}" },
                onChange = { v -> update { it.copy(quickMultiplier = v) } },
            )
            SliderSetting(
                title = "Long",
                explanation = null,
                value = c.longMultiplier,
                range = 0.0..3.0,
                step = 0.1,
                format = { "×${decimal2(it)}" },
                onChange = { v -> update { it.copy(longMultiplier = v) } },
            )
            SliderSetting(
                title = "Double lane",
                explanation = null,
                value = c.doubleLaneMultiplier,
                range = 0.0..3.0,
                step = 0.1,
                format = { "×${decimal2(it)}" },
                onChange = { v -> update { it.copy(doubleLaneMultiplier = v) } },
            )

            SectionHeader("Starting point")
            SliderSetting(
                title = "Starting rating",
                explanation = "Where every player and hero begins. Only shifts the scale; rankings stay the same.",
                value = c.initialRating,
                range = 1000.0..2000.0,
                step = 50.0,
                format = { it.roundToInt().toString() },
                onChange = { v -> update { it.copy(initialRating = v) } },
            )

            Spacer(Modifier.heightIn(min = Spacing.xl))
            OutlinedButton(
                onClick = { confirmReset = true },
                enabled = !state.isDefault,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = Spacing.lg),
            ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(if (state.isDefault) "Using default settings" else "Reset to defaults")
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset rating settings?") },
            text = {
                Text("All constants go back to their defaults and every rating is recalculated from the match log.")
            },
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
                Text("How ratings work", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Every hero seat counts. A team's strength is the average of its seats, each blending " +
                    "its pilot's and " +
                    "hero's ratings, so double pilots and uneven teams are rated fairly. " +
                    "Players and heroes each get their own Elo.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Ratings are never stored: changing a value replays the whole match log " +
                    "(${state.ratedMatches} rated ${if (state.ratedMatches == 1) "match" else "matches"}" +
                    (if (state.skippedMatches > 0) ", ${state.skippedMatches} pending or without a result" else "") +
                    "). Nothing is lost, and you can always reset.",
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

private fun decimal2(value: Double): String = String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.').let {
    if ('.' !in it) "$it.0" else it
}

private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}
