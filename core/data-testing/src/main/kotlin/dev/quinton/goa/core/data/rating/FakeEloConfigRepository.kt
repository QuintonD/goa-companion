package dev.quinton.goa.core.data.rating

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeEloConfigRepository(initial: EloConfig = EloConfig.DEFAULT, var clock: () -> Long = { 1 }) :
    EloConfigRepository,
    StampedEloConfigStore {
    val state = MutableStateFlow(initial)
    override val config: StateFlow<EloConfig> = state

    var updatedAt: Long = if (initial == EloConfig.DEFAULT) 0 else 1

    override suspend fun update(transform: (EloConfig) -> EloConfig) {
        state.update { transform(it).sanitized() }
        updatedAt = maxOf(clock(), updatedAt + 1)
    }

    override suspend fun reset() {
        state.value = EloConfig.DEFAULT
        updatedAt = maxOf(clock(), updatedAt + 1)
    }

    override suspend fun stamped(): StampedEloConfig = StampedEloConfig(state.value, updatedAt)

    override suspend fun applySynced(config: StampedEloConfig) {
        state.value = config.config
        updatedAt = config.updatedAt
    }
}
