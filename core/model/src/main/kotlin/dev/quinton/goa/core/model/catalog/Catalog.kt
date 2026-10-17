package dev.quinton.goa.core.model.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Catalog types mirror the bundled JSON in `assets/catalog/` (schema in docs/DOMAIN.md).
 * The importer (`tools/catalog-import`) writes these and the app reads them, so both sides
 * always agree. Unknown values are `null`, never guessed.
 */

/** Contents of `catalog/index.json`. */
@Serializable
data class CatalogIndex(
    val dataVersion: Int,
    val printing: Int,
    val products: List<Product>,
    val heroes: List<String>,
)

@Serializable
data class Product(
    val id: String,
    val name: String,
    val kind: ProductKind,
    val released: Int? = null,
    val bggId: Int? = null,
)

@Serializable
enum class ProductKind {
    @SerialName("base")
    BASE,

    @SerialName("hero_pack")
    HERO_PACK,

    @SerialName("promo")
    PROMO,

    @SerialName("upgrade")
    UPGRADE,
}

@Serializable
data class Hero(
    val id: String,
    val name: String,
    val title: String,
    val product: String,
    val aliases: List<String> = emptyList(),
    val complexity: Int? = null,
    val stats: HeroStats = HeroStats(),
    val roles: List<Role> = emptyList(),
    val portrait: String? = null,
    val printing: Int,
    val cards: List<Card>,
    val handicap: Card? = null,
    val extras: List<Card> = emptyList(),
    val faq: List<Ruling> = emptyList(),
    val lore: String? = null,
    val verified: Boolean,
    val sources: List<SourceRef> = emptyList(),
) {
    val displayName: String get() = "$name $title"

    /** Every card the hero owns, in deck order: deck, handicap, extras. */
    val allCards: List<Card> get() = cards + listOfNotNull(handicap) + extras
}

/** Profile bars from the hero card: `[base, max]` on a 1..8 scale. */
@Serializable
data class HeroStats(
    val attack: List<Int>? = null,
    val defense: List<Int>? = null,
    val initiative: List<Int>? = null,
    val movement: List<Int>? = null,
) {
    fun get(stat: Stat): StatRange? = when (stat) {
        Stat.ATTACK -> attack
        Stat.DEFENSE -> defense
        Stat.INITIATIVE -> initiative
        Stat.MOVEMENT -> movement
        Stat.RANGE, Stat.RADIUS -> null
    }?.takeIf { it.size == 2 }?.let { StatRange(it[0], it[1]) }
}

data class StatRange(val base: Int, val max: Int)

@Serializable
enum class Stat {
    @SerialName("attack")
    ATTACK,

    @SerialName("defense")
    DEFENSE,

    @SerialName("initiative")
    INITIATIVE,

    @SerialName("movement")
    MOVEMENT,

    @SerialName("range")
    RANGE,

    @SerialName("radius")
    RADIUS,
}

/** The 10 official hero roles (rulebook p.21); a hero has 2-6. */
@Serializable
enum class Role {
    @SerialName("tactician")
    TACTICIAN,

    @SerialName("disabler")
    DISABLER,

    @SerialName("durable")
    DURABLE,

    @SerialName("melee")
    MELEE,

    @SerialName("pusher")
    PUSHER,

    @SerialName("damager")
    DAMAGER,

    @SerialName("sniper")
    SNIPER,

    @SerialName("tokens")
    TOKENS,

    @SerialName("farming")
    FARMING,

    @SerialName("healer")
    HEALER,
}

@Serializable
data class Card(
    val id: String,
    val heroId: String,
    val name: String,
    val color: CardColor,
    /**
     * 1..4 (4 = Ultimate); null for basic Gold/Silver cards, the Handicap card and unlevelled
     * extras. Spellbook pages carry their printed spell level (null for cantrips).
     */
    val tier: Int? = null,
    val initiative: Int? = null,
    val primary: PrimaryAction? = null,
    val secondary: List<SecondaryAction> = emptyList(),
    val values: CardValues = CardValues(),
    val ranged: Boolean = false,
    val block: Boolean = false,
    /** Stat this card grants (+1) when tucked as an item; Tier II+ only. */
    val item: Stat? = null,
    /** Verbatim card text using the inline markup described in [CardText]. */
    val text: String = "",
    val image: String? = null,
)

@Serializable
enum class CardColor {
    @SerialName("gold")
    GOLD,

    @SerialName("silver")
    SILVER,

    @SerialName("red")
    RED,

    @SerialName("green")
    GREEN,

    @SerialName("blue")
    BLUE,

    @SerialName("purple")
    PURPLE,

    /** Handicap card, spellbook pages and other non-deck cards. */
    @SerialName("other")
    OTHER,
}

@Serializable
enum class PrimaryAction {
    @SerialName("attack")
    ATTACK,

    @SerialName("skill")
    SKILL,

    @SerialName("defense")
    DEFENSE,

    @SerialName("movement")
    MOVEMENT,

    @SerialName("defense_skill")
    DEFENSE_SKILL,
}

@Serializable
enum class SecondaryAction {
    @SerialName("movement")
    MOVEMENT,

    @SerialName("defense")
    DEFENSE,

    @SerialName("hold")
    HOLD,

    @SerialName("fast_travel")
    FAST_TRAVEL,

    @SerialName("clear")
    CLEAR,

    /** A secondary Attack action (e.g. Mrak's red cards); its value is in [CardValues.attack]. */
    @SerialName("attack")
    ATTACK,
}

@Serializable
data class CardValues(
    val attack: Int? = null,
    val defense: Int? = null,
    val movement: Int? = null,
    val range: Int? = null,
    val radius: Int? = null,
)

@Serializable
data class Ruling(
    val question: String,
    val answer: String,
    val source: String,
)

@Serializable
data class SourceRef(
    val name: String,
    val url: String? = null,
    val retrieved: String? = null,
)

/** The whole catalog, loaded once and held in memory. */
data class Catalog(
    val dataVersion: Int,
    val printing: Int,
    val products: List<Product>,
    val heroes: List<Hero>,
) {
    private val byId: Map<String, Hero> = heroes.associateBy { it.id }

    fun hero(id: String): Hero? = byId[id]

    fun product(id: String): Product? = products.firstOrNull { it.id == id }

    companion object {
        val EMPTY = Catalog(dataVersion = 0, printing = 2, products = emptyList(), heroes = emptyList())
    }
}
