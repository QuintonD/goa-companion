package dev.quinton.goa.feature.catalog.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Carpenter
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.FlutterDash
import androidx.compose.material.icons.filled.Foundation
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Volcano
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Campaign as OutlinedCampaign
import androidx.compose.material.icons.outlined.Carpenter as OutlinedCarpenter
import androidx.compose.material.icons.outlined.FlutterDash as OutlinedFlutterDash
import androidx.compose.material.icons.outlined.Hardware as OutlinedHardware
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Toll as OutlinedToll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.presentation.catalog.GameIconLabels

/** A game icon: glyph plus the word TalkBack reads for it. */
data class GameIcon(val vector: ImageVector, val label: String)

/**
 * Glyphs for the inline icon tokens in card text ([dev.quinton.goa.core.model.catalog.CardText.ICONS])
 * and for card values. Material Symbols where a clear match exists; the sword is an original glyph.
 */
object GameIcons {
    /** Original sword glyph on the 24-unit grid (no Material equivalent). */
    val Sword: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sword",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                // Blade
                moveTo(12f, 1.5f)
                lineTo(14.2f, 4.5f)
                lineTo(14.2f, 14.5f)
                lineTo(9.8f, 14.5f)
                lineTo(9.8f, 4.5f)
                close()
                // Cross-guard
                moveTo(6f, 15f)
                lineTo(18f, 15f)
                lineTo(18f, 17.2f)
                lineTo(6f, 17.2f)
                close()
                // Grip
                moveTo(10.9f, 17.2f)
                lineTo(13.1f, 17.2f)
                lineTo(13.1f, 20.5f)
                lineTo(10.9f, 20.5f)
                close()
                // Pommel
                moveTo(12f, 20.2f)
                lineTo(14f, 21.6f)
                lineTo(12f, 23f)
                lineTo(10f, 21.6f)
                close()
            }
            .build()
    }

    val attack get() = GameIcon(Sword, GameIconLabels.ATTACK)
    val defense get() = GameIcon(Icons.Filled.Shield, GameIconLabels.DEFENSE)
    val initiative get() = GameIcon(Icons.Filled.Bolt, GameIconLabels.INITIATIVE)
    val movement get() = GameIcon(Icons.AutoMirrored.Filled.DirectionsRun, GameIconLabels.MOVEMENT)
    val range get() = GameIcon(Icons.Filled.GpsFixed, GameIconLabels.RANGE)
    val radius get() = GameIcon(Icons.Filled.TripOrigin, GameIconLabels.RADIUS)
    val skill get() = GameIcon(Icons.Filled.AutoAwesome, GameIconLabels.SKILL)
    val ranged get() = GameIcon(Icons.Filled.NorthEast, GameIconLabels.RANGED)
    val block get() = GameIcon(Icons.Filled.PriorityHigh, GameIconLabels.BLOCK)
    val item get() = GameIcon(Icons.Filled.Backpack, GameIconLabels.ITEM)
    val ultimate get() = GameIcon(Icons.Filled.WorkspacePremium, GameIconLabels.ULTIMATE)
    val hold get() = GameIcon(Icons.Filled.PanTool, GameIconLabels.HOLD)
    val fastTravel get() = GameIcon(Icons.Filled.FastForward, GameIconLabels.FAST_TRAVEL)
    val clear get() = GameIcon(Icons.Filled.CleaningServices, GameIconLabels.CLEAR)

    private val byToken: Map<String, GameIcon> by lazy {
        mapOf(
            "attack" to attack,
            "defense" to defense,
            "initiative" to initiative,
            "movement" to movement,
            "range" to range,
            "radius" to radius,
            "skill" to skill,
            "coin" to GameIcon(Icons.Filled.MonetizationOn, label("coin")),
            "hold" to hold,
            "fast_travel" to fastTravel,
            "clear" to clear,
            "ranged" to ranged,
            "block" to block,
            "item" to item,
            "ultimate" to ultimate,
            "minion" to GameIcon(Icons.Outlined.Person, label("minion")),
            "heavy_minion" to GameIcon(Icons.Filled.Person, label("heavy_minion")),
            "spawn" to GameIcon(Icons.Filled.AddLocationAlt, label("spawn")),
            "life" to GameIcon(Icons.Filled.Favorite, label("life")),
            "wave" to GameIcon(Icons.Filled.Waves, label("wave")),
            // Hero-specific symbols printed in card text (tokens, markers, runes, tie breaker sides).
            "tiebreaker_blue" to GameIcon(Icons.Filled.Toll, label("tiebreaker_blue")),
            "tiebreaker_orange" to GameIcon(Icons.Outlined.OutlinedToll, label("tiebreaker_orange")),
            "marker_bounty" to GameIcon(Icons.Filled.Paid, label("marker_bounty")),
            "marker_poison" to GameIcon(Icons.Filled.Science, label("marker_poison")),
            "rune_anvil" to GameIcon(Icons.Filled.Hardware, label("rune_anvil")),
            "rune_axe" to GameIcon(Icons.Filled.Carpenter, label("rune_axe")),
            "rune_bird" to GameIcon(Icons.Filled.FlutterDash, label("rune_bird")),
            "rune_horn" to GameIcon(Icons.Filled.Campaign, label("rune_horn")),
            "rune_anvil_marker" to GameIcon(Icons.Outlined.OutlinedHardware, label("rune_anvil_marker")),
            "rune_axe_marker" to GameIcon(Icons.Outlined.OutlinedCarpenter, label("rune_axe_marker")),
            "rune_bird_marker" to GameIcon(Icons.Outlined.OutlinedFlutterDash, label("rune_bird_marker")),
            "rune_horn_marker" to GameIcon(Icons.Outlined.OutlinedCampaign, label("rune_horn_marker")),
            "token_barrier" to GameIcon(Icons.Filled.Fence, label("token_barrier")),
            "token_blast" to GameIcon(Icons.Filled.LocalFireDepartment, label("token_blast")),
            "token_dud" to GameIcon(Icons.Filled.DoNotDisturbOn, label("token_dud")),
            "token_familiar" to GameIcon(Icons.Filled.Pets, label("token_familiar")),
            "token_glitch" to GameIcon(Icons.Filled.BrokenImage, label("token_glitch")),
            "token_grenade" to GameIcon(Icons.Filled.Adjust, label("token_grenade")),
            "token_ice" to GameIcon(Icons.Filled.AcUnit, label("token_ice")),
            "token_illusion" to GameIcon(Icons.Filled.AutoFixHigh, label("token_illusion")),
            "token_magma" to GameIcon(Icons.Filled.Volcano, label("token_magma")),
            "token_rock" to GameIcon(Icons.Filled.Landscape, label("token_rock")),
            "token_smoke_bomb" to GameIcon(Icons.Filled.Cloud, label("token_smoke_bomb")),
            "token_totem" to GameIcon(Icons.Filled.Foundation, label("token_totem")),
            "token_tree" to GameIcon(Icons.Filled.Park, label("token_tree")),
            "token_zombie" to GameIcon(Icons.Filled.SentimentVeryDissatisfied, label("token_zombie")),
        )
    }

    private fun label(token: String): String = checkNotNull(GameIconLabels.forToken(token)) { token }

    /** Icon for a markup token such as `attack`; null for unknown names (rendered as text). */
    fun forToken(name: String): GameIcon? = byToken[name]

    fun forStat(stat: Stat): GameIcon = when (stat) {
        Stat.ATTACK -> attack
        Stat.DEFENSE -> defense
        Stat.INITIATIVE -> initiative
        Stat.MOVEMENT -> movement
        Stat.RANGE -> range
        Stat.RADIUS -> radius
    }

    fun forPrimary(action: PrimaryAction): GameIcon = when (action) {
        PrimaryAction.ATTACK -> attack
        PrimaryAction.SKILL -> skill
        PrimaryAction.DEFENSE -> defense
        PrimaryAction.MOVEMENT -> movement
        PrimaryAction.DEFENSE_SKILL -> GameIcon(Icons.Filled.Shield, GameIconLabels.DEFENSE_SKILL)
    }

    fun forSecondary(action: SecondaryAction): GameIcon = when (action) {
        SecondaryAction.MOVEMENT -> movement
        SecondaryAction.DEFENSE -> defense
        SecondaryAction.HOLD -> hold
        SecondaryAction.FAST_TRAVEL -> fastTravel
        SecondaryAction.CLEAR -> clear
        SecondaryAction.ATTACK -> attack
    }
}
