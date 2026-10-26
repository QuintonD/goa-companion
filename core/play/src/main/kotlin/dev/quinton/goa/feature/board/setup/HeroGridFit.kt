package dev.quinton.goa.feature.board.setup

/**
 * How many columns New board's hero grid uses so every hero fits the space it has without
 * scrolling (PRODUCT §8 "New board without scrolling"): the fewest columns, so the biggest tiles,
 * whose rows of square portraits ([label] below each) plus [extra] (the "Show all heroes" row)
 * fit [height]. Tiles never get smaller than [minTile] nor, with few heroes, bigger than [maxTile];
 * when even the smallest tiles don't fit, the most columns at [minTile] (the grid then scrolls the
 * least). All sizes in the same unit (dp).
 */
fun heroGridColumns(
    count: Int,
    width: Float,
    height: Float,
    label: Float,
    spacing: Float,
    extra: Float,
    minTile: Float,
    maxTile: Float,
): Int {
    fun tile(columns: Int) = (width - (columns - 1) * spacing) / columns
    val fewest = ((width + spacing) / (maxTile + spacing)).toInt().coerceAtLeast(1)
    val most = ((width + spacing) / (minTile + spacing)).toInt().coerceAtLeast(fewest)
    if (count <= 0) return fewest
    for (columns in fewest..most) {
        val rows = (count + columns - 1) / columns
        val needed = rows * (tile(columns) + label) + (rows - 1) * spacing + extra
        if (needed <= height) return columns
    }
    return most
}
