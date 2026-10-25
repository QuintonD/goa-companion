package dev.quinton.goa.web.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.timer.TimerPresetValidator
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.web.table.Stepper
import dev.quinton.goa.web.table.SwitchRow
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.GoaButton
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** The picker's "no duration" switch: an untimed phase, or a table rule turned off. */
data class OffOption(val label: String, val hint: String) {
    companion object {
        val UNTIMED = OffOption("Untimed", "Counts up and never alerts.")
    }
}

private val QuickDurations = listOf(15, 30, 45, 60, 90, 120, 180, 300)

/** Bottom-sheet duration picker: big readout, minute and 5-second steppers, quick picks, untimed. */
@Composable
fun DurationPickerSheet(
    title: String,
    initialSeconds: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
    off: OffOption? = OffOption.UNTIMED,
    maxSeconds: Int = TimerPresetValidator.MAX_SECONDS,
) {
    var untimed by remember { mutableStateOf(initialSeconds == null && off != null) }
    var seconds by remember { mutableStateOf(initialSeconds ?: DEFAULT_SECONDS) }
    fun set(value: Int) {
        seconds = value.coerceIn(TimerPresetValidator.MIN_SECONDS, maxSeconds)
    }
    BottomSheet(title, onDismiss) {
        Div({ classes("goa-table-picker") }) {
            P({
                classes("goa-table-picker-readout", "t-numeric")
                if (untimed) classes("c-variant")
                attr("aria-live", "polite")
                attr("aria-label", if (untimed) off?.label.orEmpty() else TimerText.spokenDuration(seconds.toLong()))
            }) { Text(if (untimed) "—:—" else TimerText.duration(seconds * MILLIS, roundUp = false)) }
            Div({ classes("goa-table-picker-steppers") }) {
                Div({ classes("goa-table-picker-column") }) {
                    Span({ classes("t-label-medium", "c-variant") }) { Text("Minutes") }
                    Stepper(seconds / MINUTE, 0..(maxSeconds / MINUTE), { m ->
                        set(m * MINUTE + seconds % MINUTE)
                    }, "minutes")
                }
                Div({ classes("goa-table-picker-column") }) {
                    Span({ classes("t-label-medium", "c-variant") }) { Text("Seconds") }
                    Stepper(
                        seconds % MINUTE,
                        0..MAX_SECOND_STEP,
                        { s -> set(seconds / MINUTE * MINUTE + s.coerceIn(0, MAX_SECOND_STEP)) },
                        "seconds",
                        step = SECOND_STEP,
                    )
                }
            }
            Div({ classes("goa-chip-row") }) {
                QuickDurations.filter { it <= maxSeconds }.forEach { quick ->
                    Chip(TimerText.durationLabel(quick), !untimed && quick == seconds, onClick = {
                        untimed = false
                        set(quick)
                    })
                }
            }
            if (off != null) SwitchRow(off.label, off.hint, untimed) { untimed = it }
            GoaButton("Done", { onConfirm(if (untimed) null else seconds) }, block = true)
        }
    }
}

private const val DEFAULT_SECONDS = 30
private const val MINUTE = 60
private const val MAX_SECOND_STEP = 55
private const val SECOND_STEP = 5
private const val MILLIS = 1_000L
