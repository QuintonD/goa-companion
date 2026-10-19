package dev.quinton.goa.core.data.rating

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeEloConfigRepository(initial: EloConfig = EloConfig.DEFAULT, var clock: () -> Long = { 1 }) :
    EloConfigRepository,
    StampedEloConfigStore {
    val state = MutableStateFlow(initial)
    override val config: StateFlow<EloConfig> = state

    var updatedAt: Long = if (initial == EloConfig.DEFAULT) 0 else 1

    /** As the real repository: a change that leaves the constants as they are stores nothing. */
    override suspend fun update(transform: (EloConfig) -> EloConfig) {
        val next = transform(state.value).sanitized()
        if (next == state.value) return
        state.value = next
        updatedAt = maxOf(clock(), updatedAt + 1)
    }

    override suspend fun reset() = update { EloConfig.DEFAULT }

    override suspend fun stamped(): StampedEloConfig = StampedEloConfig(state.value, updatedAt)

    override suspend fun applySynced(config: StampedEloConfig) {
        state.value = config.config
        updatedAt = config.updatedAt
    }
}
