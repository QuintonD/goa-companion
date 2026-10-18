package dev.quinton.goa.feature.timer

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.navigation.LevelLadderRoute
import dev.quinton.goa.core.navigation.MatchEditRoute
import dev.quinton.goa.core.navigation.MatchSetupRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.RulesRoute
import dev.quinton.goa.core.navigation.SettingsRoute
import dev.quinton.goa.core.navigation.TimerPresetEditRoute
import dev.quinton.goa.core.navigation.TimerPresetsRoute
import dev.quinton.goa.core.navigation.TimerRoute
import dev.quinton.goa.core.presentation.timer.RematchRequest
import dev.quinton.goa.feature.timer.ui.presets.PresetEditRoute
import dev.quinton.goa.feature.timer.ui.presets.PresetEditViewModel
import dev.quinton.goa.feature.timer.ui.presets.PresetsRoute
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupRoute
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupViewModel
import dev.quinton.goa.feature.timer.ui.timer.TimerRoute

private fun rematchRequest(key: MatchSetupRoute): RematchRequest? {
    val source = key.rematchOf ?: return null
    val kind = RematchKind.entries.firstOrNull { it.name == key.rematchKind } ?: return null
    return RematchRequest(source, kind)
}

fun EntryProviderScope<NavKey>.timerEntries(navigator: Navigator) {
    entry<TimerRoute> {
        TimerRoute(
            viewModel = hiltViewModel(),
            onOpenSetup = { matchId -> navigator.navigate(MatchSetupRoute(matchId)) },
            onManagePresets = { navigator.navigate(TimerPresetsRoute) },
            onSettings = { navigator.navigate(SettingsRoute) },
            // "Who won?": the result first, dated from the session start.
            onOpenMatch = { matchId, startedAt ->
                navigator.navigate(MatchEditRoute(matchId, result = true, prefillPlayedAt = startedAt))
            },
            onNewMatch = { event ->
                navigator.navigate(
                    MatchEditRoute(
                        matchId = null,
                        prefillDurationMin = event.durationMin,
                        prefillMode = event.mode,
                        result = true,
                        prefillPlayedAt = event.startedAt,
                        newMatchId = event.matchId,
                    ),
                )
            },
            onOpenLevels = { heroIds -> navigator.navigate(LevelLadderRoute(heroIds)) },
            onOpenRules = { navigator.navigate(RulesRoute) },
        )
    }
    entry<MatchSetupRoute> { key ->
        MatchSetupRoute(
            viewModel = hiltViewModel<MatchSetupViewModel, MatchSetupViewModel.Factory>(
                creationCallback = { factory -> factory.create(key.matchId, rematchRequest(key)) },
            ),
            onBack = navigator::back,
            // Leave setup and show the running timer: the Timer tab's stack becomes just the timer,
            // so no stale presets or earlier Match setup sits under it (one more tap would start it).
            onTimerStarted = navigator::openTimer,
        )
    }
    entry<TimerPresetsRoute> {
        PresetsRoute(
            viewModel = hiltViewModel(),
            onBack = navigator::back,
            onEdit = { id -> navigator.navigate(TimerPresetEditRoute(id)) },
        )
    }
    entry<TimerPresetEditRoute> { key ->
        PresetEditRoute(
            viewModel = hiltViewModel<PresetEditViewModel, PresetEditViewModel.Factory>(
                creationCallback = { factory -> factory.create(key.presetId) },
            ),
            onBack = navigator::back,
        )
    }
}
