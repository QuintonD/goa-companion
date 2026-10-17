package dev.quinton.goa.core.data.rating

import dev.quinton.goa.core.domain.rating.EloConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeEloConfigRepository(initial: EloConfig = EloConfig.DEFAULT) : EloConfigRepository {
    val state = MutableStateFlow(initial)
    override val config: StateFlow<EloConfig> = state

    override suspend fun update(transform: (EloConfig) -> EloConfig) = state.update { transform(it).sanitized() }

    override suspend fun reset() {
        state.value = EloConfig.DEFAULT
    }
}
