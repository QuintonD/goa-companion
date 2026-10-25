package dev.quinton.goa.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.component.CardColorDot
import dev.quinton.goa.core.designsystem.component.CardScan
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.DurationText
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.HeroSigil
import dev.quinton.goa.core.designsystem.component.HeroSymbol
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.PrintedCardText
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.StatBar
import dev.quinton.goa.core.designsystem.component.SteelBanner
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.VerifiedBadge
import dev.quinton.goa.core.designsystem.icon.HeroSigils
import dev.quinton.goa.core.designsystem.icon.PlayerCrests
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.PlayerColors
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.designsystem.theme.playerColor
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.BundledAssetImages
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Component gallery: dark + light, plus font scale 2.0 for the text-heavy components. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GalleryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledAssetImages.install(ApplicationProvider.getApplicationContext())

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GoaTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().padding(Spacing.screen)) { content() }
                    }
                }
            }
        }
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledAssetImages.idle }
        compose.onRoot().captureRoboImage()
    }

    @Test fun officialArtDark() = capture(ThemeMode.DARK) { OfficialArtGallery() }

    @Test fun officialArtLight() = capture(ThemeMode.LIGHT) { OfficialArtGallery() }

    @Test fun officialArtLightFontScale2() = capture(ThemeMode.LIGHT, fontScale = 2f) { PrintedGallery() }

    @Test fun sigilsDark() = capture(ThemeMode.DARK) { Sigils() }

    @Test fun sigilsLight() = capture(ThemeMode.LIGHT) { Sigils() }

    @Test fun crestsDark() = capture(ThemeMode.DARK) { Crests() }

    @Test fun crestsLight() = capture(ThemeMode.LIGHT) { Crests() }

    @Test fun componentsDark() = capture(ThemeMode.DARK) { Components() }

    @Test fun componentsLight() = capture(ThemeMode.LIGHT) { Components() }

    @Test fun componentsDarkFontScale2() = capture(ThemeMode.DARK, fontScale = 2f) { Components() }

    @Test fun componentsLightFontScale2() = capture(ThemeMode.LIGHT, fontScale = 2f) { Components() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sigils() {
    SectionHeader("Hero sigils")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (HeroSigils.knownIds.sorted() + "unknown-hero").forEach { id ->
            Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HeroSigil(id, Modifier.size(44.dp))
                Text(
                    id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
    SectionHeader("Small (24 dp) and portrait fallback")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        listOf("arien", "ignatia", "gydion", "trinkets", "zzz").forEach { HeroSigil(it, Modifier.size(24.dp)) }
        HeroPortrait("tali", portrait = null, contentDescription = null, modifier = Modifier.size(96.dp, 128.dp))
        HeroPortrait("someone-new", portrait = null, contentDescription = null, modifier = Modifier.size(96.dp, 128.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Crests() {
    SectionHeader("Player crests")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(PlayerCrests.count) { i ->
            PlayerAvatar(player(i, PlayerColors.default(i).argb), size = 52.dp)
        }
    }
    SectionHeader("Palette and fallbacks")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        PlayerColors.all.forEachIndexed { i, c -> PlayerAvatar(player(i, c.argb), size = 30.dp) }
    }
    Spacer(Modifier.height(Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(player(99, 0xFF3FC1B0, "Quinton"), size = 40.dp)
        PlayerAvatar(null, size = 40.dp)
        PlayerColors.all.take(4).forEach { c ->
            Text(c.name, color = playerColor(c.argb), maxLines = 1)
        }
    }
}

private fun player(i: Int, color: Long, name: String = "Player $i") =
    Player(id = "p$i", name = name, color = color, avatarId = i, createdAt = 0)

private const val IMAGE_TIMEOUT_MS = 10_000L

private val ROSTER = listOf(
    "arien", "brogan", "tigerclaw", "wasp", "sabina", "xargatha", "dodger", "misa", "whisper", "ursafar",
    "silverarrow", "tali", "garrus", "bain", "cutter", "trinkets", "nebkher", "min", "swift", "wuk", "hanu",
    "ignatia", "rowenna", "mrak", "snorri", "razzle", "gydion", "brynn", "mortimer", "widget", "takahide", "emmitt",
)

/** The text parts of the official look at large text: banner, section head, printed card text. */
@Composable
private fun PrintedGallery() {
    SteelBanner(Modifier.fillMaxWidth()) {
        Text(
            "Steel banner",
            style = GoaTextStyles.bannerTitle,
            color = GoaTheme.colors.onBanner,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
    }
    SectionHeader("Printed text")
    PrintedCardText(
        "Target a unit adjacent to you. **This turn:** gain 1 {coin} coin. ~(Reminder text is italic.)",
        Modifier.fillMaxWidth(),
    )
}

/** The official graphics the import brings in (ADR-042), as the app frames them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfficialArtGallery() {
    SteelBanner(Modifier.fillMaxWidth()) {
        Text(
            "Steel banner",
            style = GoaTextStyles.bannerTitle,
            color = GoaTheme.colors.onBanner,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
    }
    SectionHeader("Deck symbols")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ROSTER.forEach { HeroSymbol(it, "official/heroes/$it/symbol.webp", Modifier.size(40.dp)) }
        HeroSymbol("someone-new", null, Modifier.size(40.dp))
    }
    SectionHeader("Cards and printed text")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
        listOf("arien-red-2a", "wasp-gold", "gydion-purple-4").forEach { id ->
            CardScan("official/cards/$id.webp", contentDescription = id, modifier = Modifier.width(76.dp)) {}
        }
        CardScan(null, contentDescription = "missing", modifier = Modifier.width(76.dp)) {
            Text("No scan", Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    PrintedCardText(
        "Target a unit adjacent to you. **This turn:** gain 1 {coin} coin. ~(Reminder text is italic.)",
        Modifier.fillMaxWidth(),
    )
    SectionHeader("Item icons, Spawn points, Tie Breaker coin")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Stat.entries.forEach { OfficialIcon(OfficialArt.itemIcon(it), Modifier.size(width = 40.dp, height = 30.dp)) }
        Team.entries.forEach { team ->
            OfficialIcon(OfficialArt.heroSpawn(team), Modifier.size(30.dp))
            MinionType.entries.forEach { OfficialIcon(OfficialArt.minionSpawn(it, team), Modifier.size(30.dp)) }
            OfficialIcon(OfficialArt.tieBreaker(team), Modifier.size(30.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Components() {
    SectionHeader("Profile")
    GoaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroSigil("arien", Modifier.size(32.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Arien", style = MaterialTheme.typography.titleLarge)
                ComplexityStars(1, showLabel = true)
            }
            VerifiedBadge()
        }
        Spacer(Modifier.height(Spacing.md))
        StatBar("Attack", 3, 5)
        StatBar("Defense", 4, 6)
        StatBar("Initiative", 6, 6)
        StatBar("Movement", null, null)
    }
    SectionHeader("Filters", trailing = { TextButton(onClick = {}) { Text("Clear") } })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GoaFilterChip(selected = true, onClick = {}, label = "Melee")
        GoaFilterChip(selected = false, onClick = {}, label = "Healer")
        GoaFilterChip(selected = true, onClick = {}, label = "★★★")
    }
    Spacer(Modifier.height(Spacing.sm))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TeamBadge(Team.RED)
        TeamBadge(Team.BLUE)
        UnverifiedBadge()
        CardColor.entries.forEach { CardColorDot(it) }
        ComplexityStars(4, showLabel = true)
    }
    SectionHeader("Timer digits")
    Row(verticalAlignment = Alignment.CenterVertically) {
        DurationText(90_000, style = GoaTextStyles.timerDigitsSmall)
        Spacer(Modifier.width(Spacing.lg))
        DurationText(-12_000, overtimeSign = true, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(Spacing.lg))
        DurationText(3_725_000)
    }
    EmptyState(
        icon = Icons.Outlined.Groups,
        title = "No players yet",
        message = "Add your play group to track ratings.",
        modifier = Modifier.fillMaxWidth(),
        action = { TextButton(onClick = {}) { Text("Add player") } },
    )
}
