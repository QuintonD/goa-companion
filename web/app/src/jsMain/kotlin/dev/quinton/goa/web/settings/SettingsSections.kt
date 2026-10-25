package dev.quinton.goa.web.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.domain.settings.ProductOption
import dev.quinton.goa.core.domain.settings.ProductOwnership
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.BodyText
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.Load
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.Segmented
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** "Step by step" / "Whole round", the phone's labels. */
private val TimerLayout.label: String
    get() = when (this) {
        TimerLayout.STEPS -> "Step by step"
        TimerLayout.ROUND -> "Whole round"
    }

/** The Vibration API: Android browsers have it, iOS Safari doesn't (no Haptics row there). */
private val canVibrate: Boolean = js(
    "typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function'",
) as Boolean

/** The sections stored in [AppSettings] (feature:settings parity), shown once the settings have loaded. */
@Composable
internal fun AppSettingsSections() {
    val services = LocalServices.current
    val repository = services.data.content.settings
    val settings by remember(repository) { repository.settings }.collectAsState(null)
    val catalog = rememberLoad(Unit) { services.data.content.catalog.catalog.first() }
    val scope = rememberCoroutineScope()
    val current = settings ?: return
    val edit = SettingsEdits(repository, scope)

    SectionHeader("Collection")
    CollectionCard(current, (catalog.state as? Load.Ready)?.value, edit, failed = catalog.state is Load.Failed)

    SectionHeader("At the table")
    GoaCard(flush = true) {
        Ul({ classes("goa-list") }) {
            if (canVibrate) {
                SwitchRow("vibrate", "Haptics", "Vibrate on timer cues and draft turns", current.hapticsEnabled) { on ->
                    edit { it.copy(hapticsEnabled = on) }
                }
            }
            SwitchRow("sound", "Sounds", "Play timer cues and phase alerts", current.soundsEnabled) { on ->
                edit { it.copy(soundsEnabled = on) }
            }
        }
    }
    GoaCard {
        H3({ classes("t-title-medium", "goa-card-title") }) { Text("Running timer") }
        BodyText(
            "Step by step shows one big dial per phase; Whole round lists every phase of the round on one " +
                "screen. Also switchable from the timer's top bar.",
        )
        Div({ classes("goa-settings-choice") }) {
            Segmented(
                label = "Running timer",
                options = TimerLayout.entries,
                selected = current.timerLayout,
                optionLabel = { it.label },
                onSelect = { layout -> edit { it.copy(timerLayout = layout) } },
            )
        }
    }

    SectionHeader("Learning")
    GoaCard(flush = true) {
        Ul({ classes("goa-list") }) {
            SwitchRow(
                "lightbulb",
                "Puzzle nudges",
                if (current.puzzleNudges) {
                    "A gentle nudge when you're stuck on a puzzle"
                } else {
                    "Only when you ask for one from the puzzle's menu"
                },
                current.puzzleNudges,
            ) { on -> edit { it.copy(puzzleNudges = on) } }
        }
    }

    SectionHeader("Game setup")
    GoaCard(flush = true) {
        RowList {
            ListRow("Timer presets", "Phase lengths, cues and auto-advance", "nav_timer", route = Route.Timer)
            ListRow("Draft methods", "House draft, official methods, your own", "nav_draft", route = Route.Draft)
            ListRow("Ratings", "Elo constants and double-pilot weighting", "chart", route = Route.Players)
        }
    }

    SectionHeader("Data")
    GoaCard(flush = true) {
        RowList {
            ListRow("Group sync", "Share matches and players with your friends' phones", "sync", route = Route.Group)
        }
    }
}

/** Applies a settings change in the background (IndexedDB), like the phone's ViewModel. */
private class SettingsEdits(private val repository: SettingsRepository, private val scope: CoroutineScope) {
    operator fun invoke(transform: (AppSettings) -> AppSettings) {
        scope.launch { repository.update(transform) }
    }
}

@Composable
private fun CollectionCard(settings: AppSettings, catalog: Catalog?, edit: SettingsEdits, failed: Boolean) {
    val everything = ProductOwnership.ownsEverything(settings)
    val products: List<ProductOption> = catalog?.let { ProductOwnership.options(settings, it) }.orEmpty()
    val all = products.map { it.id }.toSet()
    GoaCard(flush = true) {
        Ul({ classes("goa-list") }) {
            SwitchRow(
                "collection",
                "I own every product",
                if (everything) {
                    "All heroes are available in drafts and filters"
                } else {
                    "${products.count { it.owned }} of ${products.size} products owned"
                },
                everything,
                enabled = catalog != null,
            ) { on -> edit { ProductOwnership.setOwnsEverything(it, on, all) } }
            products.forEach { product ->
                ProductRow(product, enabled = !everything) { edit { ProductOwnership.toggle(it, product.id, all) } }
            }
        }
        if (failed) {
            Div({ classes("goa-settings-note") }) {
                BodyText("The product list didn't load. Open Settings again when online.", "t-body-small")
            }
        }
    }
}

/** A row that is one big switch (`<input type=checkbox role=switch>` inside its label). */
@Composable
private fun SwitchRow(
    icon: String,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Li {
        Label(attrs = {
            classes("goa-row", "goa-settings-toggle")
            if (!enabled) classes("disabled")
        }) {
            Span({ classes("goa-row-leading") }) { Icon(icon) }
            RowText(title, subtitle)
            Input(InputType.Checkbox) {
                classes("goa-settings-switch")
                attr("role", "switch")
                checked(checked)
                if (!enabled) disabled()
                onChange { event ->
                    // Shown as stored until the change is saved: a refused change (e.g. no product
                    // left) must not leave the native control in the other state.
                    event.target.checked = checked
                    onChange(event.value)
                }
            }
        }
    }
}

/** A product with its release year and hero count; dimmed while "I own every product" is on. */
@Composable
private fun ProductRow(product: ProductOption, enabled: Boolean, onToggle: () -> Unit) {
    val detail = listOfNotNull(
        product.released?.toString(),
        if (product.heroCount > 0) "${product.heroCount} heroes" else null,
    ).joinToString(" · ").ifEmpty { null }
    Li {
        Label(attrs = {
            classes("goa-row", "goa-settings-toggle", "goa-settings-product")
            if (!enabled) classes("disabled")
        }) {
            RowText(product.name, detail)
            Input(InputType.Checkbox) {
                classes("goa-settings-check")
                checked(product.owned)
                if (!enabled) disabled()
                onChange { event ->
                    // The last owned product can't be unticked: keep showing the stored state.
                    event.target.checked = product.owned
                    onToggle()
                }
            }
        }
    }
}

@Composable
private fun RowText(title: String, subtitle: String?) {
    Span({ classes("goa-row-text") }) {
        Span({ classes("goa-row-title", "t-title-medium") }) { Text(title) }
        subtitle?.let { Span({ classes("goa-row-subtitle", "t-body-medium") }) { Text(it) } }
    }
}
