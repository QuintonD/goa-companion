package dev.quinton.goa.feature.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.rules.LevelRow
import dev.quinton.goa.feature.rules.ui.spokenDefeat
import dev.quinton.goa.feature.rules.ui.spokenIconLabel
import dev.quinton.goa.feature.rules.ui.spokenRow
import org.junit.Test

/** What TalkBack reads on the level ladder: counts agree with their nouns (QA: "1 coins"). */
class LadderSpeechTest {
    private val level1 = LevelRow(1, lowestTier = 1, defeatCoins = 1, assistCoins = 1, lifeCounters = 1, null, null)
    private val level2 = LevelRow(2, lowestTier = 1, defeatCoins = 2, assistCoins = 1, lifeCounters = 1, 2, 2)

    @Test fun `singular counts are read in the singular`() {
        assertThat(spokenRow(level1, next = 2)).contains("Defeat: 1 coin, 1 assist coin, team spends 1 Life counter.")
        assertThat(spokenDefeat(level1)).contains("spends 1 Life counter.")
        assertThat(spokenDefeat(level1)).contains("collects 1 coin.")
        assertThat(spokenDefeat(level1)).contains("collect 1 assist coin.")
    }

    @Test fun `plural counts are read in the plural`() {
        assertThat(spokenRow(level2, next = 3)).contains("Defeat: 2 coins, 1 assist coin")
        assertThat(spokenRow(level2, next = 3)).contains("Next level costs 3 coins.")
    }

    @Test fun `an icon followed by its own name is read once`() {
        val segments = CardText.parse("spends 1 additional {life} Life counter.")
        val icon = segments.indexOfFirst { it is CardText.Segment.Icon }
        assertThat(spokenIconLabel("Life counter", segments.getOrNull(icon + 1))).isEqualTo(" ")
        assertThat(spokenIconLabel("Coin", CardText.Segment.Text(" to the bank"))).isEqualTo("Coin")
    }
}
