package dev.quinton.goa.feature.draft.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.DraftSetupPrefill
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.feature.draft.setup.SetupForm
import dev.quinton.goa.feature.draft.setup.TeamMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first

/** Forms pushed from outside (a rematch's "Draft again") to a draft setup that is already open. */
@Singleton
class DraftSetupPrefills @Inject constructor() {
    private val flow = MutableSharedFlow<SetupForm>(extraBufferCapacity = 1)
    val requests: SharedFlow<SetupForm> = flow

    internal fun push(form: SetupForm) {
        flow.tryEmit(form)
    }
}

/**
 * "Draft again" with the same group: the saved setup form (ADR-017) gets these players and teams,
 * keeping the method, pool and timer choices, so the draft tab opens ready at the Players step.
 */
internal class DefaultDraftSetupPrefill @Inject constructor(
    private val drafts: DraftRepository,
    private val prefills: DraftSetupPrefills,
) : DraftSetupPrefill {
    override suspend fun prefill(red: List<String>, blue: List<String>, doublePilot: String?, handicap: Boolean) {
        val form = (drafts.setupForm.first() ?: SetupForm()).cleared().copy(
            selected = red + blue,
            teamMode = TeamMode.MANUAL,
            red = red,
            blue = blue,
            oddMode = if (handicap) OddSeatMode.HANDICAP else OddSeatMode.DOUBLE_PILOT,
            doublePilot = doublePilot,
            lastGame = true,
        )
        drafts.saveSetupForm(form)
        prefills.push(form)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DraftPrefillModule {
    @Binds fun prefill(impl: DefaultDraftSetupPrefill): DraftSetupPrefill
}
