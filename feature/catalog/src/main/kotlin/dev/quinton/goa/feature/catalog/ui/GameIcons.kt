package dev.quinton.goa.feature.catalog.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.core.model.catalog.Stat

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

    val attack get() = GameIcon(Sword, "Attack")
    val defense get() = GameIcon(Icons.Filled.Shield, "Defense")
    val initiative get() = GameIcon(Icons.Filled.Bolt, "Initiative")
    val movement get() = GameIcon(Icons.AutoMirrored.Filled.DirectionsRun, "Movement")
    val range get() = GameIcon(Icons.Filled.GpsFixed, "Range")
    val radius get() = GameIcon(Icons.Filled.TripOrigin, "Radius")
    val skill get() = GameIcon(Icons.Filled.AutoAwesome, "Skill")
    val ranged get() = GameIcon(Icons.Filled.NorthEast, "Ranged")
    val block get() = GameIcon(Icons.Filled.PriorityHigh, "Block")
    val item get() = GameIcon(Icons.Filled.Backpack, "Item")
    val ultimate get() = GameIcon(Icons.Filled.WorkspacePremium, "Ultimate")
    val hold get() = GameIcon(Icons.Filled.PanTool, "Hold")
    val fastTravel get() = GameIcon(Icons.Filled.FastForward, "Fast Travel")
    val clear get() = GameIcon(Icons.Filled.CleaningServices, "Clear")

    private val byToken: Map<String, GameIcon> by lazy {
        mapOf(
            "attack" to attack,
            "defense" to defense,
            "initiative" to initiative,
            "movement" to movement,
            "range" to range,
            "radius" to radius,
            "skill" to skill,
            "coin" to GameIcon(Icons.Filled.MonetizationOn, "Coin"),
            "hold" to hold,
            "fast_travel" to fastTravel,
            "clear" to clear,
            "ranged" to ranged,
            "block" to block,
            "item" to item,
            "ultimate" to ultimate,
            "minion" to GameIcon(Icons.Outlined.Person, "Minion"),
            "heavy_minion" to GameIcon(Icons.Filled.Person, "Heavy minion"),
            "spawn" to GameIcon(Icons.Filled.AddLocationAlt, "Spawn point"),
            "life" to GameIcon(Icons.Filled.Favorite, "Life counter"),
            "wave" to GameIcon(Icons.Filled.Waves, "Wave counter"),
        )
    }

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
        PrimaryAction.DEFENSE_SKILL -> GameIcon(Icons.Filled.Shield, "Defense / Skill")
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
