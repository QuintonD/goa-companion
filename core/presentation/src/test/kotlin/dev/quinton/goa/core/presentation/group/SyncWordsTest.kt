package dev.quinton.goa.core.presentation.group

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.SyncChanges
import org.junit.Test

/** Moved from feature/sync's `SyncTextTest` with the words (ADR-030 §1). */
class SyncWordsTest {
    @Test
    fun theResultSummaryReadsLikeTheTableWouldSayIt() {
        assertThat(SyncWords.summary(SyncChanges.NONE)).isEqualTo("Already up to date")
        assertThat(
            SyncWords.summary(SyncChanges(matchesAdded = 5, playersAdded = 1, matchesUpdated = 1, playersUpdated = 1)),
        ).isEqualTo("+5 matches, +1 player, 2 updated")
        assertThat(SyncWords.summary(SyncChanges(matchesAdded = 1, matchesRemoved = 1, playersMerged = 1)))
            .isEqualTo("+1 match, 1 removed, 1 duplicate merged")
        assertThat(SyncWords.summary(SyncChanges(ratingConstantsChanged = true))).isEqualTo("new rating constants")
    }

    @Test
    fun lastSyncedIsShortAndHuman() {
        val now = 10_000_000_000L
        assertThat(SyncWords.ago(now - 10_000, now)).isEqualTo("just now")
        assertThat(SyncWords.ago(now - 5 * 60_000, now)).isEqualTo("5 min ago")
        assertThat(SyncWords.ago(now - 3 * 3_600_000, now)).isEqualTo("3 h ago")
        assertThat(SyncWords.ago(now - 2 * 86_400_000, now)).isEqualTo("2 days ago")
    }
}
