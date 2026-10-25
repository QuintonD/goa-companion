package dev.quinton.goa.web.data.stub

import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.web.data.RulesReferenceSource
import kotlinx.coroutines.flow.Flow

// W1 content (feature/web-content) replaces these in ContentData and deletes this file.

private const val W1 = "W1"

object W1SettingsStub : SettingsRepository {
    override val settings: Flow<AppSettings> get() = notYet(W1, "SettingsRepository")

    override suspend fun update(transform: (AppSettings) -> AppSettings) = notYet(W1, "SettingsRepository")
}

object W1TriviaProgressStub : TriviaProgressRepository {
    override val progress: Flow<Map<String, FactProgress>> get() = notYet(W1, "TriviaProgressRepository")

    override val lastConfig: Flow<TriviaConfig?> get() = notYet(W1, "TriviaProgressRepository")

    override suspend fun record(updates: Map<String, FactProgress>) = notYet(W1, "TriviaProgressRepository")

    override suspend fun saveConfig(config: TriviaConfig) = notYet(W1, "TriviaProgressRepository")

    override suspend fun reset(heroIds: Set<String>?) = notYet(W1, "TriviaProgressRepository")

    override val playingAs: Flow<String?> get() = notYet(W1, "TriviaProgressRepository")

    override suspend fun setPlayingAs(playerIdOrGuest: String) = notYet(W1, "TriviaProgressRepository")
}

object W1RulesReferenceStub : RulesReferenceSource {
    override suspend fun load(): RulesReference = notYet(W1, "RulesReferenceSource")
}
