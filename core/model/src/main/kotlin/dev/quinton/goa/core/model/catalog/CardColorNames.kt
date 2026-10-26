package dev.quinton.goa.core.model.catalog

/** A card colour's name, as the screens print it ("Gold", "Red", …), on the phone and on the web. */
fun cardColorName(color: CardColor): String = when (color) {
    CardColor.GOLD -> "Gold"
    CardColor.SILVER -> "Silver"
    CardColor.RED -> "Red"
    CardColor.GREEN -> "Green"
    CardColor.BLUE -> "Blue"
    CardColor.PURPLE -> "Purple"
    CardColor.OTHER -> "Other"
}
