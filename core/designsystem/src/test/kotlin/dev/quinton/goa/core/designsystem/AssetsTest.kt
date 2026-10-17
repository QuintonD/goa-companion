package dev.quinton.goa.core.designsystem

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.component.formatDuration
import dev.quinton.goa.core.designsystem.component.spokenDuration
import dev.quinton.goa.core.designsystem.icon.HeroSigils
import dev.quinton.goa.core.designsystem.icon.PlayerCrests
import dev.quinton.goa.core.designsystem.theme.PlayerColors
import org.junit.Test

class AssetsTest {
    private val roster = listOf(
        "arien", "brogan", "tigerclaw", "wasp", "sabina", "xargatha", "dodger", "misa", "whisper", "ursafar",
        "silverarrow", "tali", "garrus", "bain", "cutter", "trinkets", "nebkher", "min", "swift", "wuk", "hanu",
        "ignatia", "rowenna", "mrak", "snorri", "razzle", "gydion", "brynn", "mortimer", "widget", "takahide", "emmitt",
    )

    @Test fun everyRosterHeroHasASigil() {
        assertThat(HeroSigils.knownIds).containsExactlyElementsIn(roster)
        roster.forEach { assertThat(HeroSigils.drawableFor(it)).isNotNull() }
    }

    @Test fun sigilLookupIsTolerantAndUnknownIsNull() {
        assertThat(HeroSigils.drawableFor("Neb-Kher")).isEqualTo(HeroSigils.drawableFor("nebkher"))
        assertThat(HeroSigils.drawableFor("Silver_Arrow")).isEqualTo(HeroSigils.drawableFor("silverarrow"))
        assertThat(HeroSigils.drawableFor("not-a-hero")).isNull()
    }

    @Test fun crests() {
        assertThat(PlayerCrests.count).isEqualTo(24)
        assertThat(PlayerCrests.names).hasSize(24)
        assertThat(PlayerCrests.all.toSet()).hasSize(24)
        assertThat(PlayerCrests.drawableFor(24)).isNull()
        assertThat(PlayerCrests.drawableFor(-1)).isNull()
    }

    @Test fun paletteIsDistinctAndFindable() {
        assertThat(PlayerColors.all.map { it.argb }.toSet()).hasSize(PlayerColors.all.size)
        PlayerColors.all.forEach { assertThat(PlayerColors.find(it.argb)).isEqualTo(it) }
        assertThat(PlayerColors.find(0xFF123456)).isNull()
        assertThat(PlayerColors.default(PlayerColors.all.size)).isEqualTo(PlayerColors.all.first())
    }

    @Test fun durations() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
        assertThat(formatDuration(90_000)).isEqualTo("1:30")
        assertThat(formatDuration(89_400, roundUp = true)).isEqualTo("1:30")
        assertThat(formatDuration(89_400)).isEqualTo("1:29")
        assertThat(formatDuration(3_725_000)).isEqualTo("1:02:05")
        assertThat(formatDuration(-12_000, overtimeSign = true)).isEqualTo("+0:12")
        assertThat(formatDuration(-12_000)).isEqualTo("−0:12")
        assertThat(formatDuration(-400)).isEqualTo("0:00")
        assertThat(spokenDuration(90_000)).isEqualTo("1 minute 30 seconds")
        assertThat(spokenDuration(0)).isEqualTo("0 seconds")
        assertThat(spokenDuration(3_600_000)).isEqualTo("1 hour")
    }
}
