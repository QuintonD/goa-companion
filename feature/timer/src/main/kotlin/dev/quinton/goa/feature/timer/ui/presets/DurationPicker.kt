package dev.quinton.goa.feature.timer.ui.presets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.timer.TimerPresetValidator
import dev.quinton.goa.core.presentation.timer.TimerText
import dev.quinton.goa.feature.timer.ui.components.goaChipBorder
import dev.quinton.goa.feature.timer.ui.components.goaChipColors
import dev.quinton.goa.feature.timer.ui.components.timerDigits
import dev.quinton.goa.feature.timer.ui.timer.Stepper

private val QuickDurations = listOf(15, 30, 45, 60, 90, 120, 180, 300)

/** Bottom sheet duration picker: big readout, minute and 5-second steppers, quick picks, untimed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPickerSheet(
    title: String,
    initialSeconds: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
    off: OffOption? = OffOption.UNTIMED,
    maxSeconds: Int = TimerPresetValidator.MAX_SECONDS,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var untimed by remember { mutableStateOf(initialSeconds == null && off != null) }
    var seconds by remember { mutableIntStateOf(initialSeconds ?: 30) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        DurationPickerContent(
            title = title,
            seconds = seconds,
            untimed = untimed,
            onSeconds = { seconds = it.coerceIn(TimerPresetValidator.MIN_SECONDS, maxSeconds) },
            onUntimed = { untimed = it },
            onDone = { onConfirm(if (untimed) null else seconds) },
            off = off,
            maxSeconds = maxSeconds,
        )
    }
}

/** The picker's "no duration" switch: untimed phase, or a table rule turned off. */
data class OffOption(val label: String, val hint: String) {
    companion object {
        val UNTIMED = OffOption("Untimed", "Counts up and never alerts.")
    }
}

@Composable
internal fun DurationPickerContent(
    title: String,
    seconds: Int,
    untimed: Boolean,
    onSeconds: (Int) -> Unit,
    onUntimed: (Boolean) -> Unit,
    onDone: () -> Unit,
    off: OffOption? = OffOption.UNTIMED,
    maxSeconds: Int = TimerPresetValidator.MAX_SECONDS,
) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.xl, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.lg))
        val label = if (untimed) "—:—" else TimerText.duration(seconds * 1000L, roundUp = false)
        Text(
            label,
            style = timerDigits(MaterialTheme.typography.displayLarge),
            color = if (untimed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                contentDescription = if (untimed) off?.label.orEmpty() else TimerText.spokenDuration(seconds.toLong())
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Minutes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Stepper(
                    value = seconds / 60,
                    range = 0..(maxSeconds / 60),
                    onChange = { m -> onSeconds(m * 60 + seconds % 60) },
                    label = "minutes",
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Seconds",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Stepper(
                    value = seconds % 60,
                    range = 0..55,
                    onChange = { s -> onSeconds(seconds / 60 * 60 + s.coerceIn(0, 55)) },
                    label = "seconds",
                    step = 5,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            QuickDurations.filter { it <= maxSeconds }.forEach { quick ->
                FilterChip(
                    selected = !untimed && quick == seconds,
                    onClick = {
                        onUntimed(false)
                        onSeconds(quick)
                    },
                    label = { Text(TimerText.durationLabel(quick)) },
                    colors = goaChipColors(),
                    border = goaChipBorder(!untimed && quick == seconds),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        if (off != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = untimed, onValueChange = onUntimed, role = Role.Switch),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(off.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        off.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = untimed, onCheckedChange = null)
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Done") }
        Spacer(Modifier.width(Spacing.md))
    }
}
